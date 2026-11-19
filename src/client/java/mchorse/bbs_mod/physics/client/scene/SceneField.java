package mchorse.bbs_mod.physics.client.scene;

import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import mchorse.bbs_mod.forms.forms.PhysicsFieldForm;
import org.joml.Vector3f;

/**
 * A gravity and cosmic anomaly field (see {@link PhysicsFieldForm}) for one simulated tick, offered
 * to everything in the scene the way an impulse is: it is one push per tick, of
 * {@code acceleration x tick}, and the rigs that already take an impulse (bodies, ragdoll parts, cloth,
 * strands, soft bodies, debris) take it with no change of their own.
 *
 * <p>What the push holds, inside the region:</p>
 * <ul>
 * <li><b>The gravity scale.</b> The scene's gravity is a fixed number in Jolt, so a different gravity
 * is the difference added as a velocity change: {@code (scale - 1) x g}, downwards. Scale 0 cancels
 * gravity exactly; 1 changes nothing; below 0 lifts.</li>
 * <li><b>The drag</b>, which only bodies can have (it needs the speed they already carry): a share of
 * the linear and the angular velocity lost per second, which is what stops a weightless limb spinning
 * for ever.</li>
 * <li><b>The pull</b>, in the Big Rip mode: towards the centre, as strong as the field says at the
 * centre and fading with distance by the falloff, with a soft core so that a body passing through the
 * centre is not flung at an infinite speed.</li>
 * <li><b>The turbulence</b>, in the same mode: a slowly changing buffeting that depends only on where
 * and when - not on any random state - so a re-recording gives the same film.</li>
 * </ul>
 *
 * <p>A body is tested at its centre; the field's edge fades over its last tenth so nothing is yanked
 * the tick it crosses the line.</p>
 */
public final class SceneField extends SceneImpulse
{
    private static final float SOFT_CORE = 0.75F;

    private final float cx;
    private final float cy;
    private final float cz;
    private final int shape;
    private final float radius;
    private final float hx;
    private final float hy;
    private final float hz;
    private final boolean bigRip;
    private final float gravityDelta;
    private final float gravityScale;
    private final boolean releases;
    /** The bodies whose gravity factor this tick's fields changed, so the next tick can put it back */
    private final java.util.Set<Integer> touched;
    private final float damping;
    private final float pull;
    private final float falloff;
    private final float turbulence;
    private final float dt;
    private final float time;

    private final Vector3f scratch = new Vector3f();
    private final Vec3 vec = new Vec3();

    private SceneField(PhysicsFieldForm form, float cx, float cy, float cz, float gravity, float dt, int tick, java.util.Set<Integer> touched)
    {
        super(cx, cy, cz, 1F, 1F, null);

        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.shape = form.shape.get();
        this.radius = Math.max(form.radius.get(), 0.5F);
        this.hx = Math.max(form.width.get(), 0.5F) / 2F;
        this.hy = Math.max(form.height.get(), 0.5F) / 2F;
        this.hz = Math.max(form.length.get(), 0.5F) / 2F;
        this.bigRip = form.mode.get() == PhysicsFieldForm.MODE_BIG_RIP;
        this.gravityScale = form.gravityScale.get();
        this.gravityDelta = (this.gravityScale - 1F) * gravity;
        this.releases = form.releaseBodies.get();
        this.touched = touched;
        this.damping = Math.max(form.damping.get(), 0F);
        this.pull = this.bigRip ? form.pullStrength.get() : 0F;
        this.falloff = Math.max(form.falloff.get(), 0F);
        this.turbulence = this.bigRip ? Math.max(form.turbulence.get(), 0F) : 0F;
        this.dt = dt;
        this.time = tick * 0.05F;
    }

    /**
     * Builds the field for a tick from a form read at that tick, or returns null when it could not
     * change anything anyway.
     *
     * @param cx the centre, already in scene coordinates
     * @param gravity the scene's gravity, in blocks per second squared (positive, downwards)
     * @param dt the length of the tick in seconds, speed included
     */
    public static SceneField of(PhysicsFieldForm form, float cx, float cy, float cz, float gravity, float dt, int tick, java.util.Set<Integer> touched)
    {
        if (!Float.isFinite(cx) || !Float.isFinite(cy) || !Float.isFinite(cz) || !(dt > 0F))
        {
            return null;
        }

        SceneField field = new SceneField(form, cx, cy, cz, gravity, dt, tick, touched);

        boolean gravityChanges = field.gravityDelta != 0F && Float.isFinite(field.gravityDelta);
        boolean pulls = field.pull != 0F || field.turbulence > 0F;

        return gravityChanges || field.damping > 0F || pulls ? field : null;
    }

    /** 1 well inside, fading to 0 over the last tenth of the region, 0 outside; the box is measured in its own half sizes */
    private float weight(float px, float py, float pz)
    {
        float dx = px - this.cx;
        float dy = py - this.cy;
        float dz = pz - this.cz;

        if (this.shape == PhysicsFieldForm.SHAPE_SCENE)
        {
            return 1F;
        }

        float edge;

        if (this.shape == PhysicsFieldForm.SHAPE_BOX)
        {
            edge = Math.max(Math.abs(dx) / this.hx, Math.max(Math.abs(dy) / this.hy, Math.abs(dz) / this.hz));
        }
        else
        {
            edge = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / this.radius;
        }

        if (edge >= 1F)
        {
            return 0F;
        }

        float t = Math.min(1F, (1F - edge) / 0.1F);

        return t * t * (3F - 2F * t);
    }

