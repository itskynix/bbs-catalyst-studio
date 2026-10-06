/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.hud.InGameHud
 *  net.minecraft.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.InGameHudHeldItemPovAccess;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={InGameHud.class})
public interface InGameHudHeldItemPovAccessor
extends InGameHudHeldItemPovAccess {
    @Override
    @Accessor(value="heldItemTooltipFade")
    public int bbsPov$getHeldItemTooltipFade();

    @Override
    @Accessor(value="currentStack")
    public ItemStack bbsPov$getHeldItemTooltipStack();
}

