/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render;

import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiBackgroundRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import net.minecraft.item.ItemStack;

public interface GuiScreenChrome {
    public static final GuiScreenChrome EMPTY = new GuiScreenChrome(){};

    default public void drawEarlyChrome(GuiRenderContext ctx) {
    }

    default public void drawBackground(GuiRenderContext ctx) {
        GuiBackgroundRenderer.drawDefault(ctx);
    }

    default public ItemStack renderItems(GuiRenderContext ctx) {
        return GuiSlotRenderer.renderDefault(ctx);
    }

    default public void drawLateItems(GuiRenderContext ctx) {
    }

    default public void drawPreview(GuiRenderContext ctx) {
    }
}

