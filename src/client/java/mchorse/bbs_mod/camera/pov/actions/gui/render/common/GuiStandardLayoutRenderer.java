/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 *  net.minecraft.text.Text
 *  net.minecraft.util.math.RotationAxis
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.gui.GuiTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiCursorRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiRecipeBookRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

public final class GuiStandardLayoutRenderer {
    private GuiStandardLayoutRenderer() {
    }

    public static void render(GuiRenderContext ctx, GuiScreenChrome chrome) {
        String titleStr;
        if (chrome == null) {
            chrome = GuiScreenChrome.EMPTY;
        }
        MatrixStack matrices = ctx.matrices;
        Batcher2D batcher = ctx.batcher;
        float globalTick = ctx.globalTick;
        float localTick = ctx.localTick;
        int screenWidth = ctx.screenWidth;
        int screenHeight = ctx.screenHeight;
        GuiTypeEntry entry = ctx.entry;
        String guiId = ctx.guiId;
        float scaleX = ctx.scaleX;
        float scaleY = ctx.scaleY;
        float opacity = ctx.opacity;
        float bgOpacity = ctx.bgOpacity;
        float originX = ctx.originX;
        float originY = ctx.originY;
        boolean recipeOpen = ctx.recipeOpen;
        boolean cursorVisible = ctx.cursorVisible;
        float cursorGuiX = ctx.cursorGuiX;
        float cursorGuiY = ctx.cursorGuiY;
        GuiPointerHover pointerHover = ctx.hover;
        int topAlpha = (int)(192.0f * opacity * bgOpacity);
        int bottomAlpha = (int)(208.0f * opacity * bgOpacity);
        if (!ctx.skipEntityPreview && bottomAlpha > 0) {
            int topColor = topAlpha << 24 | 0x101010;
            int bottomColor = bottomAlpha << 24 | 0x101010;
            batcher.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask((boolean)false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            batcher.getContext().fillGradient(RenderLayer.getGuiOverlay(), 0, 0, screenWidth, screenHeight, topColor, bottomColor, 0);
            batcher.flush();
        }
        batcher.flush();
        matrices.push();
        matrices.translate(originX, originY, 0.0f);
        if (ctx.transform != null && ctx.transform.rotate.z != 0.0f) {
            float pivotX = (float)entry.regionWidth * scaleX / 2.0f;
            float pivotY = (float)entry.regionHeight * scaleY / 2.0f;
            matrices.translate(pivotX, pivotY, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(ctx.transform.rotate.z));
            matrices.translate(-pivotX, -pivotY, 0.0f);
        }
        matrices.scale(scaleX, scaleY, 1.0f);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        batcher.getContext().setShaderColor(1.0f, 1.0f, 1.0f, opacity);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)opacity);
        chrome.drawBackground(ctx);
        chrome.drawEarlyChrome(ctx);
        if (recipeOpen) {
            GuiRecipeBookRenderer.render(batcher, ctx.clip, ctx.replayKeyframes, guiId, localTick, globalTick, originX, originY, scaleX, scaleY, screenWidth, screenHeight, opacity, cursorVisible ? cursorGuiX : -1000.0f, cursorVisible ? cursorGuiY : -1000.0f, pointerHover);
        }
        batcher.getContext().draw();
        int titleColor = (int)(255.0f * opacity) << 24 | 0x404040;
        boolean drewTitle = false;
        Text titleText = entry.getTitleText();
        String string = titleStr = titleText != null ? titleText.getString() : "";
        if (!titleStr.isBlank() && !"villager".equals(entry.id)) {
            int tx = entry.titleX;
            if (tx < 0) {
                tx = (entry.regionWidth - MinecraftClient.getInstance().textRenderer.getWidth(titleStr)) / 2;
            }
            batcher.text(titleStr, (float)tx, (float)entry.titleY, titleColor, false);
            drewTitle = true;
        }
        if (entry.inventoryTitleX >= 0) {
            String invText = Text.translatable((String)"container.inventory").getString();
            batcher.text(invText, (float)entry.inventoryTitleX, (float)entry.inventoryTitleY, titleColor, false);
            drewTitle = true;
        }
        if (drewTitle) {
            batcher.flush();
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        DiffuseLighting.enableGuiDepthLighting();
        ItemStack hoveredStack = chrome.renderItems(ctx);
        chrome.drawLateItems(ctx);
        if (recipeOpen) {
            GuiRecipeBookRenderer.renderItems(batcher, ctx.clip, ctx.replayKeyframes, guiId, localTick, globalTick, originX, originY, scaleX, scaleY, screenWidth, screenHeight, cursorVisible ? cursorGuiX : -1000.0f, cursorVisible ? cursorGuiY : -1000.0f, pointerHover);
        }
        if (recipeOpen) {
            ItemStack ghostHover = GuiRecipeBookRenderer.renderGhosts(batcher, ctx.clip, ctx.replayKeyframes, guiId, localTick, globalTick, opacity, cursorVisible ? cursorGuiX : -1000.0f, cursorVisible ? cursorGuiY : -1000.0f);
            if (pointerHover.item == null && ghostHover != null && !ghostHover.isEmpty()) {
                pointerHover.item = ghostHover;
                pointerHover.itemGroup = false;
            }
        }
        if (pointerHover.item != null && !pointerHover.item.isEmpty()) {
            hoveredStack = pointerHover.item;
            pointerHover.itemGroup = false;
        }
        chrome.drawPreview(ctx);
        batcher.flush();
        DiffuseLighting.disableGuiDepthLighting();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        matrices.pop();
        GuiCursorRenderer.renderTooltips(ctx, hoveredStack);
        batcher.getContext().setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
    }
}

