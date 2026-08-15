package net.ultranick.paper.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.SkinData;
import net.ultranick.paper.manager.PaperNickManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaperPluginMessageListener implements PluginMessageListener {

    private final PaperNickManager nickManager;
    private final Logger logger;

    public PaperPluginMessageListener(PaperNickManager nickManager, Logger logger) {
        this.nickManager = nickManager;
        this.logger = logger;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!"ultranick:channel".equals(channel) && !"ultranick:sync".equals(channel)) {
            return;
        }

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subChannel = in.readUTF();

            if ("APPLY".equalsIgnoreCase(subChannel)) {
                String uuidStr = in.readUTF();
                String realName = in.readUTF();
                String disguisedName = in.readUTF();
                String skinName = in.readUTF();
                String skinValue = in.readUTF();
                String skinSignature = in.readUTF();
                String rank = in.readUTF();
                String prefix = in.readUTF();
                String suffix = in.readUTF();
                String chatColor = in.readUTF();

                UUID uuid = UUID.fromString(uuidStr);
                SkinData skin = (!skinValue.isEmpty()) ? new SkinData(skinName, skinValue, skinSignature) : null;

                DisguiseProfile profile = DisguiseProfile.builder()
                        .uniqueId(uuid)
                        .realName(realName)
                        .disguisedName(disguisedName)
                        .skin(skin)
                        .rank(rank)
                        .prefix(prefix)
                        .suffix(suffix)
                        .chatColor(chatColor)
                        .disguiseTime(System.currentTimeMillis())
                        .active(true)
                        .build();

                nickManager.applyDisguiseLocally(profile);
            } else if ("CLEAR".equalsIgnoreCase(subChannel)) {
                String uuidStr = in.readUTF();
                UUID uuid = UUID.fromString(uuidStr);
                nickManager.undisguiseLocally(uuid);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reading plugin message: " + e.getMessage());
        }
    }
}
