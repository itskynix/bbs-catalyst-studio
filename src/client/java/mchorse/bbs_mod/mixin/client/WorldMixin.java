package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin
{
    /* A shown recap's weather is only drawn: the client world takes it, never the single player
     * server's own world, which would take it for real rain */

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    public void onGetRainGradient(CallbackInfoReturnable<Float> info)
    {
        Double rainFactor = BBSRendering.getWeather(((World) (Object) this).isClient());

        if (rainFactor != null)
        {
            info.setReturnValue(rainFactor.floatValue());
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    public void onGetThunderGradient(CallbackInfoReturnable<Float> info)
    {
        if (!((World) (Object) this).isClient())
        {
            return;
        }

        Double thunder = mchorse.bbs_mod.recap.RecapScene.thunder();

        if (thunder != null)
        {
            info.setReturnValue(thunder.floatValue());
        }
    }
}
