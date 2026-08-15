package net.ultranick.common.storage;

import net.ultranick.api.model.DisguiseProfile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-speed in-memory storage manager.
 * Used for standalone servers or local cache backing.
 *
 * @author Chatbxn
 */
public class MemoryStorageManager implements StorageManager {

    private final Map<UUID, DisguiseProfile> activeDisguises = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuidIndex = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> saveDisguise(@NotNull DisguiseProfile profile) {
        activeDisguises.put(profile.getUniqueId(), profile);
        nameToUuidIndex.put(profile.getDisguisedName().toLowerCase(Locale.ROOT), profile.getUniqueId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteDisguise(@NotNull UUID uniqueId) {
        DisguiseProfile removed = activeDisguises.remove(uniqueId);
        if (removed != null) {
            nameToUuidIndex.remove(removed.getDisguisedName().toLowerCase(Locale.ROOT));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<DisguiseProfile>> loadDisguise(@NotNull UUID uniqueId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(activeDisguises.get(uniqueId)));
    }

    @Override
    public CompletableFuture<Optional<UUID>> findRealUUIDByDisguisedName(@NotNull String disguisedName) {
        UUID uuid = nameToUuidIndex.get(disguisedName.toLowerCase(Locale.ROOT));
        return CompletableFuture.completedFuture(Optional.ofNullable(uuid));
    }

    @Override
    public CompletableFuture<Collection<DisguiseProfile>> loadAllActiveDisguises() {
        return CompletableFuture.completedFuture(Collections.unmodifiableCollection(new ArrayList<>(activeDisguises.values())));
    }

    @Override
    public CompletableFuture<Boolean> isNameTaken(@NotNull String name) {
        boolean taken = nameToUuidIndex.containsKey(name.toLowerCase(Locale.ROOT));
        return CompletableFuture.completedFuture(taken);
    }

    @Override
    public void publishSyncEvent(@NotNull String eventJson) {
        // No-op for pure memory storage
    }

    @Override
    public void close() {
        activeDisguises.clear();
        nameToUuidIndex.clear();
    }
}
