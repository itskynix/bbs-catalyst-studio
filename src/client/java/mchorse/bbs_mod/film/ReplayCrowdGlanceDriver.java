package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayCrowdGlance;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays a replay's {@link ReplayCrowdGlance}: the actor turns its head to the target when it comes
 * close, holds the look for a while and turns back to the head the animation gives it. Nothing is
 * written into the keyframes; the glance is laid over the head after the keyframes and the look-at
 * were applied, every tick.
 *
 * <p>The glance is a little state machine stepped one tick at a time: idle, glancing (the weight goes
 * up over the blend ticks, the aim follows the target), returning (the weight goes down, the aim stays
 * where it last was so the head goes back from where it looked). A glance ends when its time is up,
 * when the target leaves the radius or when the head would have to turn past the neck limit; after it
 * the actor rests for the cooldown, or until the target has stepped out of the radius.</p>
 *
 * <p>The state only depends on the film's ticks, never on frames or the clock: playing steps it by one,
 * a jump forward steps it through every tick skipped, and a jump back (scrubbing, rewinding, a looping
 * replay wrapping around) runs it again from the film's start. A paused film asks the same tick again
 * and gets the same head. So the live preview, a scrubbed frame and a video render all agree.</p>
 */
public class ReplayCrowdGlanceDriver
{
    private static final int IDLE = 0;
    private static final int GLANCING = 1;
    private static final int RETURNING = 2;

    /** A head turned at most this far up or down to the target */
    private static final double MAX_PITCH = 60D;

    /** How far back a jump runs the state from, at most, so a very long film can't stall a frame */
    private static final int MAX_REPLAY = 24000;

    private static final double DEFAULT_EYE = 1.62D;

    private static final Map<IEntity, State> STATES = new WeakHashMap<>();

    private static class State
    {
        public Replay replay;
        public Replay target;
        public long settings;
        public int tick = Integer.MIN_VALUE;

        public int phase = IDLE;
        public int held;
        public int rest;
        public double weight;
        public double aimYaw;
        public double aimPitch;

        /* The weight and aim of the tick before, for the entity's previous head */
        public double prevWeight;
        public double prevAimYaw;
        public double prevAimPitch;

        public void reset(Replay replay, Replay target, long settings)
        {
            this.replay = replay;
            this.target = target;
            this.settings = settings;
            this.tick = Integer.MIN_VALUE;
            this.phase = IDLE;
            this.held = 0;
            this.rest = 0;
            this.weight = 0D;
            this.aimYaw = 0D;
            this.aimPitch = 0D;
            this.prevWeight = 0D;
            this.prevAimYaw = 0D;
            this.prevAimPitch = 0D;
        }
    }

    public static void apply(Replay replay, Film film, int ticks, IEntity entity)
    {
        if (replay == null || entity == null)
        {
            return;
        }

        ReplayCrowdGlance glance = replay.crowdGlance;
        Replay target = glance.enabled.get() && film != null ? find(film, glance.target.get()) : null;

        if (target == null || target == replay)
        {
            STATES.remove(entity);

            return;
        }

        State state = STATES.computeIfAbsent(entity, (e) -> new State());
        long settings = settings(glance);

        /* A different replay, target or setting makes the past a different one: run it again */
        if (state.replay != replay || state.target != target || state.settings != settings || ticks < state.tick)
        {
            state.reset(replay, target, settings);
        }

        if (ticks != state.tick)
        {
            int from = state.tick == Integer.MIN_VALUE ? Math.max(0, ticks - MAX_REPLAY) : state.tick + 1;

            if (ticks - from > MAX_REPLAY)
            {
                state.reset(replay, target, settings);
                from = Math.max(0, ticks - MAX_REPLAY);
            }

            for (int t = from; t <= ticks; t++)
            {
                step(state, replay, target, glance, t);
            }

            state.tick = ticks;
        }

        if (state.weight <= 0D && state.prevWeight <= 0D)
        {
            return;
        }

        /* Over whatever head the actor has now, keyframed or turned by the look-at */
        double head = entity.getHeadYaw();
        double pitch = entity.getPitch();
        double prevHead = entity.getPrevHeadYaw();
        double prevPitch = entity.getPrevPitch();
        double eased = ease(state.weight);
        double prevEased = ease(state.prevWeight);

        entity.setHeadYaw((float) (head + wrap(state.aimYaw - head) * eased));
        entity.setPitch((float) (pitch + (state.aimPitch - pitch) * eased));
        entity.setPrevHeadYaw((float) (prevHead + wrap(state.prevAimYaw - prevHead) * prevEased));
        entity.setPrevPitch((float) (prevPitch + (state.prevAimPitch - prevPitch) * prevEased));
    }

