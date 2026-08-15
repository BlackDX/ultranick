package net.ultranick.common.redis;

import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.SkinData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * PubSub message payload for cross-server disguise synchronizations.
 *
 * @author Chatbxn
 */
public final class RedisDisguiseEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Action {
        DISGUISE,
        UNDISGUISE,
        SYNC_ALL
    }

    private final Action action;
    private final UUID uniqueId;
    private final String realName;
    private final String disguisedName;
    private final String skinName;
    private final String skinValue;
    private final String skinSignature;
    private final String rank;
    private final String prefix;
    private final String suffix;
    private final String chatColor;
    private final String serverOrigin;
    private final long timestamp;

    public RedisDisguiseEvent(
            @NotNull Action action,
            @NotNull UUID uniqueId,
            @Nullable String realName,
            @Nullable String disguisedName,
            @Nullable SkinData skin,
            @Nullable String rank,
            @Nullable String prefix,
            @Nullable String suffix,
            @Nullable String chatColor,
            @Nullable String serverOrigin
    ) {
        this.action = action;
        this.uniqueId = uniqueId;
        this.realName = realName != null ? realName : "";
        this.disguisedName = disguisedName != null ? disguisedName : "";
        this.skinName = skin != null ? skin.getName() : "";
        this.skinValue = skin != null ? skin.getValue() : "";
        this.skinSignature = skin != null ? skin.getSignature() : "";
        this.rank = rank != null ? rank : "Default";
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.chatColor = chatColor != null ? chatColor : "&7";
        this.serverOrigin = serverOrigin != null ? serverOrigin : "proxy";
        this.timestamp = System.currentTimeMillis();
    }

    public static RedisDisguiseEvent disguise(@NotNull DisguiseProfile profile, @Nullable String serverOrigin) {
        return new RedisDisguiseEvent(
                Action.DISGUISE,
                profile.getUniqueId(),
                profile.getRealName(),
                profile.getDisguisedName(),
                profile.getSkin(),
                profile.getRank(),
                profile.getPrefix(),
                profile.getSuffix(),
                profile.getChatColor(),
                serverOrigin
        );
    }

    public static RedisDisguiseEvent undisguise(@NotNull UUID uniqueId, @Nullable String serverOrigin) {
        return new RedisDisguiseEvent(
                Action.UNDISGUISE,
                uniqueId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                serverOrigin
        );
    }

    @NotNull
    public Action getAction() {
        return action;
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
    public SkinData toSkinData() {
        if (skinValue == null || skinValue.isEmpty()) {
            return null;
        }
        return new SkinData(
                (skinName != null && !skinName.isEmpty()) ? skinName : "custom",
                skinValue,
                skinSignature
        );
    }

    @NotNull
    public DisguiseProfile toDisguiseProfile() {
        return DisguiseProfile.builder()
                .uniqueId(uniqueId)
                .realName(realName)
                .disguisedName(disguisedName)
                .skin(toSkinData())
                .rank(rank)
                .prefix(prefix)
                .suffix(suffix)
                .chatColor(chatColor)
                .disguiseTime(timestamp)
                .active(action == Action.DISGUISE)
                .build();
    }

    public String getServerOrigin() {
        return serverOrigin;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
