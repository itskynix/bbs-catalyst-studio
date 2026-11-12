package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Map;
import mchorse.bbs_mod.camera.clips.fx.FxOverlay;
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

    /* Depth of field: the lens opening as an f-stop (low = shallow, f/22 = everything sharp), how much the
     * bright spots stand out in the bokeh, and a blur over the whole frame that ignores the focus. A clip
     * with none of the first and last keyframed draws no depth of field at all. */
    public final KeyframeChannel<Double> aperture = new KeyframeChannel<>("aperture", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> bokehIntensity = new KeyframeChannel<>("bokeh_intensity", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> generalBlur = new KeyframeChannel<>("general_blur", KeyframeFactories.DOUBLE);

    public static final double MIN_APERTURE = 0.5D;
    public static final double MAX_APERTURE = 22.0D;

    /* Where the focus comes from: the focus distance channel, whatever the camera's axis hits, or a
     * replay's actor followed frame by frame */
    public static final int FOCUS_MANUAL = 0;
    public static final int FOCUS_CROSSHAIR = 1;
    public static final int FOCUS_ACTOR = 2;

    public final ValueInt focusMode = new ValueInt("focus_mode", FOCUS_MANUAL, FOCUS_MANUAL, FOCUS_ACTOR);

    /** Stable id of the replay whose actor is kept in focus (empty = none), as {@code EntityClip#selector}. */
    public final ValueString focusTargetActor = new ValueString("focus_target_actor", "");

    /** Editor guide: a translucent plane at the focus distance, where the picture is sharp. Never in exports. */
    public final ValueBoolean showFocusPlane = new ValueBoolean("show_focus_plane", false);

    /** How far the crosshair focus looks for something to focus on, in blocks */
    public static final double CROSSHAIR_REACH = 512D;

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
            this.x, this.y, this.z, this.yaw, this.pitch, this.roll, this.fov, this.distance, this.focusDistance,
            this.aperture, this.bokehIntensity, this.generalBlur
        };

        this.add(this.focusDistance);
        this.add(this.aperture);
        this.add(this.bokehIntensity);
        this.add(this.generalBlur);
        this.add(this.focusMode);
        this.add(this.focusTargetActor);
        this.add(this.showFocusPlane);
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

        /* The focus of this frame, live from the crosshair or an actor when asked: the depth of field
         * and the focus plane guide in the editor both read it from the position */
        double manual = this.focusDistance.isEmpty() ? position.focusDistance : Math.max(0.1D, this.focusDistance.interpolate(t));

        position.focusDistance = (float) this.resolveFocus(context, position, manual);

        this.collectDepthOfField(context, position, t);
    }

    /**
     * Asks for the depth of field of this frame. Read after the dolly zoom, so the focal length the
     * blur is worked out with is the one the picture really has. Nothing is asked for when neither the
     * aperture nor the general blur has a keyframe: a clip made for its lens alone costs nothing.
     */
    private void collectDepthOfField(ClipContext context, Position position, float t)
    {
        boolean hasAperture = !this.aperture.isEmpty();
        boolean hasBlur = !this.generalBlur.isEmpty();

        if (!hasAperture && !hasBlur)
        {
            return;
        }

        double aperture = hasAperture ? Math.max(MIN_APERTURE, Math.min(MAX_APERTURE, this.aperture.interpolate(t))) : MAX_APERTURE;
        double bokeh = this.bokehIntensity.isEmpty() ? 0.5D : Math.max(0D, Math.min(1D, this.bokehIntensity.interpolate(t)));
        double blur = hasBlur ? Math.max(0D, Math.min(1D, this.generalBlur.interpolate(t))) : 0D;
        double focus = position.focusDistance;

        FxClip.getOverlays(context).add(new FxOverlay(FxOverlay.DEPTH_OF_FIELD)
            .set(0, focus)
            .set(1, fovToFocalLength(position.angle.fov))
            .set(2, aperture)
            .set(3, bokeh)
            .set(4, blur));
    }

    /**
     * The focus distance of this frame, in blocks. Crosshair and actor focus are worked out again every
     * frame, so the focus follows a walking actor or whatever the camera turns to; when there is nothing
     * to follow (no actor picked, the actor not in the film, the axis hitting nothing) the focus distance
     * channel takes over.
     *
     * <p>The distance is measured along the camera's axis, not straight to the point: depth of field
     * is sharp on a plane square to the lens, so an actor off to the side of the frame stays sharp only
     * when the plane goes through them.</p>
     */
    public double resolveFocus(ClipContext context, Position position, double manual)
    {
        int mode = this.focusMode.get();

        if (mode == FOCUS_MANUAL)
        {
            return manual;
        }

        Vector3f look = Matrices.rotation(MathUtils.toRad(position.angle.pitch), MathUtils.toRad(-position.angle.yaw - 180));
        Map<String, IEntity> entities = context instanceof CameraClipContext camera ? camera.entities : null;

        if (mode == FOCUS_ACTOR)
        {
            String id = this.focusTargetActor.get();
            IEntity actor = entities == null || id.isEmpty() ? null : entities.get(id);

            if (actor == null)
            {
                return manual;
            }

            /* Their eyes: the face is what a focus puller keeps sharp */
            double x = Lerps.lerp(actor.getPrevX(), actor.getX(), context.transition);
            double y = Lerps.lerp(actor.getPrevY(), actor.getY(), context.transition) + actor.getEyeHeight();
            double z = Lerps.lerp(actor.getPrevZ(), actor.getZ(), context.transition);
            double along = (x - position.point.x) * look.x + (y - position.point.y) * look.y + (z - position.point.z) * look.z;

            return Math.max(0.1D, along);
        }

        /* Crosshair: the world needed for the ray comes with any of the film's actors */
        World world = null;

        if (entities != null)
        {
            for (IEntity entity : entities.values())
            {
                if (entity.getWorld() != null)
                {
                    world = entity.getWorld();

                    break;
                }
            }
        }

        if (world == null)
        {
            return manual;
        }

        Vec3d from = new Vec3d(position.point.x, position.point.y, position.point.z);
        BlockHitResult hit = RayTracing.rayTrace(world, from, new Vec3d(look.x, look.y, look.z), CROSSHAIR_REACH);

        if (hit == null || hit.getType() == HitResult.Type.MISS)
        {
            return manual;
        }

        return Math.max(0.1D, hit.getPos().distanceTo(from));
    }

    @Override
    public Clip create()
    {
        return new ProCameraClip();
    }
}
