package net.zerodind.minecollapse.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.zerodind.minecollapse.MineCollapse;

public class WorldTrackerCapability
{
    /** @deprecated Use {@link WorldTracker#get(Level)} instead rather than querying {@link Level#getCapability(Capability)} */
    @Deprecated
    public static final Capability<WorldTracker> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = new ResourceLocation(MineCollapse.MODID, "world_tracker");

}
