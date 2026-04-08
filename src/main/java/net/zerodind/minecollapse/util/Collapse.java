package net.zerodind.minecollapse.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class Collapse
{
    BlockPos centerPos;
    List<BlockPos> nextPositions;
    double radiusSquared;

    public Collapse(BlockPos centerPos, List<BlockPos> nextPositions, double radiusSquared)
    {
        this.centerPos = centerPos;
        this.nextPositions = nextPositions;
        this.radiusSquared = radiusSquared;
    }

    public Collapse(CompoundTag nbt)
    {
        centerPos = BlockPos.of(nbt.getLong("centerPos"));
        nextPositions = Arrays.stream(nbt.getLongArray("nextPositions")).mapToObj(BlockPos::of).collect(Collectors.toList());
        radiusSquared = nbt.getDouble("radiusSquared");
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong("centerPos", centerPos.asLong());
        nbt.putLongArray("nextPositions", nextPositions.stream().mapToLong(BlockPos::asLong).toArray());
        nbt.putDouble("radiusSquared", radiusSquared);
        return nbt;
    }
}
