package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.WorldFreeCam;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.film.VehicleRide;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(double x, double y, double z);

    /**
     * With the free camera on, the game believes the camera is in the third person - which is
     * what makes it draw the player's body, even if the player is in first person. The body then
     * stays where the player was left, seen from wherever the camera flies.
     */
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean bbs$freeCamThirdPerson(boolean thirdPerson)
    {
        return thirdPerson || WorldFreeCam.isActive();
    }

    @Inject(method = "update", at = @At(value = "RETURN"))
    public void onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci)
    {
        CameraController controller = BBSModClient.getCameraController();

        controller.setup(controller.camera, tickDelta);

        if (controller.getCurrent() != null)
        {
            Vector3d position = controller.getPosition();
            float yaw = controller.getYaw();
            float pitch = controller.getPitch();

            this.setPos(position.x, position.y, position.z);
            this.setRotation(yaw, pitch);

            return;
        }

        /* Seated in a film's vehicle: the body sits on the seat, the view is where the driver's
         * camera was set and rides its spring over steps (see VehicleRide#sit) */
        Vec3d offset = VehicleRide.getCameraOffset(focusedEntity, thirdPerson, tickDelta);

        if (offset != null)
        {
            Vec3d position = ((Camera) (Object) this).getPos();

            this.setPos(position.x + offset.x, position.y + offset.y, position.z + offset.z);
        }
    }
}