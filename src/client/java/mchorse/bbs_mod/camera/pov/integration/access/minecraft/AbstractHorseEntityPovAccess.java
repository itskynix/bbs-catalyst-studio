/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.inventory.SimpleInventory
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.inventory.SimpleInventory;

public interface AbstractHorseEntityPovAccess {
    public SimpleInventory bbsPov$getItems();

    public void bbsPov$updateSaddle();

    public void bbsPov$setHorseFlag(int var1, boolean var2);
}

