/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.graphics.texture.Texture
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.render.BufferBuilder
 *  net.minecraft.client.render.BufferBuilder$BuiltBuffer
 *  net.minecraft.client.render.BufferRenderer
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.render.Tessellator
 *  net.minecraft.client.render.VertexFormat$DrawMode
 *  net.minecraft.client.render.VertexFormats
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 *  org.joml.Matrix4f
 */
package mchorse.bbs_mod.camera.pov.actions.toast.render;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ToastPovActionClip;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public final class ToastActionRenderer {
    private static final Identifier ADVANCEMENT_TEXTURE = new Identifier("toast/advancement");
    private static final Identifier RECIPE_TEXTURE = new Identifier("toast/recipe");
    private static final Identifier SYSTEM_TEXTURE = new Identifier("toast/system");
    private static final Identifier TUTORIAL_TEXTURE = new Identifier("toast/tutorial");

    private ToastActionRenderer() {
    }

    public static void renderHUD(Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height) {
        if (actions == null) {
            return;
        }
        DrawContext context = batcher.getContext();
        if (context == null) {
            return;
        }
        ArrayList<ToastPovActionClip> activeClips = new ArrayList<ToastPovActionClip>();
        for (PovActionClip clip : actions.getClips(ToastPovActionClip.class)) {
            ToastPovActionClip toastClip;
            if (!(clip instanceof ToastPovActionClip) || !(toastClip = (ToastPovActionClip)clip).isActive(tick)) continue;
            activeClips.add(toastClip);
        }
        if (activeClips.isEmpty()) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int baseLayer = PovActionType.TOASTS.seedLayer();
        for (ToastPovActionClip clip : activeClips) {
            float slide;
            float elapsed = tick - (float)((Integer)clip.tick.get()).intValue();
            int duration = (Integer)clip.duration.get();
            if (elapsed < 0.0f || elapsed > (float)duration || (slide = ToastActionRenderer.calculateSlideProgress(elapsed, duration)) <= 0.001f) continue;
            int toastW = 160;
            int toastH = 32;
            int x = width - (int)((float)toastW * slide);
            int slot = Math.max(0, Math.min(4, (Integer)clip.layer.get() - baseLayer));
            int y = 4 + slot * 34;
            ToastActionRenderer.renderSingleToast(context, font, clip, x, y);
        }
    }

    public static float calculateSlideProgress(float elapsed, int duration) {
        int slideOut;
        if (duration <= 0) {
            return 0.0f;
        }
        int slideIn = Math.max(4, Math.min(15, Math.round((float)duration * 0.15f)));
        if (duration < slideIn + (slideOut = Math.max(4, Math.min(15, Math.round((float)duration * 0.15f))))) {
            slideIn = duration / 2;
            slideOut = duration - slideIn;
        }
        if (elapsed < (float)slideIn) {
            float p = elapsed / (float)Math.max(1, slideIn);
            return MathHelper.clamp((float)(p * p), (float)0.0f, (float)1.0f);
        }
        if (elapsed > (float)(duration - slideOut)) {
            float p = ((float)duration - elapsed) / (float)Math.max(1, slideOut);
            return MathHelper.clamp((float)(p * p), (float)0.0f, (float)1.0f);
        }
        return 1.0f;
    }

    public static void renderSingleToast(DrawContext context, TextRenderer font, ToastPovActionClip clip, int x, int y) {
        String frameType = clip.getEffectiveFrameType();
        Identifier texture = ToastActionRenderer.getTextureForFrameType(frameType);
        RenderSystem.enableBlend();
        mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, texture, x, y, 160, 32);
        Link customTex = clip.getCustomTexture();
        if (customTex != null) {
            Texture tex = BBSModClient.getTextures().getTexture(customTex);
            if (tex != null && tex.isValid() && tex.id > 0) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderTexture((int)0, (int)tex.id);
                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
                BufferBuilder builder = Tessellator.getInstance().getBuffer();
                builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                builder.vertex(matrix, (float)(x + 8), (float)(y + 8 + 16), 0.0f).texture(0.0f, 1.0f).next();
                builder.vertex(matrix, (float)(x + 8 + 16), (float)(y + 8 + 16), 0.0f).texture(1.0f, 1.0f).next();
                builder.vertex(matrix, (float)(x + 8 + 16), (float)(y + 8), 0.0f).texture(1.0f, 0.0f).next();
                builder.vertex(matrix, (float)(x + 8), (float)(y + 8), 0.0f).texture(0.0f, 0.0f).next();
                BufferRenderer.drawWithGlobalProgram((BufferBuilder.BuiltBuffer)builder.end());
            }
        } else {
            String iconId = clip.getEffectiveIcon();
            ItemStack stack = ToastActionRenderer.createItemStack(iconId);
            if ("recipe".equalsIgnoreCase(frameType)) {
                context.getMatrices().push();
                context.getMatrices().translate((float)x, (float)y, 0.0f);
                context.getMatrices().scale(0.6f, 0.6f, 1.0f);
                ToastActionRenderer.renderItem(context, new ItemStack((ItemConvertible)Items.CRAFTING_TABLE), 3, 3);
                context.getMatrices().pop();
            }
            ToastActionRenderer.renderItem(context, stack, x + 8, y + 8);
        }
        Text titleText = clip.getEffectiveTitleText();
        Text descText = clip.getEffectiveDescriptionText();
        int titleColor = ToastActionRenderer.getTitleColor(frameType);
        int descColor = "recipe".equalsIgnoreCase(frameType) ? -16777216 : -1;
        context.drawText(font, titleText, x + 30, y + 7, titleColor, false);
        context.drawText(font, descText, x + 30, y + 18, descColor, false);
    }

    private static void renderItem(DrawContext context, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc((int)515);
        RenderSystem.depthMask((boolean)true);
        context.drawItem(stack, x, y);
        RenderSystem.disableDepthTest();
    }

    private static Identifier getTextureForFrameType(String frameType) {
        if ("recipe".equalsIgnoreCase(frameType)) {
            return RECIPE_TEXTURE;
        }
        if ("tutorial".equalsIgnoreCase(frameType)) {
            return TUTORIAL_TEXTURE;
        }
        if ("system".equalsIgnoreCase(frameType)) {
            return SYSTEM_TEXTURE;
        }
        return ADVANCEMENT_TEXTURE;
    }

    private static int getTitleColor(String frameType) {
        if ("challenge".equalsIgnoreCase(frameType)) {
            return -43521;
        }
        if ("goal".equalsIgnoreCase(frameType)) {
            return -11141121;
        }
        if ("recipe".equalsIgnoreCase(frameType)) {
            return -11534256;
        }
        if ("system".equalsIgnoreCase(frameType)) {
            return -1;
        }
        return -171;
    }

    private static ItemStack createItemStack(String iconId) {
        if (iconId != null && !iconId.isEmpty()) {
            try {
                Item item = (Item)Registries.ITEM.get(new Identifier(iconId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack((ItemConvertible)item);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return new ItemStack((ItemConvertible)Items.DIAMOND);
    }
}

