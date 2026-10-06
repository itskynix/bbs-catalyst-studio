/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.effect.StatusEffect
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.entity.effect.StatusEffect;

public interface BeaconScreenPovAccess {
    public StatusEffect bbsPov$getPrimaryEffect();

    public StatusEffect bbsPov$getSecondaryEffect();
}

