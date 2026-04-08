package net.zerodind.minecollapsesolarcore.entities;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.zerodind.minecollapsesolarcore.MineCollapseSolarCore;
import net.zerodind.minecollapsesolarcore.api.CollapseSchedulingAccess;
import net.zerodind.minecollapsesolarcore.api.CollapseUpdateSource;
import net.zerodind.minecollapsesolarcore.util.FluidHelpers;
import net.zerodind.minecollapsesolarcore.util.Helpers;
import net.zerodind.minecollapsesolarcore.util.WorldTracker;

/**
 * A falling block entity that has a bit more oomph - it destroys blocks underneath it rather than hovering or popping off.
 */
public class MineCollapseSolarCoreFallingBlockEntity extends FallingBlockEntity
{
    private static Field BLOCK_STATE_FIELD = null;

    public static boolean canFallThrough(BlockGetter world, BlockPos pos, BlockState state)
    {
        return !state.isFaceSturdy(world, pos, Direction.UP);
    }

    /**
     * Can the existing block at {@code pos} fall through the block in the direction {@code fallingDirection}.
     *
     * @param level            The world
     * @param pos              The position of the existing block that might fall
     * @param fallingDirection The direction that the existing block might fall in
     * @return {@code true} if the block at {@code pos} can fall through the block in the direction {@code fallingDirection}.
     */
    public static boolean canFallInDirection(BlockGetter level, BlockPos pos, Direction fallingDirection)
    {
        final BlockPos fallThroughPos = pos.relative(fallingDirection);
        return canFallThrough(level, fallThroughPos, level.getBlockState(fallThroughPos), fallingDirection, level.getBlockState(pos));
    }

    public static boolean canFallThrough(BlockGetter level, BlockPos pos, Direction fallingDirection)
    {
        final BlockState state = level.getBlockState(pos);
        return canFallThrough(level, pos, state, fallingDirection, state);
    }

    public static boolean canFallThrough(BlockGetter level, BlockPos pos, Direction fallingDirection, BlockState fallingState)
    {
        return canFallThrough(level, pos, level.getBlockState(pos), fallingDirection, fallingState);
    }

    /**
     * Can the falling block fall through (effectively destroying) a specific block
     *
     * @param level            The world
     * @param pos              The position of the block in world that we want to fall through
     * @param state            {@code level.getBlockState(pos)}
     * @param fallingDirection The direction of the fall. For most falls this will be {@link Direction#DOWN}, however for landslides, this may be a horizontal direction, indicating we want to move into the block from the side.
     * @param fallingState     The state of the falling block. This is used in order to calculate toughness, if the falling block can break the existing block.
     * @return {@code true} if the falling block can fall through the existing block.
     */
    public static boolean canFallThrough(BlockGetter level, BlockPos pos, BlockState state, Direction fallingDirection, BlockState fallingState)
    {
        return !state.isFaceSturdy(level, pos, fallingDirection.getOpposite()) // Must be non-sturdy in the direction opposed to the fall
            && getBlockToughness(fallingState) >= getBlockToughness(state) // Must be of an equal or greater toughness
            && state.getDestroySpeed(level, pos) > -1f && !(state.getBlock() == Blocks.STRUCTURE_VOID); // Don't break end portal frames or structure voids
    }

    public static int getBlockToughness(BlockState state)
    {
        if (state.getBlock() == Blocks.BEDROCK)
        {
            return 4; // Fake value, useful for simulating really hard toughness checks.
        }
        if (Helpers.isBlock(state, MineCollapseSolarCore.TAG_TOUGHNESS_3))
        {
            return 3;
        }
        if (Helpers.isBlock(state, MineCollapseSolarCore.TAG_TOUGHNESS_2))
        {
            return 2;
        }
        if (Helpers.isBlock(state, MineCollapseSolarCore.TAG_TOUGHNESS_1))
        {
            return 1;
        }
        return 0;
    }

    private final boolean dontSetBlock;
    private boolean failedBreakCheck;
    private boolean expectBlockPresentOnFirstTick;

    public MineCollapseSolarCoreFallingBlockEntity(EntityType<? extends FallingBlockEntity> entityType, Level level)
    {
        super(entityType, level);

        failedBreakCheck = false;
        dontSetBlock = false;
        expectBlockPresentOnFirstTick = true;
    }

