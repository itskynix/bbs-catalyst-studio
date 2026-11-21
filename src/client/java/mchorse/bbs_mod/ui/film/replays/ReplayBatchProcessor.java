package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.nav.NavGrid;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

public class ReplayBatchProcessor
{
    public enum Operation
    {
        RANDOM,
        LINE,
        SHIFT,
        SQUARE,
        SQUARE_OUTLINE,
        CIRCLE,
        CIRCLE_OUTLINE,
        CUBE,
        SPHERE,
        FIT_HEIGHT,
        LOOK_AT,
        FOLLOW_TARGET
    }

    public enum Error
    {
        NEED_TWO_CHANNELS,
        NEED_THREE_CHANNELS,
        NEED_Y_CHANNEL,
        NEED_TARGET,
        NEED_POSITION_CHANNELS,
        INVALID_EXPRESSION,
        NO_WORLD
    }

    public static class VisibleReplay
    {
        public final Replay replay;
        public final int i;
        public final int o;

        public VisibleReplay(Replay replay, int i, int o)
        {
            this.replay = replay;
            this.i = i;
            this.o = o;
        }
    }

    public static class NormalParams
    {
        public double randomMin;
        public double randomMax;
        public double lineOffset;
        public double size;
        public double shift;
        public boolean fill;
        public Replay lookAtTarget;
        public GroundProvider groundProvider;

        /** Follow target: a replay, or the camera's clips when no replay is given */
        public Replay followTarget;
        public Clips followCamera;

        /** Where from the target's point of view: x to its right, y up, z ahead of it */
        public double followX;
        public double followY;
        public double followZ;

        /** How many ticks behind the target the follower lags; 0 holds it on the target */
        public double followSmooth;

        /** The nearest a follower comes to the point it follows, in blocks */
        public double followMinDistance = 1.2D;

        /** How close the target must come for the chase to begin, in blocks; 0 chases from the first tick */
        public double followTriggerRange;

        /** A chase that is run rather than drawn with a ruler: sway, sprints and tiring, and a head that leads the body */
        public boolean followOrganic;

        /** How strong the sway and the changes of pace are, 0 to 2 */
        public double followIntensity = 1D;

        /** The tick the follow begins on: everything before it is left as it is */
        public int followStartTick;

        /** The navigation mesh of the scene, when it has a volume: followers find their own way on it. Null walks straight. */
        public NavGrid followGrid;

        /** The floor of the world, for a follower that has no mesh to stand on. Null keeps its own height. */
        public GroundNear followGround;

        /** How fast a follower goes, in blocks a second; 5.6 is a sprint */
        public double followSpeed = 5.6D;

        /** Turn the follower to the way it walks, and to the target while it stands */
        public boolean followRotate = true;

        /** Leave the height keyframes as they are and follow in the horizontal plane only */
        public boolean followKeepGround;

        /** How many ticks of the film to write keyframes for */
        public int followDuration;
        }

    @FunctionalInterface
    public interface GroundProvider
    {
        double getGroundY(double x, double z);
    }

    /** The floor of the world under or near a point: the height of the top of the nearest block to stand on below it, NaN when there is none */
    @FunctionalInterface
    public interface GroundNear
    {
        double groundY(double x, double y, double z);
    }

    public static Error applyAdvanced(List<VisibleReplay> selected, List<String> selectedProperties, String expressionText)
    {
        MathBuilder builder = new MathBuilder();

        builder.register("i");
        builder.register("o");
        builder.register("v");
        builder.register("ki");

        IExpression parse;

        try
        {
            parse = builder.parse(expressionText);
        }
        catch (Exception e)
        {
            return Error.INVALID_EXPRESSION;
        }

        for (VisibleReplay replay : selected)
        {
            builder.variables.get("i").set(replay.i);
            builder.variables.get("o").set(replay.o);

            for (String s : selectedProperties)
            {
                KeyframeChannel channel = (KeyframeChannel) replay.replay.keyframes.get(s);

                if (channel == null)
                {
                    continue;
                }

                List keyframes = channel.getKeyframes();

                for (int i = 0; i < keyframes.size(); i++)
                {
                    Keyframe kf = (Keyframe) keyframes.get(i);

                    builder.variables.get("v").set(kf.getFactory().getY(kf.getValue()));
                    builder.variables.get("ki").set(i);

                    kf.setValue(kf.getFactory().yToValue(parse.doubleValue()), true);
                }
            }
        }

        return null;
    }

    public static Error applyNormal(List<VisibleReplay> selected, List<String> selectedProperties, Operation operation, NormalParams params)
    {
        if (operation == null)
        {
            return null;
        }

        return switch (operation)
        {
            case SQUARE, SQUARE_OUTLINE, CIRCLE, CIRCLE_OUTLINE -> apply2DShape(selected, selectedProperties, operation, params);
            case CUBE, SPHERE -> apply3DShape(selected, selectedProperties, operation, params);
            case LINE -> applyLine(selected, selectedProperties, params);
            case SHIFT -> applyShift(selected, selectedProperties, params);
            case RANDOM -> applyRandom(selected, selectedProperties, params);
            case FIT_HEIGHT -> applyFitHeight(selected, selectedProperties, params);
            case LOOK_AT -> applyLookAt(selected, params);
            case FOLLOW_TARGET -> applyFollow(selected, params);
        };
    }

