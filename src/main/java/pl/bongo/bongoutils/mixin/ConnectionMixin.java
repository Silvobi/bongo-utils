package pl.bongo.bongoutils.mixin;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.bongo.bongoutils.ConnectionState;
@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionState {
    @Unique private String bongo$language="en_us";
    public String bongo$language() { return bongo$language; }
    public void bongo$language(String value) { bongo$language=value; }
    @Unique private boolean bongo$verified, bongo$authenticated, bongo$skinRefresh;
    public boolean bongo$verified() { return bongo$verified; }
    public void bongo$verified(boolean value) { bongo$verified = value; }
    public boolean bongo$authenticated() { return bongo$authenticated; }
    public void bongo$authenticated(boolean value) { bongo$authenticated = value; }
    public boolean bongo$skinRefresh() { return bongo$skinRefresh; }
    public void bongo$skinRefresh(boolean value) { bongo$skinRefresh = value; }
}
