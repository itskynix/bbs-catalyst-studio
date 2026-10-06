/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.MerchantScreen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.MerchantScreenPovAccess;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={MerchantScreen.class})
public interface MerchantScreenPovAccessor
extends MerchantScreenPovAccess {
    @Override
    @Accessor(value="selectedIndex")
    public int bbsPov$getSelectedIndex();

    @Override
    @Accessor(value="indexStartOffset")
    public int bbsPov$getIndexStartOffset();
}

