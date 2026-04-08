package net.zerodind.minecollapsesolarcore.recipes;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A simple {@link net.minecraft.world.item.crafting.Recipe} extension for {@link BlockInventory}
 */
public interface IBlockRecipe extends ISimpleRecipe<BlockInventory>
{
    @Override
    default boolean matches(BlockInventory inv, Level level)
    {
        return matches(inv.getState());
    }

    @Override
    default ItemStack getResultItem(RegistryAccess registryAccess)
    {
        return new ItemStack(getBlockRecipeOutput());
    }

    @Override
    default ItemStack assemble(BlockInventory inventory, RegistryAccess registryAccess)
    {
        return new ItemStack(getBlockCraftingResult(inventory).getBlock());
    }

    /**
     * Specific parameter version of {@link net.minecraft.world.item.crafting.Recipe#matches(Container, Level)} for block recipes
     */
    default boolean matches(BlockState state)
    {
        return false;
    }

    /**
     * Specific parameter version of {@link Recipe#assemble(Container, RegistryAccess)} for block recipes.
     */
    default BlockState getBlockCraftingResult(BlockInventory inventory)
    {
        return getBlockCraftingResult(inventory.getState());
    }

    default BlockState getBlockCraftingResult(BlockState state)
    {
        return getBlockRecipeOutput().defaultBlockState();
    }

    /**
     * Specific parameter version of {@link Recipe#getResultItem(RegistryAccess)} for block recipes.
     */
    default Block getBlockRecipeOutput()
    {
        return Blocks.AIR;
    }
}
