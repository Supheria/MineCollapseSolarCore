package net.zerodind.minecollapse.recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zerodind.minecollapse.Config;
import net.zerodind.minecollapse.MineCollapse;
import net.zerodind.minecollapse.entities.MineCollapseFallingBlockEntity;
import net.zerodind.minecollapse.util.FluidHelpers;
import net.zerodind.minecollapse.util.Helpers;
import net.zerodind.minecollapse.util.IndirectHashCollection;
import net.zerodind.minecollapse.util.Support;

/**
 * This handles all logic for land slides (sideways gravity affected blocks)
 * The recipe only handles landslide *transformations*, not if a block is affected by landslides. That is determined by tag.
 *
 * @see MineCollapseFallingBlockEntity
 */
public class LandslideRecipe extends SimpleBlockRecipe
{
    public static final IndirectHashCollection<Block, LandslideRecipe> CACHE = IndirectHashCollection.createForRecipe(recipe -> recipe.getBlockIngredient().blocks(), MineCollapse.RECIPE_TYPE_LANDSLIDE);

    public static LandslideRecipe getRecipe(BlockState state)
    {
        for (LandslideRecipe recipe : CACHE.getAll(state.getBlock()))
        {
            if (recipe.matches(state))
            {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Tries to cause a landslide from a given block
     *
     * @param state {@code level.getBlockState(pos)}
     * @return true if a landslide actually occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean tryLandslide(Level level, BlockPos pos, BlockState state)
    {
        if (!level.isClientSide() && Config.ENABLE_BLOCK_LANDSLIDES.get())
        {
            final BlockPos fallPos = getLandslidePos(level, pos, state);
            if (fallPos != null)
            {
                final LandslideRecipe recipe = getRecipe(state);
                if (recipe != null)
                {
                    final BlockState fallingState = recipe.getBlockCraftingResult(state);
                    if (!fallPos.equals(pos))
                    {
                        level.removeBlock(pos, false); // Remove the original position, which would be the falling block
                        if (!FluidHelpers.isAirOrEmptyFluid(level.getBlockState(fallPos)))
                        {
                            level.destroyBlock(fallPos, true); // Destroy the block that currently occupies the pos we are going to move sideways into
                        }
                    }
                    level.setBlock(fallPos, fallingState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    level.playSound(null, pos, MineCollapse.SOUND_DIRT_SLIDE_SHORT.get(), SoundSource.BLOCKS, 0.4f, 1.0f);
                    level.addFreshEntity(new MineCollapseFallingBlockEntity(level, fallPos.getX() + 0.5, fallPos.getY(), fallPos.getZ() + 0.5, fallingState, 0.8f, 10));
                }
                return true;
            }
        }
        return false;
    }

    public static BlockPos getLandslidePos(Level level, BlockPos pos, BlockState fallingState)
    {
        if (Support.isSupported(level, pos))
        {
            return null;
        }
        else if (MineCollapseFallingBlockEntity.canFallThrough(level, pos.below(), Direction.DOWN, fallingState))
        {
            return pos;
        }
        else
        {
            // Check if supported by at least two horizontals, or one on top
            if (!isSupportedOnSide(level, pos, Direction.UP))
            {
                int supportedDirections = 0;
                List<BlockPos> possibleDirections = new ArrayList<>();
                for (Direction side : Direction.Plane.HORIZONTAL)
                {
                    if (isSupportedOnSide(level, pos, side))
                    {
                        supportedDirections++;
                        if (supportedDirections >= 2)
                        {
                            // Supported by at least two sides, don't fall
                            return null;
                        }
                    }
                    else
                    {
                        // In order to fall in a direction, we need both the block immediately next to, and the one below to be open
                        // The one adjacent needs to be breakable, wheras the one below just needs to be unstable
                        final BlockPos posSide = pos.relative(side), posSideBelow = posSide.below();
                        if (MineCollapseFallingBlockEntity.canFallThrough(level, posSide, side, fallingState) && MineCollapseFallingBlockEntity.canFallThrough(level, posSideBelow, Direction.DOWN))
                        {
                            possibleDirections.add(posSide);
                        }
                    }
                }

                if (!possibleDirections.isEmpty())
                {
                    return possibleDirections.get(level.getRandom().nextInt(possibleDirections.size()));
                }
            }
        }
        return null;
    }

    public static boolean isSupportedOnSide(BlockGetter world, BlockPos pos, Direction side)
    {
        BlockPos sidePos = pos.relative(side);
        BlockState sideState = world.getBlockState(sidePos);
        return sideState.isFaceSturdy(world, sidePos, side.getOpposite()) || Helpers.isBlock(sideState, MineCollapse.TAG_SUPPORTS_LANDSLIDE);
    }

    public LandslideRecipe(ResourceLocation id, BlockIngredient ingredient, BlockState outputState, boolean copyInputState)
    {
        super(id, ingredient, outputState, copyInputState);
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return MineCollapse.RECIPE_SERIALIZER_LANDSLIDE.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return MineCollapse.RECIPE_TYPE_LANDSLIDE.get();
    }
}
