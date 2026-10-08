package pl.bongo.bongoutils.mixin;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.*;
@Mixin(PlayerList.class)
public interface PlayerListCacheAccess {
    @Accessor("stats") Map<UUID,ServerStatsCounter> bongo$stats();
    @Accessor("advancements") Map<UUID,PlayerAdvancements> bongo$advancements();
}
