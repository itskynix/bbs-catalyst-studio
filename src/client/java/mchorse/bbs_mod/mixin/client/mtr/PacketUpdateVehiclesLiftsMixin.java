package mchorse.bbs_mod.mixin.client.mtr;

import mchorse.bbs.compat.mtr.MtrGovernor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Keeps BBS-governed vehicles alive across MTR server updates: the server can neither drop them
 * from the client (despawn, recall to depot) nor leave a second copy behind.
 *
 * <p>Verified against MTR 4.0.5 for 1.20.4.</p>
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.packet.PacketUpdateVehiclesLifts", remap = false)
public abstract class PacketUpdateVehiclesLiftsMixin
{
    @ModifyArg(
        method = "updateVehiclesOrLifts",
        at = @At(
            value = "INVOKE",
            target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArraySet;removeIf(Ljava/util/function/Predicate;)Z",
            remap = false
        ),
        index = 0,
        remap = false,
        require = 0
    )
    private static Predicate<Object> bbs$keepGovernedVehicles(Predicate<Object> original)
    {
        return MtrGovernor.wrapRemoval(original);
    }

    @Inject(method = "runClientInbound", at = @At("TAIL"), remap = false, require = 0)
    private void bbs$afterServerUpdate(CallbackInfo info)
    {
        MtrGovernor.afterServerUpdate();
    }
}