    private static Error apply2DShape(List<VisibleReplay> selected, List<String> selectedProperties, Operation operation, NormalParams params)
    {
        if (selectedProperties.size() < 2)
        {
            return Error.NEED_TWO_CHANNELS;
        }

        String aId = selectedProperties.get(0);
        String bId = selectedProperties.get(1);

        int count = selected.size();

        for (VisibleReplay replay : selected)
        {
            int o = replay.o;
            double a = 0;
            double b = 0;

            if (operation == Operation.SQUARE)
            {
                int side = (int) Math.ceil(Math.sqrt(count));
                int rows = (int) Math.ceil(count / (double) side);
                double half = (side - 1) / 2D;
                double step = side > 1 ? params.size / (side - 1) : 0D;
                int col = o % side;
                int row = o / side;

                a = (col - half) * step;
                if (rows > 1)
                {
                    double rowScaled = row * (side - 1D) / (rows - 1D);
                    b = (rowScaled - half) * step;
                }
                else
                {
                    b = 0D;
                }
            }
            else if (operation == Operation.SQUARE_OUTLINE)
            {
                double size = params.size;
                double half = size / 2D;
                double s = count <= 1 ? 0D : (o / (double) count) * 4D;

                if (s < 1D)
                {
                    a = -half + size * s;
                    b = -half;
                }
                else if (s < 2D)
                {
                    a = half;
                    b = -half + size * (s - 1D);
                }
                else if (s < 3D)
                {
                    a = half - size * (s - 2D);
                    b = half;
                }
                else
                {
                    a = -half;
                    b = half - size * (s - 3D);
                }
            }
            else if (operation == Operation.CIRCLE)
            {
                double radius = params.size / 2D;
                double goldenAngle = Math.PI * (3D - Math.sqrt(5D));
                double t = (o + 0.5D) / count;
                double r = Math.sqrt(t) * radius;
                double angle = o * goldenAngle;

                a = Math.cos(angle) * r;
                b = Math.sin(angle) * r;
            }
            else if (operation == Operation.CIRCLE_OUTLINE)
            {
                double radius = params.size / 2D;
                double angle = (count == 1 ? 0D : (o / (double) count) * Math.PI * 2D);

                a = Math.cos(angle) * radius;
                b = Math.sin(angle) * radius;
            }

            applyDelta(replay.replay, aId, a);
            applyDelta(replay.replay, bId, b);
        }

        return null;
    }

    private static Error apply3DShape(List<VisibleReplay> selected, List<String> selectedProperties, Operation operation, NormalParams params)
    {
        if (selectedProperties.size() < 3)
        {
            return Error.NEED_THREE_CHANNELS;
        }

        String aId = selectedProperties.get(0);
        String bId = selectedProperties.get(1);
        String cId = selectedProperties.get(2);

        int count = selected.size();

        if (operation == Operation.CUBE)
        {
            int side;
            int[] shellCoords = null;

            if (params.fill)
            {
                side = (int) Math.ceil(Math.cbrt(count));
            }
            else
            {
                side = 2;
                while (count > cubeSurfacePoints(side))
                {
                    side++;
                }

                shellCoords = buildCubeShellCoords(count, side);
            }

            double half = (side - 1) / 2D;
            double step = side > 1 ? params.size / (side - 1) : 0D;

            for (VisibleReplay replay : selected)
            {
                int o = replay.o;
                int gx;
                int gy;
                int gz;

                if (params.fill)
                {
                    gx = o % side;
                    gy = (o / side) % side;
                    gz = o / (side * side);
                }
                else
                {
                    int i = o * 3;
                    gx = shellCoords[i];
                    gy = shellCoords[i + 1];
                    gz = shellCoords[i + 2];
                }

                double a = (gx - half) * step;
                double b = (gy - half) * step;
                double c = (gz - half) * step;

                applyDelta(replay.replay, aId, a);
                applyDelta(replay.replay, bId, b);
                applyDelta(replay.replay, cId, c);
            }
        }
        else
        {
            double radius = params.size / 2D;
            double goldenAngle = Math.PI * (3D - Math.sqrt(5D));

            for (VisibleReplay replay : selected)
            {
                int o = replay.o;
                double t = (o + 0.5D) / count;
                double theta = goldenAngle * (o + 0.5D);
                double y = 1D - 2D * t;
                double rxy = Math.sqrt(1D - y * y);
                double s = params.fill ? Math.cbrt(t) : 1D;
                double x = Math.cos(theta) * rxy;
                double z = Math.sin(theta) * rxy;

                double a = x * radius * s;
                double b = y * radius * s;
                double c = z * radius * s;

                applyDelta(replay.replay, aId, a);
                applyDelta(replay.replay, bId, b);
                applyDelta(replay.replay, cId, c);
            }
        }

        return null;
    }

    private static Error applyLine(List<VisibleReplay> selected, List<String> selectedProperties, NormalParams params)
    {
        for (VisibleReplay replay : selected)
        {
            double delta = replay.o * params.lineOffset;

            for (String s : selectedProperties)
            {
                applyDelta(replay.replay, s, delta);
            }
        }

        return null;
    }

    private static Error applyShift(List<VisibleReplay> selected, List<String> selectedProperties, NormalParams params)
    {
        for (VisibleReplay replay : selected)
        {
            for (String s : selectedProperties)
            {
                applyDelta(replay.replay, s, params.shift);
            }
        }

        return null;
    }

