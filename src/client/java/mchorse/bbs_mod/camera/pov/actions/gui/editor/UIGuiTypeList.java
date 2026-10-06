/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.list.UIList
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiActionRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

public class UIGuiTypeList
extends UIList<GuiTypeEntry> {
    public static final int ITEM_HEIGHT = 48;
    private static final GuiPovActionClip PREVIEW_CLIP = new GuiPovActionClip();

    public UIGuiTypeList(Consumer<List<GuiTypeEntry>> callback) {
        super(callback);
        this.scroll.scrollItemSize = 48;
    }

    private static <T> void setChannel(KeyframeChannel<T> channel, T value) {
        if (channel == null) {
            return;
        }
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
        } else {
            ((Keyframe)channel.getKeyframes().get(0)).setValue(value);
        }
    }

    protected String elementToString(UIContext context, int index, GuiTypeEntry element) {
        return element == null ? "" : element.name + " " + element.id + " " + element.category;
    }

    protected void renderElementPart(UIContext context, GuiTypeEntry element, int i, int x, int y, boolean hover, boolean selected) {
        if (element == null) {
            return;
        }
        int boxX = x + 4;
        int boxY = y + 3;
        int boxW = 60;
        int boxH = 42;
        context.batcher.box((float)boxX, (float)boxY, (float)(boxX + boxW), (float)(boxY + boxH), -2013265920);
        context.batcher.outline((float)boxX, (float)boxY, (float)(boxX + boxW), (float)(boxY + boxH), selected ? -855638017 : 0x44FFFFFF);
        this.renderPreview(context, element, boxX, boxY, boxW, boxH);
        int iconX = boxX + boxW + 10;
        int iconY = y + 13;
        float iconScale = 1.35f;
        ItemStack itemIcon = element.getIcon();
        if (itemIcon != null && !itemIcon.isEmpty()) {
            try {
                context.batcher.flush();
                MatrixStack matrices = context.batcher.getContext().getMatrices();
                matrices.push();
                matrices.translate((float)iconX, (float)iconY, 100.0f);
                matrices.scale(iconScale, iconScale, 1.0f);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask((boolean)true);
                RenderSystem.depthFunc((int)515);
                RenderSystem.enableCull();
                RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
                DiffuseLighting.enableGuiDepthLighting();
                context.batcher.getContext().drawItem(itemIcon, 0, 0);
                context.batcher.getContext().draw();
                DiffuseLighting.disableGuiDepthLighting();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask((boolean)false);
                RenderSystem.disableCull();
                matrices.pop();
            }
            catch (Exception matrices) {
                // empty catch block
            }
        }
        int textX = iconX + 28;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int titleColor = selected ? 0xFFFFFF : (hover ? 0xFFF0AA : 0xFFFFFF);
        context.batcher.textShadow(element.name, (float)textX, (float)(y + 10), titleColor);
        String catText = element.category != null ? element.category : "GUI";
        int catColor = GuiTypeEntry.getCategoryColor(element.category);
        int nameWidth = textRenderer.getWidth(element.name);
        int badgeX = textX + nameWidth + 6;
        int badgeY = y + 9;
        int badgeW = textRenderer.getWidth(catText) + 6;
        int badgeH = 10;
        context.batcher.box((float)badgeX, (float)badgeY, (float)(badgeX + badgeW), (float)(badgeY + badgeH), catColor & 0xFFFFFF | 0x33000000);
        context.batcher.outline((float)badgeX, (float)badgeY, (float)(badgeX + badgeW), (float)(badgeY + badgeH), catColor & 0xFFFFFF | 0x99000000);
        context.batcher.text(catText, (float)(badgeX + 3), (float)(badgeY + 1), catColor);
        int subtitleColor = selected ? 13691135 : (hover ? 0xB0B0B0 : 0x888888);
        context.batcher.textShadow("minecraft:" + element.id, (float)textX, (float)(y + 25), subtitleColor);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void renderPreview(UIContext context, GuiTypeEntry element, int boxX, int boxY, int boxW, int boxH) {
        int innerPad = 2;
        int maxW = boxW - innerPad * 2;
        int maxH = boxH - innerPad * 2;
        int regW = Math.max(1, element.regionWidth);
        int regH = Math.max(1, element.regionHeight);
        if ("creative_inventory".equals(element.id)) {
            regW = 195;
            regH = 192;
        }
        float scale = Math.min((float)maxW / (float)regW, (float)maxH / (float)regH);
        int drawW = Math.max(1, Math.round((float)regW * scale));
        int drawH = Math.max(1, Math.round((float)regH * scale));
        int drawX = boxX + innerPad + (maxW - drawW) / 2;
        int drawY = boxY + innerPad + (maxH - drawH) / 2;
        UIGuiTypeList.setChannel(UIGuiTypeList.PREVIEW_CLIP.state, element.id);
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getDarknessOpacity(element.id), Float.valueOf(0.0f));
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getCursorVisible(element.id), false);
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getOpacity(element.id), Float.valueOf(1.0f));
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getFurnaceLit(element.id), Float.valueOf(0.0f));
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getFurnaceCook(element.id), Float.valueOf(0.0f));
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getBrewProgress(element.id), Float.valueOf(0.0f));
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getBrewFuel(element.id), Float.valueOf(0.0f));
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getBrewBubbles(element.id), false);
        UIGuiTypeList.setChannel(UIGuiTypeList.PREVIEW_CLIP.anvilName, "");
        UIGuiTypeList.setChannel(PREVIEW_CLIP.getLayout(element.id), new Transform());
        MatrixStack matrices = context.batcher.getContext().getMatrices();
        matrices.push();
        matrices.translate((float)drawX + (float)drawW / 2.0f, (float)drawY + (float)drawH / 2.0f, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate((float)(-regW) / 2.0f, (float)(-regH) / 2.0f, 0.0f);
        context.batcher.flush();
        context.batcher.getContext().enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)515);
        RenderSystem.enableCull();
        RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        try {
            GuiActionRenderer.renderGuiClip(matrices, context.batcher, null, PREVIEW_CLIP, null, 0.0f, regW, regH, false, true);
            context.batcher.getContext().draw();
        }
        catch (Exception exception) {
        }
        finally {
            DiffuseLighting.disableGuiDepthLighting();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask((boolean)false);
            RenderSystem.disableCull();
            context.batcher.flush();
            context.batcher.getContext().disableScissor();
            matrices.pop();
        }
    }

    static {
        UIGuiTypeList.PREVIEW_CLIP.tick.set(0);
        UIGuiTypeList.PREVIEW_CLIP.duration.set(100);
        UIGuiTypeList.PREVIEW_CLIP.enchantBookOpen.insert(0.0f, Float.valueOf(0.0f));
        UIGuiTypeList.PREVIEW_CLIP.enchantPlayerLevel.insert(0.0f, 0);
        UIGuiTypeList.PREVIEW_CLIP.enchantSeed.insert(0.0f, 0);
        UIGuiTypeList.PREVIEW_CLIP.enchantOffers.insert(0.0f, "");
        UIGuiTypeList.PREVIEW_CLIP.gamemodeSelection.insert(0.0f, 0);
        UIGuiTypeList.PREVIEW_CLIP.creativeTab.insert(0.0f, 0);
        UIGuiTypeList.PREVIEW_CLIP.creativePage.insert(0.0f, 0);
        UIGuiTypeList.PREVIEW_CLIP.creativeScroll.insert(0.0f, Float.valueOf(0.0f));
        UIGuiTypeList.PREVIEW_CLIP.creativeRow.insert(0.0f, 0);
        UIGuiTypeList.PREVIEW_CLIP.anvilName.insert(0.0f, "");
    }
}

