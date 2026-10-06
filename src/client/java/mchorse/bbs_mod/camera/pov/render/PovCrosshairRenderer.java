/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.GlStateManager$DstFactor
 *  com.mojang.blaze3d.platform.GlStateManager$SrcFactor
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.render;

import mchorse.bbs_mod.camera.pov.hud.render.AttackIndicatorRenderer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class PovCrosshairRenderer {
    private static final Identifier CROSSHAIR_TEXTURE = new Identifier("hud/crosshair");
    private static final Identifier ATTACK_BACKGROUND = new Identifier("hud/crosshair_attack_indicator_background");
    private static final Identifier ATTACK_PROGRESS = new Identifier("hud/crosshair_attack_indicator_progress");

    private PovCrosshairRenderer() {
    }

    public static void render(Batcher2D batcher, int width, int height) {
        PovCrosshairRenderer.render(batcher, width, height, 1.0f);
    }

    public static void render(Batcher2D batcher, int width, int height, float attackCooldown) {
        DrawContext context = batcher.getContext();
        batcher.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.blendFuncSeparate((GlStateManager.SrcFactor)GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, (GlStateManager.SrcFactor)GlStateManager.SrcFactor.ZERO, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE);
        mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, CROSSHAIR_TEXTURE, (width - 15) / 2, (height - 15) / 2, 15, 15);
        if (AttackIndicatorRenderer.shouldDraw() && attackCooldown >= 0.0f && attackCooldown < 1.0f) {
            int x = width / 2 - 8;
            int y = height / 2 - 7 + 16;
            int fill = (int)(attackCooldown * 17.0f);
            mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, ATTACK_BACKGROUND, x, y, 16, 4);
            mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, ATTACK_PROGRESS, 16, 4, 0, 0, x, y, fill, 4);
        }
        context.draw();
        RenderSystem.blendFuncSeparate((GlStateManager.SrcFactor)GlStateManager.SrcFactor.SRC_ALPHA, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, (GlStateManager.SrcFactor)GlStateManager.SrcFactor.ZERO, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE);
    }
}