    private static Error applyRandom(List<VisibleReplay> selected, List<String> selectedProperties, NormalParams params)
    {
        double minValue = params.randomMin;
        double maxValue = params.randomMax;
        double minRand = Math.min(minValue, maxValue);
        double maxRand = Math.max(minValue, maxValue);
        long seed = ThreadLocalRandom.current().nextLong();

        for (VisibleReplay replay : selected)
        {
            long replaySeed = mix64(seed + (long) replay.o * 0x9e3779b97f4a7c15L);

            for (int ci = 0; ci < selectedProperties.size(); ci++)
            {
                String s = selectedProperties.get(ci);
                long channelSeed = mix64(replaySeed + (long) ci * 0xbf58476d1ce4e5b9L);
                SplittableRandom random = new SplittableRandom(channelSeed);
                double delta = minRand + random.nextDouble() * (maxRand - minRand);

                applyDelta(replay.replay, s, delta);
            }
        }

        return null;
    }

    private static Error applyFitHeight(List<VisibleReplay> selected, List<String> selectedProperties, NormalParams params)
    {
        if (!selectedProperties.contains("y"))
        {
            return Error.NEED_Y_CHANNEL;
        }

        if (params.groundProvider == null)
        {
            return Error.NO_WORLD;
        }

        return fitHeightToGround(selected, params.groundProvider);
    }

    private static Error applyLookAt(List<VisibleReplay> selected, NormalParams params)
    {
        if (params.lookAtTarget == null)
        {
            return Error.NEED_TARGET;
        }

        return lookAt(selected, params.lookAtTarget);
    }

    /** How fast the way the target is heading may swing, in degrees a tick */
    private static final double HEADING_RATE = 30D;

    /** How fast a follower's body turns, in degrees a tick; its head goes a little faster */
    private static final double BODY_TURN_RATE = 14D;
    private static final double HEAD_TURN_RATE = 24D;

    /** How far the target must go in one tick for it to count as walking, in blocks */
    private static final double WALK_STEP = 0.02D;

    /** How far apart followers standing round the same point are kept, in blocks along the circle */
    private static final double SLOT_SPACING = 0.8D;

    /** A target further than this many times the trigger range is lost: the follower slows to a stop */
    private static final double LOSE_FACTOR = 1.75D;

    /** The farthest a chase sways to either side of its path, in blocks, at an intensity of 1 */
    private static final double SWAY = 0.35D;

    /** How often a follower finds its way again on the mesh, in ticks, at most */
    private static final int REPLAN_TICKS = 8;

    /** How high a camera sits above the feet of someone standing where it is */
    private static final double EYE = 1.62D;

    /** How many ticks the body trails the head in a chase */
    private static final int BODY_LAG = 3;

