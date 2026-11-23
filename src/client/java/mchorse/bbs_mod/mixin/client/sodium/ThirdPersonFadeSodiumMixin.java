package mchorse.bbs_mod.mixin.client.sodium;

import mchorse.bbs_mod.camera.clips.misc.ThirdPersonFade;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same as {@code ThirdPersonFadeMixin}, for Sodium's own block renderer. Only applied while Sodium is loaded. */
@Mixin(value = BlockRenderer.class, remap = false)
public class ThirdPersonFadeSodiumMixin
{
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbs$fadeThin(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci)
    {
        if (ThirdPersonFade.isHidden(ctx.pos()))
        {
            ci.cancel();
        }
    }
}