    /** Whether the point is inside and the field takes what is there away from the animation */
    public boolean releases(double px, double py, double pz)
    {
        return this.releases && this.weight((float) px, (float) py, (float) pz) > 0F;
    }

    @Override
    public boolean velocityAt(float px, float py, float pz, Vector3f out)
    {
        return this.push(px, py, pz, out, true);
    }

    /**
     * The velocity change at a point for this tick. With {@code gravity} the changed gravity is in it
     * as a velocity change - for cloth and strands, whose points have no gravity of their own to
     * change; a rigid body has its gravity factor set instead, which Jolt applies on every sub-step.
     */
    private boolean push(float px, float py, float pz, Vector3f out, boolean gravity)
    {
        float w = this.weight(px, py, pz);

        if (!(w > 0F))
        {
            return false;
        }

        float vx = 0F;
        float vy = gravity ? -this.gravityDelta * this.dt : 0F;
        float vz = 0F;

        if (this.pull != 0F)
        {
            float dx = this.cx - px;
            float dy = this.cy - py;
            float dz = this.cz - pz;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float reach = this.shape == PhysicsFieldForm.SHAPE_BOX ? Math.max(this.hx, Math.max(this.hy, this.hz)) : this.radius;
            float near = Math.max(distance, SOFT_CORE);
            float strength = this.pull * (float) Math.pow(Math.max(0F, 1F - Math.min(distance, reach) / reach), this.falloff) * (distance / near);

            if (distance > 1.0E-5F)
            {
                vx += dx / distance * strength * this.dt;
                vy += dy / distance * strength * this.dt;
                vz += dz / distance * strength * this.dt;
            }
        }

        if (this.turbulence > 0F)
        {
            this.noise(px, py, pz, this.scratch);
            vx += this.scratch.x * this.turbulence * this.dt;
            vy += this.scratch.y * this.turbulence * this.dt;
            vz += this.scratch.z * this.turbulence * this.dt;
        }

        out.set(vx * w, vy * w, vz * w);

        return Float.isFinite(out.x) && Float.isFinite(out.y) && Float.isFinite(out.z);
    }

    /** A smooth buffeting in -1 to 1 for each axis, a function of the place and the time and nothing else */
    private void noise(float x, float y, float z, Vector3f out)
    {
        float t = this.time;

        out.set(
            (float) (Math.sin(y * 0.61F + t * 0.9F) * Math.cos(z * 0.43F - t * 0.7F)),
            (float) (Math.sin(z * 0.57F - t * 0.8F) * Math.cos(x * 0.49F + t * 1.1F)),
            (float) (Math.sin(x * 0.53F + t * 0.6F) * Math.cos(y * 0.41F - t * 0.9F)));
    }

    @Override
    public void apply(BodyInterface bodies, int id)
    {
        if (bodies.getMotionType(id) != EMotionType.Dynamic)
        {
            return;
        }

        RVec3 position = bodies.getCenterOfMassPosition(id);
        float px = (float) position.xx();
        float py = (float) position.yy();
        float pz = (float) position.zz();
        float w = this.weight(px, py, pz);

        if (!(w > 0F) || !this.push(px, py, pz, this.scratch, false))
        {
            return;
        }

        /* The gravity, on every sub-step of the tick: 1 outside, the field's scale well inside, faded across the edge */
        if (this.gravityScale != 1F)
        {
            bodies.setGravityFactor(id, 1F + (this.gravityScale - 1F) * w);

            if (this.touched != null)
            {
                this.touched.add(id);
            }
        }

        float keep = Math.max(0F, 1F - this.damping * this.dt * w);
        Vec3 linear = bodies.getLinearVelocity(id);

        float lx = linear.getX() * keep + this.scratch.x;
        float ly = linear.getY() * keep + this.scratch.y;
        float lz = linear.getZ() * keep + this.scratch.z;

        if (!Float.isFinite(lx) || !Float.isFinite(ly) || !Float.isFinite(lz))
        {
            return;
        }

        this.vec.set(lx, ly, lz);
        bodies.setLinearVelocity(id, this.vec);

        Vec3 angular = bodies.getAngularVelocity(id);
        float ax = angular.getX() * keep;
        float ay = angular.getY() * keep;
        float az = angular.getZ() * keep;

        if (this.turbulence > 0F)
        {
            /* The tumble of being buffeted: the same field read a little way off, so it turns things as well as shoving them */
            this.noise(pz + 7.3F, px - 3.1F, py + 5.9F, this.scratch);
            ax += this.scratch.x * this.turbulence * this.dt * 0.25F * w;
            ay += this.scratch.y * this.turbulence * this.dt * 0.25F * w;
            az += this.scratch.z * this.turbulence * this.dt * 0.25F * w;
        }

        if (Float.isFinite(ax) && Float.isFinite(ay) && Float.isFinite(az))
        {
            this.vec.set(ax, ay, az);
            bodies.setAngularVelocity(id, this.vec);
        }

        bodies.activateBody(id);
    }
}
