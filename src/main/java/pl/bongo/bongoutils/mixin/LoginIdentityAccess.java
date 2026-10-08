package pl.bongo.bongoutils.mixin;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerLoginPacketListenerImpl.class)
public interface LoginIdentityAccess {
    @Accessor("requestedUsername") String bongo$requestedName();
}
