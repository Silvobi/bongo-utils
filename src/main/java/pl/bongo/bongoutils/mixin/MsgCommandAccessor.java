package pl.bongo.bongoutils.mixin;

import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MsgCommand.class)
public interface MsgCommandAccessor {
    @Invoker("sendMessage")
    static void bongo$sendMessage(CommandSourceStack source,Collection<ServerPlayer> targets,PlayerChatMessage message) { throw new AssertionError(); }
}
