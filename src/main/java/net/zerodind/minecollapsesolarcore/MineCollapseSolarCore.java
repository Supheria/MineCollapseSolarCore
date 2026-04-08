package net.zerodind.minecollapsesolarcore;

import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.zerodind.minecollapsesolarcore.api.CollapseSchedulingAccess;
import net.zerodind.minecollapsesolarcore.api.CollapseSchedulingApi;
import net.zerodind.minecollapsesolarcore.api.CollapseUpdateSource;
import net.zerodind.minecollapsesolarcore.blocks.ExtendedProperties;
import net.zerodind.minecollapsesolarcore.blocks.FluidProperty;
import net.zerodind.minecollapsesolarcore.blocks.HorizontalSupportBlock;
import net.zerodind.minecollapsesolarcore.blocks.VerticalSupportBlock;
import net.zerodind.minecollapsesolarcore.entities.MineCollapseSolarCoreFallingBlockEntity;
import net.zerodind.minecollapsesolarcore.features.ErosionFeature;
import net.zerodind.minecollapsesolarcore.network.PacketHandler;
import net.zerodind.minecollapsesolarcore.recipes.CollapseRecipe;
import net.zerodind.minecollapsesolarcore.recipes.LandslideRecipe;
import net.zerodind.minecollapsesolarcore.recipes.SimpleBlockRecipe;
import net.zerodind.minecollapsesolarcore.util.Helpers;
import net.zerodind.minecollapsesolarcore.util.IndirectHashCollection;
import net.zerodind.minecollapsesolarcore.util.Support;
import net.zerodind.minecollapsesolarcore.util.WorldTracker;
import net.zerodind.minecollapsesolarcore.util.WorldTrackerCapability;

@Mod(MineCollapseSolarCore.MODID)
public class MineCollapseSolarCore
{
    private static final double PLAYER_CHAIN_RANGE = 10.0D;
    private static final double PLAYER_CHAIN_RANGE_SQR = PLAYER_CHAIN_RANGE * PLAYER_CHAIN_RANGE;

