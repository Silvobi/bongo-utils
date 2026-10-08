package pl.bongo.bongoutils;

public interface AuthGate {
    void bongo$submit(net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket packet);
    void bongo$finish();
}
