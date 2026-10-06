/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.passive.HorseEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HorseEntityPovAccess;
import net.minecraft.entity.passive.HorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={HorseEntity.class})
public interface HorseEntityPovAccessor
extends HorseEntityPovAccess {
    @Override
    @Invoker(value="setHorseVariant")
    public void bbsPov$setHorseVariant(int var1);
}

