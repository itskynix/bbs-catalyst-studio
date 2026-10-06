/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.camera.controller.CameraController
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Arm
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.recording.ScreenEffectRecorder;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovNauseaApplier;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovNightVisionHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovSpyglassZoomHelper;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={GameRenderer.class}, priority=1500)
public abstract class GameRendererPovMixin {
    @Shadow
    private int field_47130;

    @Inject(method={"getNightVisionStrength"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$suppressNightVision(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> info) {
        float povNv = PovNightVisionHelper.resolveNightVisionStrength(tickDelta);
        if (povNv >= 0.0f) {
            info.setReturnValue(Float.valueOf(povNv));
            return;
        }
        if (PovEffectSuppression.isBbsActive()) {
            info.setReturnValue(Float.valueOf(0.0f));
        }
    }

    @Inject(method={"showFloatingItem"}, at={@At(value="HEAD")})
    private void bbsPov$onShowFloatingItem(ItemStack floatingItem, CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean flipped = false;
        if (client.player != null && floatingItem != null) {
            boolean isOffHand = client.player.getOffHandStack().isOf(floatingItem.getItem());
            flipped = client.player.getMainArm() == Arm.LEFT && !isOffHand || client.player.getMainArm() == Arm.RIGHT && isOffHand;
        }
        ScreenEffectRecorder.onFloatingItem(floatingItem, flipped);
    }

    @Inject(method={"renderFloatingItem"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$suppressFloatingItem(int scaledWidth, int scaledHeight, float tickDelta, CallbackInfo info) {
        if (PovEffectSuppression.isBbsActive()) {
            info.cancel();
        }
    }

    @Inject(method={"renderWorld"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/render/GameRenderer;loadProjectionMatrix(Lorg/joml/Matrix4f;)V")})
    private void bbsPov$applyNauseaWorldWobble(float tickDelta, long limitTime, MatrixStack matrixStack, CallbackInfo info) {
        PovNauseaApplier.apply(matrixStack, tickDelta, this.field_47130);
    }

    @Inject(method={"getFov"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$applySpyglassZoom(CallbackInfoReturnable<Double> info) {
        if (PovPlaybackContext.getActive() == null && !PovEffectSuppression.isBbsActive()) {
            return;
        }
        CameraController controller = BBSModClient.getCameraController();
        if (controller != null && controller.getCurrent() != null) {
            return;
        }
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        float zoomMultiplier = PovSpyglassZoomHelper.resolveZoomMultiplier(tickDelta);
        if (zoomMultiplier > 1.0E-4f && Math.abs(zoomMultiplier - 1.0f) > 1.0E-4f) {
            info.setReturnValue((info.getReturnValueD() * (double)zoomMultiplier));
        }
    }
}

