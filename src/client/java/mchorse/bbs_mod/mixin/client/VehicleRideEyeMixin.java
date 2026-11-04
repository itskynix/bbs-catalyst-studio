package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.VehicleRide;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The eyes of a player sitting in a film's vehicle: at sitting height in the seat, so the camera
 * looks out of the cabin instead of through its roof. The camera eases to it on its own.
 */
@Mixin(Entity.class)
public class VehicleRideEyeMixin
{
    @Inject(method = "getStandingEyeHeight", at = @At("HEAD"), cancellable = true)
    public void onGetStandingEyeHeight(CallbackInfoReturnable<Float> cir)
    {
        float eye = VehicleRide.getEyeHeight((Entity) (Object) this);

        if (eye >= 0F)
        {
            cir.setReturnValue(eye);
        }
    }
}
