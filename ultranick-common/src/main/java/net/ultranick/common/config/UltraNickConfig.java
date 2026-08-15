package net.ultranick.common.config;

import java.util.*;

/**
 * Global configuration data model for UltraNick.
 *
 * @author Chatbxn
 */
public final class UltraNickConfig {

    // Redis
    private boolean redisEnabled = true;
    private String redisHost = "127.0.0.1";
    private int redisPort = 6379;
    private String redisPassword = "";
    private int redisDatabase = 0;
    private int redisTimeout = 3000;
    private String redisChannel = "ultranick:events";
    private String redisKeyPrefix = "ultranick:";

    // REST API Endpoints
    private boolean apiEnabled = true;
    private String apiHost = "0.0.0.0";
    private int apiPort = 8085;
    private String apiAuthToken = "ultranick_secret_token_change_me";

    // General Disguise Settings
    private boolean keepDisguiseAcrossServers = true;
    private boolean autoDisguiseOnJoin = false;
    private boolean allowCustomNick = true;
    private int commandCooldownSeconds = 3;
    private String defaultRank = "Spieler";

    // Rank Disguise Settings
    private Map<String, RankConfig> ranks = new LinkedHashMap<>();

    // Name Pool & Blacklist
    private List<String> nicknames = new ArrayList<>();
    private List<String> blacklist = new ArrayList<>();

    public UltraNickConfig() {
        // Default Rank Setup
        ranks.put("Spieler", new RankConfig("&7Spieler &8| &7", "", "&7"));
        ranks.put("Default", new RankConfig("&7", "", "&7"));
        ranks.put("VIP", new RankConfig("&aVIP &8| &a", "", "&f"));
        ranks.put("Premium", new RankConfig("&6Premium &8| &6", "", "&f"));
    }

    public static final class RankConfig {
        private String prefix;
        private String suffix;
        private String chatColor;

        public RankConfig() {}

        public RankConfig(String prefix, String suffix, String chatColor) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.chatColor = chatColor;
        }

        public String getPrefix() {
            return prefix != null ? prefix : "";
        }

        public String getSuffix() {
            return suffix != null ? suffix : "";
        }

        public String getChatColor() {
            return chatColor != null ? chatColor : "&7";
        }
    }

    // Getters and Setters
    public boolean isRedisEnabled() { return redisEnabled; }
    public void setRedisEnabled(boolean redisEnabled) { this.redisEnabled = redisEnabled; }

    public String getRedisHost() { return redisHost; }
    public void setRedisHost(String redisHost) { this.redisHost = redisHost; }

    public int getRedisPort() { return redisPort; }
    public void setRedisPort(int redisPort) { this.redisPort = redisPort; }

    public String getRedisPassword() { return redisPassword; }
    public void setRedisPassword(String redisPassword) { this.redisPassword = redisPassword; }

    public int getRedisDatabase() { return redisDatabase; }
    public void setRedisDatabase(int redisDatabase) { this.redisDatabase = redisDatabase; }

    public int getRedisTimeout() { return redisTimeout; }
    public void setRedisTimeout(int redisTimeout) { this.redisTimeout = redisTimeout; }

    public String getRedisChannel() { return redisChannel; }
    public void setRedisChannel(String redisChannel) { this.redisChannel = redisChannel; }

    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }

    public boolean isApiEnabled() { return apiEnabled; }
    public void setApiEnabled(boolean apiEnabled) { this.apiEnabled = apiEnabled; }

    public String getApiHost() { return apiHost; }
    public void setApiHost(String apiHost) { this.apiHost = apiHost; }

    public int getApiPort() { return apiPort; }
    public void setApiPort(int apiPort) { this.apiPort = apiPort; }

    public String getApiAuthToken() { return apiAuthToken; }
    public void setApiAuthToken(String apiAuthToken) { this.apiAuthToken = apiAuthToken; }

    public boolean isKeepDisguiseAcrossServers() { return keepDisguiseAcrossServers; }
    public void setKeepDisguiseAcrossServers(boolean keepDisguiseAcrossServers) { this.keepDisguiseAcrossServers = keepDisguiseAcrossServers; }

    public boolean isAutoDisguiseOnJoin() { return autoDisguiseOnJoin; }
    public void setAutoDisguiseOnJoin(boolean autoDisguiseOnJoin) { this.autoDisguiseOnJoin = autoDisguiseOnJoin; }

    public boolean isAllowCustomNick() { return allowCustomNick; }
    public void setAllowCustomNick(boolean allowCustomNick) { this.allowCustomNick = allowCustomNick; }

    public int getCommandCooldownSeconds() { return commandCooldownSeconds; }
    public void setCommandCooldownSeconds(int commandCooldownSeconds) { this.commandCooldownSeconds = commandCooldownSeconds; }

    public String getDefaultRank() { return defaultRank; }
    public void setDefaultRank(String defaultRank) { this.defaultRank = defaultRank; }

    public Map<String, RankConfig> getRanks() { return ranks; }
    public void setRanks(Map<String, RankConfig> ranks) { this.ranks = ranks; }

    public List<String> getNicknames() { return nicknames; }
    public void setNicknames(List<String> nicknames) { this.nicknames = nicknames; }

    public List<String> getBlacklist() { return blacklist; }
    public void setBlacklist(List<String> blacklist) { this.blacklist = blacklist; }
}
