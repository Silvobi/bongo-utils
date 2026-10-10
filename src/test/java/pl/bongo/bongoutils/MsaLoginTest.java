package pl.bongo.bongoutils;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static pl.bongo.bongoutils.MsaLogin.Route.*;

class MsaLoginTest {
    @TempDir Path directory;
    @Test void occupiedNameCannotBecomeOfflineEvenBeforeOwnerHasVisited() {
        UUID owner = UUID.randomUUID();
        assertEquals(OCCUPIED_MSA, MsaLogin.route(owner, UUID.randomUUID(), false, true));
        assertEquals(OCCUPIED_MSA, MsaLogin.route(owner, null, false, true));
        assertEquals(VERIFY_SESSION, MsaLogin.route(owner, owner, false, true));
        assertEquals(VERIFY_SESSION, MsaLogin.route(owner, owner, true, false));
    }
    @Test void localReservationSurvivesMissingRemoteProfileAndOfflineDisabled() {
        assertEquals(RESERVED_MSA, MsaLogin.route(null, UUID.randomUUID(), true, true));
        assertEquals(RESERVED_MSA, MsaLogin.route(UUID.randomUUID(), UUID.randomUUID(), true, false));
        assertEquals(OFFLINE, MsaLogin.route(null, UUID.randomUUID(), false, true));
        assertEquals(OFFLINE_DISABLED, MsaLogin.route(null, null, false, false));
    }
    @Test void firstMsaRegistrationPersistsAndOverridesAnOldOfflinePassword() throws Exception {
        Store store = new Store(directory);
        assertTrue(store.register("MsaOwner", "old-offline-hash"));
        UUID owner = UUID.randomUUID();
        assertTrue(store.reserve("MSAOWNER", owner));
        Store reopened = new Store(directory);
        assertTrue(reopened.reserved("msaowner"));
        assertFalse(reopened.reserve("MsaOwner", owner));
        assertEquals(RESERVED_MSA, MsaLogin.route(owner, UUID.randomUUID(), reopened.reserved("mSaOwNeR"), true));
        assertFalse(reopened.register("MsaOwner", "replacement-hash"));
        // Nick ownership may legitimately change; a verified new owner updates the stored UUID.
        UUID nextOwner = UUID.randomUUID();
        assertFalse(reopened.reserve("MsaOwner", nextOwner));
        assertFalse(new Store(directory).reserve("msaowner", nextOwner));
    }
    @Test void claimedUuidAndProfileLookupAloneDoNotRegisterMsa() throws Exception {
        Store store = new Store(directory);
        UUID owner = UUID.randomUUID();
        assertEquals(VERIFY_SESSION, MsaLogin.route(owner, owner, false, true));
        assertNull(MsaLogin.registerJoined(store, "Unverified", owner, false));
        assertFalse(store.reserved("Unverified"));
        assertEquals(OCCUPIED_MSA, MsaLogin.route(owner, UUID.randomUUID(), false, true));
        assertFalse(new Store(directory).reserved("Unverified"));
    }
    @Test void greenPrivateWelcomeIsPersistedOnceOnlyForVerifiedJoin() throws Exception {
        Store store = new Store(directory);
        UUID owner = UUID.randomUUID();
        var welcome = MsaLogin.registerJoined(store, "MsaOwner", owner, true, "pl_pl");
        assertNotNull(welcome);
        assertTrue(store.reserved("MSAOWNER"));
        assertNull(MsaLogin.registerJoined(store, "MsaOwner", owner, true)); // configuration refresh
        assertNull(MsaLogin.registerJoined(new Store(directory), "msaowner", owner, true)); // restart/reconnect
        assertEquals("Połączono z konta MSA. Gracz został zarejestrowany automatycznie", welcome.getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), welcome.getStyle().getColor());
    }
    @Test void rejectionExplainsLocalReservationOrGlobalOccupationWithOriginalCasing() {
        String local = MsaLogin.rejection("pl_pl", RESERVED_MSA, "Someone");
        assertEquals(3, local.lines().count());
        assertTrue(local.contains("zarejestrowane jako \"Konto MSA\""));
        String global = MsaLogin.rejection("pl_pl", OCCUPIED_MSA, "MiXeDCaSe");
        assertEquals(2, global.lines().count());
        assertTrue(global.startsWith("IGN MiXeDCaSe jest już zajęty przez inne konto MSA!\n"));
    }
}
