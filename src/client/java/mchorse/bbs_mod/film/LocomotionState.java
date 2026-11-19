package mchorse.bbs_mod.film;

import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How an actor moves, in the actor's own frame, worked out once a tick while a film plays: the speed
 * along the way it faces, the speed to its side, and the speed as a share of a sprint. This is the
 * input a blend space needs - picking and mixing walk, run and strafe animations by direction and
 * speed - kept apart from the smoothing that produces it, so that blending can be built on it later
 * without touching how an actor is moved.
 *
 * <p>Speeds are in blocks per second. {@link #forwardSpeed} is positive forward, {@link #strafeSpeed}
 * positive to the actor's right.</p>
 */
public final class LocomotionState
{
    /** A sprint, in blocks per second, which {@link #normalizedSpeed} is measured against */
    public static final float SPRINT_SPEED = 5.612F;

    private static final Map<IEntity, LocomotionState> STATES = new WeakHashMap<>();

    public float forwardSpeed;
    public float strafeSpeed;
    /** The ground speed, whichever way it points */
    public float speed;
    /** The speed as a share of a sprint, 0 to 1 */
    public float normalizedSpeed;
    /** Where the movement points against the way the actor faces, in degrees: 0 forward, 90 to the right, 180 backwards */
    public float direction;

    private LocomotionState()
    {}

    /** The state of an actor, or an at-rest one when it has none yet; never null */
    public static LocomotionState of(IEntity entity)
    {
        LocomotionState state = STATES.get(entity);

        return state == null ? new LocomotionState() : state;
    }

    static LocomotionState get(IEntity entity)
    {
        return STATES.computeIfAbsent(entity, (e) -> new LocomotionState());
    }

    static void forget(IEntity entity)
    {
        STATES.remove(entity);
    }

    /** Takes a ground velocity in blocks per second and the way the actor faces (a Minecraft yaw, degrees) */
    void set(double vx, double vz, float yaw)
    {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        this.forwardSpeed = (float) (-vx * sin + vz * cos);
        this.strafeSpeed = (float) (-vx * cos - vz * sin);
        this.speed = (float) Math.sqrt(vx * vx + vz * vz);
        this.normalizedSpeed = Math.min(1F, this.speed / SPRINT_SPEED);
        this.direction = this.speed < 1.0E-3F ? 0F : (float) Math.toDegrees(Math.atan2(this.strafeSpeed, this.forwardSpeed));
    }
}