    /**
     * Makes each replay follow the target through the whole film, one keyframe a tick.
     *
     * <p>The place followed is the target's plus the offset (x to its right, y up, z ahead), turned
     * by the way the target is <em>heading</em>: where it really moves, smoothed and turned at a
     * limited rate, and held where it is while the target stands. Not by the target's yaw, so a
     * target that turns on the spot does not sweep the followers round itself.</p>
     *
     * <p>A follower never comes nearer than the minimum distance to that place. Each has a slot on the
     * circle of that radius round it, on the side it came from; several followers that came from the
     * same side are fanned out along the circle {@link #SLOT_SPACING} blocks apart. The slot turns
     * only with the heading. A follower is moved to its slot with the smoothness (1 / (N + 1) of the
     * way a tick, 0 holds it there), and anything still inside the circle is put back on it.</p>
     *
     * <p>The follow begins on the start tick: nothing before it is touched, and a follower starts
     * from where its own keyframes have it at that tick, with the angles they give, and walks from
     * there at the follow speed. It is never put on the target: it goes a few blocks a second at
     * most, eases off as it nears its place and stops on the circle of the minimum distance.</p>
     *
     * <p>A follower never copies what the target does with its body: its height is the floor under
     * its own feet, from the navigation mesh or else from the blocks of the world, so a jump, a crouch
     * or a climb of the target stay the target's. Where the scene has a navigation volume the follower
     * finds its own way to its place on the mesh (A*), again every few ticks or when the target moves
     * to another node, so it goes round what is in the way, by another stair or another corridor if
     * there is one. Without a volume, or off the mesh, it walks straight.</p>
     *
     * <p>With a trigger range a follower stands where it is, its keyframes untouched, until the
     * target comes in range; from that tick on it chases, and slows to a stop if the target gets
     * beyond 1.75 times the range, running again when it is back in. A chase can be made organic:
     * a sway to the sides of its path, sprints when the gap opens and tiring after a long run,
     * every phase from a seed of the replay's own.</p>
     *
     * <p>Turning, when rotating is on: the body goes the way the follower walks, or to the target
     * while it stands, and the head the same a little quicker, each at a limited rate, so it never
     * flips half a turn between two ticks.</p>
     */
    private static Error applyFollow(List<VisibleReplay> selected, NormalParams params)
    {
        if (params.followTarget == null && params.followCamera == null)
        {
            return Error.NEED_TARGET;
        }

        int ticks = Math.max(1, params.followDuration);
        double[] tx = new double[ticks + 1];
        double[] ty = new double[ticks + 1];
        double[] tz = new double[ticks + 1];
        double[] tyaw = new double[ticks + 1];

        if (params.followTarget != null)
        {
            KeyframeChannel cx = (KeyframeChannel) params.followTarget.keyframes.get("x");
            KeyframeChannel cy = (KeyframeChannel) params.followTarget.keyframes.get("y");
            KeyframeChannel cz = (KeyframeChannel) params.followTarget.keyframes.get("z");
            KeyframeChannel cyaw = (KeyframeChannel) params.followTarget.keyframes.get("yaw");

            if (cx == null || cy == null || cz == null)
            {
                return Error.NEED_POSITION_CHANNELS;
            }

            for (int t = 0; t <= ticks; t++)
            {
                tx[t] = cx.getFactory().getY(cx.interpolate(t));
                ty[t] = cy.getFactory().getY(cy.interpolate(t));
                tz[t] = cz.getFactory().getY(cz.interpolate(t));
                tyaw[t] = cyaw == null ? 0D : cyaw.getFactory().getY(cyaw.interpolate(t));
            }
        }
        else
        {
            /* The camera's flight, tick by tick; its yaw is a half turn from an actor's looking the same way */
            Position position = new Position();
            CameraClipContext context = new CameraClipContext();

            context.clips = params.followCamera;

            for (int t = 0; t <= ticks; t++)
            {
                context.clipData.clear();
                context.setup(t, 0F);

                for (Clip clip : context.clips.getClips(t))
                {
                    context.apply(clip, position);
                }

                context.currentLayer = 0;

                tx[t] = position.point.x;
                ty[t] = position.point.y;
                tz[t] = position.point.z;
                tyaw[t] = position.angle.yaw - 180D;
            }
        }

        /* The way the target heads at every tick, shared by all the followers */
        double[] heading = new double[ticks + 1];

        heading[0] = tyaw[0];

        for (int t = 1; t <= ticks; t++)
        {
            double dx = tx[t] - tx[t - 1];
            double dz = tz[t] - tz[t - 1];

            heading[t] = dx * dx + dz * dz > WALK_STEP * WALK_STEP
                ? turnToward(heading[t - 1], Math.atan2(dz, dx) * (180D / Math.PI) - 90D, HEADING_RATE)
                : heading[t - 1];
        }

        List<VisibleReplay> followers = new ArrayList<>();

        for (VisibleReplay replay : selected)
        {
            if (replay.replay != params.followTarget)
            {
                followers.add(replay);
            }
        }

        /* How fast the target itself goes, a little averaged: what a chaser has to beat to ever catch up */
        double[] targetSpeed = new double[ticks + 1];

        for (int t = 1; t <= ticks; t++)
        {
            double sum = 0D;
            int count = 0;

            for (int k = Math.max(1, t - 3); k <= t; k++)
            {
                sum += Math.hypot(tx[k] - tx[k - 1], tz[k] - tz[k - 1]);
                count++;
            }

            targetSpeed[t] = sum / Math.max(1, count);
        }

        double alpha = 1D / (Math.max(0D, params.followSmooth) + 1D);
        double min = Math.max(0D, params.followMinDistance);
        double step = min > 0D ? SLOT_SPACING / Math.max(min, 0.4D) : 0D;
        double range = Math.max(0D, params.followTriggerRange);
        boolean ranged = range > 0D;
        boolean organic = params.followOrganic;
        double intensity = Math.max(0D, Math.min(2D, params.followIntensity));

        /* A follower goes the way a character goes, a few blocks a second at most: it is never put at the target */
        double stepSpeed = Math.max(0.5D, Math.min(20D, params.followSpeed)) / 20D;
        int from = Math.max(0, params.followStartTick);

        if (from > ticks)
        {
            return null;
        }

        for (int n = 0; n < followers.size(); n++)
        {
            VisibleReplay replay = followers.get(n);
            KeyframeChannel x = (KeyframeChannel) replay.replay.keyframes.get("x");
            KeyframeChannel y = (KeyframeChannel) replay.replay.keyframes.get("y");
            KeyframeChannel z = (KeyframeChannel) replay.replay.keyframes.get("z");

            if (x == null || z == null || (y == null && !params.followKeepGround))
            {
                return Error.NEED_POSITION_CHANNELS;
            }

            KeyframeChannel[] yaws = params.followRotate ? new KeyframeChannel[] {
                (KeyframeChannel) replay.replay.keyframes.get("yaw"),
                (KeyframeChannel) replay.replay.keyframes.get("bodyYaw"),
                (KeyframeChannel) replay.replay.keyframes.get("headYaw")
            } : new KeyframeChannel[0];

            /* Where the follower stands on its own, before anything is written: it waits there for the target to come in range */
            double[] ox = new double[ticks + 1];
            double[] oy = new double[ticks + 1];
            double[] oz = new double[ticks + 1];

            for (int t = 0; t <= ticks; t++)
            {
                ox[t] = x.getFactory().getY(x.interpolate(t));
                oz[t] = z.getFactory().getY(z.interpolate(t));
                oy[t] = y == null ? ty[t] : y.getFactory().getY(y.interpolate(t));
            }

            /* The first tick the target is in range; a follower it never comes near keeps its keyframes */
            int start = from;

            if (ranged)
            {
                start = -1;

                for (int t = from; t <= ticks; t++)
                {
                    if (Math.hypot(ox[t] - tx[t], oz[t] - tz[t]) <= range)
                    {
                        start = t;

                        break;
                    }
                }

                if (start < 0)
                {
                    continue;
                }
            }

            double startBody = 0D;
            boolean hasStartBody = false;

            if (yaws.length > 0 && yaws[0] != null)
            {
                startBody = yaws[0].getFactory().getY(yaws[0].interpolate(start));
                hasStartBody = true;
            }

            /* Where the follower starts from decides which side of the target it keeps to */
            double[] goal0 = followGoal(params, tx[start], ty[start], tz[start], heading[start]);
            double ax = ox[start] - goal0[0];
            double az = oz[start] - goal0[2];

            /* The slot, as an angle from the heading: the side it came from, or behind when it came from nowhere */
            double approach = ax * ax + az * az > 1.0E-6D
                ? Math.atan2(az, ax) * (180D / Math.PI) - 90D
                : heading[start] + 180D;
            double slot = approach - heading[start] + Math.toDegrees((n - (followers.size() - 1) / 2D) * step);

            /* A range keeps what the follower did before the target came: only what is from then on is written afresh */
            clearFrom(x, start);
            clearFrom(z, start);

            if (!params.followKeepGround)
            {
                clearFrom(y, start);
            }

            for (KeyframeChannel yaw : yaws)
            {
                if (yaw != null)
                {
                    clearFrom(yaw, start);
                }
            }

            /* Every random phase of this one's run comes from its own seed: the same on every run, different from the others' */
            SplittableRandom random = new SplittableRandom(mix64(replay.replay.getId().hashCode() * 31L + replay.i * 0x9e3779b97f4a7c15L));
            double[] period = {46D + random.nextDouble() * 24D, 17D + random.nextDouble() * 10D, 7D + random.nextDouble() * 4D};
            double[] phase = {random.nextDouble() * Math.PI * 2D, random.nextDouble() * Math.PI * 2D, random.nextDouble() * Math.PI * 2D};
            double bodyPhase = random.nextDouble() * Math.PI * 2D;
            int nextTire = 80 + random.nextInt(61);

            double sx = 0D;
            double sy = 0D;
            double sz = 0D;
            double lastOutX = 0D;
            double lastOutZ = 0D;
            double body = 0D;
            double head = 0D;
            double lastSpeed = 0D;
            double lastGap = 0D;
            double dirX = 0D;
            double dirZ = 0D;
            double moveFactor = 0D;
            double speedScale = 1D;
            double pace = 1D;
            double paceTarget;
            boolean chasing = true;
            int runTicks = 0;
            int sprintLeft = 0;
            int tireLeft = 0;
            double sprintPace = 1.2D;
            double tirePace = 0.7D;
            double[] bodyWant = new double[ticks + 1];

            /* Its way round the obstacles, on the mesh, from where it is to its place: found again as the target moves */
            List<NavGrid.Node> route = null;
            int routeNext = 1;
            int lastPlan = -100;
            NavGrid.Node lastDestination = null;

            for (int t = start; t <= ticks; t++)
            {
                double[] goal = followGoal(params, tx[t], ty[t], tz[t], heading[t]);

                /* The slot on the stop circle round the place followed, turned only with the heading */
                double slotRadians = Math.toRadians(heading[t] + slot);
                double px = goal[0] - Math.sin(slotRadians) * min;
                double pz = goal[2] + Math.cos(slotRadians) * min;
                double beforeX = sx;
                double beforeZ = sz;

                if (t == start)
                {
                    /* A follower starts where it stands at that tick, and walks from there */
                    sx = ox[t];
                    sy = oy[t];
                    sz = oz[t];

                    double heading0 = Math.toRadians(heading[t]);

                    dirX = -Math.sin(heading0);
                    dirZ = Math.cos(heading0);
                    lastGap = Math.hypot(px - sx, pz - sz);
                }
                else
                {
                    /* Straight at its place, unless the mesh has a way round something in between */
                    double aimX = px;
                    double aimZ = pz;
                    double gap = Math.hypot(px - sx, pz - sz);
                    double ease = alpha;

                    if (params.followGrid != null)
                    {
                        NavGrid.Node here = nodeNear(params.followGrid, sx, sy, sz);
                        NavGrid.Node destination = nodeNear(params.followGrid, px, ty[t] - (params.followCamera != null ? EYE : 0D), pz);

                        if (here == null || destination == null)
                        {
                            route = null;
                        }
                        else if (route == null || t - lastPlan >= REPLAN_TICKS || (destination != lastDestination && t - lastPlan >= 3))
                        {
                            List<NavGrid.Node> path = params.followGrid.path(here, destination);

                            route = path != null && path.size() > 1 ? path : null;
                            routeNext = 1;
                            lastPlan = t;
                            lastDestination = destination;
                        }

                        if (route != null)
                        {
                            /* Past the points it has already reached */
                            while (routeNext < route.size() - 1 && Math.hypot(route.get(routeNext).centerX() - sx, route.get(routeNext).centerZ() - sz) < 0.35D)
                            {
                                routeNext++;
                            }

                            if (routeNext < route.size() - 1)
                            {
                                NavGrid.Node way = route.get(routeNext);
                                double left = Math.hypot(way.centerX() - sx, way.centerZ() - sz);

                                for (int i = routeNext; i < route.size() - 1; i++)
                                {
                                    left += Math.hypot(route.get(i + 1).centerX() - route.get(i).centerX(), route.get(i + 1).centerZ() - route.get(i).centerZ());
                                }

                                aimX = way.centerX();
                                aimZ = way.centerZ();
                                gap = left + Math.hypot(px - route.get(route.size() - 1).centerX(), pz - route.get(route.size() - 1).centerZ());
                                ease = 1D;
                            }
                        }
                    }

                    double stepX = (aimX - sx) * ease;
                    double stepZ = (aimZ - sz) * ease;

                    if (ranged)
                    {
                        /* Out of reach the follower slows to a stop; back in range it runs again */
                        double away = Math.hypot(tx[t] - sx, tz[t] - sz);

                        if (chasing && away > range * LOSE_FACTOR)
                        {
                            chasing = false;
                        }
                        else if (!chasing && away <= range)
                        {
                            chasing = true;
                        }

                        speedScale += ((chasing ? 1D : 0D) - speedScale) * 0.12D;
                    }

                    {
                        paceTarget = 1D;

                        if (organic)
                        {
                            /* A gap that opens is a reason to sprint, a long run without a pause one to tire */
                            if (sprintLeft > 0)
                            {
                                sprintLeft--;
                                paceTarget = sprintPace;
                            }
                            else if (tireLeft > 0)
                            {
                                tireLeft--;
                                paceTarget = tirePace;
                            }
                            else if (gap > 2.5D && gap > lastGap + 0.02D)
                            {
                                sprintLeft = 15 + random.nextInt(26);
                                sprintPace = 1.15D + random.nextDouble() * 0.15D;
                            }
                            else if (runTicks >= nextTire)
                            {
                                tireLeft = 18 + random.nextInt(23);
                                tirePace = 0.65D + random.nextDouble() * 0.15D;
                                runTicks = 0;
                                nextTire = 80 + random.nextInt(61);
                            }

                            pace += (paceTarget - pace) * 0.2D;
                        }

                        double effective = organic ? Math.max(0.3D, 1D + (pace - 1D) * intensity) : 1D;
                        /* Near its place it slows down, to a stop if the target stands, to the target's own pace if it walks */
                        double cap = Math.min(stepSpeed * effective, gap * 0.5D + targetSpeed[t]) * speedScale;
                        double length = Math.hypot(stepX, stepZ);

                        if (length > cap)
                        {
                            stepX *= cap / length;
                            stepZ *= cap / length;
                        }
                    }

                    sx += stepX;
                    sz += stepZ;
                    lastGap = gap;
                }

                /* Never inside the circle: whatever is, even when the target walks into it, goes back out along the line from the centre */
                double[] kept = outside(sx, sz, goal, min, slotRadians);

                sx = kept[0];
                sz = kept[1];

                /* Its height is that of the floor it walks on, not the target's: a jump, a crouch, a ladder are the target's own */
                if (t > start)
                {
                    double floor = floorFor(params, sx, sy, sz);

                    if (!Double.isNaN(floor))
                    {
                        sy += Math.max(-0.3D, Math.min(0.3D, floor + params.followY - sy));
                    }
                }

                double speed = Math.hypot(sx - beforeX, sz - beforeZ);

                if (t > start)
                {
                    lastSpeed = speed;

                    if (speed > 0.03D)
                    {
                        runTicks++;
                        dirX = (sx - beforeX) / speed;
                        dirZ = (sz - beforeZ) / speed;
                    }
                    else
                    {
                        runTicks = Math.max(0, runTicks - 2);
                    }
                }

                /* The sway to either side of the way it runs: only while it runs, and fading as it stops */
                double outX = sx;
                double outZ = sz;

                moveFactor += ((lastSpeed > 0.03D ? 1D : 0D) - moveFactor) * 0.25D;

                if (organic && intensity > 0D)
                {
                    double wave = 0.6D * Math.sin(Math.PI * 2D * t / period[0] + phase[0])
                        + 0.3D * Math.sin(Math.PI * 2D * t / period[1] + phase[1])
                        + 0.1D * Math.sin(Math.PI * 2D * t / period[2] + phase[2]);
                    double sway = SWAY * intensity * moveFactor * wave;

                    outX = sx - dirZ * sway;
                    outZ = sz + dirX * sway;

                    double[] swayed = outside(outX, outZ, goal, min, slotRadians);

                    outX = swayed[0];
                    outZ = swayed[1];
                }

                appendKey(x, t, outX);
                appendKey(z, t, outZ);

                if (!params.followKeepGround)
                {
                    appendKey(y, t, sy);
                }

                if (yaws.length == 0)
                {
                    lastOutX = outX;
                    lastOutZ = outZ;

                    continue;
                }

                double dx = outX - lastOutX;
                double dz = outZ - lastOutZ;
                double toTarget = Math.atan2(tz[t] - outZ, tx[t] - outX) * (180D / Math.PI) - 90D;
                boolean targetClose = (tx[t] - outX) * (tx[t] - outX) + (tz[t] - outZ) * (tz[t] - outZ) > 1.0E-6D;
                double wanted;

                if (t > start && dx * dx + dz * dz > WALK_STEP * WALK_STEP)
                {
                    wanted = Math.atan2(dz, dx) * (180D / Math.PI) - 90D;
                }
                else
                {
                    wanted = targetClose ? toTarget : (t == start ? tyaw[t] : body);
                }

                bodyWant[t] = wanted;

                if (t == start)
                {
                    body = hasStartBody ? startBody : wanted;
                    head = body;

                    if (yaws.length > 2 && yaws[2] != null)
                    {
                        head = yaws[2].getFactory().getY(yaws[2].interpolate(start));
                    }
                }
                else if (organic)
                {
                    /* The head locks on the target at once; the body comes after it a few ticks late, with a little sway of its own */
                    double lagged = bodyWant[Math.max(start, t - BODY_LAG)];
                    double wobble = 4D * intensity * moveFactor * Math.sin(Math.PI * 2D * t / 14D + bodyPhase);

                    head = turnToward(head, targetClose ? toTarget : wanted, 34D);
                    body = turnToward(body, lagged + wobble, 12D);
                }
                else
                {
                    body = turnToward(body, wanted, BODY_TURN_RATE);
                    head = turnToward(head, wanted, HEAD_TURN_RATE);
                }

                if (yaws[0] != null) appendKey(yaws[0], t, body);
                if (yaws.length > 1 && yaws[1] != null) appendKey(yaws[1], t, body);
                if (yaws.length > 2 && yaws[2] != null) appendKey(yaws[2], t, head);

                lastOutX = outX;
                lastOutZ = outZ;
            }
        }

        return null;
    }

