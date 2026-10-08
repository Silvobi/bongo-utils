package pl.bongo.bongoutils;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A persisted name-only allowlist. UUIDs and Mojang profile lookups are deliberately absent. */
public final class IgnWhitelist {
    private final Path file;
    private boolean enabled;
    private final NavigableMap<String, String> names = new TreeMap<>();

    public IgnWhitelist(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) reload();
        else save(false, names);
    }
    public synchronized boolean enabled() { return enabled; }
    public synchronized boolean contains(String name) {
        return name != null && names.containsKey(name.toLowerCase(Locale.ROOT));
    }
    public synchronized List<String> names() { return List.copyOf(names.values()); }
    public synchronized boolean setEnabled(boolean value) throws IOException {
        if (enabled == value) return false;
        save(value, names); enabled = value; return true;
    }
    public synchronized int add(Collection<String> requested) throws IOException {
        NavigableMap<String, String> next = new TreeMap<>(names);
        // Validate the complete request before writing: a bad name cannot cause a partial update.
        requested.forEach(Store::key);
        requested.forEach(name -> next.putIfAbsent(Store.key(name), name));
        int changed = next.size() - names.size();
        if (changed > 0) { save(enabled, next); names.clear(); names.putAll(next); }
        return changed;
    }
    public synchronized int remove(Collection<String> requested) throws IOException {
        NavigableMap<String, String> next = new TreeMap<>(names);
        requested.forEach(Store::key);
        requested.forEach(name -> next.remove(Store.key(name)));
        int changed = names.size() - next.size();
        if (changed > 0) { save(enabled, next); names.clear(); names.putAll(next); }
        return changed;
    }
    public synchronized void reload() throws IOException {
        // A broken file must not silently disable the gate or discard its previous good contents.
        try {
            JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonElement flag = data.get("enabled");
            if (flag == null || !flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()
                    || !data.has("names") || !data.get("names").isJsonArray())
                throw new IllegalArgumentException("Expected enabled:boolean and names:array");
            NavigableMap<String, String> next = new TreeMap<>();
            for (JsonElement entry : data.getAsJsonArray("names")) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Whitelist names must be strings");
                String name = entry.getAsString();
                if (next.putIfAbsent(Store.key(name), name) != null)
                    throw new IllegalArgumentException("Duplicate whitelist name: " + name);
            }
            boolean nextEnabled = flag.getAsBoolean();
            names.clear(); names.putAll(next); enabled = nextEnabled;
        } catch (RuntimeException e) { throw new IOException("Nieprawidłowy plik ign-whitelist.json.", e); }
    }
    private void save(boolean enabled, Map<String, String> names) throws IOException {
        JsonObject data = new JsonObject(); data.addProperty("enabled", enabled);
        JsonArray array = new JsonArray(); names.values().forEach(array::add); data.add("names", array);
        Store.atomic(file, Store.JSON.toJson(data));
    }
    /** Mirrors enforce-whitelist, using IGN even if vanilla's UUID whitelist is enabled. */
    public void kickUnlisted(net.minecraft.server.MinecraftServer server) {
        if (!enabled() || !server.isEnforceWhitelist()) return;
        for (var player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!contains(player.getGameProfile().name()))
                player.connection.disconnect(net.minecraft.network.chat.Component.translatable("multiplayer.disconnect.not_whitelisted"));
        }
    }
}
