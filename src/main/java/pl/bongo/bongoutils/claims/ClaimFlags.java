package pl.bongo.bongoutils.claims;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import pl.bongo.bongoutils.BongoUtils;

/** Shared, optional API. Other Bongo mods register flags and resolve them without a hard dependency. */
public final class ClaimFlags {
    public record Definition(String id,String pl,String en,List<String> values,String fallback,String nativeFlag,boolean inheritIndependent) {
        public String label(boolean polish) { return polish?pl:en; }
    }
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    public static ClaimFlags active;
    public final GpClaims gp;
    public final ClaimFlagStore store;
    private int ticks,applying;
    static {
        nativeFlag("explosions","Eksplozje","Explosions","off","EXPLOSIONS");
        nativeFlag("wither_explosions","Eksplozje Withera","Wither explosions","off","WITHER_EXPLOSIONS");
        nativeFlag("pvp","PvP","PvP","on","PVP");
        nativeFlag("alerts","Alerty granic","Boundary alerts","on","ALERTS");
    }
    private static void nativeFlag(String id,String pl,String en,String fallback,String nativeFlag) {
        DEFINITIONS.put("gp3d:"+id,new Definition("gp3d:"+id,pl,en,List.of("off","on"),fallback,nativeFlag,true));
    }
    public static void register(String id,String pl,String en,List<String> values,String fallback) {
        register(id,pl,en,values,fallback,true);
    }
    public static void register(String id,String pl,String en,List<String> values,String fallback,boolean inheritIndependent) {
        if(id==null || !id.matches("[a-z0-9_]+:[a-z0-9_]+") || values==null || values.isEmpty() || !values.contains(fallback)
            || values.stream().anyMatch(v->v==null || !v.matches("[a-z_]+"))) throw new IllegalArgumentException("Invalid claim flag");
        if(!inheritIndependent && !values.contains("off")) throw new IllegalArgumentException("A non-inheritable flag needs off");
        Definition definition=new Definition(id,pl,en,List.copyOf(values),fallback,null,inheritIndependent);
        Definition old=DEFINITIONS.putIfAbsent(id,definition);
        if(old!=null && !old.equals(definition)) throw new IllegalArgumentException("Duplicate claim flag: "+id);
    }
    private ClaimFlags(GpClaims gp,ClaimFlagStore store) { this.gp=gp;this.store=store; }
    public static void start(MinecraftServer server) {
        GpClaims gp=GpClaims.detect();
        if(gp==null) { active=null;BongoUtils.LOG.info("GP3D absent: /manageclaim integration disabled");return; }
        active=new ClaimFlags(gp,new ClaimFlagStore(server.getWorldPath(LevelResource.ROOT).resolve("bongo-claim-flags.json")));
        active.reconcile();
        BongoUtils.LOG.info("BongoUtils claim flags ready: {} registered flags",DEFINITIONS.size());
    }
    public static List<Definition> definitions() { return List.copyOf(DEFINITIONS.values()); }
    public static Definition definition(String id) {
        Definition d=DEFINITIONS.get(id);if(d==null) throw new IllegalArgumentException("Unknown claim flag "+id);return d;
    }
    private static ClaimFlags require() {
        if(active==null) throw new IllegalStateException("GriefPrevention3D nie jest dostępne.");return active;
    }
    public static String value(Object claim,String id,String fallback) {
        ClaimFlags f=require();Definition d=definition(id);
        if(!d.inheritIndependent && f.gp.independent(claim) && f.store.local(f.gp.key(claim),id)==null) return "off";
        String value=f.store.value(f.gp.key(claim),f.gp.owner(claim),id,fallback);
        if(!d.values.contains(value)) throw new IllegalStateException("Nieprawidłowa wartość flagi "+id);
        return value;
    }
    public static boolean hasLocal(Object claim,String id) { ClaimFlags f=require();return f.store.local(f.gp.key(claim),id)!=null; }
    public static void override(Object claim,String id,String value) {
        ClaimFlags f=require();validate(id,value);f.store.local(f.gp.key(claim),id,value);f.reconcile();
    }
    public static void importLegacy(Object claim,String id,String value) {
        ClaimFlags f=require();validate(id,value);f.store.legacy(f.gp.key(claim),id,value);
    }
    static void validate(String id,String value) {
        if(value!=null && !definition(id).values.contains(value)) throw new IllegalArgumentException("Invalid value: "+id);
    }
    public void defaults(UUID owner,String id,String value) {
        validate(id,value);store.owner(owner,id,value);reconcile();
    }
    public String source(Object claim,String id,boolean pl) {
        if(store.local(gp.key(claim),id)!=null) return pl?"wyjątek tej działki":"claim override";
        if(!definition(id).inheritIndependent && gp.independent(claim)) return pl?"brak dziedziczenia":"no inheritance";
        if(store.owner(gp.owner(claim),id)!=null) return pl?"globalne właściciela":"owner default";
        return pl?"domyślne moda":"mod default";
    }
    public boolean editable(ServerPlayer player,Object claim,Definition def) {
        if(claim!=null && !gp.allows(claim,player,"MANAGE")) return false;
        if(def.nativeFlag==null) return true;
        String permission=switch(def.nativeFlag) {
            case "EXPLOSIONS" -> "griefprevention.claimexplosions";
            case "WITHER_EXPLOSIONS" -> "griefprevention.witherexplosions";
            case "ALERTS" -> "griefprevention.claimalerts";
            default -> "griefprevention.claimpvp";
        };
        if(!gp.permission(player,permission)) return false;
        if(claim!=null && def.nativeFlag.equals("ALERTS") && !gp.allows(claim,player,"EDIT")) return false;
        return !def.nativeFlag.equals("PVP") || (claim==null?gp.anyPvpAllowed():gp.pvpAllowed(claim));
    }
    public void tick() {
        if(++ticks%20!=0) return;
        try { reconcile(); }
        catch(RuntimeException e) { BongoUtils.LOG.error("Could not synchronize claim flag defaults; will retry",e); }
    }
    public void reconcile() {
        Map<String,Map<String,String>> discovered=new HashMap<>();
        Collection<Object> claims=gp.all();
        for(Object claim:claims) {
            Map<String,String> initial=new HashMap<>();
            for(Definition def:definitions()) if(def.nativeFlag!=null) {
                String nativeValue=gp.nativeValue(claim,def.nativeFlag)?"on":"off";
                if(!nativeValue.equals(def.fallback) || def.nativeFlag.equals("PVP") && gp.pvpExplicit(claim)) initial.put(def.id,nativeValue);
            }
            discovered.put(gp.key(claim),initial);
        }
        store.discover(discovered);
        applying++;
        Map<String,List<Object>> batches=new LinkedHashMap<>();
        try {
            for(Object claim:claims) for(Definition def:definitions()) if(def.nativeFlag!=null) {
                if(def.nativeFlag.equals("PVP") && !gp.pvpAllowed(claim)) continue;
                String desired=value(claim,def.id,def.fallback);
                boolean enabled=desired.equals("on");
                boolean explicit=store.local(gp.key(claim),def.id)!=null || store.owner(gp.owner(claim),def.id)!=null;
                if(def.nativeFlag.equals("PVP") && !explicit) {
                    if(gp.pvpExplicit(claim)) gp.resetPvp(claim);
                    continue;
                }
                if(gp.nativeValue(claim,def.nativeFlag)!=enabled || def.nativeFlag.equals("PVP") && explicit && !gp.pvpExplicit(claim)) {
                    if(def.nativeFlag.equals("PVP")) gp.nativeValue(claim,def.nativeFlag,enabled);
                    else batches.computeIfAbsent(def.nativeFlag+":"+enabled,k->new ArrayList<>()).add(claim);
                }
            }
            for(var batch:batches.entrySet()) {
                String[] parts=batch.getKey().split(":");gp.nativeValues(batch.getValue(),parts[0],Boolean.parseBoolean(parts[1]));
            }
        } finally { applying--; }
    }
    /** Called after successful native GP commands, including no-op writes of the current value. */
    public static void nativeChanged(Object repository,Collection<Long> ids,Object flag,boolean value) {
        ClaimFlags f=active;if(f==null || f.applying>0 || repository!=f.gp.repository) return;
        String name=((Enum<?>)flag).name();
        Definition def=definitions().stream().filter(d->name.equals(d.nativeFlag)).findFirst().orElseThrow();
        try {
            Map<String,String> keys=new HashMap<>();
            for(long id:ids) { Object claim=f.gp.byId(id);if(claim!=null) keys.put(f.gp.key(claim),value?"on":"off"); }
            if(!keys.isEmpty()) f.store.change(s->keys.forEach((key,v)->ClaimFlagStore.put(s.claims,key,def.id,v)));
        } catch(RuntimeException e) { BongoUtils.LOG.error("Native flag was saved by GP3D, but its local override could not be persisted",e); }
    }
    public static void nativePvpChanged(Object repository,long id,boolean value) {
        if(active!=null) nativeChanged(repository,List.of(id),active.gp.constant("ClaimFlag","PVP"),value);
    }
}
