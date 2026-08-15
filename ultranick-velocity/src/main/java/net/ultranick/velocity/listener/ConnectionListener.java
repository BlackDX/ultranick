package net.ultranick.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.ultranick.common.config.UltraNickConfig;
import net.ultranick.velocity.manager.VelocityNickManager;

import java.util.Objects;
import java.util.logging.Logger;

public final class ConnectionListener {

    private final VelocityNickManager nickManager;
    private final UltraNickConfig config;
    private final Logger logger;

    public ConnectionListener(VelocityNickManager nickManager, UltraNickConfig config, Logger logger) {
        this.nickManager = Objects.requireNonNull(nickManager, "nickManager cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();

        if (nickManager.isDisguised(player.getUniqueId())) {
            // Keep nick when switching servers
            nickManager.handleServerSwitch(player);
        } else if (config.isAutoDisguiseOnJoin() && player.hasPermission("ultranick.autonick")) {
            nickManager.disguiseRandom(player.getUniqueId());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (!config.isKeepDisguiseAcrossServers()) {
            nickManager.undisguise(player.getUniqueId());
        }
    }
}
