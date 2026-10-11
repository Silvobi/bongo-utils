package pl.bongo.bongoutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Store {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    private final Map<String, UUID> premium = new HashMap<>();
    public Store(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve("accounts"));
        Files.createDirectories(root.resolve("skins"));
        Path names = root.resolve("premium.json");
        if (Files.exists(names)) {
            var object = com.google.gson.JsonParser.parseString(Files.readString(names)).getAsJsonObject();
            object.entrySet().forEach(e -> premium.put(e.getKey(), UUID.fromString(e.getValue().getAsString())));
        }
    }
    public static String key(String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid player name");
        return name.toLowerCase(Locale.ROOT);
    }
    public synchronized boolean reserved(String name) { return premium.containsKey(key(name)); }
    public synchronized UUID premiumId(String name) { return premium.get(key(name)); }
    public Path root() { return root; }
    public synchronized boolean reserve(String name, UUID uuid) throws IOException {
        String key = key(name);
        if (uuid.equals(premium.get(key))) return false;
        boolean firstRegistration = !premium.containsKey(key);
        Map<String, UUID> next = new HashMap<>(premium); next.put(key, uuid);
        atomic(root.resolve("premium.json"), JSON.toJson(next));
        premium.clear(); premium.putAll(next);
        return firstRegistration;
    }
    public synchronized String password(String name) throws IOException {
        Path file = root.resolve("accounts").resolve(key(name) + ".json");
        if (!Files.exists(file)) return null;
        try {
            Account account = JSON.fromJson(Files.readString(file), Account.class);
            if (account == null || account.hash() == null || account.hash().isEmpty()) throw new IOException("Invalid account data");
            return account.hash();
        } catch (RuntimeException e) { throw new IOException("Invalid account data", e); }
    }
    public synchronized boolean register(String name, String hash) throws IOException {
        if (reserved(name) || password(name) != null) return false;
        atomic(root.resolve("accounts").resolve(key(name) + ".json"), JSON.toJson(new Account(hash)));
        return true;
    }
    public synchronized String offlinePassword(String name) throws IOException {
        return reserved(name) ? null : password(name);
    }
    public synchronized boolean matchesOfflinePassword(String name, String expectedHash) throws IOException {
        return expectedHash != null && expectedHash.equals(offlinePassword(name));
    }
    /** Compare with the dialog's snapshot to reject stale edits, resets and changes to MSA ownership. */
    public synchronized boolean editOfflinePassword(String name, String expectedHash, String nextHash) throws IOException {
        if (!matchesOfflinePassword(name, expectedHash)) return false;
        Path file = root.resolve("accounts").resolve(key(name) + ".json");
        if (nextHash == null) Files.delete(file);
        else atomic(file, JSON.toJson(new Account(nextHash)));
        return true;
    }
    public synchronized void reset(String name) throws IOException {
        Files.deleteIfExists(root.resolve("accounts").resolve(key(name) + ".json"));
    }
    public synchronized SkinRecord skin(UUID uuid) throws IOException {
        Path file = root.resolve("skins").resolve(uuid + ".json");
        return Files.exists(file) ? JSON.fromJson(Files.readString(file), SkinRecord.class) : null;
    }
    public synchronized void skin(UUID uuid, SkinRecord skin) throws IOException {
        atomic(root.resolve("skins").resolve(uuid + ".json"), JSON.toJson(skin));
    }
    public Path image(String sha256) { return root.resolve("skins").resolve(sha256 + ".png"); }
    public static void atomic(Path file, String contents) throws IOException {
        Path temp = Files.createTempFile(file.getParent(), ".bongo-", ".tmp");
        try {
            Files.writeString(temp, contents, StandardCharsets.UTF_8);
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private record Account(String hash) {}
    public record SkinRecord(String source, String value, String signature, String imageHash) {}
}
