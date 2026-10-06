/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.BeaconScreen
 *  net.minecraft.entity.effect.StatusEffect
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BeaconScreenPovAccess;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.entity.effect.StatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BeaconScreen.class})
public interface BeaconScreenPovAccessor
extends BeaconScreenPovAccess {
    @Override
    @Accessor(value="primaryEffect")
    public StatusEffect bbsPov$getPrimaryEffect();

    @Override
    @Accessor(value="secondaryEffect")
    public StatusEffect bbsPov$getSecondaryEffect();
}

