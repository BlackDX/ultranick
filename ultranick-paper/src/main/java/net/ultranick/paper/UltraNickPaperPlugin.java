package net.ultranick.paper;

import net.ultranick.api.UltraNickAPI;
import net.ultranick.common.config.UltraNickConfig;
import net.ultranick.common.name.NamePoolManager;
import net.ultranick.common.redis.UltraRedisClient;
import net.ultranick.common.skin.PreloadedSkinManager;
import net.ultranick.common.storage.MemoryStorageManager;
import net.ultranick.common.storage.RedisStorageManager;
import net.ultranick.common.storage.StorageManager;
import net.ultranick.paper.command.PaperNickCommand;
import net.ultranick.paper.listener.PaperChatListener;
import net.ultranick.paper.listener.PaperMessageListener;
import net.ultranick.paper.listener.PaperPluginMessageListener;
import net.ultranick.paper.manager.PaperNickManager;
import net.ultranick.paper.nametag.PaperNametagManager;
import net.ultranick.paper.refresh.PaperEntityRefresher;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public final class UltraNickPaperPlugin extends JavaPlugin {

    private UltraNickConfig config;
    private StorageManager storageManager;
    private UltraRedisClient redisClient;
    private NamePoolManager namePoolManager;
    private PreloadedSkinManager skinManager;
    private PaperNametagManager nametagManager;
    private PaperEntityRefresher entityRefresher;
    private PaperNickManager nickManager;

    @Override
    public void onEnable() {
        getLogger().info("Starting UltraNick Paper backend by Chatbxn...");

        saveDefaultConfig();
        loadConfiguration();

        // Initialize managers
        this.namePoolManager = new NamePoolManager(config.getNicknames(), config.getBlacklist());
        this.skinManager = new PreloadedSkinManager();
        this.nametagManager = new PaperNametagManager();
        this.entityRefresher = new PaperEntityRefresher(this);

        // Storage setup
        if (config.isRedisEnabled()) {
            try {
                this.redisClient = new UltraRedisClient(
                        config.getRedisHost(),
                        config.getRedisPort(),
                        config.getRedisPassword(),
                        config.getRedisDatabase(),
                        config.getRedisTimeout(),
                        4
                );
                this.storageManager = new RedisStorageManager(redisClient, config.getRedisChannel(), config.getRedisKeyPrefix());
                getLogger().info("Connected to Redis for backend sync.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to connect to Redis, fallback to memory storage: " + e.getMessage());
                this.storageManager = new MemoryStorageManager();
            }
        } else {
            this.storageManager = new MemoryStorageManager();
        }

        // Nick manager
        this.nickManager = new PaperNickManager(
                this,
                config,
                storageManager,
                namePoolManager,
                skinManager,
                nametagManager,
                entityRefresher
        );

        this.nickManager.loadInitialState();
        UltraNickAPI.setInstance(nickManager);

        if (redisClient != null) {
            redisClient.subscribe(config.getRedisChannel(), nickManager::handleRemoteRedisEvent);
        }

        // Plugin messaging channels
        getServer().getMessenger().registerIncomingPluginChannel(this, "ultranick:channel", new PaperPluginMessageListener(nickManager, getLogger()));
        getServer().getMessenger().registerIncomingPluginChannel(this, "ultranick:sync", new PaperPluginMessageListener(nickManager, getLogger()));
        getServer().getMessenger().registerOutgoingPluginChannel(this, "ultranick:channel");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "ultranick:sync");

        // Register listeners
        getServer().getPluginManager().registerEvents(new PaperChatListener(nickManager), this);
        getServer().getPluginManager().registerEvents(new PaperMessageListener(nickManager), this);

        // Register commands
        PaperNickCommand cmdExecutor = new PaperNickCommand(nickManager);
        if (getCommand("nick") != null) getCommand("nick").setExecutor(cmdExecutor);
        if (getCommand("unnick") != null) getCommand("unnick").setExecutor(cmdExecutor);
        if (getCommand("realname") != null) getCommand("realname").setExecutor(cmdExecutor);
        if (getCommand("ultranick") != null) getCommand("ultranick").setExecutor(cmdExecutor);

        getLogger().info("UltraNick Paper backend successfully loaded! Author: Chatbxn");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling UltraNick Paper...");

        if (storageManager != null) {
            storageManager.close();
        }
        if (redisClient != null) {
            redisClient.close();
        }

        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);

        getLogger().info("UltraNick Paper disabled.");
    }

    private void loadConfiguration() {
        FileConfiguration fc = getConfig();
        this.config = new UltraNickConfig();

        config.setRedisEnabled(fc.getBoolean("redis.enabled", true));
        config.setRedisHost(fc.getString("redis.host", "127.0.0.1"));
        config.setRedisPort(fc.getInt("redis.port", 6379));
        config.setRedisPassword(fc.getString("redis.password", ""));
        config.setRedisDatabase(fc.getInt("redis.database", 0));
        config.setRedisTimeout(fc.getInt("redis.timeout", 3000));
        config.setRedisChannel(fc.getString("redis.channel", "ultranick:events"));
        config.setRedisKeyPrefix(fc.getString("redis.key-prefix", "ultranick:"));

        if (fc.isConfigurationSection("ranks")) {
            Map<String, UltraNickConfig.RankConfig> ranks = new LinkedHashMap<>();
            for (String key : Objects.requireNonNull(fc.getConfigurationSection("ranks")).getKeys(false)) {
                ranks.put(key, new UltraNickConfig.RankConfig(
                        fc.getString("ranks." + key + ".prefix", ""),
                        fc.getString("ranks." + key + ".suffix", ""),
                        fc.getString("ranks." + key + ".chat-color", "&7")
                ));
            }
            if (!ranks.isEmpty()) {
                config.setRanks(ranks);
            }
        }
    }
}
