package net.ultranick.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.ultranick.api.model.NickResult;
import net.ultranick.common.config.MessageService;
import net.ultranick.velocity.manager.VelocityNickManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Command to remove disguise (/unnick [target]).
 *
 * @author Chatbxn
 */
public final class UnnickCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final VelocityNickManager nickManager;
    private final MessageService messageService;

    public UnnickCommand(ProxyServer proxy, VelocityNickManager nickManager, MessageService messageService) {
        this.proxy = proxy;
        this.nickManager = nickManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length > 0 && invocation.source().hasPermission("ultranick.admin")) {
            // Unnick other player
            String targetName = args[0];
            Player target = proxy.getPlayer(targetName).orElse(null);
            if (target == null) {
                invocation.source().sendMessage(messageService.getMessage("player-not-found"));
                return;
            }

            nickManager.undisguise(target.getUniqueId()).thenAccept(result -> {
                if (result.isSuccessful()) {
                    invocation.source().sendMessage(messageService.getMessage("unnick-other-success", Map.of("target", target.getUsername())));
                    target.sendMessage(messageService.getMessage("unnick-success", Map.of("real_name", target.getUsername())));
                } else {
                    invocation.source().sendMessage(messageService.getMessage("not-nicked-other"));
                }
            });
            return;
        }

        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(messageService.parse("<red>Befehl erfordert Spielerkontext.</red>"));
            return;
        }

        nickManager.undisguise(player.getUniqueId()).thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(messageService.getMessage("unnick-success", Map.of("real_name", player.getUsername())));
            } else if (result == NickResult.NOT_NICKED) {
                player.sendMessage(messageService.getMessage("not-nicked"));
            } else {
                player.sendMessage(messageService.parse("<red>" + result.getDefaultMessage() + "</red>"));
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ultranick.use");
    }
}
