package pl.bongo.bongoutils.mixin;

import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.social.SocialFeatures;

@Mixin(MsgCommand.class)
public abstract class MsgCommandMixin {
    @Inject(method="sendMessage",at=@At("RETURN"))
    private static void bongo$conversation(CommandSourceStack source,Collection<ServerPlayer> targets,PlayerChatMessage message,CallbackInfo ci) {
        if(SocialFeatures.active!=null) SocialFeatures.active.conversation(source,targets,message);
    }
}
