package pl.bongo.bongoutils.mixin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.Social;
@Mixin(Entity.class)
public abstract class VanishEntityMixin {
    @Inject(method={"isInvisible", "isSilent"}, at=@At("HEAD"), cancellable=true)
    private void bongo$hidden(CallbackInfoReturnable<Boolean> ci) { if (Social.full((Entity)(Object)this)) ci.setReturnValue(true); }
    @Inject(method={"isPushable", "isPickable"}, at=@At("HEAD"), cancellable=true)
    private void bongo$collision(CallbackInfoReturnable<Boolean> ci) { if (Social.full((Entity)(Object)this)) ci.setReturnValue(false); }
}
