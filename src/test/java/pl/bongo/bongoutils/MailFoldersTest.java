package pl.bongo.bongoutils;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MailFoldersTest {
    @TempDir Path root;
    final UUID sender = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    @Test void sentCopiesPersistDeduplicateAllAndNeverReadRecipientsMail() throws Exception {
        var store = new MailStore(root.resolve("mail.json"));
        store.send(sender,"Admin",List.of(a,b,a,sender),"Temat","Polski","Subject","English",true);
        var sent = store.list(sender,MailStore.Folder.SENT,(x,y)->false);
        assertEquals(1,sent.size()); assertEquals(List.of(a,b,sender),sent.getFirst().recipients());
        assertEquals(1,store.list(sender,MailStore.Folder.ALL,(x,y)->false).size());
        assertEquals(1,store.list(sender,MailStore.Folder.NEW,(x,y)->false).size());
        assertEquals(1,store.list(a,MailStore.Folder.RECEIVED,(x,y)->true).size());
        assertTrue(store.find(a,sent.getFirst().id()).recipients().isEmpty());
        store.read(a,sent.getFirst().id());
        var reloaded = new MailStore(root.resolve("mail.json"));
        assertEquals(1,reloaded.list(sender,MailStore.Folder.SENT,(x,y)->true).size());
        assertTrue(reloaded.list(a,MailStore.Folder.NEW,(x,y)->false).isEmpty());
        assertEquals(1,reloaded.list(b,MailStore.Folder.NEW,(x,y)->false).size());
        assertEquals("⚠ Alice, Bob, Admin - Temat",MailUi.label(sent.getFirst(),sender,"pl_pl",id->id.equals(a)?"Alice":id.equals(b)?"Bob":"Admin"));
        assertEquals("⚠ Admin - Subject",MailUi.label(reloaded.find(a,sent.getFirst().id()),a,"de_de",id->"Recipient"));
        assertEquals("English",sent.getFirst().body("de_de"));
        assertEquals("Polski",sent.getFirst().body("pl_pl"));
    }
    @Test void legacyHistoryRecoversSendersCopyAndMigratesBothDirections() throws Exception {
        var data = new MailStore.Data(); data.schemaVersion=2;
        var legacy = new MailStore.Message(UUID.randomUUID(),sender,"Sender",1,"Old text",false,List.of("https://example.com"),false);
        data.inboxes.put(a,new ArrayList<>(List.of(legacy))); data.inboxes.put(b,new ArrayList<>(List.of(legacy)));
        Files.writeString(root.resolve("mail.json"),Store.JSON.toJson(data));
        var store = new MailStore(root.resolve("mail.json"));
        var sent = store.list(sender,MailStore.Folder.SENT,(x,y)->false).getFirst();
        assertEquals(Set.of(a,b),new HashSet<>(sent.recipients()));
        assertEquals("Old text",sent.title()); assertFalse(store.find(a,sent.id()).read());
        var moved = MailStore.migrate(store.snapshot(),sender,a,true);
        assertEquals(a,moved.sentboxes.get(a).getFirst().sender());
        assertTrue(moved.sentboxes.get(a).getFirst().recipients().contains(sender));
        assertEquals(a,moved.inboxes.get(b).getFirst().sender());
    }
    @Test void failedWriteDoesNotCreateOutgoingCopyAndBothTranslationsAreRequired() throws Exception {
        var store = new MailStore(root.resolve("mail.json"));
        assertThrows(IllegalArgumentException.class,()->store.send(sender,"Sender",List.of(a),"PL","Body","","",true));
        Files.createDirectory(root.resolve("mail.json"));
        assertThrows(java.io.IOException.class,()->store.send(sender,"Sender",List.of(a),"Title","Body","","",false));
        assertTrue(store.list(sender,MailStore.Folder.SENT,(x,y)->false).isEmpty());
        assertTrue(store.inbox(a,false,(x,y)->false).isEmpty());
    }
}
