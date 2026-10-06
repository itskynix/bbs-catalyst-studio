/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.BookScreen$Contents
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.client.gui.screen.ingame.BookScreen;

public interface BookScreenPovAccess {
    public BookScreen.Contents bbsPov$getContents();

    public int bbsPov$getPageIndex();
}

