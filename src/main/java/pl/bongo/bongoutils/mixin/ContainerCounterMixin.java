package pl.bongo.bongoutils.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.Social;
import java.util.List;
@Mixin(ContainerOpenersCounter.class)
public abstract class ContainerCounterMixin {
    @Inject(method="getEntitiesWithContainerOpen", at=@At("RETURN"), cancellable=true)
    private void bongo$count(Level level, BlockPos pos, CallbackInfoReturnable<List<ContainerUser>> ci) {
        ci.setReturnValue(ci.getReturnValue().stream().filter(u -> !(u instanceof net.minecraft.world.entity.Entity e) || !Social.full(e)).toList());
    }
}
