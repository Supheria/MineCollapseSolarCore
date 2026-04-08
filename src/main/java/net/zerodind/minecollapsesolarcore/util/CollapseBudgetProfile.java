package net.zerodind.minecollapsesolarcore.util;

import net.minecraft.world.level.Level;
import net.zerodind.minecollapsesolarcore.api.CollapseUpdateSource;

public final class CollapseBudgetProfile {
    private static final int MAX_COLLAPSE_POSITIONS_PER_TICK = 96;
    private static final int MAX_QUEUED_LANDSLIDES = 1024;
    private static final int IMMEDIATE_LANDSLIDE_BUDGET = 64;
    private static final int DIRTY_SECTION_SCAN_BUDGET = 192;
    private static final int DIRTY_SECTION_BATCH_SIZE = 48;
    private static final long LANDSLIDE_REQUEUE_COOLDOWN_TICKS = 4;

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
        return source != null && source.isSolarDriven() ? MAX_QUEUED_LANDSLIDES / 2 : MAX_QUEUED_LANDSLIDES;
    }

    public static long landslideRequeueCooldownTicks() {
        return LANDSLIDE_REQUEUE_COOLDOWN_TICKS;
    }

    private CollapseBudgetProfile() {}
}
