package net.ultranick.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.ultranick.api.UltraNickAPI;
import net.ultranick.common.config.UltraNickConfig;
import net.ultranick.common.http.UltraNickHttpServer;
import net.ultranick.common.name.NamePoolManager;
import net.ultranick.common.redis.UltraRedisClient;
import net.ultranick.common.skin.PreloadedSkinManager;
import net.ultranick.common.storage.MemoryStorageManager;
import net.ultranick.common.storage.RedisStorageManager;
import net.ultranick.common.storage.StorageManager;
import net.ultranick.velocity.command.*;
import net.ultranick.velocity.config.VelocityConfigManager;
import net.ultranick.velocity.listener.ConnectionListener;
import net.ultranick.velocity.listener.PluginMessageListener;
import net.ultranick.velocity.manager.VelocityNickManager;
import net.ultranick.velocity.protocol.PluginMessageDispatcher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(
        id = "ultranick",
        name = "UltraNick",
        version = "1.0.0",
        authors = {"Chatbxn"},
        description = "High-Performance Server-Wide Disguise and Nickname System"
)
public final class UltraNickVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityConfigManager configManager;
    private StorageManager storageManager;
    private UltraRedisClient redisClient;
    private NamePoolManager namePoolManager;
    private PreloadedSkinManager skinManager;
    private PluginMessageDispatcher messageDispatcher;
    private VelocityNickManager nickManager;
    private UltraNickHttpServer httpServer;

    @Inject
    public UltraNickVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Initializing UltraNick Velocity proxy plugin...");

        // Load configs
        this.configManager = new VelocityConfigManager(dataDirectory, logger);
        configManager.load();
        UltraNickConfig config = configManager.getConfig();

        // Initialize managers
        this.namePoolManager = new NamePoolManager(config.getNicknames(), config.getBlacklist());
        this.skinManager = new PreloadedSkinManager();

        // Storage setup
        if (config.isRedisEnabled()) {
            try {
                this.redisClient = new UltraRedisClient(
                        config.getRedisHost(),
                        config.getRedisPort(),
                        config.getRedisPassword(),
                        config.getRedisDatabase(),
                        config.getRedisTimeout(),
                        8
                );
                this.storageManager = new RedisStorageManager(redisClient, config.getRedisChannel(), config.getRedisKeyPrefix());
                logger.info("Redis storage connected.");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to connect to Redis, using in-memory storage: " + e.getMessage());
                this.storageManager = new MemoryStorageManager();
            }
        } else {
            this.storageManager = new MemoryStorageManager();
        }

        // Messaging channels
        this.messageDispatcher = new PluginMessageDispatcher(logger);
        proxy.getChannelRegistrar().register(PluginMessageDispatcher.CHANNEL, PluginMessageDispatcher.SYNC_CHANNEL);

        // Nick manager
        this.nickManager = new VelocityNickManager(
                this,
                proxy,
                logger,
                config,
                storageManager,
                namePoolManager,
                skinManager,
                messageDispatcher
        );

        UltraNickAPI.setInstance(nickManager);

        if (redisClient != null) {
            redisClient.subscribe(config.getRedisChannel(), nickManager::handleRemoteRedisEvent);
        }

        nickManager.loadInitialState();

        // Register listeners
        proxy.getEventManager().register(this, new ConnectionListener(nickManager, config, logger));
        proxy.getEventManager().register(this, new PluginMessageListener(nickManager, messageDispatcher, logger));

        // Register commands
        var cmdManager = proxy.getCommandManager();
        cmdManager.register(cmdManager.metaBuilder("nick").plugin(this).build(), new NickCommand(proxy, nickManager, configManager.getMessageService()));
        cmdManager.register(cmdManager.metaBuilder("unnick").plugin(this).build(), new UnnickCommand(proxy, nickManager, configManager.getMessageService()));
        cmdManager.register(cmdManager.metaBuilder("realname").plugin(this).build(), new RealNameCommand(nickManager, configManager.getMessageService()));
        cmdManager.register(cmdManager.metaBuilder("nicklist").plugin(this).build(), new NickListCommand(nickManager, configManager.getMessageService()));
        cmdManager.register(cmdManager.metaBuilder("ultranick").aliases("unick").plugin(this).build(), new UltraNickCommand(configManager, nickManager, configManager.getMessageService()));

        // REST API
        if (config.isApiEnabled()) {
            try {
                this.httpServer = new UltraNickHttpServer(config.getApiHost(), config.getApiPort(), config.getApiAuthToken(), nickManager);
                httpServer.start();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to start REST API on port " + config.getApiPort(), e);
            }
        }

        logger.info("UltraNick Velocity started!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down UltraNick Velocity...");

        if (httpServer != null) {
            httpServer.close();
        }
        if (storageManager != null) {
            storageManager.close();
        }
        if (redisClient != null) {
            redisClient.close();
        }

        logger.info("UltraNick Velocity shutdown completed.");
    }
}