    /** The mesh's node at a point, or null when the point is not within a couple of blocks of the mesh, which is a place to walk straight */
    private static NavGrid.Node nodeNear(NavGrid grid, double x, double y, double z)
    {
        NavGrid.Node node = grid.nearest(x, y, z);

        return node != null && Math.hypot(node.centerX() - x, node.centerZ() - z) <= 2.5D ? node : null;
    }

    /** The floor under a follower: the mesh's, or the world's when the mesh has none there; NaN when nothing is known */
    private static double floorFor(NormalParams params, double x, double y, double z)
    {
        double floor = params.followGrid == null ? Double.NaN : params.followGrid.floorAt(x, y, z);

        if (Double.isNaN(floor) && params.followGround != null)
        {
            floor = params.followGround.groundY(x, y, z);
        }

        return floor;
    }

    /** Puts a point that is inside the stop circle back out on it, along the line from the centre */
    private static double[] outside(double x, double z, double[] centre, double min, double slotRadians)
    {
        double ex = x - centre[0];
        double ez = z - centre[2];
        double distance = Math.sqrt(ex * ex + ez * ez);

        if (min <= 0D || distance >= min)
        {
            return new double[] {x, z};
        }

        double ux = distance > 1.0E-6D ? ex / distance : -Math.sin(slotRadians);
        double uz = distance > 1.0E-6D ? ez / distance : Math.cos(slotRadians);

        return new double[] {centre[0] + ux * min, centre[2] + uz * min};
    }

