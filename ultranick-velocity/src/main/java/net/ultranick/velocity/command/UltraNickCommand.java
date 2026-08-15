package net.ultranick.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.ultranick.common.config.MessageService;
import net.ultranick.velocity.config.VelocityConfigManager;
import net.ultranick.velocity.manager.VelocityNickManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main administration command for UltraNick (/ultranick <reload|info|api>).
 *
 * @author Chatbxn
 */
public final class UltraNickCommand implements SimpleCommand {

    private final VelocityConfigManager configManager;
    private final VelocityNickManager nickManager;
    private final MessageService messageService;

    public UltraNickCommand(VelocityConfigManager configManager, VelocityNickManager nickManager, MessageService messageService) {
        this.configManager = configManager;
        this.nickManager = nickManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("ultranick.admin")) {
            invocation.source().sendMessage(messageService.getMessage("no-permission"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0 || "info".equalsIgnoreCase(args[0])) {
            invocation.source().sendMessage(messageService.parse("<dark_gray>-----------------------------</dark_gray>"));
            invocation.source().sendMessage(messageService.parse("<gradient:#55cdfc:#f7a8b8><b>UltraNick Proxy System</b></gradient> <dark_gray>v1.0.0</dark_gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>Author: <yellow>Chatbxn</yellow></gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>Aktive Tarnungen: <green>" + nickManager.getActiveDisguises().size() + "</green></gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>Redis Sync: <aqua>" + (configManager.getConfig().isRedisEnabled() ? "Aktiviert" : "Deaktiviert") + "</aqua></gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>REST API: <aqua>" + (configManager.getConfig().isApiEnabled() ? "Port " + configManager.getConfig().getApiPort() : "Deaktiviert") + "</aqua></gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>Befehle: <yellow>/ultranick reload</yellow> | <yellow>/realname</yellow> | <yellow>/nicklist</yellow></gray>"));
            invocation.source().sendMessage(messageService.parse("<dark_gray>-----------------------------</dark_gray>"));
            return;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            configManager.load();
            invocation.source().sendMessage(messageService.getMessage("reload-success"));
            return;
        }

        if ("api".equalsIgnoreCase(args[0])) {
            invocation.source().sendMessage(messageService.parse("<gradient:#55cdfc:#f7a8b8><b>UltraNick REST API Endpoints:</b></gradient>"));
            invocation.source().sendMessage(messageService.parse("<gray>• GET /api/v1/health</gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>• GET /api/v1/nicks</gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>• GET /api/v1/nick/{player}</gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>• POST /api/v1/nick/{player}</gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>• DELETE /api/v1/nick/{player}</gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>• GET /api/v1/realname/{disguisedName}</gray>"));
            invocation.source().sendMessage(messageService.parse("<gray>• GET /api/v1/skins</gray>"));
            return;
        }

        invocation.source().sendMessage(messageService.parse("<red>Unbekannter Unterbefehl. Nutze <yellow>/ultranick [info|reload|api]</yellow></red>"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            List<String> list = new ArrayList<>();
            for (String sub : List.of("info", "reload", "api")) {
                if (args.length == 0 || sub.startsWith(args[0].toLowerCase())) {
                    list.add(sub);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ultranick.admin");
    }
}
