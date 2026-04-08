package net.zerodind.minecollapse.features;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.zerodind.minecollapse.MineCollapse;
import net.zerodind.minecollapse.entities.MineCollapseFallingBlockEntity;
import net.zerodind.minecollapse.recipes.LandslideRecipe;

public class ErosionFeature extends Feature<NoneFeatureConfiguration>
{
    public ErosionFeature(Codec<NoneFeatureConfiguration> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        final WorldGenLevel level = context.level();
        final BlockPos pos = context.origin();

        final ChunkAccess chunk = level.getChunk(pos);
        final ChunkPos chunkPos = new ChunkPos(pos);
        final int chunkX = chunkPos.getMinBlockX(), chunkZ = chunkPos.getMinBlockZ();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        final int minY = context.chunkGenerator().getMinY();

        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                // Top down iteration, attempt to either fix unstable locations, or remove the offending blocks.
                final int baseHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, chunkX + x, chunkZ + z);
                boolean prevBlockCanLandslide = false;
                Block prevBlockHardened = null;

                mutablePos.set(chunkX + x, baseHeight, chunkZ + z);

                for (int y = baseHeight; y >= minY; y--)
                {
                    mutablePos.setY(y);

                    BlockState stateAt = chunk.getBlockState(mutablePos);
                    LandslideRecipe recipe = stateAt.isAir() ? null : LandslideRecipe.getRecipe(stateAt);
                    boolean stateAtIsFragile = stateAt.isAir() || MineCollapseFallingBlockEntity.canFallThrough(level, mutablePos, stateAt);
                    if (prevBlockCanLandslide)
                    {
                        // Continuing a collapsible downwards
                        // If the block is also collapsible, we just continue until we reach either the bottom (solid) or something to collapse through
                        if (recipe == null)
                        {
                            // This block is sturdy, preventing the column from collapsing
                            // However, we need to make sure we can't collapse *through* this block
                            if (stateAtIsFragile)
                            {
                                mutablePos.setY(y + 1);
                                setBlock(level, chunk, mutablePos, getHardened(chunk.getBlockState(mutablePos).getBlock()));
                            }
                            prevBlockCanLandslide = false;
                        }
                    }
                    else
                    {
                        // Last block is sturdy
                        if (recipe != null)
                        {
                            // This block can collapse. lastSafeY will already be y + 1, so all we need to mark is the prev flag for next iteration
                            prevBlockCanLandslide = true;
                        }
                    }

                    // Update stone from raw -> hardened
                    if (stateAtIsFragile)
                    {
                        if (prevBlockHardened != null)
                        {
                            mutablePos.setY(y + 1);
                            setBlock(level, chunk, mutablePos, prevBlockHardened);
                        }
                        prevBlockHardened = null;
                    }
                    else
                    {
                        prevBlockHardened = getHardened(stateAt.getBlock());
                    }
                }
            }
        }
        return true;
    }

    private Block getHardened(Block rock) {
        if (rock == Blocks.STONE) {
            return MineCollapse.BLOCK_HARDENED_STONE.get();
        } else if (rock == Blocks.GRANITE) {
            return MineCollapse.BLOCK_HARDENED_GRANITE.get();
        } else if (rock == Blocks.DIORITE) {
            return MineCollapse.BLOCK_HARDENED_DIORITE.get();
        } else if (rock == Blocks.ANDESITE) {
            return MineCollapse.BLOCK_HARDENED_ANDESITE.get();
        } else if (rock == Blocks.TUFF) {
            return MineCollapse.BLOCK_HARDENED_TUFF.get();
        } else if (rock == Blocks.DEEPSLATE) {
            return MineCollapse.BLOCK_HARDENED_DEEPSLATE.get();
        }
        return null;
    }

    /**
     * Faster than using {@link net.minecraft.server.level.WorldGenRegion#setBlock(BlockPos, BlockState, int)} or variants. Optimized as we're not setting any block entities or need to re-query the chunk and check in-range.
     * Worthwhile as erosion feature is responsible for a sizazble chunk of all feature generation time.
     */
    private void setBlock(WorldGenLevel level, ChunkAccess chunk, BlockPos pos, Block block)
    {
        if (block == null) {
            return;
        }

        final BlockState state = block.defaultBlockState();
        final BlockState prevState = chunk.setBlockState(pos, state, false);
        if (prevState != null && prevState.hasBlockEntity())
        {
            chunk.removeBlockEntity(pos);
        }
        if (state.hasPostProcess(level, pos))
        {
            chunk.markPosForPostprocessing(pos);
        }
    }
}
