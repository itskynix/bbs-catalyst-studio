/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.passive.AbstractHorseEntity
 *  net.minecraft.inventory.SimpleInventory
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.AbstractHorseEntityPovAccess;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.inventory.SimpleInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={AbstractHorseEntity.class})
public interface AbstractHorseEntityPovAccessor
extends AbstractHorseEntityPovAccess {
    @Override
    @Accessor(value="items")
    public SimpleInventory bbsPov$getItems();

    @Override
    @Invoker(value="updateSaddle")
    public void bbsPov$updateSaddle();

    @Override
    @Invoker(value="setHorseFlag")
    public void bbsPov$setHorseFlag(int var1, boolean var2);
}

