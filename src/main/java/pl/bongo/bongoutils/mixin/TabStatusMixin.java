package pl.bongo.bongoutils.mixin;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.social.SocialFeatures;

@Mixin(ClientboundPlayerInfoUpdatePacket.Entry.class)
public abstract class TabStatusMixin {
    @Shadow @Final private UUID profileId;
    @Shadow @Final @Mutable private Component displayName;
    @Inject(method="<init>(Ljava/util/UUID;Lcom/mojang/authlib/GameProfile;ZILnet/minecraft/world/level/GameType;Lnet/minecraft/network/chat/Component;ZILnet/minecraft/network/chat/RemoteChatSession$Data;)V",at=@At("RETURN"))
    private void bongo$status(CallbackInfo ci) {
        if(SocialFeatures.active!=null) displayName=SocialFeatures.active.tab(profileId,displayName);
    }
}
