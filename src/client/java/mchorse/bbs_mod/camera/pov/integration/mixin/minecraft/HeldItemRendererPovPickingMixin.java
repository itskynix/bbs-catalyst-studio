/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  mchorse.bbs_mod.utils.MatrixStackUtils
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.AbstractClientPlayerEntity
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  net.minecraft.client.render.WorldRenderer
 *  net.minecraft.client.render.item.HeldItemRenderer
 *  net.minecraft.client.render.model.BakedModel
 *  net.minecraft.client.render.model.json.ModelTransformationMode
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Arm
 *  net.minecraft.util.Hand
 *  net.minecraft.util.UseAction
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.world.BlockRenderView
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.hand.HandState;
import mchorse.bbs_mod.camera.pov.hand.PovItemPose;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandGizmo;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.HeldItemRendererPovAccessor;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={HeldItemRenderer.class})
public class HeldItemRendererPovPickingMixin {
    @Unique
    private boolean bbsPov$startedPlayback;
    @Unique
    private boolean bbsPov$itemPosePushed;

    @ModifyVariable(method={"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private int bbsPov$cameraLight(int original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && PovHandPlayback.isHandActive(client.getTickDelta())) {
            return WorldRenderer.getLightmapCoordinates((BlockRenderView)client.world, (BlockPos)client.gameRenderer.getCamera().getBlockPos());
        }
        return original;
    }

