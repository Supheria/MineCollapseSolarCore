package net.zerodind.minecollapse.recipes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * This is a version of {@link net.minecraftforge.items.wrapper.RecipeWrapper} that is intended to be used for {@link IBlockRecipe}.
 * It extends {@link ItemStackInventory} for ease of use, and so the block can be visible (via the proxy stack)
 */
public class BlockInventory implements EmptyInventory
{
    protected final BlockPos pos;
    protected BlockState state;

    public BlockInventory(BlockPos pos, BlockState state)
    {
        this.pos = pos;
        this.state = state;
    }

    public BlockPos getPos()
    {
        return pos;
    }

    public BlockState getState()
    {
        return state;
    }
}
