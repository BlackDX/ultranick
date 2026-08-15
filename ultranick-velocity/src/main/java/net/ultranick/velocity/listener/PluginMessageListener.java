package net.ultranick.velocity.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import net.ultranick.velocity.manager.VelocityNickManager;
import net.ultranick.velocity.protocol.PluginMessageDispatcher;

import java.util.UUID;
import java.util.logging.Logger;

public final class PluginMessageListener {

    private final VelocityNickManager nickManager;
    private final PluginMessageDispatcher messageDispatcher;
    private final Logger logger;

    public PluginMessageListener(VelocityNickManager nickManager, PluginMessageDispatcher messageDispatcher, Logger logger) {
        this.nickManager = nickManager;
        this.messageDispatcher = messageDispatcher;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!PluginMessageDispatcher.CHANNEL.equals(event.getIdentifier()) && !PluginMessageDispatcher.SYNC_CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (event.getSource() instanceof ServerConnection serverConn) {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            String subChannel = in.readUTF();

            // Paper server asks for player nick info
            if ("REQUEST_SYNC".equalsIgnoreCase(subChannel)) {
                String uuidStr = in.readUTF();
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    nickManager.getDisguise(uuid).ifPresent(profile ->
                            messageDispatcher.sendApplyDisguiseToServer(serverConn, profile)
                    );
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
