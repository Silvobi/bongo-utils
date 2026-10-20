package pl.bongo.bongoutils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable before-images plus rollback, including files that did not previously exist. */
public final class FileTransaction {
    public record Entry(String path, String backup, boolean existed) {}
    public record Manifest(int schemaVersion, List<Entry> entries) {}
    private final LinkedHashMap<Path, byte[]> writes = new LinkedHashMap<>();
    public void put(Path path, byte[] contents) { writes.put(path.toAbsolutePath().normalize(), contents); }
    public void pair(Path from, Path to, boolean swap, java.util.function.BiFunction<byte[],Path,byte[]> transform) throws IOException {
        byte[] a = Files.exists(from) ? Files.readAllBytes(from) : null;
        byte[] b = Files.exists(to) ? Files.readAllBytes(to) : null;
        put(to, a == null ? null : transform.apply(a,to));
        put(from, swap && b != null ? transform.apply(b,from) : null);
    }
    public Path commit(Path backupRoot, String description, Runnable reload) throws IOException {
        Files.createDirectories(backupRoot);
        Path backup = Files.createDirectory(backupRoot.resolve(java.time.Instant.now().toString().replace(':','-') + "-" + UUID.randomUUID()));
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (Path path : writes.keySet()) {
            boolean exists = Files.exists(path); String saved = "file-" + index++;
            if (exists) {
                Files.copy(path, backup.resolve(saved));
                try(var channel=java.nio.channels.FileChannel.open(backup.resolve(saved),StandardOpenOption.WRITE)){channel.force(true);}
            }
            entries.add(new Entry(path.toString(), saved, exists));
        }
        Store.atomic(backup.resolve("manifest.json"), Store.JSON.toJson(new Manifest(1,entries)));
        Store.atomic(backup.resolve("operation.txt"), description);
        Store.atomic(backup.resolve("status.txt"), "PENDING");
        try {
            for (var write : writes.entrySet()) write(write.getKey(),write.getValue());
            reload.run();
            Store.atomic(backup.resolve("status.txt"), "COMMITTED");
            return backup;
        } catch (Exception failure) {
            try { restore(backup); reload.run(); Store.atomic(backup.resolve("status.txt"), "ROLLED_BACK"); }
            catch (Exception rollback) { failure.addSuppressed(rollback); throw new IOException("Migration rollback failed; stop the server and restore " + backup, failure); }
            throw new IOException("Migration rolled back; backup: " + backup, failure);
        }
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        if (bytes == null) { Files.deleteIfExists(path); return; }
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".bongo-migrate-", ".tmp");
        try {
            Files.write(temporary,bytes);
            try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)){channel.force(true);}
            try { Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static void restore(Path backup) throws IOException {
        Manifest manifest = Store.JSON.fromJson(Files.readString(backup.resolve("manifest.json")), Manifest.class);
        if (manifest == null || manifest.schemaVersion != 1 || manifest.entries == null) throw new IOException("Invalid migration manifest");
        for (Entry entry : manifest.entries) write(Path.of(entry.path), entry.existed ? Files.readAllBytes(backup.resolve(entry.backup)) : null);
    }
    /** Recover an interrupted transaction before accounts, worlds or mod state are loaded. */
    public static void recover(Path backupRoot) throws IOException {
        if (!Files.isDirectory(backupRoot)) return;
        try (var directories = Files.list(backupRoot)) {
            for (Path backup : directories.filter(Files::isDirectory).sorted().toList()) {
                Path status = backup.resolve("status.txt");
                if (Files.exists(status) && Files.readString(status).equals("PENDING")) {
                    restore(backup); Store.atomic(status,"ROLLED_BACK");
                    BongoUtils.LOG.warn("Recovered interrupted migration: {}",backup);
                }
            }
        }
    }
}
