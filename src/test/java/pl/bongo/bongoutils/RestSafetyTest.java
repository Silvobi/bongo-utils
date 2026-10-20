package pl.bongo.bongoutils;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class RestSafetyTest {
    static final EntityDimensions PLAYER = EntityDimensions.scalable(.6F, 1.8F);
    @BeforeAll static void boot() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void acceptsFullBlocksBottomSlabsCarpetsAndStairTreads() {
        Grid grid = new Grid(); grid.floor(Blocks.STONE.defaultBlockState());
        assertNull(RestSafety.check(grid, new Vec3(.5, 1, .5), PLAYER, Rest.Mode.SIT, 0));
        assertNull(RestSafety.check(grid, new Vec3(.5, 1, .5), PLAYER, Rest.Mode.LAY, 0));
        assertNull(RestSafety.check(grid, new Vec3(.5, 1, .5), PLAYER, Rest.Mode.LAY, 90));
        grid.floor(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        assertNull(RestSafety.check(grid, new Vec3(.5, .5, .5), PLAYER, Rest.Mode.SIT, 0));
        assertNull(RestSafety.check(grid, new Vec3(.5, .5, .5), PLAYER, Rest.Mode.LAY, 0));
        grid.floor(Blocks.STONE.defaultBlockState()); grid.put(0, 1, 0, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
        assertNull(RestSafety.check(grid, new Vec3(.5, 1.0625, .5), PLAYER, Rest.Mode.SIT, 0));
        grid.blocks.clear(); grid.put(0, 0, 0, Blocks.STONE_STAIRS.defaultBlockState());
        // Default north-facing stairs have their high tread in the north half.
        assertNull(RestSafety.check(grid, new Vec3(.5, 1, .25), PLAYER, Rest.Mode.SIT, 0));
    }
    @Test void rejectsUnsupportedFeetCliffsWallsAndLowCeilings() {
        Grid grid = new Grid(); Vec3 feet = new Vec3(.5, 1, .5);
        assertEquals(RestSafety.Problem.SUPPORT, RestSafety.check(grid, feet, PLAYER, Rest.Mode.SIT, 0));
        grid.put(0, 0, 0, Blocks.STONE.defaultBlockState());
        assertNull(RestSafety.check(grid, feet, PLAYER, Rest.Mode.SIT, 0));
        assertEquals(RestSafety.Problem.SUPPORT, RestSafety.check(grid, feet, PLAYER, Rest.Mode.LAY, 0));
        grid.floor(Blocks.STONE.defaultBlockState()); grid.put(0, 2, 0, Blocks.STONE.defaultBlockState());
        assertEquals(RestSafety.Problem.SPACE, RestSafety.check(grid, feet, PLAYER, Rest.Mode.SIT, 0));
        grid.blocks.remove(new BlockPos(0, 2, 0)); grid.put(2, 1, 0, Blocks.STONE.defaultBlockState());
        assertEquals(RestSafety.Problem.SPACE, RestSafety.check(grid, feet, PLAYER, Rest.Mode.LAY, 0));
    }
    @Test void rejectsFluidsAndDamageBlocksButAllowsAnExtinguishedCampfire() {
        for (Block block : List.of(Blocks.LAVA, Blocks.WATER, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CACTUS, Blocks.MAGMA_BLOCK,
                Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE, Blocks.POWDER_SNOW, Blocks.POINTED_DRIPSTONE,
                Blocks.LAVA_CAULDRON, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY))
            assertTrue(RestSafety.hazardous(block.defaultBlockState()), block.toString());
        assertTrue(RestSafety.hazardous(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)));
        assertFalse(RestSafety.hazardous(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)));
        Grid grid = new Grid(); grid.floor(Blocks.MAGMA_BLOCK.defaultBlockState());
        assertEquals(RestSafety.Problem.HAZARD, RestSafety.check(grid, new Vec3(.5, 1, .5), PLAYER, Rest.Mode.SIT, 0));
    }
    @Test void environmentalChangesInvalidateAFormerlySafeSpot() {
        Grid grid = new Grid(); grid.floor(Blocks.STONE.defaultBlockState()); Vec3 feet = new Vec3(.5, 1, .5);
        assertNull(RestSafety.check(grid, feet, PLAYER, Rest.Mode.SIT, 0));
        grid.blocks.remove(new BlockPos(0, 0, 0));
        assertEquals(RestSafety.Problem.SUPPORT, RestSafety.check(grid, feet, PLAYER, Rest.Mode.SIT, 0));
        grid.put(0, 0, 0, Blocks.STONE.defaultBlockState()); grid.put(0, 1, 0, Blocks.FIRE.defaultBlockState());
        assertEquals(RestSafety.Problem.HAZARD, RestSafety.check(grid, feet, PLAYER, Rest.Mode.SIT, 0));
    }
    @Test void everyMovementJumpAndDismountInputExitsButLookingDoesNot() {
        assertFalse(RestSafety.moving(Input.EMPTY));
        for (int index = 0; index < 6; index++) {
            boolean[] keys = new boolean[6]; keys[index] = true;
            assertTrue(RestSafety.moving(new Input(keys[0], keys[1], keys[2], keys[3], keys[4], keys[5], false)));
        }
    }
    static final class Grid implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        void put(int x, int y, int z, BlockState state) { blocks.put(new BlockPos(x, y, z), state); }
        void floor(BlockState state) { blocks.clear(); for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) put(x, 0, z, state); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
    }
}
