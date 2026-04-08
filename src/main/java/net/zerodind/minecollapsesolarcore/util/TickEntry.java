package net.zerodind.minecollapsesolarcore.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class TickEntry
{
    private final BlockPos pos;
    private int ticks;

    public TickEntry(CompoundTag nbt)
    {
        this(BlockPos.of(nbt.getLong("pos")), nbt.getInt("ticks"));
    }

    public TickEntry(BlockPos pos, int ticks)
    {
        this.pos = pos;
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

    public CompoundTag serializeNBT()
    {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong("pos", pos.asLong());
        nbt.putInt("ticks", ticks);
        return nbt;
    }
}
