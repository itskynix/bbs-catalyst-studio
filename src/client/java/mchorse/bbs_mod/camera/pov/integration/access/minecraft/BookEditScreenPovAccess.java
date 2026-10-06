/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.util.SelectionManager
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import java.util.List;
import net.minecraft.client.util.SelectionManager;

public interface BookEditScreenPovAccess {
    public List<String> bbsPov$getPages();

    public int bbsPov$getCurrentPage();

    public boolean bbsPov$isSigning();

    public String bbsPov$getTitle();

    public SelectionManager bbsPov$getPageSelection();

    public SelectionManager bbsPov$getTitleSelection();
}

