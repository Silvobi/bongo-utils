package pl.bongo.bongoutils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiPredicate;

/** Server-thread mailbox state. Delivery and the sender's copy share one atomic write. */
public final class MailStore {
    public static final int MAX_BODY = 4000, MAX_TITLE = 120;
    public enum Folder { NEW, ALL, SENT, RECEIVED }
    public record Message(UUID id, UUID sender, String senderName, long sentAt, String body,
                          boolean important, List<String> attachments, boolean read,
                          String title, String englishTitle, String englishBody, List<UUID> recipients) {
        public Message {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
            recipients = recipients == null ? List.of() : List.copyOf(recipients);
            title = title == null ? legacyTitle(body) : title;
            englishTitle = englishTitle == null ? "" : englishTitle;
            englishBody = englishBody == null ? "" : englishBody;
        }
        public Message(UUID id, UUID sender, String senderName, long sentAt, String body,
                       boolean important, List<String> attachments, boolean read) {
            this(id, sender, senderName, sentAt, body, important, attachments, read, null, "", "", List.of());
        }
        Message readCopy() { return copy(sender, recipients, true); }
        Message copy(UUID author, List<UUID> targets, boolean seen) {
            return new Message(id, author, senderName, sentAt, body, important, attachments, seen, title, englishTitle, englishBody, targets);
        }
        public String body(String locale) { return important && !Lang.language(locale).equals("pl") && !englishBody.isBlank() ? englishBody : body; }
        public String title(String locale) { return important && !Lang.language(locale).equals("pl") && !englishTitle.isBlank() ? englishTitle : title; }
    }
    private static String legacyTitle(String body) {
        if (body == null) return "";
        String line = body.replaceAll("\\s+", " ").strip();
        return line.length() > 70 ? line.substring(0, 69) + "…" : line;
    }
    public static final class Data {
        public int schemaVersion = 3;
        public Map<UUID, List<Message>> inboxes = new HashMap<>();
        public Map<UUID, List<Message>> sentboxes = new HashMap<>();
    }
    private final Path file;
    private Data data;
    public MailStore(Path file) throws IOException { this.file = file; reload(); }
    public void reload() throws IOException {
        try {
            Data next = Files.exists(file) ? Store.JSON.fromJson(Files.readString(file), Data.class) : new Data();
            if (next == null || next.schemaVersion < 1 || next.schemaVersion > 3 || next.inboxes == null || next.sentboxes == null) throw new IllegalArgumentException();
            validateBoxes(next.inboxes); validateBoxes(next.sentboxes);
            if (next.schemaVersion < 3) recoverSent(next);
            next.schemaVersion = 3;
            data = next;
        } catch (RuntimeException e) { throw new IOException("Invalid mail.json", e); }
    }
    private static void validateBoxes(Map<UUID, List<Message>> boxes) {
        for (var box : boxes.entrySet()) {
            if (box.getKey() == null || box.getValue() == null) throw new IllegalArgumentException();
            Set<UUID> ids = new HashSet<>();
            for (Message m : box.getValue()) {
                if (m == null || m.id == null || !ids.add(m.id) || m.sender == null || m.senderName == null) throw new IllegalArgumentException();
                validateText(m.title, m.body);
                if (!m.englishBody.isEmpty() || !m.englishTitle.isEmpty()) validateText(m.englishTitle, m.englishBody);
                MailAttachments.validate(m.attachments);
            }
        }
    }
    /** Older versions kept only received copies. Recover outgoing history from those copies. */
    private static void recoverSent(Data next) {
        Map<UUID, Message> messages = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> recipients = new HashMap<>();
        next.inboxes.forEach((owner, inbox) -> inbox.forEach(m -> {
            messages.putIfAbsent(m.id, m);
            recipients.computeIfAbsent(m.id, ignored -> new TreeSet<>()).add(owner);
        }));
        for (Message m : messages.values()) next.sentboxes.computeIfAbsent(m.sender, ignored -> new ArrayList<>())
                .add(m.copy(m.sender, List.copyOf(recipients.get(m.id)), true));
        next.sentboxes.values().forEach(box -> box.sort(Comparator.comparingLong(Message::sentAt).thenComparing(Message::id)));
    }
    public Data snapshot() { return Store.JSON.fromJson(Store.JSON.toJson(data), Data.class); }
    private void replace(Data next) throws IOException { Store.atomic(file, Store.JSON.toJson(next)); data = next; }
    public boolean hasInbox(UUID owner) { return !data.inboxes.getOrDefault(owner, List.of()).isEmpty() || !data.sentboxes.getOrDefault(owner, List.of()).isEmpty(); }
    public void send(UUID sender, String name, Collection<UUID> recipients, String body, boolean important, List<String> attachments) throws IOException {
        deliver(sender, name, recipients, legacyTitle(body), body, "", "", important, attachments);
    }
    public void send(UUID sender, String name, Collection<UUID> recipients, String title, String body,
                     String englishTitle, String englishBody, boolean important) throws IOException {
        if (important) validateText(englishTitle, englishBody);
        deliver(sender, name, recipients, title, body, important ? englishTitle : "", important ? englishBody : "", important, List.of());
    }
    static void validateText(String title, String body) {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE || title.codePoints().anyMatch(Character::isISOControl)
                || body == null || body.isBlank() || body.length() > MAX_BODY) throw new IllegalArgumentException("Invalid mail title/body");
    }
    private void deliver(UUID sender, String name, Collection<UUID> recipients, String title, String body,
                         String englishTitle, String englishBody, boolean important, List<String> attachments) throws IOException {
        Objects.requireNonNull(sender); Store.key(name); validateText(title, body);
        Set<UUID> unique = new LinkedHashSet<>(recipients);
        if (unique.isEmpty() || unique.contains(null)) throw new IllegalArgumentException("Missing recipients");
        List<String> links = MailAttachments.validate(attachments);
        Data next = snapshot();
        // Recipient lists belong exclusively to the sender's copy (no BCC disclosure).
        Message message = new Message(UUID.randomUUID(), sender, name, System.currentTimeMillis(), body, important, links, false,
                title, englishTitle, englishBody, List.of());
        for (UUID recipient : unique) next.inboxes.computeIfAbsent(recipient, ignored -> new ArrayList<>()).add(message);
        next.sentboxes.computeIfAbsent(sender, ignored -> new ArrayList<>()).add(message.copy(sender, List.copyOf(unique), true));
        replace(next);
    }
    public static boolean visible(UUID viewer, Message message, BiPredicate<UUID, UUID> blocked) {
        return viewer.equals(message.sender) || message.important || !blocked.test(viewer, message.sender);
    }
    public List<Message> inbox(UUID owner, boolean unreadOnly, BiPredicate<UUID, UUID> blocked) {
        return data.inboxes.getOrDefault(owner, List.of()).reversed().stream()
                .filter(m -> (!unreadOnly || !m.read) && visible(owner, m, blocked)).toList();
    }
    public List<Message> list(UUID owner, Folder folder, BiPredicate<UUID, UUID> blocked) {
        if (folder == Folder.NEW || folder == Folder.RECEIVED) return inbox(owner, folder == Folder.NEW, blocked);
        List<Message> sent = data.sentboxes.getOrDefault(owner, List.of()).reversed();
        if (folder == Folder.SENT) return List.copyOf(sent);
        Map<UUID, Message> all = new LinkedHashMap<>();
        sent.forEach(m -> all.put(m.id, m));
        inbox(owner, false, blocked).forEach(m -> all.putIfAbsent(m.id, m));
        return all.values().stream().sorted(Comparator.comparingLong(Message::sentAt).reversed()).toList();
    }
    public Message find(UUID owner, UUID id) {
        return data.inboxes.getOrDefault(owner, List.of()).stream().filter(m -> m.id.equals(id)).findFirst().orElse(null);
    }
    public Message sent(UUID owner, UUID id) {
        return data.sentboxes.getOrDefault(owner, List.of()).stream().filter(m -> m.id.equals(id)).findFirst().orElse(null);
    }
    public Message read(UUID owner, UUID id) throws IOException {
        Message message = find(owner, id);
        if (message == null || message.read) return message;
        Message next = message.readCopy();
        Data copy = snapshot(); List<Message> inbox = copy.inboxes.get(owner);
        for (int i = 0; i < inbox.size(); i++) if (inbox.get(i).id.equals(id)) { inbox.set(i, next); break; }
        replace(copy); return next;
    }
    public static Data migrate(Data original, UUID from, UUID to, boolean swap) {
        Data next = Store.JSON.fromJson(Store.JSON.toJson(original), Data.class);
        migrateBoxes(next.inboxes, from, to, swap); migrateBoxes(next.sentboxes, from, to, swap);
        return next;
    }
    private static UUID migrateId(UUID id, UUID from, UUID to, boolean swap) { return id.equals(from) ? to : swap && id.equals(to) ? from : id; }
    private static void migrateBoxes(Map<UUID,List<Message>> boxes, UUID from, UUID to, boolean swap) {
        List<Message> source = boxes.remove(from), destination = boxes.remove(to);
        if (source != null) boxes.put(to, source);
        if (swap && destination != null) boxes.put(from, destination);
        boxes.replaceAll((owner, box) -> box.stream().map(m -> m.copy(migrateId(m.sender, from, to, swap),
                m.recipients.stream().map(id -> migrateId(id, from, to, swap)).toList(), m.read)).toList());
    }
}
