package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.api.client.events.FilmEvents;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Caps how many of a scene's forms get drawn per frame with focus-distance support.
 *
 * <p>Uses camera look-direction (frustum weighting) so that actors in the camera's field of view
 * are prioritized over actors behind the camera, eliminating ghost renders and missing replays.
 * Tracks both position and rotation with a sensitive deadband for smooth, responsive movement.
 * The model of a selected replay obeys LOD and focus culling rules, while its transform gizmo
 * remains visible in the editor.</p>
 *
 * <p>Every film controller owns its own {@link State}, so concurrent films and previews never
 * overwrite each other's culling data.</p>
 */
public class LodEngine
{
    private static final Map<BaseFilmController, State> states = new WeakHashMap<>();

    /** Movement deadband: ~0.1 blocks. */
    private static final double MOVE_THRESHOLD_SQ = 0.01D;

    /** Rotation deadband: 0.5 degrees. */
    private static final float ROTATION_THRESHOLD = 0.5F;

    /**
     * The actors move on their own while the camera stands still (a locked-off shot of a film
     * playing), and the ranking is by their distance: it is redone at least this often (frames)
     * whatever the camera does, or an actor walking up to a still camera stayed hidden.
     */
    private static final int RERANK_FRAMES = 10;

    public static void register()
    {
        FormRenderEvents.BEFORE.register(LodEngine::before);
        FilmEvents.RENDER_AFTER.register(LodEngine::onRenderAfter);
        FilmEvents.SHUTDOWN.register(LodEngine::onShutdown);
    }

    /**
     * Intercepts form rendering: if this form was ranked as culled for the current frame,
     * set its visible runtime value to false so its model, shadow and nametag are skipped.
     */
    private static void before(Form form, FormRenderingContext context)
    {
        /* Never cull in UI previews, when LOD is disabled, or during video recording/export */
        if (context.ui || BBSSettings.replayLod == null || !BBSSettings.replayLod.get() || !form.visible.get())
        {
            return;
        }

        if (BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording())
        {
            return;
        }

        for (State state : states.values())
        {
            if (state.budgetValid && state.culled.contains(form))
            {
                form.visible.setRuntimeValue(Boolean.FALSE);
                state.touched.add(form);

                return;
            }
        }
    }

    /**
     * Ranks this controller's forms by distance/focus to the camera with view-direction weighting.
     */
    private static void onRenderAfter(BaseFilmController controller, WorldRenderContext context)
    {
        State state = states.computeIfAbsent(controller, (c) -> new State());

        state.restore();

        boolean enabled = BBSSettings.replayLod != null && BBSSettings.replayLod.get();
        int limit = BBSSettings.replayLodLimit != null ? BBSSettings.replayLodLimit.get() : 100;

        /* If video recorder is active, never cull */
        if (BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording())
        {
            state.reset();

            return;
        }

        state.budgetValid = enabled && limit > 0;

        if (!state.budgetValid)
        {
            /* LOD went off: every form it hid gets its default visibility back before the sets go */
            state.reset();

            return;
        }

        Camera camera = context.camera();

        if (camera == null)
        {
            return;
        }

        Vec3d camPos = camera.getPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        double focus = BBSSettings.replayLodFocus != null ? BBSSettings.replayLodFocus.get() : 0D;
        int entityCount = controller.getEntities().size();

        double dx = camPos.x - state.lastCamX;
        double dy = camPos.y - state.lastCamY;
        double dz = camPos.z - state.lastCamZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        float dYaw = Math.abs(yaw - state.lastYaw);
        float dPitch = Math.abs(pitch - state.lastPitch);

        boolean settingsChanged = enabled != state.lastEnabled || limit != state.lastLimit || focus != state.lastFocus || entityCount != state.lastEntityCount;
        boolean camMoved = distSq >= MOVE_THRESHOLD_SQ;
        boolean camRotated = dYaw >= ROTATION_THRESHOLD || dPitch >= ROTATION_THRESHOLD;

        state.framesSinceRank++;

        /* Skip re-ranking only when camera is completely stationary, settings are unchanged and
         * the last ranking is recent enough to still hold for actors that moved */
        if (!settingsChanged && !camMoved && !camRotated && !state.candidates.isEmpty() && state.framesSinceRank < RERANK_FRAMES)
        {
            return;
        }

        state.framesSinceRank = 0;

        state.lastCamX = camPos.x;
        state.lastCamY = camPos.y;
        state.lastCamZ = camPos.z;
        state.lastYaw = yaw;
        state.lastPitch = pitch;
        state.lastFocus = focus;
        state.lastLimit = limit;
        state.lastEnabled = enabled;
        state.lastEntityCount = entityCount;

        /* Compute forward gaze direction from camera yaw and pitch */
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double lookX = -Math.sin(yawRad) * cosPitch;
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * cosPitch;

        int count = 0;

        for (IEntity entity : controller.getEntities().values())
        {
            Form form = entity.getForm();

            /* A form that carries a sound is never hidden: hiding it takes its sound off the draw that
             * places it, and the rank flips with every turn of the head - the sound jumped between
             * two places and stuttered as the actor went in and out of the budget */
            if (form == null || form.anchor.get().hasTarget() || carriesSound(form))
            {
                continue;
            }

            double fx = entity.getX() - camPos.x;
            double fy = entity.getY() - camPos.y;
            double fz = entity.getZ() - camPos.z;
            double dist = Math.sqrt(fx * fx + fy * fy + fz * fz);
            double invDist = dist > 0.0001D ? 1.0D / dist : 0.0D;
            double dot = (fx * lookX + fy * lookY + fz * lookZ) * invDist;

            double err = focus > 0D ? Math.abs(dist - focus) : dist;
            double score;

            /* Frustum weighting:
             * dot < 0.1 means actor is behind or outside view frustum — heavy penalty.
             * dot >= 0.1 slightly favors actors toward screen center. */
            if (dot < 0.1D)
            {
                score = err + 1000.0D * (0.1D - dot);
            }
            else
            {
                score = err + (1.0D - dot) * 0.5D;
            }

            if (count < state.candidates.size())
            {
                state.candidates.get(count).set(form, score);
            }
            else
            {
                state.candidates.add(new Candidate(form, score));
            }

            count++;
        }

        while (state.candidates.size() > count)
        {
            state.candidates.remove(state.candidates.size() - 1);
        }

        state.candidates.sort(null);

        /* Update state.culled set for next frame's BEFORE pass */
        state.culled.clear();
        int effectiveLimit = Math.min(limit, count);

        for (int i = effectiveLimit; i < count; i++)
        {
            state.culled.add(state.candidates.get(i).form);
        }
    }

