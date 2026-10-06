/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.LivingEntityPovAccess;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={LivingEntity.class})
public interface LivingEntityPovAccessor
extends LivingEntityPovAccess {
    @Override
    @Accessor(value="activeItemStack")
    public ItemStack bbsPov$getActiveItemStack();

    @Override
    @Accessor(value="activeItemStack")
    public void bbsPov$setActiveItemStack(ItemStack var1);

    @Override
    @Accessor(value="itemUseTimeLeft")
    public int bbsPov$getItemUseTimeLeft();

    @Override
    @Accessor(value="itemUseTimeLeft")
    public void bbsPov$setItemUseTimeLeft(int var1);

    @Override
    @Invoker(value="setLivingFlag")
    public void bbsPov$setLivingFlag(int var1, boolean var2);
}

