package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.modifiers.ThirdPersonClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Places the camera at the end of a spring arm hung on a replay: the arm's pivot is the replay (or
 * one of its bones) plus the target offset, the camera sits {@code arm length} behind the pivot along
 * the arm's yaw and pitch, shifted by the socket offset in the camera's own axes.
 *
 * <p>Lag and the collision recovery are exponential dampings on the film's own time, not on frames:
 * {@code 1 - exp(-speed * dt)}, with {@code dt} read off the film's tick plus transition. A render
 * that steps the film at a fixed rate therefore lags exactly as the live playback does. What the
 * damping needs is the previous state, and the previous state only makes sense when time moved
 * forwards a little: while the film is paused, scrubbed, rewound or jumped, the state snaps to where
 * the target is, so what the user keys is what is seen.</p>
 */
public class ThirdPersonClientClip extends ThirdPersonClip
{
    /** A step further than this (in seconds) is a jump of the cursor, not playback. */
    private static final double MAX_STEP = 0.5D;

    /** How fast the arm grows back to its length once the wall is out of the way, per second. */
    private static final double RECOVER_SPEED = 6D;

    /* The damped state, carried from one applied tick to the next */
    private final Vector3d lagPivot = new Vector3d();
    private double lagYaw;
    private double lagPitch;
    private double reach = 1D;
    private double lastTime = Double.NaN;

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        IEntity entity = this.getEntity(context);

        if (entity == null)
        {
            return;
        }

        float tick = context.relativeTick + context.transition;
        Vector3d pivot = this.resolvePivot(context, entity, tick);

        /* The arm's direction before any smoothing: where it is aimed, plus the replay's facing
         * as far as the clip follows it. Facing is the camera looking the way the replay looks */
        double yaw = this.yaw.get(tick);

        if (this.followTargetYaw.get())
        {
            yaw += this.autoAlign.get(tick) * (this.facing(entity, context.transition) + 180D);
        }

        double pitch = this.pitch.get(tick);

        /* Where the film is, in seconds */
        double time = (context.ticks + context.transition) / 20D;
        double dt = time - this.lastTime;
        boolean snap = !context.playing || Double.isNaN(this.lastTime) || dt < 0D || dt > MAX_STEP;

        if (snap)
        {
            this.lagPivot.set(pivot);
            this.lagYaw = yaw;
            this.lagPitch = pitch;
            this.reach = 1D;
            dt = 0D;
        }

        this.lastTime = time;

        /* Position lag: the pivot trails the target, never further than the longest lag allowed */
        double positionSpeed = this.positionLagSpeed.get(tick);

        if (!this.positionLag.get() || positionSpeed <= 0D)
        {
            this.lagPivot.set(pivot);
        }
        else if (dt > 0D)
        {
            this.lagPivot.lerp(pivot, 1D - Math.exp(-positionSpeed * dt));

            double max = this.maxLagDistance.get(tick);
            double behind = this.lagPivot.distance(pivot);

            if (max > 0D && behind > max)
            {
                this.lagPivot.set(new Vector3d(this.lagPivot).sub(pivot).mul(max / behind).add(pivot));
            }
        }

        /* Rotation lag, by the short way round */
        double rotationSpeed = this.rotationLagSpeed.get(tick);

        if (!this.rotationLag.get() || rotationSpeed <= 0D)
        {
            this.lagYaw = yaw;
            this.lagPitch = pitch;
        }
        else if (dt > 0D)
        {
            double k = 1D - Math.exp(-rotationSpeed * dt);

            this.lagYaw += wrap(yaw - this.lagYaw) * k;
            this.lagPitch += (pitch - this.lagPitch) * k;
        }

        /* The arm in the world. Yaw 0 looks along -Z of the camera's convention, see Angle.angle */
        double yawRad = Math.toRadians(this.lagYaw);
        double pitchRad = Math.toRadians(this.lagPitch);
        Vector3d forward = new Vector3d(
            Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            -Math.cos(yawRad) * Math.cos(pitchRad)
        );
        Vector3d right = new Vector3d(-forward.z, 0D, forward.x);

