package pl.bongo.bongoutils;

import java.io.IOException;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/** Operator dialogs bind every action to the current connection and a server-side account snapshot. */
public final class ChangePassUi {
    private final Map<UUID, Session> sessions = new HashMap<>();
    public static boolean allowed(CommandSourceStack source) {
        return Commands.hasPermission(Commands.LEVEL_ADMINS).test(source);
    }
    public int open(ServerPlayer player) {
        if (!allowed(player.createCommandSourceStack())) return 0;
        choose(player, Lang.text(player,"change_intro"));
        return 1;
    }
    private Session session(ServerPlayer player, String stage, String target, String hash) {
        Session session = new Session(player, stage, target, hash);
        sessions.put(player.getUUID(), session);
        return session;
    }
    private void choose(ServerPlayer player, String message) {
        Session session = session(player, "nick", null, null);
        player.openDialog(Dialogs.changePassNick(Lang.locale(player), session.token, message));
    }
    private void edit(ServerPlayer player, String target, String hash, String message) {
        Session session = session(player, "edit", target, hash);
        player.openDialog(Dialogs.changePassEdit(Lang.locale(player), session.token, target, message));
    }
    public void submit(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        if (!packet.id().getPath().startsWith("changepass_")) return;
        Session session = sessions.get(player.getUUID());
        if (session == null || session.owner != player || session.expires < System.nanoTime()) return;
        if (!allowed(player.createCommandSourceStack())) {
            sessions.remove(player.getUUID());
            player.connection.send(ClientboundClearDialogPacket.INSTANCE);
            player.sendSystemMessage(Component.literal(Lang.text(player,"change_denied")));
            return;
        }
        String[] action = packet.id().getPath().split("/", 2);
        if (action.length != 2 || !session.token.equals(action[1]) || session.busy) return;
        try {
            switch (action[0]) {
                case "changepass_cancel" -> {
                    sessions.remove(player.getUUID());
                    player.connection.send(ClientboundClearDialogPacket.INSTANCE);
                }
                case "changepass_back" -> choose(player, Lang.text(player,"change_choose"));
                case "changepass_lookup" -> {
                    if (!session.stage.equals("nick") || !(packet.payload().orElse(null) instanceof CompoundTag data)) return;
                    String target = data.getString("nick").orElse("").strip();
                    if (!target.matches("[A-Za-z0-9_]{1,16}")) { choose(player, Lang.text(player,"invalid_nick")); return; }
                    String hash = BongoUtils.store.offlinePassword(target);
                    if (hash == null) { choose(player, Lang.text(player,"change_no_account")); return; }
                    edit(player, target, hash, Lang.text(player,"change_warning"));
                }
                case "changepass_save" -> {
                    if (!session.stage.equals("edit") || !(packet.payload().orElse(null) instanceof CompoundTag data)) return;
                    String password = data.getString("password").orElse("");
                    if (password.length() < 8 || password.length() > 128) { edit(player, session.target, session.hash, Lang.text(player,"change_length")); return; }
                    if (!password.equals(data.getString("repeat").orElse(""))) { edit(player, session.target, session.hash, Lang.text(player,"change_mismatch")); return; }
                    if (!BongoUtils.limits.allow("changepass:" + player.getUUID(), 10, 60_000)) {
                        edit(player, session.target, session.hash, Lang.text(player,"change_rate_limit")); return;
                    }
                    session.busy = true;
                    MinecraftServer server = Objects.requireNonNull(player.level().getServer());
                    if (!BongoUtils.submit(() -> {
                        try {
                            String hash = PasswordHash.create(password);
                            server.execute(() -> {
                                session.busy = false;
                                if (sessions.get(player.getUUID()) != session || session.owner != server.getPlayerList().getPlayer(player.getUUID())
                                        || session.expires < System.nanoTime() || !player.connection.isAcceptingMessages()) return;
                                if (!allowed(player.createCommandSourceStack())) {
                                    sessions.remove(player.getUUID());
                                    player.connection.send(ClientboundClearDialogPacket.INSTANCE);
                                    player.sendSystemMessage(Component.literal(Lang.text(player,"change_permission_revoked"))); return;
                                }
                                try { apply(player, session, hash); }
                                catch (IOException e) { storageError(player); }
                            });
                        } catch (Exception e) {
                            server.execute(() -> {
                                if (sessions.get(player.getUUID()) == session) { session.busy = false; storageError(player); }
                            });
                        }
                    })) { session.busy = false; edit(player, session.target, session.hash, Lang.text(player,"busy")); }
                }
                case "changepass_clear" -> {
                    if (!session.stage.equals("edit")) return;
                    Session next = session(player, "confirm", session.target, session.hash);
                    player.openDialog(Dialogs.changePassClear(Lang.locale(player), next.token, next.target));
                }
                case "changepass_edit" -> {
                    if (!session.stage.equals("confirm")) return;
                    edit(player, session.target, session.hash, Lang.text(player,"change_edit"));
                }
                case "changepass_confirm" -> {
                    if (!session.stage.equals("confirm")) return;
                    apply(player, session, null);
                }
            }
        } catch (IOException e) { storageError(player); }
    }
    private void apply(ServerPlayer operator, Session session, String nextHash) throws IOException {
        if (!BongoUtils.store.editOfflinePassword(session.target, session.hash, nextHash)) {
            choose(operator, Lang.text(operator,"change_stale")); return;
        }
        sessions.remove(operator.getUUID());
        operator.connection.send(ClientboundClearDialogPacket.INSTANCE);
        operator.sendSystemMessage(Component.literal(nextHash == null
                ? Lang.text(operator,"change_cleared", session.target)
                : Lang.text(operator,"change_saved", session.target)));
        MinecraftServer server = Objects.requireNonNull(operator.level().getServer());
        BongoUtils.LOG.info("Operator {} {} password for offline account {}", operator.getGameProfile().name(),
                nextHash == null ? "cleared" : "changed", Store.key(session.target));
        for (var connection : List.copyOf(server.getConnection().getConnections())) {
            if (connection.getPacketListener() instanceof ServerCommonPacketListenerImpl target
                    && !((ConnectionState) connection).bongo$verified()
                    && Store.key(target.getOwner().name()).equals(Store.key(session.target)))
                target.disconnect(Component.literal(nextHash == null
                        ? Lang.text(target,"change_disconnect_clear")
                        : Lang.text(target,"change_disconnect_save")));
        }
    }
    private void storageError(ServerPlayer player) {
        choose(player, Lang.text(player,"change_storage_error"));
    }
    public void disconnected(UUID uuid) { sessions.remove(uuid); }
    private static final class Session {
        final ServerPlayer owner;
        final String token = UUID.randomUUID().toString(), stage, target, hash;
        final long expires = System.nanoTime() + 300_000_000_000L;
        boolean busy;
        Session(ServerPlayer owner, String stage, String target, String hash) {
            this.owner = owner; this.stage = stage; this.target = target; this.hash = hash;
        }
    }
}
