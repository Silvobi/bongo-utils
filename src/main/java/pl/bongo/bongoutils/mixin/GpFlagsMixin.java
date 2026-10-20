package pl.bongo.bongoutils.mixin;

import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.bongoutils.claims.ClaimFlags;

@Pseudo
@Mixin(targets="com.griefprevention.fabric.mc26_1.FabricClaimRepository",remap=false)
public abstract class GpFlagsMixin {
    @Inject(method="setFlag(JLcom/griefprevention/claims/ClaimFlag;Z)Ljava/lang/Boolean;",at=@At("RETURN"),remap=false)
    private void bongo$flag(long id,@Coerce Object flag,boolean value,CallbackInfoReturnable<Boolean> ci) {
        if(ci.getReturnValue()!=null) ClaimFlags.nativeChanged(this,List.of(id),flag,value);
    }
    @Inject(method="setFlag(Ljava/util/Collection;Lcom/griefprevention/claims/ClaimFlag;Z)I",at=@At("RETURN"),remap=false)
    private void bongo$flags(Collection<Long> ids,@Coerce Object flag,boolean value,CallbackInfoReturnable<Integer> ci) {
        if(ci.getReturnValue()>0) ClaimFlags.nativeChanged(this,ids,flag,value);
    }
    @Inject(method="setPvpToggle(JZ)Z",at=@At("RETURN"),remap=false)
    private void bongo$pvp(long id,boolean value,CallbackInfoReturnable<Boolean> ci) {
        if(ci.getReturnValue()) ClaimFlags.nativePvpChanged(this,id,value);
    }
}
