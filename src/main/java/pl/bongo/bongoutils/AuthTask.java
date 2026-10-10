package pl.bongo.bongoutils;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import java.util.UUID;
import java.util.function.Consumer;

/** Authentication runs after registries arrive, before PrepareSpawnTask and JoinWorldTask. */
public final class AuthTask implements ConfigurationTask {
    public static final Type TYPE = new Type("bongoutils:authentication");
    private final ServerConfigurationPacketListenerImpl owner;
    private final MinecraftServer server;
    private final GameProfile profile;
    private final ConnectionState state;
    private String token;
    private Consumer<Packet<?>> sender;
    private long started, lastAttempt;
    private boolean busy, done, registered;
    private int attempts;
    public AuthTask(ServerConfigurationPacketListenerImpl owner, MinecraftServer server, GameProfile profile, ConnectionState state) {
        this.owner = owner; this.server = server; this.profile = profile; this.state = state;
    }
    public Type type() { return TYPE; }
    public void start(Consumer<Packet<?>> sender) {
        this.sender = sender; started = System.nanoTime();
        try { registered = BongoUtils.store.password(profile.name()) != null; show(Lang.text(owner,"auth_intro")); }
        catch (Exception e) { fail(Lang.text(owner,"account_read_failed")); }
    }
    private void show(String message) {
        token = UUID.randomUUID().toString();
        sender.accept(new ClientboundShowDialogPacket(Dialogs.auth(Lang.locale(owner), !registered, token, message)));
    }
    public void submit(ServerboundCustomClickActionPacket packet) {
        if (sender == null || done || busy) return;
        if (packet.id().equals(Dialogs.id("quit/" + token))) { fail(Lang.text(owner,"disconnected")); return; }
        if (!packet.id().equals(Dialogs.id("auth/" + token))) return;
        if (System.nanoTime() - lastAttempt < 1_000_000_000L) return;
        lastAttempt = System.nanoTime();
        if (!(packet.payload().orElse(null) instanceof CompoundTag data)) { show(Lang.text(owner,"invalid_form")); return; }
        String password = data.getString("password").orElse("");
        if (password.length() < 8 || password.length() > 128) { show(Lang.text(owner,"password_length")); return; }
        if (!registered && !password.equals(data.getString("repeat").orElse(""))) { show(Lang.text(owner,"password_mismatch")); return; }
        if (!BongoUtils.limits.allow("password:" + Store.key(profile.name()), 10, 300_000)) { fail(Lang.text(owner,"auth_rate_limit")); return; }
        busy = true;
        if (!BongoUtils.submit(() -> {
            try {
                boolean valid;
                String existing = BongoUtils.store.password(profile.name());
                String verifiedHash;
                if (existing != null) { verifiedHash = existing; valid = PasswordHash.verify(password, existing); }
                else {
                    verifiedHash = !registered ? PasswordHash.create(password) : null;
                    valid = verifiedHash != null && BongoUtils.store.register(profile.name(), verifiedHash);
                }
                server.execute(() -> {
                    busy = false;
                    if (done || !owner.isAcceptingMessages()) return;
                    try {
                        if (valid && BongoUtils.store.matchesOfflinePassword(profile.name(), verifiedHash)) {
                            done = true; state.bongo$authenticated(true);
                            sender.accept(ClientboundClearDialogPacket.INSTANCE);
                            ((AuthGate) owner).bongo$finish();
                        } else if (++attempts >= 5) { fail(Lang.text(owner,"auth_attempt_limit")); }
                        else { registered = BongoUtils.store.password(profile.name()) != null; show(Lang.text(owner,"auth_invalid", 5 - attempts)); }
                    } catch (Exception e) { fail(Lang.text(owner,"account_read_error")); }
                });
            } catch (Exception e) { server.execute(() -> fail(Lang.text(owner,"account_io_error"))); }
        })) { busy = false; show(Lang.text(owner,"busy_wait")); }
    }
    private void fail(String reason) { done = true; owner.disconnect(Component.literal(reason)); }
    public boolean tick() {
        if (!done && System.nanoTime() - started > 120_000_000_000L) fail(Lang.text(owner,"auth_timeout"));
        return false;
    }
}
