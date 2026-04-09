package com.supheria.minecollapsesolarcore.util;

import net.minecraft.world.level.Level;
import com.supheria.minecollapsesolarcore.api.CollapseUpdateSource;

public final class CollapseBudgetProfile {
    private static final int MAX_COLLAPSE_POSITIONS_PER_TICK = 48;
    private static final int MAX_QUEUED_LANDSLIDES = 384;
    private static final int IMMEDIATE_LANDSLIDE_BUDGET = 24;
    private static final int DIRTY_SECTION_SCAN_BUDGET = 48;
    private static final int DIRTY_SECTION_BATCH_SIZE = 16;
    private static final long LANDSLIDE_REQUEUE_COOLDOWN_TICKS = 8;

    public static int collapseBudget(Level level) {
        return MAX_COLLAPSE_POSITIONS_PER_TICK;
    }

    public static int immediateLandslideBudget(Level level) {
        return IMMEDIATE_LANDSLIDE_BUDGET;
    }

    public static int dirtySectionScanBudget(Level level) {
        return DIRTY_SECTION_SCAN_BUDGET;
    }

    public static int dirtySectionBatchSize(Level level) {
        return DIRTY_SECTION_BATCH_SIZE;
    }

    public static int maxQueuedLandslides(Level level, CollapseUpdateSource source) {
        return source != null && source.isSolarDriven() ? MAX_QUEUED_LANDSLIDES / 4 : MAX_QUEUED_LANDSLIDES;
    }

    public static long landslideRequeueCooldownTicks() {
        return LANDSLIDE_REQUEUE_COOLDOWN_TICKS;
    }

    private CollapseBudgetProfile() {}
}

