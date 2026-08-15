package net.ultranick.api.event;

import net.ultranick.api.model.DisguiseProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Event triggered when a player un-disguises / removes their disguise.
 *
 * @author Chatbxn
 */
public class PlayerUndisguiseEvent implements UltraNickEvent {
    private static final long serialVersionUID = 1L;

    private final UUID uniqueId;
    private final DisguiseProfile previousProfile;
    private boolean cancelled;

    public PlayerUndisguiseEvent(@NotNull UUID uniqueId, @Nullable DisguiseProfile previousProfile) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId cannot be null");
        this.previousProfile = previousProfile;
        this.cancelled = false;
    }

    @NotNull
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Nullable
    public DisguiseProfile getPreviousProfile() {
        return previousProfile;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
