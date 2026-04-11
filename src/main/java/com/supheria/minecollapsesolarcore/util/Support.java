package com.supheria.minecollapsesolarcore.util;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.supheria.minecollapsesolarcore.MineCollapseSolarCore;
import com.supheria.minecollapsesolarcore.network.DataManagerSyncPacket;
import com.supheria.minecollapsesolarcore.recipes.BlockIngredient;

public final class Support
{
    public static final DataManager<Support> MANAGER = new DataManager<>(new ResourceLocation(MineCollapseSolarCore.MODID, "supports"), "support", Support::new, Support::new, Support::encode, Packet::new);
    public static final IndirectHashCollection<Block, Support> CACHE = IndirectHashCollection.create(s -> s.ingredient.blocks(), MANAGER::getValues);

    /**
     * The maximum range of all supports, used for support radius checks.
     */
    private static SupportRange RANGE = new SupportRange(0, 0, 0);

    /**
     * Exposed for addons. See comment for {@link Support#RANGE}.
     */
    public static SupportRange getSupportCheckRange()
    {
        return RANGE;
    }

    /**
     * Finds all unsupported positions in a large area. It's more efficient than checking each block individually and calling {@link Support#isSupported(BlockGetter, BlockPos)}
     */
    public static Set<BlockPos> findUnsupportedPositions(BlockGetter worldIn, BlockPos from, BlockPos to)
    {
        Set<BlockPos> listSupported = new HashSet<>();
        Set<BlockPos> listUnsupported = new HashSet<>();
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (BlockPos searchingPoint : getMaximumSupportedAreaAround(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)))
        {
            if (!listSupported.contains(searchingPoint))
            {
                listUnsupported.add(searchingPoint.immutable()); // Adding blocks that wasn't found supported
            }
            final BlockState supportState = worldIn.getBlockState(searchingPoint);
            final Support support = get(supportState);
            if (support != null)
            {
                for (BlockPos supported : support.getSupportedArea(searchingPoint))
                {
                    listSupported.add(supported.immutable()); // Adding all supported blocks by this support
                    listUnsupported.remove(supported); // Remove if this block was added earlier
                }
            }
        }
        // Searching point wasn't from points between from <-> to but
        // Time to remove the outsides that were added for convenience
        listUnsupported.removeIf(content -> content.getX() < minX || content.getX() > maxX || content.getY() < minY || content.getY() > maxY || content.getZ() < minZ || content.getZ() > maxZ);
        return listUnsupported;
    }

    public static boolean isSupported(BlockGetter world, BlockPos pos)
    {
        for (BlockPos supportPos : getMaximumSupportedAreaAround(pos, pos))
        {
            final BlockState supportState = world.getBlockState(supportPos);
            final Support support = get(supportState);
            if (support != null && support.canSupport(world, supportPos, pos))
            {
                return true;
            }
        }
        return false;
    }

    public static Iterable<BlockPos> getMaximumSupportedAreaAround(BlockPos minPoint, BlockPos maxPoint)
    {
        return BlockPos.betweenClosed(minPoint.offset(-RANGE.horizontal(), -RANGE.down(), -RANGE.horizontal()), maxPoint.offset(RANGE.horizontal(), RANGE.up(), RANGE.horizontal()));
    }

    public static Support get(BlockState state)
    {
        for (Support support : CACHE.getAll(state.getBlock()))
        {
            if (support.matches(state))
            {
                return support;
            }
        }
        return null;
    }

    public static void updateMaximumSupportRange()
    {
        // Re-calculate maximum support range
        int up = 0, down = 0, horizontal = 0;
        for (Support support : MANAGER.getValues())
        {
            up = Math.max(support.getSupportUp(), up);
            down = Math.max(support.getSupportDown(), down);
            horizontal = Math.max(support.getSupportHorizontal(), horizontal);
        }

        RANGE = new SupportRange(up, down, horizontal);
    }

    private final ResourceLocation id;
    private final BlockIngredient ingredient;
    private final int supportUp, supportDown, supportHorizontal;

    public Support(ResourceLocation id, JsonObject json)
    {
        this.id = id;

        this.ingredient = BlockIngredient.fromJson(JsonHelpers.get(json, "ingredient"));
        this.supportUp = GsonHelper.getAsInt(json, "support_up", 0);
        this.supportDown = GsonHelper.getAsInt(json, "support_down", 0);
        this.supportHorizontal = GsonHelper.getAsInt(json, "support_horizontal", 0);

        if (supportUp < 0 || supportDown < 0 || supportHorizontal < 0)
        {
            throw new JsonParseException("Support values must be nonnegative.");
        }
    }

    private Support(ResourceLocation id, FriendlyByteBuf buffer)
    {
        this.id = id;

        this.ingredient = BlockIngredient.fromNetwork(buffer);
        this.supportUp = buffer.readVarInt();
        this.supportDown = buffer.readVarInt();
        this.supportHorizontal = buffer.readVarInt();
    }

    public ResourceLocation getId()
    {
        return id;
    }

    public int getSupportUp()
    {
        return supportUp;
    }

    public int getSupportDown()
    {
        return supportDown;
    }

    public int getSupportHorizontal()
    {
        return supportHorizontal;
    }

    public boolean matches(BlockState state)
    {
        return ingredient.test(state);
    }

    public boolean canSupport(BlockGetter world, BlockPos supportPos, BlockPos testPos)
    {
        BlockPos diff = supportPos.subtract(testPos);
        return Math.abs(diff.getX()) <= supportHorizontal
                && -supportDown <= diff.getY()
                && diff.getY() <= supportUp
                && Math.abs(diff.getZ()) <= supportHorizontal
                && hasContinuousVerticalLoadPath(world, supportPos, testPos);
    }

    private boolean hasContinuousVerticalLoadPath(BlockGetter world, BlockPos supportPos, BlockPos testPos)
    {
        if (testPos.getY() <= supportPos.getY() + 1)
        {
            return true;
        }

        BlockPos.MutableBlockPos cursor = testPos.mutable().move(0, -1, 0);
        while (cursor.getY() > supportPos.getY())
        {
            if (FluidHelpers.isAirOrEmptyFluid(world.getBlockState(cursor)))
            {
                return false;
            }
            cursor.move(0, -1, 0);
        }
        return true;
    }

    public Iterable<BlockPos> getSupportedArea(BlockPos center)
    {
        return BlockPos.betweenClosed(center.offset(-supportHorizontal, -supportDown, -supportHorizontal), center.offset(supportHorizontal, supportUp, supportHorizontal));
    }

    private void encode(FriendlyByteBuf buffer)
    {
        ingredient.toNetwork(buffer);

        buffer.writeVarInt(supportUp);
        buffer.writeVarInt(supportDown);
        buffer.writeVarInt(supportHorizontal);
    }

    public record SupportRange(int up, int down, int horizontal) {}

    public static class Packet extends DataManagerSyncPacket<Support> {}
}

