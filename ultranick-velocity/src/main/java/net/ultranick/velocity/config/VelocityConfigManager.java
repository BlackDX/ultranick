package net.ultranick.velocity.config;

import net.ultranick.common.config.MessageService;
import net.ultranick.common.config.UltraNickConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VelocityConfigManager {

    private final Path dataFolder;
    private final Logger logger;
    private UltraNickConfig config;
    private MessageService messageService;

    public VelocityConfigManager(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.config = new UltraNickConfig();
        this.messageService = new MessageService();
    }

    public void load() {
        try {
            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder);
            }
            loadConfig();
            loadMessages();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load UltraNick configuration", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        Path configFile = dataFolder.resolve("config.yml");
        Yaml yaml = new Yaml();

        if (!Files.exists(configFile)) {
            saveDefaultConfigFile(configFile);
        }

        try (InputStream in = Files.newInputStream(configFile)) {
            Map<String, Object> map = yaml.load(in);
            if (map != null) {
                // Redis
                if (map.containsKey("redis")) {
                    Map<String, Object> redis = (Map<String, Object>) map.get("redis");
                    config.setRedisEnabled(getBoolean(redis, "enabled", true));
                    config.setRedisHost(getString(redis, "host", "127.0.0.1"));
                    config.setRedisPort(getInt(redis, "port", 6379));
                    config.setRedisPassword(getString(redis, "password", ""));
                    config.setRedisDatabase(getInt(redis, "database", 0));
                    config.setRedisTimeout(getInt(redis, "timeout", 3000));
                    config.setRedisChannel(getString(redis, "channel", "ultranick:events"));
                    config.setRedisKeyPrefix(getString(redis, "key-prefix", "ultranick:"));
                }

                // REST API
                if (map.containsKey("api")) {
                    Map<String, Object> api = (Map<String, Object>) map.get("api");
                    config.setApiEnabled(getBoolean(api, "enabled", true));
                    config.setApiHost(getString(api, "host", "0.0.0.0"));
                    config.setApiPort(getInt(api, "port", 8085));
                    config.setApiAuthToken(getString(api, "auth-token", "ultranick_secret_token_change_me"));
                }

                // Settings
                if (map.containsKey("settings")) {
                    Map<String, Object> settings = (Map<String, Object>) map.get("settings");
                    config.setKeepDisguiseAcrossServers(getBoolean(settings, "keep-disguise-across-servers", true));
                    config.setAutoDisguiseOnJoin(getBoolean(settings, "auto-disguise-on-join", false));
                    config.setAllowCustomNick(getBoolean(settings, "allow-custom-nick", true));
                    config.setCommandCooldownSeconds(getInt(settings, "cooldown-seconds", 3));
                    config.setDefaultRank(getString(settings, "default-rank", "Spieler"));
                }

                // Ranks
                if (map.containsKey("ranks")) {
                    Map<String, Object> ranksMap = (Map<String, Object>) map.get("ranks");
                    Map<String, UltraNickConfig.RankConfig> parsedRanks = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : ranksMap.entrySet()) {
                        if (entry.getValue() instanceof Map) {
                            Map<String, Object> r = (Map<String, Object>) entry.getValue();
                            parsedRanks.put(entry.getKey(), new UltraNickConfig.RankConfig(
                                    getString(r, "prefix", ""),
                                    getString(r, "suffix", ""),
                                    getString(r, "chat-color", "&7")
                            ));
                        }
                    }
                    if (!parsedRanks.isEmpty()) {
                        config.setRanks(parsedRanks);
                    }
                }

                // Nicknames
                if (map.containsKey("nicknames") && map.get("nicknames") instanceof List) {
                    List<String> list = (List<String>) map.get("nicknames");
                    config.setNicknames(new ArrayList<>(list));
                }

                // Blacklist
                if (map.containsKey("blacklist") && map.get("blacklist") instanceof List) {
                    List<String> list = (List<String>) map.get("blacklist");
                    config.setBlacklist(new ArrayList<>(list));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing config.yml, using defaults: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadMessages() {
        Path msgFile = dataFolder.resolve("messages.yml");
        Yaml yaml = new Yaml();

        if (!Files.exists(msgFile)) {
            saveDefaultMessagesFile(msgFile);
        }

        try (InputStream in = Files.newInputStream(msgFile)) {
            Map<String, Object> map = yaml.load(in);
            if (map != null) {
                if (map.containsKey("prefix")) {
                    messageService.setPrefix(String.valueOf(map.get("prefix")));
                }
                if (map.containsKey("messages") && map.get("messages") instanceof Map) {
                    Map<String, Object> msgs = (Map<String, Object>) map.get("messages");
                    for (Map.Entry<String, Object> entry : msgs.entrySet()) {
                        messageService.setMessage(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing messages.yml, using defaults: " + e.getMessage());
        }
    }

    private void saveDefaultConfigFile(Path path) {
        String yamlContent = ""
                + "# UltraNick configuration\n\n"
                + "redis:\n"
                + "  enabled: true\n"
                + "  host: \"127.0.0.1\"\n"
                + "  port: 6379\n"
                + "  password: \"\"\n"
                + "  database: 0\n"
                + "  timeout: 3000\n"
                + "  channel: \"ultranick:events\"\n"
                + "  key-prefix: \"ultranick:\"\n\n"
                + "api:\n"
                + "  enabled: true\n"
                + "  host: \"0.0.0.0\"\n"
                + "  port: 8085\n"
                + "  auth-token: \"ultranick_secret_token_change_me\"\n\n"
                + "settings:\n"
                + "  keep-disguise-across-servers: true\n"
                + "  auto-disguise-on-join: false\n"
                + "  allow-custom-nick: true\n"
                + "  cooldown-seconds: 3\n"
                + "  default-rank: \"Spieler\"\n\n"
                + "ranks:\n"
                + "  Spieler:\n"
                + "    prefix: \"&7Spieler &8| &7\"\n"
                + "    suffix: \"\"\n"
                + "    chat-color: \"&7\"\n"
                + "  Default:\n"
                + "    prefix: \"&7\"\n"
                + "    suffix: \"\"\n"
                + "    chat-color: \"&7\"\n"
                + "  VIP:\n"
                + "    prefix: \"&aVIP &8| &a\"\n"
                + "    suffix: \"\"\n"
                + "    chat-color: \"&f\"\n\n"
                + "nicknames:\n"
                + "  - \"ShadowKnight\"\n"
                + "  - \"EnderWolf\"\n"
                + "  - \"LunarPulse\"\n"
                + "  - \"PixelCrafter\"\n"
                + "  - \"VortexGamer\"\n"
                + "  - \"FrostByte\"\n"
                + "  - \"StormRider\"\n"
                + "  - \"NightCrawler\"\n"
                + "  - \"EchoStrike\"\n"
                + "  - \"QuantumLeaf\"\n"
                + "  - \"AuraPlayer\"\n"
                + "  - \"BlazeHunter\"\n"
                + "  - \"CyberFox\"\n\n"
                + "blacklist:\n"
                + "  - \"admin\"\n"
                + "  - \"owner\"\n"
                + "  - \"moderator\"\n"
                + "  - \"support\"\n";

        try {
            Files.writeString(path, yamlContent);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save default config.yml", e);
        }
    }

    private void saveDefaultMessagesFile(Path path) {
        String msgContent = ""
                + "prefix: \"<gradient:#55cdfc:#f7a8b8><b>UltraNick</b></gradient> <dark_gray>»</dark_gray> <gray>\"\n"
                + "messages:\n"
                + "  nick-success: \"Du bist nun als <yellow>%disguised_name%</yellow> mit dem Rang <aqua>%rank%</aqua> getarnt!\"\n"
                + "  nick-other-success: \"Der Spieler <yellow>%target%</yellow> ist nun als <yellow>%disguised_name%</yellow> getarnt.\"\n"
                + "  unnick-success: \"Deine Tarnung wurde <red>aufgehoben</red>. Du heißt wieder <yellow>%real_name%</yellow>.\"\n"
                + "  unnick-other-success: \"Die Tarnung von <yellow>%target%</yellow> wurde aufgehoben.\"\n"
                + "  already-nicked: \"<red>Du bist bereits genickt! Nutze <yellow>/unnick</yellow> zum Enttarnen.</red>\"\n"
                + "  not-nicked: \"<red>Du bist derzeit nicht genickt.</red>\"\n"
                + "  not-nicked-other: \"<red>Dieser Spieler ist nicht genickt.</red>\"\n"
                + "  name-taken: \"<red>Der Name <yellow>%name%</yellow> ist bereits vergeben oder online!</red>\"\n"
                + "  invalid-name: \"<red>Der Nickname <yellow>%name%</yellow> ist ungültig (3-16 alphanumerische Zeichen)!</red>\"\n"
                + "  cooldown: \"<red>Bitte warte noch <yellow>%seconds%s</yellow>, bevor du dich erneut nickst.</red>\"\n"
                + "  no-permission: \"<red>Dazu hast du keine Berechtigung!</red>\"\n"
                + "  player-not-found: \"<red>Spieler wurde nicht gefunden.</red>\"\n"
                + "  realname-lookup: \"Der Spieler <yellow>%disguised_name%</yellow> ist in Wirklichkeit <green>%real_name%</green> <dark_gray>(%uuid%)</dark_gray>.\"\n"
                + "  reload-success: \"<green>Konfiguration und Skins erfolgreich neu geladen!</green>\"\n";

        try {
            Files.writeString(path, msgContent);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save default messages.yml", e);
        }
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    public UltraNickConfig getConfig() {
        return config;
    }

    public MessageService getMessageService() {
        return messageService;
    }
}
