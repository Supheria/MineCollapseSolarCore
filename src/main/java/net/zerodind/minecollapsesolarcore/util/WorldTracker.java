package net.zerodind.minecollapsesolarcore.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.zerodind.minecollapsesolarcore.Config;
import net.zerodind.minecollapsesolarcore.MineCollapseSolarCore;
import net.zerodind.minecollapsesolarcore.entities.MineCollapseSolarCoreFallingBlockEntity;
import net.zerodind.minecollapsesolarcore.recipes.CollapseRecipe;
import net.zerodind.minecollapsesolarcore.recipes.LandslideRecipe;

public class WorldTracker implements ICapabilitySerializable<CompoundTag>
{
    private static final int MAX_COLLAPSE_POSITIONS_PER_TICK = 96;
    private static final int MAX_LANDSLIDES_PER_TICK = 64;

    /**
     * Returns the world tracker for a given world. Every <strong>real</strong> world must have a world tracker attached, i.e.
     * worlds created by vanilla. Mods may do weird things (see <a href="https://github.com/TerraFirmaCraft/TerraFirmaCraft/issues/2842">
     * TerraFirmaCraft#2842</a>) that create worlds without trackers, so in those cases we return an empty no-op'd instance.
     * <p>
     * Note that while a tracker exists for both server and client, certain data may only be valid on server.
     */
    @SuppressWarnings("deprecation")
    public static WorldTracker get(Level level)
    {
        return level.getCapability(WorldTrackerCapability.CAPABILITY).orElse(NoopInstance.INSTANCE);
    }

    private final Level level;
    private final Random random;
    private final LazyOptional<WorldTracker> capability;

    private final BufferedList<TickEntry> landslideTicks;
    private final List<Collapse> collapsesInProgress;

    public WorldTracker(Level level)
    {
        this.level = level;
        this.random = new Random();
        this.capability = LazyOptional.of(() -> this);
        this.landslideTicks = new BufferedList<>();
        this.collapsesInProgress = new ArrayList<>();
    }

    public void addLandslidePos(BlockPos pos)
    {
        landslideTicks.add(new TickEntry(pos, 2));
    }

    public void addCollapseData(Collapse collapse)
    {
        collapsesInProgress.add(collapse);
    }

    public void addCollapsePositions(BlockPos centerPos, Collection<BlockPos> positions)
    {
        List<BlockPos> collapsePositions = new ArrayList<>();
        double maxRadiusSquared = 0;
        for (BlockPos pos : positions)
        {
            double distSquared = pos.distSqr(centerPos);
            if (distSquared > maxRadiusSquared)
            {
                maxRadiusSquared = distSquared;
            }
            if (random.nextFloat() < Config.COLLAPSE_EXPLOSION_PROPAGATE_CHANCE.get())
            {
                collapsePositions.add(pos.above()); // Check the above position
            }
        }
        addCollapseData(new Collapse(centerPos, collapsePositions, maxRadiusSquared));
    }

    /**
     * Must only be called from logical server!
     */
    public void tick()
    {
        int remainingCollapseBudget = MAX_COLLAPSE_POSITIONS_PER_TICK;
        if (!collapsesInProgress.isEmpty() && random.nextInt(10) == 0)
        {
            for (Collapse collapse : collapsesInProgress)
            {
                if (remainingCollapseBudget <= 0)
                {
                    break;
                }

                Set<BlockPos> updatedPositions = new HashSet<>();
                List<BlockPos> remainingPositions = new ArrayList<>();
                int processed = 0;
                for (BlockPos posAt : collapse.nextPositions)
                {
                    if (processed >= remainingCollapseBudget)
                    {
                        remainingPositions.add(posAt);
                        continue;
                    }

                    processed++;
                    // Check the current position for collapsing
                    final BlockState stateAt = level.getBlockState(posAt);
                    if (Helpers.isBlock(stateAt, MineCollapseSolarCore.TAG_CAN_COLLAPSE) && MineCollapseSolarCoreFallingBlockEntity.canFallInDirection(level, posAt, Direction.DOWN) && posAt.distSqr(collapse.centerPos) < collapse.radiusSquared && random.nextFloat() < Config.COLLAPSE_PROPAGATE_CHANCE.get())
                    {
                        if (CollapseRecipe.collapseBlock(level, posAt, stateAt))
                        {
                            // This column has started to collapse. Mark the next block above as unstable for the "follow up"
                            updatedPositions.add(posAt.above());
                        }
                    }
                }
                remainingCollapseBudget -= processed;
                collapse.nextPositions.clear();
                collapse.nextPositions.addAll(remainingPositions);
                if (!updatedPositions.isEmpty())
                {
                    level.playSound(null, collapse.centerPos, MineCollapseSolarCore.SOUND_ROCK_SLIDE_SHORT.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
                    collapse.nextPositions.addAll(updatedPositions);
                    collapse.radiusSquared *= 0.8; // lower radius each successive time
                }
            }
            collapsesInProgress.removeIf(collapse -> collapse.nextPositions.isEmpty());
        }

        landslideTicks.flush();
        Iterator<TickEntry> tickIterator = landslideTicks.listIterator();
        int processedLandslides = 0;
        while (tickIterator.hasNext())
        {
            if (processedLandslides >= MAX_LANDSLIDES_PER_TICK)
            {
                break;
            }

            TickEntry entry = tickIterator.next();
            if (entry.tick())
            {
                final BlockState currentState = level.getBlockState(entry.getPos());
                LandslideRecipe.tryLandslide(level, entry.getPos(), currentState);
                tickIterator.remove();
                processedLandslides++;
            }
        }
    }

    @Override
    public CompoundTag serializeNBT()
    {
        landslideTicks.flush();

        CompoundTag nbt = new CompoundTag();
        ListTag landslideNbt = new ListTag();
        for (TickEntry entry : landslideTicks)
        {
            landslideNbt.add(entry.serializeNBT());
        }
        nbt.put("landslideTicks", landslideNbt);

        ListTag collapseNbt = new ListTag();
        for (Collapse collapse : collapsesInProgress)
        {
            collapseNbt.add(collapse.serializeNBT());
        }
        nbt.put("collapsesInProgress", collapseNbt);

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        if (nbt != null)
        {
            landslideTicks.clear();
            collapsesInProgress.clear();

            ListTag landslideNbt = nbt.getList("landslideTicks", Tag.TAG_COMPOUND);
            for (int i = 0; i < landslideNbt.size(); i++)
            {
                landslideTicks.add(new TickEntry(landslideNbt.getCompound(i)));
            }

            ListTag collapseNbt = nbt.getList("collapsesInProgress", Tag.TAG_COMPOUND);
            for (int i = 0; i < collapseNbt.size(); i++)
            {
                collapsesInProgress.add(new Collapse(collapseNbt.getCompound(i)));
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
    {
        return WorldTrackerCapability.CAPABILITY.orEmpty(cap, capability);
    }

    static final class NoopInstance extends WorldTracker
    {
        public static final NoopInstance INSTANCE = new NoopInstance();

        @SuppressWarnings("DataFlowIssue")
        public NoopInstance()
        {
            super(null);
        }

        @Override public void addLandslidePos(BlockPos pos) {}
        @Override public void addCollapseData(Collapse collapse) {}
        @Override public void addCollapsePositions(BlockPos centerPos, Collection<BlockPos> positions) {}
    }
}
