package com.supheria.minecollapsesolarcore.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

public class Helpers
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Field RECIPE_MAP_FIELD = null;

    private static RecipeManager CACHED_RECIPE_MANAGER = null;

    public static RecipeManager getUnsafeRecipeManager()
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null)
        {
            return server.getRecipeManager();
        }

        try
        {
            final Level level = ClientHelpers.getLevel();
            if (level != null)
            {
                return level.getRecipeManager();
            }
        }
        catch (Throwable t)
        {
            LOGGER.info("^ This is fine - No client or server recipe manager present upon initial resource reload on physical server");
        }

        if (CACHED_RECIPE_MANAGER != null)
        {
            LOGGER.info("Successfully captured server recipe manager");
            return CACHED_RECIPE_MANAGER;
        }

        throw new IllegalStateException("No recipe manager was present - tried server, client, and captured value. This will cause problems!");
    }

    public static void setCachedRecipeManager(RecipeManager manager)
    {
        CACHED_RECIPE_MANAGER = manager;
    }

    @SuppressWarnings("unchecked")
    public static <C extends Container, R extends Recipe<C>> Map<ResourceLocation, R> getRecipes(RecipeManager recipeManager, Supplier<RecipeType<R>> type)
    {
        final Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipesByType = getRecipesByType(recipeManager);
        return (Map<ResourceLocation, R>) recipesByType.getOrDefault(type.get(), Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private static Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> getRecipesByType(RecipeManager recipeManager)
    {
        try
        {
            if (RECIPE_MAP_FIELD == null)
            {
                for (Field field : RecipeManager.class.getDeclaredFields())
                {
                    if (Map.class.isAssignableFrom(field.getType()))
                    {
                        field.setAccessible(true);
                        final Object value = field.get(recipeManager);
                        if (value instanceof Map<?, ?> map)
                        {
                            final Object firstKey = map.keySet().stream().findFirst().orElse(null);
                            if (firstKey == null || firstKey instanceof RecipeType<?>)
                            {
                                RECIPE_MAP_FIELD = field;
                                break;
                            }
                        }
                    }
                }
            }

            if (RECIPE_MAP_FIELD == null)
            {
                throw new IllegalStateException("Unable to locate RecipeManager recipe map field");
            }

            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) RECIPE_MAP_FIELD.get(recipeManager);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Unable to access RecipeManager recipes", e);
        }
    }

    public static boolean isBlock(BlockState state, TagKey<Block> tag)
    {
        return state.is(tag);
    }

    public static boolean isBlock(Block block, TagKey<Block> tag)
    {
        return block.builtInRegistryHolder().is(tag);
    }

    public static Stream<Block> allBlocks(TagKey<Block> tag)
    {
        return BuiltInRegistries.BLOCK.getOrCreateTag(tag).stream().map(Holder::value);
    }

    public static void dropWithContext(ServerLevel level, BlockState state, BlockPos pos, Consumer<LootParams.Builder> consumer, boolean randomized)
    {
        BlockEntity tileEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        // Copied from Block.getDrops()
        LootParams.Builder params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, null)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, tileEntity);
        consumer.accept(params);

        state.getDrops(params).forEach(stackToSpawn -> {
            if (randomized)
            {
                Block.popResource(level, pos, stackToSpawn);
            }
            else
            {
                spawnDropsAtExactCenter(level, pos, stackToSpawn);
            }
        });
        state.spawnAfterBreak(level, pos, ItemStack.EMPTY, false);
    }

    /**
     * {@link Block#popResource(Level, BlockPos, ItemStack)} but without randomness as to the velocity and position.
     */
    public static void spawnDropsAtExactCenter(Level level, BlockPos pos, ItemStack stack)
    {
        if (!level.isClientSide && !stack.isEmpty() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && !level.restoringBlockSnapshots)
        {
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack, 0D, 0D, 0D);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    /**
     * Select N unique elements from a list, without having to shuffle the whole list.
     * This involves moving the selected elements to the end of the list. Note: this method will mutate the passed in list!
     * From <a href="https://stackoverflow.com/questions/4702036/take-n-random-elements-from-a-liste">Stack Overflow</a>
     */
    public static <T> List<T> uniqueRandomSample(List<T> list, int n, RandomSource r)
    {
        final int length = list.size();
        if (length < n)
        {
            throw new IllegalArgumentException("Cannot select n=" + n + " from a list of size = " + length);
        }
        for (int i = length - 1; i >= length - n; --i)
        {
            Collections.swap(list, i, r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }

    public static <E, C extends Collection<E>> void encodeAll(FriendlyByteBuf buffer, C collection, BiConsumer<E, FriendlyByteBuf> encoder)
    {
        buffer.writeVarInt(collection.size());
        for (E e : collection)
        {
            encoder.accept(e, buffer);
        }
    }

    public static <E, C extends Collection<E>> C decodeAll(FriendlyByteBuf buffer, C collection, Function<FriendlyByteBuf, E> decoder)
    {
        final int size = buffer.readVarInt();
        for (int i = 0; i < size; i++)
        {
            collection.add(decoder.apply(buffer));
        }
        return collection;
    }

}

