package pl.bongo.bongoutils.claims;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/** Reflection boundary for the supported GP3D 19 universal Fabric adapter. No bundled GP classes. */
public final class GpClaims {
    public final Object repository;
    public GpClaims(Object repository) { this.repository=Objects.requireNonNull(repository); }
    public static GpClaims detect() {
        if(!FabricLoader.getInstance().isModLoaded("griefprevention3d")) return null;
        try {
            Class<?> entry=Class.forName("com.griefprevention.fabric.mc26_1.GriefPreventionFabric");
            return new GpClaims(entry.getMethod("getClaimRepository").invoke(null));
        } catch(ReflectiveOperationException | LinkageError e) { throw new IllegalStateException("Nieobsługiwana wersja GriefPrevention3D (wymagana 19.0.0 universal).",e); }
    }
    public static Object call(Object target,String name,Object... args) {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()) for(Method method:type.getDeclaredMethods()) {
            if(!method.getName().equals(name) || method.getParameterCount()!=args.length) continue;
            Class<?>[] params=method.getParameterTypes(); boolean match=true;
            for(int i=0;i<args.length;i++) {
                Class<?> p=params[i];
                if(p==long.class) p=Long.class; else if(p==int.class) p=Integer.class; else if(p==boolean.class) p=Boolean.class;
                if(args[i]!=null && !p.isInstance(args[i])) { match=false; break; }
            }
            if(!match) continue;
            try { method.setAccessible(true); return method.invoke(target,args); }
            catch(ReflectiveOperationException | RuntimeException e) { throw new IllegalStateException("GP3D: błąd operacji "+name,e); }
        }
        throw new IllegalStateException("GP3D: brak metody "+name);
    }
    public static Object field(Object target,String name) {
        try { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("GP3D: brak pola "+name,e); }
    }
    @SuppressWarnings("unchecked") public Collection<Object> all() { return (Collection<Object>)call(repository,"getClaims"); }
    public Object byId(long id) { return ((Optional<?>)call(repository,"getClaim",id)).orElse(null); }
    public Object at(ServerPlayer player) {
        String world=switch(player.level().dimension().identifier().toString()) {
            case "minecraft:overworld" -> "world";
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> player.level().dimension().identifier().toString();
        };
        var p=player.blockPosition();
        return ((Optional<?>)call(repository,"findClaimAt",world,p.getX(),p.getY(),p.getZ(),false,false)).orElse(null);
    }
    public long id(Object claim) { return (Long)call(claim,"id"); }
    public UUID owner(Object claim) {
        if((boolean)call(repository,"isAdminSubdivision",claim)) return null;
        return (UUID)call(repository,"effectiveOwnerId",claim);
    }
    public boolean independent(Object claim) {
        Long parent=(Long)call(claim,"parentId");
        return parent!=null && ((boolean)call(repository,"isRestrictedSubdivision",claim) || call(byId(parent),"parentId")!=null);
    }
    public String key(Object claim) { return call(claim,"worldKey")+":"+id(claim)+":"+owner(claim); }
    public Object constant(String type,String name) {
        try {
            for(Object c:Class.forName("com.griefprevention.claims."+type).getEnumConstants()) if(((Enum<?>)c).name().equals(name)) return c;
        } catch(ClassNotFoundException e) { throw new IllegalStateException(e); }
        throw new IllegalArgumentException(name);
    }
    public boolean allows(Object claim,ServerPlayer player,String trust) { return (boolean)call(repository,"allows",claim,player,constant("ClaimTrustLevel",trust)); }
    public boolean permission(ServerPlayer player,String permission) { return (boolean)call(repository,"hasPermission",player,permission); }
    public boolean nativeValue(Object claim,String flag) { return (boolean)call(repository,"flag",id(claim),constant("ClaimFlag",flag)); }
    public boolean pvpExplicit(Object claim) {
        Object doc=((Map<?,?>)field(repository,"documentsByClaimId")).get(id(claim));
        return doc!=null && (boolean)call(doc,"pvpToggled");
    }
    private Object worldSettings() {
        try {
            Class<?> type=Class.forName("com.griefprevention.fabric.mc26_1.FabricWorldProtection");
            Field active=type.getDeclaredField("active");active.setAccessible(true);
            Object protection=active.get(null);
            return call(field(protection,"settings"),"world");
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    public boolean pvpAllowed(Object claim) {
        boolean sub=call(claim,"parentId")!=null;
        return (boolean)call(worldSettings(),sub?"pvpToggleForSubdivisions":"pvpToggleForClaims");
    }
    public boolean anyPvpAllowed() {
        Object settings=worldSettings();
        return (boolean)call(settings,"pvpToggleForClaims") || (boolean)call(settings,"pvpToggleForSubdivisions");
    }
    public void nativeValues(Collection<Object> claims,String flag,boolean value) {
        List<Long> ids=claims.stream().map(this::id).toList();
        call(repository,"setFlag",ids,constant("ClaimFlag",flag),value);
    }
    public void resetPvp(Object claim) {
        call(repository,"setPvpToggle",id(claim),false);
        call(repository,"setFlag",id(claim),constant("ClaimFlag","PVP"),true);
    }
    public void nativeValue(Object claim,String flag,boolean value) {
        if(flag.equals("PVP")) call(repository,"setPvpToggle",id(claim),value);
        else call(repository,"setFlag",id(claim),constant("ClaimFlag",flag),value);
    }
}
