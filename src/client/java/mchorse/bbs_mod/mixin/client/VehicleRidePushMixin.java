package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.VehicleRide;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nothing shoves a player sitting in a film's vehicle, and they shove nothing: the vehicle's actor
 * entity stands right where the seat is, and the two pushing each other out every tick (on the
 * client and, in single player, on the server too) is what shook the view while driving. Matched by
 * id, so the server's copy of the player is spared as well.
 */
@Mixin(Entity.class)
public class VehicleRidePushMixin
{
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    public void onPushAwayFrom(Entity entity, CallbackInfo info)
    {
        if (VehicleRide.isSeatedAnywhere((Entity) (Object) this) || VehicleRide.isSeatedAnywhere(entity))
        {
            info.cancel();
        }
    }
}
