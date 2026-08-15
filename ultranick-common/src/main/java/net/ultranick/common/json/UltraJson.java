package net.ultranick.common.json;

import com.google.gson.*;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.SkinData;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Fast and thread-safe JSON serializer/deserializer for UltraNick.
 *
 * @author Chatbxn
 */
public final class UltraJson {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UuidTypeAdapter())
            .disableHtmlEscaping()
            .create();

    private static final Gson PRETTY_GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UuidTypeAdapter())
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private UltraJson() {}

    public static Gson gson() {
        return GSON;
    }

    public static Gson prettyGson() {
        return PRETTY_GSON;
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    public static String toPrettyJson(Object object) {
        return PRETTY_GSON.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> T fromJson(String json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    private static final class UuidTypeAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        @Override
        public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return UUID.fromString(json.getAsString());
        }
    }
}
