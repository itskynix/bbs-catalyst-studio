package mchorse.bbs.compat.mtr.mixin;

import mchorse.bbs.compat.mtr.MtrCompatHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface for MTR's {@code org.mtr.mod.client.VehicleRidingMovement} static fields.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.client.VehicleRidingMovement", remap = false)
public interface VehicleRidingMovementAccessor
{
    @Accessor(value = "ridingVehicleId", remap = false)
    public static long getRidingVehicleId()
    {
        return MtrCompatHelper.readRidingVehicleId();
    }

    @Accessor(value = "ridingVehicleCarNumber", remap = false)
    public static int getRidingVehicleCarNumber()
    {
        return MtrCompatHelper.readRidingVehicleCarNumber();
    }

    @Accessor(value = "ridingVehicleX", remap = false)
    public static double getRidingVehicleX()
    {
        return MtrCompatHelper.readRidingVehicleX();
    }

    @Accessor(value = "ridingVehicleY", remap = false)
    public static double getRidingVehicleY()
    {
        return MtrCompatHelper.readRidingVehicleY();
    }

    @Accessor(value = "ridingVehicleZ", remap = false)
    public static double getRidingVehicleZ()
    {
        return MtrCompatHelper.readRidingVehicleZ();
    }

    @Accessor(value = "previousVehicleYaw", remap = false)
    public static double getPreviousVehicleYaw()
    {
        return MtrCompatHelper.readPreviousVehicleYaw();
    }
}
