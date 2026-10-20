package pl.bongo.bongoutils.mixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.Social;
@Mixin(ServerLevel.class)
public abstract class VanishGameEventMixin {
    @Inject(method="gameEvent",at=@At("HEAD"),cancellable=true)
    private void bongo$silent(Holder<GameEvent> event,Vec3 position,GameEvent.Context context,CallbackInfo ci) {
        if(Social.full(context.sourceEntity()))ci.cancel();
    }
}