    public MineCollapseSolarCoreFallingBlockEntity(Level level, double x, double y, double z, BlockState fallingBlockState, float damagePerBlockFallen, int maximumFallDamage)
    {
        this(level, x, y, z, fallingBlockState);
        setHurtsEntities(damagePerBlockFallen, maximumFallDamage);
    }

    public MineCollapseSolarCoreFallingBlockEntity(Level level, double x, double y, double z, BlockState fallingBlockState)
    {
        this(MineCollapseSolarCore.ENTITY_FALLING_BLOCK.get(), level);
        setFallingBlockState(fallingBlockState);
        blocksBuilding = true;
        setPos(x, y, z);
        setDeltaMovement(Vec3.ZERO);
        xo = x;
        yo = y;
        zo = z;
        setStartPos(blockPosition());
    }

    private void setFallingBlockState(BlockState state)
    {
        try
        {
            if (BLOCK_STATE_FIELD == null)
            {
                for (Field field : FallingBlockEntity.class.getDeclaredFields())
                {
                    if (field.getType() == BlockState.class)
                    {
                        field.setAccessible(true);
                        BLOCK_STATE_FIELD = field;
                        break;
                    }
                }
            }

            if (BLOCK_STATE_FIELD == null)
            {
                throw new IllegalStateException("Unable to locate FallingBlockEntity block state field");
            }

            BLOCK_STATE_FIELD.set(this, state);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Unable to set falling block state", e);
        }
    }

    public MineCollapseSolarCoreFallingBlockEntity setExpectBlockPresentOnFirstTick(boolean expectBlockPresentOnFirstTick)
    {
        this.expectBlockPresentOnFirstTick = expectBlockPresentOnFirstTick;
        return this;
    }

    @Override
    public void tick()
    {
        final BlockState fallingBlockState = getBlockState();
        if (fallingBlockState.isAir())
        {
            remove(RemovalReason.DISCARDED);
        }
        else
        {
            final Block block = fallingBlockState.getBlock();
            if (time++ == 0)
            {
                if (expectBlockPresentOnFirstTick)
                {
                    // First tick, replace the existing block
                    BlockPos blockpos = blockPosition();
                    if (block == level().getBlockState(blockpos).getBlock())
                    {
                        level().removeBlock(blockpos, false);
                    }
                    else if (!level().isClientSide)
                    {
                        remove(RemovalReason.DISCARDED);
                    }
                }
                // If we spawn two falling block entities on the same tick, in adjacent positions, and the one above ticks first, it can cause a situation where the block below gets deleted by the falling block destruction code.
                // This causes the next block entity to disappear. So, we don't do anything on first tick except capture and replace the block.
                return;
            }

            if (!isNoGravity())
            {
                setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            move(MoverType.SELF, getDeltaMovement());

            if (!level().isClientSide)
            {
                BlockPos posAt = blockPosition();
                if (!onGround())
                {
                    failedBreakCheck = false;
                    if ((time > 100 && (posAt.getY() < 1 || posAt.getY() > 256)) || time > 600)
                    {
                        attemptToDropAsItem(fallingBlockState);
                        remove(RemovalReason.DISCARDED);
                    }
                }
                else
                {
                    // On ground
                    if (!failedBreakCheck)
                    {
                        if (!FluidHelpers.isAirOrEmptyFluid(level().getBlockState(posAt)) && canFallThrough(level(), posAt, Direction.DOWN, fallingBlockState))
                        {
                            // Breaking through blocks without drops avoids a large temporary item-entity spike during big collapses.
                            level().destroyBlock(posAt, false);
                            failedBreakCheck = true;
                            return;
                        }
                        else if (!FluidHelpers.isAirOrEmptyFluid(level().getBlockState(posAt.below())) && canFallThrough(level(), posAt.below(), Direction.DOWN, fallingBlockState))
                        {
                            // Breaking through blocks without drops avoids a large temporary item-entity spike during big collapses.
                            level().destroyBlock(posAt.below(), false);
                            failedBreakCheck = true;
                            return;
                        }
                    }

                    BlockState hitBlockState = level().getBlockState(posAt);
                    setDeltaMovement(getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));

                    if (hitBlockState.getBlock() != Blocks.MOVING_PISTON)
                    {
                        remove(RemovalReason.DISCARDED);
                        if (!dontSetBlock)
                        {
                            // Attempt to set the block, first by replacing the target block.
                            if (canPlaceAt(hitBlockState, posAt, fallingBlockState, fallingBlockState))
                            {
                                placeAsBlockOrDropAsItem(hitBlockState, posAt, fallingBlockState);
                            }
                            else
                            {
                                // Second check: try and place the block one block above from it's current position
                                // This is to handle blocks such as soul sand or mud, which it will attempt to fall into (because it has a < a block collision shape), but then needs to pretend to place above the block (since they support falling blocks).
                                // Note that the second time we do this, we have to use bedrock as the toughness check - because we only want to place if we can't fall, and can't fall includes checks against toughness - not just against sturdy ground.
                                final BlockPos posAbove = posAt.above();
                                final BlockState hitAboveBlockState = level().getBlockState(posAbove);
                                if (canPlaceAt(hitAboveBlockState, posAbove, fallingBlockState, Blocks.BEDROCK.defaultBlockState()))
                                {
                                    placeAsBlockOrDropAsItem(hitAboveBlockState, posAbove, fallingBlockState);
                                }
                                else
                                {
                                    // Cannot find a location where can survive, and breaking checks have failed.
                                    attemptToDropAsItem(fallingBlockState);
                                }
                            }
                        }
                    }
                }
            }
            setDeltaMovement(getDeltaMovement().scale(0.98D));
        }
    }

