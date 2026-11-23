package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.camera.clips.misc.ThirdPersonFade;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves the blocks the third person camera looks through out of vanilla's chunk meshes. */
@Mixin(BlockRenderManager.class)
public class ThirdPersonFadeMixin
{
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void bbs$fadeThin(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull, Random random, CallbackInfo ci)
    {
        if (ThirdPersonFade.isHidden(pos))
        {
            ci.cancel();
        }
    }
}
