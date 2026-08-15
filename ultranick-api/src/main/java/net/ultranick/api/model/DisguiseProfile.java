package net.ultranick.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a player's disguise profile.
 * Holds all disguise data including nickname, skin, rank disguise, prefix/suffix and timestamps.
 *
 * @author Chatbxn
 */
public final class DisguiseProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID uniqueId;
    private final String realName;
    private final String disguisedName;
    private final SkinData skin;
    private final String rank;
    private final String prefix;
    private final String suffix;
    private final String chatColor;
    private final long disguiseTime;
    private final boolean active;

    private DisguiseProfile(Builder builder) {
        this.uniqueId = Objects.requireNonNull(builder.uniqueId, "uniqueId cannot be null");
        this.realName = Objects.requireNonNull(builder.realName, "realName cannot be null");
        this.disguisedName = Objects.requireNonNull(builder.disguisedName, "disguisedName cannot be null");
        this.skin = builder.skin;
        this.rank = builder.rank != null ? builder.rank : "Default";
        this.prefix = builder.prefix != null ? builder.prefix : "";
        this.suffix = builder.suffix != null ? builder.suffix : "";
        this.chatColor = builder.chatColor != null ? builder.chatColor : "&7";
        this.disguiseTime = builder.disguiseTime > 0 ? builder.disguiseTime : System.currentTimeMillis();
        this.active = builder.active;
    }

    @NotNull
    public UUID getUniqueId() {
        return uniqueId;
    }

    @NotNull
    public String getRealName() {
        return realName;
    }

    @NotNull
    public String getDisguisedName() {
        return disguisedName;
    }

    @Nullable
    public SkinData getSkin() {
        return skin;
    }

    @NotNull
    public String getRank() {
        return rank;
    }

    @NotNull
    public String getPrefix() {
        return prefix;
    }

    @NotNull
    public String getSuffix() {
        return suffix;
    }

    @NotNull
    public String getChatColor() {
        return chatColor;
    }

    public long getDisguiseTime() {
        return disguiseTime;
    }

    public boolean isActive() {
        return active;
    }

    @NotNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID uniqueId;
        private String realName;
        private String disguisedName;
        private SkinData skin;
        private String rank = "Default";
        private String prefix = "";
        private String suffix = "";
        private String chatColor = "&7";
        private long disguiseTime = System.currentTimeMillis();
        private boolean active = true;

        public Builder() {}

        public Builder(DisguiseProfile profile) {
            this.uniqueId = profile.uniqueId;
            this.realName = profile.realName;
            this.disguisedName = profile.disguisedName;
            this.skin = profile.skin;
            this.rank = profile.rank;
            this.prefix = profile.prefix;
            this.suffix = profile.suffix;
            this.chatColor = profile.chatColor;
            this.disguiseTime = profile.disguiseTime;
            this.active = profile.active;
        }

        public Builder uniqueId(@NotNull UUID uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }

        public Builder realName(@NotNull String realName) {
            this.realName = realName;
            return this;
        }

        public Builder disguisedName(@NotNull String disguisedName) {
            this.disguisedName = disguisedName;
            return this;
        }

        public Builder skin(@Nullable SkinData skin) {
            this.skin = skin;
            return this;
        }

        public Builder rank(@Nullable String rank) {
            this.rank = rank;
            return this;
        }

        public Builder prefix(@Nullable String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder suffix(@Nullable String suffix) {
            this.suffix = suffix;
            return this;
        }

        public Builder chatColor(@Nullable String chatColor) {
            this.chatColor = chatColor;
            return this;
        }

        public Builder disguiseTime(long disguiseTime) {
            this.disguiseTime = disguiseTime;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        @NotNull
        public DisguiseProfile build() {
            return new DisguiseProfile(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisguiseProfile that = (DisguiseProfile) o;
        return active == that.active &&
                Objects.equals(uniqueId, that.uniqueId) &&
                Objects.equals(realName, that.realName) &&
                Objects.equals(disguisedName, that.disguisedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueId, realName, disguisedName, active);
    }

    @Override
    public String toString() {
        return "DisguiseProfile{" +
                "uniqueId=" + uniqueId +
                ", realName='" + realName + '\'' +
                ", disguisedName='" + disguisedName + '\'' +
                ", rank='" + rank + '\'' +
                ", active=" + active +
                '}';
    }
}
