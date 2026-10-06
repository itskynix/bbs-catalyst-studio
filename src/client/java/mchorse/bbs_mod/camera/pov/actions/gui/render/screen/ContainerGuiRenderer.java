/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiEntityPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiRecipeBookRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;

public final class ContainerGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final ContainerGuiRenderer INSTANCE = new ContainerGuiRenderer();

    private ContainerGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorY;
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float f = cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        if ("inventory".equals(ctx.guiId)) {
            GuiRecipeBookRenderer.drawRecipeButton(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick, 104, 61, cursorX, cursorY);
        } else if ("crafting_table".equals(ctx.guiId)) {
            GuiRecipeBookRenderer.drawRecipeButton(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick, 5, 34, cursorX, cursorY);
        }
    }

    @Override
    public void drawPreview(GuiRenderContext ctx) {
        if ("inventory".equals(ctx.guiId)) {
            GuiEntityPreviewRenderer.drawInventoryPreview(ctx, false);
        }
    }
}

