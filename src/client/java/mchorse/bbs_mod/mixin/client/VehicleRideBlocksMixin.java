package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.VehicleRide;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla nudges a player whose feet sit inside a block back out of it; a seated driver is placed
 * on the vehicle's floor every tick, and that nudge fighting the placement shook the view.
 */
@Mixin(ClientPlayerEntity.class)
public class VehicleRideBlocksMixin
{
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    public void onPushOutOfBlocks(double x, double z, CallbackInfo info)
    {
        if (VehicleRide.isSeated((ClientPlayerEntity) (Object) this))
        {
            info.cancel();
        }
    }
}
