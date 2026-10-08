package pl.bongo.bongoutils.mixin;
import net.minecraft.server.level.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Accessor("entityMap") Int2ObjectMap<ChunkMap.TrackedEntity> bongo$entities();
}
