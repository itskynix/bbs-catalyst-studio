package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Locomotion smoothing: an actor that starts, turns and stops with some inertia on the screen, while
 * landing exactly where its keyframes put it.
 *
 * <p>Each tick the keyframes give the actor's place. With smoothing on, what is shown follows that
 * place through a critically damped spring - no overshoot, no ringing - so a start eases in and a
 * stop eases out. A spring alone would trail the keys by its time constant the whole time the actor
 * walks, and a scene timed to the keys (a hand reaching a door as the actor arrives) would be late. So
 * the spring does not follow the keyed place but the place the actor will be in one time constant from
 * now, taken from how fast the keys are moving: that is exactly the lead that cancels a spring's lag
 * on steady movement, so a walk is neither early nor late and only its changes are softened. When the
 * keys stop the lead is gone, the spring settles onto the keyed place, and the last millimetre is
 * snapped - it ends exactly on the target.</p>
 *
 * <p>The state is stepped one tick at a time; anything that is not the very next tick (a scrub, a
 * jump, a loop, a first sight) starts it over on the keyed place, so a paused or scrubbed frame is the
 * keyed one, the same every time. Only the ground plane is smoothed: height is left to the keys, so
 * jumps and falls keep their timing.</p>
 *
 * <p>Whatever the setting, the actor's {@link LocomotionState} is kept up to date.</p>
 */
public final class LocomotionSmoother
{
    private static final double TICK = 0.05D;

    /** A move this far in one tick is a teleport, not a walk, and is not smoothed */
    private static final double TELEPORT = 8D;

    /** Closer than this and slower than {@link #REST_SPEED}, it has arrived */
    private static final double REST_DISTANCE = 0.002D;
    private static final double REST_SPEED = 0.02D;

    private static final Map<IEntity, State> STATES = new WeakHashMap<>();

    private static class State
    {
        int tick = Integer.MIN_VALUE;
        double lastX;
        double lastZ;
        double x;
        double z;
        double vx;
        double vz;
    }

    private LocomotionSmoother()
    {}

    /**
     * Called every tick after the keyframes have put the actor on its keyed place; leaves the shown
     * place in the entity.
     */
    public static void apply(Replay replay, int ticks, IEntity entity)
    {
        if (replay == null || entity == null)
        {
            return;
        }

        State state = STATES.computeIfAbsent(entity, (e) -> new State());
        LocomotionState locomotion = LocomotionState.get(entity);
        double tx = entity.getX();
        double tz = entity.getZ();
        double ty = entity.getY();

        boolean next = state.tick != Integer.MIN_VALUE && ticks == state.tick + 1;
        double jump = Math.hypot(tx - state.lastX, tz - state.lastZ);

        if (!next || jump > TELEPORT || !finite(tx, tz))
        {
            state.tick = ticks;
            state.lastX = tx;
            state.lastZ = tz;
            state.x = tx;
            state.z = tz;
            state.vx = 0D;
            state.vz = 0D;
            locomotion.set(0D, 0D, entity.getBodyYaw());

            return;
        }

        /* The keys' own velocity, in blocks per second */
        double kx = (tx - state.lastX) / TICK;
        double kz = (tz - state.lastZ) / TICK;

        state.tick = ticks;
        state.lastX = tx;
        state.lastZ = tz;

        if (!replay.locomotionSmoothing.get())
        {
            state.x = tx;
            state.z = tz;
            state.vx = kx;
            state.vz = kz;
            locomotion.set(kx, kz, entity.getBodyYaw());

            return;
        }

        double time = Math.max(0.02D, replay.locomotionTime.get());

        /* The place one time constant ahead, which cancels the spring's lag on a steady walk */
        double gx = tx + kx * time;
        double gz = tz + kz * time;

        /* Critically damped spring, exact over the step (the smooth-damp closed form) */
        double omega = 2D / time;
        double x = omega * TICK;
        double damp = 1D / (1D + x + 0.48D * x * x + 0.235D * x * x * x);

        double changeX = state.x - gx;
        double tempX = (state.vx + omega * changeX) * TICK;

        state.vx = (state.vx - omega * tempX) * damp;
        state.x = gx + (changeX + tempX) * damp;

        double changeZ = state.z - gz;
        double tempZ = (state.vz + omega * changeZ) * TICK;

        state.vz = (state.vz - omega * tempZ) * damp;
        state.z = gz + (changeZ + tempZ) * damp;

        /* Arrived: the keys are still and what is shown is on them to the last millimetre */
        if (Math.hypot(kx, kz) < REST_SPEED && Math.hypot(state.x - tx, state.z - tz) < REST_DISTANCE && Math.hypot(state.vx, state.vz) < REST_SPEED)
        {
            state.x = tx;
            state.z = tz;
            state.vx = 0D;
            state.vz = 0D;
        }

        if (!finite(state.x, state.z) || !finite(state.vx, state.vz))
        {
            state.x = tx;
            state.z = tz;
            state.vx = 0D;
            state.vz = 0D;
        }

        entity.setPosition(state.x, ty, state.z);
        locomotion.set(state.vx, state.vz, entity.getBodyYaw());
    }

    private static boolean finite(double a, double b)
    {
        return Double.isFinite(a) && Double.isFinite(b);
    }

    /** An actor that is not played from a replay any more */
    public static void forget(IEntity entity)
    {
        STATES.remove(entity);
        LocomotionState.forget(entity);
    }
}
