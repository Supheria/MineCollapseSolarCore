package com.supheria.minecollapsesolarcore.recipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.supheria.minecollapsesolarcore.Config;
import com.supheria.minecollapsesolarcore.MineCollapseSolarCore;
import com.supheria.minecollapsesolarcore.api.CollapseUpdateSource;
import com.supheria.minecollapsesolarcore.api.CollapseSchedulingAccess;
import com.supheria.minecollapsesolarcore.entities.MineCollapseSolarCoreFallingBlockEntity;
import com.supheria.minecollapsesolarcore.util.FluidHelpers;
import com.supheria.minecollapsesolarcore.util.IndirectHashCollection;
import com.supheria.minecollapsesolarcore.util.Support;
import com.supheria.minecollapsesolarcore.util.WorldTracker;

/**
 * This handles gravity-triggered movement for unstable side-fall blocks.
 *
 * <p>Solar fork behavior keeps only vertical falling. Sideways landslides are intentionally disabled.</p>
 *
 * @see MineCollapseSolarCoreFallingBlockEntity
 */
public class LandslideRecipe extends SimpleBlockRecipe
{
    private static final double PLAYER_CHAIN_RANGE = 24.0D;
    private static final double PLAYER_CHAIN_RANGE_SQR = PLAYER_CHAIN_RANGE * PLAYER_CHAIN_RANGE;

    public static final IndirectHashCollection<Block, LandslideRecipe> CACHE = IndirectHashCollection.createForRecipe(recipe -> recipe.getBlockIngredient().blocks(), MineCollapseSolarCore.RECIPE_TYPE_LANDSLIDE);

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
    public static boolean tryLandslide(Level level, BlockPos pos, BlockState state, CollapseUpdateSource source)
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
                    level.removeBlock(pos, false);
                    level.playSound(null, pos, MineCollapseSolarCore.SOUND_DIRT_SLIDE_SHORT.get(), SoundSource.BLOCKS, 0.4f, 1.0f);
                    level.addFreshEntity(new MineCollapseSolarCoreFallingBlockEntity(level, fallPos.getX() + 0.5, fallPos.getY(), fallPos.getZ() + 0.5, fallingState, 0.8f, 10)
                            .setExpectBlockPresentOnFirstTick(false));
                    if (source == CollapseUpdateSource.PLAYER_ACTION && isWithinPlayerChainRange(level, pos)) {
                        WorldTracker.get(level).scheduleDirectLandslideRetry(pos.above(), source);
                    }
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
        else if (MineCollapseSolarCoreFallingBlockEntity.canFallThrough(level, pos.below(), Direction.DOWN, fallingState))
        {
            return pos;
        }
        return null;
    }

    private static boolean isWithinPlayerChainRange(Level level, BlockPos pos)
    {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        for (Player player : level.players())
        {
            double dx = player.getX() - centerX;
            double dy = player.getY() - (pos.getY() + 0.5D);
            double dz = player.getZ() - centerZ;
            if (dx * dx + dy * dy + dz * dz <= PLAYER_CHAIN_RANGE_SQR)
            {
                return true;
            }
        }
        return false;
    }

    public LandslideRecipe(ResourceLocation id, BlockIngredient ingredient, BlockState outputState, boolean copyInputState)
    {
        super(id, ingredient, outputState, copyInputState);
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return MineCollapseSolarCore.RECIPE_SERIALIZER_LANDSLIDE.get();
    }

    @Override
    public RecipeType<?> getType()
    {
        return MineCollapseSolarCore.RECIPE_TYPE_LANDSLIDE.get();
    }
}

