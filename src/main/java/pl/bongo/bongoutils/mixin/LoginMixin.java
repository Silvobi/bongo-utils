package pl.bongo.bongoutils.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.login.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;
    @Shadow @Final private byte[] challenge;
    @Shadow private String requestedUsername;
    @Shadow private GameProfile authenticatedProfile;
    @Shadow private volatile ServerLoginPacketListenerImpl.State state;
    @Shadow protected abstract void startClientVerification(GameProfile profile);
    @Shadow public abstract void disconnect(Component reason);
    @Unique private boolean bongo$offline;
    @Unique private boolean bongo$officialName;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void bongo$hello(ServerboundHelloPacket packet, CallbackInfo ci) {
        ci.cancel();
        if (state != ServerLoginPacketListenerImpl.State.HELLO || !packet.name().matches("[A-Za-z0-9_]{1,16}")) {
            disconnect(Component.literal(Lang.text((String) null,"login_invalid"))); return;
        }
        requestedUsername = packet.name();
        String address = connection.getRemoteAddress() instanceof java.net.InetSocketAddress remote ? remote.getAddress().getHostAddress() : "local";
        if (!BongoUtils.limits.allow("ip:" + address, 20, 60_000)) {
            disconnect(Component.literal(Lang.text((String) null,"login_rate_limit"))); return;
        }
        state = ServerLoginPacketListenerImpl.State.AUTHENTICATING;
        if (!BongoUtils.submit(() -> {
            try {
                UUID official = BongoUtils.skins.lookupUuid(packet.name());
                MsaLogin.Route route = MsaLogin.route(official, packet.profileId(), BongoUtils.store.reserved(packet.name()), BongoUtils.config.allowOffline);
                boolean premiumCandidate = route == MsaLogin.Route.VERIFY_SESSION;
                // The UUID only chooses the handshake. Only Mojang session verification grants access.
                if (!premiumCandidate && route != MsaLogin.Route.OFFLINE) {
                    server.execute(() -> disconnect(Component.literal(MsaLogin.rejection(route, packet.name())))); return;
                }
                server.execute(() -> {
                    if (!connection.isConnected() || state != ServerLoginPacketListenerImpl.State.AUTHENTICATING) return;
                    bongo$offline = !premiumCandidate;
                    bongo$officialName = official != null;
                    state = ServerLoginPacketListenerImpl.State.KEY;
                    connection.send(new ClientboundHelloPacket("", server.getKeyPair().getPublic().getEncoded(), challenge, premiumCandidate));
                });
            } catch (Exception e) {
                server.execute(() -> disconnect(Component.literal(Lang.text((String) null,"mojang_failed"))));
            }
        })) disconnect(Component.literal(Lang.text((String) null,"busy")));
    }

    @Inject(method = "handleKey", at = @At("HEAD"), cancellable = true)
    private void bongo$key(ServerboundKeyPacket packet, CallbackInfo ci) {
        if (!bongo$offline) return; // Vanilla verifies the actual online session and never falls back.
        ci.cancel();
        if (state != ServerLoginPacketListenerImpl.State.KEY) { disconnect(Component.literal(Lang.text((String) null,"packet_order"))); return; }
        try {
            var privateKey = server.getKeyPair().getPrivate();
            if (!packet.isChallengeValid(challenge, privateKey)) { disconnect(Component.literal(Lang.text((String) null,"connection_key"))); return; }
            var secret = packet.getSecretKey(privateKey);
            connection.setEncryptionKey(Crypt.getCipher(2, secret), Crypt.getCipher(1, secret));
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + Store.key(requestedUsername)).getBytes(StandardCharsets.UTF_8));
            startClientVerification(new GameProfile(uuid, requestedUsername));
        } catch (Exception e) { disconnect(Component.literal(Lang.text((String) null,"encryption_failed"))); }
    }

    @Inject(method = "startClientVerification", at = @At("HEAD"), cancellable = true)
    private void bongo$verified(GameProfile profile, CallbackInfo ci) {
        try {
            if (!bongo$offline) {
                ((ConnectionState) connection).bongo$verified(true);
                ((ConnectionState) connection).bongo$authenticated(true);
            }
        } catch (Exception e) {
            ci.cancel(); disconnect(Component.literal(Lang.text((String) null,"data_error")));
        }
    }

    @ModifyVariable(method = "disconnect", at = @At("HEAD"), argsOnly = true)
    private Component bongo$sessionRejection(Component reason) {
        // Some offline launchers claim the official UUID. Their failed session is still rejected.
        if (bongo$officialName && reason.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("multiplayer.disconnect.unverified_username")) {
            MsaLogin.Route route = BongoUtils.store.reserved(requestedUsername)
                    ? MsaLogin.Route.RESERVED_MSA : MsaLogin.Route.OCCUPIED_MSA;
            return Component.literal(MsaLogin.rejection(route, requestedUsername));
        }
        return reason;
    }

    @Inject(method = "startClientVerification", at = @At("RETURN"))
    private void bongo$restoreSkin(GameProfile profile, CallbackInfo ci) {
        try { authenticatedProfile = BongoUtils.skins.restore(profile); }
        catch (Exception e) { disconnect(Component.literal(Lang.text((String) null,"skin_read_failed"))); }
    }

    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
    private void bongo$duplicate(GameProfile profile, CallbackInfo ci) {
        // Vanilla disconnects an existing player before our configuration-phase password gate.
        if (server.getPlayerList().getPlayer(profile.id()) != null) {
            ci.cancel(); disconnect(Component.literal(Lang.text((String) null,"already_connected")));
        }
    }
}
