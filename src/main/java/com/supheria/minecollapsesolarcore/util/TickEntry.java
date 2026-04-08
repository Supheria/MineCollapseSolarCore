package com.supheria.minecollapsesolarcore.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import com.supheria.minecollapsesolarcore.api.CollapseUpdateSource;

public class TickEntry
{
    private final BlockPos pos;
    private final CollapseUpdateSource source;
    private int ticks;

    public TickEntry(CompoundTag nbt)
    {
        this(BlockPos.of(nbt.getLong("pos")), CollapseUpdateSource.fromName(nbt.getString("source")), nbt.getInt("ticks"));
    }

    public TickEntry(BlockPos pos, int ticks)
    {
        this(pos, CollapseUpdateSource.NEIGHBOR_UPDATE, ticks);
    }

    public TickEntry(BlockPos pos, CollapseUpdateSource source, int ticks)
    {
        this.pos = pos;
        this.source = source;
        this.ticks = ticks;
    }

    public BlockPos getPos()
    {
        return pos;
    }

    public boolean tick()
    {
        this.ticks--;
        return this.ticks == 0;
    }

    public int getTicks()
    {
        return ticks;
    }

    public CollapseUpdateSource getSource()
    {
        return source;
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong("pos", pos.asLong());
        nbt.putString("source", source.name());
        nbt.putInt("ticks", ticks);
        return nbt;
    }
}

