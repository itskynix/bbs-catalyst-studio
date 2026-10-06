/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class BrewingGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final BrewingGuiRenderer INSTANCE = new BrewingGuiRenderer();
    private static final Identifier BREWING_FUEL = new Identifier("container/brewing_stand/fuel_length");
    private static final Identifier BREWING_ARROW = new Identifier("container/brewing_stand/brew_progress");
    private static final Identifier BREWING_BUBBLES = new Identifier("container/brewing_stand/bubbles");
    private static final int[] BREWING_BUBBLE_FRAMES = new int[]{0, 6, 11, 16, 20, 24, 29};

    private BrewingGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        BrewingGuiRenderer.drawProgress(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick);
    }

    private static void drawProgress(Batcher2D batcher, GuiPovActionClip clip, String guiId, float tick) {
        int frame;
        int bubbleH;
        int arrowH;
        float fuel = MathHelper.clamp((float)GuiTextRenderer.sampleFloat(clip.getBrewFuel(guiId), tick, 0.0f), (float)0.0f, (float)1.0f);
        float brew = MathHelper.clamp((float)GuiTextRenderer.sampleFloat(clip.getBrewProgress(guiId), tick, 0.0f), (float)0.0f, (float)1.0f);
        boolean bubbles = GuiTextRenderer.sampleBool(clip.getBrewBubbles(guiId), tick, false);
        int fuelW = MathHelper.clamp((int)Math.round(fuel * 18.0f), (int)0, (int)18);
        if (fuelW > 0) {
            batcher.drawGuiTexture(BREWING_FUEL, 18, 4, 0, 0, 60, 44, fuelW, 4);
        }
        if ((arrowH = MathHelper.clamp((int)Math.round(brew * 28.0f), (int)0, (int)28)) > 0) {
            batcher.drawGuiTexture(BREWING_ARROW, 9, 28, 0, 0, 97, 16, 9, arrowH);
        }
        if (bubbles && (bubbleH = BREWING_BUBBLE_FRAMES[frame = Math.floorMod((int)tick / 2, BREWING_BUBBLE_FRAMES.length)]) > 0) {
            batcher.drawGuiTexture(BREWING_BUBBLES, 12, 29, 0, 29 - bubbleH, 63, 43 - bubbleH, 12, bubbleH);
        }
    }
}

