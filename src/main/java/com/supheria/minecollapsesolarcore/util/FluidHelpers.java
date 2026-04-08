package com.supheria.minecollapsesolarcore.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

public class FluidHelpers
{
    public static final int BUCKET_VOLUME = 1000;

    /**
     * Checks if a block state is empty other than a provided fluid
     *
     * @return true if the provided state is a source block of its current fluid
     */
    public static boolean isAirOrEmptyFluid(BlockState state)
    {
        return state.isAir() || state.getBlock() == state.getFluidState().getType().defaultFluidState().createLegacyBlock().getBlock();
    }

    /**
     * Intended to be called from {@link Block#updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos)} by blocks which support a fluid state.
     * This is responsible for causing fluid-logged blocks to spread fluid when they are updated.
     * <p>
     * Example implementation in vanilla is seen in {@link net.minecraft.world.level.block.SlabBlock#updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos)}
     *
     * @param tickWhenEmpty If when the fluid state is empty, this should still schedule a block tick. This is for blocks that want to be removed, generally, when fluid is removed.
     */
    public static void tickFluid(LevelAccessor level, BlockPos pos, BlockState state, boolean tickWhenEmpty)
    {
        if (!state.getFluidState().isEmpty())
        {
            final Fluid fluid = state.getFluidState().getType();
            level.scheduleTick(pos, fluid, fluid.getTickDelay(level));
        }
        else if (tickWhenEmpty)
        {
            level.scheduleTick(pos, state.getBlock(), 1);
        }
    }

    public static void tickFluid(LevelAccessor level, BlockPos pos, BlockState state)
    {
        tickFluid(level, pos, state, false);
    }
}

