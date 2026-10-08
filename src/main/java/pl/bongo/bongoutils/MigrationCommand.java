package pl.bongo.bongoutils;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.Method;

public final class MigrationCommand {
    public enum Mode { MOVE, SWAP, OVERRIDE }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var migrate = Commands.literal("migrate").requires(Commands.hasPermission(Commands.LEVEL_ADMINS));
        for (Mode mode : Mode.values()) migrate.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT))
                .then(Commands.argument("from",StringArgumentType.word())
                        .then(Commands.argument("to",StringArgumentType.word()).executes(c -> run(c.getSource(),
                                StringArgumentType.getString(c,"from"),StringArgumentType.getString(c,"to"),mode)))));
        // The existing resetpassword command keeps its console-only permission on its own branch.
        dispatcher.register(Commands.literal("bongoutils").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).then(migrate));
    }
    private static UUID known(String name) {
        UUID premium = BongoUtils.store.premiumId(name); if (premium != null) return premium;
        return Social.state.find(name);
    }
    private static int run(CommandSourceStack source, String fromName, String toName, Mode mode) throws CommandSyntaxException {
        try { Store.key(fromName); Store.key(toName); }
        catch (IllegalArgumentException e) { throw new SimpleCommandExceptionType(Component.literal("Nieprawidłowy nick.")).create(); }
        UUID knownFrom=known(fromName),knownTo=known(toName);
        if(knownFrom!=null && knownTo!=null) return execute(source,fromName,toName,knownFrom,knownTo,mode);
        if(!BongoUtils.submit(() -> {
            try {
                UUID from=knownFrom==null ? resolveUnseen(fromName) : knownFrom,to=knownTo==null ? resolveUnseen(toName) : knownTo;
                source.getServer().execute(() -> { try { execute(source,fromName,toName,from,to,mode); }
                    catch(CommandSyntaxException e){source.sendFailure(e.getRawMessage() instanceof Component component?component:Component.literal(e.getMessage()));} });
            } catch(Exception e){source.getServer().execute(()->source.sendFailure(Component.literal("Nie można zweryfikować nicków Mojang; migracja nie została wykonana.")));}
        })) throw new SimpleCommandExceptionType(Component.literal("Serwer jest zajęty. Spróbuj ponownie.")).create();
        source.sendSuccess(()->Component.literal("Sprawdzam tożsamości kont przed migracją..."),false);return 1;
    }
    private static UUID resolveUnseen(String name) throws Exception {
        UUID official=BongoUtils.skins.lookupUuid(name);
        return official==null ? UUIDUtil.createOfflinePlayerUUID(Store.key(name)) : official;
    }
    private static int execute(CommandSourceStack source, String fromName, String toName, UUID from, UUID to, Mode mode) throws CommandSyntaxException {
        try {
            Store.key(fromName); Store.key(toName);
            if (fromName.equalsIgnoreCase(toName)) throw new IllegalArgumentException("Nick źródłowy i docelowy muszą być różne.");
            MinecraftServer server = source.getServer();
            if (from.equals(to)) throw new IllegalArgumentException("Oba nicki wskazują na tę samą tożsamość UUID.");
            if (server.getPlayerList().getPlayer(from) != null || server.getPlayerList().getPlayer(to) != null
                    || server.getPlayerList().getPlayerByName(fromName) != null || server.getPlayerList().getPlayerByName(toName) != null)
                throw new IllegalArgumentException("Obaj gracze muszą być offline podczas migracji.");
            Path world = server.getWorldPath(LevelResource.ROOT), root = BongoUtils.store.root();
            Path playerFrom = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(from + ".dat");
            Path playerTo = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(to + ".dat");
            if (!Files.exists(playerFrom)) throw new IllegalArgumentException("Brak danych gracza źródłowego. Użyj zapisanego nicku z właściwą wielkością liter.");
            if (mode == Mode.SWAP && !Files.exists(playerTo)) throw new IllegalArgumentException("SWAP wymaga zapisanych danych obu graczy.");
            if (mode == Mode.MOVE && (Files.exists(playerTo) || Files.exists(root.resolve("accounts/"+Store.key(toName)+".json"))
                    || Files.exists(root.resolve("skins/"+to+".json")) || !Social.state.ignoredBy(to).isEmpty() || Social.hidden(to)
                    || BongoUtils.store.reserved(toName) || Files.exists(server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(to+".json"))
                    || Files.exists(server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(to+".json"))))
                throw new IllegalArgumentException("Nick docelowy ma dane. Wybierz swap lub override.");
            boolean swap = mode == Mode.SWAP;
            FileTransaction tx = new FileTransaction();
            tx.pair(playerFrom,playerTo,swap,(bytes,path) -> playerData(bytes, path.equals(playerTo) ? to : from));
            tx.pair(playerFrom.resolveSibling(from+".dat_old"),playerTo.resolveSibling(to+".dat_old"),swap,
                    (bytes,path) -> playerData(bytes,path.getFileName().toString().startsWith(to.toString()) ? to : from));
            for (LevelResource folder : List.of(LevelResource.PLAYER_STATS_DIR,LevelResource.PLAYER_ADVANCEMENTS_DIR))
                tx.pair(server.getWorldPath(folder).resolve(from+".json"),server.getWorldPath(folder).resolve(to+".json"),swap,(bytes,path)->bytes);
            Path accountFrom = root.resolve("accounts/"+Store.key(fromName)+".json"), accountTo = root.resolve("accounts/"+Store.key(toName)+".json");
            // Mojang reservations always remain bound to their real name/UUID; stale offline hashes never become MSA credentials.
            boolean offlineFrom=from.version()==3 && !BongoUtils.store.reserved(fromName),offlineTo=to.version()==3 && !BongoUtils.store.reserved(toName);
            byte[] passwordFrom = !offlineFrom || !Files.exists(accountFrom) ? null : Files.readAllBytes(accountFrom);
            byte[] passwordTo = !offlineTo || !Files.exists(accountTo) ? null : Files.readAllBytes(accountTo);
            tx.put(accountTo, offlineTo ? passwordFrom : null);
            tx.put(accountFrom, swap && offlineFrom ? passwordTo : null);
            tx.pair(root.resolve("skins/"+from+".json"),root.resolve("skins/"+to+".json"),swap,(bytes,path)->bytes);
            var nextSocial = SocialState.migrate(Social.state.snapshot(),from,to,fromName,toName,swap);
            tx.put(root.resolve("social.json"),Store.JSON.toJson(nextSocial).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            OptionalTeleports teleports = OptionalTeleports.find();
            ClanMigration clans=ClanMigration.find(world);
            if(mode==Mode.MOVE && clans!=null && clans.contains(to))throw new IllegalArgumentException("Nick docelowy ma dane klanowe. Wybierz swap lub override.");
            if(clans!=null)clans.prepare(tx,from,to,fromName,toName,swap);
            if (teleports != null) {
                teleports.save(); Path file = world.resolve("bongos-teleports.json");
                String json=Files.readString(file);
                if(mode==Mode.MOVE && teleportsContains(json,to))throw new IllegalArgumentException("Nick docelowy ma dane teleportów. Wybierz swap lub override.");
                tx.put(file,migrateTeleports(json,from,to,swap).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Path backup = tx.commit(root.resolve("migration-backups"),mode+" "+fromName+" ("+from+") -> "+toName+" ("+to+")",() -> {
                try { Social.state.reload(); if (teleports != null) teleports.reload(); if(clans!=null)clans.reload(); }
                catch (Exception e) { throw new IllegalStateException(e); }
            });
            var cache=(pl.bongo.bongoutils.mixin.PlayerListCacheAccess)server.getPlayerList();
            cache.bongo$stats().remove(from);cache.bongo$stats().remove(to);cache.bongo$advancements().remove(from);cache.bongo$advancements().remove(to);
            if(clans!=null)try{clans.refresh();}catch(RuntimeException e){BongoUtils.LOG.error("Migration committed, but clan display refresh failed; restart to rebuild display",e);}
            source.sendSuccess(() -> Component.literal("Migracja "+mode+" zakończona: "+fromName+" -> "+toName+". Kopia: "+backup),false);
            BongoUtils.LOG.info("Administrator {} migrated {} -> {} using {}; backup {}",source.getTextName(),fromName,toName,mode,backup);
            return 1;
        } catch (Exception e) {
            if(e instanceof IllegalArgumentException) BongoUtils.LOG.info("Migration refused: {}",e.getMessage());
            else BongoUtils.LOG.error("Migration failed",e);
            throw new SimpleCommandExceptionType(Component.literal("Migracja nie została wykonana: "+e.getMessage())).create();
        }
    }
    static byte[] playerData(byte[] bytes, UUID newId) {
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtAccounter.create(64L*1024*1024));
            tag.putIntArray("UUID",UUIDUtil.uuidToIntArray(newId));
            if (tag.contains("uuid")) tag.putIntArray("uuid",UUIDUtil.uuidToIntArray(newId));
            if (tag.contains("UUIDMost")) { tag.putLong("UUIDMost",newId.getMostSignificantBits()); tag.putLong("UUIDLeast",newId.getLeastSignificantBits()); }
            ByteArrayOutputStream output = new ByteArrayOutputStream(); NbtIo.writeCompressed(tag,output); return output.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Nieprawidłowe dane playerdata",e); }
    }
    static String migrateTeleports(String json, UUID fromId, UUID toId, boolean swap) {
        var data = com.google.gson.JsonParser.parseString(json).getAsJsonObject(); String from=fromId.toString(),to=toId.toString();
        if (data.get("schemaVersion").getAsInt()!=1) throw new IllegalArgumentException("Unsupported Teleports schema");
        for (var entry : data.getAsJsonObject("pads").entrySet()) {
            var pad = entry.getValue().getAsJsonObject(); String owner = pad.get("owner").getAsString();
            if (owner.equals(from)) pad.addProperty("owner",to); else if (swap && owner.equals(to)) pad.addProperty("owner",from);
        }
        var cooldowns = data.getAsJsonObject("cooldowns"); var a=cooldowns.remove(from); var b=cooldowns.remove(to);
        if(a!=null) cooldowns.add(to,a); if(swap && b!=null) cooldowns.add(from,b);
        var ignored = data.getAsJsonArray("ignored"); boolean source=false,target=false;
        var next = new com.google.gson.JsonArray();
        for(var id:ignored) { if(id.getAsString().equals(from)) source=true; else if(id.getAsString().equals(to)) target=true; else next.add(id); }
        if(source) next.add(to); if(swap && target) next.add(from); data.add("ignored",next);
        return Store.JSON.toJson(data);
    }
    private static boolean teleportsContains(String json,UUID id) {
        var data=com.google.gson.JsonParser.parseString(json).getAsJsonObject();String player=id.toString();
        return data.getAsJsonObject("cooldowns").has(player)
                || java.util.stream.StreamSupport.stream(data.getAsJsonArray("ignored").spliterator(),false).anyMatch(e->e.getAsString().equals(player))
                || data.getAsJsonObject("pads").entrySet().stream().anyMatch(e->e.getValue().getAsJsonObject().get("owner").getAsString().equals(player));
    }
    private record OptionalTeleports(Method saveMethod, Method reloadMethod) {
        static OptionalTeleports find() throws Exception {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bongos_teleports")) return null;
            Class<?> type = Class.forName("pl.bongo.teleports.Teleports");
            try { return new OptionalTeleports(type.getMethod("migrationSave"),type.getMethod("migrationReload")); }
            catch (NoSuchMethodException e) { throw new IllegalArgumentException("Zaktualizuj Bongo's Teleports do 1.4.0 przed migracją."); }
        }
        void save() { invoke(saveMethod); } void reload() { invoke(reloadMethod); }
        private static void invoke(Method method) { try { method.invoke(null); } catch (Exception e) { throw new IllegalStateException("Teleports migration hook failed",e); } }
    }
}
