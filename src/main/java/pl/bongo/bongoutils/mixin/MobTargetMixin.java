package pl.bongo.bongoutils.mixin;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pl.bongo.bongoutils.Social;
@Mixin(Mob.class)
public abstract class MobTargetMixin {
    @Inject(method="setTarget", at=@At("HEAD"), cancellable=true)
    private void bongo$target(LivingEntity target, CallbackInfo ci) { if (Social.full(target)) ci.cancel(); }
    @Inject(method={"getTarget", "getTargetUnchecked"}, at=@At("RETURN"), cancellable=true)
    private void bongo$existing(CallbackInfoReturnable<LivingEntity> ci) { if (Social.full(ci.getReturnValue())) ci.setReturnValue(null); }
    @Inject(method="canAttack", at=@At("HEAD"), cancellable=true)
    private void bongo$attack(LivingEntity target, CallbackInfoReturnable<Boolean> ci) { if (Social.full(target)) ci.setReturnValue(false); }
}
