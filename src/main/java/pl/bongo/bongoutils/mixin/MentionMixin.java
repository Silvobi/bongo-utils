package pl.bongo.bongoutils.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import pl.bongo.bongoutils.social.SocialFeatures;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MentionMixin {
    @Shadow public ServerPlayer player;
    @ModifyVariable(method="sendPlayerChatMessage",at=@At("HEAD"),argsOnly=true)
    private PlayerChatMessage bongo$mention(PlayerChatMessage value,PlayerChatMessage original,ChatType.Bound type) {
        return SocialFeatures.active==null?value:SocialFeatures.active.chat(player,value,type);
    }
}
