package pl.bongo.bongoutils.mixin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.Social;
import java.util.List;
@Mixin(EntitySelector.class)
public abstract class SelectorMixin {
    @Shadow private String playerName;
    @Inject(method="findPlayers", at=@At("RETURN"), cancellable=true)
    private void bongo$players(CommandSourceStack source, CallbackInfoReturnable<List<ServerPlayer>> ci) {
        if (Social.state == null || source.getPlayer() == null) return;
        ci.setReturnValue(ci.getReturnValue().stream().filter(p -> p == source.getPlayer() || !Social.hidden(p.getUUID())
                || playerName != null && !Social.full(p)).toList());
    }
    @Inject(method="findEntities", at=@At("RETURN"), cancellable=true)
    private void bongo$entities(CommandSourceStack source, CallbackInfoReturnable<List<net.minecraft.world.entity.Entity>> ci) {
        if (Social.state == null || source.getPlayer() == null) return;
        ci.setReturnValue(ci.getReturnValue().stream().filter(e -> !(e instanceof ServerPlayer p) || p == source.getPlayer()
                || !Social.hidden(p.getUUID()) || playerName != null && !Social.full(p)).toList());
    }
}
