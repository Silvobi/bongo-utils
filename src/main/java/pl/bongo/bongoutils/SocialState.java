package pl.bongo.bongoutils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server-thread state; write first, then publish changes to the live state. */
public final class SocialState {
    public enum Mode { VISIBLE, SEMI, VANISH }
    public static final class Data {
        public int schemaVersion = 1;
        public Map<UUID, String> names = new HashMap<>();
        public Map<UUID, Set<UUID>> ignores = new HashMap<>();
        public Map<UUID, Mode> modes = new HashMap<>();
        public Set<UUID> fakeQuit = new HashSet<>();
    }
    private final Path file;
    private volatile Data data;
    public SocialState(Path file) throws IOException { this.file = file; reload(); }
    public void reload() throws IOException {
        Data next = Files.exists(file) ? Store.JSON.fromJson(Files.readString(file), Data.class) : new Data();
        if (next == null || next.schemaVersion != 1 || next.names == null || next.ignores == null
                || next.modes == null || next.fakeQuit == null || next.modes.containsValue(null)
                || next.ignores.values().stream().anyMatch(Objects::isNull)) throw new IOException("Invalid social.json");
        data = next;
    }
    public Data snapshot() { return Store.JSON.fromJson(Store.JSON.toJson(data), Data.class); }
    public void replace(Data next) throws IOException { Store.atomic(file, Store.JSON.toJson(next)); data = next; }
    public void remember(UUID id, String name) throws IOException {
        if (!name.equals(data.names.get(id))) { Data next = snapshot(); next.names.put(id, name); replace(next); }
    }
    public UUID find(String name) {
        return data.names.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public String name(UUID id) { return data.names.getOrDefault(id, id.toString()); }
    public Set<UUID> ignoredBy(UUID id) { return Set.copyOf(data.ignores.getOrDefault(id, Set.of())); }
    public boolean ignores(UUID viewer, UUID other) { return data.ignores.getOrDefault(viewer, Set.of()).contains(other); }
    public boolean blocked(UUID a, UUID b) { return !a.equals(b) && (ignores(a, b) || ignores(b, a)); }
    public Mode mode(UUID id) { return data.modes.getOrDefault(id, Mode.VISIBLE); }
    public boolean fakeQuit(UUID id) { return data.fakeQuit.contains(id); }
    public boolean toggleIgnore(UUID viewer, UUID other) throws IOException {
        if (viewer.equals(other)) throw new IllegalArgumentException("Cannot ignore yourself");
        Data next = snapshot(); Set<UUID> list = next.ignores.computeIfAbsent(viewer, x -> new HashSet<>());
        boolean enabled = list.add(other); if (!enabled) list.remove(other); replace(next); return enabled;
    }
    public void mode(UUID id, Mode mode, boolean fakeQuit) throws IOException {
        Data next = snapshot(); if (mode == Mode.VISIBLE) next.modes.remove(id); else next.modes.put(id, mode);
        if (fakeQuit) next.fakeQuit.add(id); else next.fakeQuit.remove(id); replace(next);
    }
    public static Data migrate(Data original, UUID from, UUID to, String fromName, String toName, boolean swap) {
        Data next = Store.JSON.fromJson(Store.JSON.toJson(original), Data.class);
        Map<UUID, Set<UUID>> ignores = new HashMap<>();
        // Override removes the destination's old relations before transferring the source's relations.
        original.ignores.forEach((owner, targets) -> {
            if (!swap && owner.equals(to)) return;
            UUID newOwner = remap(owner, from, to, swap);
            Set<UUID> mapped = new HashSet<>();
            for (UUID target : targets) {
                if (!swap && target.equals(to)) continue;
                UUID newTarget = remap(target, from, to, swap);
                if (!newOwner.equals(newTarget)) mapped.add(newTarget);
            }
            if (!mapped.isEmpty()) ignores.put(newOwner, mapped);
        });
        next.ignores = ignores;
        Mode sourceMode = original.modes.get(from), targetMode = original.modes.get(to);
        next.modes.remove(from); next.modes.remove(to);
        if (sourceMode != null) next.modes.put(to, sourceMode);
        if (swap && targetMode != null) next.modes.put(from, targetMode);
        boolean sourceFake = original.fakeQuit.contains(from), targetFake = original.fakeQuit.contains(to);
        next.fakeQuit.remove(from); next.fakeQuit.remove(to);
        if (sourceFake) next.fakeQuit.add(to); if (swap && targetFake) next.fakeQuit.add(from);
        next.names.put(from, fromName); next.names.put(to, toName);
        return next;
    }
    private static UUID remap(UUID id, UUID from, UUID to, boolean swap) {
        return id.equals(from) ? to : swap && id.equals(to) ? from : id;
    }
}
