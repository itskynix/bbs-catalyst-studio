package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.gl.sampler.SamplerBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.iris.IrlSamplersBind;

import java.util.function.IntSupplier;

@Mixin(value = SamplerBinding.class, remap = false)
public abstract class SamplerBindingCubeArrayMixin
{
    @Shadow
    @Final
    private int textureUnit;

    @Shadow
    @Final
    private IntSupplier texture;

    @Inject(method = "updateSampler", at = @At("HEAD"), cancellable = true)
    private void irlite$bindCubeArrayInsteadOf2D(CallbackInfo ci)
    {
        if (IrlSamplersBind.tryRebind(this.texture.getAsInt(), this.textureUnit))
        {
            ci.cancel();
        }
    }
}

