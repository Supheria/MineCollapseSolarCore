package net.zerodind.minecollapsesolarcore.network;

import org.apache.commons.lang3.mutable.MutableInt;

import net.zerodind.minecollapsesolarcore.MineCollapseSolarCore;
import net.zerodind.minecollapsesolarcore.util.DataManager;
import net.zerodind.minecollapsesolarcore.util.Support;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PacketHandler
{
    private static final String VERSION = ModList.get().getModFileById(MineCollapseSolarCore.MODID).versionString();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(MineCollapseSolarCore.MODID, "network"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static final MutableInt ID = new MutableInt(0);

    public static void send(PacketDistributor.PacketTarget target, Object message)
    {
        CHANNEL.send(target, message);
    }

    public static void init()
    {
        registerDataManager(Support.Packet.class, Support.MANAGER);
    }

    @SuppressWarnings("unchecked")
    public static <T extends DataManagerSyncPacket<E>, E> void registerDataManager(Class<T> cls, DataManager<E> manager, SimpleChannel channel, int id)
    {
        channel.registerMessage(id, cls,
            (packet, buffer) -> packet.encode(manager, buffer),
            buffer -> {
                final T packet = (T) manager.createEmptyPacket();
                packet.decode(manager, buffer);
                return packet;
            },
            (packet, context) -> {
                context.get().setPacketHandled(true);
                context.get().enqueueWork(() -> packet.handle(context.get(), manager));
            });
    }

    private static <T extends DataManagerSyncPacket<E>, E> void registerDataManager(Class<T> cls, DataManager<E> manager)
    {
        registerDataManager(cls, manager, CHANNEL, ID.getAndIncrement());
    }

}