        if (right.lengthSquared() < 1E-12D)
        {
            /* Straight up or down: any horizontal axis will do, the yaw says which */
            right.set(Math.cos(yawRad), 0D, Math.sin(yawRad));
        }

        right.normalize();

        Vector3d up = new Vector3d(right).cross(forward).normalize();
        Vector3d arm = new Vector3d(forward).mul(-this.armLength.get(tick))
            .add(new Vector3d(right).mul(this.socketX.get(tick)))
            .add(new Vector3d(up).mul(this.socketY.get(tick)))
            .add(new Vector3d(forward).mul(this.socketZ.get(tick)));

        /* The wall: the arm is cut short in front of what it would end up behind, and grows back
         * smoothly. Shrinking is instant, a camera inside a wall is never worth showing */
        double length = arm.length();
        double goal = 1D;

        if (this.collisionTest.get() && length > 1E-4D)
        {
            goal = this.probe(entity, arm, length, this.probeRadius.get(tick));
        }

        if (goal < this.reach || snap)
        {
            this.reach = goal;
        }
        else if (dt > 0D)
        {
            this.reach += (goal - this.reach) * (1D - Math.exp(-RECOVER_SPEED * dt));
        }

        if (!this.collisionTest.get())
        {
            this.reach = 1D;
        }

        position.point.x = this.lagPivot.x + arm.x * this.reach;
        position.point.y = this.lagPivot.y + arm.y * this.reach;
        position.point.z = this.lagPivot.z + arm.z * this.reach;
        position.angle.yaw = (float) this.lagYaw;
        position.angle.pitch = (float) this.lagPitch;
        position.angle.roll = (float) this.roll.get(tick);
    }

    private IEntity getEntity(ClipContext context)
    {
        String id = this.selector.get();

        if (id.isEmpty() || !(context instanceof CameraClipContext camera))
        {
            return null;
        }

        return camera.entities.get(id);
    }

    /** The point the arm hangs on: the bone (or the replay itself) in the world, moved by the target offset. */
    private Vector3d resolvePivot(ClipContext context, IEntity entity, float tick)
    {
        Vector3d base = null;
        String group = this.group.get();

        if (!group.isEmpty())
        {
            TrackerFrame frame = TrackerFrame.resolve(((CameraClipContext) context).entities, entity, group, 0D, 0D, 0D, context.transition);

            if (frame != null)
            {
                base = frame.position(new Point(0D, 0D, 0D));
            }
        }

        if (base == null)
        {
            Matrix4f matrix = FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, context.transition);
            Vector3f root = matrix.getTranslation(new Vector3f());

            base = new Vector3d(root.x, root.y, root.z);
        }

        return base.add(this.targetX.get(tick), this.targetY.get(tick), this.targetZ.get(tick));
    }

    /** The way the replay faces, in the game's yaw, between its last tick and this one. */
    private double facing(IEntity entity, float transition)
    {
        double previous = entity.getPrevBodyYaw();

        return previous + wrap(entity.getBodyYaw() - previous) * transition;
    }

    /**
     * How much of the arm is free: the share of it from the pivot to the first block in its way,
     * less the probe's radius. 1 when nothing is in the way.
     */
    private double probe(IEntity entity, Vector3d arm, double length, double radius)
    {
        World world = entity.getWorld() != null ? entity.getWorld() : MinecraftClient.getInstance().world;

        if (world == null)
        {
            return 1D;
        }

        Vec3d start = new Vec3d(this.lagPivot.x, this.lagPivot.y, this.lagPivot.z);
        BlockHitResult hit = RayTracing.rayTrace(world, start, new Vec3d(arm.x, arm.y, arm.z), length);

        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
        {
            return 1D;
        }

        double free = start.distanceTo(hit.getPos()) - Math.max(0D, radius);

        return Math.max(0D, Math.min(1D, free / length));
    }

    private static double wrap(double degrees)
    {
        return degrees - 360D * Math.floor((degrees + 180D) / 360D);
    }

    @Override
    protected Clip create()
    {
        return new ThirdPersonClientClip();
    }
}
