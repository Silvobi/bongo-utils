package pl.bongo.bongoutils.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.Social;
@Mixin(targets="net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackingMixin {
    @Shadow @Final private Entity entity;
    @Shadow public abstract void removePlayer(ServerPlayer player);
    @Inject(method="updatePlayer", at=@At("HEAD"), cancellable=true)
    private void bongo$track(ServerPlayer viewer, CallbackInfo ci) {
        if (entity != viewer && Social.full(entity)) { removePlayer(viewer); ci.cancel(); }
    }
}
