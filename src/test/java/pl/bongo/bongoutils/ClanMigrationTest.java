package pl.bongo.bongoutils;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;
class ClanMigrationTest {
    private static String fixture(){return "{\"schemaVersion\":1,\"clans\":{\"clan\":{\"members\":{\"a\":{\"id\":\"a\",\"name\":\"Alice\",\"role\":\"MEMBER\"},\"b\":{\"id\":\"b\",\"name\":\"Bob\",\"role\":\"FOUNDER\"}}}},\"users\":{\"a\":{\"lastName\":\"Alice\"},\"b\":{\"lastName\":\"Bob\"}},\"invitations\":{\"c\":{\"clan\":\"clan\",\"inviter\":\"b\"}}}";}
    @Test void overrideCannotOrphanExistingClanMembers(){assertThrows(IllegalArgumentException.class,()->ClanMigration.transform(fixture(),"a","b","Alice","Bob",false));}
    @Test void swapTransfersRolesNamesAndInviterInOnePass(){
        var data=JsonParser.parseString(ClanMigration.transform(fixture(),"a","b","Alice","Bob",true)).getAsJsonObject();
        var members=data.getAsJsonObject("clans").getAsJsonObject("clan").getAsJsonObject("members");
        assertEquals("FOUNDER",members.getAsJsonObject("a").get("role").getAsString());
        assertEquals("Alice",members.getAsJsonObject("a").get("name").getAsString());
        assertEquals("a",members.getAsJsonObject("a").get("id").getAsString());
        assertEquals("MEMBER",members.getAsJsonObject("b").get("role").getAsString());
        assertEquals("a",data.getAsJsonObject("invitations").getAsJsonObject("c").get("inviter").getAsString());
    }
}
