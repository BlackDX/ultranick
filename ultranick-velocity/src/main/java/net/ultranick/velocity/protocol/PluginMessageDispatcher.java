package net.ultranick.velocity.protocol;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.SkinData;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public final class PluginMessageDispatcher {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("ultranick:channel");
    public static final MinecraftChannelIdentifier SYNC_CHANNEL = MinecraftChannelIdentifier.from("ultranick:sync");

    private final Logger logger;

    public PluginMessageDispatcher(Logger logger) {
        this.logger = logger;
    }

    // Send disguise data to the player's current server
    public void sendApplyDisguise(@NotNull Player player, @NotNull DisguiseProfile profile) {
        player.getCurrentServer().ifPresent(serverConn -> {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("APPLY");
            out.writeUTF(profile.getUniqueId().toString());
            out.writeUTF(profile.getRealName());
            out.writeUTF(profile.getDisguisedName());

            SkinData skin = profile.getSkin();
            out.writeUTF(skin != null ? skin.getName() : "Steve");
            out.writeUTF(skin != null ? skin.getValue() : "");
            out.writeUTF((skin != null && skin.getSignature() != null) ? skin.getSignature() : "");

            out.writeUTF(profile.getRank());
            out.writeUTF(profile.getPrefix());
            out.writeUTF(profile.getSuffix());
            out.writeUTF(profile.getChatColor());

            serverConn.sendPluginMessage(CHANNEL, out.toByteArray());
        });
    }

    // Send clear disguise to the player's current server
    public void sendClearDisguise(@NotNull Player player) {
        player.getCurrentServer().ifPresent(serverConn -> {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("CLEAR");
            out.writeUTF(player.getUniqueId().toString());
            serverConn.sendPluginMessage(CHANNEL, out.toByteArray());
        });
    }

    // Send disguise data directly to a specific backend server connection
    public void sendApplyDisguiseToServer(@NotNull ServerConnection serverConn, @NotNull DisguiseProfile profile) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("APPLY");
        out.writeUTF(profile.getUniqueId().toString());
        out.writeUTF(profile.getRealName());
        out.writeUTF(profile.getDisguisedName());

        SkinData skin = profile.getSkin();
        out.writeUTF(skin != null ? skin.getName() : "Steve");
        out.writeUTF(skin != null ? skin.getValue() : "");
        out.writeUTF((skin != null && skin.getSignature() != null) ? skin.getSignature() : "");

        out.writeUTF(profile.getRank());
        out.writeUTF(profile.getPrefix());
        out.writeUTF(profile.getSuffix());
        out.writeUTF(profile.getChatColor());

        serverConn.sendPluginMessage(CHANNEL, out.toByteArray());
    }
}
