package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.recap.RecapParticles;
import mchorse.bbs_mod.recap.RecapScene;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recap Studio leaving the particles out of the world while hiding them, and tracking replayed ones for scrubbing. */
@Mixin(ParticleManager.class)
public class RecapParticlesMixin
{
    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapHidesParticles(CallbackInfo info)
    {
        if (RecapScene.hidesParticles())
        {
            info.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), require = 0)
    private void bbs$recapTrackParticle(Particle particle, CallbackInfo info)
    {
        if (RecapParticles.replaying)
        {
            RecapParticles.trackSpawned(particle);
        }
    }
}