    @ModifyVariable(method={"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private float bbsPov$lockRenderItemTickDelta(float originalTickDelta) {
        if (PovHandPlayback.isActive()) {
            return PovHandPlayback.getCurrentItemRenderTickDelta(originalTickDelta);
        }
        return originalTickDelta;
    }

    @ModifyVariable(method={"renderFirstPersonItem"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private float bbsPov$lockRenderFirstPersonItemTickDelta(float originalTickDelta) {
        if (PovHandPlayback.isActive()) {
            return PovHandPlayback.getCurrentItemRenderTickDelta(originalTickDelta);
        }
        return originalTickDelta;
    }

    @Inject(method={"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$beginItemPlayback(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light, CallbackInfo info) {
        if (!PovHandPlayback.isActive()) {
            this.bbsPov$startedPlayback = PovHandPlayback.begin(tickDelta);
            if (this.bbsPov$startedPlayback) {
                matrices.push();
                matrices.loadIdentity();
                PovHandPlayback.applyTransforms(matrices);
            } else if (PovPlaybackContext.getActive() != null || PovReplaySettings.getFilmPanel() != null || BBSModClient.getCameraController().getCurrent() != null || MinecraftClient.getInstance().currentScreen instanceof UIScreen) {
                info.cancel();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"}, at={@At(value="RETURN")})
    private void bbsPov$endItemPlayback(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light, CallbackInfo info) {
        if (PovHandPlayback.isActive()) {
            try {
                consumers.draw();
                if (PovHandPicking.isStencilPass()) {
                    PovHandPlayback.renderBodyPartsForPicking(light, PovHandPicking.getStencilMap());
                } else {
                    PovHandPlayback.renderBodyParts(light);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (this.bbsPov$startedPlayback) {
            try {
                PovHandPlayback.popTransforms(matrices);
                matrices.pop();
                PovHandGizmo.captureVisual();
                PovHandPlayback.end();
            }
            finally {
                this.bbsPov$startedPlayback = false;
            }
        }
    }

    @ModifyVariable(method={"renderFirstPersonItem"}, at=@At(value="HEAD"), argsOnly=true, ordinal=2)
    private float bbsPov$overrideSwingProgress(float originalSwingProgress, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand) {
        HandState state;
        if (PovHandPlayback.isActive() && (state = PovHandPlayback.getActiveState()) != null) {
            Arm arm = hand == Hand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
            return arm == Arm.RIGHT ? state.rightSwingProgress : state.leftSwingProgress;
        }
        return originalSwingProgress;
    }

    @Inject(method={"renderFirstPersonItem"}, at={@At(value="HEAD")})
    private void bbsPov$beforeRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        if (PovHandPlayback.isActive()) {
            matrices.push();
            PovHandPlayback.applyHandAnimations(matrices, hand);
        }
    }

    @Inject(method={"renderFirstPersonItem"}, at={@At(value="RETURN")})
    private void bbsPov$afterRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        if (PovHandPlayback.isActive()) {
            matrices.pop();
        }
    }

    @Inject(method={"renderFirstPersonItem"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$renderEmptyOffHand(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        if (!PovHandPlayback.isActive() || hand != Hand.OFF_HAND || !item.isEmpty() || player.isInvisible()) {
            return;
        }
        Arm arm = player.getMainArm().getOpposite();
        if (PovHandPlayback.shouldRenderArm(arm)) {
            ((HeldItemRendererPovAccessor)(this)).bbsPov$renderArmHoldingItem(matrices, consumers, light, equipProgress, swingProgress, arm);
            info.cancel();
        }
    }

    @Redirect(method={"renderFirstPersonItem"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingSpyglass()Z"))
    private boolean bbsPov$neverHideSpyglassHand(AbstractClientPlayerEntity player) {
        if (PovHandPlayback.isActive()) {
            return false;
        }
        return player.isUsingSpyglass();
    }

    @Redirect(method={"renderFirstPersonItem"}, at=@At(value="INVOKE", target="Lnet/minecraft/item/ItemStack;getUseAction()Lnet/minecraft/util/UseAction;"))
    private UseAction bbsPov$overrideUseAction(ItemStack stack) {
        HandState state;
        UseAction original = stack.getUseAction();
        if (PovHandPlayback.isActive() && (state = PovHandPlayback.getActiveState()) != null && state.usingItem && original == UseAction.NONE) {
            return UseAction.EAT;
        }
        return original;
    }

    @Redirect(method={"applyEatOrDrinkTransformation"}, at=@At(value="INVOKE", target="Lnet/minecraft/item/ItemStack;getMaxUseTime()I"))
    private int bbsPov$overrideMaxUseTime(ItemStack stack) {
        int original = stack.getMaxUseTime();
        if (PovHandPlayback.isActive() && original <= 0) {
            return 32;
        }
        return original;
    }

    @Inject(method={"renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"}, at={@At(value="HEAD")})
    private void bbsPov$applyItemPose(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        this.bbsPov$itemPosePushed = false;
        if (!PovHandPlayback.isActive() || PovHandPicking.isStencilPass()) {
            return;
        }
        String bone = PovHandPlayback.getItemBone(leftHanded);
        PoseTransform transform = PovHandPlayback.getItemPose(leftHanded);
        Matrix4f origin = new Matrix4f((Matrix4fc)matrices.peek().getPositionMatrix());
        matrices.push();
        PovItemPose.apply(matrices, transform);
        this.bbsPov$itemPosePushed = true;
        PovHandMatrices.captureItem(bone, origin, matrices.peek().getPositionMatrix());
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getItemRenderer().getModel(stack, entity.getWorld(), entity, entity.getId());
        MatrixStack itemBounds = new MatrixStack();
        MatrixStackUtils.multiply((MatrixStack)itemBounds, (Matrix4f)matrices.peek().getPositionMatrix());
        model.getTransformation().getTransformation(mode).apply(leftHanded, itemBounds);
        itemBounds.translate(-0.5f, -0.5f, -0.5f);
        PovHandPicking.captureItemBounds(bone, itemBounds.peek().getPositionMatrix());
    }

    @Inject(method={"renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"}, at={@At(value="RETURN")})
    private void bbsPov$restoreItemPose(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        if (this.bbsPov$itemPosePushed) {
            matrices.pop();
            this.bbsPov$itemPosePushed = false;
        }
    }

    @Inject(method={"renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideItemFromHandStencil(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        if (PovHandPicking.isStencilPass()) {
            info.cancel();
        }
    }
}

