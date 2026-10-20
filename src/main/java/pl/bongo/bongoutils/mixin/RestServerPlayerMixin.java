package pl.bongo.bongoutils.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pl.bongo.bongoutils.*;

@Mixin(ServerPlayer.class)
public abstract class RestServerPlayerMixin {
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void bongo$hurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) Rest.stop((ServerPlayer)(Object)this, false);
    }
    @Inject(method = "setGameMode", at = @At("HEAD"))
    private void bongo$mode(net.minecraft.world.level.GameType mode, CallbackInfoReturnable<Boolean> ci) { Rest.stop((ServerPlayer)(Object)this, true); }
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"))
    private void bongo$dimension(net.minecraft.world.level.portal.TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> ci) {
        Rest.stop((ServerPlayer)(Object)this, false);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"))
    private void bongo$worldTeleport(ServerLevel level, double x, double y, double z, java.util.Set<net.minecraft.world.entity.Relative> relatives,
                                     float yaw, float pitch, boolean camera, CallbackInfoReturnable<Boolean> ci) {
        Rest.stop((ServerPlayer)(Object)this, false);
    }
    @Inject(method = "saveParentVehicle", at = @At("HEAD"), cancellable = true)
    private void bongo$noSeatSave(ValueOutput output, CallbackInfo ci) {
        if (((ServerPlayer)(Object)this).getVehicle() instanceof RestSeat) ci.cancel();
    }
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void bongo$saveAnchor(ValueOutput output, CallbackInfo ci) {
        Vec3 anchor = Rest.savedPosition((ServerPlayer)(Object)this);
        if (anchor != null) output.store("Pos", Vec3.CODEC, anchor);
    }
}
