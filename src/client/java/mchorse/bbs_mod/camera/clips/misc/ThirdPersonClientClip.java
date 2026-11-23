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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /** A thin obstacle has to hold the view this long, 4 ticks, before it pulls the arm in. */
    private static final double THIN_DELAY = 4D / 20D;

    /* The damped state, carried from one applied tick to the next */
    private final Vector3d lagPivot = new Vector3d();
    private double lagYaw;
    private double lagPitch;
    private double reach = 1D;
    private double lastTime = Double.NaN;

    /** How long a thin obstacle has been in the way without a break, in seconds. */
    private double thinTime;

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

        /* The wall. A solid block pulls the camera in front of it on the very frame, a thin one
         * (leaves, fences, bars) only after it has held the view for 0.2 seconds, or not at all while
         * it is faded out of the way. Shrinking is instant, growing back is a damped spring */
        double length = arm.length();
        double goal = 1D;
        double radius = Math.max(0D, this.probeRadius.get(tick));
        boolean collide = this.collisionTest.get() && length > 1E-4D;
        boolean fade = this.fadeThinObstacles.get();
        Probe probe = collide ? this.probe(entity, arm, length, right, up, radius) : null;

        if (probe != null)
        {
            goal = Math.max(0D, Math.min(1D, probe.solid / length));

            if (!fade && probe.thin < Double.MAX_VALUE)
            {
                this.thinTime = snap ? 0D : this.thinTime + dt;

                if (this.thinTime >= THIN_DELAY)
                {
                    goal = Math.min(goal, Math.max(0D, Math.min(1D, probe.thin / length)));
                }
            }
            else
            {
                this.thinTime = 0D;
            }
        }
        else
        {
            this.thinTime = 0D;
        }

        if (goal < this.reach || snap)
        {
            this.reach = goal;
        }
        else if (dt > 0D)
        {
            this.reach += (goal - this.reach) * (1D - Math.exp(-RECOVER_SPEED * dt));
        }

        if (!collide)
        {
            this.reach = 1D;
        }

        /* The thin blocks between the pivot and where the camera really ended up fade out of the way */
        Set<Long> faded = new HashSet<>();

        if (fade && probe != null)
        {
            double reachDistance = length * this.reach + radius;

            for (Map.Entry<Long, Double> entry : probe.thinBlocks.entrySet())
            {
                if (entry.getValue() <= reachDistance)
                {
                    faded.add(entry.getKey());
                }
            }
        }

        ThirdPersonFade.submit(faded);

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

    /** What one sweep of the arm found, in blocks along the arm from the pivot. */
    private static class Probe
    {
        /** The nearest solid stop, the sphere's radius already taken off. The arm's length when nothing is in the way. */
        public double solid;

        /** The nearest thin obstacle, as above. Double.MAX_VALUE when there is none. */
        public double thin = Double.MAX_VALUE;

        /** Every thin block the sweep went through, with its distance along the arm. */
        public final Map<Long, Double> thinBlocks = new HashMap<>();
    }

    /**
     * Sweeps a sphere along the arm: the centre ray and eight more on the ring of the probe's radius
     * round the pivot, in the camera's own right and up. A ring ray whose start is inside a solid block
     * is dropped, a pivot hugging a wall is no reason to pull the camera onto the pivot. A ray at an
     * offset of o touches the sphere sqrt(r^2 - o^2) before its own hit.
     */
    private Probe probe(IEntity entity, Vector3d arm, double length, Vector3d right, Vector3d up, double radius)
    {
        World world = entity.getWorld() != null ? entity.getWorld() : MinecraftClient.getInstance().world;

        if (world == null)
        {
            return null;
        }

        Vec3d pivot = new Vec3d(this.lagPivot.x, this.lagPivot.y, this.lagPivot.z);
        Vec3d direction = new Vec3d(arm.x / length, arm.y / length, arm.z / length);
        Probe probe = new Probe();
        int rays = radius > 1E-4D ? 9 : 1;

        probe.solid = length;

        for (int i = 0; i < rays; i++)
        {
            double ox = 0D;
            double oy = 0D;

            if (i > 0)
            {
                /* Right, up, left, down, then the four diagonals on the same ring */
                double angle = i <= 4 ? (i - 1) * Math.PI / 2D : Math.PI / 4D + (i - 5) * Math.PI / 2D;

                ox = Math.cos(angle) * radius;
                oy = Math.sin(angle) * radius;
            }

            Vec3d start = pivot.add(right.x * ox + up.x * oy, right.y * ox + up.y * oy, right.z * ox + up.z * oy);

            if (i > 0)
            {
                Vec3d to = start.subtract(pivot);
                BlockHitResult out = RayTracing.rayTrace(world, pivot, to, to.length());

                if (out != null && out.getType() == HitResult.Type.BLOCK && !isThin(world, out.getBlockPos()))
                {
                    continue;
                }
            }

            double offset = Math.sqrt(ox * ox + oy * oy);
            double sphere = Math.sqrt(Math.max(0D, radius * radius - offset * offset));

            this.march(world, start, direction, length, sphere, probe);
        }

        probe.solid = Math.max(0D, probe.solid);

        if (probe.thin < Double.MAX_VALUE)
        {
            probe.thin = Math.max(0D, probe.thin);
        }

        return probe;
    }

    /** Walks one ray: thin blocks are noted and passed, the first solid one ends it. */
    private void march(World world, Vec3d start, Vec3d direction, double length, double sphere, Probe probe)
    {
        Vec3d from = start;

        for (int i = 0; i < 24; i++)
        {
            double left = length - start.distanceTo(from);

            if (left <= 1E-4D)
            {
                return;
            }

            BlockHitResult hit = RayTracing.rayTrace(world, from, direction, left);

            if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            {
                return;
            }

            BlockPos pos = hit.getBlockPos();
            Vec3d at = hit.getPos();
            double distance = start.distanceTo(at);

            if (!isThin(world, pos))
            {
                probe.solid = Math.min(probe.solid, distance - sphere);

                return;
            }

            probe.thin = Math.min(probe.thin, distance - sphere);
            probe.thinBlocks.merge(pos.asLong(), distance, Math::min);

            /* Out of the thin block's cell, and on */
            double exit = Math.min(axisExit(at.x, direction.x, pos.getX()), Math.min(axisExit(at.y, direction.y, pos.getY()), axisExit(at.z, direction.z, pos.getZ())));

            from = at.add(direction.multiply(Math.max(0D, exit) + 0.002D));
        }
    }

    private static double axisExit(double position, double direction, int cell)
    {
        if (direction > 1E-9D)
        {
            return (cell + 1D - position) / direction;
        }

        if (direction < -1E-9D)
        {
            return (cell - position) / direction;
        }

        return Double.MAX_VALUE;
    }

    /**
     * Thin: leaves, fences, walls, gates, bars, chains, lanterns, panes, and anything whose collision
     * box is under half a block across in two axes or more. A full block, or a box at least half a
     * block wide in two axes, is a wall.
     */
    public static boolean isThin(World world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS) || state.isIn(BlockTags.FENCE_GATES))
        {
            return true;
        }

        Block block = state.getBlock();

        if (block instanceof PaneBlock || block instanceof ChainBlock || block instanceof LanternBlock || block == Blocks.IRON_BARS)
        {
            return true;
        }

        VoxelShape shape = state.getCollisionShape(world, pos);

        if (shape.isEmpty())
        {
            return false;
        }

        Box box = shape.getBoundingBox();
        int thin = 0;

        thin += box.getLengthX() < 0.5D ? 1 : 0;
        thin += box.getLengthY() < 0.5D ? 1 : 0;
        thin += box.getLengthZ() < 0.5D ? 1 : 0;

        return thin >= 2;
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
