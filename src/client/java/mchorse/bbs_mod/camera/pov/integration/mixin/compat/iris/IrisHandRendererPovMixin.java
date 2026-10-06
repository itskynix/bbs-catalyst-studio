/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  mchorse.bbs_mod.utils.iris.IrisUtils
 *  net.irisshaders.iris.pathways.HandRenderer
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.GameRenderer
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.compat.iris;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.HandState;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandDepthManager;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"net.irisshaders.iris.pathways.HandRenderer"}, remap=false)
public class IrisHandRendererPovMixin {
    @Inject(method={"setupGlState"}, at={@At(value="TAIL")})
    private void bbsPov$fixedHandProjection(CallbackInfo info) {
        HandState state;
        int height;
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getTickDelta();
        if (!PovHandPlayback.isHandActive(tickDelta)) {
            return;
        }
        PovHandPlayback.begin(tickDelta);
        GameRenderer renderer = client.gameRenderer;
        Matrix4f projection = renderer.getBasicProjectionMatrix(70.0);
        int width = BBSRendering.isCustomSize() ? BBSRendering.getVideoWidth() : client.getWindow().getFramebufferWidth();
        int n = height = BBSRendering.isCustomSize() ? BBSRendering.getVideoHeight() : client.getWindow().getFramebufferHeight();
        if (width > 0 && height > 0) {
            projection.m00(projection.m11() / ((float)width / (float)height));
        }
        boolean worldInteraction = (state = PovHandPlayback.getActiveState()) != null && state.worldInteraction;
        boolean replayInteraction = state != null && state.replayInteraction;
        Matrix4f irisProjection = new Matrix4f().scaling(1.0f, 1.0f, 0.125f).mul((Matrix4fc)projection);
        renderer.loadProjectionMatrix(irisProjection);
        PovHandPicking.captureProjection(projection);
        if (!PovHandDepthManager.hasSavedSceneDepth()) {
            PovHandDepthManager.prepareIrisHandDepth(worldInteraction, replayInteraction);
        }
    }

    @Inject(method={"canRender"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$forceCanRenderInFilm(CallbackInfoReturnable<Boolean> info) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getTickDelta();
        if (UIPovHandEditor.isActive()) {
            info.setReturnValue(false);
            return;
        }
        if (PovHandPlayback.isHandActive(tickDelta)) {
            info.setReturnValue(true);
            return;
        }
        if (PovReplaySettings.getFilmPanel() != null || BBSModClient.getCameraController().getCurrent() != null || client.currentScreen instanceof UIScreen) {
            info.setReturnValue(false);
        }
    }

    @Inject(method={"isRenderingSolid"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$overrideIsRenderingSolid(CallbackInfoReturnable<Boolean> info) {
        if (PovHandPicking.isStencilPass() || IrisUtils.isRenderingOffscreen()) {
            info.setReturnValue(true);
        }
    }

    @Inject(method={"isHandTranslucent"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$overrideIsHandTranslucent(CallbackInfoReturnable<Boolean> info) {
        if (PovHandPicking.isStencilPass() || IrisUtils.isRenderingOffscreen()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method={"renderSolid"}, at={@At(value="HEAD")})
    private void bbsPov$beginSolidHand(CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getTickDelta();
        if (PovHandPlayback.isHandActive(tickDelta) && !PovHandPlayback.isActive()) {
            PovHandPlayback.begin(tickDelta);
        }
    }

    @Inject(method={"renderSolid"}, at={@At(value="RETURN")})
    private void bbsPov$endSolidHand(CallbackInfo info) {
        boolean replayInteraction;
        HandState state = PovHandPlayback.getActiveState();
        boolean worldInteraction = state != null && state.worldInteraction;
        boolean bl = replayInteraction = state != null && state.replayInteraction;
        if (PovHandDepthManager.hasSavedSceneDepth() && !HandRenderer.INSTANCE.isAnyHandTranslucent()) {
            PovHandDepthManager.endIrisHandDepth(worldInteraction, replayInteraction);
        }
    }

    @Inject(method={"renderTranslucent"}, at={@At(value="HEAD")})
    private void bbsPov$beginTranslucentHand(CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getTickDelta();
        if (PovHandPlayback.isHandActive(tickDelta) && !PovHandPlayback.isActive()) {
            PovHandPlayback.begin(tickDelta);
        }
    }

    @Inject(method={"renderTranslucent"}, at={@At(value="RETURN")})
    private void bbsPov$endTranslucentHand(CallbackInfo info) {
        boolean replayInteraction;
        HandState state = PovHandPlayback.getActiveState();
        boolean worldInteraction = state != null && state.worldInteraction;
        boolean bl = replayInteraction = state != null && state.replayInteraction;
        if (PovHandDepthManager.hasSavedSceneDepth()) {
            PovHandDepthManager.endIrisHandDepth(worldInteraction, replayInteraction);
        }
        if (PovHandPlayback.isActive()) {
            PovHandPlayback.end();
        }
    }
}

