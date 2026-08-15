package net.ultranick.common.skin;

import net.ultranick.api.model.SkinData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance internal skin repository.
 * Preloads realistic default and custom skins into memory, eliminating all external API requests.
 *
 * @author Chatbxn
 */
public final class PreloadedSkinManager {

    private final Map<String, SkinData> skinMap = new ConcurrentHashMap<>();
    private final List<SkinData> skinList = new ArrayList<>();

    public PreloadedSkinManager() {
        registerDefaultSkins();
    }

    private void registerDefaultSkins() {
        // Pre-bundled standard and modern default Minecraft textures (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri)
        registerSkin(new SkinData("Steve",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JmNDQzYjU1MjQ4Y2E4OWQyYjBhNzc1YTY0MjhkZTg0ZTI2YjE5Yjg0ZGU4MjQ4MWVlYmIxN2NlZGU0In19fQ==",
                ""));

        registerSkin(new SkinData("Alex",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmU5YzljOTU3YmQ2Mzg4NDhiMmI4NTVjNWRiYzA2OGRiNWMyYzk1ZTRmNWUzMWFhYjNmZjRhMmVkOWY5In19fQ==",
                ""));

        registerSkin(new SkinData("Ari",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWZmZmI4MmY5ZGRjMWI1MGI2MjA3MDk5ZTk2NDBhNTAyZWE3ZDg2ODk3MTkxYmYzNzE1Nzc5OGI5ZTMwYzEzYSJ9fX0=",
                ""));

        registerSkin(new SkinData("Efe",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTEzZGRmNWIwOTQ2ODNkZDhkYmY4NjkxMzA5ZWY4Yzg4OGE1NGExNDYwMmQ2NWFjMmFhNDk5MmEyYTAxZDQ0YSJ9fX0=",
                ""));

        registerSkin(new SkinData("Kai",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTFhYmE5MmNjYzNmYmE1Zjg2YTBkYzg2OTVjMWVhMjQ4ZmE3NDNlMjE1OTk2MzNmZjg1NGU3MzRmZTcxZDkyNCJ9fX0=",
                ""));

        registerSkin(new SkinData("Makena",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQzMjA2MmI2YTQ5N2NkNWQxMmM4ZjA5YTM3MDU3ZGIyMmU5ZDdkYzNjNTNjMjE4OTc1M2ZiMzg3N2FjMDQwMCJ9fX0=",
                ""));

        registerSkin(new SkinData("Noor",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzZhNTYwNzg1MzNmYjhhNmEyODFlZTg1YTE5ZDZlZDFiYjc0ZmRjMmQzNzAxNDZkZWYwZjhkMWZjZDhmYTkyMSJ9fX0=",
                ""));

        registerSkin(new SkinData("Sunny",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTExMTc5YjFhMTBkMGQxZDExZjhkM2RjMjI5OWRiMjlkYjU0YmVmOWM3YmE5YTkxODQ0YmYwYTQ0YmY5ZDhmNiJ9fX0=",
                ""));

        registerSkin(new SkinData("Zuri",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNhNzBhYTdlMGUyYTgyNzIzZTU0OWE5MDgwYTIzMDU1Yjg0YmQ3YzVhZTJlYjVkZDQxMzk1NjNmYzE2ODQxZCJ9fX0=",
                ""));
    }

    public void registerSkin(@NotNull SkinData skin) {
        Objects.requireNonNull(skin, "skin cannot be null");
        skinMap.put(skin.getName().toLowerCase(Locale.ROOT), skin);
        synchronized (skinList) {
            skinList.removeIf(s -> s.getName().equalsIgnoreCase(skin.getName()));
            skinList.add(skin);
        }
    }

    @NotNull
    public Optional<SkinData> getSkin(@NotNull String name) {
        return Optional.ofNullable(skinMap.get(name.toLowerCase(Locale.ROOT)));
    }

    @NotNull
    public SkinData getRandomSkin() {
        synchronized (skinList) {
            if (skinList.isEmpty()) {
                return new SkinData("Default", "", "");
            }
            int index = ThreadLocalRandom.current().nextInt(skinList.size());
            return skinList.get(index);
        }
    }

    public Collection<SkinData> getAllSkins() {
        return Collections.unmodifiableCollection(skinMap.values());
    }

    public Set<String> getSkinNames() {
        return Collections.unmodifiableSet(skinMap.keySet());
    }
}
