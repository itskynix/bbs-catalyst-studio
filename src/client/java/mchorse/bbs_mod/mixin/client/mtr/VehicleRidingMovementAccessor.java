package mchorse.bbs_mod.mixin.client.mtr;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor for MTR's client-side {@code VehicleRidingMovement} state.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.client.VehicleRidingMovement", remap = false)
public interface VehicleRidingMovementAccessor
{
    @Accessor(value = "ridingVehicleId", remap = false)
    public static long getRidingVehicleId()
    {
        throw new AssertionError();
    }

    @Accessor(value = "ridingVehicleCarNumber", remap = false)
    public static int getRidingVehicleCarNumber()
    {
        throw new AssertionError();
    }

    @Accessor(value = "ridingVehicleX", remap = false)
    public static double getRidingVehicleX()
    {
        throw new AssertionError();
    }

    @Accessor(value = "ridingVehicleY", remap = false)
    public static double getRidingVehicleY()
    {
        throw new AssertionError();
    }

    @Accessor(value = "ridingVehicleZ", remap = false)
    public static double getRidingVehicleZ()
    {
        throw new AssertionError();
    }

    @Accessor(value = "previousVehicleYaw", remap = false)
    public static double getPreviousVehicleYaw()
    {
        throw new AssertionError();
    }
}
