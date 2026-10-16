package pl.bongo.bongoutils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;

/** Server-thread mailbox state. A batch delivery is one atomic write, published only after it succeeds. */
public final class MailStore {
    public static final int MAX_BODY = 4000;
    public record Message(UUID id, UUID sender, String senderName, long sentAt, String body,
                          boolean important, String attachment, boolean read, boolean claimed) {
        Message readCopy() { return new Message(id, sender, senderName, sentAt, body, important, attachment, true, claimed); }
        Message claimedCopy() { return new Message(id, sender, senderName, sentAt, body, important, attachment, read, true); }
    }
    public static final class Data {
        public int schemaVersion = 1;
        public Map<UUID, List<Message>> inboxes = new HashMap<>();
    }
    private final Path file;
    private Data data;
    public MailStore(Path file) throws IOException { this.file = file; reload(); }
    public void reload() throws IOException {
        try {
            Data next = Files.exists(file) ? Store.JSON.fromJson(Files.readString(file), Data.class) : new Data();
            if (next == null || next.schemaVersion != 1 || next.inboxes == null) throw new IllegalArgumentException();
            for (var inbox : next.inboxes.entrySet()) {
                if (inbox.getKey() == null || inbox.getValue() == null) throw new IllegalArgumentException();
                Set<UUID> ids = new HashSet<>();
                for (Message m : inbox.getValue()) {
                    if (m == null || m.id == null || !ids.add(m.id) || m.sender == null || m.senderName == null
                            || m.body == null || m.body.isBlank() || m.body.length() > MAX_BODY
                            || m.claimed && m.attachment == null) throw new IllegalArgumentException();
                }
            }
            data = next;
        } catch (RuntimeException e) { throw new IOException("Invalid mail.json", e); }
    }
    public Data snapshot() { return Store.JSON.fromJson(Store.JSON.toJson(data), Data.class); }
    private void replace(Data next) throws IOException { Store.atomic(file, Store.JSON.toJson(next)); data = next; }
    public boolean hasInbox(UUID owner) { return !data.inboxes.getOrDefault(owner, List.of()).isEmpty(); }
    public void send(UUID sender, String name, Collection<UUID> recipients, String body, boolean important, String attachment) throws IOException {
        Objects.requireNonNull(sender); Store.key(name);
        if (body == null || body.isBlank() || body.length() > MAX_BODY) throw new IllegalArgumentException("Invalid mail body");
        Set<UUID> unique = new LinkedHashSet<>(recipients);
        if (unique.isEmpty() || unique.contains(null)) throw new IllegalArgumentException("Missing recipients");
        Data next = snapshot();
        Message message = new Message(UUID.randomUUID(), sender, name, System.currentTimeMillis(), body, important, attachment, false, false);
        for (UUID recipient : unique) next.inboxes.computeIfAbsent(recipient, ignored -> new ArrayList<>()).add(message);
        replace(next);
    }
    public static boolean visible(UUID viewer, Message message, BiPredicate<UUID, UUID> blocked) {
        return message.important || !blocked.test(viewer, message.sender);
    }
    public List<Message> inbox(UUID owner, boolean unreadOnly, BiPredicate<UUID, UUID> blocked) {
        // Reversing stable delivery order also handles multiple messages sent in the same millisecond.
        return data.inboxes.getOrDefault(owner, List.of()).reversed().stream()
                .filter(m -> (!unreadOnly || !m.read) && visible(owner, m, blocked)).toList();
    }
    public Message find(UUID owner, UUID id) {
        return data.inboxes.getOrDefault(owner, List.of()).stream().filter(m -> m.id.equals(id)).findFirst().orElse(null);
    }
    public Message read(UUID owner, UUID id) throws IOException {
        Message message = find(owner, id);
        if (message == null || message.read) return message;
        Message next = message.readCopy(); update(owner, next); return next;
    }
    /** Persist before awarding an item: repeated or replayed claims cannot mint a second copy. */
    public boolean claim(UUID owner, UUID id) throws IOException {
        Message message = find(owner, id);
        if (message == null || message.attachment == null || message.claimed) return false;
        update(owner, message.claimedCopy()); return true;
    }
    private void update(UUID owner, Message message) throws IOException {
        Data next = snapshot();
        List<Message> inbox = next.inboxes.get(owner);
        for (int i = 0; i < inbox.size(); i++) if (inbox.get(i).id.equals(message.id)) { inbox.set(i, message); break; }
        replace(next);
    }
    public static Data migrate(Data original, UUID from, UUID to, boolean swap) {
        Data next = Store.JSON.fromJson(Store.JSON.toJson(original), Data.class);
        List<Message> source = next.inboxes.remove(from), destination = next.inboxes.remove(to);
        if (source != null) next.inboxes.put(to, source);
        if (swap && destination != null) next.inboxes.put(from, destination);
        next.inboxes.replaceAll((owner, inbox) -> inbox.stream().map(m -> new Message(m.id,
                m.sender.equals(from) ? to : swap && m.sender.equals(to) ? from : m.sender,
                m.senderName, m.sentAt, m.body, m.important, m.attachment, m.read, m.claimed)).toList());
        return next;
    }
}
