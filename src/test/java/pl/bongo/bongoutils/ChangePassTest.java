package pl.bongo.bongoutils;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ChangePassTest {
    @TempDir Path root;
    @Test void replacementPersistsAndOnlyNewPasswordMatches() throws Exception {
        Store store = new Store(root);
        String first = PasswordHash.create("old-test-password");
        String next = PasswordHash.create("new-test-password");
        assertTrue(store.register("Target", first));
        assertTrue(store.editOfflinePassword("tArGeT", first, next));
        String saved = new Store(root).offlinePassword("TARGET");
        assertTrue(PasswordHash.verify("new-test-password", saved));
        assertFalse(PasswordHash.verify("old-test-password", saved));
        assertFalse(Files.readString(root.resolve("accounts/target.json")).contains("new-test-password"));
        assertFalse(store.matchesOfflinePassword("Target", first));
    }
    @Test void clearDeletesHashAndAllowsRegistrationAgain() throws Exception {
        Store store = new Store(root); store.register("Target", "first-hash");
        assertTrue(store.editOfflinePassword("TARGET", "first-hash", null));
        assertFalse(Files.exists(root.resolve("accounts/target.json")));
        assertNull(new Store(root).offlinePassword("target"));
        assertFalse(store.matchesOfflinePassword("target", "first-hash"));
        assertTrue(store.register("target", "re-registered-hash"));
    }
    @Test void unknownOrUnregisteredAccountIsNeverCreatedByAdminEdit() throws Exception {
        Store store = new Store(root);
        assertFalse(store.editOfflinePassword("Missing", "invented-hash", "new-hash"));
        assertFalse(store.editOfflinePassword("Missing", null, "new-hash"));
        assertFalse(store.editOfflinePassword("Missing", null, null));
        assertNull(store.password("Missing"));
        assertThrows(IllegalArgumentException.class, () -> store.editOfflinePassword("../other", "hash", null));
    }
    @Test void msaReservationProtectsEvenAnOldOfflineHash() throws Exception {
        Store store = new Store(root); store.register("Target", "old-offline-hash");
        store.reserve("Target", UUID.randomUUID());
        assertNull(store.offlinePassword("Target"));
        assertFalse(store.editOfflinePassword("Target", "old-offline-hash", "new-hash"));
        assertFalse(store.editOfflinePassword("Target", "old-offline-hash", null));
        assertEquals("old-offline-hash", new Store(root).password("Target"));
    }
    @Test void staleDialogAndAuthenticationSnapshotCannotOverrideNewCredentials() throws Exception {
        Store store = new Store(root); store.register("Target", "first-hash");
        assertTrue(store.editOfflinePassword("Target", "first-hash", "second-hash"));
        assertFalse(store.editOfflinePassword("target", "first-hash", "stale-edit"));
        assertFalse(store.editOfflinePassword("target", "first-hash", null));
        assertFalse(store.matchesOfflinePassword("target", "first-hash"));
        assertTrue(store.matchesOfflinePassword("target", "second-hash"));
        assertTrue(store.editOfflinePassword("target", "second-hash", null));
        store.register("target", "third-hash");
        assertFalse(store.editOfflinePassword("target", "second-hash", "stale-edit"));
        assertEquals("third-hash", store.offlinePassword("Target"));
    }
    @Test void corruptedAccountIsNotTreatedAsAnUnregisteredAccount() throws Exception {
        Store store = new Store(root);
        Path file = root.resolve("accounts/target.json");
        for (String invalid : new String[]{"null", "{}", "{\"hash\":null}", "{broken"}) {
            Files.writeString(file, invalid);
            assertThrows(java.io.IOException.class, () -> store.offlinePassword("Target"));
            assertThrows(java.io.IOException.class, () -> store.editOfflinePassword("Target", "hash", null));
            assertEquals(invalid, Files.readString(file));
        }
    }
}
