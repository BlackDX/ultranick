package net.ultranick.common.name;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * High-performance nickname pool manager.
 * Supplies realistic player names and validates nickname formats.
 *
 * @author Chatbxn
 */
public final class NamePoolManager {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private final List<String> defaultNames = new CopyOnWriteArrayList<>(Arrays.asList(
            "ShadowKnight", "EnderWolf", "LunarPulse", "PixelCrafter", "VortexGamer",
            "FrostByte", "StormRider", "NightCrawler", "EchoStrike", "QuantumLeaf",
            "AuraPlayer", "BlazeHunter", "CyberFox", "MysticDrift", "NovaFalcon",
            "ObsidianX", "PhantomBlade", "RaptorClaw", "SolarFlare", "TitanFury",
            "VenomBite", "WildCanyon", "ZenithStar", "ApexPredator", "BoltRunner",
            "CosmicDust", "DragonEye", "ElectricWave", "FrozenTide", "GoldenArrow",
            "HyperNova", "IronGrip", "JadeTiger", "KingsGuard", "LaserBeam",
            "MeteorShower", "NeonKnight", "OmegaForce", "PixelPioneer", "QuickSilver",
            "RedShift", "ShadowHawk", "ThunderBolt", "UltraMarine", "Valkyrie",
            "WarpDrive", "XenoWarrior", "ZeroGravity", "AlphaWolf", "SwiftBlade",
            "DarkMatter", "GhostRider", "SkyWalker", "SilentStorm", "FireStorm"
    ));

    private final Set<String> blacklistedNames = Collections.synchronizedSet(new HashSet<>());

    public NamePoolManager() {}

    public NamePoolManager(@NotNull List<String> customNames, @NotNull List<String> blacklist) {
        if (customNames != null && !customNames.isEmpty()) {
            defaultNames.clear();
            defaultNames.addAll(customNames);
        }
        if (blacklist != null) {
            for (String b : blacklist) {
                blacklistedNames.add(b.toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * Checks if a nickname is valid according to Minecraft username rules (3-16 chars, alphanumeric or underscores).
     */
    public boolean isValidName(@NotNull String name) {
        if (name == null || name.length() < 3 || name.length() > 16) {
            return false;
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            return false;
        }
        return !blacklistedNames.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets a random nickname from the configured name pool.
     */
    @NotNull
    public String getRandomName() {
        if (defaultNames.isEmpty()) {
            return "Player_" + ThreadLocalRandom.current().nextInt(1000, 9999);
        }
        int index = ThreadLocalRandom.current().nextInt(defaultNames.size());
        return defaultNames.get(index);
    }

    /**
     * Gets a random nickname from the pool that is not currently taken.
     */
    @NotNull
    public String getRandomAvailableName(@NotNull Predicate<String> isTakenPredicate) {
        List<String> shuffled = new ArrayList<>(defaultNames);
        Collections.shuffle(shuffled);

        for (String candidate : shuffled) {
            if (!isTakenPredicate.test(candidate)) {
                return candidate;
            }
        }

        // Fallback if all standard names are taken
        String fallback;
        int attempts = 0;
        do {
            fallback = "Player_" + ThreadLocalRandom.current().nextInt(100, 99999);
            attempts++;
        } while (isTakenPredicate.test(fallback) && attempts < 20);

        return fallback;
    }

    public void addName(@NotNull String name) {
        if (isValidName(name) && !defaultNames.contains(name)) {
            defaultNames.add(name);
        }
    }

    public List<String> getAllNames() {
        return Collections.unmodifiableList(defaultNames);
    }
}
