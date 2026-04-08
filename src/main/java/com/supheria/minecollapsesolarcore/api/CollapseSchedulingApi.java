package com.supheria.minecollapsesolarcore.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface CollapseSchedulingApi {
    void scheduleImmediateLandslide(Level level, BlockPos pos, CollapseUpdateSource source);

    void markLandslideRegionDirty(Level level, BlockPos pos, CollapseUpdateSource source);

    void scheduleImmediateCollapseCheck(Level level, BlockPos pos, CollapseUpdateSource source);
}

