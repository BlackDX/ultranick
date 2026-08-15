package net.ultranick.common.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance text formatting and message service.
 * Supports both modern MiniMessage tags (e.g. <gradient:...>) and classic & color codes.
 *
 * @author Chatbxn
 */
public final class MessageService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();

    private String prefix = "<gradient:#55cdfc:#f7a8b8><b>UltraNick</b></gradient> <dark_gray>»</dark_gray> <gray>";
    private final Map<String, String> messages = new ConcurrentHashMap<>();

    public MessageService() {
        loadDefaultMessages();
    }

    private void loadDefaultMessages() {
        messages.put("nick-success", "Du bist nun als <yellow>%disguised_name%</yellow> mit dem Rang <aqua>%rank%</aqua> getarnt!");
        messages.put("nick-other-success", "Der Spieler <yellow>%target%</yellow> ist nun als <yellow>%disguised_name%</yellow> getarnt.");
        messages.put("unnick-success", "Deine Tarnung wurde <red>aufgehoben</red>. Du heißt wieder <yellow>%real_name%</yellow>.");
        messages.put("unnick-other-success", "Die Tarnung von <yellow>%target%</yellow> wurde aufgehoben.");
        messages.put("already-nicked", "<red>Du bist bereits genickt! Nutze <yellow>/unnick</yellow> zum Enttarnen.</red>");
        messages.put("not-nicked", "<red>Du bist derzeit nicht genickt.</red>");
        messages.put("not-nicked-other", "<red>Dieser Spieler ist nicht genickt.</red>");
        messages.put("name-taken", "<red>Der Name <yellow>%name%</yellow> ist bereits vergeben oder online!</red>");
        messages.put("invalid-name", "<red>Der Nickname <yellow>%name%</yellow> ist ungültig (3-16 alphanumerische Zeichen)!</red>");
        messages.put("cooldown", "<red>Bitte warte noch <yellow>%seconds%s</yellow>, bevor du dich erneut nickst.</red>");
        messages.put("no-permission", "<red>Dazu hast du keine Berechtigung!</red>");
        messages.put("player-not-found", "<red>Spieler wurde nicht gefunden.</red>");
        messages.put("realname-lookup", "Der Spieler <yellow>%disguised_name%</yellow> ist in Wirklichkeit <green>%real_name%</green> <dark_gray>(%uuid%)</dark_gray>.");
        messages.put("reload-success", "<green>Konfiguration und Skins erfolgreich neu geladen!</green>");
    }

    @NotNull
    public Component parse(@NotNull String text) {
        if (text.contains("<") && text.contains(">")) {
            try {
                // If it contains legacy codes, convert them to minimessage or parse legacy first
                String converted = text.replace("&0", "<black>")
                        .replace("&1", "<dark_blue>")
                        .replace("&2", "<dark_green>")
                        .replace("&3", "<dark_aqua>")
                        .replace("&4", "<dark_red>")
                        .replace("&5", "<dark_purple>")
                        .replace("&6", "<gold>")
                        .replace("&7", "<gray>")
                        .replace("&8", "<dark_gray>")
                        .replace("&9", "<blue>")
                        .replace("&a", "<green>")
                        .replace("&b", "<aqua>")
                        .replace("&c", "<red>")
                        .replace("&d", "<light_purple>")
                        .replace("&e", "<yellow>")
                        .replace("&f", "<white>")
                        .replace("&l", "<b>")
                        .replace("&o", "<i>")
                        .replace("&n", "<u>")
                        .replace("&m", "<st>")
                        .replace("&k", "<obf>")
                        .replace("&r", "<reset>");
                return MINI_MESSAGE.deserialize(converted);
            } catch (Exception ignored) {
                return AMPERSAND_SERIALIZER.deserialize(text);
            }
        }
        return AMPERSAND_SERIALIZER.deserialize(text);
    }

    @NotNull
    public Component getMessage(@NotNull String key, @NotNull Map<String, String> placeholders) {
        String msg = messages.getOrDefault(key, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return parse(prefix + msg);
    }

    @NotNull
    public Component getMessage(@NotNull String key) {
        return getMessage(key, Map.of());
    }

    public String getRawMessage(@NotNull String key) {
        return messages.getOrDefault(key, key);
    }

    public void setMessage(@NotNull String key, @NotNull String message) {
        messages.put(key, message);
    }

    public void setPrefix(@NotNull String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
