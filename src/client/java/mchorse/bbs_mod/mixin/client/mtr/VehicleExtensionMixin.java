package mchorse.bbs_mod.mixin.client.mtr;

import mchorse.bbs.compat.mtr.MtrGovernor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-asserts the BBS-governed state of an MTR client vehicle right after MTR advanced it by the
 * wall clock (core simulation + door tick), and before the rest of {@code simulate(long)}
 * (signals, sensors, oscillation, platform stopping) and the renderer read it.
 *
 * <p>Verified against MTR 4.0.5 for 1.20.4. {@code require = 0}: an MTR build with a different
 * layout must not crash the game; {@link MtrGovernor#isSimulateHookSeen()} tells whether it applied.</p>
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.data.VehicleExtension", remap = false)
public abstract class VehicleExtensionMixin
{
    @Inject(
        method = "simulate(J)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/mtr/mod/data/PersistentVehicleData;tick(DJLorg/mtr/core/data/VehicleExtraData;)V",
            shift = At.Shift.AFTER,
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void bbs$enforceGovernedState(long millisElapsed, CallbackInfo info)
    {
        MtrGovernor.enforce(this);
    }
}
