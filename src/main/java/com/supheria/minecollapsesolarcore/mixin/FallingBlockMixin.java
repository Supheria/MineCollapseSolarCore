package com.supheria.minecollapsesolarcore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.supheria.minecollapsesolarcore.recipes.LandslideRecipe;

@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin
{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void preventVanillaFallingBlockBehavior(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci)
    {
        if (LandslideRecipe.getRecipe(state) != null)
        {
            ci.cancel();
        }
    }
}

