package net.zerodind.minecollapsesolarcore.blocks;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.RegistryObject;

/**
 * A pair of a source and getFlowing fluid, for {@link FlowingFluid}s.
 */
public record FluidRegistryObject<F extends FlowingFluid>(RegistryObject<FluidType> type, RegistryObject<F> flowing, RegistryObject<F> source)
{
    public F getFlowing()
    {
        return flowing.get();
    }

    public F getSource()
    {
        return source.get();
    }

    public BlockState createSourceBlock()
    {
        return source.get().defaultFluidState().createLegacyBlock();
    }
}
