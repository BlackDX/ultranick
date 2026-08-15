package net.ultranick.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.paper.manager.PaperNickManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Optional;

public final class PaperChatListener implements Listener {

    private final PaperNickManager nickManager;

    public PaperChatListener(PaperNickManager nickManager) {
        this.nickManager = nickManager;
    }

    // Modern Paper chat renderer
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Optional<DisguiseProfile> opt = nickManager.getDisguise(player.getUniqueId());

        if (opt.isPresent()) {
            DisguiseProfile profile = opt.get();
            Component nameComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    profile.getPrefix() + profile.getDisguisedName() + profile.getSuffix()
            );
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    nameComponent.append(Component.text(": ")).append(message)
            );
        }
    }

    // Legacy chat format fallback
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Optional<DisguiseProfile> opt = nickManager.getDisguise(player.getUniqueId());

        if (opt.isPresent()) {
            DisguiseProfile profile = opt.get();
            String prefix = profile.getPrefix();
            String disguisedName = profile.getDisguisedName();
            String color = profile.getChatColor();

            event.setFormat(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    prefix + disguisedName + "&r: " + color
            ) + "%2$s");
        }
    }
}
