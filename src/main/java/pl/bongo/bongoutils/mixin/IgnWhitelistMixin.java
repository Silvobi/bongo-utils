package pl.bongo.bongoutils.mixin;

import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.BongoUtils;

@Mixin(DedicatedPlayerList.class)
public abstract class IgnWhitelistMixin {
    @Inject(method = "isWhiteListed", at = @At("HEAD"), cancellable = true)
    private void bongo$ignOnly(NameAndId player, CallbackInfoReturnable<Boolean> ci) {
        if (BongoUtils.ignWhitelist != null && BongoUtils.ignWhitelist.enabled())
            ci.setReturnValue(BongoUtils.ignWhitelist.contains(player.name()));
    }
}
