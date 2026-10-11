package pl.bongo.bongoutils.mixin;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.Social;
import java.util.Collection;
@Mixin(CommandSourceStack.class)
public abstract class SuggestionMixin {
    @Inject(method="getOnlinePlayerNames", at=@At("HEAD"), cancellable=true)
    private void bongo$names(CallbackInfoReturnable<Collection<String>> ci) {
        if (Social.state != null) ci.setReturnValue(Social.names((CommandSourceStack)(Object)this));
    }
}
