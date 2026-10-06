/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.util.math.MatrixStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRendererRegistry;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiEntityPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.statuseffect.render.StatusEffectActionRenderer;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.util.math.MatrixStack;

public final class GuiActionRenderer {
    static final GuiScreenChrome FALLBACK_CHROME = new GuiScreenChrome(){

        @Override
        public void drawPreview(GuiRenderContext ctx) {
            if ("inventory".equals(ctx.guiId)) {
                GuiEntityPreviewRenderer.drawInventoryPreview(ctx, false);
            }
        }
    };

    private GuiActionRenderer() {
    }

    public static void render(MatrixStack matrices, Batcher2D batcher, ReplayKeyframes replayKeyframes, RecordedPovActions actions, RecordedHandData handData, float tick, int screenWidth, int screenHeight, boolean allowCursor) {
        if (actions == null) {
            return;
        }
        GuiPovActionClip guiClip = actions.getActiveGui(tick);
        if (guiClip == null) {
            return;
        }
        GuiActionRenderer.renderGuiClip(matrices, batcher, replayKeyframes, guiClip, handData, tick, screenWidth, screenHeight, allowCursor);
        GuiRenderContext ctx = GuiRenderContext.create(matrices, batcher, replayKeyframes, guiClip, handData, tick, screenWidth, screenHeight, allowCursor);
        if (ctx != null && batcher.getContext() != null && ("inventory".equals(ctx.guiId) || "creative_inventory".equals(ctx.guiId))) {
            StatusEffectActionRenderer.renderInventorySidebar(batcher.getContext(), actions, tick, Math.round(ctx.originX), Math.round(ctx.originY), ctx.entry != null ? ctx.entry.regionWidth : 176, ctx.entry != null ? ctx.entry.regionHeight : 166);
        }
    }

    public static void renderGuiClip(MatrixStack matrices, Batcher2D batcher, ReplayKeyframes replayKeyframes, GuiPovActionClip clip, RecordedHandData handData, float globalTick, int screenWidth, int screenHeight) {
        GuiActionRenderer.renderGuiClip(matrices, batcher, replayKeyframes, clip, handData, globalTick, screenWidth, screenHeight, true);
    }

    public static void renderGuiClip(MatrixStack matrices, Batcher2D batcher, ReplayKeyframes replayKeyframes, GuiPovActionClip clip, RecordedHandData handData, float globalTick, int screenWidth, int screenHeight, boolean allowCursor) {
        GuiActionRenderer.renderGuiClip(matrices, batcher, replayKeyframes, clip, handData, globalTick, screenWidth, screenHeight, allowCursor, false);
    }

    public static void renderGuiClip(MatrixStack matrices, Batcher2D batcher, ReplayKeyframes replayKeyframes, GuiPovActionClip clip, RecordedHandData handData, float globalTick, int screenWidth, int screenHeight, boolean allowCursor, boolean skipEntityPreview) {
        GuiRenderContext ctx = GuiRenderContext.create(matrices, batcher, replayKeyframes, clip, handData, globalTick, screenWidth, screenHeight, allowCursor);
        if (ctx == null) {
            return;
        }
        ctx.skipEntityPreview = skipEntityPreview;
        GuiRendererRegistry.get(ctx.guiId).render(ctx);
    }

    static void renderFallback(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, FALLBACK_CHROME);
    }
}

