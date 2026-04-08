package net.zerodind.minecollapsesolarcore;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_COLLAPSING = BUILDER
            .comment("Enable rock collapsing when mining raw stone blocks")
            .define("enableBlockCollapsing", true);
    public static final ForgeConfigSpec.BooleanValue ENABLE_EXPLOSION_COLLAPSING = BUILDER
            .comment("Enable explosions causing immediate collapses.")
            .define("enableExplosionCollapsing", true);
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_LANDSLIDES = BUILDER
            .comment("Enable land slides (gravity affected blocks) when placing blocks or on block updates.")
            .define("enableBlockLandslides", true);
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHISELS_START_COLLAPSES = BUILDER
            .comment("Enable chisels starting collapses")
            .define("enableChiselsStartCollapses", true);

    public static final ForgeConfigSpec.DoubleValue COLLAPSE_TRIGGER_CHANCE = BUILDER
            .comment("Chance for a collapse to be triggered by mining a block.")
            .defineInRange("collapseTriggerChance", 0.1, 0, 1);
    public static final ForgeConfigSpec.DoubleValue COLLAPSE_FAKE_TRIGGER_CHANCE = BUILDER
            .comment("Chance for a collapse to be fake triggered by mining a block.")
            .defineInRange("collapseFakeTriggerChance", 0.35, 0, 1);
    public static final ForgeConfigSpec.DoubleValue COLLAPSE_PROPAGATE_CHANCE = BUILDER
            .comment("Chance for a block fo fall from mining collapse. Higher = more likely.")
            .defineInRange("collapsePropagateChance", 0.55, 0, 1);
    public static final ForgeConfigSpec.DoubleValue COLLAPSE_EXPLOSION_PROPAGATE_CHANCE = BUILDER
            .comment("Chance for a block to fall from an explosion triggered collapse. Higher = more likely.")
            .defineInRange("collapseExplosionPropagateChance", 0.3, 0, 1);
    public static final ForgeConfigSpec.IntValue COLLAPSE_MIN_RADIUS = BUILDER
            .comment("Minimum radius for a collapse")
            .defineInRange("collapseMinRadius", 3, 1, 32);
    public static final ForgeConfigSpec.IntValue COLLAPSE_RADIUS_VARIANCE = BUILDER
            .comment("Variance of the radius of a collapse. Total size is in [minRadius, minRadius + radiusVariance]")
            .defineInRange("collapseRadiusVariance", 16, 1, 32);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
