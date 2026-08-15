package net.ultranick.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.common.config.MessageService;
import net.ultranick.velocity.manager.VelocityNickManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command to list all currently disguised players across the network (/nicklist).
 *
 * @author Chatbxn
 */
public final class NickListCommand implements SimpleCommand {

    private final VelocityNickManager nickManager;
    private final MessageService messageService;

    public NickListCommand(VelocityNickManager nickManager, MessageService messageService) {
        this.nickManager = nickManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("ultranick.list")) {
            invocation.source().sendMessage(messageService.getMessage("no-permission"));
            return;
        }

        Collection<DisguiseProfile> disguises = nickManager.getActiveDisguises();
        if (disguises.isEmpty()) {
            invocation.source().sendMessage(messageService.parse("<yellow>Aktuell sind keine Spieler im Netzwerk genickt.</yellow>"));
            return;
        }

        invocation.source().sendMessage(messageService.parse("<dark_gray>--- <gradient:#55cdfc:#f7a8b8><b>Aktive Tarnungen</b></gradient> <dark_gray>(<white>" + disguises.size() + "</white>) ---</dark_gray>"));
        for (DisguiseProfile profile : disguises) {
            String line = "<gray>• </gray><green>" + profile.getRealName() + "</green> <dark_gray>➔</dark_gray> <yellow>"
                    + profile.getDisguisedName() + "</yellow> <dark_gray>[Rang: " + profile.getRank() + "]</dark_gray>";
            invocation.source().sendMessage(messageService.parse(line));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ultranick.list");
    }
}
