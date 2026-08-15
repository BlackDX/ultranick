package net.ultranick.paper.refresh;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class PaperEntityRefresher {

    private final Plugin plugin;

    public PaperEntityRefresher(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    // Refresh player visuals for everyone online (hides and reshows entity)
    public void refreshPlayer(@NotNull Player target) {
        if (!target.isOnline()) return;

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            if (viewer.canSee(target)) {
                viewer.hidePlayer(plugin, target);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (target.isOnline() && viewer.isOnline()) {
                        viewer.showPlayer(plugin, target);
                    }
                });
            }
        }
    }
}
