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
        store.send(sender, "Sender", List.of(target), "first", false, null);
        store.send(sender, "Sender", List.of(target), "second", false, null);
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
        store.send(sender, "Sender", List.of(target), "private", false, null);
        store.send(sender, "Sender", List.of(target), "admin", true, null);
        social.toggleIgnore(sender, target);
        assertEquals(List.of("admin"), store.inbox(target, true, social::blocked).stream().map(MailStore.Message::body).toList());
        social.toggleIgnore(sender, target); social.toggleIgnore(target, sender);
        assertEquals(1, store.inbox(target, false, social::blocked).size());
        social.toggleIgnore(target, sender);
        assertEquals(2, store.inbox(target, true, social::blocked).size());
    }
    @Test void batchDeduplicatesRecipientsAndReadAndClaimStateAreIndependent() throws Exception {
        MailStore store = store();
        store.send(sender, "Sender", List.of(target, other, target), "broadcast", true, "item-json");
        var a = store.inbox(target, true, (x, y) -> false).getFirst();
        assertEquals(1, store.inbox(target, true, (x, y) -> false).size());
        assertFalse(store.claim(sender, a.id()));
        assertTrue(store.claim(target, a.id()));
        assertFalse(store.claim(target, a.id()));
        assertFalse(store().claim(target, a.id()));
        assertTrue(store.claim(other, a.id()));
        store.read(target, a.id());
        assertTrue(store.inbox(other, true, (x, y) -> false).getFirst().claimed());
        assertEquals(1, store.inbox(other, true, (x, y) -> false).size());
    }
    @Test void validationAndFailedWritesNeverPublishPartialDeliveryOrConsumeClaims() throws Exception {
        MailStore store = store();
        for (String body : new String[]{"", "  \n ", "x".repeat(MailStore.MAX_BODY + 1)})
            assertThrows(IllegalArgumentException.class, () -> store.send(sender, "Sender", List.of(target), body, false, null));
        assertThrows(IllegalArgumentException.class, () -> store.send(sender, "../bad", List.of(target), "body", false, null));
        assertThrows(IllegalArgumentException.class, () -> store.send(sender, "Sender", List.of(), "body", false, null));
        store.send(sender, "Sender", List.of(target), "saved", true, "item-json");
        UUID id = store.inbox(target, false, (a, b) -> false).getFirst().id();
        Files.delete(root.resolve("mail.json")); Files.createDirectory(root.resolve("mail.json"));
        assertThrows(IOException.class, () -> store.send(sender, "Sender", List.of(target, other), "failed", true, null));
        assertThrows(IOException.class, () -> store.claim(target, id));
        assertThrows(IOException.class, () -> store.read(target, id));
        assertFalse(store.find(target, id).read()); assertFalse(store.find(target, id).claimed());
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
    @Test void migrationMovesOrSwapsMailboxesAndPreservesClaimsAndAuthorPrivacyIdentity() throws Exception {
        MailStore store = store();
        store.send(sender, "Sender", List.of(sender, target, other), "source", true, "item");
        store.send(target, "Target", List.of(target), "destination", false, null);
        UUID id = store.inbox(sender, false, (a, b) -> false).getFirst().id();
        store.claim(sender, id); store.read(sender, id);
        var moved = MailStore.migrate(store.snapshot(), sender, target, false);
        assertFalse(moved.inboxes.containsKey(sender));
        assertEquals(1, moved.inboxes.get(target).size());
        assertTrue(moved.inboxes.get(target).getFirst().claimed());
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