    /** One tick of the glance, read off the keyframes of both replays at that tick. */
    private static void step(State state, Replay replay, Replay target, ReplayCrowdGlance glance, int t)
    {
        ReplayKeyframes keys = replay.keyframes;
        ReplayKeyframes goal = target.keyframes;
        int at = target.getTick(t);

        double dx = goal.x.interpolateDouble(at) - keys.x.interpolateDouble(t);
        double feet = goal.y.interpolateDouble(at) - keys.y.interpolateDouble(t);
        double dz = goal.z.interpolateDouble(at) - keys.z.interpolateDouble(t);
        double flat = Math.sqrt(dx * dx + dz * dz);
        double body = keys.bodyYaw.interpolateDouble(t);

        /* Distance is feet to feet, the look is eyes to eyes */
        double dy = feet + eye(target) - eye(replay);

        /* The game's yaw: 0 faces +Z and turns toward -X, the same aim as ReplayLookAt */
        double yaw = flat < 1.0E-4D ? body : Math.toDegrees(Math.atan2(dz, dx)) - 90D;
        boolean inside = Math.sqrt(flat * flat + feet * feet) <= glance.radius.get();
        boolean reachable = Math.abs(wrap(yaw - body)) <= glance.maxNeckAngle.get();
        double speed = 1D / Math.max(1, glance.blendTicks.get());

        state.prevWeight = state.weight;
        state.prevAimYaw = state.aimYaw;
        state.prevAimPitch = state.aimPitch;

        if (state.phase == IDLE)
        {
            if (state.rest > 0)
            {
                /* The rest ends early once the target has walked away */
                state.rest = inside ? state.rest - 1 : 0;
            }

            if (state.rest == 0 && inside && reachable)
            {
                state.phase = GLANCING;
                state.held = 0;
            }
        }
        else if (state.phase == GLANCING)
        {
            state.held += 1;

            if (state.held > glance.duration.get() || !inside || !reachable)
            {
                state.phase = RETURNING;
            }
        }

        if (state.phase == GLANCING)
        {
            state.aimYaw = yaw;
            state.aimPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, -Math.toDegrees(Math.atan2(dy, flat))));
            state.weight = Math.min(1D, state.weight + speed);

            /* The first tick of a glance: the tick before had no aim, it starts from where it is */
            if (state.prevWeight <= 0D)
            {
                state.prevAimYaw = state.aimYaw;
                state.prevAimPitch = state.aimPitch;
            }
        }
        else if (state.phase == RETURNING)
        {
            state.weight = Math.max(0D, state.weight - speed);

            if (state.weight <= 0D)
            {
                state.phase = IDLE;
                state.rest = glance.cooldownTicks.get();
            }
        }
    }

    private static double eye(Replay replay)
    {
        Form form = replay.form.get();

        return form == null ? DEFAULT_EYE : form.hitboxHeight.get() * form.hitboxEyeHeight.get();
    }

    /** Smoothstep: the head starts and stops turning gently */
    private static double ease(double weight)
    {
        return weight * weight * (3D - 2D * weight);
    }

    private static double wrap(double degrees)
    {
        return degrees - 360D * Math.floor((degrees + 180D) / 360D);
    }

    private static long settings(ReplayCrowdGlance glance)
    {
        long hash = Float.floatToIntBits(glance.radius.get());

        hash = hash * 31 + glance.duration.get();
        hash = hash * 31 + Float.floatToIntBits(glance.maxNeckAngle.get());
        hash = hash * 31 + glance.blendTicks.get();
        hash = hash * 31 + glance.cooldownTicks.get();

        return hash;
    }

    private static Replay find(Film film, String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        for (Replay replay : film.replays.getList())
        {
            if (replay.getId().equals(id))
            {
                return replay;
            }
        }

        return null;
    }
}
