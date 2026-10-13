package pl.bongo.bongoutils.mixin;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.Social;
@Mixin({ChestBlockEntity.class, EnderChestBlockEntity.class, BarrelBlockEntity.class, ShulkerBoxBlockEntity.class})
public abstract class SilentContainerMixin {
    @Inject(method={"startOpen", "stopOpen"}, at=@At("HEAD"), cancellable=true)
    private void bongo$silent(ContainerUser user, CallbackInfo ci) {
        if (user instanceof net.minecraft.world.entity.Entity e && Social.full(e)) ci.cancel();
    }
}
