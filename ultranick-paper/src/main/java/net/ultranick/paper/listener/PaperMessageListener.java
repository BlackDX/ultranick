package net.ultranick.paper.listener;

import net.kyori.adventure.text.Component;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.paper.manager.PaperNickManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Optional;

public final class PaperMessageListener implements Listener {

    private final PaperNickManager nickManager;

    public PaperMessageListener(PaperNickManager nickManager) {
        this.nickManager = nickManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoinEarly(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Local cache check
        Optional<DisguiseProfile> opt = nickManager.getDisguise(player.getUniqueId());
        if (opt.isPresent()) {
            nickManager.applyDisguiseLocally(opt.get());
        } else {
            // 2. Storage / Redis lookup
            nickManager.loadDisguiseFromStorage(player.getUniqueId());

            // 3. Ask Velocity proxy for nick data
            nickManager.requestProxySync(player);
        }

        // Sync nametags for the player
        nickManager.getNametagManager().updateScoreboardForViewer(player, nickManager.getActiveDisguises());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinLate(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Safety sync after spawn packets
        Bukkit.getScheduler().runTaskLater(nickManager.getPlugin(), () -> {
            if (player.isOnline()) {
                Optional<DisguiseProfile> opt = nickManager.getDisguise(player.getUniqueId());
                if (opt.isPresent()) {
                    nickManager.applyDisguiseLocally(opt.get());
                } else {
                    nickManager.requestProxySync(player);
                }
                nickManager.getNametagManager().updateScoreboardForViewer(player, nickManager.getActiveDisguises());
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoinMessage(PlayerJoinEvent event) {
        Component joinMsg = event.joinMessage();
        if (joinMsg == null) return;

        event.joinMessage(maskDisguisedNames(joinMsg));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuitMessage(PlayerQuitEvent event) {
        Component quitMsg = event.quitMessage();
        if (quitMsg == null) return;

        event.quitMessage(maskDisguisedNames(quitMsg));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component deathMsg = event.deathMessage();
        if (deathMsg == null) return;

        // Replace real name with disguised name in death message
        event.deathMessage(maskDisguisedNames(deathMsg));
    }

    private Component maskDisguisedNames(Component original) {
        if (original == null) return null;
        Component result = original;
        Collection<DisguiseProfile> active = nickManager.getActiveDisguises();
        for (DisguiseProfile profile : active) {
            String realName = profile.getRealName();
            String disguisedName = profile.getDisguisedName();
            if (realName != null && !realName.isEmpty() && disguisedName != null && !disguisedName.isEmpty()) {
                result = result.replaceText(b -> b.matchLiteral(realName).replacement(disguisedName));
            }
        }
        return result;
    }
}
