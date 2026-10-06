/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.item.ItemStack;

public interface LivingEntityPovAccess {
    public ItemStack bbsPov$getActiveItemStack();

    public void bbsPov$setActiveItemStack(ItemStack var1);

    public int bbsPov$getItemUseTimeLeft();

    public void bbsPov$setItemUseTimeLeft(int var1);

    public void bbsPov$setLivingFlag(int var1, boolean var2);
}

