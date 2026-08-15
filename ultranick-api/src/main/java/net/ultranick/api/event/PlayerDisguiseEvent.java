package net.ultranick.api.event;

import net.ultranick.api.model.DisguiseProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Event triggered when a player disguises or their disguise changes.
 *
 * @author Chatbxn
 */
public class PlayerDisguiseEvent implements UltraNickEvent {
    private static final long serialVersionUID = 1L;

    private final UUID uniqueId;
    private final DisguiseProfile disguiseProfile;
    private boolean cancelled;

    public PlayerDisguiseEvent(@NotNull UUID uniqueId, @NotNull DisguiseProfile disguiseProfile) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId cannot be null");
        this.disguiseProfile = Objects.requireNonNull(disguiseProfile, "disguiseProfile cannot be null");
        this.cancelled = false;
    }

    @NotNull
    public UUID getUniqueId() {
        return uniqueId;
    }

    @NotNull
    public DisguiseProfile getDisguiseProfile() {
        return disguiseProfile;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
