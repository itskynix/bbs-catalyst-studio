package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.recap.RecapRecorder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The rain's splashes and sounds: the weather is played live, not kept by a recap. */
@Mixin(WorldRenderer.class)
public class RecapAmbientRainMixin
{
    @Inject(method = "tickRainSplashing", at = @At("HEAD"), require = 0)
    private void bbs$recapRainStart(Camera camera, CallbackInfo info)
    {
        RecapRecorder.ambient = true;
    }

    @Inject(method = "tickRainSplashing", at = @At("RETURN"), require = 0)
    private void bbs$recapRainEnd(Camera camera, CallbackInfo info)
    {
        RecapRecorder.ambient = false;
    }
}
