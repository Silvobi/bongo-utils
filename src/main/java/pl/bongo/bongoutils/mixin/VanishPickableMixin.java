package pl.bongo.bongoutils.mixin;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.Social;
@Mixin({LivingEntity.class,Player.class})
public abstract class VanishPickableMixin {
    @Inject(method={"isPickable","canBeSeenAsEnemy"},at=@At("HEAD"),cancellable=true)
    private void bongo$hidden(CallbackInfoReturnable<Boolean> ci){if(Social.full((Entity)(Object)this))ci.setReturnValue(false);}
}
