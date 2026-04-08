package net.zerodind.minecollapsesolarcore.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

public class ClientHelpers
{
    public static Level getLevel()
    {
        return Minecraft.getInstance().level;
    }
}
