package pl.bongo.bongoutils.social;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SocialTest {
    @TempDir Path root;
    @Test void thresholdUsesElapsedTimeAndAnnouncesOnlyTransitions() {
        var a=new Activity("world",0,1,0,10);
        assertFalse(a.sample("world",0,1,0,10+Activity.IDLE_NANOS-1));assertFalse(a.afk());
        assertTrue(a.sample("world",0,1,0,10+Activity.IDLE_NANOS));assertTrue(a.afk());
        assertFalse(a.sample("world",0,1,0,10+Activity.IDLE_NANOS+100));
    }
    @Test void movementAndDimensionClearManualAndAutomaticAfk() {
        var a=new Activity("world",0,1,0,0);a.toggle("world",0,1,0,1);
        assertTrue(a.sample("world",.1,1,0,2));assertFalse(a.afk());
        a.sample("world",.1,1,0,2+Activity.IDLE_NANOS);
        assertTrue(a.sample("nether",.1,1,0,3+Activity.IDLE_NANOS));assertFalse(a.afk());
    }
    @Test void SmallStepsAccumulateAndMovementRestartsTimer() {
        var a=new Activity("world",0,1,0,0);
        assertFalse(a.sample("world",.0005,1,0,Activity.IDLE_NANOS-2));
        assertFalse(a.sample("world",.0015,1,0,Activity.IDLE_NANOS-1));
        assertFalse(a.sample("world",.0015,1,0,Activity.IDLE_NANOS));assertFalse(a.afk());
        assertTrue(a.sample("world",.0015,1,0,2*Activity.IDLE_NANOS-1));
    }
    @Test void manualReturnRestartsTimer() {
        var a=new Activity("world",0,1,0,0);a.sample("world",0,1,0,Activity.IDLE_NANOS);
        a.toggle("world",0,1,0,Activity.IDLE_NANOS);assertFalse(a.afk());
        assertFalse(a.sample("world",0,1,0,Activity.IDLE_NANOS+1));
    }
    @Test void statusesSurviveRestartAndClearWithoutTouchingOtherPlayers() throws Exception {
        Path file=root.resolve("status.json");UUID a=UUID.randomUUID(),b=UUID.randomUUID();var store=new Statuses(file);
        store.set(a,"Buduję bazę");store.set(b,"Zwiedzam");store=new Statuses(file);
        assertEquals("Buduję bazę",store.get(a));store.set(a,null);store=new Statuses(file);
        assertNull(store.get(a));assertEquals("Zwiedzam",store.get(b));
    }
    @Test void rejectsSpoofedFormattingAndOversizedStatusBeforeWriting() throws Exception {
        var file=root.resolve("status.json");var store=new Statuses(file);UUID id=UUID.randomUUID();store.set(id,"OK");
        for(String text:new String[]{" ","x".repeat(33),"hello\nadmin","§4Admin","a\u202Eb"})
            assertThrows(IllegalArgumentException.class,()->store.set(id,text));
        assertEquals("OK",new Statuses(file).get(id));
    }
    @Test void failedWriteDoesNotPublishStatusInMemory() throws Exception {
        Path file=root.resolve("status.json");var store=new Statuses(file);UUID id=UUID.randomUUID();store.set(id,"Old");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"),"x");
        assertThrows(java.io.IOException.class,()->store.set(id,"New"));assertEquals("Old",store.get(id));
    }
}
