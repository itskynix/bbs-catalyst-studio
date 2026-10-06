/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.BookScreen
 *  net.minecraft.client.gui.screen.ingame.BookScreen$Contents
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BookScreenPovAccess;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BookScreen.class})
public interface BookScreenPovAccessor
extends BookScreenPovAccess {
    @Override
    @Accessor(value="contents")
    public BookScreen.Contents bbsPov$getContents();

    @Override
    @Accessor(value="pageIndex")
    public int bbsPov$getPageIndex();
}

