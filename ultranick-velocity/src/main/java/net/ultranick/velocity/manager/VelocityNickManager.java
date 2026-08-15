package net.ultranick.velocity.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.ultranick.api.UltraNickAPI;
import net.ultranick.api.event.PlayerDisguiseEvent;
import net.ultranick.api.event.PlayerUndisguiseEvent;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.NickResult;
import net.ultranick.api.model.SkinData;
import net.ultranick.common.config.UltraNickConfig;
import net.ultranick.common.json.UltraJson;
import net.ultranick.common.name.NamePoolManager;
import net.ultranick.common.redis.RedisDisguiseEvent;
import net.ultranick.common.skin.PreloadedSkinManager;
import net.ultranick.common.storage.StorageManager;
import net.ultranick.velocity.protocol.PluginMessageDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VelocityNickManager implements UltraNickAPI {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final UltraNickConfig config;
    private final StorageManager storageManager;
    private final NamePoolManager namePoolManager;
    private final PreloadedSkinManager skinManager;
    private final PluginMessageDispatcher messageDispatcher;

    private final Map<UUID, DisguiseProfile> activeDisguises = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public VelocityNickManager(
            @NotNull Object plugin,
            @NotNull ProxyServer proxy,
            @NotNull Logger logger,
            @NotNull UltraNickConfig config,
            @NotNull StorageManager storageManager,
            @NotNull NamePoolManager namePoolManager,
            @NotNull PreloadedSkinManager skinManager,
            @NotNull PluginMessageDispatcher messageDispatcher
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.proxy = Objects.requireNonNull(proxy, "proxy cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.storageManager = Objects.requireNonNull(storageManager, "storageManager cannot be null");
        this.namePoolManager = Objects.requireNonNull(namePoolManager, "namePoolManager cannot be null");
        this.skinManager = Objects.requireNonNull(skinManager, "skinManager cannot be null");
        this.messageDispatcher = Objects.requireNonNull(messageDispatcher, "messageDispatcher cannot be null");
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

    public void handleRemoteRedisEvent(@NotNull String payload) {
        try {
            RedisDisguiseEvent event = UltraJson.fromJson(payload, RedisDisguiseEvent.class);
            if (event == null || "proxy".equalsIgnoreCase(event.getServerOrigin())) {
                return;
            }

            if (event.getAction() == RedisDisguiseEvent.Action.DISGUISE) {
                DisguiseProfile profile = event.toDisguiseProfile();
                activeDisguises.put(profile.getUniqueId(), profile);
                nameIndex.put(profile.getDisguisedName().toLowerCase(Locale.ROOT), profile.getUniqueId());

                // Send update to player's backend server
                proxy.getPlayer(profile.getUniqueId()).ifPresent(player ->
                        messageDispatcher.sendApplyDisguise(player, profile)
                );
            } else if (event.getAction() == RedisDisguiseEvent.Action.UNDISGUISE) {
                DisguiseProfile removed = activeDisguises.remove(event.getUniqueId());
                if (removed != null) {
                    nameIndex.remove(removed.getDisguisedName().toLowerCase(Locale.ROOT));
                }
                proxy.getPlayer(event.getUniqueId()).ifPresent(messageDispatcher::sendClearDisguise);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to handle Redis event: " + payload, e);
        }
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
            DisguiseProfile profile = activeDisguises.get(uuid);
            if (profile != null) {
                return profile.getRealName();
            }
            return proxy.getPlayer(uuid).map(Player::getUsername).orElse(null);
        });
    }

    @Override
    public @NotNull Optional<String> getDisguisedName(@NotNull UUID uniqueId) {
        DisguiseProfile profile = activeDisguises.get(uniqueId);
        return profile != null ? Optional.of(profile.getDisguisedName()) : Optional.empty();
    }

    @Override
    public @NotNull CompletableFuture<NickResult> disguise(@NotNull UUID uniqueId, @NotNull String disguisedName, @Nullable SkinData skin, @Nullable String rank) {
        Optional<Player> optPlayer = proxy.getPlayer(uniqueId);
        String realName = optPlayer.map(Player::getUsername).orElse("Unknown");

        // Check if name format is valid
        if (!namePoolManager.isValidName(disguisedName)) {
            return CompletableFuture.completedFuture(NickResult.INVALID_NAME);
        }

        // Check if name is already taken
        if (proxy.getPlayer(disguisedName).isPresent() && !disguisedName.equalsIgnoreCase(realName)) {
            return CompletableFuture.completedFuture(NickResult.NAME_TAKEN);
        }
        UUID existingUuid = nameIndex.get(disguisedName.toLowerCase(Locale.ROOT));
        if (existingUuid != null && !existingUuid.equals(uniqueId)) {
            return CompletableFuture.completedFuture(NickResult.NAME_TAKEN);
        }

        // Check cooldown
        if (isOnCooldown(uniqueId)) {
            return CompletableFuture.completedFuture(NickResult.COOLDOWN);
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
        PlayerDisguiseEvent event = new PlayerDisguiseEvent(profile.getUniqueId(), profile);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(NickResult.ERROR);
        }

        activeDisguises.put(profile.getUniqueId(), profile);
        nameIndex.put(profile.getDisguisedName().toLowerCase(Locale.ROOT), profile.getUniqueId());
        setCooldown(profile.getUniqueId());

        // Save to storage and publish to Redis
        storageManager.saveDisguise(profile);
        RedisDisguiseEvent redisEvent = RedisDisguiseEvent.disguise(profile, "proxy");
        storageManager.publishSyncEvent(UltraJson.toJson(redisEvent));

        // Send to backend server
        proxy.getPlayer(profile.getUniqueId()).ifPresent(player -> {
            messageDispatcher.sendApplyDisguise(player, profile);
            proxy.getScheduler().buildTask(plugin, () -> {
                if (player.isActive()) {
                    messageDispatcher.sendApplyDisguise(player, profile);
                }
            }).delay(300, TimeUnit.MILLISECONDS).schedule();
        });

        return CompletableFuture.completedFuture(NickResult.SUCCESS);
    }

    @Override
    public @NotNull CompletableFuture<NickResult> disguiseRandom(@NotNull UUID uniqueId) {
        String randomName = namePoolManager.getRandomAvailableName(name ->
                proxy.getPlayer(name).isPresent() || nameIndex.containsKey(name.toLowerCase(Locale.ROOT))
        );
        SkinData randomSkin = skinManager.getRandomSkin();
        return disguise(uniqueId, randomName, randomSkin, config.getDefaultRank());
    }

    @Override
    public @NotNull CompletableFuture<NickResult> undisguise(@NotNull UUID uniqueId) {
        DisguiseProfile removed = activeDisguises.remove(uniqueId);
        if (removed == null) {
            return CompletableFuture.completedFuture(NickResult.NOT_NICKED);
        }

        nameIndex.remove(removed.getDisguisedName().toLowerCase(Locale.ROOT));

        PlayerUndisguiseEvent event = new PlayerUndisguiseEvent(uniqueId, removed);
        if (event.isCancelled()) {
            activeDisguises.put(uniqueId, removed);
            nameIndex.put(removed.getDisguisedName().toLowerCase(Locale.ROOT), uniqueId);
            return CompletableFuture.completedFuture(NickResult.ERROR);
        }

        // Delete from storage and publish to Redis
        storageManager.deleteDisguise(uniqueId);
        RedisDisguiseEvent redisEvent = RedisDisguiseEvent.undisguise(uniqueId, "proxy");
        storageManager.publishSyncEvent(UltraJson.toJson(redisEvent));

        // Tell backend server to clear nick
        proxy.getPlayer(uniqueId).ifPresent(messageDispatcher::sendClearDisguise);

        return CompletableFuture.completedFuture(NickResult.SUCCESS);
    }

    @Override
    public @NotNull Collection<DisguiseProfile> getActiveDisguises() {
        return Collections.unmodifiableCollection(activeDisguises.values());
    }

    @Override
    public @NotNull String getRandomNickName() {
        return namePoolManager.getRandomAvailableName(name ->
                proxy.getPlayer(name).isPresent() || nameIndex.containsKey(name.toLowerCase(Locale.ROOT))
        );
    }

    @Override
    public @NotNull SkinData getRandomSkin() {
        return skinManager.getRandomSkin();
    }

    @Override
    public @NotNull Optional<SkinData> getSkin(@NotNull String skinName) {
        return skinManager.getSkin(skinName);
    }

    public boolean isOnCooldown(UUID uuid) {
        Long expireTime = cooldowns.get(uuid);
        if (expireTime == null) return false;
        if (System.currentTimeMillis() > expireTime) {
            cooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    public long getRemainingCooldownSeconds(UUID uuid) {
        Long expireTime = cooldowns.get(uuid);
        if (expireTime == null) return 0;
        long rem = (expireTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, rem);
    }

    public void setCooldown(UUID uuid) {
        if (config.getCommandCooldownSeconds() > 0) {
            cooldowns.put(uuid, System.currentTimeMillis() + (config.getCommandCooldownSeconds() * 1000L));
        }
    }

    public void handleServerSwitch(Player player) {
        DisguiseProfile profile = activeDisguises.get(player.getUniqueId());
        if (profile != null && profile.isActive()) {
            // Send immediately and with delay to make sure backend gets it
            messageDispatcher.sendApplyDisguise(player, profile);

            proxy.getScheduler().buildTask(plugin, () -> {
                if (player.isActive()) {
                    messageDispatcher.sendApplyDisguise(player, profile);
                }
            }).delay(250, TimeUnit.MILLISECONDS).schedule();

            proxy.getScheduler().buildTask(plugin, () -> {
                if (player.isActive()) {
                    messageDispatcher.sendApplyDisguise(player, profile);
                }
            }).delay(750, TimeUnit.MILLISECONDS).schedule();
        }
    }
}
