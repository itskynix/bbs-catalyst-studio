/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.film.BaseFilmController
 *  mchorse.bbs_mod.utils.iris.IrisUtils
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandDepthManager;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BaseFilmController.class}, remap=false)
public class BaseFilmControllerDepthPovMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$captureWorldBeforeReplays(WorldRenderContext context, CallbackInfo info) {
        float tickDelta;
        if (BBSRendering.isIrisShadersEnabled() && IrisUtils.isShadowPass()) {
            return;
        }
        float f = tickDelta = context != null ? context.tickDelta() : 0.0f;
        if (PovHandPlayback.isHandActive(tickDelta)) {
            PovHandDepthManager.onBeforeReplayRender();
        }
    }

    @Inject(method={"render"}, at={@At(value="TAIL")})
    private void bbsPov$captureReplayDepthAfterReplays(WorldRenderContext context, CallbackInfo info) {
        float tickDelta;
        if (BBSRendering.isIrisShadersEnabled() && IrisUtils.isShadowPass()) {
            return;
        }
        float f = tickDelta = context != null ? context.tickDelta() : 0.0f;
        if (PovHandPlayback.isHandActive(tickDelta)) {
            VertexConsumerProvider vertexConsumerProvider;
            if (context != null && (vertexConsumerProvider = context.consumers()) instanceof VertexConsumerProvider.Immediate) {
                VertexConsumerProvider.Immediate immediate = (VertexConsumerProvider.Immediate)vertexConsumerProvider;
                immediate.draw();
            }
            PovHandDepthManager.captureReplayDepth();
        }
    }
}

