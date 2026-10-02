package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.camera.pov.POVHandRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin
{
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void bbsBeforeRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        if (POVHandRenderer.isRendering() && POVHandRenderer.shouldRenderHand(hand))
        {
            matrices.push();
            POVHandRenderer.applyHandAnimations(matrices, hand);
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void bbsProcessFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        if (POVHandRenderer.isRendering())
        {
            if (!POVHandRenderer.shouldRenderHand(hand))
            {
                ci.cancel();
                return;
            }

            if (hand == Hand.OFF_HAND && item.isEmpty() && !player.isInvisible())
            {
                Arm arm = player.getMainArm().getOpposite();
                ((HeldItemRendererAccessor) this).bbs$invokeRenderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, swingProgress, arm);
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void bbsAfterRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        if (POVHandRenderer.isRendering() && POVHandRenderer.shouldRenderHand(hand))
        {
            matrices.pop();
        }
    }
}
