/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.systems.VertexSorter
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.math.RotationAxis
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package mchorse.bbs_mod.camera.pov.render;

import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.hud.HudState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class PovCursorRenderer {
    private PovCursorRenderer() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void render(Batcher2D batcher, HudState state, int width, int height) {
        if (state == null || !state.cursorVisible) {
            return;
        }
        float screenCenterX = (float)width / 2.0f;
        float screenCenterY = (float)height / 2.0f;
        float curScreenX = screenCenterX + state.cursorLayout.translate.x * 2.0f;
        float curScreenY = screenCenterY - state.cursorLayout.translate.y * 2.0f;
        float scaleX = Math.max(0.001f, state.cursorLayout.scale.x);
        float scaleY = Math.max(0.001f, state.cursorLayout.scale.y);
        ItemStack cursorStack = state.cursorItem;
        if (cursorStack != null && !cursorStack.isEmpty()) {
            MatrixStack guiMatrices = batcher.getContext().getMatrices();
            MatrixStack modelView = RenderSystem.getModelViewStack();
            Matrix4f previousProjection = new Matrix4f((Matrix4fc)RenderSystem.getProjectionMatrix());
            guiMatrices.push();
            modelView.push();
            try {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc((int)515);
                RenderSystem.depthMask((boolean)true);
                RenderSystem.clearDepth((double)1.0);
                RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
                DiffuseLighting.enableGuiDepthLighting();
                GuiSlotRenderer.drawSlotItem(batcher, cursorStack, (int)(curScreenX - 8.0f), (int)(curScreenY - 8.0f));
                batcher.flush();
            }
            finally {
                DiffuseLighting.disableGuiDepthLighting();
                modelView.pop();
                RenderSystem.applyModelViewMatrix();
                guiMatrices.pop();
                RenderSystem.setProjectionMatrix((Matrix4f)previousProjection, (VertexSorter)VertexSorter.BY_Z);
                RenderSystem.colorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
                RenderSystem.depthFunc((int)515);
                RenderSystem.clearDepth((double)1.0);
                RenderSystem.enableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask((boolean)false);
            }
        }
        batcher.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        MatrixStack matrices = batcher.getContext().getMatrices();
        matrices.push();
        matrices.translate(curScreenX, curScreenY, 0.0f);
        if (state.cursorLayout.rotate.z != 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(state.cursorLayout.rotate.z));
        }
        float defaultScale = PovSettings.getCursorDefaultScale();
        matrices.scale(scaleX * defaultScale, scaleY * defaultScale, 1.0f);
        PovSettings.renderCursor(batcher);
        batcher.flush();
        matrices.pop();
    }
}

