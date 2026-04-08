package net.zerodind.minecollapse.blocks;

import java.util.Map;

import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public interface HorizontalPipeBlock
{
    Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION.entrySet().stream()
        .filter(facing -> facing.getKey().getAxis().isHorizontal()).collect(Util.toMap());

    BooleanProperty NORTH = PipeBlock.NORTH;
    BooleanProperty EAST = PipeBlock.EAST;
    BooleanProperty SOUTH = PipeBlock.SOUTH;
    BooleanProperty WEST = PipeBlock.WEST;

}
