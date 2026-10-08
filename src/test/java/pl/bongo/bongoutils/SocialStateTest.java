package pl.bongo.bongoutils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SocialStateTest {
    @TempDir Path directory;
    @Test void ignoreIsDirectedButCommunicationBlockIsBilateralAndPersists() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        SocialState state=new SocialState(directory.resolve("social.json"));
        state.remember(a,"Alice"); state.remember(b,"Bob");
        assertTrue(state.toggleIgnore(a,b)); assertTrue(state.blocked(a,b));assertTrue(state.blocked(b,a));
        assertTrue(state.ignores(a,b));assertFalse(state.ignores(b,a));
        SocialState loaded=new SocialState(directory.resolve("social.json"));
        assertTrue(loaded.blocked(b,a)); assertEquals(b,loaded.find("bOB"));
        assertFalse(loaded.toggleIgnore(a,b)); assertFalse(loaded.blocked(a,b));
        assertThrows(IllegalArgumentException.class,()->loaded.toggleIgnore(a,a));
    }
    @Test void visibilityAndFakeQuitPersistAndSwitchWithoutLeakingFlag() throws Exception {
        UUID a=UUID.randomUUID(); SocialState state=new SocialState(directory.resolve("social.json"));
        state.mode(a,SocialState.Mode.VANISH,true);
        state=new SocialState(directory.resolve("social.json")); assertEquals(SocialState.Mode.VANISH,state.mode(a));assertTrue(state.fakeQuit(a));
        state.mode(a,SocialState.Mode.SEMI,false); assertFalse(state.fakeQuit(a));
        state.mode(a,SocialState.Mode.VISIBLE,false);assertEquals(SocialState.Mode.VISIBLE,state.mode(a));
    }
    @Test void swapRemapsBothOwnersAndIncomingRelationsInOnePass() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        SocialState state=new SocialState(directory.resolve("social.json"));
        state.toggleIgnore(a,c); state.toggleIgnore(c,a); state.toggleIgnore(b,a);state.mode(a,SocialState.Mode.VANISH,true);
        var swapped=SocialState.migrate(state.snapshot(),a,b,"Alice","Bob",true);
        assertEquals(Set.of(c),swapped.ignores.get(b));assertEquals(Set.of(b),swapped.ignores.get(c));assertEquals(Set.of(b),swapped.ignores.get(a));
        assertEquals(SocialState.Mode.VANISH,swapped.modes.get(b));assertFalse(swapped.modes.containsKey(a));assertTrue(swapped.fakeQuit.contains(b));
        assertTrue(state.ignores(a,c)); // Original is untouched until persistence succeeds.
    }
    @Test void overrideDiscardsDestinationRelationsAndPreventsSelfIgnore() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        SocialState state=new SocialState(directory.resolve("social.json"));
        state.toggleIgnore(a,b);state.toggleIgnore(b,c);state.toggleIgnore(c,a);state.toggleIgnore(c,b);
        var moved=SocialState.migrate(state.snapshot(),a,b,"Alice","Bob",false);
        assertFalse(moved.ignores.containsKey(a));assertFalse(moved.ignores.containsKey(b));assertEquals(Set.of(b),moved.ignores.get(c));
    }
}