    /** Drops the keyframes of a channel from a tick onwards, keeping what came before */
    private static void clearFrom(KeyframeChannel channel, int tick)
    {
        if (tick <= 0)
        {
            channel.removeAll();

            return;
        }

        List keyframes = channel.getKeyframes();

        for (int i = keyframes.size() - 1; i >= 0; i--)
        {
            if (((Keyframe) keyframes.get(i)).getTick() >= tick)
            {
                channel.remove(i);
            }
        }
    }

    /** The target's place plus the offset, the offset turned by the way the target is heading */
    private static double[] followGoal(NormalParams params, double x, double y, double z, double heading)
    {
        double radians = Math.toRadians(heading);
        double fx = -Math.sin(radians);
        double fz = Math.cos(radians);
        double rx = -Math.cos(radians);
        double rz = -Math.sin(radians);

        return new double[] {
            x + rx * params.followX + fx * params.followZ,
            y + params.followY,
            z + rz * params.followX + fz * params.followZ
        };
    }

    /** An angle taken toward another the short way round by at most a rate; the result is kept near the first, never wrapped */
    private static double turnToward(double from, double to, double rate)
    {
        double diff = (to - from) % 360D;

        if (diff < -180D)
        {
            diff += 360D;
        }
        else if (diff > 180D)
        {
            diff -= 360D;
        }

        return from + Math.max(-rate, Math.min(rate, diff));
    }

