package pl.bongo.bongoutils.mixin;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoAccess {
    @Mutable @Accessor("entries") void bongo$entries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
