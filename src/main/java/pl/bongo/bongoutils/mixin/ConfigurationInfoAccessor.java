package pl.bongo.bongoutils.mixin;

import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public interface ConfigurationInfoAccessor {
    @Accessor("clientInformation") ClientInformation bongo$clientInformation();
}
