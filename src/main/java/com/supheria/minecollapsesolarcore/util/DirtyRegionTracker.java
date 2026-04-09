package com.supheria.minecollapsesolarcore.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import com.supheria.minecollapsesolarcore.api.CollapseUpdateSource;

public final class DirtyRegionTracker {
    private static final int MAX_DIRTY_COLUMNS = 768;

    private final Map<Long, DirtyColumn> dirtyColumns = new LinkedHashMap<>();

    public void markSection(BlockPos pos, CollapseUpdateSource source) {
        long columnKey = columnKey(pos.getX(), pos.getZ());
        dirtyColumns.compute(columnKey, (ignored, current) -> {
            if (current == null) {
                return new DirtyColumn(pos.getX(), pos.getZ(), source, pos.getY());
            }
            current.mergeSource(source);
            current.addY(pos.getY());
            return current;
        });
        trimDirtyColumns();
    }

    public void scanDirtySections(Level level, int scanBudget, int batchSize, BiConsumer<BlockPos, CollapseUpdateSource> consumer) {
        if (scanBudget <= 0 || dirtyColumns.isEmpty()) {
            return;
        }

        int remaining = scanBudget;
        int processedInBatch = 0;
        Iterator<Map.Entry<Long, DirtyColumn>> iterator = dirtyColumns.entrySet().iterator();
        while (iterator.hasNext() && remaining > 0) {
            DirtyColumn column = iterator.next().getValue();
            while (remaining > 0) {
                Integer nextY = column.pollY();
                if (nextY == null) {
                    iterator.remove();
                    break;
                }

                consumer.accept(new BlockPos(column.x(), nextY, column.z()), column.source());
                remaining--;
                processedInBatch++;
                if (batchSize > 0 && processedInBatch >= batchSize) {
                    return;
                }
            }
        }
    }

    public ListTag serializeNBT() {
        ListTag nbt = new ListTag();
        for (DirtyColumn column : dirtyColumns.values()) {
            CompoundTag columnNbt = new CompoundTag();
            columnNbt.putInt("x", column.x());
            columnNbt.putInt("z", column.z());
            columnNbt.putIntArray("ys", column.ys().stream().mapToInt(Integer::intValue).toArray());
            columnNbt.putString("source", column.source().name());
            nbt.add(columnNbt);
        }
        return nbt;
    }

    public void deserializeNBT(ListTag nbt) {
        dirtyColumns.clear();
        for (int i = 0; i < nbt.size(); i++) {
            CompoundTag columnNbt = nbt.getCompound(i);
            if (!columnNbt.contains("x") || !columnNbt.contains("z") || !columnNbt.contains("ys")) {
                continue;
            }

            int[] ys = columnNbt.getIntArray("ys");
            if (ys.length == 0) {
                continue;
            }

            int x = columnNbt.getInt("x");
            int z = columnNbt.getInt("z");
            CollapseUpdateSource source = CollapseUpdateSource.fromName(columnNbt.getString("source"));
            DirtyColumn column = new DirtyColumn(x, z, source, ys[0]);
            for (int index = 1; index < ys.length; index++) {
                column.addY(ys[index]);
            }
            dirtyColumns.put(columnKey(x, z), column);
            trimDirtyColumns();
        }
    }

    private void trimDirtyColumns() {
        Iterator<Long> iterator = dirtyColumns.keySet().iterator();
        while (dirtyColumns.size() > MAX_DIRTY_COLUMNS && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static CollapseUpdateSource mergeSource(CollapseUpdateSource current, CollapseUpdateSource incoming) {
        if (incoming == null) {
            return current == null ? CollapseUpdateSource.NEIGHBOR_UPDATE : current;
        }
        if (current == null) {
            return incoming;
        }
        if (!incoming.isSolarDriven()) {
            return incoming;
        }
        return current;
    }

    private static final class DirtyColumn {
        private final int x;
        private final int z;
        private final Set<Integer> ys = new LinkedHashSet<>();
        private CollapseUpdateSource source;

        private DirtyColumn(int x, int z, CollapseUpdateSource source, int initialY) {
            this.x = x;
            this.z = z;
            this.source = source == null ? CollapseUpdateSource.NEIGHBOR_UPDATE : source;
            ys.add(initialY);
        }

        private int x() {
            return x;
        }

        private int z() {
            return z;
        }

        private Set<Integer> ys() {
            return ys;
        }

        private CollapseUpdateSource source() {
            return source;
        }

        private void addY(int y) {
            ys.add(y);
        }

        private Integer pollY() {
            Iterator<Integer> iterator = ys.iterator();
            if (!iterator.hasNext()) {
                return null;
            }
            Integer value = iterator.next();
            iterator.remove();
            return value;
        }

        private void mergeSource(CollapseUpdateSource incoming) {
            source = DirtyRegionTracker.mergeSource(source, incoming);
        }
    }
}
