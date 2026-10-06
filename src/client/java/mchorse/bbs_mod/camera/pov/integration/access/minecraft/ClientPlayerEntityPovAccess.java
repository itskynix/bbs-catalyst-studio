/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.Hand
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.util.Hand;

public interface ClientPlayerEntityPovAccess {
    public boolean bbsPov$isUsingItem();

    public void bbsPov$setUsingItem(boolean var1);

    public Hand bbsPov$getClientActiveHand();

    public void bbsPov$setClientActiveHand(Hand var1);
}

