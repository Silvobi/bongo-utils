package pl.bongo.bongoutils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.UUIDUtil;
import static org.junit.jupiter.api.Assertions.*;
class MigrationTest {
    @TempDir Path directory;
    @Test void swapHandlesMissingFilesAndCreatesRestorableBeforeImages() throws Exception {
        Path a=directory.resolve("a"),b=directory.resolve("b"),c=directory.resolve("c"),d=directory.resolve("d");
        Files.writeString(a,"source");Files.writeString(b,"target");Files.writeString(c,"source-only");
        FileTransaction tx=new FileTransaction();tx.pair(a,b,true,(bytes,path)->bytes);tx.pair(c,d,true,(bytes,path)->bytes);
        Path backup=tx.commit(directory.resolve("backups"),"SWAP",()->{});
        assertEquals("target",Files.readString(a));assertEquals("source",Files.readString(b));assertFalse(Files.exists(c));assertEquals("source-only",Files.readString(d));
        FileTransaction.restore(backup); assertEquals("source",Files.readString(a));assertEquals("target",Files.readString(b));assertTrue(Files.exists(c));assertFalse(Files.exists(d));
    }
    @Test void failedReloadRollsBackAllWritesAndRemovals() throws Exception {
        Path a=directory.resolve("a"),b=directory.resolve("b");Files.writeString(a,"source");Files.writeString(b,"target");
        FileTransaction tx=new FileTransaction();tx.pair(a,b,false,(bytes,path)->bytes);
        int[] attempts={0};assertThrows(IOException.class,()->tx.commit(directory.resolve("backups"),"OVERRIDE",()->{if(attempts[0]++==0)throw new IllegalStateException("injected failure");}));
        assertEquals("source",Files.readString(a));assertEquals("target",Files.readString(b));assertEquals(2,attempts[0]);
    }
    @Test void pendingCrashIsRecoveredBeforeNewDataLoads() throws Exception {
        Path a=directory.resolve("a"),b=directory.resolve("b");Files.writeString(a,"source");
        FileTransaction tx=new FileTransaction();tx.pair(a,b,false,(bytes,path)->bytes);
        Path backup=tx.commit(directory.resolve("backups"),"MOVE",()->{});
        Files.writeString(backup.resolve("status.txt"),"PENDING");FileTransaction.recover(directory.resolve("backups"));
        assertEquals("source",Files.readString(a));assertFalse(Files.exists(b));assertEquals("ROLLED_BACK",Files.readString(backup.resolve("status.txt")));
    }
    @Test void nbtChangesIdentityAndRetainsInventoryExperienceAndPosition() throws Exception {
        CompoundTag tag=new CompoundTag();tag.putInt("XpTotal",321);tag.putString("inventoryMarker","diamond");tag.putIntArray("UUID",UUIDUtil.uuidToIntArray(UUID.randomUUID()));
        ByteArrayOutputStream out=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,out);UUID id=UUID.randomUUID();
        CompoundTag migrated=NbtIo.readCompressed(new ByteArrayInputStream(MigrationCommand.playerData(out.toByteArray(),id)),NbtAccounter.unlimitedHeap());
        assertEquals(321,migrated.getIntOr("XpTotal",0));assertEquals("diamond",migrated.getStringOr("inventoryMarker",""));assertEquals(id,UUIDUtil.uuidFromIntArray(migrated.getIntArray("UUID").orElseThrow()));
    }
    @Test void teleportSwapPreservesPadIdsLinksAndTransfersOwnershipCooldownAndIgnore() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        String json="{\"schemaVersion\":1,\"pads\":{\"p1\":{\"owner\":\""+a+"\",\"partner\":\"p2\"},\"p2\":{\"owner\":\""+b+"\",\"partner\":\"p1\"}},\"cooldowns\":{\""+a+"\":10,\""+b+"\":20},\"ignored\":[\""+a+"\"]}";
        var data=com.google.gson.JsonParser.parseString(MigrationCommand.migrateTeleports(json,a,b,true)).getAsJsonObject();
        assertEquals(b.toString(),data.getAsJsonObject("pads").getAsJsonObject("p1").get("owner").getAsString());
        assertEquals(a.toString(),data.getAsJsonObject("pads").getAsJsonObject("p2").get("owner").getAsString());
        assertEquals("p2",data.getAsJsonObject("pads").getAsJsonObject("p1").get("partner").getAsString());
        assertEquals(20,data.getAsJsonObject("cooldowns").get(a.toString()).getAsInt());assertEquals(b.toString(),data.getAsJsonArray("ignored").get(0).getAsString());
    }
}
