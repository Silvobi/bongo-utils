package pl.bongo.bongoutils;

import io.netty.buffer.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Crypt;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;

/** A plain vanilla wire-protocol client. It neither advertises nor installs BongoUtils. */
public final class IntegrationHarness {
    static int port;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port = Integer.parseInt(args[0]);
        String name = "BgTest" + Long.toString(System.currentTimeMillis(), 36);
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (Bot bot = new Bot(name, offline)) {
            MultiActionDialog registration = bot.dialog();
            check(registration.common().inputs().size() == 2, "registration asks password twice");
            // Deliberately submit nothing; no finish-configuration packet may arrive.
            bot.socket.setSoTimeout(500);
            try { while (true) { Packet<?> packet = bot.read(); check(!(packet instanceof ClientboundFinishConfigurationPacket), "world gate blocks unregistered client"); } }
            catch (SocketTimeoutException expected) {} finally { bot.socket.setSoTimeout(20000); }
            bot.auth(registration, "integration-password", "wrong-repeat");
            registration = bot.dialog(); check(registration.common().inputs().size() == 2, "mismatched registration stays blocked");
            Thread.sleep(1100);
            bot.auth(registration, "integration-password", "integration-password");
            bot.finish();
            bot.skin(false);
            try (Bot duplicate = new Bot(name, offline)) {
                check(duplicate.disconnected, "duplicate login rejected before existing player is evicted");
            }
        }
        Thread.sleep(1000);
        try (Bot bot = new Bot(name.toUpperCase(Locale.ROOT), offline)) {
            MultiActionDialog login = bot.dialog(); check(login.common().inputs().size() == 1, "reconnect and case change asks login, not registration");
            bot.auth(login, "incorrect-password", null);
            MultiActionDialog retry = bot.dialog(); check(retry.common().inputs().size() == 1, "wrong password stays blocked");
            Thread.sleep(1100);
            bot.auth(retry, "integration-password", null); bot.finish();
            // The per-account skin cooldown intentionally survives reconnects.
            bot.waitActive(30000); bot.skin(true);
        }
        try (Bot forgedPremium = new Bot("Notch", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"))) {
            check(forgedPremium.challengeAuthenticated, "premium UUID selects Mojang authentication handshake");
            check(forgedPremium.disconnected, "forged premium UUID with no Mojang session is rejected");
        }
        System.out.println("PASS: encrypted offline registration, pre-world gate, mismatch, reconnect, casing, wrong password, duplicate protection, forged premium rejection, IGN and URL skins with live refresh.");
    }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); System.out.println("PASS: " + label); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class Bot implements AutoCloseable {
        final Socket socket = new Socket("127.0.0.1", port);
        InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream();
        StreamCodec inbound = LoginProtocols.CLIENTBOUND.codec(), outbound = HandshakeProtocols.SERVERBOUND.codec();
        int compression = -1; boolean disconnected, challengeAuthenticated;
        String disconnectReason;
        Bot(String name, UUID uuid) throws Exception {
            socket.setSoTimeout(20000);
            send(new ClientIntentionPacket(SharedConstants.getProtocolVersion(), "localhost", port, ClientIntent.LOGIN));
            outbound = LoginProtocols.SERVERBOUND.codec();
            send(new ServerboundHelloPacket(name, uuid));
            while (true) {
                Packet<?> packet = read();
                if (packet instanceof ClientboundHelloPacket hello) {
                    challengeAuthenticated = hello.shouldAuthenticate();
                    var secret = Crypt.generateSecretKey();
                    send(new ServerboundKeyPacket(secret, hello.getPublicKey(), hello.getChallenge()));
                    input = new CipherInputStream(input, Crypt.getCipher(2, secret));
                    output = new CipherOutputStream(output, Crypt.getCipher(1, secret));
                } else if (packet instanceof ClientboundLoginCompressionPacket threshold) compression = threshold.getCompressionThreshold();
                else if (packet instanceof ClientboundLoginDisconnectPacket kick) { disconnected = true; disconnectReason = kick.reason().getString(); return; }
                else if (packet instanceof ClientboundLoginFinishedPacket) {
                    send(ServerboundLoginAcknowledgedPacket.INSTANCE);
                    inbound = ConfigurationProtocols.CLIENTBOUND.codec(); outbound = ConfigurationProtocols.SERVERBOUND.codec(); return;
                }
            }
        }
        MultiActionDialog dialog() throws Exception {
            while (true) {
                Packet<?> packet = read();
                if (packet instanceof ClientboundSelectKnownPacks packs) send(new ServerboundSelectKnownPacks(packs.knownPacks()));
                else if (packet instanceof ClientboundPingPacket ping) send(new ServerboundPongPacket(ping.getId()));
                else if (packet instanceof ClientboundKeepAlivePacket alive) send(new ServerboundKeepAlivePacket(alive.getId()));
                else if (packet instanceof ClientboundShowDialogPacket dialog) return (MultiActionDialog) dialog.dialog().value();
                else if (packet instanceof ClientboundDisconnectPacket kick) throw new AssertionError("Disconnected: " + kick.reason());
                else if (packet instanceof ClientboundFinishConfigurationPacket) throw new AssertionError("Entered world before password accepted");
            }
        }
        void auth(MultiActionDialog dialog, String password, String repeat) throws Exception {
            CustomAll action = (CustomAll) dialog.actions().getFirst().action().orElseThrow();
            CompoundTag fields = new CompoundTag(); fields.putString("password", password);
            if (repeat != null) fields.putString("repeat", repeat);
            send(new ServerboundCustomClickActionPacket(action.id(), Optional.of(fields)));
        }
        void finish() throws Exception {
            while (true) {
                Packet<?> packet = read();
                if (packet instanceof ClientboundKeepAlivePacket alive) send(new ServerboundKeepAlivePacket(alive.getId()));
                else if (packet instanceof ClientboundFinishConfigurationPacket) {
                    send(ServerboundFinishConfigurationPacket.INSTANCE);
                    byte[] worldPacket = frame(); check(worldPacket.length > 0, "authenticated player receives world packets"); return;
                } else if (packet instanceof ClientboundDisconnectPacket kick) throw new AssertionError("Disconnected: " + kick.reason());
            }
        }
        void prepareGame() throws Exception {
            outbound = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY), new GameProtocols.Context() {
                public boolean hasInfiniteMaterials() { return false; }
                public boolean canUseCommandBlocks() { return false; }
            }).codec();
            send(new ServerboundPlayerLoadedPacket());
        }
        void waitActive(long millis) throws Exception {
            prepareGame();
            long until = System.currentTimeMillis() + millis;
            socket.setSoTimeout(500);
            try {
                while (System.currentTimeMillis() < until) {
                    try { gamePacket(); } catch (SocketTimeoutException expected) {}
                }
            } finally { socket.setSoTimeout(20000); }
        }
        void skin(boolean upload) throws Exception {
            prepareGame();
            send(new ServerboundChatCommandPacket("skin"));
            MultiActionDialog menu = gameDialog();
            check(menu.actions().size() == 2, "/skin opens IGN and URL choices for unmodified protocol client");
            click(menu.actions().get(1), new CompoundTag());
            MultiActionDialog url = gameDialog();
            check(url.common().inputs().size() == 2, "URL dialog provides URL and arm-model choice");
            click(url.exitAction().orElseThrow(), new CompoundTag());
            menu = gameDialog(); click(menu.actions().getFirst(), new CompoundTag());
            MultiActionDialog ign = gameDialog();
            check(ign.common().inputs().size() == 1, "IGN dialog provides player name");
            CompoundTag source = new CompoundTag();
            if (upload) {
                var sample = new SkinService().fromIgn("Notch");
                var decoded = com.google.gson.JsonParser.parseString(new String(Base64.getDecoder().decode(sample.value()), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                String textureUrl = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
                click(ign.exitAction().orElseThrow(), new CompoundTag()); menu = gameDialog();
                click(menu.actions().get(1), new CompoundTag()); url = gameDialog();
                source.putString("source", textureUrl); source.putString("slim", "false");
                click(url.actions().getFirst(), source);
            } else { source.putString("source", "Notch"); click(ign.actions().getFirst(), source); }
            while (true) {
                Packet<?> packet = gamePacket();
                if (packet instanceof ClientboundStartConfigurationPacket) {
                    send(ServerboundConfigurationAcknowledgedPacket.INSTANCE);
                    inbound = ConfigurationProtocols.CLIENTBOUND.codec(); outbound = ConfigurationProtocols.SERVERBOUND.codec();
                    while (true) {
                        packet = read();
                        if (packet instanceof ClientboundPingPacket ping) send(new ServerboundPongPacket(ping.getId()));
                        else if (packet instanceof ClientboundSelectKnownPacks packs) send(new ServerboundSelectKnownPacks(packs.knownPacks()));
                        else if (packet instanceof ClientboundFinishConfigurationPacket) break;
                        else if (packet instanceof ClientboundShowDialogPacket) throw new AssertionError("Skin refresh wrongly requested password again");
                        else if (packet instanceof ClientboundDisconnectPacket kick) throw new AssertionError("Refresh disconnected: " + kick.reason());
                    }
                    send(ServerboundFinishConfigurationPacket.INSTANCE);
                    outbound = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY), new GameProtocols.Context() {
                        public boolean hasInfiniteMaterials() { return false; } public boolean canUseCommandBlocks() { return false; }
                    }).codec();
                    while (true) {
                        packet = gamePacket();
                        if (packet instanceof ClientboundPlayerInfoUpdatePacket info && info.newEntries().stream().anyMatch(e -> e.profile() != null && e.profile().properties().containsKey("textures"))) {
                            check(true, "skin refresh sends signed texture to client without reconnect or repeated login"); return;
                        }
                    }
                }
                if (packet instanceof ClientboundShowDialogPacket error) throw new AssertionError("Skin failed: " + error.dialog().value());
            }
        }
        void click(ActionButton button, CompoundTag fields) throws Exception {
            send(new ServerboundCustomClickActionPacket(((CustomAll) button.action().orElseThrow()).id(), Optional.of(fields)));
        }
        MultiActionDialog gameDialog() throws Exception {
            while (true) { Packet<?> packet = gamePacket(); if (packet instanceof ClientboundShowDialogPacket dialog) return (MultiActionDialog) dialog.dialog().value(); }
        }
        Packet<?> gamePacket() throws Exception {
            while (true) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(frame()));
                try {
                    int id = buffer.readVarInt();
                    java.util.Map<Integer, net.minecraft.network.protocol.PacketType<?>> types = new HashMap<>();
                    GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type, index) -> types.put(index, type));
                    var type = types.get(id);
                    if (type == CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG) return ClientboundShowDialogPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY));
                    if (type == CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE) { send(new ServerboundKeepAlivePacket(ClientboundKeepAlivePacket.STREAM_CODEC.decode(buffer).getId())); continue; }
                    if (type == CommonPacketTypes.CLIENTBOUND_PING) { send(new ServerboundPongPacket(ClientboundPingPacket.STREAM_CODEC.decode(buffer).getId())); continue; }
                    if (type == CommonPacketTypes.CLIENTBOUND_DISCONNECT) throw new AssertionError("Game disconnected: " + ClientboundDisconnectPacket.STREAM_CODEC.decode(buffer).reason());
                    if (type == GamePacketTypes.CLIENTBOUND_PLAYER_POSITION) {
                        var p = ClientboundPlayerPositionPacket.STREAM_CODEC.decode(buffer); var c = p.change();
                        send(new ServerboundAcceptTeleportationPacket(p.id(), c.position().x, c.position().y, c.position().z, c.yRot(), c.xRot())); continue;
                    }
                    if (type == GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE) return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY));
                    if (type == GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT) return ClientboundSystemChatPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY));
                    if (type == GamePacketTypes.CLIENTBOUND_START_CONFIGURATION) return ClientboundStartConfigurationPacket.INSTANCE;
                } finally { buffer.release(); }
            }
        }
        Packet<?> read() throws Exception {
            ByteBuf buffer = Unpooled.wrappedBuffer(frame());
            try { return (Packet<?>) inbound.decode(buffer); } finally { buffer.release(); }
        }
        byte[] frame() throws Exception {
            int length = varInt(input); if (length < 0 || length > 8_388_608) throw new IOException("Bad frame length");
            byte[] bytes = new byte[length];
            for (int offset = 0; offset < length;) {
                int count = input.read(bytes, offset, length - offset); if (count < 0) throw new EOFException(); offset += count;
            }
            if (compression >= 0) {
                ByteArrayInputStream compressed = new ByteArrayInputStream(bytes);
                int original = varInt(compressed);
                bytes = original == 0 ? compressed.readAllBytes() : new InflaterInputStream(compressed).readAllBytes();
            }
            return bytes;
        }
        void send(Packet<?> packet) throws Exception {
            ByteBuf buffer = Unpooled.buffer();
            byte[] bytes;
            try { outbound.encode(buffer, packet); bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); } finally { buffer.release(); }
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            if (compression >= 0) {
                if (bytes.length >= compression) {
                    varInt(frame, bytes.length);
                    DeflaterOutputStream deflate = new DeflaterOutputStream(frame); deflate.write(bytes); deflate.finish();
                } else { varInt(frame, 0); frame.write(bytes); }
                bytes = frame.toByteArray();
            }
            varInt(output, bytes.length); output.write(bytes); output.flush();
        }
        public void close() throws IOException { socket.close(); }
    }
    static int varInt(InputStream input) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) { int b = input.read(); if (b < 0) throw new EOFException(); value |= (b & 127) << (i * 7); if ((b & 128) == 0) return value; }
        throw new IOException("Bad VarInt");
    }
    static void varInt(OutputStream output, int value) throws IOException {
        do { int b = value & 127; value >>>= 7; output.write(b | (value == 0 ? 0 : 128)); } while (value != 0);
    }
}
