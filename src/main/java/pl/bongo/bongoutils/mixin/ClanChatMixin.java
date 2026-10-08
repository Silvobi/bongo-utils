package pl.bongo.bongoutils.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import pl.bongo.bongoutils.Social;
/** Optional compatibility with Bongo's Community Clans 1.0.3; no mandatory dependency. */
@Pseudo @Mixin(targets="pl.bongo.clans.ClanRuntime", remap=false)
public abstract class ClanChatMixin {
    @Redirect(method="chat", at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"),remap=false)
    private void bongo$chat(ServerPlayer recipient, Component message, ServerPlayer sender, String text) {
        if(Social.chatAllowed(recipient,sender.getUUID())) recipient.sendSystemMessage(message);
    }
}
