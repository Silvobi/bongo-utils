package pl.bongo.bongoutils.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.Social;
@Mixin(ServerPlayer.class)
public abstract class SocialPlayerMixin {
    @Inject(method="sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at=@At("HEAD"), cancellable=true)
    private void bongo$quiet(Component message, boolean overlay, CallbackInfo ci) {
        if (Social.hiddenSystem(message)) ci.cancel();
    }
    @Inject(method="sendChatMessage", at=@At("HEAD"), cancellable=true)
    private void bongo$chat(OutgoingChatMessage message, boolean filtered, ChatType.Bound type, CallbackInfo ci) {
        var sender = Social.chatSender(message,type);
        // Filter before vanilla records a signed message in the receiver's last-seen chain.
        if (sender != null && !Social.chatAllowed((ServerPlayer)(Object)this,sender)) ci.cancel();
    }
}
