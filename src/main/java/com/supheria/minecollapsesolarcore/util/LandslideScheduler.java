package com.supheria.minecollapsesolarcore.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.supheria.minecollapsesolarcore.MineCollapseSolarCore;
import com.supheria.minecollapsesolarcore.api.CollapseUpdateSource;
import com.supheria.minecollapsesolarcore.recipes.LandslideRecipe;

public final class LandslideScheduler {
    private final Level level;
    private final BufferedList<TickEntry> immediateTicks = new BufferedList<>();
    private final Set<BlockPos> queuedPositions = new HashSet<>();
    private final Map<BlockPos, Long> lastQueuedTick = new HashMap<>();
    private final Map<BlockPos, CollapseUpdateSource> directRetryPositions = new LinkedHashMap<>();
    private final DirtyRegionTracker dirtyRegions = new DirtyRegionTracker();
    private long tickCounter;

    public LandslideScheduler(Level level) {
        this.level = level;
    }

    public void scheduleImmediate(BlockPos pos, CollapseUpdateSource source) {
        final BlockPos immutablePos = pos.immutable();
        if (queuedPositions.contains(immutablePos)) {
            return;
        }
        if (queuedPositions.size() >= CollapseBudgetProfile.maxQueuedLandslides(level, source)) {
            markDirty(immutablePos, source);
            return;
        }
        Long lastQueued = lastQueuedTick.get(immutablePos);
        if (lastQueued != null && tickCounter - lastQueued < CollapseBudgetProfile.landslideRequeueCooldownTicks()) {
            return;
        }

        queuedPositions.add(immutablePos);
        lastQueuedTick.put(immutablePos, tickCounter);
        immediateTicks.add(new TickEntry(immutablePos, source, 2));
    }

    public void markDirty(BlockPos pos, CollapseUpdateSource source) {
        dirtyRegions.markSection(pos, source);
    }

    public void scheduleDirectRetry(BlockPos pos, CollapseUpdateSource source) {
        directRetryPositions.putIfAbsent(pos.immutable(), source);
    }

    public CompoundTag serializeNBT() {
        immediateTicks.flush();

        CompoundTag nbt = new CompoundTag();
        ListTag immediateTickNbt = new ListTag();
        for (TickEntry entry : immediateTicks) {
            immediateTickNbt.add(entry.serializeNBT());
        }
        nbt.put("immediateTicks", immediateTickNbt);

        ListTag directRetryNbt = new ListTag();
        for (Map.Entry<BlockPos, CollapseUpdateSource> entry : directRetryPositions.entrySet()) {
            CompoundTag retryNbt = new CompoundTag();
            retryNbt.putLong("pos", entry.getKey().asLong());
            retryNbt.putString("source", entry.getValue().name());
            directRetryNbt.add(retryNbt);
        }
        nbt.put("directRetries", directRetryNbt);
        nbt.put("dirtyRegions", dirtyRegions.serializeNBT());
        nbt.putLong("tickCounter", tickCounter);
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        immediateTicks.clear();
        queuedPositions.clear();
        lastQueuedTick.clear();
        directRetryPositions.clear();
        dirtyRegions.deserializeNBT(nbt.getList("dirtyRegions", Tag.TAG_COMPOUND));
        tickCounter = nbt.getLong("tickCounter");

        ListTag immediateTickNbt = nbt.getList("immediateTicks", Tag.TAG_COMPOUND);
        for (int i = 0; i < immediateTickNbt.size(); i++) {
            TickEntry tickEntry = new TickEntry(immediateTickNbt.getCompound(i));
            BlockPos pos = tickEntry.getPos().immutable();
            if (queuedPositions.add(pos)) {
                lastQueuedTick.put(pos, tickCounter);
                immediateTicks.add(new TickEntry(pos, tickEntry.getSource(), tickEntry.getTicks()));
            }
        }

        ListTag directRetryNbt = nbt.getList("directRetries", Tag.TAG_COMPOUND);
        for (int i = 0; i < directRetryNbt.size(); i++) {
            CompoundTag retryNbt = directRetryNbt.getCompound(i);
            directRetryPositions.put(BlockPos.of(retryNbt.getLong("pos")).immutable(), CollapseUpdateSource.fromName(retryNbt.getString("source")));
        }
    }

    public void tick() {
        tickCounter++;

        int remainingBudget = CollapseBudgetProfile.immediateLandslideBudget(level);
        remainingBudget -= processDirectRetries(remainingBudget);

        immediateTicks.flush();
        Iterator<TickEntry> iterator = immediateTicks.listIterator();
        int processed = 0;
        while (iterator.hasNext() && processed < remainingBudget) {
            TickEntry entry = iterator.next();
            if (!entry.tick()) {
                continue;
            }

            BlockState state = level.getBlockState(entry.getPos());
            LandslideRecipe.tryLandslide(level, entry.getPos(), state, entry.getSource());
            queuedPositions.remove(entry.getPos());
            iterator.remove();
            processed++;
        }

        dirtyRegions.scanDirtySections(level, CollapseBudgetProfile.dirtySectionScanBudget(level), CollapseBudgetProfile.dirtySectionBatchSize(level), (pos, source) -> {
            BlockState state = level.getBlockState(pos);
            if (Helpers.isBlock(state, MineCollapseSolarCore.TAG_CAN_LANDSLIDE)) {
                scheduleImmediate(pos, source);
            }
        });

        if (!lastQueuedTick.isEmpty()) {
            long cleanupBeforeTick = tickCounter - CollapseBudgetProfile.landslideRequeueCooldownTicks();
            lastQueuedTick.entrySet().removeIf(entry -> !queuedPositions.contains(entry.getKey()) && entry.getValue() <= cleanupBeforeTick);
        }
    }

    private int processDirectRetries(int budget) {
        if (directRetryPositions.isEmpty()) {
            return 0;
        }

        int remaining = Math.max(0, budget);
        List<Map.Entry<BlockPos, CollapseUpdateSource>> batch = new ArrayList<>(Math.min(directRetryPositions.size(), remaining));
        Iterator<Map.Entry<BlockPos, CollapseUpdateSource>> iterator = directRetryPositions.entrySet().iterator();
        while (iterator.hasNext() && remaining > 0) {
            Map.Entry<BlockPos, CollapseUpdateSource> entry = iterator.next();
            batch.add(Map.entry(entry.getKey(), entry.getValue()));
            iterator.remove();
            remaining--;
        }

        for (Map.Entry<BlockPos, CollapseUpdateSource> entry : batch) {
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            if (Helpers.isBlock(state, MineCollapseSolarCore.TAG_CAN_LANDSLIDE)) {
                LandslideRecipe.tryLandslide(level, pos, state, entry.getValue());
            }
        }
        return batch.size();
    }
}

