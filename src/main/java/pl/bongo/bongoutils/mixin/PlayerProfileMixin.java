package pl.bongo.bongoutils.mixin;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Player.class)
public interface PlayerProfileMixin {
    @Mutable @Accessor("gameProfile") void bongo$profile(GameProfile profile);
}