    private static boolean carriesSound(Form form)
    {
        if (form instanceof mchorse.bbs_mod.forms.forms.AudioForm)
        {
            return true;
        }

        for (mchorse.bbs_mod.forms.forms.BodyPart part : form.parts.getAllTyped())
        {
            if (part.getForm() != null && carriesSound(part.getForm()))
            {
                return true;
            }
        }

        return false;
    }

    private static void onShutdown(BaseFilmController controller)
    {
        State state = states.remove(controller);

        if (state != null)
        {
            state.reset();
        }
    }

    /** Per-controller LOD state. */
    private static class State
    {
        /** Forms whose visible runtimeValue was set to false this frame, restored on RENDER_AFTER. */
        final Set<Form> touched = Collections.newSetFromMap(new IdentityHashMap<>());

        /** Forms determined by the latest ranking to be outside the render limit / focus window. */
        final Set<Form> culled = Collections.newSetFromMap(new IdentityHashMap<>());

        final List<Candidate> candidates = new ArrayList<>();

        double lastCamX = Double.NaN;
        double lastCamY = Double.NaN;
        double lastCamZ = Double.NaN;
        float lastYaw = Float.NaN;
        float lastPitch = Float.NaN;
        double lastFocus = -1D;
        int lastLimit = -1;
        boolean lastEnabled = false;
        int lastEntityCount = -1;
        int framesSinceRank;
        boolean budgetValid = false;

        /** Gives every form this state hid its default visibility back. */
        void restore()
        {
            for (Form form : this.touched)
            {
                form.visible.setRuntimeValue(null);
            }

            for (Form form : this.culled)
            {
                form.visible.setRuntimeValue(null);
            }

            this.touched.clear();
        }

        void reset()
        {
            this.restore();

            this.lastCamX = Double.NaN;
            this.lastCamY = Double.NaN;
            this.lastCamZ = Double.NaN;
            this.lastYaw = Float.NaN;
            this.lastPitch = Float.NaN;
            this.lastFocus = -1D;
            this.lastLimit = -1;
            this.lastEntityCount = -1;
            this.lastEnabled = false;
            this.budgetValid = false;
            this.culled.clear();
            this.candidates.clear();
        }
    }

    private static class Candidate implements Comparable<Candidate>
    {
        public Form form;
        public double score;

        public Candidate(Form form, double score)
        {
            this.form = form;
            this.score = score;
        }

        public void set(Form form, double score)
        {
            this.form = form;
            this.score = score;
        }

        @Override
        public int compareTo(Candidate other)
        {
            return Double.compare(this.score, other.score);
        }
    }
}
