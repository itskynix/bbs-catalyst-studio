package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
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

        double alpha = 1D / (Math.max(0D, params.followSmooth) + 1D);
        double min = Math.max(0D, params.followMinDistance);
        double step = min > 0D ? SLOT_SPACING / Math.max(min, 0.4D) : 0D;

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

            /* Where the follower starts from decides which side of the target it keeps to */
            double ox = x.getFactory().getY(x.interpolate(0F));
            double oz = z.getFactory().getY(z.interpolate(0F));
            double[] goal0 = followGoal(params, tx[0], ty[0], tz[0], heading[0]);
            double ax = ox - goal0[0];
            double az = oz - goal0[2];

            /* The slot, as an angle from the heading: the side it came from, or behind when it came from nowhere */
            double approach = ax * ax + az * az > 1.0E-6D
                ? Math.atan2(az, ax) * (180D / Math.PI) - 90D
                : heading[0] + 180D;
            double slot = approach - heading[0] + Math.toDegrees((n - (followers.size() - 1) / 2D) * step);

            KeyframeChannel[] yaws = params.followRotate ? new KeyframeChannel[] {
                (KeyframeChannel) replay.replay.keyframes.get("yaw"),
                (KeyframeChannel) replay.replay.keyframes.get("bodyYaw"),
                (KeyframeChannel) replay.replay.keyframes.get("headYaw")
            } : new KeyframeChannel[0];

            x.removeAll();
            z.removeAll();

            if (!params.followKeepGround)
            {
                y.removeAll();
            }

            for (KeyframeChannel yaw : yaws)
            {
                if (yaw != null)
                {
                    yaw.removeAll();
                }
            }

            double sx = 0D;
            double sy = 0D;
            double sz = 0D;
            double body = 0D;
            double head = 0D;

            for (int t = 0; t <= ticks; t++)
            {
                double[] goal = followGoal(params, tx[t], ty[t], tz[t], heading[t]);

                /* The slot on the stop circle round the place followed, turned only with the heading */
                double slotRadians = Math.toRadians(heading[t] + slot);
                double px = goal[0] - Math.sin(slotRadians) * min;
                double pz = goal[2] + Math.cos(slotRadians) * min;
                double lastX = sx;
                double lastZ = sz;

                if (t == 0)
                {
                    sx = px;
                    sy = goal[1];
                    sz = pz;
                }
                else
                {
                    sx += (px - sx) * alpha;
                    sy += (goal[1] - sy) * alpha;
                    sz += (pz - sz) * alpha;
                }

                /* Never inside the circle: whatever is, even when the target walks into it, goes back out along the line from the centre */
                double ex = sx - goal[0];
                double ez = sz - goal[2];
                double distance = Math.sqrt(ex * ex + ez * ez);

                if (min > 0D && distance < min)
                {
                    double ux = distance > 1.0E-6D ? ex / distance : -Math.sin(slotRadians);
                    double uz = distance > 1.0E-6D ? ez / distance : Math.cos(slotRadians);

                    sx = goal[0] + ux * min;
                    sz = goal[2] + uz * min;
                }

                appendKey(x, t, sx);
                appendKey(z, t, sz);

                if (!params.followKeepGround)
                {
                    appendKey(y, t, sy);
                }

                if (!params.followRotate)
                {
                    continue;
                }

                double dx = sx - lastX;
                double dz = sz - lastZ;
                double wanted;

                if (t > 0 && dx * dx + dz * dz > WALK_STEP * WALK_STEP)
                {
                    wanted = Math.atan2(dz, dx) * (180D / Math.PI) - 90D;
                }
                else
                {
                    double fx = tx[t] - sx;
                    double fz = tz[t] - sz;

                    wanted = fx * fx + fz * fz > 1.0E-6D ? Math.atan2(fz, fx) * (180D / Math.PI) - 90D : (t == 0 ? tyaw[0] : body);
                }

                if (t == 0)
                {
                    body = wanted;
                    head = wanted;
                }
                else
                {
                    body = turnToward(body, wanted, BODY_TURN_RATE);
                    head = turnToward(head, wanted, HEAD_TURN_RATE);
                }

                if (yaws.length > 0)
                {
                    if (yaws[0] != null) appendKey(yaws[0], t, body);
                    if (yaws[1] != null) appendKey(yaws[1], t, body);
                    if (yaws[2] != null) appendKey(yaws[2], t, head);
                }
            }
        }

        return null;
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
