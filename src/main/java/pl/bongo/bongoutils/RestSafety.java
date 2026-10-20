package pl.bongo.bongoutils;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** Checks real collision surfaces, including slabs, stairs, carpets and snow layers. */
public final class RestSafety {
    public enum Problem { HAZARD, SUPPORT, SPACE }
    public static final TagKey<net.minecraft.world.level.block.Block> EXTRA_UNSAFE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("bongoutils", "unsafe_rest"));
    private static final Set<String> HAZARDS = Set.of("fire", "soul_fire", "cactus", "magma_block", "sweet_berry_bush",
            "wither_rose", "powder_snow", "pointed_dripstone", "lava_cauldron", "nether_portal", "end_portal", "end_gateway");
    private RestSafety() {}
    public static boolean moving(Input input) {
        return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift();
    }
    public static boolean hazardous(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.is(EXTRA_UNSAFE)) return true;
        if (state.getBlock() instanceof CampfireBlock) return state.getValue(BlockStateProperties.LIT);
        return state.is(BlockTags.DANGEROUS_FOR_TELEPORTATION) || state.is(BlockTags.PORTALS)
                || HAZARDS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }
    /** Sleeping renderer extends the model from its feet; cover both its body and normal standing space. */
    public static AABB occupied(Vec3 feet, EntityDimensions dimensions, Rest.Mode mode, float yaw) {
        AABB standing = dimensions.makeBoundingBox(feet);
        if (mode == Rest.Mode.SIT) return standing;
        double scale = dimensions.height() / 1.8, angle = Math.toRadians(yaw);
        double dx = Math.cos(angle), dz = -Math.sin(angle), length = 1.8 * scale, halfWidth = dimensions.width() / 2.0;
        AABB lying = new AABB(feet.x - Math.abs(dz) * halfWidth + Math.min(-dx * .2 * scale, dx * length), feet.y,
                feet.z - Math.abs(dx) * halfWidth + Math.min(-dz * .2 * scale, dz * length),
                feet.x + Math.abs(dz) * halfWidth + Math.max(-dx * .2 * scale, dx * length), feet.y + .6 * scale,
                feet.z + Math.abs(dx) * halfWidth + Math.max(-dz * .2 * scale, dz * length));
        return standing.minmax(lying);
    }
    public static Problem check(BlockGetter level, Vec3 feet, EntityDimensions dimensions, Rest.Mode mode, float yaw) {
        AABB space = occupied(feet, dimensions, mode, yaw).deflate(1.0E-5);
        AABB surroundings = space.inflate(.1, 0, .1).expandTowards(0, -.15, 0);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(surroundings.minX, surroundings.minY, surroundings.minZ),
                BlockPos.containing(surroundings.maxX, surroundings.maxY, surroundings.maxZ)))
            if (hazardous(level.getBlockState(pos))) return Problem.HAZARD;
        if (mode == Rest.Mode.SIT) {
            if (!supported(level, feet.x, feet.y, feet.z)) return Problem.SUPPORT;
        } else {
            for (double x : new double[]{space.minX + .03, (space.minX + space.maxX) / 2, space.maxX - .03})
                for (double z : new double[]{space.minZ + .03, (space.minZ + space.maxZ) / 2, space.maxZ - .03})
                    if (!supported(level, x, feet.y, z)) return Problem.SUPPORT;
        }
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(space.minX, space.minY - 1, space.minZ),
                BlockPos.containing(space.maxX, space.maxY, space.maxZ)))
            for (AABB shape : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs())
                if (shape.move(pos).intersects(space)) return Problem.SPACE;
        return null;
    }
    private static boolean supported(BlockGetter level, double x, double y, double z) {
        BlockPos top = BlockPos.containing(x, y - .01, z);
        for (BlockPos pos : new BlockPos[]{top, top.below()})
            for (AABB local : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                AABB shape = local.move(pos);
                if (Math.abs(shape.maxY - y) < .08 && x >= shape.minX - 1.0E-5 && x <= shape.maxX + 1.0E-5
                        && z >= shape.minZ - 1.0E-5 && z <= shape.maxZ + 1.0E-5) return true;
            }
        return false;
    }
}
