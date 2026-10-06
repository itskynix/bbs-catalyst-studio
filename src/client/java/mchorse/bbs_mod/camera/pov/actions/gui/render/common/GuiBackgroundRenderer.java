/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.gui.GuiTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

public final class GuiBackgroundRenderer {
    private GuiBackgroundRenderer() {
    }

    public static void drawDefault(GuiRenderContext ctx) {
        Batcher2D batcher = ctx.batcher;
        GuiTypeEntry entry = ctx.entry;
        if ("chest".equals(entry.id) || "barrel".equals(entry.id) || "ender_chest".equals(entry.id)) {
            batcher.getContext().drawTexture(entry.texture, 0, 0, 0.0f, 0.0f, 176, 71, 256, 256);
            batcher.getContext().drawTexture(entry.texture, 0, 71, 0.0f, 126.0f, 176, 96, 256, 256);
        } else if ("book".equals(entry.id)) {
            batcher.getContext().drawTexture(entry.texture, 0, 0, 0.0f, 0.0f, 192, 192, 256, 256);
        } else {
            batcher.getContext().drawTexture(entry.texture, 0, 0, (float)entry.u, (float)entry.v, entry.regionWidth, entry.regionHeight, entry.textureWidth, entry.textureHeight);
        }
    }
}

