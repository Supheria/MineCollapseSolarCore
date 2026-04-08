package com.supheria.minecollapsesolarcore.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.common.extensions.IForgeBlock;

/**
 * This implements some of the more annoying methods in {@link IForgeBlock} which would otherwise require implementing across all manner of vanilla subclasses.
 */
public interface IForgeBlockExtension extends IForgeBlock
{
    ExtendedProperties getExtendedProperties();

    @Override
    default int getFlammability(BlockState state, BlockGetter world, BlockPos pos, Direction face)
    {
        return getExtendedProperties().getFlammability();
    }

    @Override
    default boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return state.getFlammability(level, pos, direction) > 0;
    }

    @Override
    default int getFireSpreadSpeed(BlockState state, BlockGetter world, BlockPos pos, Direction face)
    {
        return getExtendedProperties().getFireSpreadSpeed();
    }

    @Override
    default BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob entity)
    {
        final BlockPathTypes type = getExtendedProperties().getPathType();
        return type != null ? type : IForgeBlock.super.getBlockPathType(state, level, pos, entity);
    }

    @Override
    default float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos)
    {
        return getExtendedProperties().getEnchantmentPower(state);
    }
}

