package pl.bongo.bongoutils.mixin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import pl.bongo.bongoutils.Social;
import java.util.*;
@Mixin(MsgCommand.class)
public abstract class MsgMixin {
    @ModifyVariable(method="sendMessage", at=@At("HEAD"), argsOnly=true)
    private static Collection<ServerPlayer> bongo$targets(Collection<ServerPlayer> targets, CommandSourceStack source,
            Collection<ServerPlayer> original, PlayerChatMessage message) {
        if (source.getPlayer() == null || Social.state == null) return targets;
        ServerPlayer sender = source.getPlayer();
        var allowed = targets.stream().filter(p -> !Social.blocked(sender.getUUID(),p.getUUID()) && (p == sender || !Social.full(p))).toList();
        if (allowed.isEmpty()) source.sendFailure(Component.literal(Social.polish(sender) ? "Nie można wysłać wiadomości do tego gracza." : "Cannot message that player."));
        return allowed;
    }
}
