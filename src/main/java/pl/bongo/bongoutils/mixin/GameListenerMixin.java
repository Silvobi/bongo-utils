package pl.bongo.bongoutils.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.ConnectionState;
import pl.bongo.bongoutils.Rest;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GameListenerMixin extends ServerCommonPacketListenerImpl {
    protected GameListenerMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) { super(server, connection, cookie); }
    @Inject(method = "switchToConfig", at = @At("HEAD"))
    private void bongo$configuration(CallbackInfo ci) { Rest.stop(((ServerGamePacketListenerImpl)(Object)this).player, true); }
    @Inject(method = "handlePlayerInput", at = @At("RETURN"))
    private void bongo$stand(net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        Rest.input(((ServerGamePacketListenerImpl)(Object)this).player, packet.input());
    }
    @Inject(method = "handlePlayerCommand", at = @At("RETURN"))
    private void bongo$leaveBed(net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SLEEPING)
            Rest.stop(((ServerGamePacketListenerImpl)(Object)this).player, true);
    }
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void bongo$teleport(net.minecraft.world.entity.PositionMoveRotation position, java.util.Set<net.minecraft.world.entity.Relative> relatives, CallbackInfo ci) {
        Rest.stop(((ServerGamePacketListenerImpl)(Object)this).player, false);
    }
    @Inject(method = "handleConfigurationAcknowledged", at = @At("RETURN"))
    private void bongo$refresh(ServerboundConfigurationAcknowledgedPacket packet, CallbackInfo ci) {
        ConnectionState state = (ConnectionState) connection;
        if (state.bongo$skinRefresh()) {
            state.bongo$skinRefresh(false);
            if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config) config.startConfiguration();
        }
    }
}
