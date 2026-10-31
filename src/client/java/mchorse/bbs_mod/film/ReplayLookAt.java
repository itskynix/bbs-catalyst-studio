package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turns a replay's actor toward a target after its keyframes were applied: another replay, the
 * film camera or a point in the world. The recorded pose is the base and is blended toward the
 * aimed one by the {@code look_at_weight} track, so 0 is the animation as authored and 1 is a full
 * lock.
 *
 * <p>The turn is the model block's look-at, step for step: the aim is followed as a continuous
 * yaw that never jumps when the target passes behind, the head takes the turn up to the model's
 * head limit and the body takes the rest, and once the body has wound most of a full turn round
 * the head eases back to the middle so the turn can be folded back by 360 degrees unseen.</p>
 */
public class ReplayLookAt
{
    public static final int OFF = 0;
    public static final int REPLAY = 1;
    public static final int CAMERA = 2;
    public static final int POINT = 3;

    /** The model block's defaults for a form without a model view */
    private static final float DEFAULT_LIMIT = 45F;
    private static final double DEFAULT_EYE = 1.62D;

    /** Yaw, head yaw, body yaw and pitch, in degrees as an entity holds them */
    private static final int YAW = 0;
    private static final int HEAD = 1;
    private static final int BODY = 2;
    private static final int PITCH = 3;

    /** The continuous aim of every actor and what it was given last, the model block's own state */
    private static final Map<IEntity, State> STATES = new WeakHashMap<>();

    private static class State
    {
        public float lastYaw = Float.NaN;
        public float currentYaw = Float.NaN;
        public int tick = Integer.MIN_VALUE;
        public float[] out;
        public float[] prevOut;
    }

    public static void apply(Replay replay, Film film, int ticks, IEntity entity)
    {
        if (replay == null || entity == null)
        {
            return;
        }

        if (replay.lookAtMode.get() == OFF)
        {
            STATES.remove(entity);

            return;
        }

        State state = STATES.computeIfAbsent(entity, (e) -> new State());
        float[] base = {entity.getYaw(), entity.getHeadYaw(), entity.getBodyYaw(), entity.getPitch()};
        float[] now = solve(replay, film, ticks, entity.getX(), entity.getY(), entity.getZ(), base, state);

        if (now == null)
        {
            return;
        }

        /* What the entity is drawn from: the frame before when the film stepped one tick, the same
         * as before when the tick was only asked again, and nothing when it jumped */
        float[] before;

        if (state.out != null && ticks == state.tick + 1)
        {
            before = state.out;
        }
        else if (state.prevOut != null && ticks == state.tick)
        {
            before = state.prevOut;
        }
        else
        {
            before = now;
        }

        state.prevOut = before;
        state.out = now;
        state.tick = ticks;

        entity.setYaw(now[YAW]);
        entity.setHeadYaw(now[HEAD]);
        entity.setBodyYaw(now[BODY]);
        entity.setPitch(now[PITCH]);

        entity.setPrevYaw(near(before[YAW], now[YAW]));
        entity.setPrevHeadYaw(near(before[HEAD], now[HEAD]));
        entity.setPrevBodyYaw(near(before[BODY], now[BODY]));
        entity.setPrevPitch(before[PITCH]);
    }

    private static float near(float value, float reference)
    {
        return reference + MathHelper.wrapDegrees(value - reference);
    }

