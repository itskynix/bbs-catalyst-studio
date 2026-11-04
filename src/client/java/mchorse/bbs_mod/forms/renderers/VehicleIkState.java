package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.film.VehicleRide;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueVehicleParts;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The vehicle IK of one model form: what its wheels, steering and body do, worked out from how the
 * actor carrying it moves and from the ground under it. Motion runs on the film's ticks (the actor's
 * previous and current place and yaw), so a re-rendered or exported take moves the same way, and
 * every value keeps its previous tick so the frames in between are interpolated.
 *
 * <ul>
 *     <li>Wheels roll by the distance travelled along the vehicle's forward axis over their radius.</li>
 *     <li>Front wheels steer by the bicycle model: the angle that turning rate needs at this speed
 *     (wheelbase {@link #WHEELBASE}), clamped to ±{@link #MAX_STEER}°; the steering wheel turns
 *     {@link #STEERING_RATIO} times as far.</li>
 *     <li>The whole vehicle, wheels and all, stands on the ground under its four wheels: tilted to
 *     the plane they make and settled onto it, each wheel then hanging into a hole or pushed up by a
 *     bump as far as its suspension goes. A drive (and the take it records) says what that ground is
 *     in the actor's own pitch and lean; a vehicle standing in a world finds it itself, every tick,
 *     on the client's clock when its film is paused.</li>
 *     <li>Over the wheels the body - the body bone and every part beside it that is not a wheel:
 *     doors, seats, the steering wheel, the hood - sways as one: nose down under braking, outward in
 *     a corner, lagging behind a step it is lifted over.</li>
 * </ul>
 *
 * Steering, body and ground settle through critically damped springs, so nothing snaps and a
 * straight line returns the wheel to centre without overshoot.
 */
public class VehicleIkState
{
    private static final float TICK = 0.05F;
    private static final float WHEELBASE = 2.5F;
    private static final float MAX_STEER = 35F;
    private static final float STEERING_RATIO = 3F;
    private static final float MAX_BODY = 6F;
    /** How far the ground tilts a vehicle, in degrees */
    private static final float MAX_SLOPE = 30F;
    /** How far the suspension takes up a step, in blocks */
    private static final float MAX_LIFT = 1.5F;
    /** Faster than this (blocks per second) is a teleport or a cut, not driving */
    private static final float MAX_SPEED = 80F;
    /** How far a wheel hangs below the plane of the others over a hole, and a bump pushes it up into its arch, in blocks */
    private static final float DROOP = 0.4F;
    private static final float BUMP = 0.3F;
    /** How far below its wheels a standing vehicle looks for the ground, in blocks: a fall of that far is one it can fall */
    private static final double GROUND_REACH = 64D;
    /** The pull on a vehicle with nothing under it, in blocks a tick a tick (the player's is twice this), and the fastest it falls, in blocks a tick */
    private static final float GRAVITY = 0.04F;
    private static final float TERMINAL = 3F;
    /** The springs a standing vehicle settles on, per second: the body onto the ground, a wheel into a hole */
    private static final float SETTLE_OMEGA = 7F;
    private static final float WHEEL_OMEGA = 14F;
    /** Client ticks a vehicle keeps finding its ground for after it was last drawn */
    private static final int UNSEEN_TICKS = 40;
    /** A jump this far between two looks at the ground is a cut: the vehicle stands there at once */
    private static final double GROUND_CUT = 3D;

    private boolean primed;
    private float speed;

    private float wheel;
    private float prevWheel;

    private float steer;
    private float prevSteer;
    private float steerVelocity;

    /* The body's sway, in degrees about the model's X (nose up) and Z: carrying the ground too
     * while it is known (see tick), so the body lags behind a change of it on its springs */
    private float pitch;
    private float prevPitch;
    private float pitchVelocity;

    private float roll;
    private float prevRoll;
    private float rollVelocity;

    /* The body's height against the wheels, in blocks: a step taken up by the suspension */
    private float lift;
    private float prevLift;
    private float liftVelocity;

    /** The diagnostic log of the vehicle IK tab: once a second, what every vehicle measured (never saved) */
    public static boolean debug;

    /* Where this vehicle was at its last tick, kept here: the film controllers tick an actor with
     * entity.update() first, which makes the previous position the current one, and only then tick
     * its form - read from the entity, the motion was always zero */
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;

    private int debugTicks;
    /** The driver's steering, -1 right to 1 left, or NaN when nobody drives it (see step) */
    private float steerInput = Float.NaN;
    private float lastTurn;

    /* The ground a drive or a recorded take gives: the actor's own climb and lean, in degrees
     * (nose up, left side up), by the film's ticks */
    private boolean known;
    private float knownPitch;
    private float prevKnownPitch;
    private float knownRoll;
    private float prevKnownRoll;

    /* The ground under the four wheels as this vehicle finds it: the plane they make as turns about
     * the model's X and Z (radians) and how far the vehicle settles onto it (blocks), then how far
     * each wheel (FL, FR, RL, RR) hangs below that plane or is pushed up off it. Stepped on the
     * film's tick while the film runs, on the client's otherwise (see tickGround) */
    private final float[] ground = new float[3];
    private final float[] prevGround = new float[3];
    private final float[] groundVelocity = new float[3];
    private final float[] droop = new float[4];
    private final float[] prevDroop = new float[4];
    private final float[] droopVelocity = new float[4];
    /** How fast the vehicle falls onto the ground that opened under it, in blocks a tick (negative is down) */
    private float fall;
    /** The ground's height in the world under each wheel at the last look; NaN with none found */
    private final double[] wheelGround = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    private boolean groundPrimed;
    private boolean groundByFilm;
    private long groundClock = -1L;
    private double groundX;
    private double groundZ;
    private int unseen;
    private WeakReference<IEntity> groundEntity;
    private ModelForm groundForm;

    private static final Set<VehicleIkState> GROUNDED = Collections.newSetFromMap(new WeakHashMap<>());
    private static long clientTicks;

    /* The parts that open, by bone: how far open each is at the last tick and this one (0 shut,
     * 1 open) and where it is going. Ticked by the client's clock, not the film's, so a door still
     * swings in a paused film; see {@link #tickDoors()} */
    private final Map<String, float[]> doors = new HashMap<>();

    private static final Set<VehicleIkState> MOVING = Collections.newSetFromMap(new WeakHashMap<>());
    /** A part swings fully open or shut in this many ticks (0.3 s) */
    private static final float DOOR_TICKS = 6F;

    public void tick(IEntity entity, ModelForm form)
    {
        this.prevWheel = this.wheel;
        this.prevSteer = this.steer;
        this.prevPitch = this.pitch;
        this.prevRoll = this.roll;
        this.prevLift = this.lift;
        this.prevKnownPitch = this.knownPitch;
        this.prevKnownRoll = this.knownRoll;

        if (entity == null || !form.vehicleIk.get())
        {
            this.reset();

            return;
        }

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        float yaw = entity.getBodyYaw();
        VehicleGeometry.Wheels wheels = VehicleGeometry.wheels(form);

        /* A drive (and the replay it records) carries the ground in the actor's pitch and lean: the
         * front axle up a step before the rear, as the wheelbase has it. The lean is in hundredths
         * of a degree (the replay's roll channel holds whole numbers) */
        boolean known = form.pitchFromReplay.get() || VehicleRide.isRidden(entity);
        float oldX = this.known ? this.knownPitch * wheels.front : 0F;
        float oldZ = this.known ? -this.knownRoll * wheels.left : 0F;

        this.knownPitch = known ? entity.getPitch() : 0F;
        this.knownRoll = known ? entity.getRoll() / 100F : 0F;

        float newX = known ? this.knownPitch * wheels.front : 0F;
        float newZ = known ? -this.knownRoll * wheels.left : 0F;

        if (known != this.known || !this.hasLast)
        {
            /* The body springs carry the known ground, and stop carrying it once it is the vehicle's
             * own to find: moved over by the difference, the body stays where it was in the world */
            if (this.hasLast)
            {
                this.pitch += newX - oldX;
                this.prevPitch += newX - oldX;
                this.roll += newZ - oldZ;
                this.prevRoll += newZ - oldZ;
            }

            this.prevKnownPitch = this.knownPitch;
            this.prevKnownRoll = this.knownRoll;
            this.known = known;
        }

        if (this.hasLast)
        {
            /* Standing in a world, the ground carries the whole vehicle and the body springs take its
             * motion only; anywhere else (a form previewed in a menu) the slope is worked out from it */
            float terrain = known ? newX : VehicleRide.isFilmActor(entity) ? 0F : Float.NaN;

            this.steerInput = VehicleRide.getSteering(entity);
            this.step(x - this.lastX, y - this.lastY, z - this.lastZ, yaw, this.lastYaw, form, terrain, newZ);
        }

        this.hasLast = true;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;

        /* The ground under the wheels, by the film's clock while the film runs */
        this.see(entity, form);
        this.stepGround(true);

        if (debug && ++this.debugTicks >= 20)
        {
            this.debugTicks = 0;

            BBSMod.LOGGER.info(String.format(Locale.ROOT,
                "[VehicleIK] %s speed=%.2f b/s turn=%.1f deg/s steer=%.1f sway=%.2f/%.2f lift=%.3f ground=%.2f/%.2f deg settle=%.3f known=%s wheel=%.2f rad bones=[body=%s steering=%s FL=%s FR=%s RL=%s RR=%s]",
                form.model.get(), this.speed, this.lastTurn, this.steer, this.pitch, this.roll, this.lift,
                Math.toDegrees(this.ground[0]), Math.toDegrees(this.ground[1]), this.ground[2], this.known, this.wheel,
                form.boneBody.get(), form.boneSteering.get(), form.boneWheelFL.get(), form.boneWheelFR.get(), form.boneWheelRL.get(), form.boneWheelRR.get()));
        }
    }

    /**
     * One tick of motion: how far the actor went, and its yaw now and a tick ago.
     *
     * @param terrain     the ground's climb (degrees about the model's X) the body springs carry, or NaN
     *                    to work it out from the slope the actor moves along
     * @param terrainRoll the same for the lean, about the model's Z
     */
    private void step(double dx, double dy, double dz, float yaw, float prevYaw, ModelForm form, float terrain, float terrainRoll)
    {
        double yawRad = Math.toRadians(yaw);
        /* Minecraft's forward for a yaw: -sin on X, cos on Z */
        float forward = (float) (dx * -Math.sin(yawRad) + dz * Math.cos(yawRad));
        float v = forward / TICK;
        float turn = wrapDegrees(yaw - prevYaw) / TICK;

        this.lastTurn = turn;

        if (!this.primed || Math.abs(v) > MAX_SPEED || Math.abs(turn) > 720F || Math.abs(dy) > 3D)
        {
            /* The first tick, or a jump in place: nothing to learn a motion from */
            this.primed = true;
            this.speed = Math.abs(v) > MAX_SPEED ? 0F : v;

            return;
        }

        /* A step up or down in one tick (a block's edge): the wheels follow it, the body is caught
         * by the suspension and rises after them instead of jumping - "tak tak" becomes a soft lift */
        if (Math.abs(dy) > 0.2D)
        {
            this.lift = MathUtils.clamp(this.lift - (float) dy, -MAX_LIFT, MAX_LIFT);
        }

        float accel = (v - this.speed) / TICK;

        this.speed = v;

        /* Rolling: the arc a wheel of this radius covers over the distance travelled. Which way it
         * rolls is the drawing's business (see apply), so the switch turns it at once. Rolled back
         * against the travel: the models turn their wheels about X the other way round to the
         * actor's forward, so this is what rolls them forward by default */
        float radius = Math.max(form.wheelRadius.get(), 0.05F);

        this.wheel -= v * TICK / radius;

        /* Kept small, both together, so the in-between frames never interpolate across a jump */
        if (Math.abs(this.wheel) > MathUtils.PI * 8F)
        {
            float wrap = MathUtils.PI * 2F * (float) Math.floor(this.wheel / (MathUtils.PI * 2F));

            this.wheel -= wrap;
            this.prevWheel -= wrap;
        }

        float steerTarget = 0F;

        if (Math.abs(v) > 0.3F)
        {
            /* Over the signed speed: backing up, the same turn takes the wheels the other way, so a
             * turn the driver steered left still shows the wheels left */
            steerTarget = (float) Math.toDegrees(Math.atan(WHEELBASE * Math.toRadians(turn) / v));
        }

        /* Driven: the wheels follow the driver's hands, standing still too (left takes it down) */
        if (!Float.isNaN(this.steerInput))
        {
            steerTarget = -this.steerInput * MAX_STEER;
        }

        steerTarget = MathUtils.clamp(steerTarget * form.steeringMultiplier.get(), -MAX_STEER, MAX_STEER);

        float strength = form.suspensionStrength.get();
        /* The nose follows the slope it drives on: up a ramp nose up, down a ramp nose down */
        float slope = !Float.isNaN(terrain) ? terrain : Math.abs(forward) > 0.01F && Math.abs(dy) <= 0.2D
            ? (float) Math.toDegrees(Math.atan2(dy, Math.abs(forward))) * Math.signum(forward)
            : 0F;
        float pitchTarget = MathUtils.clamp(MathUtils.clamp(accel * 0.35F * strength, -MAX_BODY, MAX_BODY) + MathUtils.clamp(slope, -MAX_SLOPE, MAX_SLOPE), -MAX_SLOPE, MAX_SLOPE);
        float rollTarget = MathUtils.clamp((float) -Math.toRadians(turn) * v * 0.3F * strength, -MAX_BODY, MAX_BODY);

        rollTarget = MathUtils.clamp(rollTarget + MathUtils.clamp(terrainRoll, -MAX_SLOPE, MAX_SLOPE), -MAX_SLOPE, MAX_SLOPE);

        float[] s = {this.steer, this.steerVelocity};
        float[] p = {this.pitch, this.pitchVelocity};
        float[] r = {this.roll, this.rollVelocity};

        spring(s, steerTarget, 10F);
        spring(p, pitchTarget, 7F);
        spring(r, rollTarget, 7F);

        this.steer = s[0];
        this.steerVelocity = s[1];
        this.pitch = p[0];
        this.pitchVelocity = p[1];
        this.roll = r[0];
        this.rollVelocity = r[1];

        float[] l = {this.lift, this.liftVelocity};

        spring(l, 0F, 9F);

        this.lift = l[0];
        this.liftVelocity = l[1];
    }

    /* The ground */

    /** Drawn (or ticked) as this actor's form: the ground is looked for under it from now on */
    private void see(IEntity entity, ModelForm form)
    {
        if (this.groundEntity == null || this.groundEntity.get() != entity)
        {
            this.groundEntity = new WeakReference<>(entity);
        }

        this.groundForm = form;
        this.unseen = 0;
        GROUNDED.add(this);
    }

    /** One client tick of every vehicle standing in a world whose film did not tick it (a paused film) */
    public static void tickGround()
    {
        clientTicks += 1;
        GROUNDED.removeIf((state) -> !state.tickGroundOnClient());
    }

    private boolean tickGroundOnClient()
    {
        /* Not drawn for a while (gone, or far away): it rests as it was until it is drawn again */
        if (++this.unseen > UNSEEN_TICKS)
        {
            return false;
        }

        /* Its film ticked it on the last client tick: the film's clock has it */
        if (this.groundClock < clientTicks - 1L)
        {
            this.stepGround(false);
        }

        return true;
    }

    private void stepGround(boolean film)
    {
        this.groundClock = clientTicks;
        this.groundByFilm = film;

        System.arraycopy(this.ground, 0, this.prevGround, 0, 3);
        System.arraycopy(this.droop, 0, this.prevDroop, 0, 4);

        IEntity entity = this.groundEntity == null ? null : this.groundEntity.get();
        ModelForm form = this.groundForm;
        float[] target = new float[3];
        float[] hang = new float[4];
        boolean found = entity != null && form != null && form.vehicleIk.get() && this.findGround(entity, form, target, hang);

        if (!found)
        {
            Arrays.fill(this.wheelGround, Double.NaN);
        }
        else
        {
            boolean cut = Math.abs(entity.getX() - this.groundX) > GROUND_CUT || Math.abs(entity.getZ() - this.groundZ) > GROUND_CUT;

            this.groundX = entity.getX();
            this.groundZ = entity.getZ();

            /* The first look, or a cut: it has stood there all along, there is nothing to settle */
            if (!this.groundPrimed || cut)
            {
                this.groundPrimed = true;

                System.arraycopy(target, 0, this.ground, 0, 3);
                System.arraycopy(target, 0, this.prevGround, 0, 3);
                System.arraycopy(hang, 0, this.droop, 0, 4);
                System.arraycopy(hang, 0, this.prevDroop, 0, 4);
                Arrays.fill(this.groundVelocity, 0F);
                Arrays.fill(this.droopVelocity, 0F);

                return;
            }
        }

        float[] state = new float[2];

        for (int i = 0; i < 3; i++)
        {
            if (i == 2 && found && target[2] < this.ground[2] - 0.002F)
            {
                /* The ground is further down than the vehicle stands: nothing holds it, so it falls -
                 * faster every tick, and stops where it lands. Its tilt follows what it falls onto */
                this.fall = Math.max(this.fall - GRAVITY, -TERMINAL);
                this.ground[2] = Math.max(this.ground[2] + this.fall, target[2]);

                if (this.ground[2] <= target[2])
                {
                    this.fall = 0F;
                }

                this.groundVelocity[2] = 0F;

                continue;
            }

            if (i == 2)
            {
                this.fall = 0F;
            }

            state[0] = this.ground[i];
            state[1] = this.groundVelocity[i];
            spring(state, target[i], SETTLE_OMEGA);
            this.ground[i] = state[0];
            this.groundVelocity[i] = state[1];
        }

        for (int i = 0; i < 4; i++)
        {
            state[0] = this.droop[i];
            state[1] = this.droopVelocity[i];
            spring(state, hang[i], WHEEL_OMEGA);
            this.droop[i] = state[0];
            this.droopVelocity[i] = state[1];
        }
    }

    /**
     * The ground under the four wheels, each found straight below where it touches, from the step
     * height above it to {@link #GROUND_REACH} under: the plane they make (its climb and lean as
     * turns about the model's X and Z, and how far the vehicle settles onto it) into {@code target},
     * and how far each wheel then hangs below the plane the vehicle is drawn on or is pushed up off
     * it into {@code hang}. A wheel over nothing hangs as far as its suspension lets it below the
     * others. False when the actor does not stand in a world or there is no ground in reach.
     */
    private boolean findGround(IEntity entity, ModelForm form, float[] target, float[] hang)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = entity.getWorld();

        if (world == null || mc.player == null || !form.groundSettle.get() || !VehicleRide.isFilmActor(entity))
        {
            return false;
        }

        VehicleGeometry.Wheels wheels = VehicleGeometry.wheels(form);
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double rad = Math.toRadians(entity.getBodyYaw());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double reach = Math.max(form.stepHeight.get(), 0.6F) + 0.05D;
        double[] d = new double[4];
        double sum = 0D;
        int valid = 0;

        for (int i = 0; i < 4; i++)
        {
            Vector3f point = wheels.points[i];
            /* The actor's frame into the world: left (+X) is (cos, sin) for Minecraft's yaw, forward (+Z) is (-sin, cos) */
            double wx = x + point.x * cos - point.z * sin;
            double wz = z + point.x * sin + point.z * cos;
            double contact = y + point.y;
            BlockHitResult hit = world.raycast(new RaycastContext(new Vec3d(wx, contact + reach, wz), new Vec3d(wx, contact - GROUND_REACH, wz),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

            if (hit.getType() == HitResult.Type.MISS)
            {
                d[i] = Double.NaN;

                continue;
            }

            /* Started in a block: the ground stands at least that high */
            d[i] = hit.isInsideBlock() ? reach : hit.getPos().y - contact;
            sum += d[i];
            valid += 1;
        }

        if (valid == 0)
        {
            return false;
        }

        for (int i = 0; i < 4; i++)
        {
            if (Double.isNaN(d[i]))
            {
                d[i] = sum / valid - DROOP;
            }
        }

        double total = d[0] + d[1] + d[2] + d[3];

        for (int i = 0; i < 4; i++)
        {
            d[i] = Math.max(d[i], (total - d[i]) / 3D - DROOP);
            this.wheelGround[i] = y + wheels.points[i].y + d[i];
        }

        /* The plane through them by least squares, in the model's frame: X and Z turned half a turn
         * from the actor's. Its slope b across and c along are the turns about Z and X */
        double mx = 0D;
        double mz = 0D;
        double md = 0D;

        for (int i = 0; i < 4; i++)
        {
            mx += -wheels.points[i].x / 4D;
            mz += -wheels.points[i].z / 4D;
            md += d[i] / 4D;
        }

        double sxx = 0D, szz = 0D, sxz = 0D, sxd = 0D, szd = 0D;

        for (int i = 0; i < 4; i++)
        {
            double px = -wheels.points[i].x - mx;
            double pz = -wheels.points[i].z - mz;
            double pd = d[i] - md;

            sxx += px * px;
            szz += pz * pz;
            sxz += px * pz;
            sxd += px * pd;
            szd += pz * pd;
        }

        double det = sxx * szz - sxz * sxz;
        double b = 0D;
        double c = 0D;

        if (Math.abs(det) > 1.0E-6D)
        {
            b = (sxd * szz - szd * sxz) / det;
            c = (szd * sxx - sxd * sxz) / det;
        }
        else if (sxx > 1.0E-6D)
        {
            b = sxd / sxx;
        }
        else if (szz > 1.0E-6D)
        {
            c = szd / szz;
        }

        float max = MathUtils.toRad(MAX_SLOPE);

        /* A turn about X lifts the model's -Z end (the actor's front) for a plane rising that way */
        target[0] = MathUtils.clamp((float) Math.atan(-c), -max, max);
        target[1] = MathUtils.clamp((float) Math.atan(b), -max, max);
        target[2] = (float) MathUtils.clamp(md, -GROUND_REACH, reach);

        /* The plane the vehicle is drawn on: this one, or the one its drive gives */
        double planeD = md;

        if (this.known)
        {
            b = Math.tan(MathUtils.toRad(-this.knownRoll * wheels.left));
            c = -Math.tan(MathUtils.toRad(this.knownPitch * wheels.front));
            planeD = 0D;
        }

        for (int i = 0; i < 4; i++)
        {
            double plane = planeD + b * (-wheels.points[i].x - mx) + c * (-wheels.points[i].z - mz);

            hang[i] = (float) MathUtils.clamp(d[i] - plane, -DROOP, BUMP);
        }

        return true;
    }

    /**
     * The ground's height in the world under each wheel (FL, FR, RL, RR) as this vehicle last found
     * it, or null when it found none: what a drive starts from, so getting in moves nothing.
     */
    public double[] getWheelGround()
    {
        for (double height : this.wheelGround)
        {
            if (Double.isNaN(height))
            {
                return null;
            }
        }

        return this.wheelGround.clone();
    }

    /**
     * Where a point of the body (in the actor's frame, as the model stands at rest) is drawn now:
     * carried by the ground the whole vehicle stands on and the body's sway over it, at
     * {@code partial} between the last tick and this one. What a seated rider sits on.
     */
    public Vector3f carry(ModelForm form, Vector3f point, float partial)
    {
        Matrix4f[] frames = this.frames(form, partial, partial);
        Vector3f moved = new Vector3f(-point.x, point.y, -point.z);

        if (frames[1] != null)
        {
            frames[1].transformPosition(moved);
        }

        if (frames[0] != null)
        {
            frames[0].transformPosition(moved);
        }

        return moved.set(-moved.x, moved.y, -moved.z);
    }

    /**
     * The ground the whole vehicle stands on and the body's sway over it, as transforms of the
     * model's frame (blocks, before its half turn); null for one that does nothing. The ground turns
     * about the middle of the wheels where they touch it, the body about the middle of the wheels at
     * the body bone's height.
     */
    private Matrix4f[] frames(ModelForm form, float transition, float tickDelta)
    {
        VehicleGeometry.Wheels wheels = VehicleGeometry.wheels(form);
        float pitch = this.prevPitch + (this.pitch - this.prevPitch) * transition;
        float roll = this.prevRoll + (this.roll - this.prevRoll) * transition;
        float lift = this.prevLift + (this.lift - this.prevLift) * transition;
        float groundX;
        float groundZ;
        float settle;

        if (this.known)
        {
            /* The climb straight from the actor, as it is drawn: the form ticks before its film
             * moves the actor on, so what the tick kept of it is a tick behind */
            IEntity entity = this.groundEntity == null ? null : this.groundEntity.get();
            float climb = entity != null
                ? entity.getPrevPitch() + (entity.getPitch() - entity.getPrevPitch()) * transition
                : this.prevKnownPitch + (this.knownPitch - this.prevKnownPitch) * transition;

            groundX = MathUtils.toRad(climb * wheels.front);
            groundZ = MathUtils.toRad(-(this.prevKnownRoll + (this.knownRoll - this.prevKnownRoll) * transition) * wheels.left);
            settle = 0F;
            /* The body springs carry the known ground: what is left over is the body's own sway */
            pitch -= MathUtils.toDeg(groundX);
            roll -= MathUtils.toDeg(groundZ);
        }
        else
        {
            float t = this.groundByFilm ? transition : tickDelta;

            groundX = this.prevGround[0] + (this.ground[0] - this.prevGround[0]) * t;
            groundZ = this.prevGround[1] + (this.ground[1] - this.prevGround[1]) * t;
            settle = this.prevGround[2] + (this.ground[2] - this.prevGround[2]) * t;
        }

        Matrix4f[] frames = new Matrix4f[2];
        Vector3f middle = wheels.middle;

        if (Math.abs(groundX) + Math.abs(groundZ) > 1.0E-5F || Math.abs(settle) > 1.0E-5F)
        {
            frames[0] = new Matrix4f()
                .translate(-middle.x, middle.y + settle, -middle.z)
                .rotate(Matrices.toQuaternionZYXRadians(groundX, 0F, groundZ))
                .translate(middle.x, -middle.y, middle.z);
        }

        Model model = VehicleGeometry.getModel(form);
        ModelGroup body = model == null || form.boneBody.get().isEmpty() ? null : model.getGroup(form.boneBody.get());

        if (body != null && (Math.abs(pitch) + Math.abs(roll) > 1.0E-3F || Math.abs(lift) > 5.0E-4F))
        {
            float height = body.initial.translate.y / 16F;
            float cx = wheels.fromBones ? -middle.x : body.initial.translate.x / 16F;
            float cz = wheels.fromBones ? -middle.z : body.initial.translate.z / 16F;

            frames[1] = new Matrix4f()
                .translate(cx, height + lift, cz)
                .rotate(Matrices.toQuaternionZYXRadians(MathUtils.toRad(pitch), 0F, MathUtils.toRad(roll)))
                .translate(-cx, -height, -cz);
        }

        return frames;
    }

    /* Springs and small things */

    private static float wrapDegrees(float degrees)
    {
        degrees %= 360F;

        if (degrees >= 180F) degrees -= 360F;
        if (degrees < -180F) degrees += 360F;

        return degrees;
    }

    /** A critically damped spring over one tick, in small steps so it stays stable at any stiffness */
    private static void spring(float[] state, float target, float omega)
    {
        float step = TICK / 4F;

        for (int i = 0; i < 4; i++)
        {
            float acceleration = omega * omega * (target - state[0]) - 2F * omega * state[1];

            state[1] += acceleration * step;
            state[0] += state[1] * step;
        }
    }

    private void reset()
    {
        this.doors.clear();
        this.primed = false;
        this.speed = 0F;
        this.wheel = this.prevWheel = 0F;
        this.steer = this.prevSteer = this.steerVelocity = 0F;
        this.pitch = this.prevPitch = this.pitchVelocity = 0F;
        this.roll = this.prevRoll = this.rollVelocity = 0F;
        this.lift = this.prevLift = this.liftVelocity = 0F;
        this.hasLast = false;
        this.known = false;
        this.knownPitch = this.prevKnownPitch = this.knownRoll = this.prevKnownRoll = 0F;
        this.groundPrimed = false;
        this.fall = 0F;
        Arrays.fill(this.ground, 0F);
        Arrays.fill(this.prevGround, 0F);
        Arrays.fill(this.groundVelocity, 0F);
        Arrays.fill(this.droop, 0F);
        Arrays.fill(this.prevDroop, 0F);
        Arrays.fill(this.droopVelocity, 0F);
        Arrays.fill(this.wheelGround, Double.NaN);
    }

    /* Drawing */

    /**
     * Lay the vehicle's motion over the posed model, on top of everything else: an additive pose,
     * like the form's own, so keyed bone rotations still apply under it. The ground and the body's
     * sway move whole groups of bones as one; each of those bones is turned about the common point
     * and moved by what that turn does to its pivot, composed as quaternions so a door swung open
     * still swings about its own hinge on a vehicle standing on a slope.
     */
    public void apply(IModel model, ModelForm form, IEntity entity, float transition)
    {
        if (!form.vehicleIk.get() || model == null)
        {
            return;
        }

        if (entity != null)
        {
            this.see(entity, form);
        }

        float wheel = (this.prevWheel + (this.wheel - this.prevWheel) * transition) * (form.invertWheelRoll.get() ? -1F : 1F);
        /* Minecraft turns a left turn with the yaw going down, and the model's yaw turns the other way:
         * the wheels and the steering wheel take the steering with its sign flipped */
        float steer = (float) -Math.toRadians(this.prevSteer + (this.steer - this.prevSteer) * transition);
        /* The client's own partial tick: a paused film draws its actors at the start of the tick */
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        String[] wheels = {form.boneWheelFL.get(), form.boneWheelFR.get(), form.boneWheelRL.get(), form.boneWheelRR.get()};
        Pose pose = new Pose();

        rotate(pose, wheels[0], wheel, steer, 0F);
        rotate(pose, wheels[1], wheel, steer, 0F);
        rotate(pose, wheels[2], wheel, 0F, 0F);
        rotate(pose, wheels[3], wheel, 0F, 0F);
        rotate(pose, form.boneSteering.get(), 0F, 0F, steer * STEERING_RATIO);

        if (!this.doors.isEmpty())
        {
            for (ValueVehicleParts.Part part : VehicleGeometry.parts(form))
            {
                float[] door = this.doors.get(part.bone.get());

                if (door == null)
                {
                    continue;
                }

                float t = MathUtils.clamp(door[0] + (door[1] - door[0]) * tickDelta, 0F, 1F);
                float angle = (float) Math.toRadians(part.angle.get() * t * t * (3F - 2F * t));

                if (part.isDoor())
                {
                    rotate(pose, part.bone.get(), 0F, angle, 0F);
                }
                else
                {
                    rotate(pose, part.bone.get(), angle, 0F, 0F);
                }
            }
        }

        Matrix4f[] frames = this.frames(form, transition, tickDelta);

        if (model instanceof Model cubic)
        {
            this.carryBones(cubic, form, pose, frames, transition, tickDelta);
        }
        else if (frames[0] != null || frames[1] != null)
        {
            /* A model of another kind has no groups to move together: its body bone takes the lot */
            Matrix4f all = frames[0] == null ? frames[1] : frames[1] == null ? frames[0] : new Matrix4f(frames[0]).mul(frames[1]);
            Vector3f angles = all.getNormalizedRotation(new Quaternionf()).getEulerAnglesZYX(new Vector3f());

            rotate(pose, form.boneBody.get(), angles.x, 0F, angles.z);
        }

        if (pose.isEmpty())
        {
            return;
        }

        /* A pose layer also sets each bone's colour, overlay and lighting; this one is about moving
         * only, so it carries over what the layers before it gave the bone instead of wiping it */
        for (ModelGroup group : model.getAllGroups())
        {
            PoseTransform transform = pose.transforms.get(group.id);

            if (transform != null)
            {
                transform.color.copy(group.color);
                transform.overlay.copy(group.overlay);
                transform.lighting = group.lighting;
            }
        }

        model.applyPose(pose);
    }

    /**
     * The ground onto every top group (all of the model rides on it), the body's sway onto the body
     * and every part beside it that holds no wheel (and back off a wheel inside the body), and each
     * wheel's own travel onto the wheel.
     */
    private void carryBones(Model model, ModelForm form, Pose pose, Matrix4f[] frames, float transition, float tickDelta)
    {
        Map<ModelGroup, Matrix4f> carried = new LinkedHashMap<>();
        String[] wheelBones = {form.boneWheelFL.get(), form.boneWheelFR.get(), form.boneWheelRL.get(), form.boneWheelRR.get()};
        ModelGroup[] wheelGroups = new ModelGroup[4];

        for (int i = 0; i < 4; i++)
        {
            wheelGroups[i] = wheelBones[i].isEmpty() ? null : model.getGroup(wheelBones[i]);
        }

        if (frames[0] != null)
        {
            for (ModelGroup group : model.getAllGroups())
            {
                if (group.parent == null)
                {
                    carry(carried, group, frames[0]);
                }
            }
        }

        ModelGroup body = form.boneBody.get().isEmpty() ? null : model.getGroup(form.boneBody.get());

        if (frames[1] != null && body != null)
        {
            /* The sway is in the model's frame; a bone moves in its parent's */
            Matrix4f parent = world(body.parent);
            Matrix4f sway = parent == null ? frames[1] : new Matrix4f(parent).invert().mul(frames[1]).mul(parent);
            Iterable<ModelGroup> siblings = body.parent == null ? model.getAllGroups() : body.parent.children;

            for (ModelGroup group : siblings)
            {
                if (group.parent == body.parent && (group == body || !holdsWheel(group, wheelGroups)))
                {
                    carry(carried, group, sway);
                }
            }

            /* A wheel the body holds stays on the ground: the sway is taken back off it */
            Matrix4f back = new Matrix4f(frames[1]).invert();

            for (ModelGroup group : wheelGroups)
            {
                if (group != null && isWithin(group, body))
                {
                    Matrix4f frame = world(group.parent);

                    carry(carried, group, frame == null ? back : new Matrix4f(frame).invert().mul(back).mul(frame));
                }
            }
        }

        /* Each wheel down into a hole or up over a bump, along its parent's up */
        float t = this.groundByFilm ? transition : tickDelta;

        for (int i = 0; i < 4; i++)
        {
            float hang = this.prevDroop[i] + (this.droop[i] - this.prevDroop[i]) * t;

            if (wheelGroups[i] != null && Math.abs(hang) > 5.0E-4F)
            {
                carry(carried, wheelGroups[i], new Matrix4f().translation(0F, hang, 0F));
            }
        }

        for (Map.Entry<ModelGroup, Matrix4f> entry : carried.entrySet())
        {
            move(pose, entry.getKey(), entry.getValue());
        }
    }

    /** Add a transform (in the bone's parent's frame) under what the bone is already carried by */
    private static void carry(Map<ModelGroup, Matrix4f> carried, ModelGroup group, Matrix4f matrix)
    {
        Matrix4f before = carried.get(group);

        carried.put(group, before == null ? new Matrix4f(matrix) : new Matrix4f(before).mul(matrix));
    }

    /**
     * Move a bone by a transform of its parent's frame: its pivot goes where the transform takes it
     * (as a translation, in the pose's own terms - X mirrored), and the transform's turn goes on top
     * of the bone's whole orientation, the vehicle IK's own turn of it (a wheel's roll, a door's
     * swing) included, as one quaternion layer.
     */
    private static void move(Pose pose, ModelGroup group, Matrix4f matrix)
    {
        PoseTransform transform = pose.getOrCreate(group.id);
        Vector3f pivot = group.initial.translate;
        Vector3f at = group.current.translate;
        Vector3f offset = new Vector3f(-(at.x - pivot.x) / 16F, (at.y - pivot.y) / 16F, (at.z - pivot.z) / 16F);
        Vector3f origin = new Vector3f(pivot).div(16F);
        Vector3f moved = matrix.transformPosition(new Vector3f(offset).add(origin)).sub(origin).sub(offset);

        transform.translate.add(-moved.x * 16F, moved.y * 16F, moved.z * 16F);

        boolean euler = group.orient == null && group.current.rotationMode != Transform.RotationMode.QUATERNION;
        Quaternionf base = group.orient != null ? new Quaternionf(group.orient)
            : !euler ? new Quaternionf(group.current.quat)
            : Matrices.toLocalRotationZYXDegrees(group.current.rotate);
        Vector3f own = transform.rotationMode == Transform.RotationMode.QUATERNION ? new Vector3f() : new Vector3f(transform.rotate);
        /* What the bone would turn to with the vehicle IK's own turn alone, the way the pose adds it */
        Quaternionf turned = euler
            ? Matrices.toQuaternionZYXDegrees(group.current.rotate.x + MathUtils.toDeg(own.x), group.current.rotate.y + MathUtils.toDeg(own.y), group.current.rotate.z + MathUtils.toDeg(own.z))
            : new Quaternionf(base).mul(Matrices.toQuaternionZYXRadians(own.x, own.y, own.z));
        Quaternionf layer = new Quaternionf(base).invert().mul(matrix.getNormalizedRotation(new Quaternionf())).mul(turned);

        transform.rotate.set(0F, 0F, 0F);
        transform.quat.set(layer);
        transform.rotationMode = Transform.RotationMode.QUATERNION;

        /* A quaternion layer goes on top of the bone's orientation; one held as a quaternion is not
         * in its euler channels, which is where that orientation is seeded from otherwise */
        if (!euler && group.orient == null)
        {
            group.orient = new Quaternionf(group.current.quat);
        }
    }

    /** A bone's frame in the model's (blocks, before its half turn), as the renderer builds it; null for the model itself */
    private static Matrix4f world(ModelGroup group)
    {
        if (group == null)
        {
            return null;
        }

        Vector3f pivot = group.initial.translate;
        Vector3f at = group.current.translate;
        Vector3f scale = group.current.scale;
        Quaternionf rotation = group.orient != null ? group.orient
            : group.current.rotationMode == Transform.RotationMode.QUATERNION ? group.current.quat
            : Matrices.toLocalRotationZYXDegrees(group.current.rotate);
        Matrix4f local = new Matrix4f()
            .translate(-(at.x - pivot.x) / 16F, (at.y - pivot.y) / 16F, (at.z - pivot.z) / 16F)
            .translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F)
            .rotate(rotation)
            .scale(scale.x, scale.y, scale.z)
            .translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
        Matrix4f parent = world(group.parent);

        return parent == null ? local : parent.mul(local);
    }

    private static boolean holdsWheel(ModelGroup group, ModelGroup[] wheels)
    {
        for (ModelGroup wheel : wheels)
        {
            if (wheel != null && (wheel == group || isWithin(wheel, group)))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isWithin(ModelGroup group, ModelGroup ancestor)
    {
        for (ModelGroup current = group.parent; current != null; current = current.parent)
        {
            if (current == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    /* The parts that open */

    public boolean isOpen(String bone)
    {
        float[] door = this.doors.get(bone);

        return door != null && door[2] > 0.5F;
    }

    /** How far open a part is at this tick, eased the way it is drawn (0 shut, 1 open) */
    public float getOpenness(String bone)
    {
        float[] door = this.doors.get(bone);
        float t = door == null ? 0F : MathUtils.clamp(door[1], 0F, 1F);

        return t * t * (3F - 2F * t);
    }

    /** Whether a part is still on its way (opening or closing) */
    public boolean isMoving(String bone)
    {
        float[] door = this.doors.get(bone);

        return door != null && door[1] != door[2];
    }

    public void setOpen(String bone, boolean open)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        this.doors.computeIfAbsent(bone, (k) -> new float[3])[2] = open ? 1F : 0F;
        MOVING.add(this);
    }

    public void toggle(String bone)
    {
        this.setOpen(bone, !this.isOpen(bone));
    }

    /* The rider's legs, in a cubic model's degrees, as vanilla seats a rider: hips up, knees apart */
    private static final float SEAT_LEG_PITCH = 81F;
    private static final float SEAT_LEG_YAW = 18F;

    /** Whether a bone is a leg, by its name (left_leg, RightLeg, sol_bacak ...): +1 on the left, -1 on the right, 0 for anything else */
    public static int legSide(String bone)
    {
        String flat = VehicleBoneDetector.flat(bone);

        if (flat.equals("leftleg") || flat.equals("legleft") || flat.equals("lleg") || flat.equals("solbacak"))
        {
            return 1;
        }

        if (flat.equals("rightleg") || flat.equals("legright") || flat.equals("rleg") || flat.equals("sagbacak"))
        {
            return -1;
        }

        return 0;
    }

    /**
     * Fold a rider's legs onto the seat: the bones named as legs are set to the seated angle
     * outright, over whatever the walk put there.
     */
    public static void seatLegs(IModel model)
    {
        if (!(model instanceof Model cubic))
        {
            return;
        }

        for (ModelGroup group : cubic.getAllGroups())
        {
            int side = legSide(group.id);

            if (side == 0)
            {
                continue;
            }

            group.current.rotate.set(SEAT_LEG_PITCH, SEAT_LEG_YAW * side, 0F);
            /* The render follows the composed orientation once a layer set it: drop it for the angles above */
            group.orient = null;
        }
    }

    /** Let go of a part at once, shut and still: something else (a recorded track) holds it now */
    public void forget(String bone)
    {
        this.doors.remove(bone);
    }

    /** One tick of every part that is moving, in any vehicle */
    public static void tickDoors()
    {
        MOVING.removeIf((state) -> !state.stepDoors());
    }

    private boolean stepDoors()
    {
        boolean moving = false;

        for (float[] door : this.doors.values())
        {
            moving |= door[0] != door[1];
            door[0] = door[1];

            if (door[1] != door[2])
            {
                float step = 1F / DOOR_TICKS;

                door[1] = door[2] > door[1] ? Math.min(door[1] + step, door[2]) : Math.max(door[1] - step, door[2]);
                moving = true;
            }
        }

        return moving;
    }

    private static void rotate(Pose pose, String bone, float x, float y, float z)
    {
        if (bone == null || bone.isEmpty() || (x == 0F && y == 0F && z == 0F))
        {
            return;
        }

        PoseTransform transform = pose.getOrCreate(bone);

        transform.rotate.add(x, y, z);
    }
}
