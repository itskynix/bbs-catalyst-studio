/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.utils.iris.IrisUtils
 *  net.minecraft.client.render.Camera
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.render.LightmapTextureManager
 *  net.minecraft.client.render.WorldRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  org.joml.Matrix4f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovBlindnessHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovDarknessHelper;
import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandDepthManager;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={WorldRenderer.class})
public abstract class WorldRendererPovMixin {
    @Inject(method={"renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$suppressSkyAndStarsOnDarkness(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo info) {
        float darkFactor = PovDarknessHelper.resolveDarknessFactor(tickDelta);
        float blindFactor = PovBlindnessHelper.resolveBlindnessFactor(tickDelta);
        if (darkFactor > 0.05f || blindFactor > 0.05f) {
            info.cancel();
        }
    }

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$onRenderWorldStart(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo info) {
        if (BBSRendering.isIrisShadersEnabled() && IrisUtils.isShadowPass()) {
            return;
        }
        if (PovHandPlayback.isHandActive(tickDelta)) {
            PovHandDepthManager.onRenderWorldStart();
        }
    }

    @Inject(method={"render"}, at={@At(value="TAIL")})
    private void bbsPov$onRenderWorldEnd(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo info) {
        if (BBSRendering.isIrisShadersEnabled() && IrisUtils.isShadowPass()) {
            return;
        }
        if (PovHandPlayback.isHandActive(tickDelta)) {
            PovHandDepthManager.onRenderWorldEnd();
        }
    }
}

