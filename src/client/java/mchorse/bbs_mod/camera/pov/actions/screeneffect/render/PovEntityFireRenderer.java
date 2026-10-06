/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.FilmControllerContext
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.client.render.Camera
 *  net.minecraft.client.render.OverlayTexture
 *  net.minecraft.client.render.TexturedRenderLayers
 *  net.minecraft.client.render.VertexConsumer
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.model.ModelLoader
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.client.util.math.MatrixStack$Entry
 *  net.minecraft.util.math.RotationAxis
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class PovEntityFireRenderer {
    private PovEntityFireRenderer() {
    }

    public static float resolveFireIntensity(FilmControllerContext context) {
        if (context == null || context.replay == null) {
            return 0.0f;
        }
        ReplayKeyframes replayKeyframes = context.replay.keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return 0.0f;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedPovActions actions = access.bbsPov$getActions();
        if (actions == null) {
            return 0.0f;
        }
        float replayTick = PovEntityFireRenderer.resolveReplayTick(context);
        for (Clip clip : actions.get()) {
            ScreenEffectPovActionClip se;
            if (!(clip instanceof ScreenEffectPovActionClip) || !(se = (ScreenEffectPovActionClip)clip).isActive(replayTick) || !se.hasEffect("fire")) continue;
            float elapsed = se.getLocalTick(replayTick);
            boolean visible = se.fireVisible.isEmpty() ? true : (Boolean)se.fireVisible.interpolate(elapsed);
            return visible ? 1.0f : 0.0f;
        }
        return 0.0f;
    }

    private static float resolveReplayTick(FilmControllerContext context) {
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(context.transition);
        if (playback != null && playback.replay() == context.replay) {
            return playback.replayTick();
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel != null && panel.getData() instanceof Film) {
            int cursor = panel.getCursor();
            boolean playing = panel.getRunner() != null && panel.getRunner().isRunning();
            float transition = playing ? Math.max(0.0f, Math.min(1.0f, context.transition)) : 0.0f;
            return (float)context.replay.getTick(cursor) + transition;
        }
        return (float)context.replay.getTick(0) + context.transition;
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Camera camera, float width, float height, float intensity) {
        if (intensity <= 0.001f || vertexConsumers == null || camera == null) {
            return;
        }
        Sprite sprite0 = ModelLoader.FIRE_0.getSprite();
        Sprite sprite1 = ModelLoader.FIRE_1.getSprite();
        if (sprite0 == null || sprite1 == null) {
            return;
        }
        matrices.push();
        float f = width * 1.4f;
        matrices.scale(f, f, f);
        float g = 0.5f;
        float i = height / f;
        float j = 0.0f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.translate(0.0f, 0.0f, -0.3f + (float)((int)i) * 0.02f);
        float k = 0.0f;
        int l = 0;
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout());
        MatrixStack.Entry entry = matrices.peek();
        while (i > 0.0f) {
            Sprite sprite = l % 2 == 0 ? sprite0 : sprite1;
            float u0 = sprite.getMinU();
            float v0 = sprite.getMinV();
            float u1 = sprite.getMaxU();
            float v1 = sprite.getMaxV();
            if (l / 2 % 2 == 0) {
                float temp = u1;
                u1 = u0;
                u0 = temp;
            }
            PovEntityFireRenderer.drawFireVertex(entry, vertexConsumer, g - 0.0f, 0.0f - j, k, u1, v1, intensity);
            PovEntityFireRenderer.drawFireVertex(entry, vertexConsumer, -g - 0.0f, 0.0f - j, k, u0, v1, intensity);
            PovEntityFireRenderer.drawFireVertex(entry, vertexConsumer, -g - 0.0f, 1.4f - j, k, u0, v0, intensity);
            PovEntityFireRenderer.drawFireVertex(entry, vertexConsumer, g - 0.0f, 1.4f - j, k, u1, v0, intensity);
            i -= 0.45f;
            j -= 0.45f;
            g *= 0.9f;
            k += 0.03f;
            ++l;
        }
        matrices.pop();
    }

    private static void drawFireVertex(MatrixStack.Entry entry, VertexConsumer vertices, float x, float y, float z, float u, float v, float alpha) {
        vertices.vertex(entry.getPositionMatrix(), x, y, z).color(1.0f, 1.0f, 1.0f, alpha).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry.getNormalMatrix(), 0.0f, 1.0f, 0.0f).next();
    }
}

