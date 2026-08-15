package net.ultranick.paper.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.SkinData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Low-level GameProfile modifier using modern Paper API and safe reflection.
 * Ensures the internal GameProfile name and textures are updated so Minecraft clients
 * render the disguised nametag above the player's head and apply skins correctly.
 *
 * @author Chatbxn
 */
public final class GameProfileModifier {

    private static final Logger LOGGER = Logger.getLogger(GameProfileModifier.class.getName());

    private static boolean initialized = false;
    private static Method GET_HANDLE_METHOD;
    private static Field SERVER_PLAYER_GAME_PROFILE_FIELD;
    private static Class<?> GAME_PROFILE_CLASS;
    private static Constructor<?> GAME_PROFILE_CONSTRUCTOR;
    private static Method GET_PROPERTIES_METHOD;
    private static Class<?> PROPERTY_CLASS;
    private static Constructor<?> PROPERTY_CTOR_3;
    private static Constructor<?> PROPERTY_CTOR_2;
    private static Field GAME_PROFILE_NAME_FIELD;

    static {
        initReflection();
    }

    private static synchronized void initReflection() {
        if (initialized) return;
        initialized = true;

        try {
            // Find getHandle on CraftPlayer
            Class<?> craftPlayerClass = null;
            try {
                craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            } catch (ClassNotFoundException e) {
                for (Package pkg : Package.getPackages()) {
                    if (pkg.getName().startsWith("org.bukkit.craftbukkit.") && pkg.getName().endsWith(".entity")) {
                        try {
                            craftPlayerClass = Class.forName(pkg.getName() + ".CraftPlayer");
                            break;
                        } catch (ClassNotFoundException ignored) {}
                    }
                }
            }

            if (craftPlayerClass != null) {
                GET_HANDLE_METHOD = craftPlayerClass.getMethod("getHandle");
                GET_HANDLE_METHOD.setAccessible(true);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "CraftPlayer#getHandle reflection note: " + t.getMessage());
        }

        try {
            GAME_PROFILE_CLASS = Class.forName("com.mojang.authlib.GameProfile");
            try {
                GAME_PROFILE_CONSTRUCTOR = GAME_PROFILE_CLASS.getConstructor(UUID.class, String.class);
                GAME_PROFILE_CONSTRUCTOR.setAccessible(true);
            } catch (Throwable ignored) {}

            try {
                GET_PROPERTIES_METHOD = GAME_PROFILE_CLASS.getMethod("getProperties");
                GET_PROPERTIES_METHOD.setAccessible(true);
            } catch (Throwable ignored) {}

            for (Field f : GAME_PROFILE_CLASS.getDeclaredFields()) {
                if (f.getType() == String.class && (f.getName().equals("name") || f.getName().equalsIgnoreCase("name"))) {
                    f.setAccessible(true);
                    GAME_PROFILE_NAME_FIELD = f;
                    break;
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "GameProfile class reflection note: " + t.getMessage());
        }

        try {
            PROPERTY_CLASS = Class.forName("com.mojang.authlib.properties.Property");
            for (Constructor<?> ctor : PROPERTY_CLASS.getConstructors()) {
                if (ctor.getParameterCount() == 3) {
                    PROPERTY_CTOR_3 = ctor;
                    PROPERTY_CTOR_3.setAccessible(true);
                } else if (ctor.getParameterCount() == 2) {
                    PROPERTY_CTOR_2 = ctor;
                    PROPERTY_CTOR_2.setAccessible(true);
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Property class reflection note: " + t.getMessage());
        }
    }

    private static Field findGameProfileField(Class<?> clazz) {
        if (SERVER_PLAYER_GAME_PROFILE_FIELD != null) {
            return SERVER_PLAYER_GAME_PROFILE_FIELD;
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getType().getName().equals("com.mojang.authlib.GameProfile") ||
                        f.getType().getSimpleName().equals("GameProfile")) {
                    f.setAccessible(true);
                    SERVER_PLAYER_GAME_PROFILE_FIELD = f;
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Applies the disguise to the player's internal GameProfile and Paper PlayerProfile.
     */
    public static void applyProfileDisguise(@NotNull Player player, @NotNull DisguiseProfile profile) {
        String newName = profile.getDisguisedName();
        SkinData skin = profile.getSkin();

        // 1. Update Paper PlayerProfile API
        try {
            PlayerProfile paperProfile = Bukkit.createProfile(player.getUniqueId(), newName);
            if (skin != null && skin.getValue() != null && !skin.getValue().isEmpty()) {
                paperProfile.setProperty(new ProfileProperty(
                        "textures",
                        skin.getValue(),
                        skin.getSignature() != null ? skin.getSignature() : ""
                ));
            }
            player.setPlayerProfile(paperProfile);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Paper PlayerProfile apply note: " + t.getMessage());
        }

        // 2. Update NMS ServerPlayer GameProfile via reflection
        try {
            Object serverPlayer = getServerPlayer(player);
            if (serverPlayer != null) {
                Field profileField = findGameProfileField(serverPlayer.getClass());
                if (profileField != null) {
                    Object oldProfile = profileField.get(serverPlayer);

                    // Mutate name field on existing profile if available
                    if (GAME_PROFILE_NAME_FIELD != null && oldProfile != null) {
                        try {
                            GAME_PROFILE_NAME_FIELD.set(oldProfile, newName);
                        } catch (Throwable ignored) {}
                    }

                    // Create new GameProfile instance
                    if (GAME_PROFILE_CONSTRUCTOR != null) {
                        Object disguisedProfile = GAME_PROFILE_CONSTRUCTOR.newInstance(player.getUniqueId(), newName);

                        if (GET_PROPERTIES_METHOD != null) {
                            Object propMap = GET_PROPERTIES_METHOD.invoke(disguisedProfile);
                            if (propMap != null) {
                                // Add skin textures
                                if (skin != null && skin.getValue() != null && !skin.getValue().isEmpty()) {
                                    Object prop = createProperty("textures", skin.getValue(), skin.getSignature());
                                    if (prop != null) {
                                        Method putMethod = propMap.getClass().getMethod("put", Object.class, Object.class);
                                        putMethod.invoke(propMap, "textures", prop);
                                    }
                                } else if (oldProfile != null) {
                                    Object oldPropMap = GET_PROPERTIES_METHOD.invoke(oldProfile);
                                    if (oldPropMap != null) {
                                        Method putAll = propMap.getClass().getMethod("putAll", Class.forName("com.google.common.collect.Multimap"));
                                        putAll.invoke(propMap, oldPropMap);
                                    }
                                }
                            }
                        }

                        profileField.set(serverPlayer, disguisedProfile);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "NMS GameProfile disguise note: " + t.getMessage());
        }
    }

    /**
     * Restores the player's original GameProfile and skin properties.
     */
    public static void restoreOriginalProfile(
            @NotNull Player player,
            @Nullable Set<ProfileProperty> originalProperties
    ) {
        String realName = player.getName();

        // 1. Restore Paper PlayerProfile API
        try {
            PlayerProfile originalProfile = Bukkit.createProfile(player.getUniqueId(), realName);
            if (originalProperties != null) {
                originalProfile.setProperties(originalProperties);
            }
            player.setPlayerProfile(originalProfile);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Paper PlayerProfile restore note: " + t.getMessage());
        }

        // 2. Restore NMS ServerPlayer GameProfile via reflection
        try {
            Object serverPlayer = getServerPlayer(player);
            if (serverPlayer != null) {
                Field profileField = findGameProfileField(serverPlayer.getClass());
                if (profileField != null) {
                    Object currentProfile = profileField.get(serverPlayer);

                    if (GAME_PROFILE_NAME_FIELD != null && currentProfile != null) {
                        try {
                            GAME_PROFILE_NAME_FIELD.set(currentProfile, realName);
                        } catch (Throwable ignored) {}
                    }

                    if (GAME_PROFILE_CONSTRUCTOR != null) {
                        Object restoredProfile = GAME_PROFILE_CONSTRUCTOR.newInstance(player.getUniqueId(), realName);

                        if (GET_PROPERTIES_METHOD != null) {
                            Object propMap = GET_PROPERTIES_METHOD.invoke(restoredProfile);
                            if (propMap != null && originalProperties != null) {
                                Method putMethod = propMap.getClass().getMethod("put", Object.class, Object.class);
                                for (ProfileProperty p : originalProperties) {
                                    Object prop = createProperty(p.getName(), p.getValue(), p.getSignature());
                                    if (prop != null) {
                                        putMethod.invoke(propMap, p.getName(), prop);
                                    }
                                }
                            }
                        }

                        profileField.set(serverPlayer, restoredProfile);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "NMS GameProfile restore note: " + t.getMessage());
        }
    }

    private static Object createProperty(String name, String value, String signature) {
        try {
            if (PROPERTY_CTOR_3 != null && signature != null && !signature.isEmpty()) {
                return PROPERTY_CTOR_3.newInstance(name, value, signature);
            }
            if (PROPERTY_CTOR_2 != null) {
                return PROPERTY_CTOR_2.newInstance(name, value);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getServerPlayer(Player player) {
        try {
            if (GET_HANDLE_METHOD != null) {
                return GET_HANDLE_METHOD.invoke(player);
            }
            Method getHandle = player.getClass().getMethod("getHandle");
            getHandle.setAccessible(true);
            return getHandle.invoke(player);
        } catch (Throwable t) {
            return null;
        }
    }
}
