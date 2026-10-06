/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.item.HeldItemRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Arm
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HeldItemRendererPovAccess;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={HeldItemRenderer.class})
public interface HeldItemRendererPovAccessor
extends HeldItemRendererPovAccess {
    @Override
    @Accessor(value="mainHand")
    public ItemStack bbsPov$getMainHand();

    @Override
    @Accessor(value="mainHand")
    public void bbsPov$setMainHand(ItemStack var1);

    @Override
    @Accessor(value="offHand")
    public ItemStack bbsPov$getOffHand();

    @Override
    @Accessor(value="offHand")
    public void bbsPov$setOffHand(ItemStack var1);

    @Override
    @Accessor(value="equipProgressMainHand")
    public float bbsPov$getEquipProgressMainHand();

    @Override
    @Accessor(value="equipProgressMainHand")
    public void bbsPov$setEquipProgressMainHand(float var1);

    @Override
    @Accessor(value="prevEquipProgressMainHand")
    public float bbsPov$getPrevEquipProgressMainHand();

    @Override
    @Accessor(value="prevEquipProgressMainHand")
    public void bbsPov$setPrevEquipProgressMainHand(float var1);

    @Override
    @Accessor(value="equipProgressOffHand")
    public float bbsPov$getEquipProgressOffHand();

    @Override
    @Accessor(value="equipProgressOffHand")
    public void bbsPov$setEquipProgressOffHand(float var1);

    @Override
    @Accessor(value="prevEquipProgressOffHand")
    public float bbsPov$getPrevEquipProgressOffHand();

    @Override
    @Accessor(value="prevEquipProgressOffHand")
    public void bbsPov$setPrevEquipProgressOffHand(float var1);

    @Override
    @Invoker(value="renderArmHoldingItem")
    public void bbsPov$renderArmHoldingItem(MatrixStack var1, VertexConsumerProvider var2, int var3, float var4, float var5, Arm var6);
}

