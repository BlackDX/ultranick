package net.ultranick.api;

import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.NickResult;
import net.ultranick.api.model.SkinData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * UltraNick developer API entry point.
 * Accessible from anywhere via {@link UltraNickAPI#get()}.
 *
 * @author Chatbxn
 */
public interface UltraNickAPI {

    /**
     * Singleton instance holder.
     */
    class Holder {
        private static UltraNickAPI instance;
    }

    /**
     * Get the active UltraNick API instance.
     *
     * @return UltraNickAPI instance
     * @throws IllegalStateException if the API is not yet initialized
     */
    @NotNull
    static UltraNickAPI get() {
        UltraNickAPI instance = Holder.instance;
        if (instance == null) {
            throw new IllegalStateException("UltraNick API has not been initialized yet!");
        }
        return instance;
    }

    /**
     * Set the global API instance. Internal use only.
     *
     * @param api UltraNickAPI implementation
     */
    static void setInstance(@NotNull UltraNickAPI api) {
        Holder.instance = api;
    }

    /**
     * Checks if a player is currently nicked/disguised.
     *
     * @param uniqueId Player's real UUID
     * @return true if nicked, false otherwise
     */
    boolean isDisguised(@NotNull UUID uniqueId);

    /**
     * Gets the disguise profile of a player by their real UUID.
     *
     * @param uniqueId Player's real UUID
     * @return Optional containing DisguiseProfile if disguised
     */
    @NotNull
    Optional<DisguiseProfile> getDisguise(@NotNull UUID uniqueId);

    /**
     * Looks up the real UUID of a player given their disguised nickname.
     *
     * @param disguisedName Disguised name to lookup
     * @return Optional containing real UUID if found
     */
    @NotNull
    Optional<UUID> getRealUUID(@NotNull String disguisedName);

    /**
     * Looks up the real name of a player given their disguised nickname.
     *
     * @param disguisedName Disguised name to lookup
     * @return Optional containing real username if found
     */
    @NotNull
    Optional<String> getRealName(@NotNull String disguisedName);

    /**
     * Gets the disguised name of a player by their real UUID.
     *
     * @param uniqueId Player's real UUID
     * @return Optional containing disguised name if active
     */
    @NotNull
    Optional<String> getDisguisedName(@NotNull UUID uniqueId);

    /**
     * Disguises a player with custom nickname, skin, and rank.
     *
     * @param uniqueId Player's real UUID
     * @param disguisedName Chosen nickname
     * @param skin Optional custom skin data
     * @param rank Optional rank name to disguise as
     * @return CompletableFuture containing NickResult
     */
    @NotNull
    CompletableFuture<NickResult> disguise(@NotNull UUID uniqueId, @NotNull String disguisedName, @Nullable SkinData skin, @Nullable String rank);

    /**
     * Disguises a player with a full disguise profile.
     *
     * @param profile Disguise profile
     * @return CompletableFuture containing NickResult
     */
    @NotNull
    CompletableFuture<NickResult> disguise(@NotNull DisguiseProfile profile);

    /**
     * Disguises a player with a random nickname and skin.
     *
     * @param uniqueId Player's real UUID
     * @return CompletableFuture containing NickResult
     */
    @NotNull
    CompletableFuture<NickResult> disguiseRandom(@NotNull UUID uniqueId);

    /**
     * Removes disguise from a player, restoring their original identity.
     *
     * @param uniqueId Player's real UUID
     * @return CompletableFuture containing NickResult
     */
    @NotNull
    CompletableFuture<NickResult> undisguise(@NotNull UUID uniqueId);

    /**
     * Retrieves all currently active disguises across the network.
     *
     * @return Unmodifiable collection of active disguise profiles
     */
    @NotNull
    Collection<DisguiseProfile> getActiveDisguises();

    /**
     * Returns a random available nickname from the internal name pool.
     *
     * @return Random nickname
     */
    @NotNull
    String getRandomNickName();

    /**
     * Returns a random preloaded skin from the internal high-performance skin pool.
     *
     * @return Random SkinData
     */
    @NotNull
    SkinData getRandomSkin();

    /**
     * Gets a preloaded skin by name (e.g. "Steve", "Alex", etc.).
     *
     * @param skinName Name of skin
     * @return Optional containing SkinData if available
     */
    @NotNull
    Optional<SkinData> getSkin(@NotNull String skinName);
}