    private static float[] solve(Replay replay, Film film, int tick, double sx, double sy, double sz, float[] base, State state)
    {
        Vector3d target = target(replay, film, tick);

        if (target == null)
        {
            return null;
        }

        ReplayKeyframes keys = replay.keyframes;
        float weight = keys.lookAtWeight.isEmpty() ? 1F : MathHelper.clamp(keys.lookAtWeight.interpolate((float) tick).floatValue(), 0F, 1F);
        Form form = replay.form.get();
        double eye = form == null ? DEFAULT_EYE : form.hitboxHeight.get() * form.hitboxEyeHeight.get();
        double dx = target.x - sx;
        double dz = target.z - sz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float initialYaw = base[BODY];

        /* The aim in the replay's yaw: 0 faces +Z, turning toward -X */
        float yaw = distance < 1.0E-4D ? initialYaw : (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;

        /* updateLookYawContinuous, started on the branch of the body so a body that has wound
         * several turns over the recording is not unwound by the first aim */
        if (Float.isNaN(state.currentYaw))
        {
            state.lastYaw = yaw;
            state.currentYaw = initialYaw + MathHelper.wrapDegrees(yaw - initialYaw);
        }
        else
        {
            state.currentYaw += MathHelper.wrapDegrees(yaw - state.lastYaw);
            state.lastYaw = yaw;
        }

        float yawDelta = state.currentYaw - initialYaw;
        float travel = Math.abs(yawDelta) % 360F;

        boolean lookAt = form instanceof MobForm;
        float constraint = DEFAULT_LIMIT;
        boolean isPitching = true;

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);
            View view = model == null ? null : model.getView();

            if (view != null)
            {
                lookAt = true;
                constraint = view.constraint;
                isPitching = view.pitch;
            }
        }

        float headRel = 0F;
        float anchorYaw = yawDelta;
        float pitch = base[PITCH];

        if (lookAt)
        {
            double deltaHead = target.y - (sy + eye);

            pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(deltaHead, distance)), -90D, 90D);
            headRel = getHeadYaw(constraint, yawDelta, travel);
            anchorYaw = yawDelta - headRel;

            if (travel >= 359F)
            {
                headRel = 0F;
                anchorYaw = 0F;

                /* snapLookYawToBase: the full turn is folded back while the head is centred */
                state.lastYaw = yaw;
                state.currentYaw = initialYaw;
            }

            if (!isPitching)
            {
                pitch = 0F;
            }
        }

        /* Blended with the recording on the continuous turn itself, so a partial weight turns
         * part of the way instead of taking a shortcut across the back */
        float baseHead = MathHelper.wrapDegrees(base[HEAD] - initialYaw);
        float baseLook = MathHelper.wrapDegrees(base[YAW] - initialYaw);
        float body = initialYaw + anchorYaw * weight;
        float[] out = new float[4];

        out[BODY] = body;
        out[HEAD] = body + baseHead + (headRel - baseHead) * weight;
        out[YAW] = body + baseLook + (headRel - baseLook) * weight;
        out[PITCH] = base[PITCH] + (pitch - base[PITCH]) * weight;

        return out;
    }

    /** The model block's head share of the turn: up to the limit, and back to the middle over the last 45 degrees of a full turn */
    private static float getHeadYaw(float constraint, float yawDelta, float travel)
    {
        float headYawBase = MathHelper.clamp(yawDelta, -constraint, constraint);
        float t = 0F;

        if (travel >= 315F)
        {
            t = Math.min(1F, (travel - 315F) / 45F);
        }

        return headYawBase * (1F - t);
    }

    private static Vector3d target(Replay replay, Film film, int tick)
    {
        float t = tick;

        switch (replay.lookAtMode.get())
        {
            case REPLAY:
            {
                Replay other = film == null ? null : find(film, replay.lookAtTarget.get());

                if (other == null || other == replay)
                {
                    return null;
                }

                float at = other.getTick(tick);
                Form form = other.form.get();
                double eye = form == null ? DEFAULT_EYE : form.hitboxHeight.get() * form.hitboxEyeHeight.get();

                return new Vector3d(
                    other.keyframes.x.interpolate(at),
                    other.keyframes.y.interpolate(at) + eye,
                    other.keyframes.z.interpolate(at)
                );
            }
            case CAMERA:
            {
                Camera camera = BBSModClient.getCameraController().camera;

                return new Vector3d(camera.position);
            }
            case POINT:
            {
                Point point = replay.lookAtPoint.get();
                ReplayKeyframes keys = replay.keyframes;

                return new Vector3d(
                    keys.lookAtX.isEmpty() ? point.x : keys.lookAtX.interpolate(t),
                    keys.lookAtY.isEmpty() ? point.y : keys.lookAtY.interpolate(t),
                    keys.lookAtZ.isEmpty() ? point.z : keys.lookAtZ.interpolate(t)
                );
            }
            default:
                return null;
        }
    }

    private static Replay find(Film film, String id)
    {
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
