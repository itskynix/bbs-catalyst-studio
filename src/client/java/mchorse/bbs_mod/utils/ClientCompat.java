package mchorse.bbs_mod.utils;

import net.minecraft.client.MinecraftClient;

/**
 * Client API differences between the supported Minecraft versions.
 */
public class ClientCompat
{
    /** How far the player reaches for blocks. */
    public static double getReach(MinecraftClient mc)
    {
        //? if >=1.21 {
        /*return mc.player == null ? 4.5D : mc.player.getBlockInteractionRange();
        *///?} else {
        return mc.interactionManager == null ? 4.5D : mc.interactionManager.getReachDistance();
        //?}
    }
}
