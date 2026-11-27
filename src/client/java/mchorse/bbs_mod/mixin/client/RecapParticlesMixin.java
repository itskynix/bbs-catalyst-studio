package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recap Studio leaving the particles out of the world while it shows a recap that hides them. */
@Mixin(ParticleManager.class)
public class RecapParticlesMixin
{
    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void bbs$recapHidesParticles(CallbackInfo info)
    {
        if (mchorse.bbs_mod.recap.RecapScene.hidesParticles())
        {
            info.cancel();
        }
    }
}
