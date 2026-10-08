package pl.bongo.bongoutils;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;

/** Optional integration with the installed clan mod. Validate with its own schema before any write. */
final class ClanMigration {
    private final Object runtime,store;
    private final Field stateField;
    private final Class<?> stateType;
    private final Method validate,sync;
    private final Path file;
    private ClanMigration(Path file) throws Exception {
        this.file=file;Class<?> clans=Class.forName("pl.bongo.clans.CommunityClans");runtime=clans.getField("runtime").get(null);
        if(runtime==null)throw new IllegalStateException("Clan runtime is unavailable");
        Field field=runtime.getClass().getDeclaredField("store");field.setAccessible(true);store=field.get(runtime);
        stateField=store.getClass().getDeclaredField("state");stateField.setAccessible(true);
        stateType=Class.forName("pl.bongo.clans.ClanData$State");
        validate=Class.forName("pl.bongo.clans.ClanData").getMethod("validate",stateType);sync=runtime.getClass().getMethod("syncDisplay");
    }
    static ClanMigration find(Path world) throws Exception {
        Path file=world.resolve("bongo-community-clans.json");
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bongo_community_clans") && Files.exists(file)?new ClanMigration(file):null;
    }
    boolean contains(UUID id) throws Exception {
        var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();String player=id.toString();
        return data.getAsJsonObject("users").has(player) || data.getAsJsonObject("invitations").has(player)
                || data.getAsJsonObject("clans").entrySet().stream().anyMatch(e->e.getValue().getAsJsonObject().getAsJsonObject("members").has(player));
    }
    void prepare(FileTransaction tx,UUID from,UUID to,String fromName,String toName,boolean swap) throws Exception {
        String json=transform(Files.readString(file),from.toString(),to.toString(),fromName,toName,swap);
        validate.invoke(null,Store.JSON.fromJson(json,stateType));
        tx.put(file,json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    void reload() { try { Object state=Store.JSON.fromJson(Files.readString(file),stateType);validate.invoke(null,state);stateField.set(store,state); }
        catch(Exception e){throw new IllegalStateException("Cannot reload migrated clan data",e);} }
    void refresh() {try{sync.invoke(runtime);}catch(Exception e){throw new IllegalStateException("Cannot refresh clan display",e);} }
    static String transform(String json,String from,String to,String fromName,String toName,boolean swap) {
        JsonObject data=JsonParser.parseString(json).getAsJsonObject();
        if(data.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported clan schema");
        JsonObject clans=data.getAsJsonObject("clans");
        for(String id:new ArrayList<>(clans.keySet())) {
            JsonObject clan=clans.getAsJsonObject(id),members=clan.getAsJsonObject("members");
            JsonElement a=members.get(from),b=members.get(to);
            if(!swap && b!=null && b.getAsJsonObject().get("role").getAsString().equals("FOUNDER") && members.size()>1)
                throw new IllegalArgumentException("Nick docelowy jest założycielem klanu z innymi członkami. Użyj swap lub najpierw przekaż przywództwo klanu.");
            members.remove(from);members.remove(to);
            if(a!=null){JsonObject member=a.getAsJsonObject();member.addProperty("id",to);member.addProperty("name",toName);members.add(to,member);}
            if(swap && b!=null){JsonObject member=b.getAsJsonObject();member.addProperty("id",from);member.addProperty("name",fromName);members.add(from,member);}
            if(members.isEmpty())clans.remove(id);
        }
        for(String map:List.of("users","invitations")) {
            JsonObject values=data.getAsJsonObject(map);JsonElement a=values.remove(from),b=values.remove(to);
            if(a!=null)values.add(to,a);if(swap && b!=null)values.add(from,b);
        }
        JsonObject users=data.getAsJsonObject("users");
        if(users.has(to))users.getAsJsonObject(to).addProperty("lastName",toName);
        if(users.has(from))users.getAsJsonObject(from).addProperty("lastName",fromName);
        JsonObject invites=data.getAsJsonObject("invitations");
        for(String id:new ArrayList<>(invites.keySet())) {
            JsonObject invite=invites.getAsJsonObject(id);String inviter=invite.get("inviter").getAsString();
            if(!clans.has(invite.get("clan").getAsString()) || !swap && inviter.equals(to)){invites.remove(id);continue;}
            if(inviter.equals(from))invite.addProperty("inviter",to);else if(swap && inviter.equals(to))invite.addProperty("inviter",from);
        }
        return Store.JSON.toJson(data);
    }
}
