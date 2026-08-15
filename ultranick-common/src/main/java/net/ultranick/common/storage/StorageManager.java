package net.ultranick.common.storage;

import net.ultranick.api.model.DisguiseProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Storage interface for storing, retrieving and persisting player disguise profiles.
 *
 * @author Chatbxn
 */
public interface StorageManager extends AutoCloseable {

    /**
     * Saves or updates a disguise profile in the storage backend.
     *
     * @param profile Disguise profile to save
     * @return CompletableFuture completing when saved
     */
    CompletableFuture<Void> saveDisguise(@NotNull DisguiseProfile profile);

    /**
     * Removes a player's disguise from the storage backend.
     *
     * @param uniqueId Player's real UUID
     * @return CompletableFuture completing when deleted
     */
    CompletableFuture<Void> deleteDisguise(@NotNull UUID uniqueId);

    /**
     * Retrieves a disguise profile from storage by real UUID.
     *
     * @param uniqueId Player's real UUID
     * @return CompletableFuture containing Optional DisguiseProfile
     */
    CompletableFuture<Optional<DisguiseProfile>> loadDisguise(@NotNull UUID uniqueId);

    /**
     * Looks up the real UUID by disguised name.
     *
     * @param disguisedName Disguised name
     * @return CompletableFuture containing Optional UUID
     */
    CompletableFuture<Optional<UUID>> findRealUUIDByDisguisedName(@NotNull String disguisedName);

    /**
     * Loads all active disguises from storage.
     *
     * @return CompletableFuture containing collection of active profiles
     */
    CompletableFuture<Collection<DisguiseProfile>> loadAllActiveDisguises();

    /**
     * Checks if a nickname is currently taken in storage.
     *
     * @param name Nickname to test
     * @return CompletableFuture boolean
     */
    CompletableFuture<Boolean> isNameTaken(@NotNull String name);

    /**
     * Publishes a cross-server disguise event.
     *
     * @param eventJson JSON representation of event
     */
    void publishSyncEvent(@NotNull String eventJson);

    @Override
    void close();
}
