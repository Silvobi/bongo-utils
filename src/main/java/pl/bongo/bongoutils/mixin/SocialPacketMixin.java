package pl.bongo.bongoutils.mixin;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import pl.bongo.bongoutils.Social;
import java.util.*;
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class SocialPacketMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> bongo$tab(Packet<?> packet) {
        if ((Object)this instanceof ServerGamePacketListenerImpl game && Social.state != null) return transform(packet, game.player);
        return packet;
    }
    private static Packet<?> transform(Packet<?> packet, net.minecraft.server.level.ServerPlayer viewer) {
        if(packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
            var names=Social.state.snapshot().names;
            var filtered=suggestions.suggestions().stream().filter(e -> names.entrySet().stream().noneMatch(p -> !p.getKey().equals(viewer.getUUID())
                    && Social.hidden(p.getKey()) && (e.text().equalsIgnoreCase(p.getValue()) || e.text().equalsIgnoreCase(p.getKey().toString())))).toList();
            return new ClientboundCommandSuggestionsPacket(suggestions.id(),suggestions.start(),suggestions.length(),filtered);
        }
        if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
            var entries = info.entries().stream().map(e -> Social.tab(viewer,e)).filter(Objects::nonNull).toList();
            var actions = info.actions().clone();
            actions.addAll(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE));
            var next = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
            ((PlayerInfoAccess)next).bongo$entries(entries); return next;
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> children = new ArrayList<>();
            for (var child : bundle.subPackets()) {
                @SuppressWarnings("unchecked") var transformed = (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) transform(child,viewer);
                children.add(transformed);
            }
            return new ClientboundBundlePacket(children);
        }
        return packet;
    }
}
