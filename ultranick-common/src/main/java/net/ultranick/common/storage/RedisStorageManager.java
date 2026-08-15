package net.ultranick.common.storage;

import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.common.json.UltraJson;
import net.ultranick.common.redis.UltraRedisClient;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cross-server Redis storage implementation.
 * Stores profiles in Redis keys and synchronizes updates in real-time via Pub/Sub.
 *
 * @author Chatbxn
 */
public class RedisStorageManager implements StorageManager {

    private static final Logger LOGGER = Logger.getLogger("UltraNick-RedisStorage");

    private final UltraRedisClient redisClient;
    private final String channel;
    private final String keyPrefix;
    private final ExecutorService asyncExecutor;

    public RedisStorageManager(@NotNull UltraRedisClient redisClient, @NotNull String channel, @NotNull String keyPrefix) {
        this.redisClient = Objects.requireNonNull(redisClient, "redisClient cannot be null");
        this.channel = (channel != null && !channel.isBlank()) ? channel : "ultranick:events";
        this.keyPrefix = (keyPrefix != null && !keyPrefix.isBlank()) ? keyPrefix : "ultranick:";
        this.asyncExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    private String getPlayerKey(UUID uuid) {
        return keyPrefix + "player:" + uuid.toString();
    }

    private String getNameIndexKey(String disguisedName) {
        return keyPrefix + "name_index:" + disguisedName.toLowerCase(Locale.ROOT);
    }

    private String getActiveSetKey() {
        return keyPrefix + "active_players";
    }

    @Override
    public CompletableFuture<Void> saveDisguise(@NotNull DisguiseProfile profile) {
        return CompletableFuture.runAsync(() -> {
            try {
                String playerKey = getPlayerKey(profile.getUniqueId());
                String nameKey = getNameIndexKey(profile.getDisguisedName());
                String json = UltraJson.toJson(profile);

                redisClient.set(playerKey, json);
                redisClient.set(nameKey, profile.getUniqueId().toString());
                redisClient.execute("SADD", getActiveSetKey(), profile.getUniqueId().toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to save disguise profile to Redis for " + profile.getUniqueId(), e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> deleteDisguise(@NotNull UUID uniqueId) {
        return CompletableFuture.runAsync(() -> {
            try {
                String playerKey = getPlayerKey(uniqueId);
                String oldJson = redisClient.get(playerKey);
                if (oldJson != null) {
                    try {
                        DisguiseProfile oldProfile = UltraJson.fromJson(oldJson, DisguiseProfile.class);
                        if (oldProfile != null) {
                            redisClient.del(getNameIndexKey(oldProfile.getDisguisedName()));
                        }
                    } catch (Exception ignored) {}
                }
                redisClient.del(playerKey);
                redisClient.execute("SREM", getActiveSetKey(), uniqueId.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to delete disguise profile from Redis for " + uniqueId, e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<DisguiseProfile>> loadDisguise(@NotNull UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = redisClient.get(getPlayerKey(uniqueId));
                if (json == null || json.isBlank()) {
                    return Optional.empty();
                }
                DisguiseProfile profile = UltraJson.fromJson(json, DisguiseProfile.class);
                return Optional.ofNullable(profile);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to load disguise from Redis for " + uniqueId, e);
                return Optional.empty();
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<UUID>> findRealUUIDByDisguisedName(@NotNull String disguisedName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String uuidStr = redisClient.get(getNameIndexKey(disguisedName));
                if (uuidStr == null || uuidStr.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(UUID.fromString(uuidStr));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to find UUID by disguised name in Redis: " + disguisedName);
                return Optional.empty();
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Collection<DisguiseProfile>> loadAllActiveDisguises() {
        return CompletableFuture.supplyAsync(() -> {
            List<DisguiseProfile> list = new ArrayList<>();
            try {
                Set<String> keys = redisClient.keys(keyPrefix + "player:*");
                for (String key : keys) {
                    String json = redisClient.get(key);
                    if (json != null && !json.isBlank()) {
                        DisguiseProfile profile = UltraJson.fromJson(json, DisguiseProfile.class);
                        if (profile != null && profile.isActive()) {
                            list.add(profile);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to load all active disguises from Redis", e);
            }
            return Collections.unmodifiableList(list);
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> isNameTaken(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return redisClient.exists(getNameIndexKey(name));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to check if name is taken in Redis: " + name, e);
                return false;
            }
        }, asyncExecutor);
    }

    @Override
    public void publishSyncEvent(@NotNull String eventJson) {
        asyncExecutor.submit(() -> {
            try {
                redisClient.publish(channel, eventJson);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to publish sync event to Redis channel " + channel, e);
            }
        });
    }

    @Override
    public void close() {
        asyncExecutor.shutdown();
        redisClient.close();
    }
}
