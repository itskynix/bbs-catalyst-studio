package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker
{
    @Invoker("getAnimationCounter")
    public float bbs$getAnimationCounter(LivingEntity entity, float tickDelta);

    @Invoker("getHandSwingProgress")
    public float bbs$getHandSwingProgress(LivingEntity entity, float tickDelta);

    @Invoker("setupTransforms")
    //? if >=1.21 {
    /*public void bbs$setupTransforms(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale);
    *///?} else {
    public void bbs$setupTransforms(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta);
    //?}

    @Invoker("scale")
    public void bbs$scale(LivingEntity entity, MatrixStack matrices, float tickDelta);
}