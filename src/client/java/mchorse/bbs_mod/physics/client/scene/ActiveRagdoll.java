package mchorse.bbs_mod.physics.client.scene;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RRayCast;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RayCastResult;
import com.github.stephengold.joltjni.SpecifiedBroadPhaseLayerFilter;
import com.github.stephengold.joltjni.SpecifiedObjectLayerFilter;
import com.github.stephengold.joltjni.Vec3;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.physics.engine.PhysicsLayers;
import mchorse.bbs_mod.physics.engine.PhysicsWorld;
import mchorse.bbs_mod.physics.engine.RigMemory;
import mchorse.bbs_mod.physics.ragdoll.ActiveKnob;
import mchorse.bbs_mod.physics.ragdoll.ActiveRagdolls;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The live controller of one ragdoll's active physics: it keeps the trunk over its pose, reacts to
 * what hits it, braces before the ground and decides, tick by tick, how much the animation and the
 * muscles still hold. Nothing here plays a canned reaction; every answer is worked out from the
 * bodies as they are on that tick.
 *
 * <p><b>What it reads and what it changes.</b> It reads the trunk (the part no joint hangs from):
 * its velocity, its rotation against the animated one, and the ground under it. It changes four
 * things, all through what the ragdoll already has: the share of the animation's hold
 * ({@link #authorityScale}), the muscle tone and damping ({@link #tone}, {@link #toneDamping}),
 * how far the muscle targets lean to the build pose when bracing ({@link #brace}), and, directly,
 * the trunk's spin (balance) and every part's speed on landing.</p>
 *
 * <ul>
 *     <li><b>Balance.</b> The lean is the angle between the trunk's up and the animated trunk's up.
 *     Under the fall angle the trunk's sideways spin is steered towards upright and damped, and the
 *     muscles tighten with the lean. Past it the body gives up: it goes down, the animation lets go
 *     and the muscles drop to a quarter.</li>
 *     <li><b>Impacts.</b> A push from an impulse clip, and any velocity change of the trunk that its
 *     drives and gravity do not explain (a hit, a hard contact), adds to the stagger. The stagger
 *     takes away that share of the animation's hold and of the muscles, and wears off at the
 *     recovery rate. A push on a character the animation fully holds releases it on the spot, so an
 *     animated character takes a hit instead of ignoring it.</li>
 *     <li><b>Falling.</b> Falling faster than {@link #FALL_SPEED}, the ground is looked for under the
 *     trunk; when it is under half a second away the muscles brace and the limbs lean to the pose
 *     the ragdoll was built in. On the landing every part loses some of its speed and gets a share
 *     of it back as a bounce.</li>
 *     <li><b>Getting up.</b> A fallen body lying still for a second gets its hold back at the
 *     recovery rate, and the drives stand it back on its pose.</li>
 * </ul>
 *
 * <p><b>Cost.</b> Off, held by the animation or dead, it reads its knobs and returns. Resting
 * (every part of it slower than the sleep speed for half a second) it reads one velocity a tick
 * and nothing more until something moves it. Otherwise it is a handful of reads and at most one
 * ray every second tick while falling, with every vector and quaternion allocated once. Past the
 * scene's budget it runs its balance and ground checks every second or fourth tick and scales them
 * up to match ({@link ActiveSceneState}).</p>
 *
 * <p><b>Deterministic.</b> Time is counted in film ticks, never in frames or wall time; physics steps
 * the same fixed amount per tick whatever the frame rate; every correction is clamped, so a low frame
 * rate has nothing to blow up. Its state is written into the scene's checkpoints
 * ({@link #remember}), so a scrub or an export back to any tick carries on exactly as the first run
 * did.</p>
 */
public final class ActiveRagdoll
{
    /** A film tick, in seconds: the clock everything here counts in. */
    private static final float DT = PhysicsWorld.TICK;

    /** A velocity change of the trunk below this, in blocks per second, is the drives and gravity at work, not a hit. */
    private static final float IMPACT_FLOOR = 2.5F;
    /** The velocity change, over the floor, that staggers fully at an impact knob of 1. */
    private static final float IMPACT_SCALE = 8F;
    /** A push this fast, in blocks per second, releases a character the animation holds. */
    private static final float RELEASE_SPEED = 1.5F;
    /** Falling faster than this, in blocks per second, the ground is looked for. */
    private static final float FALL_SPEED = 3F;
    /** How far ahead, in seconds, the brace starts before the ground. */
    private static final float BRACE_TIME = 0.5F;
    /** How long a brace holds after it was last confirmed, in ticks. */
    private static final int BRACE_TICKS = 6;
    /** How quickly the balance turns the trunk back, per second, for each radian of lean. */
    private static final float BALANCE_RATE = 6F;
    /** The most spin the balance may add in one tick, in radians per second. */
    private static final float MAX_CORRECTION = 8F;
    /** Ticks of rest before the controller sleeps, and before a fallen body starts getting up. */
    private static final int SLEEP_AFTER = 10;
    private static final int GET_UP_AFTER = 20;
    /** Muscle values are handed to the joints in these steps, so a slow drift does not call into Jolt every tick. */
    private static final float TONE_STEP = 1F / 50F;

    private static SpecifiedBroadPhaseLayerFilter staticBroadPhase;
    private static SpecifiedObjectLayerFilter staticObjects;

    private final float[] knobs = new float[ActiveRagdolls.COUNT];

    /* State: written into checkpoints */
    private float stun;
    private float down;
    private boolean fallen;
    private int restTicks;
    private int sleepTicks;
    private boolean sleeping;
    private int braceTicks;
    private float braceWeight;
    private boolean hasLast;
    private final Vector3f last = new Vector3f();
    private float landing;

    /* This tick's answers */
    private boolean on;
    private float strength;
    private int interval = 1;
    private boolean heavy;
    public float authorityScale = 1F;
    public float tone;
    public float toneDamping;
    public float brace;
    private float lean;

    /* Scratch, allocated once */
    private final Vec3 velocity = new Vec3();
    private final Vec3 write = new Vec3();
    private final Quat rotation = new Quat();
    private final RVec3 position = new RVec3();
    private final RVec3 rayOrigin = new RVec3();
    private final Vec3 rayDirection = new Vec3();
    private final Quaternionf actual = new Quaternionf();
    private final Quaternionf error = new Quaternionf();
    private final Vector3f up = new Vector3f();
    private final Vector3f axis = new Vector3f();
    private final Vector3f now = new Vector3f();

    public boolean isOn()
    {
        return this.on;
    }

    public boolean isSleeping()
    {
        return this.sleeping;
    }

    /** Whether the body has gone down past its fall angle and not yet got its hold back. */
    public boolean isFallen()
    {
        return this.fallen;
    }

    /** How staggered it is, 0 to 1. */
    public float getStun()
    {
        return this.stun;
    }

    /** The trunk's lean off the animated pose at the last balance check, in radians. */
    public float getLean()
    {
        return this.lean;
    }

    public float knob(ActiveKnob knob)
    {
        return this.knobs[knob.ordinal()];
    }

    /**
     * The start of a tick, before the drives: reads the knobs and any flow change, measures what the
     * last step did to the trunk, and decides the hold and the muscle tone for this tick.
     *
     * @param trunk     the trunk's body id, or -1 when the ragdoll has no part
     * @param free      whether the trunk is a dynamic body this tick (the animation does not fully hold it)
     * @param available whether the controller may run at all: not dead, not floated by a field
     * @param muscles   the ragdoll's own muscle strength and damping, which the tone is blended from
     * @return whether the controller is on this tick
     */
    public boolean begin(Form form, ActiveSceneState scene, BodyInterface bodies, int trunk, boolean free, boolean available, float muscles, float muscleDamping)
    {
        ActiveRagdolls.read(form, this.knobs);

        return this.decide(scene, bodies, trunk, free, available, muscles, muscleDamping);
    }

    /** The knobs this controller reads, by {@link ActiveKnob#ordinal()}; filled from the form by {@link #begin}. */
    public float[] knobs()
    {
        return this.knobs;
    }

    /** {@link #begin} on the knobs as they already are. */
    public boolean decide(ActiveSceneState scene, BodyInterface bodies, int trunk, boolean free, boolean available, float muscles, float muscleDamping)
    {
        float override = scene == null ? Float.NaN : scene.strength();

        this.strength = Float.isNaN(override) ? this.knob(ActiveKnob.STRENGTH) : Math.max(0F, Math.min(1F, override));
        this.on = available && this.strength > 0F && trunk >= 0;
        this.authorityScale = 1F;
        this.brace = 0F;

        if (!this.on)
        {
            this.calm();
            this.tone = muscles;
            this.toneDamping = muscleDamping;

            return false;
        }

        /* A flow's one-off, on the tick it was pinned to */
        ActiveSceneState.Event event = scene == null ? null : scene.now();

        if (event != null)
        {
            this.stun = Math.min(1F, this.stun + event.stagger() * this.knob(ActiveKnob.IMPACT));

            if (event.knockdown())
            {
                this.fall();
            }

            this.wake();
        }

        if (free)
        {
            this.measure(bodies, trunk);
        }
        else
        {
            this.hasLast = false;
        }

        if (this.sleeping)
        {
            this.heavy = false;
        }
        else
        {
            int slot = scene == null ? 0 : scene.take();
            int tick = scene == null ? 0 : scene.getTick();

            this.interval = ActiveSceneState.interval(slot);
            this.heavy = (tick + slot) % this.interval == 0;
        }

        /* Wearing off, on the film's clock */
        float recovery = this.knob(ActiveKnob.RECOVERY);

        this.stun = Math.max(0F, this.stun - recovery * DT);

        if (this.fallen && this.restTicks >= GET_UP_AFTER && recovery > 0F)
        {
            this.down = Math.max(0F, this.down - recovery * DT);

            if (this.down <= 0F)
            {
                this.fallen = false;
            }
        }

        float knock = Math.max(this.stun, this.fallen ? this.down : 0F);

        this.authorityScale = 1F - this.strength * knock;

        /* The tone: the controller's own, slackened by a stagger, dropped when down, tightened with the lean and the brace */
        float stiffness = this.knob(ActiveKnob.STIFFNESS);
        float fallAngle = (float) Math.toRadians(this.knob(ActiveKnob.FALL_ANGLE));
        float active = this.fallen ? stiffness * 0.25F : stiffness * (1F - 0.7F * this.stun);

        active += this.knob(ActiveKnob.BALANCE) * 0.3F * Math.min(1F, this.lean / Math.max(0.01F, fallAngle));

        if (this.braceTicks > 0)
        {
            active = Math.max(active, this.knob(ActiveKnob.REFLEX) * this.braceWeight);
            this.brace = this.knob(ActiveKnob.REFLEX) * this.braceWeight * this.strength;
        }

        this.tone = step(mix(muscles, Math.min(1F, active), this.strength));
        this.toneDamping = step(mix(muscleDamping, this.knob(ActiveKnob.DAMPING), this.strength));

        return true;
    }

    /**
     * After the drives: the balance on the trunk, the ground check while falling, and the landing.
     *
     * @param target the animated rotation of the trunk this tick, or null when the pose had none
     * @param parts  every part's body id, for the landing
     */
    public void after(PhysicsWorld physics, BodyInterface bodies, int trunk, Quaternionf target, int[] parts, boolean free)
    {
        if (!this.on || !free || this.sleeping)
        {
            this.remember(bodies, trunk, free);

            return;
        }

        if (this.braceTicks > 0)
        {
            this.braceTicks -= 1;
        }

        if (this.heavy)
        {
            if (target != null)
            {
                this.balance(bodies, trunk, target);
            }

            this.lookForGround(physics, bodies, trunk);
        }

        if (this.landing > 0F)
        {
            this.land(bodies, parts);
        }

        this.remember(bodies, trunk, true);
    }

    /** A push was given this tick at {@code speed} blocks per second: staggered by it, and awake. */
    public void hit(float speed)
    {
        if (!this.on || !(speed > 0F))
        {
            return;
        }

        this.stun = Math.min(1F, this.stun + this.knob(ActiveKnob.IMPACT) * this.strength * speed / IMPACT_SCALE);
        this.wake();
    }

    /** Whether a push this fast should take the character from the animation. */
    public boolean releases(float speed)
    {
        return this.on && this.knob(ActiveKnob.IMPACT) > 0F && speed >= RELEASE_SPEED;
    }

    /** The trunk's velocity after something outside the step changed it, so it is not read as a hit next tick. */
    public void remember(BodyInterface bodies, int trunk, boolean free)
    {
        if (!free || trunk < 0)
        {
            this.hasLast = false;

            return;
        }

        bodies.getLinearVelocity(trunk, this.velocity);
        this.last.set(this.velocity.getX(), this.velocity.getY(), this.velocity.getZ());
        this.hasLast = this.last.isFinite();
    }

    /* The steps */

    /** What the last step did to the trunk: a hit, a landing, rest. */
    private void measure(BodyInterface bodies, int trunk)
    {
        bodies.getLinearVelocity(trunk, this.velocity);
        this.now.set(this.velocity.getX(), this.velocity.getY(), this.velocity.getZ());

        if (!this.now.isFinite())
        {
            this.hasLast = false;

            return;
        }

        float speed = this.now.length();

        if (this.hasLast)
        {
            float dx = this.now.x - this.last.x;
            float dy = this.now.y - this.last.y;
            float dz = this.now.z - this.last.z;
            float change = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (change > IMPACT_FLOOR)
            {
                this.stun = Math.min(1F, this.stun + this.knob(ActiveKnob.IMPACT) * this.strength * (change - IMPACT_FLOOR) / IMPACT_SCALE);
                this.wake();
            }

            /* Coming down fast and stopped from below: the landing */
            if (this.last.y < -FALL_SPEED && dy > IMPACT_FLOOR)
            {
                this.landing = dy;
            }
        }

        float sleep = this.knob(ActiveKnob.SLEEP_SPEED);

        if (speed < sleep && this.stun < 0.01F && this.braceTicks == 0 && this.landing == 0F)
        {
            this.sleepTicks = Math.min(SLEEP_AFTER, this.sleepTicks + 1);
            this.sleeping = this.sleepTicks >= SLEEP_AFTER;
        }
        else
        {
            this.wake();
        }

        this.restTicks = speed < Math.max(sleep, 0.5F) ? Math.min(GET_UP_AFTER, this.restTicks + 1) : 0;
    }

    /** Steers the trunk's sideways spin towards the animated trunk's up, and gives up past the fall angle. */
    private void balance(BodyInterface bodies, int trunk, Quaternionf target)
    {
        float balance = this.knob(ActiveKnob.BALANCE);

        bodies.getRotation(trunk, this.rotation);
        this.actual.set(this.rotation.getX(), this.rotation.getY(), this.rotation.getZ(), this.rotation.getW());

        if (!Float.isFinite(this.actual.w) || !Float.isFinite(target.w))
        {
            return;
        }

        /* The turn from where the animation has the trunk to where it is, and what it does to "up" */
        this.error.set(target).conjugate().premul(this.actual).normalize();
        this.up.set(0F, 1F, 0F);
        this.error.transform(this.up);
        this.lean = (float) Math.acos(Math.max(-1F, Math.min(1F, this.up.y)));

        if (this.fallen)
        {
            return;
        }

        if (this.lean > Math.toRadians(this.knob(ActiveKnob.FALL_ANGLE)))
        {
            this.fall();

            return;
        }

        float effective = balance * this.strength * (1F - this.stun);

        if (effective <= 0F)
        {
            return;
        }

        /* The axis that turns the leaning up back to straight up: up × Y, horizontal by construction */
        this.axis.set(this.up).cross(0F, 1F, 0F);

        float sine = this.axis.length();
        bodies.getAngularVelocity(trunk, this.velocity);

        float wx = this.velocity.getX();
        float wy = this.velocity.getY();
        float wz = this.velocity.getZ();
        float share = Math.min(1F, 0.35F * this.interval);
        float damping = 1F - Math.min(0.9F, this.knob(ActiveKnob.DAMPING) * 0.25F * effective * this.interval);
        float tx = 0F;
        float tz = 0F;

        if (sine > 1.0e-4F)
        {
            float speed = this.lean * BALANCE_RATE * effective;

            tx = this.axis.x / sine * speed;
            tz = this.axis.z / sine * speed;
        }

        /* The sideways spin moves towards the upright one and is damped; the yaw is the animation's business */
        float nx = (wx + (tx - wx) * share) * damping;
        float nz = (wz + (tz - wz) * share) * damping;
        float cx = clamp(nx - wx, MAX_CORRECTION);
        float cz = clamp(nz - wz, MAX_CORRECTION);

        this.write.set(wx + cx, wy, wz + cz);
        bodies.setAngularVelocity(trunk, this.write);
        bodies.activateBody(trunk);
    }

    /** Falling fast: how far the ground is under the trunk, and a brace when it is close. */
    private void lookForGround(PhysicsWorld physics, BodyInterface bodies, int trunk)
    {
        float reflex = this.knob(ActiveKnob.REFLEX);

        if (reflex <= 0F || !this.hasLast || this.last.y > -FALL_SPEED)
        {
            return;
        }

        float fall = -this.last.y;
        float reach = Math.min(12F, fall * BRACE_TIME + 1F);

        bodies.getPosition(trunk, this.position);
        this.rayOrigin.set(this.position.xx(), this.position.yy(), this.position.zz());
        this.rayDirection.set(0F, -reach, 0F);

        if (staticObjects == null)
        {
            staticBroadPhase = new SpecifiedBroadPhaseLayerFilter(PhysicsLayers.BROAD_PHASE_STATIC);
            staticObjects = new SpecifiedObjectLayerFilter(PhysicsLayers.STATIC);
        }

        try (RRayCast ray = new RRayCast(this.rayOrigin, this.rayDirection); RayCastResult result = new RayCastResult())
        {
            if (!physics.getSystem().getNarrowPhaseQuery().castRay(ray, result, staticBroadPhase, staticObjects))
            {
                return;
            }

            float time = result.getFraction() * reach / Math.max(0.001F, fall);

            if (time < BRACE_TIME)
            {
                this.braceTicks = BRACE_TICKS;
                this.braceWeight = 1F - time / BRACE_TIME;
            }
        }
    }

    /** The landing: every part loses part of its speed to the reflex, and gets part of the stop back as a bounce. */
    private void land(BodyInterface bodies, int[] parts)
    {
        float keep = 1F - this.knob(ActiveKnob.REFLEX) * 0.5F * this.strength;
        float bounce = this.landing * this.knob(ActiveKnob.BOUNCE) * 0.3F * this.strength;

        for (int id : parts)
        {
            bodies.getLinearVelocity(id, this.velocity);

            float x = this.velocity.getX() * keep;
            float y = this.velocity.getY() * keep + bounce;
            float z = this.velocity.getZ() * keep;

            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z))
            {
                this.write.set(x, y, z);
                bodies.setLinearVelocity(id, this.write);
            }
        }

        this.landing = 0F;
        this.braceTicks = 0;
    }

    private void fall()
    {
        this.fallen = true;
        this.down = 1F;
        this.restTicks = 0;
    }

    private void wake()
    {
        this.sleeping = false;
        this.sleepTicks = 0;
    }

    /** Off: nothing carried over to the next time it is on. */
    private void calm()
    {
        this.stun = 0F;
        this.down = 0F;
        this.fallen = false;
        this.restTicks = 0;
        this.braceTicks = 0;
        this.braceWeight = 0F;
        this.landing = 0F;
        this.lean = 0F;
        this.hasLast = false;
        this.wake();
    }

    /* Checkpoints */

    public void remember(RigMemory memory)
    {
        memory.write(this.stun).write(this.down).write(this.fallen).write(this.restTicks).write(this.sleepTicks).write(this.sleeping)
            .write(this.braceTicks).write(this.braceWeight).write(this.hasLast).write(this.last).write(this.landing).write(this.lean);
    }

    public void recall(RigMemory memory)
    {
        this.stun = memory.readFloat();
        this.down = memory.readFloat();
        this.fallen = memory.readBoolean();
        this.restTicks = memory.readInt();
        this.sleepTicks = memory.readInt();
        this.sleeping = memory.readBoolean();
        this.braceTicks = memory.readInt();
        this.braceWeight = memory.readFloat();
        this.hasLast = memory.readBoolean();
        memory.read(this.last);
        this.landing = memory.readFloat();
        this.lean = memory.readFloat();
    }

    /* Numbers */

    private static float mix(float a, float b, float t)
    {
        return a + (b - a) * t;
    }

    private static float step(float value)
    {
        return Math.round(value / TONE_STEP) * TONE_STEP;
    }

    private static float clamp(float value, float limit)
    {
        return Math.max(-limit, Math.min(limit, value));
    }
}
