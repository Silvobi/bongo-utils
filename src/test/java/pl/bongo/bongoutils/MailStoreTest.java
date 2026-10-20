package pl.bongo.bongoutils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MailStoreTest {
    @TempDir Path root;
    final UUID sender = UUID.randomUUID(), target = UUID.randomUUID(), other = UUID.randomUUID();
    MailStore store() throws IOException { return new MailStore(root.resolve("mail.json")); }
    @Test void offlineMailPersistsAndReadingOneMessageDoesNotReadTheList() throws Exception {
        MailStore store = store();
        store.send(sender, "Sender", List.of(target), "first", false, List.of());
        store.send(sender, "Sender", List.of(target), "second", false, List.of());
        List<MailStore.Message> mails = store.inbox(target, true, (a, b) -> false);
        assertEquals(List.of("second", "first"), mails.stream().map(MailStore.Message::body).toList());
        assertEquals(2, store.inbox(target, true, (a, b) -> false).size());
        assertNull(store.read(other, mails.getFirst().id()));
        store.read(target, mails.getFirst().id());
        MailStore reopened = store();
        assertEquals(List.of("first"), reopened.inbox(target, true, (a, b) -> false).stream().map(MailStore.Message::body).toList());
        assertEquals(2, reopened.inbox(target, false, (a, b) -> false).size());
    }
    @Test void ignoredHistoryIsHiddenAndAdminMailAlwaysRemainsVisible() throws Exception {
        SocialState social = new SocialState(root.resolve("social.json"));
        MailStore store = store();
        store.send(sender, "Sender", List.of(target), "private", false, List.of());
        store.send(sender, "Sender", List.of(target), "admin", true, List.of());
        social.toggleIgnore(sender, target);
        assertEquals(List.of("admin"), store.inbox(target, true, social::blocked).stream().map(MailStore.Message::body).toList());
        social.toggleIgnore(sender, target); social.toggleIgnore(target, sender);
        assertEquals(1, store.inbox(target, false, social::blocked).size());
        social.toggleIgnore(target, sender);
        assertEquals(2, store.inbox(target, true, social::blocked).size());
    }
    @Test void batchDeduplicatesRecipientsAndLinksPersistWithIndependentReadState() throws Exception {
        MailStore store = store();
        List<String> links = List.of("https://example.com/document.pdf?token=a%2Fb", "http://example.org/image.png");
        store.send(sender, "Sender", List.of(target, other, target), "broadcast", true, links);
        var a = store.inbox(target, true, (x, y) -> false).getFirst();
        assertEquals(1, store.inbox(target, true, (x, y) -> false).size());
        store.read(target, a.id());
        MailStore reopened = store();
        assertEquals(links, reopened.find(target, a.id()).attachments());
        assertEquals(links, reopened.inbox(other, true, (x, y) -> false).getFirst().attachments());
        assertEquals(0, reopened.inbox(target, true, (x, y) -> false).size());
        assertEquals(1, reopened.inbox(other, true, (x, y) -> false).size());
    }
    @Test void validationAndFailedWritesNeverPublishPartialDeliveryOrReadState() throws Exception {
        MailStore store = store();
        for (String body : new String[]{"", "  \n ", "x".repeat(MailStore.MAX_BODY + 1)})
            assertThrows(IllegalArgumentException.class, () -> store.send(sender, "Sender", List.of(target), body, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> store.send(sender, "../bad", List.of(target), "body", false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> store.send(sender, "Sender", List.of(), "body", false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> store.send(sender, "Sender", List.of(target), "body", true, List.of("javascript:alert(1)")));
        store.send(sender, "Sender", List.of(target), "saved", true, List.of("https://example.com/a"));
        UUID id = store.inbox(target, false, (a, b) -> false).getFirst().id();
        Files.delete(root.resolve("mail.json")); Files.createDirectory(root.resolve("mail.json"));
        assertThrows(IOException.class, () -> store.send(sender, "Sender", List.of(target, other), "failed", true, List.of()));
        assertThrows(IOException.class, () -> store.read(target, id));
        assertFalse(store.find(target, id).read());
        assertFalse(store.hasInbox(other));
        assertEquals(1, store.inbox(target, false, (a, b) -> false).size());
    }
    @Test void corruptOrUnsupportedStorageFailsClosedWithoutOverwritingIt() throws Exception {
        for (String json : List.of("null", "{broken", "{\"schemaVersion\":99}", "{\"inboxes\":null}",
                "{\"inboxes\":{\"" + target + "\":[null]}}")) {
            Files.writeString(root.resolve("mail.json"), json);
            assertThrows(IOException.class, this::store);
            assertEquals(json, Files.readString(root.resolve("mail.json")));
        }
    }
    @Test void itemPrototypeStorageUpgradesWithoutLosingMessageTextOrReadState() throws Exception {
        String old = "{\"schemaVersion\":1,\"inboxes\":{\"" + target + "\":[{\"id\":\"" + UUID.randomUUID()
                + "\",\"sender\":\"" + sender + "\",\"senderName\":\"Sender\",\"sentAt\":1,\"body\":\"original text\","
                + "\"important\":true,\"read\":true,\"attachment\":\"item-json\",\"claimed\":false}]}}";
        Files.writeString(root.resolve("mail.json"), old);
        MailStore upgraded = store();
        var mail = upgraded.inbox(target, false, (a, b) -> false).getFirst();
        assertEquals("original text", mail.body()); assertTrue(mail.read()); assertTrue(mail.important());
        assertTrue(mail.attachments().isEmpty()); assertEquals(2, upgraded.snapshot().schemaVersion);
        upgraded.send(sender, "Sender", List.of(target), "new text", true, List.of("https://example.com/a"));
        assertEquals(2, store().inbox(target, false, (a, b) -> false).size());
        assertFalse(Files.readString(root.resolve("mail.json")).contains("item-json"));
    }
    @Test void migrationMovesOrSwapsMailboxesAndPreservesLinksAndAuthorPrivacyIdentity() throws Exception {
        MailStore store = store();
        store.send(sender, "Sender", List.of(sender, target, other), "source", true, List.of("https://example.com/a"));
        store.send(target, "Target", List.of(target), "destination", false, List.of());
        UUID id = store.inbox(sender, false, (a, b) -> false).getFirst().id();
        store.read(sender, id);
        var moved = MailStore.migrate(store.snapshot(), sender, target, false);
        assertFalse(moved.inboxes.containsKey(sender));
        assertEquals(1, moved.inboxes.get(target).size());
        assertEquals(List.of("https://example.com/a"), moved.inboxes.get(target).getFirst().attachments());
        assertTrue(moved.inboxes.get(target).getFirst().read());
        assertEquals(target, moved.inboxes.get(other).getFirst().sender());
        var swapped = MailStore.migrate(store.snapshot(), sender, target, true);
        assertEquals(2, swapped.inboxes.get(sender).size());
        assertEquals(sender, swapped.inboxes.get(sender).getLast().sender());
        assertEquals(target, swapped.inboxes.get(target).getFirst().sender());
        assertEquals(0, store.inbox(sender, true, (a, b) -> false).size());
        assertEquals(1, store.inbox(sender, false, (a, b) -> false).size());
    }
    @Test void deliveryHonorsBilateralIgnoreAndFullVanishButAllowsSemiVanishAndAdminMail() throws Exception {
        SocialState previous = Social.state;
        try {
            Social.state = new SocialState(root.resolve("social.json"));
            Social.state.mode(target, SocialState.Mode.SEMI, false);
            assertTrue(MailUi.deliverable(sender, target, false));
            Social.state.toggleIgnore(target, sender);
            assertFalse(MailUi.deliverable(sender, target, false));
            assertFalse(MailUi.deliverable(target, sender, false));
            assertTrue(MailUi.deliverable(sender, target, true));
            Social.state.toggleIgnore(target, sender);
            Social.state.mode(target, SocialState.Mode.VANISH, false);
            assertFalse(MailUi.deliverable(sender, target, false));
            assertTrue(MailUi.deliverable(sender, target, true));
        } finally { Social.state = previous; }
    }
}
