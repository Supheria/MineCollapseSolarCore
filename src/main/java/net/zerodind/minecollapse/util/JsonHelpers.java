package net.zerodind.minecollapse.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;

public final class JsonHelpers extends GsonHelper
{
    public static <T> T getRegistryEntry(JsonObject json, String key, Registry<T> registry)
    {
        return getRegistryEntry(GsonHelper.getAsString(json, key), registry);
    }

    public static <T> T getRegistryEntry(JsonElement json, Registry<T> registry)
    {
        return getRegistryEntry(GsonHelper.convertToString(json, "entry"), registry);
    }

    public static <T> T getRegistryEntry(String key, Registry<T> registry)
    {
        final ResourceLocation res = new ResourceLocation(key);
        final T obj = registry.get(res);
        if (obj == null || !registry.containsKey(res))
        {
            throw new JsonParseException("Unknown " + registry.key().location().getPath() + ": " + key);
        }
        return obj;
    }

    public static <T> TagKey<T> getTag(JsonObject json, String key, ResourceKey<? extends Registry<T>> registry)
    {
        return getTag(GsonHelper.getAsString(json, key), registry);
    }

    public static <T> TagKey<T> getTag(String key, ResourceKey<? extends Registry<T>> registry)
    {
        final ResourceLocation res = new ResourceLocation(key);
        return TagKey.create(registry, res);
    }

    public static JsonElement get(JsonObject json, String key)
    {
        if (!json.has(key))
        {
            throw new JsonParseException("Missing required key: " + key);
        }
        return json.get(key);
    }

    public static BlockState getBlockState(String block)
    {
        final StringReader reader = new StringReader(block);
        try
        {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), reader, false).blockState();
        }
        catch (CommandSyntaxException e)
        {
            throw new JsonParseException(e.getMessage());
        }
    }
}