    public static final String MODID = "minecollapsesolarcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final RegistryObject<Block> BLOCK_HARDENED_STONE = BLOCKS.register("hardened_stone",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F)));
    public static final RegistryObject<Block> BLOCK_HARDENED_GRANITE = BLOCKS.register("hardened_granite",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).requiresCorrectToolForDrops().strength(1.5F, 6.0F)));
    public static final RegistryObject<Block> BLOCK_HARDENED_DIORITE = BLOCKS.register("hardened_diorite",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops().strength(1.5F, 6.0F)));
    public static final RegistryObject<Block> BLOCK_HARDENED_ANDESITE = BLOCKS.register("hardened_andesite",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F)));
    public static final RegistryObject<Block> BLOCK_HARDENED_TUFF = BLOCKS.register("hardened_tuff",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).sound(SoundType.TUFF).requiresCorrectToolForDrops().strength(1.5F, 6.0F)));
    public static final RegistryObject<Block> BLOCK_HARDENED_DEEPSLATE = BLOCKS.register("hardened_deepslate",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops().strength(3.0F, 6.0F)));

    public static final RegistryObject<Block> BLOCK_VERTICAL_SUPPORT = BLOCKS.register("vertical_support",
            () -> new VerticalSupportBlock(ExtendedProperties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0F).noOcclusion().flammableLikeLogs()));
    public static final RegistryObject<Block> BLOCK_HORIZONTAL_SUPPORT = BLOCKS.register("horizontal_support",
            () -> new HorizontalSupportBlock(ExtendedProperties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0F).noOcclusion().flammableLikeLogs()));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final RegistryObject<Item> ITEM_HARDENED_STONE = ITEMS.register("hardened_stone",
            () -> new BlockItem(BLOCK_HARDENED_STONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ITEM_HARDENED_GRANITE = ITEMS.register("hardened_granite",
            () -> new BlockItem(BLOCK_HARDENED_GRANITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ITEM_HARDENED_DIORITE = ITEMS.register("hardened_diorite",
            () -> new BlockItem(BLOCK_HARDENED_DIORITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ITEM_HARDENED_ANDESITE = ITEMS.register("hardened_andesite",
            () -> new BlockItem(BLOCK_HARDENED_ANDESITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> ITEM_HARDENED_TUFF = ITEMS.register("hardened_tuff",
            () -> new BlockItem(BLOCK_HARDENED_TUFF.get(), new Item.Properties()));
    public static final RegistryObject<Item> ITEM_HARDENED_DEEPSLATE = ITEMS.register("hardened_deepslate",
            () -> new BlockItem(BLOCK_HARDENED_DEEPSLATE.get(), new Item.Properties()));

    public static final RegistryObject<Item> ITEM_SUPPORT = ITEMS.register("support",
            () -> new StandingAndWallBlockItem(BLOCK_VERTICAL_SUPPORT.get(), BLOCK_HORIZONTAL_SUPPORT.get(), new Item.Properties(), Direction.DOWN));

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register(MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ITEM_SUPPORT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ITEM_SUPPORT.get());
                output.accept(ITEM_HARDENED_STONE.get());
                output.accept(ITEM_HARDENED_GRANITE.get());
                output.accept(ITEM_HARDENED_DIORITE.get());
                output.accept(ITEM_HARDENED_ANDESITE.get());
                output.accept(ITEM_HARDENED_TUFF.get());
                output.accept(ITEM_HARDENED_DEEPSLATE.get());
            }).build());

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final RegistryObject<EntityType<MineCollapseSolarCoreFallingBlockEntity>> ENTITY_FALLING_BLOCK = ENTITIES.register("falling_block",
            () -> EntityType.Builder.<MineCollapseSolarCoreFallingBlockEntity>of(MineCollapseSolarCoreFallingBlockEntity::new, MobCategory.MISC).sized(0.98f, 0.98f).build("falling_block"));

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);
    public static final RegistryObject<ErosionFeature> FEATURE_EROSION = FEATURES.register("erosion", () -> new ErosionFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final RegistryObject<SoundEvent> SOUND_ROCK_SLIDE_LONG = SOUNDS.register("random.rock_slide_long", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "random.rock_slide_long")));
    public static final RegistryObject<SoundEvent> SOUND_ROCK_SLIDE_LONG_FAKE = SOUNDS.register("random.rock_slide_long_fake", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "random.rock_slide_long_fake")));
    public static final RegistryObject<SoundEvent> SOUND_ROCK_SLIDE_SHORT = SOUNDS.register("random.rock_slide_short", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "random.rock_slide_short")));
    public static final RegistryObject<SoundEvent> SOUND_DIRT_SLIDE_SHORT = SOUNDS.register("random.dirt_slide_short", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "random.dirt_slide_short")));

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
    public static final RegistryObject<RecipeType<CollapseRecipe>> RECIPE_TYPE_COLLAPSE = RECIPE_TYPES.register("collapse", () -> new RecipeType<CollapseRecipe>() {
        @Override
        public String toString() {
            return "collapse";
        }
    });
    public static final RegistryObject<RecipeType<LandslideRecipe>> RECIPE_TYPE_LANDSLIDE = RECIPE_TYPES.register("landslide", () -> new RecipeType<LandslideRecipe>() {
        @Override
        public String toString() {
            return "landslide";
        }
    });

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final RegistryObject<SimpleBlockRecipe.Serializer<CollapseRecipe>> RECIPE_SERIALIZER_COLLAPSE = RECIPE_SERIALIZERS.register("collapse", () -> new SimpleBlockRecipe.Serializer<>(CollapseRecipe::new));
    public static final RegistryObject<SimpleBlockRecipe.Serializer<LandslideRecipe>> RECIPE_SERIALIZER_LANDSLIDE = RECIPE_SERIALIZERS.register("landslide", () -> new SimpleBlockRecipe.Serializer<>(LandslideRecipe::new));

    public static final TagKey<Block> TAG_CAN_TRIGGER_COLLAPSE = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "can_trigger_collapse"));
    public static final TagKey<Block> TAG_CAN_START_COLLAPSE = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "can_start_collapse"));
    public static final TagKey<Block> TAG_CAN_COLLAPSE = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "can_collapse"));
    public static final TagKey<Block> TAG_CAN_LANDSLIDE = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "can_landslide"));
    public static final TagKey<Block> TAG_SUPPORTS_LANDSLIDE = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "supports_landslide")); // Non-full blocks that count as full blocks for the purposes of landslide side support check
    public static final TagKey<Block> TAG_NOT_SOLID_SUPPORTING = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "not_solid_supporting")); // Blocks that don't count as supporting the block above for the purposes of collapse start checks
    public static final TagKey<Block> TAG_TOUGHNESS_1 = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "toughness_1")); // Tags for toughness of materials w.r.t falling blocks
    public static final TagKey<Block> TAG_TOUGHNESS_2 = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "toughness_2")); // Tags for toughness of materials w.r.t falling blocks
    public static final TagKey<Block> TAG_TOUGHNESS_3 = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "toughness_3")); // Tags for toughness of materials w.r.t falling blocks
    public static final TagKey<Block> TAG_SUPPORT_BEAMS = TagKey.create(Registries.BLOCK, new ResourceLocation(MODID, "support_beams"));

    public static final FluidProperty FLUID_PROPERTY_WATER = FluidProperty.create("fluid", Stream.of(Fluids.EMPTY, Fluids.WATER));
    
    public MineCollapseSolarCore()
    {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            modEventBus.addListener(this::registerEntityRenderers);
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITIES.register(modEventBus);
        FEATURES.register(modEventBus);
        SOUNDS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);

        PacketHandler.init();

        // Register ourselves for server and other game events we are interested in
        final IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
        forgeEventBus.addGenericListener(Level.class, this::attachWorldCapabilities);
        forgeEventBus.addListener(this::onBlockBroken);
        forgeEventBus.addListener(this::onBlockPlace);
        forgeEventBus.addListener(this::onNeighborUpdate);
        forgeEventBus.addListener(this::onExplosionDetonate);
        forgeEventBus.addListener(this::onWorldTick);
        forgeEventBus.addListener(this::onDataPackSync);
        forgeEventBus.addListener(this::onTagsUpdated);
        forgeEventBus.addListener(this::addReloadListeners);

        CollapseSchedulingAccess.setApi(new CollapseSchedulingApi()
        {
            @Override
            public void scheduleImmediateLandslide(Level level, BlockPos pos, CollapseUpdateSource source)
            {
                WorldTracker.get(level).scheduleImmediateLandslide(pos, source);
            }

            @Override
            public void markLandslideRegionDirty(Level level, BlockPos pos, CollapseUpdateSource source)
            {
                WorldTracker.get(level).markLandslideRegionDirty(pos, source);
            }

            @Override
            public void scheduleImmediateCollapseCheck(Level level, BlockPos pos, CollapseUpdateSource source)
            {
                WorldTracker.get(level).scheduleImmediateCollapseCheck(pos, source);
            }
        });

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void attachWorldCapabilities(AttachCapabilitiesEvent<Level> event)
    {
        event.addCapability(WorldTrackerCapability.KEY, new WorldTracker(event.getObject()));
    }

    private void onBlockBroken(final BlockEvent.BreakEvent event)
    {
        // Trigger a collapse
        final LevelAccessor levelAccess = event.getLevel();
        final BlockPos pos = event.getPos();
        final BlockState state = levelAccess.getBlockState(pos);

        if (levelAccess instanceof Level level)
        {
            scheduleImmediateLandslideNeighbors(level, pos);
            scheduleDirectLandslideRetryNeighbors(level, pos, event.getPlayer());
        }

        if (Helpers.isBlock(state, TAG_CAN_TRIGGER_COLLAPSE) && levelAccess instanceof Level level)
        {
            CollapseRecipe.tryTriggerCollapse(level, pos);
            return;
        }
    }

    private void onBlockPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel() instanceof final ServerLevel world)
        {
            final BlockPos pos = event.getPos();
            final BlockState state = event.getState();

            if (Helpers.isBlock(state, TAG_CAN_LANDSLIDE))
            {
                CollapseUpdateSource source = CollapseUpdateSource.fromName(CollapseSchedulingAccess.getActiveSourceName());
                if (source == CollapseUpdateSource.FALLING_BLOCK_SETTLE)
                {
                    return;
                }
                else if (source.isSolarDriven())
                {
                    WorldTracker.get(world).markLandslideRegionDirty(pos, source);
                }
                else
                {
                    WorldTracker.get(world).scheduleImmediateLandslide(pos, CollapseUpdateSource.PLAYER_ACTION);
                }
            }
        }
    }

    private void onNeighborUpdate(BlockEvent.NeighborNotifyEvent event)
    {
        if (event.getLevel() instanceof final ServerLevel level)
        {
            for (Direction direction : event.getNotifiedSides())
            {
                // Check each notified block for a potential gravity block
                final BlockPos pos = event.getPos().relative(direction);
                final BlockState state = level.getBlockState(pos);

                if (Helpers.isBlock(state, TAG_CAN_LANDSLIDE))
                {
                    CollapseUpdateSource source = CollapseUpdateSource.fromName(CollapseSchedulingAccess.getActiveSourceName());
                    if (source == CollapseUpdateSource.FALLING_BLOCK_SETTLE)
                    {
                        WorldTracker.get(level).markLandslideRegionDirty(pos, source);
                    }
                    else if (source.isSolarDriven())
                    {
                        WorldTracker.get(level).markLandslideRegionDirty(pos, source);
                    }
                    else
                    {
                        WorldTracker.get(level).scheduleImmediateLandslide(pos, CollapseUpdateSource.NEIGHBOR_UPDATE);
                    }
                }
            }
        }
    }

    private void onExplosionDetonate(ExplosionEvent event)
    {
        final Level level = event.getLevel();
        if (!level.isClientSide)
        {
            WorldTracker.get(level).addCollapsePositions(BlockPos.containing(event.getExplosion().getPosition()), event.getExplosion().getToBlow());
        }
    }

    private void onWorldTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START && event.level instanceof ServerLevel level)
        {
            WorldTracker.get(level).tick();
        }
    }

    private void scheduleImmediateLandslideNeighbors(Level level, BlockPos origin)
    {
        for (Direction direction : Direction.values())
        {
            BlockPos candidatePos = origin.relative(direction);
            BlockState candidateState = level.getBlockState(candidatePos);
            if (Helpers.isBlock(candidateState, TAG_CAN_LANDSLIDE))
            {
                WorldTracker.get(level).scheduleImmediateLandslide(candidatePos, CollapseUpdateSource.PLAYER_ACTION);
            }
        }
    }

    private void scheduleDirectLandslideRetryNeighbors(Level level, BlockPos origin, Player player)
    {
        for (Direction direction : Direction.values())
        {
            BlockPos candidatePos = origin.relative(direction);
            BlockState candidateState = level.getBlockState(candidatePos);
            if (!Helpers.isBlock(candidateState, TAG_CAN_LANDSLIDE))
            {
                continue;
            }

            WorldTracker.get(level).scheduleDirectLandslideRetry(candidatePos, CollapseUpdateSource.PLAYER_ACTION);
            schedulePlayerColumnRetries(level, candidatePos, player);
        }
    }

    private void schedulePlayerColumnRetries(Level level, BlockPos startPos, Player player)
    {
        if (player == null) {
            return;
        }

        BlockPos currentPos = startPos.above();
        while (isWithinPlayerChainRange(player, currentPos))
        {
            BlockState currentState = level.getBlockState(currentPos);
            if (!Helpers.isBlock(currentState, TAG_CAN_LANDSLIDE))
            {
                break;
            }
            WorldTracker.get(level).scheduleDirectLandslideRetry(currentPos, CollapseUpdateSource.PLAYER_ACTION);
            currentPos = currentPos.above();
        }
    }

    private static boolean isWithinPlayerChainRange(Player player, BlockPos pos)
    {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= PLAYER_CHAIN_RANGE_SQR;
    }

    private void onDataPackSync(OnDatapackSyncEvent event)
    {
        // Sync managers
        final ServerPlayer player = event.getPlayer();
        final PacketDistributor.PacketTarget target = player == null ? PacketDistributor.ALL.noArg() : PacketDistributor.PLAYER.with(() -> player);

        PacketHandler.send(target, Support.MANAGER.createSyncPacket());
    }

    /**
     * This is when tags are safe to be loaded, so we can do post reload actions that involve querying ingredients.
     * It is fired on both logical server and client after resources are reloaded (or, sent from server).
     * In addition, during the first load on a server in {@link net.minecraft.server.Main}, the server won't exist yet at all.
     * In that case, we need to rely on the fact that {@link AddReloadListenerEvent} will be fired before that point, and we can capture the server's recipe manager there.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onTagsUpdated(TagsUpdatedEvent event)
    {
        if (event.shouldUpdateStaticData())
        {
            // First, reload all caches
            final RecipeManager manager = Helpers.getUnsafeRecipeManager();
            IndirectHashCollection.reloadAllCaches(manager);

            // Then apply post reload actions which may query the cache
            Support.updateMaximumSupportRange();

            for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE)
            {
                LOGGER.debug("Loaded {} recipes of type {}", Helpers.getRecipes(manager, () -> (RecipeType) type).size(), BuiltInRegistries.RECIPE_TYPE.getKey(type));
            }
        }
    }

    private void addReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(Support.MANAGER);

        // In addition, we capture the recipe manager here
        Helpers.setCachedRecipeManager(event.getServerResources().getRecipeManager());
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ENTITY_FALLING_BLOCK.get(), FallingBlockRenderer::new);
    }
}
