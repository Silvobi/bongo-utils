package pl.bongo.bongoutils.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.ConnectionState;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GameListenerMixin extends ServerCommonPacketListenerImpl {
    protected GameListenerMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) { super(server, connection, cookie); }
    @Inject(method = "handleConfigurationAcknowledged", at = @At("RETURN"))
    private void bongo$refresh(ServerboundConfigurationAcknowledgedPacket packet, CallbackInfo ci) {
        ConnectionState state = (ConnectionState) connection;
        if (state.bongo$skinRefresh()) {
            state.bongo$skinRefresh(false);
            if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl config) config.startConfiguration();
        }
    }
}
