package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * Pro Camera Clip
 *
 * Professional cinema camera clip providing:
 * - 35mm full-frame sensor equivalent focal length (mm) with bidirectional FOV conversion
 * - Lens presets (24mm, 35mm, 50mm, 85mm)
 * - Independent Roll / Dutch Angle channel directly applied to camera matrix
 * - Built-in Dolly Zoom / Vertigo effect with "Lock Subject Size"
 * - Focus distance keyframe channel for cinematic depth-of-field pipelines
 */
public class ProCameraClip extends KeyframeClip
{
    /* Standard 35mm full frame sensor height in mm (Minecraft vertical FOV matches 24mm sensor height) */
    public static final double SENSOR_HEIGHT_MM = 24.0;

    /* Focus distance channel (in blocks) */
    public final KeyframeChannel<Double> focusDistance = new KeyframeChannel<>("focus_distance", KeyframeFactories.DOUBLE);

    /* Built-in Dolly Zoom (Vertigo) Mode */
    public final ValueBoolean lockSubjectSize = new ValueBoolean("lock_subject_size", false);
    public final ValuePoint subject = new ValuePoint("subject", new Point(0, 0, 0));
    public final ValueFloat baseSubjectDistance = new ValueFloat("base_subject_distance", 5.0F);

    /* Focal length property (in mm) */
    public final ValueFloat focalLength = new ValueFloat("focal_length", 35.0F);

    public ProCameraClip()
    {
        super();

        this.channels = new KeyframeChannel[] {
            this.x, this.y, this.z, this.yaw, this.pitch, this.roll, this.fov, this.distance, this.focusDistance
        };

        this.add(this.focusDistance);
        this.add(this.lockSubjectSize);
        this.add(this.subject);
        this.add(this.baseSubjectDistance);
        this.add(this.focalLength);
    }

    /**
     * Converts vertical FOV (in degrees) to 35mm sensor equivalent focal length (in mm).
     * Formula: focal_length = sensor_height / (2 * tan(FOV / 2))
     */
    public static double fovToFocalLength(double fov)
    {
        double clampedFov = Math.max(1.0, Math.min(179.0, fov));
        double halfRad = Math.toRadians(clampedFov / 2.0);
        return (SENSOR_HEIGHT_MM / 2.0) / Math.tan(halfRad);
    }

    /**
     * Converts 35mm focal length (in mm) to vertical FOV (in degrees).
     * Formula: FOV = 2 * atan(sensor_height / (2 * focal_length))
     */
    public static double focalLengthToFov(double mm)
    {
        double clampedMm = Math.max(1.0, mm);
        double rad = 2.0 * Math.atan((SENSOR_HEIGHT_MM / 2.0) / clampedMm);
        return Math.toDegrees(rad);
    }

    @Override
    public void fromCamera(Camera camera)
    {
        super.fromCamera(camera);

        double fovDeg = Math.toDegrees(camera.fov);
        this.focalLength.set((float) fovToFocalLength(fovDeg));
        this.focusDistance.insert(0, 5.0D);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        super.applyClip(context, position);

        float t = context.relativeTick + context.transition;

        /* Apply focus distance */
        if (!this.focusDistance.isEmpty())
        {
            position.focusDistance = this.focusDistance.interpolate(t).floatValue();
        }

        /* Dolly Zoom (Vertigo) Mode: Lock Subject Size */
        if (this.lockSubjectSize.get())
        {
            Point s = this.subject.get();
            double x0 = this.x.isEmpty() ? position.point.x : this.x.interpolate(0F);
            double y0 = this.y.isEmpty() ? position.point.y : this.y.interpolate(0F);
            double z0 = this.z.isEmpty() ? position.point.z : this.z.interpolate(0F);
            double fov0 = this.fov.isEmpty() ? 70.0 : this.fov.interpolate(0F);

            double d0 = Math.sqrt(
                Math.pow(x0 - s.x, 2) + Math.pow(y0 - s.y, 2) + Math.pow(z0 - s.z, 2)
            );
            if (d0 < 0.05)
            {
                d0 = Math.max(0.5, this.baseSubjectDistance.get());
            }

            double dt = Math.sqrt(
                Math.pow(position.point.x - s.x, 2) + Math.pow(position.point.y - s.y, 2) + Math.pow(position.point.z - s.z, 2)
            );
            if (dt < 0.05)
            {
                dt = 0.05;
            }

            double tanHalfFov0 = Math.tan(Math.toRadians(fov0 / 2.0));
            double tanHalfFovT = tanHalfFov0 * (d0 / dt);
            double fovT = Math.toDegrees(2.0 * Math.atan(tanHalfFovT));
            fovT = Math.max(1.0, Math.min(170.0, fovT));

            position.angle.fov = (float) fovT;
        }
    }

    @Override
    public Clip create()
    {
        return new ProCameraClip();
    }
}
