package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.recap.RecapRecorder;
import mchorse.bbs_mod.recap.RecapScene;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recap and the game's particles: every particle of the world goes into a running recording and
 * the instant stretch, and while Recap Studio plays a recap with its own particles the live
 * world's are held back, apart from those blocks and the weather give off by themselves.
 */
@Mixin(ParticleManager.class)
public class RecapParticleRecordMixin
{
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapParticle(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> info)
    {
        try
        {
            RecapRecorder.onParticle(effect, x, y, z, vx, vy, vz);

            if (RecapScene.mutesLiveParticles())
            {
                info.setReturnValue(null);
            }
        }
        catch (RuntimeException e)
        {}
    }

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapBlockBreak(BlockPos pos, BlockState state, CallbackInfo info)
    {
        try
        {
            RecapRecorder.onBlockBreakParticles(pos, state);

            if (RecapScene.mutesLiveParticles())
            {
                info.cancel();
            }
        }
        catch (RuntimeException e)
        {}
    }

    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapBlockHit(BlockPos pos, Direction side, CallbackInfo info)
    {
        try
        {
            RecapRecorder.onBlockHitParticles(pos, side);

            if (RecapScene.mutesLiveParticles())
            {
                info.cancel();
            }
        }
        catch (RuntimeException e)
        {}
    }
}
