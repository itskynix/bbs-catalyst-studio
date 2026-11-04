package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.VehicleRide;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player sitting in a film's vehicle has their hips on the seat, and under a low seat that puts
 * their feet a little into the ground. The server would take that for walking into a block and pull
 * them back out every tick; for the server's copy of the seated player it is no new collision. Only
 * in single player, where that copy is the one this client seats; see {@code VehicleRide#sit} for
 * another server.
 */
@Mixin(ServerPlayNetworkHandler.class)
public class VehicleRideMoveMixin
{
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "isPlayerNotCollidingWithBlocks", at = @At("HEAD"), cancellable = true)
    private void bbs$seatedInVehicle(WorldView world, Box box, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir)
    {
        if (VehicleRide.isSeatedAnywhere(this.player))
        {
            cir.setReturnValue(false);
        }
    }
}
