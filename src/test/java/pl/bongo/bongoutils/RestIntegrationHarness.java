package pl.bongo.bongoutils;

import io.netty.buffer.Unpooled;
import java.net.SocketTimeoutException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.*;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import static pl.bongo.bongoutils.IntegrationHarness.*;
import static pl.bongo.bongoutils.WhitelistIntegrationHarness.command;

/** Vanilla clients and RCON against the packaged mod. No client mod or authentication bypass. */
public final class RestIntegrationHarness {
    static final Map<Integer, PacketType<?>> TYPES = new HashMap<>();
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port = Integer.parseInt(args[0]);
        WhitelistIntegrationHarness.rconPort = Integer.parseInt(args[1]); WhitelistIntegrationHarness.password = args[2];
        GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, id) -> TYPES.put(id, type));
        command("ign-whitelist off"); command("whitelist off");
        command("forceload add 96 96 127 127");
        String name = "RS" + Long.toString(System.nanoTime(), 36);
        try (Client player = new Client(name, "pl_pl", true)) {
            for (int attempt = 0; attempt < 20; attempt++) {
                command("fill 98 80 98 115 80 115 stone");
                if (command("execute if block 106 80 106 stone").contains("Test passed")) break;
                player.pump(500);
            }
            wiki(player, "pl_pl");
            prepare(player, 106.5, 81, 106.5);
            player.send("sit"); player.chat("Siedzisz"); player.pump(200);
            check(player.id >= 0 && mounted(name), "/sit mounts a zero-size vanilla interaction entity without OP");
            check(command("data get entity " + name + " RootVehicle").contains("Found no elements"), "seat is excluded from player vehicle saves");
            player.send("sit"); player.chat("Wstałeś");
            check(!seats(), "repeating /sit removes the helper");
            prepare(player, 106.5, 81, 106.5);
            player.send("sit"); player.chat("Siedzisz");
            player.bot.send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, true, false, false)));
            player.chat("Wstałeś"); player.bot.send(new ServerboundPlayerInputPacket(Input.EMPTY));
            check(!seats(), "jump input stands up and cleans the seat");

            prepare(player, 106.5, 81, 106.5);
            player.send("lay"); player.chat("Leżysz"); player.pose(player.id, Pose.SLEEPING);
            check(player.markers.getOrDefault(player.id, false), "lying client receives sleeping-position metadata for the native animation");
            check(command("data get entity " + name + " sleeping_pos").contains("Found no elements"), "server sleeping state remains empty");
            command("time set 18000"); player.pump(5500);
            int daytime = lastNumber(command("time query time"));
            check(daytime >= 18000 && daytime < 18300, "/lay does not skip the night after the vanilla sleep delay");
            try (Client observer = new Client("RO" + Long.toString(System.nanoTime(), 36), "en_us", true)) {
                ground(observer, 106.5, 81, 110.5); observer.pose(player.id, Pose.SLEEPING);
                check(observer.markers.getOrDefault(player.id, false), "late observer receives complete lying metadata");
                wiki(observer, "en_us");
            }
            player.leaveBed(); player.pose(player.id, Pose.STANDING);
            check(!player.markers.getOrDefault(player.id, true), "Leave Bed clears the client-only sleeping marker");

            prepare(player, 106.5, 81, 106.5);
            player.send("sit"); player.chat("Siedzisz"); player.send("lay"); player.chat("Leżysz");
            player.pose(player.id, Pose.SLEEPING); check(!seats(), "switching from sit to lay cleans the seat");
            command("damage " + name + " 1 minecraft:generic"); player.pose(player.id, Pose.STANDING);
            check(lastNumber(command("data get entity " + name + " Health")) < 20, "laying remains vulnerable to ordinary damage");

            prepare(player, 106.5, 81, 106.5);
            player.send("sit"); player.chat("Siedzisz");
            command("tp " + name + " 110.5 81 110.5"); player.pump(300);
            check(!seats() && command("data get entity " + name + " Pos").contains("110.5"), "teleport exits sitting without snapping back to its anchor");
            prepare(player, 106.5, 81, 106.5);
            player.send("sit"); player.chat("Siedzisz");
            command("setblock 106 80 106 air"); player.pump(300);
            check(!seats(), "removing the supporting block automatically ends sitting");

            prepare(player, 106.5, 81, 106.5);
            command("setblock 106 80 106 stone_slab[type=bottom]"); ground(player, 106.5, 80.5, 106.5);
            player.send("sit"); player.chat("Siedzisz"); check(mounted(name), "sitting works on a bottom slab");
            prepare(player, 106.5, 81, 106.5);
            command("setblock 106 81 106 white_carpet"); ground(player, 106.5, 81.0625, 106.5);
            player.send("sit"); player.chat("Siedzisz"); check(mounted(name), "sitting works on carpet");
            prepare(player, 106.5, 81, 106.5);
            command("setblock 106 80 106 stone_stairs[facing=north]"); ground(player, 106.5, 81, 106.25);
            player.send("sit"); player.chat("Siedzisz"); check(mounted(name), "sitting works on a stair tread");

            prepare(player, 106.5, 81, 106.5);
            command("setblock 106 82 106 stone"); player.send("sit"); player.chat("Za mało miejsca"); check(!seats(), "low ceiling is rejected");
            prepare(player, 106.5, 82, 106.5);
            player.send("sit"); player.denied(); check(!seats(), "air with no stable floor is rejected");
            for (String hazard : List.of("fire", "water", "lava", "cactus", "powder_snow", "sweet_berry_bush")) {
                prepare(player, 106.7, 81, 106.5);
                command("setblock 107 80 106 sand"); command("setblock 107 81 106 " + hazard);
                player.send("sit"); player.denied(); check(!seats(), "nearby " + hazard + " is rejected");
            }
            prepare(player, 106.5, 81, 106.5);
            command("gamemode spectator " + name); player.send("sit"); player.chat("Najpierw stań");
            check(!seats(), "spectator mode cannot enter a pose"); command("gamemode survival " + name);
            prepare(player, 106.5, 81, 106.5);
            player.send("sit"); player.chat("Siedzisz"); command("save-all flush"); player.pump(200);
        }
        Thread.sleep(1200); check(!seats(), "disconnect removes every temporary seat");
        try (Client player = new Client(name, "pl_pl", false)) {
            player.pump(300);
            check(command("data get entity " + name + " RootVehicle").contains("Found no elements"), "reconnect does not restore a temporary mount");
            check(command("data get entity " + name + " Pos").contains("81.0d"), "autosave and reconnect preserve the safe standing height");
        }
        System.out.println("PASS: /sit, /lay, native poses, late observer, no sleep voting, wiki, geometry, hazards, damage, movement, teleport, saves and disconnect cleanup.");
    }
    static void prepare(Client c, double x, double y, double z) throws Exception {
        command("tp " + c.name + " 106.5 86 106.5"); c.pump(100);
        command("fill 98 81 98 115 85 115 air"); command("fill 98 80 98 115 80 115 stone"); ground(c, x, y, z);
        c.pump(500);
    }
    static void ground(Client c, double x, double y, double z) throws Exception {
        command("tp " + c.name + " " + x + " " + y + " " + z + " 0 0"); c.pump(100);
        c.bot.send(new ServerboundPlayerInputPacket(Input.EMPTY));
        c.bot.send(new ServerboundMovePlayerPacket.Pos(x, y, z, true, false));
        c.bot.send(ServerboundClientTickEndPacket.INSTANCE); c.pump(100);
    }
    static boolean seats() throws Exception { return command("execute if entity @e[type=minecraft:interaction,tag=" + RestSeat.TAG + "]").contains("Test passed"); }
    static boolean mounted(String name) throws Exception { return command("execute as " + name + " on vehicle run data get entity @s width").contains("0.0f"); }
    static int lastNumber(String text) {
        var matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(text);
        String token = null; while (matcher.find()) token = matcher.group();
        if (token == null) throw new AssertionError("No numeric command result: " + text);
        return (int)Double.parseDouble(token);
    }
    static void wiki(Client c, String locale) throws Exception {
        c.send("wiki"); var dialog = c.dialog();
        check(dialog.common().title().getString().equals(Lang.text(locale, "wiki_title")), "/wiki uses client language " + locale);
        check(((ClickEvent.OpenUrl)((StaticAction)dialog.actions().getFirst().action().orElseThrow()).value()).uri().toString()
                .equals("https://tinyurl.com/bxw-smp"), "/wiki links to the requested URL using the vanilla browser action");
    }
    static final class Client implements AutoCloseable {
        final Bot bot; final String name;
        int id = -1;
        final Map<Integer, Pose> poses = new HashMap<>();
        final Map<Integer, Boolean> markers = new HashMap<>();
        final Set<Integer> watched = new HashSet<>();
        final Map<String, ClientboundSetPlayerTeamPacket.Parameters> teams = new HashMap<>();
        final Map<String, String> membership = new HashMap<>();
        final Map<UUID, ClientboundPlayerInfoUpdatePacket.Entry> tabs = new HashMap<>();
        Client(String name, String locale, boolean registration) throws Exception {
            this.name = name; bot = new Bot(name, UUID.randomUUID(), locale);
            bot.auth(bot.dialog(), "rest-test-password", registration ? "rest-test-password" : null); bot.finish(); bot.prepareGame();
        }
        void send(String command) throws Exception { bot.send(new ServerboundChatCommandPacket(command)); }
        Packet<?> next() throws Exception {
            while (true) {
                var data = new FriendlyByteBuf(Unpooled.wrappedBuffer(bot.frame()));
                try {
                    var type = TYPES.get(data.readVarInt());
                    if (type == GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM) {
                        var p = ClientboundSetPlayerTeamPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(data, RegistryAccess.EMPTY));
                        if (p.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                            teams.remove(p.getName()); membership.values().removeIf(p.getName()::equals);
                        }
                        p.getParameters().ifPresent(parameters -> teams.put(p.getName(), parameters));
                        if (p.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.ADD) for (String name : p.getPlayers()) membership.put(name, p.getName());
                        if (p.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) for (String name : p.getPlayers()) membership.remove(name, p.getName());
                        return p;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE) {
                        var p = ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(data, RegistryAccess.EMPTY));
                        p.entries().forEach(entry -> tabs.put(entry.profileId(), entry)); return p;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_PLAYER_INFO_REMOVE) {
                        var p = ClientboundPlayerInfoRemovePacket.STREAM_CODEC.decode(data); p.profileIds().forEach(tabs::remove); return p;
                    }
                    if (type == CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE) { bot.send(new ServerboundKeepAlivePacket(ClientboundKeepAlivePacket.STREAM_CODEC.decode(data).getId())); return null; }
                    if (type == CommonPacketTypes.CLIENTBOUND_PING) { bot.send(new ServerboundPongPacket(ClientboundPingPacket.STREAM_CODEC.decode(data).getId())); return null; }
                    if (type == CommonPacketTypes.CLIENTBOUND_DISCONNECT) throw new AssertionError(ClientboundDisconnectPacket.STREAM_CODEC.decode(data).reason());
                    if (type == GamePacketTypes.CLIENTBOUND_CHUNK_BATCH_FINISHED) { bot.send(new ServerboundChunkBatchReceivedPacket(64.0F)); return null; }
                    if (type == GamePacketTypes.CLIENTBOUND_ADD_ENTITY) {
                        int entityId = data.readVarInt(); data.readUUID(); int entityType = data.readVarInt();
                        if (entityType == net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(net.minecraft.world.entity.EntityTypes.PLAYER)) watched.add(entityId);
                        return null;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_PLAYER_POSITION) {
                        var p = ClientboundPlayerPositionPacket.STREAM_CODEC.decode(data); var a = p.change();
                        bot.send(new ServerboundAcceptTeleportationPacket(p.id(), a.position().x, a.position().y, a.position().z, a.yRot(), a.xRot()));
                        return null;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_SET_PASSENGERS) {
                        var p = ClientboundSetPassengersPacket.STREAM_CODEC.decode(data);
                        if (id < 0 && p.getPassengers().length > 0) id = p.getPassengers()[0]; return p;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA) {
                        int index = data.readerIndex(), entityId = data.readVarInt(); data.readerIndex(index);
                        if (entityId != id && !watched.contains(entityId)) return null;
                        var p = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(data, RegistryAccess.EMPTY));
                        for (var item : p.packedItems()) {
                            if (item.value() instanceof Pose pose) poses.put(p.id(), pose);
                            if (item.serializer() == net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_BLOCK_POS)
                                markers.put(p.id(), ((Optional<?>)item.value()).isPresent());
                        }
                        return p;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT) return ClientboundSystemChatPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(data, RegistryAccess.EMPTY));
                    if (type == CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG) return ClientboundShowDialogPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(data, RegistryAccess.EMPTY));
                    return null;
                } finally { data.release(); }
            }
        }
        void pump(long millis) throws Exception {
            long until = System.currentTimeMillis() + millis; bot.socket.setSoTimeout(200);
            try { while (System.currentTimeMillis() < until) { try { next(); } catch (SocketTimeoutException expected) {} } }
            finally { bot.socket.setSoTimeout(20000); }
        }
        void chat(String text) throws Exception { await(text, null, -1); }
        void denied() throws Exception { await(null, null, -1); }
        void pose(int target, Pose pose) throws Exception { await(null, pose, target); }
        void await(String text, Pose pose, int target) throws Exception {
            long until = System.currentTimeMillis() + 7000; bot.socket.setSoTimeout(500);
            try {
                while (System.currentTimeMillis() < until) {
                    if (pose != null && poses.get(target) == pose) return;
                    try {
                        var packet = next();
                        if (packet instanceof ClientboundSystemChatPacket chat) {
                            String value = chat.content().getString();
                            if (text != null && value.contains(text)) return;
                            if (text != null && (value.contains("Najpierw stań") || value.contains("Za mało miejsca") || value.contains("Brakuje stabilnego") || value.contains("niebezpieczne")))
                                throw new AssertionError("Unexpected rest denial while expecting " + text + ": " + value);
                            if (text == null && pose == null && (value.contains("Najpierw stań") || value.contains("niebezpieczne") || value.contains("Brakuje stabilnego") || value.contains("Za mało miejsca"))) return;
                        }
                    } catch (SocketTimeoutException expected) {}
                }
                throw new AssertionError("Missing rest response: " + text + " / " + pose + "; latest poses=" + poses + "; watched=" + watched);
            } finally { bot.socket.setSoTimeout(20000); }
        }
        MultiActionDialog dialog() throws Exception {
            while (true) { var packet = next(); if (packet instanceof ClientboundShowDialogPacket dialog) return (MultiActionDialog)dialog.dialog().value(); }
        }
        void leaveBed() throws Exception {
            var data = new FriendlyByteBuf(Unpooled.buffer());
            try {
                data.writeVarInt(id); data.writeEnum(ServerboundPlayerCommandPacket.Action.STOP_SLEEPING); data.writeVarInt(0);
                bot.send(ServerboundPlayerCommandPacket.STREAM_CODEC.decode(data));
            } finally { data.release(); }
        }
        public void close() throws Exception { bot.close(); }
    }
}
