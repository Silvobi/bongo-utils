package pl.bongo.bongoutils.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.*;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CommonListenerMixin implements pl.bongo.bongoutils.ListenerConnection {
    @org.spongepowered.asm.mixin.gen.Accessor("connection") public abstract net.minecraft.network.Connection bongo$connection();
    @Inject(method = "handleCustomClickAction", at = @At("RETURN"))
    private void bongo$click(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!packet.id().getNamespace().equals("bongoutils")) return;
        if ((Object) this instanceof AuthGate gate) gate.bongo$submit(packet);
        else if ((Object) this instanceof ServerGamePacketListenerImpl game) {
            BongoUtils.skinUi.submit(game.player, packet);
            BongoUtils.changePassUi.submit(game.player, packet);
        }
    }
}
