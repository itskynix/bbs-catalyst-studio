/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.entity.Entity
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

public interface InGameHudVignettePovAccess {
    public void bbsPov$renderVignetteOverlay(DrawContext var1, Entity var2);

    public float bbsPov$getVignetteDarkness();

    public float bbsPov$getSpyglassScale();
}

