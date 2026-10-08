package pl.bongo.bongoutils.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.BongoUtils;

/** Vanilla's kick loop reads UserWhiteList directly, so changing isWhiteListed alone is insufficient. */
@Mixin(MinecraftServer.class)
public abstract class WhitelistEnforcementMixin {
    @Inject(method = "kickUnlistedPlayers", at = @At("HEAD"), cancellable = true)
    private void bongo$enforceIgn(CallbackInfo ci) {
        if (BongoUtils.ignWhitelist != null && BongoUtils.ignWhitelist.enabled()) {
            ci.cancel(); BongoUtils.ignWhitelist.kickUnlisted((MinecraftServer) (Object) this);
        }
    }
}
