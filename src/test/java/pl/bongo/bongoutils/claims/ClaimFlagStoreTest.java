package pl.bongo.bongoutils.claims;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ClaimFlagStoreTest {
    @TempDir Path root;
    @Test void explicitOffBeatsGlobalOnUntilResetAndSurvivesRestart() {
        UUID owner=UUID.randomUUID();Path file=root.resolve("flags.json");var store=new ClaimFlagStore(file);
        store.owner(owner,"pads","on");store.local("world:1:owner","pads","off");
        assertEquals("off",store.value("world:1:owner",owner,"pads","off"));
        assertEquals("on",store.value("world:2:owner",owner,"pads","off"));
        store=new ClaimFlagStore(file);assertEquals("off",store.value("world:1:owner",owner,"pads","off"));
        store.local("world:1:owner","pads",null);assertEquals("on",store.value("world:1:owner",owner,"pads","off"));
        store.owner(owner,"pads",null);assertEquals("off",store.value("world:1:owner",owner,"pads","off"));
    }
    @Test void independentFlagsClaimsAndOwnersAndLegacyImportNeverResurrectsReset() {
        var store=new ClaimFlagStore(root.resolve("flags.json"));UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        store.owner(owner,"pads","on");store.owner(owner,"clans","build");store.local("sub","clans","off");
        assertEquals("on",store.value("sub",owner,"pads","off"));assertEquals("off",store.value("sub",owner,"clans","build"));
        assertEquals("off",store.value("other",other,"pads","off"));
        store.legacy("sub","pads","on");store.local("sub","pads",null);store.owner(owner,"pads","off");store.legacy("sub","pads","on");
        assertNull(store.local("sub","pads"));assertEquals("off",store.value("sub",owner,"pads","off"));
    }
    @Test void nonDefaultMigrationRunsOnlyOnceAndDoesNotReimportAppliedGlobals() {
        var store=new ClaimFlagStore(root.resolve("flags.json"));UUID owner=UUID.randomUUID();
        store.discover(Map.of("one",Map.of("explosions","on"),"two",Map.of()));store.owner(owner,"explosions","off");
        assertEquals("on",store.value("one",owner,"explosions","off"));assertEquals("off",store.value("two",owner,"explosions","off"));
        store.discover(Map.of("two",Map.of("explosions","on")));assertNull(store.local("two","explosions"));
    }
    @Test void failedWritesAndInvalidReadsPreserveLastGoodState() throws Exception {
        Path file=root.resolve("flags.json");var store=new ClaimFlagStore(file);UUID owner=UUID.randomUUID();store.owner(owner,"pads","off");long revision=store.revision();
        Files.createDirectory(root.resolve("flags.json.tmp"));
        assertThrows(java.io.UncheckedIOException.class,()->store.owner(owner,"pads","on"));assertEquals(revision,store.revision());assertEquals("off",store.owner(owner,"pads"));
        Files.writeString(file,"{broken");assertThrows(IllegalStateException.class,()->new ClaimFlagStore(file));assertEquals("{broken",Files.readString(file));
    }
}
