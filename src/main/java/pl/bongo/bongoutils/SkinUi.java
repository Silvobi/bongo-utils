package pl.bongo.bongoutils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.level.ServerPlayer;
import pl.bongo.bongoutils.mixin.PlayerProfileMixin;
import java.util.*;

public final class SkinUi {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    public int open(ServerPlayer player) {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis());
        if (busy.contains(player.getUUID())) { player.sendSystemMessage(Component.literal(I18n.t(player, "skin.processing"))); return 0; }
        Session session = session(player, "menu");
        player.openDialog(Dialogs.skinMenu(I18n.language(player), session.token)); return 1;
    }
    private Session session(ServerPlayer player, String screen) {
        Session session = new Session(UUID.randomUUID().toString(), screen, System.nanoTime() + 300_000_000_000L);
        sessions.put(player.getUUID(), session); return session;
    }
    public void submit(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        Session session = sessions.get(player.getUUID());
        if (session == null || session.expiry < System.nanoTime() || busy.contains(player.getUUID())) return;
        String[] action = packet.id().getPath().split("/", 2);
        if (action.length != 2 || !session.token.equals(action[1])) return;
        switch (action[0]) {
            case "skin_back" -> { open(player); }
            case "skin_ign", "skin_url" -> {
                if (!session.screen.equals("menu")) return;
                boolean url = action[0].equals("skin_url");
                Session next = session(player, url ? "url" : "ign");
                player.openDialog(Dialogs.skinForm(I18n.language(player), url, next.token, url ? I18n.t(player, "skin.url.help") : I18n.t(player, "skin.ign.help")));
            }
            case "skin_save_ign", "skin_save_url" -> {
                boolean url = action[0].equals("skin_save_url");
                if (!session.screen.equals(url ? "url" : "ign")) return;
                if (!(packet.payload().orElse(null) instanceof CompoundTag fields)) return;
                String source = fields.getString("source").orElse("").strip();
                if (source.isEmpty() || source.length() > (url ? 2048 : 16)) { error(player, url, I18n.t(player, "skin.source.invalid")); return; }
                long now = System.currentTimeMillis();
                if (now < cooldowns.getOrDefault(player.getUUID(), 0L)) { error(player, url, I18n.t(player, "skin.cooldown")); return; }
                busy.add(player.getUUID()); sessions.remove(player.getUUID());
                boolean slim = fields.getString("slim").orElse("false").equals("true");
                var server = Objects.requireNonNull(player.level().getServer());
                if (!BongoUtils.submitSkin(() -> {
                    try {
                        Store.SkinRecord skin = url ? BongoUtils.skins.fromUrl(source, slim) : BongoUtils.skins.fromIgn(source);
                        BongoUtils.store.skin(player.getUUID(), skin);
                        server.execute(() -> {
                            busy.remove(player.getUUID()); cooldowns.put(player.getUUID(), System.currentTimeMillis() + 30_000);
                            if (server.getPlayerList().getPlayer(player.getUUID()) != player || !player.connection.isAcceptingMessages()) return;
                            ((PlayerProfileMixin) player).bongo$profile(SkinService.apply(player.getGameProfile(), skin));
                            player.connection.send(ClientboundClearDialogPacket.INSTANCE);
                            player.sendSystemMessage(Component.literal(I18n.t(player, "skin.saved")));
                            ((ConnectionState) ((ListenerConnection) player.connection).bongo$connection()).bongo$skinRefresh(true);
                            player.connection.switchToConfig();
                        });
                    } catch (Exception e) {
                        server.execute(() -> {
                            busy.remove(player.getUUID());
                            if (server.getPlayerList().getPlayer(player.getUUID()) == player && player.connection.isAcceptingMessages())
                                error(player, url, I18n.error(I18n.language(player),e,"skin.error"));
                        });
                    }
                })) { busy.remove(player.getUUID()); error(player, url, I18n.t(player, "skin.queue")); return; }
                player.connection.send(ClientboundClearDialogPacket.INSTANCE);
                player.sendSystemMessage(Component.literal(I18n.t(player, "skin.pending")));
            }
        }
    }
    private void error(ServerPlayer player, boolean url, String message) {
        Session next = session(player, url ? "url" : "ign");
        player.openDialog(Dialogs.skinForm(I18n.language(player), url, next.token, message));
    }
    public void disconnected(UUID uuid) { sessions.remove(uuid); }
    private record Session(String token, String screen, long expiry) {}
}