    /** Adds a keyframe at the end of a channel that is being written from its first tick onwards */
    private static void appendKey(KeyframeChannel channel, int tick, double value)
    {
        channel.add(new Keyframe("", channel.getFactory(), (float) tick, channel.getFactory().yToValue(value)));
    }

    private static Error fitHeightToGround(List<VisibleReplay> selected, GroundProvider groundProvider)
    {
        for (VisibleReplay replay : selected)
        {
            KeyframeChannel xChannel = (KeyframeChannel) replay.replay.keyframes.get("x");
            KeyframeChannel yChannel = (KeyframeChannel) replay.replay.keyframes.get("y");
            KeyframeChannel zChannel = (KeyframeChannel) replay.replay.keyframes.get("z");

            if (xChannel == null || yChannel == null || zChannel == null)
            {
                return Error.NEED_POSITION_CHANNELS;
            }

            double x = xChannel.getFactory().getY(xChannel.interpolate(0F));
            double y = yChannel.getFactory().getY(yChannel.interpolate(0F));
            double z = zChannel.getFactory().getY(zChannel.interpolate(0F));
            double groundY = groundProvider.getGroundY(x, z);

            if (!Double.isNaN(groundY))
            {
                applyDelta(replay.replay, "y", groundY - y);
            }
        }

        return null;
    }

