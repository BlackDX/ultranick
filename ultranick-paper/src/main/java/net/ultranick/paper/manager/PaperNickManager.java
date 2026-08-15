package net.ultranick.paper.manager;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.ultranick.api.UltraNickAPI;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.NickResult;
import net.ultranick.api.model.SkinData;
import net.ultranick.common.config.UltraNickConfig;
import net.ultranick.common.json.UltraJson;
import net.ultranick.common.name.NamePoolManager;
import net.ultranick.common.redis.RedisDisguiseEvent;
import net.ultranick.common.skin.PreloadedSkinManager;
import net.ultranick.common.storage.StorageManager;
import net.ultranick.paper.nametag.PaperNametagManager;
import net.ultranick.paper.refresh.PaperEntityRefresher;
import net.ultranick.paper.util.GameProfileModifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaperNickManager implements UltraNickAPI {

    private final Plugin plugin;
    private final Logger logger;
    private final UltraNickConfig config;
    private final StorageManager storageManager;
    private final NamePoolManager namePoolManager;
    private final PreloadedSkinManager skinManager;
    private final PaperNametagManager nametagManager;
    private final PaperEntityRefresher entityRefresher;

    private final Map<UUID, DisguiseProfile> activeDisguises = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ProfileProperty>> originalProperties = new ConcurrentHashMap<>();

    public PaperNickManager(
            @NotNull Plugin plugin,
            @NotNull UltraNickConfig config,
            @NotNull StorageManager storageManager,
            @NotNull NamePoolManager namePoolManager,
            @NotNull PreloadedSkinManager skinManager,
            @NotNull PaperNametagManager nametagManager,
            @NotNull PaperEntityRefresher entityRefresher
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getLogger();
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.storageManager = Objects.requireNonNull(storageManager, "storageManager cannot be null");
        this.namePoolManager = Objects.requireNonNull(namePoolManager, "namePoolManager cannot be null");
        this.skinManager = Objects.requireNonNull(skinManager, "skinManager cannot be null");
        this.nametagManager = Objects.requireNonNull(nametagManager, "nametagManager cannot be null");
        this.entityRefresher = Objects.requireNonNull(entityRefresher, "entityRefresher cannot be null");
    }

    public @NotNull Plugin getPlugin() {
        return plugin;
    }

    public @NotNull PaperNametagManager getNametagManager() {
        return nametagManager;
    }

    public @NotNull PaperEntityRefresher getEntityRefresher() {
        return entityRefresher;
    }

    public void loadInitialState() {
        storageManager.loadAllActiveDisguises().thenAccept(disguises -> {
            for (DisguiseProfile profile : disguises) {
                activeDisguises.put(profile.getUniqueId(), profile);
                nameIndex.put(profile.getDisguisedName().toLowerCase(Locale.ROOT), profile.getUniqueId());
            }
            logger.info("Loaded " + disguises.size() + " active disguises from storage.");
        });
    }

    public CompletableFuture<Optional<DisguiseProfile>> loadDisguiseFromStorage(@NotNull UUID uniqueId) {
        return storageManager.loadDisguise(uniqueId).thenApply(opt -> {
            opt.ifPresent(profile -> {
                activeDisguises.put(profile.getUniqueId(), profile);
                nameIndex.put(profile.getDisguisedName().toLowerCase(Locale.ROOT), profile.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(uniqueId);
                    if (player != null && player.isOnline()) {
                        applyDisguiseLocally(profile);
                    }
                });
            });
            return opt;
        });
    }

    public void requestProxySync(@NotNull Player player) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("REQUEST_SYNC");
            out.writeUTF(player.getUniqueId().toString());
            player.sendPluginMessage(plugin, "ultranick:sync", out.toByteArray());
        } catch (Throwable ignored) {}
    }

    public void handleRemoteRedisEvent(@NotNull String payload) {
        try {
            RedisDisguiseEvent event = UltraJson.fromJson(payload, RedisDisguiseEvent.class);
            if (event == null) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getAction() == RedisDisguiseEvent.Action.DISGUISE) {
                    DisguiseProfile profile = event.toDisguiseProfile();
                    applyDisguiseLocally(profile);
                } else if (event.getAction() == RedisDisguiseEvent.Action.UNDISGUISE) {
                    undisguiseLocally(event.getUniqueId());
                }
            });
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to handle Redis disguise event: " + e.getMessage());
        }
    }

    // Apply disguise to an online player on Bukkit main thread
    public void applyDisguiseLocally(@NotNull DisguiseProfile profile) {
        activeDisguises.put(profile.getUniqueId(), profile);
        nameIndex.put(profile.getDisguisedName().toLowerCase(Locale.ROOT), profile.getUniqueId());

        Player player = Bukkit.getPlayer(profile.getUniqueId());
        if (player == null || !player.isOnline()) {
            return;
        }

        applyToPlayer(player, profile);

        // Multi-tick safety re-apply
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(profile.getUniqueId());
            if (p != null && p.isOnline()) {
                applyToPlayer(p, profile);
            }
        }, 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(profile.getUniqueId());
            if (p != null && p.isOnline()) {
                nametagManager.applyDisguiseNametag(p, profile);
            }
        }, 10L);
    }

    private void applyToPlayer(@NotNull Player player, @NotNull DisguiseProfile profile) {
        // Save original textures
        originalProperties.putIfAbsent(player.getUniqueId(), new HashSet<>(player.getPlayerProfile().getProperties()));

        // Apply GameProfile & PlayerProfile
        GameProfileModifier.applyProfileDisguise(player, profile);

        // Display name & tablist
        Component displayName = LegacyComponentSerializer.legacyAmpersand().deserialize(
                profile.getPrefix() + profile.getDisguisedName() + profile.getSuffix()
        );
        player.displayName(displayName);
        player.playerListName(displayName);
        player.customName(null);
        player.setCustomNameVisible(false);

        // Nametag team
        nametagManager.applyDisguiseNametag(player, profile);

        // Refresh visuals
        entityRefresher.refreshPlayer(player);
    }

    // Remove disguise locally on Bukkit main thread
    public void undisguiseLocally(@NotNull UUID uniqueId) {
        DisguiseProfile removed = activeDisguises.remove(uniqueId);
        if (removed != null) {
            nameIndex.remove(removed.getDisguisedName().toLowerCase(Locale.ROOT));
        }

        Player player = Bukkit.getPlayer(uniqueId);
        if (player == null || !player.isOnline()) {
            return;
        }

        // Restore original GameProfile & PlayerProfile
        Set<ProfileProperty> origProps = originalProperties.remove(uniqueId);
        GameProfileModifier.restoreOriginalProfile(player, origProps);

        // Restore display name & tablist
        player.displayName(Component.text(player.getName()));
        player.playerListName(Component.text(player.getName()));
        player.customName(null);
        player.setCustomNameVisible(false);

        // Clear nametag team
        if (removed != null) {
            nametagManager.clearDisguiseNametag(player, removed.getDisguisedName());
        }

        // Refresh visuals
        entityRefresher.refreshPlayer(player);
    }

    @Override
    public boolean isDisguised(@NotNull UUID uniqueId) {
        return activeDisguises.containsKey(uniqueId);
    }

    @Override
    public @NotNull Optional<DisguiseProfile> getDisguise(@NotNull UUID uniqueId) {
        return Optional.ofNullable(activeDisguises.get(uniqueId));
    }

    @Override
    public @NotNull Optional<UUID> getRealUUID(@NotNull String disguisedName) {
        UUID uuid = nameIndex.get(disguisedName.toLowerCase(Locale.ROOT));
        return Optional.ofNullable(uuid);
    }

    @Override
    public @NotNull Optional<String> getRealName(@NotNull String disguisedName) {
        return getRealUUID(disguisedName).map(uuid -> {
            DisguiseProfile p = activeDisguises.get(uuid);
            if (p != null) return p.getRealName();
            Player player = Bukkit.getPlayer(uuid);
            return player != null ? player.getName() : null;
        });
    }

    @Override
    public @NotNull Optional<String> getDisguisedName(@NotNull UUID uniqueId) {
        DisguiseProfile p = activeDisguises.get(uniqueId);
        return p != null ? Optional.of(p.getDisguisedName()) : Optional.empty();
    }

    @Override
    public @NotNull CompletableFuture<NickResult> disguise(@NotNull UUID uniqueId, @NotNull String disguisedName, @Nullable SkinData skin, @Nullable String rank) {
        Player player = Bukkit.getPlayer(uniqueId);
        String realName = player != null ? player.getName() : "Unknown";

        if (!namePoolManager.isValidName(disguisedName)) {
            return CompletableFuture.completedFuture(NickResult.INVALID_NAME);
        }

        String chosenRank = (rank != null && !rank.isBlank()) ? rank : config.getDefaultRank();
        UltraNickConfig.RankConfig rankConfig = config.getRanks().getOrDefault(chosenRank, new UltraNickConfig.RankConfig("&7", "", "&7"));
        SkinData chosenSkin = (skin != null) ? skin : skinManager.getRandomSkin();

        DisguiseProfile profile = DisguiseProfile.builder()
                .uniqueId(uniqueId)
                .realName(realName)
                .disguisedName(disguisedName)
                .skin(chosenSkin)
                .rank(chosenRank)
                .prefix(rankConfig.getPrefix())
                .suffix(rankConfig.getSuffix())
                .chatColor(rankConfig.getChatColor())
                .disguiseTime(System.currentTimeMillis())
                .active(true)
                .build();

        return disguise(profile);
    }

    @Override
    public @NotNull CompletableFuture<NickResult> disguise(@NotNull DisguiseProfile profile) {
        CompletableFuture<NickResult> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            applyDisguiseLocally(profile);

            // Save to storage and publish to Redis
            storageManager.saveDisguise(profile);
            RedisDisguiseEvent redisEvent = RedisDisguiseEvent.disguise(profile, "paper");
            storageManager.publishSyncEvent(UltraJson.toJson(redisEvent));

            future.complete(NickResult.SUCCESS);
        });

        return future;
    }

    @Override
    public @NotNull CompletableFuture<NickResult> disguiseRandom(@NotNull UUID uniqueId) {
        String randomName = namePoolManager.getRandomAvailableName(name ->
                Bukkit.getPlayerExact(name) != null || nameIndex.containsKey(name.toLowerCase(Locale.ROOT))
        );
        SkinData randomSkin = skinManager.getRandomSkin();
        return disguise(uniqueId, randomName, randomSkin, config.getDefaultRank());
    }

    @Override
    public @NotNull CompletableFuture<NickResult> undisguise(@NotNull UUID uniqueId) {
        if (!activeDisguises.containsKey(uniqueId)) {
            return CompletableFuture.completedFuture(NickResult.NOT_NICKED);
        }

        CompletableFuture<NickResult> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            undisguiseLocally(uniqueId);

            // Delete from storage and publish to Redis
            storageManager.deleteDisguise(uniqueId);
            RedisDisguiseEvent redisEvent = RedisDisguiseEvent.undisguise(uniqueId, "paper");
            storageManager.publishSyncEvent(UltraJson.toJson(redisEvent));

            future.complete(NickResult.SUCCESS);
        });

        return future;
    }

    @Override
    public @NotNull Collection<DisguiseProfile> getActiveDisguises() {
        return Collections.unmodifiableCollection(activeDisguises.values());
    }

    @Override
    public @NotNull String getRandomNickName() {
        return namePoolManager.getRandomName();
    }

    @Override
    public @NotNull SkinData getRandomSkin() {
        return skinManager.getRandomSkin();
    }

    @Override
    public @NotNull Optional<SkinData> getSkin(@NotNull String skinName) {
        return skinManager.getSkin(skinName);
    }
}
