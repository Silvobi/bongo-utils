package pl.bongo.bongoutils.claims;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

/** Each rendered page has a fresh player-bound, single-use token and a pinned claim identity. */
public final class ManageClaimUi {
    private static final long TTL=300_000_000_000L;
    private record Session(String token,Long claimId,String key,UUID owner,long revision,long opened,String flag,String pending,boolean reset) {}
    private final Map<UUID,Session> sessions=new HashMap<>();
    public void forget(UUID player) { sessions.remove(player); }
    private static boolean pl(ServerPlayer p) { return p.clientInformation().language().toLowerCase(Locale.ROOT).startsWith("pl"); }
    private static String text(ServerPlayer p,String polish,String english) { return pl(p)?polish:english; }
    private static void error(ServerPlayer p,String message) { p.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED)); }
    private static ClaimFlags flags() { if(ClaimFlags.active==null) throw new IllegalStateException("GriefPrevention3D nie jest dostępne.");return ClaimFlags.active; }
    public int open(ServerPlayer player,boolean global) {
        sessions.remove(player.getUUID());
        try {
            ClaimFlags f=flags();Object claim=global?null:f.gp.at(player);
            if(!global && claim==null) throw new IllegalStateException("Nie stoisz na żadnej działce.");
            if(claim!=null && !f.gp.allows(claim,player,"MANAGE")) throw new IllegalStateException("Ta działka wymaga własności lub Manage Trust.");
            Session s=new Session("",claim==null?null:f.gp.id(claim),claim==null?null:f.gp.key(claim),global?player.getUUID():f.gp.owner(claim),f.store.revision(),System.nanoTime(),null,null,false);
            show(player,s,null,null,false);return 1;
        } catch(RuntimeException e) { error(player,e.getMessage());return 0; }
    }
    private Object validate(ServerPlayer player,Session s) {
        ClaimFlags f=flags();
        if(System.nanoTime()-s.opened>TTL) throw new IllegalStateException("Okno wygasło. Otwórz /manageclaim ponownie.");
        if(s.claimId==null) {
            if(!player.getUUID().equals(s.owner)) throw new IllegalStateException("Nieprawidłowy właściciel ustawień.");
            return null;
        }
        Object claim=f.gp.byId(s.claimId);
        if(claim==null || !f.gp.key(claim).equals(s.key)) throw new IllegalStateException("Działka została usunięta lub zmieniła właściciela. Otwórz panel ponownie.");
        if(!f.gp.allows(claim,player,"MANAGE")) throw new IllegalStateException("Utracono uprawnienia do tej działki.");
        return claim;
    }
    private static ActionButton button(String label,String action) {
        return new ActionButton(new CommonButtonData(Component.literal(label),360),Optional.of(new CustomAll(Identifier.fromNamespaceAndPath("bongoutils",action),Optional.empty())));
    }
    private static String valueLabel(ServerPlayer p,String value) {
        return switch(value) {
            case "on" -> text(p,"Włączone","On");case "off" -> text(p,"Wyłączone","Off");
            case "access" -> "Access Trust";case "container" -> "Container Trust";case "build" -> "Build Trust";
            default -> value;
        };
    }
    private String current(ServerPlayer p,Session s,Object claim,ClaimFlags.Definition def) {
        ClaimFlags f=flags();String local=claim==null?null:f.store.local(s.key,def.id());
        String owner=f.store.owner(s.owner,def.id());
        if("PVP".equals(def.nativeFlag()) && local==null && owner==null) return text(p,"Polityka GP3D","GP3D policy");
        String value=claim==null?(owner==null?def.fallback():owner):ClaimFlags.value(claim,def.id(),def.fallback());
        return valueLabel(p,value);
    }
    private void show(ServerPlayer player,Session old,String flag,String pending,boolean reset) {
        Object claim=validate(player,old);ClaimFlags f=flags();
        Session s=new Session(UUID.randomUUID().toString(),old.claimId,old.key,old.owner,f.store.revision(),old.opened,flag,pending,reset);
        sessions.put(player.getUUID(),s);
        String prefix="claim/"+s.token+"/";
        List<ActionButton> buttons=new ArrayList<>();
        String title=claim==null?text(player,"Moje działki • ustawienia globalne","My claims • global defaults")
            :text(player,"Działka / poddziałka #","Claim / subdivision #")+s.claimId;
        String body=claim==null?text(player,"Domyślne flagi wszystkich Twoich działek i poddziałek, również przyszłych. Lokalne wyjątki mają pierwszeństwo.","Default flags for all your claims and subdivisions, including future claims. Local overrides take priority.")
            :text(player,"To okno dotyczy działki wybranej przy otwarciu. Zmiana miejsca gracza nie zmienia celu.","This window remains bound to the claim selected when opened. Moving does not change its target.");
        if(flag==null) {
            int i=0;
            for(var def:ClaimFlags.definitions()) {
                String source=claim==null?(f.store.owner(s.owner,def.id())==null?text(player,"domyślne moda","mod default"):text(player,"globalne właściciela","owner default")):f.source(claim,def.id(),pl(player));
                String label=def.label(pl(player))+": "+current(player,s,claim,def)+" • "+source;
                if(!f.editable(player,claim,def)) label+=" 🔒";
                buttons.add(button(label,prefix+"flag/"+i++));
            }
        } else {
            var def=ClaimFlags.definition(flag);
            if(!f.editable(player,claim,def)) throw new IllegalStateException("Brak uprawnień do tej flagi albo flaga jest wyłączona w konfiguracji GP3D.");
            title=def.label(pl(player));
            body+="\n"+text(player,"Aktualnie: ","Current: ")+current(player,s,claim,def);
            if("PVP".equals(def.nativeFlag())) body+="\n"+text(player,"Jawne Włączone zezwala na PvP na działce. Domyślne moda przywraca politykę GP3D.","Explicit On enables PvP in the claim. Mod default restores GP3D policy.");
            if(def.id().equals("bongo_community_clans:access")) body+="\n"+text(player,"Build Trust pozwala członkom klanu budować, niszczyć i otwierać skrzynie. W ograniczonej poddziałce wymaga lokalnego wyjątku.","Build Trust lets clan members build, break blocks and open containers. Restricted subdivisions require a local override.");
            if(pending!=null || reset) {
                body+="\n"+text(player,"Zastosować: ","Apply: ")+(reset?text(player,"Dziedziczenie / domyślne","Inherit / default"):valueLabel(player,pending))+"?";
                buttons.add(button(text(player,"Zastosuj","Apply"),prefix+"save"));
                buttons.add(button(text(player,"Anuluj zmianę","Cancel change"),prefix+"edit"));
            } else {
                int i=0;for(String value:def.values()) buttons.add(button(valueLabel(player,value),prefix+"value/"+i++));
                buttons.add(button(claim==null?text(player,"Domyślne moda","Mod default"):text(player,"Dziedzicz ustawienie globalne","Inherit owner default"),prefix+"reset"));
                buttons.add(button(text(player,"Wróć do flag","Back to flags"),prefix+"back"));
            }
        }
        var common=new CommonDialogData(Component.literal(title),Optional.empty(),true,false,DialogAction.WAIT_FOR_RESPONSE,List.of(new PlainMessage(Component.literal(body),360)),List.of());
        player.openDialog(Holder.direct(new MultiActionDialog(common,buttons,Optional.of(button(text(player,"Zamknij","Close"),prefix+"close")),1)));
    }
    public void submit(ServerPlayer player,ServerboundCustomClickActionPacket packet) {
        String path=packet.id().getPath();if(!path.startsWith("claim/")) return;
        String[] parts=path.split("/",4);Session s=sessions.get(player.getUUID());
        if(parts.length<3 || s==null || !s.token.equals(parts[1])) { error(player,text(player,"Nieaktualny formularz działki.","Expired claim form."));return; }
        sessions.remove(player.getUUID());
        try {
            Object claim=validate(player,s);ClaimFlags f=flags();
            if(parts[2].equals("close")) { player.connection.send(ClientboundClearDialogPacket.INSTANCE);return; }
            if(f.store.revision()!=s.revision) throw new IllegalStateException("Ustawienia zmieniły się w międzyczasie. Otwórz panel ponownie.");
            switch(parts[2]) {
                case "flag" -> {
                    if(parts.length!=4) throw new IllegalArgumentException();int index=Integer.parseInt(parts[3]);
                    show(player,s,ClaimFlags.definitions().get(index).id(),null,false);
                }
                case "value" -> {
                    if(parts.length!=4 || s.flag==null) throw new IllegalArgumentException();
                    String value=ClaimFlags.definition(s.flag).values().get(Integer.parseInt(parts[3]));
                    show(player,s,s.flag,value,false);
                }
                case "reset" -> { if(s.flag==null) throw new IllegalArgumentException();show(player,s,s.flag,null,true); }
                case "edit" -> { if(s.flag==null) throw new IllegalArgumentException();show(player,s,s.flag,null,false); }
                case "back" -> show(player,s,null,null,false);
                case "save" -> {
                    if(s.flag==null || s.pending==null && !s.reset) throw new IllegalArgumentException();
                    var def=ClaimFlags.definition(s.flag);
                    if(!f.editable(player,claim,def)) throw new IllegalStateException("Brak uprawnień do zmiany tej flagi.");
                    if(claim==null) f.defaults(player.getUUID(),s.flag,s.reset?null:s.pending);
                    else ClaimFlags.override(claim,s.flag,s.reset?null:s.pending);
                    show(player,s,null,null,false);
                }
                default -> throw new IllegalArgumentException("Nieprawidłowa akcja formularza.");
            }
        } catch(RuntimeException e) {
            player.connection.send(ClientboundClearDialogPacket.INSTANCE);
            error(player,e.getMessage()==null?text(player,"Nieprawidłowy formularz działki.","Invalid claim form."):e.getMessage());
        }
    }
}
