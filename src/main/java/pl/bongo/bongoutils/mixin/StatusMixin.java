package pl.bongo.bongoutils.mixin;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.status.*;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import pl.bongo.bongoutils.Social;
import java.util.*;
@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class StatusMixin {
    @ModifyArg(method="handleStatusRequest", at=@At(value="INVOKE", target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"), index=0)
    private Packet<?> bongo$status(Packet<?> packet) {
        if (!(packet instanceof ClientboundStatusResponsePacket response) || Social.server == null) return packet;
        ServerStatus status = response.status();
        var visible = Social.server.getPlayerList().getPlayers().stream().filter(p -> !Social.hidden(p.getUUID())).toList();
        var players = status.players().map(p -> new ServerStatus.Players(p.max(),visible.size(),
                p.sample().stream().filter(e -> !Social.hidden(e.id())).toList()));
        return new ClientboundStatusResponsePacket(new ServerStatus(status.description(),players,status.version(),status.favicon(),status.enforcesSecureChat()));
    }
}
