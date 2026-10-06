/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.GlStateManager$DstFactor
 *  com.mojang.blaze3d.platform.GlStateManager$SrcFactor
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.systems.VertexSorter
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.colors.Color
 *  net.minecraft.block.Block
 *  net.minecraft.block.Blocks
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.render.BackgroundRenderer
 *  net.minecraft.client.render.BufferBuilder
 *  net.minecraft.client.render.BufferBuilder$BuiltBuffer
 *  net.minecraft.client.render.BufferRenderer
 *  net.minecraft.client.render.CameraSubmersionType
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.render.OverlayTexture
 *  net.minecraft.client.render.Tessellator
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  net.minecraft.client.render.VertexFormat$DrawMode
 *  net.minecraft.client.render.VertexFormats
 *  net.minecraft.client.render.model.ModelLoader
 *  net.minecraft.client.render.model.json.ModelTransformationMode
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.BlockItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.RotationAxis
 *  net.minecraft.world.World
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.ArrayList;
import java.util.Comparator;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ScreenEffectActionRenderer {
    private static final Identifier VIGNETTE_TEX = new Identifier("textures/misc/vignette.png");
    private static final Identifier SPYGLASS_TEX = new Identifier("textures/misc/spyglass_scope.png");
    private static final Identifier FROST_TEX = new Identifier("textures/misc/powder_snow_outline.png");
    private static final Identifier PUMPKIN_TEX = new Identifier("textures/misc/pumpkinblur.png");
    private static final Identifier UNDERWATER_TEX = new Identifier("textures/misc/underwater.png");

    private ScreenEffectActionRenderer() {
    }

    public static void render(MatrixStack matrices, Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height) {
        if (actions == null) {
            return;
        }
        DrawContext context = batcher.getContext();
        if (context == null) {
            return;
        }
        ArrayList<ScreenEffectPovActionClip> activeClips = new ArrayList<ScreenEffectPovActionClip>();
        for (PovActionClip clip : actions.getClips(ScreenEffectPovActionClip.class)) {
            ScreenEffectPovActionClip effectClip;
            if (!(clip instanceof ScreenEffectPovActionClip) || !(effectClip = (ScreenEffectPovActionClip)clip).isActive(tick)) continue;
            activeClips.add(effectClip);
        }
        if (activeClips.isEmpty()) {
            return;
        }
        batcher.flush();
        context.draw();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null || client.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.LAVA) {
            BackgroundRenderer.clearFog();
        }
        ArrayList<ActiveEffect> effectsToRender = new ArrayList<ActiveEffect>();
        for (ScreenEffectPovActionClip clip : activeClips) {
            float elapsed = tick - (float)((Integer)clip.tick.get()).intValue();
            int duration = (Integer)clip.duration.get();
            if (elapsed < 0.0f || elapsed > (float)duration) continue;
            for (String effectId : clip.getActiveEffectList()) {
                effectsToRender.add(new ActiveEffect(clip, effectId, elapsed));
            }
        }
        effectsToRender.sort(Comparator.comparingInt(e -> ScreenEffectActionRenderer.getEffectLayer(e.effectId())));
        for (ActiveEffect effect : effectsToRender) {
            ScreenEffectActionRenderer.renderEffect(context, effect.clip(), effect.effectId(), effect.elapsed(), width, height);
        }
        context.draw();
        batcher.flush();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
    }

    private static int getEffectLayer(String effectId) {
        return switch (effectId.toLowerCase()) {
            case "suffocation" -> 10;
            case "underwater" -> 15;
            case "pumpkin" -> 20;
            case "spyglass" -> 25;
            case "frost" -> 30;
            case "portal" -> 40;
            case "fire" -> 50;
            case "vignette" -> 60;
            case "totem" -> 80;
            default -> 100;
        };
    }

    private static void renderEffect(DrawContext context, ScreenEffectPovActionClip clip, String effectId, float elapsed, int width, int height) {
        switch (effectId.toLowerCase()) {
            case "vignette": {
                ScreenEffectActionRenderer.renderVignette(context, clip, elapsed, width, height);
                break;
            }
            case "underwater": {
                ScreenEffectActionRenderer.renderUnderwater(context, clip, elapsed, width, height);
                break;
            }
            case "night_vision": {
                ScreenEffectActionRenderer.renderNightVision(context, clip, elapsed, width, height);
                break;
            }
            case "blindness": {
                ScreenEffectActionRenderer.renderBlindness(context, clip, elapsed, width, height);
                break;
            }
            case "darkness": {
                ScreenEffectActionRenderer.renderDarkness(context, clip, elapsed, width, height);
                break;
            }
            case "spyglass": {
                ScreenEffectActionRenderer.renderSpyglass(context, clip, elapsed, width, height);
                break;
            }
            case "frost": {
                ScreenEffectActionRenderer.renderFrost(context, clip, elapsed, width, height);
                break;
            }
            case "portal": {
                ScreenEffectActionRenderer.renderPortal(context, clip, elapsed, width, height);
                break;
            }
            case "fire": {
                ScreenEffectActionRenderer.renderFire(context, clip, elapsed, width, height);
                break;
            }
            case "pumpkin": {
                ScreenEffectActionRenderer.renderPumpkin(context, clip, elapsed, width, height);
                break;
            }
            case "suffocation": {
                ScreenEffectActionRenderer.renderSuffocation(context, clip, elapsed, width, height);
                break;
            }
            case "totem": {
                ScreenEffectActionRenderer.renderTotem(context, clip, elapsed, width, height);
            }
        }
    }

    private static void renderVignette(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float intensity;
        boolean isVisible;
        boolean bl = isVisible = clip.vignetteVisible.isEmpty() ? true : (Boolean)clip.vignetteVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = intensity = clip.vignetteOpacity.isEmpty() ? 0.5f : ((Float)clip.vignetteOpacity.interpolate(elapsed)).floatValue();
        if (intensity <= 0.001f) {
            return;
        }
        Color color = clip.vignetteColor.isEmpty() ? null : (Color)clip.vignetteColor.interpolate(elapsed);
        float r = color != null ? color.r : 0.0f;
        float g = color != null ? color.g : 0.0f;
        float b = color != null ? color.b : 0.0f;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate((GlStateManager.SrcFactor)GlStateManager.SrcFactor.ZERO, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, (GlStateManager.SrcFactor)GlStateManager.SrcFactor.SRC_ALPHA, (GlStateManager.DstFactor)GlStateManager.DstFactor.ZERO);
        if (r == 0.0f && g == 0.0f && b == 0.0f) {
            RenderSystem.setShaderColor((float)intensity, (float)intensity, (float)intensity, (float)1.0f);
        } else {
            RenderSystem.setShaderColor((float)(r * intensity), (float)(g * intensity), (float)(b * intensity), (float)1.0f);
        }
        context.drawTexture(VIGNETTE_TEX, 0, 0, width, height, 0.0f, 0.0f, 256, 256, 256, 256);
        context.draw();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.defaultBlendFunc();
    }

    private static void renderUnderwater(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float intensity;
        boolean isVisible;
        if (!ScreenEffectActionRenderer.isFirstPerson()) {
            return;
        }
        boolean bl = isVisible = clip.underwaterVisible.isEmpty() ? true : (Boolean)clip.underwaterVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = intensity = clip.underwaterOpacity.isEmpty() ? 0.1f : ((Float)clip.underwaterOpacity.interpolate(elapsed)).floatValue();
        if (intensity <= 0.001f) {
            return;
        }
        Color tint = (Color)clip.underwaterColor.get();
        float tr = tint != null ? tint.r : 1.0f;
        float tg = tint != null ? tint.g : 1.0f;
        float tb = tint != null ? tint.b : 1.0f;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((float)tr, (float)tg, (float)tb, (float)intensity);
        context.drawTexture(UNDERWATER_TEX, 0, 0, width, height, 0.0f, 0.0f, 256, 256, 256, 256);
        context.draw();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
    }

    private static void renderSpyglass(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        boolean isVisible;
        if (!ScreenEffectActionRenderer.isFirstPerson()) {
            return;
        }
        boolean bl = isVisible = clip.spyglassVisible.isEmpty() ? true : (Boolean)clip.spyglassVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float scale = clip.spyglassScale.isEmpty() ? 1.12f : ((Float)clip.spyglassScale.interpolate(elapsed)).floatValue();
        float f = Math.min(width, height);
        float h = Math.min((float)width / f, (float)height / f) * scale;
        int i = MathHelper.floor((float)(f * h));
        int j = MathHelper.floor((float)(f * h));
        int k = (width - i) / 2;
        int l = (height - j) / 2;
        int m = k + i;
        int n = l + j;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawTexture(SPYGLASS_TEX, k, l, 0, 0.0f, 0.0f, i, j, i, j);
        context.fill(0, n, width, height, -16777216);
        context.fill(0, 0, width, l, -16777216);
        context.fill(0, l, k, n, -16777216);
        context.fill(m, l, width, n, -16777216);
        context.draw();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
    }

    private static void renderFrost(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float intensity;
        boolean isVisible;
        boolean bl = isVisible = clip.frostVisible.isEmpty() ? true : (Boolean)clip.frostVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = intensity = clip.frostProgress.isEmpty() ? 1.0f : ((Float)clip.frostProgress.interpolate(elapsed)).floatValue();
        if (intensity <= 0.001f) {
            return;
        }
        Color tint = (Color)clip.frostColor.get();
        float tr = tint != null ? tint.r : 1.0f;
        float tg = tint != null ? tint.g : 1.0f;
        float tb = tint != null ? tint.b : 1.0f;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((float)tr, (float)tg, (float)tb, (float)intensity);
        context.drawTexture(FROST_TEX, 0, 0, width, height, 0.0f, 0.0f, 256, 256, 256, 256);
        context.draw();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
    }

    private static void renderPortal(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float intensity;
        boolean isVisible;
        boolean bl = isVisible = clip.portalVisible.isEmpty() ? true : (Boolean)clip.portalVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = intensity = clip.portalOpacity.isEmpty() ? 1.0f : ((Float)clip.portalOpacity.interpolate(elapsed)).floatValue();
        if (intensity <= 0.001f) {
            return;
        }
        Sprite sprite = MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(Blocks.NETHER_PORTAL.getDefaultState());
        if (sprite == null) {
            return;
        }
        Color tint = (Color)clip.portalColor.get();
        float tr = tint != null ? tint.r : 1.0f;
        float tg = tint != null ? tint.g : 1.0f;
        float tb = tint != null ? tint.b : 1.0f;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(tr, tg, tb, intensity);
        context.drawSprite(0, 0, 0, width, height, sprite);
        context.draw();
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
    }

    private static void renderFire(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        boolean isVisible;
        if (!ScreenEffectActionRenderer.isFirstPerson()) {
            return;
        }
        boolean bl = isVisible = clip.fireVisible.isEmpty() ? true : (Boolean)clip.fireVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        Sprite sprite = ModelLoader.FIRE_1.getSprite();
        if (sprite == null) {
            return;
        }
        Color tint = (Color)clip.fireColor.get();
        float tr = tint != null ? tint.r : 1.0f;
        float tg = tint != null ? tint.g : 1.0f;
        float tb = tint != null ? tint.b : 1.0f;
        Matrix4f prevProjection = new Matrix4f((Matrix4fc)RenderSystem.getProjectionMatrix());
        float aspect = (float)width / (float)Math.max(1, height);
        Matrix4f fireProjection = new Matrix4f().setPerspective((float)Math.toRadians(70.0), aspect, 0.05f, 100.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)fireProjection, (VertexSorter)VertexSorter.BY_Z);
        MatrixStack fireMatrices = new MatrixStack();
        fireMatrices.loadIdentity();
        RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
        RenderSystem.depthFunc((int)519);
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture((int)0, (Identifier)sprite.getAtlasId());
        float f = sprite.getMinU();
        float g = sprite.getMaxU();
        float h = (f + g) / 2.0f;
        float i = sprite.getMinV();
        float j = sprite.getMaxV();
        float k = (i + j) / 2.0f;
        float l = sprite.getAnimationFrameDelta();
        float m = MathHelper.lerp((float)l, (float)f, (float)h);
        float n = MathHelper.lerp((float)l, (float)g, (float)h);
        float o = MathHelper.lerp((float)l, (float)i, (float)k);
        float p = MathHelper.lerp((float)l, (float)j, (float)k);
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        float alpha = 0.9f;
        for (int r = 0; r < 2; ++r) {
            fireMatrices.push();
            float s = -((float)(r * 2 - 1)) * 0.24f;
            float t = -0.3f;
            float u = 0.0f;
            fireMatrices.translate(s, t, u);
            fireMatrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)(r * 2 - 1) * 10.0f));
            Matrix4f matrix4f = fireMatrices.peek().getPositionMatrix();
            bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
            bufferBuilder.vertex(matrix4f, -0.5f, -0.5f, -0.5f).color(tr, tg, tb, alpha).texture(n, p).next();
            bufferBuilder.vertex(matrix4f, 0.5f, -0.5f, -0.5f).color(tr, tg, tb, alpha).texture(m, p).next();
            bufferBuilder.vertex(matrix4f, 0.5f, 0.5f, -0.5f).color(tr, tg, tb, alpha).texture(m, o).next();
            bufferBuilder.vertex(matrix4f, -0.5f, 0.5f, -0.5f).color(tr, tg, tb, alpha).texture(n, o).next();
            BufferRenderer.drawWithGlobalProgram((BufferBuilder.BuiltBuffer)bufferBuilder.end());
            fireMatrices.pop();
        }
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)515);
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)prevProjection, (VertexSorter)VertexSorter.BY_Z);
    }

    private static void renderPumpkin(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float intensity;
        boolean isVisible;
        boolean bl = isVisible = clip.pumpkinVisible.isEmpty() ? true : (Boolean)clip.pumpkinVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = intensity = clip.pumpkinOpacity.isEmpty() ? 1.0f : ((Float)clip.pumpkinOpacity.interpolate(elapsed)).floatValue();
        if (intensity <= 0.001f) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)intensity);
        context.drawTexture(PUMPKIN_TEX, 0, 0, width, height, 0.0f, 0.0f, 256, 256, 256, 256);
        context.draw();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
    }

    private static void renderSuffocation(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        Sprite sprite;
        Identifier id;
        boolean isVisible;
        boolean bl = isVisible = clip.suffocationVisible.isEmpty() ? true : (Boolean)clip.suffocationVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        String blockId = clip.suffocationBlock.isEmpty() ? "minecraft:stone" : (String)clip.suffocationBlock.interpolate(elapsed);
        Block block = Blocks.STONE;
        if (blockId != null && !blockId.trim().isEmpty() && (id = Identifier.tryParse((String)(blockId.contains(":") ? blockId : "minecraft:" + blockId))) != null) {
            Item item;
            if (Registries.BLOCK.containsId(id)) {
                block = (Block)Registries.BLOCK.get(id);
            } else if (Registries.ITEM.containsId(id) && (item = (Item)Registries.ITEM.get(id)) instanceof BlockItem) {
                BlockItem bi = (BlockItem)item;
                block = bi.getBlock();
            }
        }
        if ((sprite = MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(block.getDefaultState())) == null) {
            return;
        }
        Matrix4f prevProjection = new Matrix4f((Matrix4fc)RenderSystem.getProjectionMatrix());
        float aspect = (float)width / (float)Math.max(1, height);
        Matrix4f suffocationProjection = new Matrix4f().setPerspective((float)Math.toRadians(70.0), aspect, 0.05f, 100.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)suffocationProjection, (VertexSorter)VertexSorter.BY_Z);
        MatrixStack suffocationMatrices = new MatrixStack();
        suffocationMatrices.loadIdentity();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
        RenderSystem.setShaderTexture((int)0, (Identifier)sprite.getAtlasId());
        float l = sprite.getMinU();
        float m = sprite.getMaxU();
        float n = sprite.getMinV();
        float o = sprite.getMaxV();
        float r = 0.1f;
        float g = 0.1f;
        float b = 0.1f;
        float a = 1.0f;
        Matrix4f matrix4f = suffocationMatrices.peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
        builder.vertex(matrix4f, -1.0f, -1.0f, -0.5f).color(r, g, b, a).texture(m, o).next();
        builder.vertex(matrix4f, 1.0f, -1.0f, -0.5f).color(r, g, b, a).texture(l, o).next();
        builder.vertex(matrix4f, 1.0f, 1.0f, -0.5f).color(r, g, b, a).texture(l, n).next();
        builder.vertex(matrix4f, -1.0f, 1.0f, -0.5f).color(r, g, b, a).texture(m, n).next();
        BufferRenderer.drawWithGlobalProgram((BufferBuilder.BuiltBuffer)builder.end());
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)prevProjection, (VertexSorter)VertexSorter.BY_Z);
    }

    private static void renderTotem(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float progress;
        boolean isVisible;
        boolean bl = isVisible = clip.totemVisible.isEmpty() ? true : (Boolean)clip.totemVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = progress = clip.totemProgress.isEmpty() ? 0.0f : ((Float)clip.totemProgress.interpolate(elapsed)).floatValue();
        if (progress <= 1.0E-4f || progress >= 1.0f) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        String itemId = (String)clip.totemItem.get();
        if (itemId == null || itemId.isEmpty()) {
            itemId = "minecraft:totem_of_undying";
        }
        ItemStack stack = ScreenEffectActionRenderer.createItemStack(itemId);
        float f2 = MathHelper.clamp((float)progress, (float)0.0f, (float)1.0f);
        float g = f2 * f2;
        float h = f2 * g;
        float j = 10.25f * h * g - 24.95f * g * g + 25.5f * h - 13.8f * g + 4.0f * f2;
        float k = j * (float)Math.PI;
        boolean flipped = clip.totemFlipped.isEmpty() ? false : (Boolean)clip.totemFlipped.interpolate(elapsed);
        float f10 = (flipped ? 0.5f : -0.5f) * ((float)width / 4.0f);
        float f11 = 0.5f * ((float)height / 4.0f);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate((float)(width / 2) + f10 * MathHelper.abs((float)MathHelper.sin((float)(k * 2.0f))), (float)(height / 2) + f11 * MathHelper.abs((float)MathHelper.sin((float)(k * 2.0f))), -50.0f);
        float scale = 50.0f + 175.0f * MathHelper.sin((float)k);
        matrices.scale(scale, -scale, scale);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((flipped ? -900.0f : 900.0f) * MathHelper.abs((float)MathHelper.sin((float)k))));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f * MathHelper.cos((float)(f2 * 8.0f))));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((flipped ? -6.0f : 6.0f) * MathHelper.cos((float)(f2 * 8.0f))));
        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        client.getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, 0xF000F0, OverlayTexture.DEFAULT_UV, matrices, (VertexConsumerProvider)immediate, (World)client.world, 0);
        immediate.draw();
        matrices.pop();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        DiffuseLighting.disableGuiDepthLighting();
    }

    private static void renderNightVision(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
    }

    private static void renderBlindness(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
        float intensity;
        boolean isVisible;
        if (!ScreenEffectActionRenderer.isFirstPerson()) {
            return;
        }
        boolean bl = isVisible = clip.blindnessVisible.isEmpty() ? true : (Boolean)clip.blindnessVisible.interpolate(elapsed);
        if (!isVisible) {
            return;
        }
        float f = intensity = clip.blindnessOpacity.isEmpty() ? 1.0f : ((Float)clip.blindnessOpacity.interpolate(elapsed)).floatValue();
        if (intensity <= 0.001f) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate((GlStateManager.SrcFactor)GlStateManager.SrcFactor.ZERO, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, (GlStateManager.SrcFactor)GlStateManager.SrcFactor.SRC_ALPHA, (GlStateManager.DstFactor)GlStateManager.DstFactor.ZERO);
        RenderSystem.setShaderColor((float)(intensity * 0.9f), (float)(intensity * 0.9f), (float)(intensity * 0.9f), (float)1.0f);
        context.drawTexture(VIGNETTE_TEX, 0, 0, width, height, 0.0f, 0.0f, 256, 256, 256, 256);
        context.draw();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        RenderSystem.defaultBlendFunc();
    }

    private static void renderDarkness(DrawContext context, ScreenEffectPovActionClip clip, float elapsed, int width, int height) {
    }

    public static boolean isFirstPerson() {
        MinecraftClient client = MinecraftClient.getInstance();
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(client.getTickDelta());
        if (playback != null) {
            if (playback.clip() != null) {
                return playback.clip().isFirstPerson();
            }
            return client.options.getPerspective().isFirstPerson();
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel != null && panel.getData() != null) {
            int povMode = panel.getController().getPovMode();
            if (povMode == 6 || povMode == 3) {
                return true;
            }
            if (povMode == 4 || povMode == 5 || povMode == 1 || povMode == 2) {
                return false;
            }
            float filmTick = panel.getRunner() != null && panel.getRunner().isRunning() ? (float)panel.getRunner().ticks + client.getTickDelta() : (float)panel.getCursor();
            PovCameraClip clip = PovCameraClips.resolve((Film)panel.getData(), filmTick);
            if (clip != null) {
                return clip.isFirstPerson();
            }
        }
        return client.options.getPerspective().isFirstPerson();
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
        return new ItemStack((ItemConvertible)Items.TOTEM_OF_UNDYING);
    }

    private record ActiveEffect(ScreenEffectPovActionClip clip, String effectId, float elapsed) {
    }
}

