/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.advancement.AdvancementEntry
 *  net.minecraft.client.toast.AdvancementToast
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.toast.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={AdvancementToast.class})
public interface AdvancementToastPovAccessor {
    @Accessor(value="advancement")
    public AdvancementEntry bbsPov$getAdvancement();
}

