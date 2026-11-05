package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.FramePipeline;

@Mixin(value = CapturedRenderingState.class, remap = false)
public class CapturedRenderingStateClusterMixin
{
    @Inject(method = "setGbufferProjection", at = @At("TAIL"), remap = false)
    private void irlite$captureGbufferMatrices(CallbackInfo ci)
    {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        FramePipeline.onGbufferMatricesCaptured(state.getGbufferModelView(), state.getGbufferProjection());
    }
}

