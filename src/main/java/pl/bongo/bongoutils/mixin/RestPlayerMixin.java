package pl.bongo.bongoutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.Rest;

@Mixin(Player.class)
public abstract class RestPlayerMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void bongo$lying(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && Rest.laying(player)) { player.setPose(Pose.SLEEPING); ci.cancel(); }
    }
}
