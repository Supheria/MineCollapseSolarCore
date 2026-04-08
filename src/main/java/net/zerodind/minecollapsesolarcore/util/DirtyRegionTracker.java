package net.zerodind.minecollapsesolarcore.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.zerodind.minecollapsesolarcore.api.CollapseUpdateSource;

public final class DirtyRegionTracker {
    private final Map<Long, DirtySection> dirtySections = new LinkedHashMap<>();

    public void markSection(BlockPos pos, CollapseUpdateSource source) {
        BlockPos sectionPos = new BlockPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        dirtySections.compute(sectionPos.asLong(), (ignored, current) -> {
            if (current == null) {
                return new DirtySection(0, source);
            }
            current.mergeSource(source);
            return current;
        });
    }

    public void scanDirtySections(Level level, int scanBudget, int batchSize, BiConsumer<BlockPos, CollapseUpdateSource> consumer) {
        if (scanBudget <= 0 || dirtySections.isEmpty()) {
            return;
        }

        int remaining = scanBudget;
        Iterator<Map.Entry<Long, DirtySection>> iterator = dirtySections.entrySet().iterator();
        while (iterator.hasNext() && remaining > 0) {
            Map.Entry<Long, DirtySection> entry = iterator.next();
            BlockPos sectionPos = BlockPos.of(entry.getKey());
            DirtySection dirtySection = entry.getValue();
            int cursor = dirtySection.cursor();
            int processed = 0;
            while (cursor < 4096 && remaining > 0 && processed < batchSize) {
                consumer.accept(cursorToWorldPos(sectionPos, cursor), dirtySection.source());
                cursor++;
                remaining--;
                processed++;
            }
            if (cursor >= 4096) {
                iterator.remove();
            } else {
                dirtySection.setCursor(cursor);
            }
        }
    }

    public ListTag serializeNBT() {
        ListTag nbt = new ListTag();
        for (Map.Entry<Long, DirtySection> entry : dirtySections.entrySet()) {
            CompoundTag sectionNbt = new CompoundTag();
            DirtySection dirtySection = entry.getValue();
            sectionNbt.putLong("sectionPos", entry.getKey());
            sectionNbt.putInt("cursor", dirtySection.cursor());
            sectionNbt.putString("source", dirtySection.source().name());
            nbt.add(sectionNbt);
        }
        return nbt;
    }

    public void deserializeNBT(ListTag nbt) {
        dirtySections.clear();
        for (int i = 0; i < nbt.size(); i++) {
            CompoundTag sectionNbt = nbt.getCompound(i);
            long sectionPos = sectionNbt.getLong("sectionPos");
            int cursor = sectionNbt.getInt("cursor");
            CollapseUpdateSource source = CollapseUpdateSource.fromName(sectionNbt.getString("source"));
            dirtySections.put(sectionPos, new DirtySection(cursor, source));
        }
    }

    private static final class DirtySection {
        private int cursor;
        private CollapseUpdateSource source;

        private DirtySection(int cursor, CollapseUpdateSource source) {
            this.cursor = cursor;
            this.source = source == null ? CollapseUpdateSource.NEIGHBOR_UPDATE : source;
        }

        private int cursor() {
            return cursor;
        }

        private void setCursor(int cursor) {
            this.cursor = cursor;
        }

        private CollapseUpdateSource source() {
            return source;
        }

        private void mergeSource(CollapseUpdateSource incoming) {
            if (incoming == null) {
                return;
            }
            if (!incoming.isSolarDriven()) {
                source = incoming;
                return;
            }
            if (source == null) {
                source = incoming;
            }
        }
    }

    private static BlockPos cursorToWorldPos(BlockPos sectionPos, int cursor) {
        int localX = cursor & 15;
        int localZ = (cursor >> 4) & 15;
        int localY = (cursor >> 8) & 15;
        return new BlockPos((sectionPos.getX() << 4) + localX, (sectionPos.getY() << 4) + localY, (sectionPos.getZ() << 4) + localZ);
    }
}
