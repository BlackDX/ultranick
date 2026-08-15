package net.ultranick.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.common.config.MessageService;
import net.ultranick.velocity.manager.VelocityNickManager;

import java.util.*;

/**
 * Command to lookup the real identity of a nicked player (/realname <nick>).
 *
 * @author Chatbxn
 */
public final class RealNameCommand implements SimpleCommand {

    private final VelocityNickManager nickManager;
    private final MessageService messageService;

    public RealNameCommand(VelocityNickManager nickManager, MessageService messageService) {
        this.nickManager = nickManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("ultranick.realname")) {
            invocation.source().sendMessage(messageService.getMessage("no-permission"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(messageService.parse("<red>Verwendung: <yellow>/realname <Nickname></yellow></red>"));
            return;
        }

        String query = args[0];
        Optional<UUID> optUuid = nickManager.getRealUUID(query);

        if (optUuid.isPresent()) {
            UUID uuid = optUuid.get();
            Optional<DisguiseProfile> optProfile = nickManager.getDisguise(uuid);
            String realName = optProfile.map(DisguiseProfile::getRealName).orElse("Unbekannt");
            String disguisedName = optProfile.map(DisguiseProfile::getDisguisedName).orElse(query);

            invocation.source().sendMessage(messageService.getMessage("realname-lookup", Map.of(
                    "disguised_name", disguisedName,
                    "real_name", realName,
                    "uuid", uuid.toString()
            )));
        } else {
            invocation.source().sendMessage(messageService.parse("<red>Kein aktiver Spieler mit dem Nicknamen <yellow>" + query + "</yellow> gefunden.</red>"));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            List<String> list = new ArrayList<>();
            for (DisguiseProfile p : nickManager.getActiveDisguises()) {
                list.add(p.getDisguisedName());
            }
            return list;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ultranick.realname");
    }
}
