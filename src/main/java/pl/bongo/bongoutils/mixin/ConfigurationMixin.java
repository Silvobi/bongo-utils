package pl.bongo.bongoutils.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.bongoutils.*;
import java.util.Queue;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ConfigurationMixin extends ServerCommonPacketListenerImpl implements AuthGate {
    @Shadow @Final private GameProfile gameProfile;
    @Shadow @Final private Queue<ConfigurationTask> configurationTasks;
    @Unique private AuthTask bongo$task;
    protected ConfigurationMixin(MinecraftServer server, Connection connection, CommonListenerCookie cookie) { super(server, connection, cookie); }
    @Invoker("finishCurrentTask") protected abstract void bongo$finishTask(ConfigurationTask.Type type);
    @Inject(method = "returnToWorld", at = @At("HEAD"))
    private void bongo$gate(CallbackInfo ci) {
        ConnectionState state = (ConnectionState) connection;
        if (!state.bongo$authenticated() && bongo$task == null) {
            bongo$task = new AuthTask((ServerConfigurationPacketListenerImpl) (Object) this, server, gameProfile, state);
            configurationTasks.add(bongo$task);
        }
    }
    public void bongo$submit(net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket packet) {
        if (bongo$task != null) bongo$task.submit(packet);
    }
    public void bongo$finish() { bongo$finishTask(AuthTask.TYPE); }
}