    private static Error lookAt(List<VisibleReplay> selected, Replay target)
    {
        KeyframeChannel targetX = (KeyframeChannel) target.keyframes.get("x");
        KeyframeChannel targetY = (KeyframeChannel) target.keyframes.get("y");
        KeyframeChannel targetZ = (KeyframeChannel) target.keyframes.get("z");

        if (targetX == null || targetY == null || targetZ == null)
        {
            return Error.NEED_POSITION_CHANNELS;
        }

        for (VisibleReplay replay : selected)
        {
            if (replay.replay == target)
            {
                continue;
            }

            KeyframeChannel srcX = (KeyframeChannel) replay.replay.keyframes.get("x");
            KeyframeChannel srcY = (KeyframeChannel) replay.replay.keyframes.get("y");
            KeyframeChannel srcZ = (KeyframeChannel) replay.replay.keyframes.get("z");

            if (srcX == null || srcY == null || srcZ == null)
            {
                return Error.NEED_POSITION_CHANNELS;
            }

            KeyframeChannel yaw = (KeyframeChannel) replay.replay.keyframes.get("yaw");
            KeyframeChannel pitch = (KeyframeChannel) replay.replay.keyframes.get("pitch");

            if (yaw == null || pitch == null)
            {
                continue;
            }

            applyLookAtToChannel(yaw, srcX, srcY, srcZ, targetX, targetY, targetZ, true);
            applyLookAtToChannel(pitch, srcX, srcY, srcZ, targetX, targetY, targetZ, false);

            KeyframeChannel headYaw = (KeyframeChannel) replay.replay.keyframes.get("headYaw");
            if (headYaw != null)
            {
                applyLookAtToChannel(headYaw, srcX, srcY, srcZ, targetX, targetY, targetZ, true);
            }

            KeyframeChannel bodyYaw = (KeyframeChannel) replay.replay.keyframes.get("bodyYaw");
            if (bodyYaw != null)
            {
                applyLookAtToChannel(bodyYaw, srcX, srcY, srcZ, targetX, targetY, targetZ, true);
            }
        }

        return null;
    }

    private static void applyLookAtToChannel(KeyframeChannel channel, KeyframeChannel srcX, KeyframeChannel srcY, KeyframeChannel srcZ, KeyframeChannel targetX, KeyframeChannel targetY, KeyframeChannel targetZ, boolean yaw)
    {
        List keyframes = channel.getKeyframes();

        if (keyframes.isEmpty())
        {
            double value = computeLookAtValue(0F, srcX, srcY, srcZ, targetX, targetY, targetZ, yaw);
            channel.insert(0F, channel.getFactory().yToValue(value));
            return;
        }

        double prevAngle = Double.NaN;

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe kf = (Keyframe) keyframes.get(i);
            float tick = (float) kf.getTick();
            double value = computeLookAtValue(tick, srcX, srcY, srcZ, targetX, targetY, targetZ, yaw);

            if (!Double.isNaN(prevAngle))
            {
                double diff = (value - prevAngle) % 360D;
                if (diff < -180D)
                {
                    diff += 360D;
                }
                else if (diff > 180D)
                {
                    diff -= 360D;
                }
                value = prevAngle + diff;
            }

            prevAngle = value;
            kf.setValue(kf.getFactory().yToValue(value));
        }
    }

    private static double computeLookAtValue(float tick, KeyframeChannel srcX, KeyframeChannel srcY, KeyframeChannel srcZ, KeyframeChannel targetX, KeyframeChannel targetY, KeyframeChannel targetZ, boolean isYaw)
    {
        double sx = srcX.getFactory().getY(srcX.interpolate(tick));
        double sy = srcY.getFactory().getY(srcY.interpolate(tick));
        double sz = srcZ.getFactory().getY(srcZ.interpolate(tick));
        double tx = targetX.getFactory().getY(targetX.interpolate(tick));
        double ty = targetY.getFactory().getY(targetY.interpolate(tick));
        double tz = targetZ.getFactory().getY(targetZ.interpolate(tick));

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;

        if (isYaw)
        {
            return Math.atan2(dz, dx) * (180D / Math.PI) - 90D;
        }

        double h = Math.sqrt(dx * dx + dz * dz);
        return -Math.atan2(dy, h) * (180D / Math.PI);
    }

    private static void applyDelta(Replay replay, String id, double delta)
    {
        KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(id);

        if (channel == null || !KeyframeFactories.isNumeric(channel.getFactory()))
        {
            return;
        }

        List keyframes = channel.getKeyframes();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe kf = (Keyframe) keyframes.get(i);
            double v = kf.getFactory().getY(kf.getValue());

            kf.setValue(kf.getFactory().yToValue(v + delta), true);
        }
    }

    private static long mix64(long z)
    {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static int cubeSurfacePoints(int side)
    {
        if (side <= 1)
        {
            return 1;
        }

        int inner = Math.max(0, side - 2);

        return side * side * side - inner * inner * inner;
    }

    private static int[] buildCubeShellCoords(int count, int side)
    {
        int[] out = new int[count * 3];
        int i = 0;

        for (int z = 0; z < side && i < count; z++)
        {
            for (int y = 0; y < side && i < count; y++)
            {
                for (int x = 0; x < side && i < count; x++)
                {
                    if (x != 0 && x != side - 1 && y != 0 && y != side - 1 && z != 0 && z != side - 1)
                    {
                        continue;
                    }

                    int j = i * 3;

                    out[j] = x;
                    out[j + 1] = y;
                    out[j + 2] = z;
                    i++;
                }
            }
        }

        return out;
    }
}
