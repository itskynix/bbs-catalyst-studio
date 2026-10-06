/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ingame.BookEditScreen
 *  net.minecraft.client.util.SelectionManager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BookEditScreenPovAccess;
import java.util.List;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.util.SelectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BookEditScreen.class})
public interface BookEditScreenPovAccessor
extends BookEditScreenPovAccess {
    @Override
    @Accessor(value="pages")
    public List<String> bbsPov$getPages();

    @Override
    @Accessor(value="currentPage")
    public int bbsPov$getCurrentPage();

    @Override
    @Accessor(value="signing")
    public boolean bbsPov$isSigning();

    @Override
    @Accessor(value="title")
    public String bbsPov$getTitle();

    @Override
    @Accessor(value="currentPageSelectionManager")
    public SelectionManager bbsPov$getPageSelection();

    @Override
    @Accessor(value="bookTitleSelectionManager")
    public SelectionManager bbsPov$getTitleSelection();
}

