package net.zerodind.minecollapsesolarcore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityAccessor
{
    @Accessor
    void setBlockState(BlockState value);
}
