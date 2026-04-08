package com.supheria.minecollapsesolarcore.api;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class CollapseSchedulingAccess {
    private static final ThreadLocal<Deque<String>> ACTIVE_SOURCES = ThreadLocal.withInitial(ArrayDeque::new);
    private static CollapseSchedulingApi api;

    public static void setApi(CollapseSchedulingApi collapseSchedulingApi) {
        api = collapseSchedulingApi;
    }

    public static CollapseSchedulingApi getApi() {
        return api;
    }

    public static void pushActiveSource(String sourceName) {
        ACTIVE_SOURCES.get().push(sourceName);
    }

    public static void popActiveSource() {
        Deque<String> sources = ACTIVE_SOURCES.get();
        if (!sources.isEmpty()) {
            sources.pop();
        }
        if (sources.isEmpty()) {
            ACTIVE_SOURCES.remove();
        }
    }

    public static String getActiveSourceName() {
        Deque<String> sources = ACTIVE_SOURCES.get();
        return sources.isEmpty() ? null : sources.peek();
    }

    public static void scheduleImmediateLandslide(Level level, BlockPos pos, String sourceName) {
        if (api != null) {
            api.scheduleImmediateLandslide(level, pos, CollapseUpdateSource.fromName(sourceName));
        }
    }

    public static void markLandslideRegionDirty(Level level, BlockPos pos, String sourceName) {
        if (api != null) {
            api.markLandslideRegionDirty(level, pos, CollapseUpdateSource.fromName(sourceName));
        }
    }

    public static void scheduleImmediateCollapseCheck(Level level, BlockPos pos, String sourceName) {
        if (api != null) {
            api.scheduleImmediateCollapseCheck(level, pos, CollapseUpdateSource.fromName(sourceName));
        }
    }

    private CollapseSchedulingAccess() {}
}

