package pl.bongo.bongoutils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Publishes a complete validated snapshot so login/skin workers also see reloads. */
public final class ConfigFile {
    private final Path file;
    private volatile BongoUtils.Config current;

    public ConfigFile(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) Store.atomic(file, Store.JSON.toJson(new BongoUtils.Config()));
        reload();
    }

    public BongoUtils.Config current() { return current; }

    public void reload() throws IOException {
        try {
            JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (object.has("allowOffline") && (!object.get("allowOffline").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("allowOffline").isBoolean())) throw new IllegalArgumentException("allowOffline must be a boolean");
            string(object, "mineSkinApiKey");
            if (object.has("rules")) {
                JsonObject rules = object.getAsJsonObject("rules");
                if (rules == null) throw new IllegalArgumentException("rules must be an object");
                string(rules, "mode"); string(rules, "url"); string(rules, "title");
                if (rules.has("entries")) {
                    for (JsonElement entry : rules.getAsJsonArray("entries")) {
                        if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || entry.getAsString().isBlank())
                            throw new IllegalArgumentException("rules.entries must contain non-empty strings");
                    }
                }
            }
            BongoUtils.Config next = Store.JSON.fromJson(object, BongoUtils.Config.class);
            if (!next.rules.mode.equals("link") && !next.rules.mode.equals("dialog"))
                throw new IllegalArgumentException("rules.mode must be link or dialog");
            if (next.rules.title.isBlank()) throw new IllegalArgumentException("rules.title must not be blank");
            if (!next.rules.url.isEmpty()) url(next.rules.url);
            if (next.rules.mode.equals("link") && next.rules.url.isEmpty())
                throw new IllegalArgumentException("rules.url is required in link mode");
            next.rules.entries = java.util.List.copyOf(next.rules.entries);
            current = next;
        } catch (RuntimeException e) {
            throw new IOException("Invalid config.json: " + e.getMessage(), e);
        }
    }

    private static void string(JsonObject object, String key) {
        if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()))
            throw new IllegalArgumentException(key + " must be a string");
    }

    private static void url(String value) {
        URI uri = URI.create(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("rules.url must be an HTTP(S) URL without credentials");
    }
}
