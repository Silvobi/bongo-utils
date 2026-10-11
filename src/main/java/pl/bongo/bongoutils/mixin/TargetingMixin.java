package pl.bongo.bongoutils.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.Social;
@Mixin(TargetingConditions.class)
public abstract class TargetingMixin {
    @Inject(method="test", at=@At("HEAD"), cancellable=true)
    private void bongo$target(ServerLevel level, LivingEntity observer, LivingEntity target, CallbackInfoReturnable<Boolean> ci) {
        if (Social.full(target)) ci.setReturnValue(false);
    }
}
