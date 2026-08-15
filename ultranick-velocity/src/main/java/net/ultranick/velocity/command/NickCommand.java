package net.ultranick.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.ultranick.api.model.NickResult;
import net.ultranick.common.config.MessageService;
import net.ultranick.velocity.manager.VelocityNickManager;

import java.util.*;

/**
 * Command for player disguising (/nick [name]).
 *
 * @author Chatbxn
 */
public final class NickCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final VelocityNickManager nickManager;
    private final MessageService messageService;

    public NickCommand(ProxyServer proxy, VelocityNickManager nickManager, MessageService messageService) {
        this.proxy = proxy;
        this.nickManager = nickManager;
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(messageService.parse("<red>Dieser Befehl kann nur von Spielern ausgeführt werden.</red>"));
            return;
        }

        if (!player.hasPermission("ultranick.use")) {
            player.sendMessage(messageService.getMessage("no-permission"));
            return;
        }

        String[] args = invocation.arguments();

        // If cooldown active
        if (nickManager.isOnCooldown(player.getUniqueId()) && !player.hasPermission("ultranick.bypass.cooldown")) {
            long remaining = nickManager.getRemainingCooldownSeconds(player.getUniqueId());
            player.sendMessage(messageService.getMessage("cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }

        if (args.length == 0) {
            // Random Disguise
            nickManager.disguiseRandom(player.getUniqueId()).thenAccept(result -> {
                if (result.isSuccessful()) {
                    nickManager.getDisguise(player.getUniqueId()).ifPresent(profile ->
                            player.sendMessage(messageService.getMessage("nick-success", Map.of(
                                    "disguised_name", profile.getDisguisedName(),
                                    "rank", profile.getRank()
                            )))
                    );
                } else if (result == NickResult.ALREADY_NICKED) {
                    player.sendMessage(messageService.getMessage("already-nicked"));
                } else {
                    player.sendMessage(messageService.parse("<red>" + result.getDefaultMessage() + "</red>"));
                }
            });
        } else {
            // Custom Disguise
            if (!player.hasPermission("ultranick.custom")) {
                player.sendMessage(messageService.getMessage("no-permission"));
                return;
            }

            String chosenName = args[0];
            nickManager.disguise(player.getUniqueId(), chosenName, null, null).thenAccept(result -> {
                if (result.isSuccessful()) {
                    nickManager.getDisguise(player.getUniqueId()).ifPresent(profile ->
                            player.sendMessage(messageService.getMessage("nick-success", Map.of(
                                    "disguised_name", profile.getDisguisedName(),
                                    "rank", profile.getRank()
                            )))
                    );
                } else if (result == NickResult.NAME_TAKEN) {
                    player.sendMessage(messageService.getMessage("name-taken", Map.of("name", chosenName)));
                } else if (result == NickResult.INVALID_NAME) {
                    player.sendMessage(messageService.getMessage("invalid-name", Map.of("name", chosenName)));
                } else if (result == NickResult.ALREADY_NICKED) {
                    player.sendMessage(messageService.getMessage("already-nicked"));
                } else {
                    player.sendMessage(messageService.parse("<red>" + result.getDefaultMessage() + "</red>"));
                }
            });
        }
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
