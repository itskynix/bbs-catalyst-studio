/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Arm
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

public interface HeldItemRendererPovAccess {
    public ItemStack bbsPov$getMainHand();

    public void bbsPov$setMainHand(ItemStack var1);

    public ItemStack bbsPov$getOffHand();

    public void bbsPov$setOffHand(ItemStack var1);

    public float bbsPov$getEquipProgressMainHand();

    public void bbsPov$setEquipProgressMainHand(float var1);

    public float bbsPov$getPrevEquipProgressMainHand();

    public void bbsPov$setPrevEquipProgressMainHand(float var1);

    public float bbsPov$getEquipProgressOffHand();

    public void bbsPov$setEquipProgressOffHand(float var1);

    public float bbsPov$getPrevEquipProgressOffHand();

    public void bbsPov$setPrevEquipProgressOffHand(float var1);

    public void bbsPov$renderArmHoldingItem(MatrixStack var1, VertexConsumerProvider var2, int var3, float var4, float var5, Arm var6);
}

