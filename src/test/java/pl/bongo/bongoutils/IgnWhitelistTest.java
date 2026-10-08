package pl.bongo.bongoutils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IgnWhitelistTest {
    @TempDir Path directory;
    @Test void storesNamesAndModeAcrossRestartsWithoutUuid() throws Exception {
        Path file = directory.resolve("ign-whitelist.json");
        IgnWhitelist first = new IgnWhitelist(file);
        assertFalse(first.enabled());
        assertEquals(2, first.add(List.of("Bongo", "OfflinePlayer")));
        assertTrue(first.setEnabled(true));
        IgnWhitelist next = new IgnWhitelist(file);
        assertTrue(next.enabled());
        assertTrue(next.contains("BONGO"));
        assertTrue(next.contains("offlineplayer"));
        assertFalse(next.contains("Stranger"));
        assertFalse(next.contains(null));
        assertFalse(Files.readString(file).toLowerCase().contains("uuid"));
    }
    @Test void mergesCaseVariantsAndRemovesByName() throws Exception {
        IgnWhitelist whitelist = new IgnWhitelist(directory.resolve("ign-whitelist.json"));
        assertEquals(1, whitelist.add(List.of("Bongo", "bOnGo")));
        assertEquals(0, whitelist.add(List.of("BONGO")));
        assertEquals(List.of("Bongo"), whitelist.names());
        assertEquals(1, whitelist.remove(List.of("bongo", "BONGO")));
        assertFalse(whitelist.contains("Bongo"));
        assertEquals(0, whitelist.remove(List.of("Bongo")));
    }
    @Test void invalidBatchCannotPartiallyChangeGate() throws Exception {
        IgnWhitelist whitelist = new IgnWhitelist(directory.resolve("ign-whitelist.json"));
        whitelist.add(List.of("Bongo")); whitelist.setEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> whitelist.add(List.of("NewPlayer", "../bad")));
        assertFalse(whitelist.contains("NewPlayer"));
        assertThrows(IllegalArgumentException.class, () -> whitelist.remove(List.of("Bongo", "@a")));
        assertTrue(whitelist.contains("Bongo"));
    }
    @Test void reloadIsAtomicAndRejectsBrokenOrIncompleteFiles() throws Exception {
        Path file = directory.resolve("ign-whitelist.json");
        IgnWhitelist whitelist = new IgnWhitelist(file);
        whitelist.add(List.of("Bongo")); whitelist.setEnabled(true);
        for (String bad : List.of("{}", "null", "broken", "{\"enabled\":false,\"names\":[\"../bad\"]}",
                "{\"enabled\":false,\"names\":[\"Bongo\",\"BONGO\"]}")) {
            Files.writeString(file, bad);
            assertThrows(IOException.class, whitelist::reload);
            assertTrue(whitelist.enabled()); assertEquals(List.of("Bongo"), whitelist.names());
        }
        Files.writeString(file, "{\"enabled\":false,\"names\":[\"NewPlayer\"]}");
        whitelist.reload(); assertFalse(whitelist.enabled());
        assertTrue(whitelist.contains("newplayer")); assertFalse(whitelist.contains("Bongo"));
    }
}