    private boolean canPlaceAt(BlockState hitBlockState, BlockPos posAt, BlockState fallingBlockState, BlockState toughnessBlockState)
    {
        final BlockPos below = posAt.below();
        return hitBlockState.canBeReplaced(new DirectionalPlaceContext(this.level(), posAt, Direction.DOWN, ItemStack.EMPTY, Direction.UP))
            && fallingBlockState.canSurvive(this.level(), posAt)
            && !canFallThrough(this.level(), below, Direction.DOWN, toughnessBlockState);
    }

    private void placeAsBlockOrDropAsItem(BlockState hitBlockState, BlockPos posAt, BlockState fallingBlockState)
    {
        boolean placed;
        CollapseSchedulingAccess.pushActiveSource(CollapseUpdateSource.FALLING_BLOCK_SETTLE.name());
        try
        {
            placed = level().setBlockAndUpdate(posAt, fallingBlockState);
        }
        finally
        {
            CollapseSchedulingAccess.popActiveSource();
        }

        if (placed)
        {
            afterPlacementAsBlock(hitBlockState, posAt, fallingBlockState);
        }
        else
        {
            attemptToDropAsItem(fallingBlockState);
        }
    }

    private void afterPlacementAsBlock(BlockState hitBlockState, BlockPos posAt, BlockState fallingBlockState)
    {
        if (fallingBlockState.getBlock() instanceof FallingBlock fallingBlock)
        {
            fallingBlock.onLand(this.level(), posAt, fallingBlockState, hitBlockState, this);
        }

        if (Helpers.isBlock(fallingBlockState.getBlock(), MineCollapseSolarCore.TAG_CAN_LANDSLIDE))
        {
            // Re-scan the settled area without immediately re-launching the same block,
            // which otherwise creates visible repeated "ghost" falls after player mining.
            WorldTracker.get(level()).markLandslideRegionDirty(posAt, CollapseUpdateSource.FALLING_BLOCK_SETTLE);
        }

        // Sets the tile entity if it exists
        if (blockData != null && fallingBlockState.hasBlockEntity())
        {
            BlockEntity tileEntity = level().getBlockEntity(posAt);
            if (tileEntity != null)
            {
                CompoundTag tileEntityData = tileEntity.saveWithoutMetadata();
                for (String dataKey : tileEntityData.getAllKeys())
                {
                    Tag dataElement = tileEntityData.get(dataKey);
                    if (!"x".equals(dataKey) && !"y".equals(dataKey) && !"z".equals(dataKey) && dataElement != null)
                    {
                        tileEntityData.put(dataKey, dataElement.copy());
                    }
                }
                tileEntity.load(tileEntityData);
                tileEntity.setChanged();
            }
        }
    }

    private void attemptToDropAsItem(BlockState fallingBlockState)
    {
        if (dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS) && level() instanceof ServerLevel server)
        {
            Helpers.dropWithContext(server, fallingBlockState, blockPosition(), p -> {}, true);
        }
    }
}
