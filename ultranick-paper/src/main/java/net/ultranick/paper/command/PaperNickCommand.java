package net.ultranick.paper.command;

import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.NickResult;
import net.ultranick.paper.manager.PaperNickManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PaperNickCommand implements CommandExecutor, TabCompleter {

    private final PaperNickManager nickManager;

    public PaperNickCommand(PaperNickManager nickManager) {
        this.nickManager = nickManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        switch (name) {
            case "nick":
                handleNick(sender, args);
                return true;
            case "unnick":
                handleUnnick(sender, args);
                return true;
            case "realname":
                handleRealName(sender, args);
                return true;
            case "ultranick":
                handleUltraNick(sender, args);
                return true;
            default:
                return false;
        }
    }

    private void handleNick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDioser Befehl kann nur von Spielern ausgeführt werden.");
            return;
        }

        if (!player.hasPermission("ultranick.use")) {
            player.sendMessage("§cDazu hast du keine Berechtigung!");
            return;
        }

        if (args.length == 0) {
            // Random nick
            nickManager.disguiseRandom(player.getUniqueId()).thenAccept(result -> {
                if (result.isSuccessful()) {
                    nickManager.getDisguise(player.getUniqueId()).ifPresent(profile ->
                            player.sendMessage("§aDu bist nun als §e" + profile.getDisguisedName() + " §agetarnt!")
                    );
                } else {
                    player.sendMessage("§c" + result.getDefaultMessage());
                }
            });
        } else {
            // Custom nick
            if (!player.hasPermission("ultranick.custom")) {
                player.sendMessage("§cDu hast keine Berechtigung für eigene Nicknamen!");
                return;
            }
            String chosen = args[0];
            nickManager.disguise(player.getUniqueId(), chosen, null, null).thenAccept(result -> {
                if (result.isSuccessful()) {
                    nickManager.getDisguise(player.getUniqueId()).ifPresent(profile ->
                            player.sendMessage("§aDu bist nun als §e" + profile.getDisguisedName() + " §agetarnt!")
                    );
                } else {
                    player.sendMessage("§c" + result.getDefaultMessage());
                }
            });
        }
    }

    private void handleUnnick(CommandSender sender, String[] args) {
        // Unnick other player (admin only)
        if (args.length > 0 && sender.hasPermission("ultranick.admin")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                nickManager.undisguise(target.getUniqueId()).thenAccept(result -> {
                    sender.sendMessage("§aTarnung von " + target.getName() + " aufgehoben.");
                    target.sendMessage("§eDeine Tarnung wurde aufgehoben.");
                });
                return;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cBefehl erfordert Spielerkontext.");
            return;
        }

        nickManager.undisguise(player.getUniqueId()).thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage("§aDeine Tarnung wurde aufgehoben. Du heißt wieder §e" + player.getName() + "§a.");
            } else if (result == NickResult.NOT_NICKED) {
                player.sendMessage("§cDu bist derzeit nicht genickt.");
            } else {
                player.sendMessage("§c" + result.getDefaultMessage());
            }
        });
    }

    private void handleRealName(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ultranick.realname")) {
            sender.sendMessage("§cDazu hast du keine Berechtigung!");
            return;
        }

        if (args.length == 0) {
            sender.sendMessage("§cVerwendung: §e/realname <Nickname>");
            return;
        }

        String query = args[0];
        Optional<UUID> optUuid = nickManager.getRealUUID(query);

        if (optUuid.isPresent()) {
            UUID uuid = optUuid.get();
            Optional<DisguiseProfile> optProfile = nickManager.getDisguise(uuid);
            String realName = optProfile.map(DisguiseProfile::getRealName).orElse("Unbekannt");
            String disguisedName = optProfile.map(DisguiseProfile::getDisguisedName).orElse(query);
            sender.sendMessage("§7Der Spieler §e" + disguisedName + " §7ist in Wirklichkeit §a" + realName + " §8(" + uuid + ")");
        } else {
            sender.sendMessage("§cKein Spieler mit dem Nicknamen §e" + query + " §cgefunden.");
        }
    }

    private void handleUltraNick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ultranick.admin")) {
            sender.sendMessage("§cDazu hast du keine Berechtigung!");
            return;
        }

        sender.sendMessage("§8-----------------------------");
        sender.sendMessage("§b§lUltraNick Paper Backend §7v1.0.0");
        sender.sendMessage("§7Author: §eChatbxn");
        sender.sendMessage("§7Aktive Tarnungen: §a" + nickManager.getActiveDisguises().size());
        sender.sendMessage("§8-----------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if ("realname".equalsIgnoreCase(command.getName()) && args.length == 1) {
            List<String> list = new ArrayList<>();
            for (DisguiseProfile p : nickManager.getActiveDisguises()) {
                list.add(p.getDisguisedName());
            }
            return list;
        }
        return Collections.emptyList();
    }
}
