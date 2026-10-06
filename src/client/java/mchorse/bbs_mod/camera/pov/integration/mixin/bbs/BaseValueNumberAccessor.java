/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValueNumber
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BaseValueNumber.class}, remap=false)
public interface BaseValueNumberAccessor {
    @Accessor(value="max")
    public void bbsPov$setMaximum(Number var1);
}

