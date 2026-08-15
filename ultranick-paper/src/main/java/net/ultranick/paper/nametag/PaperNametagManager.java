package net.ultranick.paper.nametag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.ultranick.api.model.DisguiseProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PaperNametagManager {

    private static final String TEAM_PREFIX = "un_";

    public PaperNametagManager() {}

    // Apply disguise prefix, suffix, and color to scoreboard teams
    public void applyDisguiseNametag(@NotNull Player player, @NotNull DisguiseProfile profile) {
        Set<Scoreboard> scoreboards = getAllActiveScoreboards(player);
        String teamName = getTeamName(player);

        Component prefixComp = !profile.getPrefix().isEmpty()
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(profile.getPrefix())
                : Component.empty();

        Component suffixComp = !profile.getSuffix().isEmpty()
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(profile.getSuffix())
                : Component.empty();

        NamedTextColor color = parseNamedTextColor(
                !profile.getChatColor().isEmpty() ? profile.getChatColor() : profile.getPrefix()
        );

        for (Scoreboard board : scoreboards) {
            try {
                Team existingReal = board.getEntryTeam(player.getName());
                if (existingReal != null && !existingReal.getName().equals(teamName)) {
                    existingReal.removeEntry(player.getName());
                }

                Team existingDisguised = board.getEntryTeam(profile.getDisguisedName());
                if (existingDisguised != null && !existingDisguised.getName().equals(teamName)) {
                    existingDisguised.removeEntry(profile.getDisguisedName());
                }

                Team team = board.getTeam(teamName);
                if (team == null) {
                    team = board.registerNewTeam(teamName);
                }

                team.prefix(prefixComp);
                team.suffix(suffixComp);
                if (color != null) {
                    team.color(color);
                }

                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);

                if (!team.hasEntry(profile.getDisguisedName())) {
                    team.addEntry(profile.getDisguisedName());
                }
                if (!team.hasEntry(player.getName())) {
                    team.addEntry(player.getName());
                }
            } catch (Throwable ignored) {}
        }
    }

    // Remove disguise team from scoreboards
    public void clearDisguiseNametag(@NotNull Player player, @NotNull String disguisedName) {
        Set<Scoreboard> scoreboards = getAllActiveScoreboards(player);
        String teamName = getTeamName(player);

        for (Scoreboard board : scoreboards) {
            try {
                Team team = board.getTeam(teamName);
                if (team != null) {
                    team.removeEntry(player.getName());
                    if (disguisedName != null && !disguisedName.isEmpty()) {
                        team.removeEntry(disguisedName);
                    }
                    team.unregister();
                }
            } catch (Throwable ignored) {}
        }
    }

    // Update scoreboard for a specific player viewing other disguised players
    public void updateScoreboardForViewer(@NotNull Player viewer, @NotNull Collection<DisguiseProfile> activeDisguises) {
        Scoreboard board = viewer.getScoreboard();
        for (DisguiseProfile profile : activeDisguises) {
            Player target = Bukkit.getPlayer(profile.getUniqueId());
            if (target != null && target.isOnline()) {
                applyDisguiseToScoreboard(board, target, profile);
            }
        }
    }

    private void applyDisguiseToScoreboard(@NotNull Scoreboard board, @NotNull Player player, @NotNull DisguiseProfile profile) {
        String teamName = getTeamName(player);
        try {
            Team existingReal = board.getEntryTeam(player.getName());
            if (existingReal != null && !existingReal.getName().equals(teamName)) {
                existingReal.removeEntry(player.getName());
            }

            Team existingDisguised = board.getEntryTeam(profile.getDisguisedName());
            if (existingDisguised != null && !existingDisguised.getName().equals(teamName)) {
                existingDisguised.removeEntry(profile.getDisguisedName());
            }

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }

            Component prefixComp = !profile.getPrefix().isEmpty()
                    ? LegacyComponentSerializer.legacyAmpersand().deserialize(profile.getPrefix())
                    : Component.empty();

            Component suffixComp = !profile.getSuffix().isEmpty()
                    ? LegacyComponentSerializer.legacyAmpersand().deserialize(profile.getSuffix())
                    : Component.empty();

            NamedTextColor color = parseNamedTextColor(
                    !profile.getChatColor().isEmpty() ? profile.getChatColor() : profile.getPrefix()
            );

            team.prefix(prefixComp);
            team.suffix(suffixComp);
            if (color != null) {
                team.color(color);
            }

            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);

            if (!team.hasEntry(profile.getDisguisedName())) {
                team.addEntry(profile.getDisguisedName());
            }
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        } catch (Throwable ignored) {}
    }

    private Set<Scoreboard> getAllActiveScoreboards(@NotNull Player player) {
        Set<Scoreboard> scoreboards = new HashSet<>();
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm != null) {
            scoreboards.add(sm.getMainScoreboard());
        }
        scoreboards.add(player.getScoreboard());
        for (Player p : Bukkit.getOnlinePlayers()) {
            scoreboards.add(p.getScoreboard());
        }
        return scoreboards;
    }

    private String getTeamName(Player player) {
        String hex = Integer.toHexString(player.getUniqueId().hashCode());
        return TEAM_PREFIX + (hex.length() > 12 ? hex.substring(0, 12) : hex);
    }

    @Nullable
    public static NamedTextColor parseNamedTextColor(@Nullable String colorCode) {
        if (colorCode == null || colorCode.isEmpty()) return null;
        String clean = colorCode.replace("&", "").replace("§", "").toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) return null;
        char code = clean.charAt(clean.length() - 1);
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }
}
