package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.recap.RecapRecorder;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The particles and sounds blocks give off by themselves (flames, drips, ash, crackles): not kept by a recap. */
@Mixin(ClientWorld.class)
public class RecapAmbientWorldMixin
{
    @Inject(method = "doRandomBlockDisplayTicks", at = @At("HEAD"), require = 0)
    private void bbs$recapAmbientStart(int x, int y, int z, CallbackInfo info)
    {
        RecapRecorder.ambient = true;
    }

    @Inject(method = "doRandomBlockDisplayTicks", at = @At("RETURN"), require = 0)
    private void bbs$recapAmbientEnd(int x, int y, int z, CallbackInfo info)
    {
        RecapRecorder.ambient = false;
    }
}
