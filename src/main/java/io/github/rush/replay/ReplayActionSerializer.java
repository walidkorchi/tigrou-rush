package io.github.rush.replay;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.UUID;

public final class ReplayActionSerializer {

    private ReplayActionSerializer() {}

    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(UUID.class,
                        (JsonSerializer<UUID>) (src, t, ctx) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(UUID.class,
                        (JsonDeserializer<UUID>) (json, t, ctx) -> UUID.fromString(json.getAsString()))
                .registerTypeHierarchyAdapter(ReplayAction.class, new ReplayActionAdapter())
                .create();
    }

    private static final class ReplayActionAdapter
            implements JsonSerializer<ReplayAction>, JsonDeserializer<ReplayAction> {

        private static final Gson INNER = new GsonBuilder()
                .registerTypeAdapter(UUID.class,
                        (JsonSerializer<UUID>) (src, t, ctx) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(UUID.class,
                        (JsonDeserializer<UUID>) (json, t, ctx) -> UUID.fromString(json.getAsString()))
                .create();

        @Override
        public JsonElement serialize(ReplayAction src, Type typeOfSrc, JsonSerializationContext ctx) {
            JsonObject obj = INNER.toJsonTree(src, src.getClass()).getAsJsonObject();
            obj.addProperty("type", src.getClass().getSimpleName());
            return obj;
        }

        @Override
        public ReplayAction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get("type").getAsString();
            return switch (type) {
                case "MoveAction"        -> INNER.fromJson(json, MoveAction.class);
                case "BlockChangeAction" -> INNER.fromJson(json, BlockChangeAction.class);
                case "DeathAction"       -> INNER.fromJson(json, DeathAction.class);
                case "RespawnAction"     -> INNER.fromJson(json, RespawnAction.class);
                case "PhaseAction"       -> INNER.fromJson(json, PhaseAction.class);
                case "BedDestroyAction"  -> INNER.fromJson(json, BedDestroyAction.class);
                default -> throw new JsonParseException("Unknown ReplayAction type: " + type);
            };
        }
    }
}
