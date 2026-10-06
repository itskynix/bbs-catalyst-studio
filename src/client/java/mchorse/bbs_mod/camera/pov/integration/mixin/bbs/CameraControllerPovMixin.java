/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.camera.Camera
 *  mchorse.bbs_mod.camera.controller.CameraController
 *  mchorse.bbs_mod.camera.controller.ICameraController
 *  net.minecraft.client.MinecraftClient
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovSpyglassZoomHelper;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={CameraController.class}, remap=false)
public class CameraControllerPovMixin {
    @Inject(method={"getCurrent"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$noControllerInHandEditor(CallbackInfoReturnable<ICameraController> info) {
        if (UIPovHandEditor.isActive()) {
            info.setReturnValue(null);
        }
    }

    @Inject(method={"setup"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$noSetupInHandEditor(Camera camera, float tickDelta, CallbackInfo info) {
        if (UIPovHandEditor.isActive()) {
            info.cancel();
        }
    }

    @Inject(method={"getFOV"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$applySpyglassZoom(CallbackInfoReturnable<Double> info) {
        if (PovPlaybackContext.getActive() == null && !PovEffectSuppression.isBbsActive()) {
            return;
        }
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        float zoomMultiplier = PovSpyglassZoomHelper.resolveZoomMultiplier(tickDelta);
        if (zoomMultiplier > 1.0E-4f && Math.abs(zoomMultiplier - 1.0f) > 1.0E-4f) {
            info.setReturnValue((info.getReturnValueD() * (double)zoomMultiplier));
        }
    }
}

