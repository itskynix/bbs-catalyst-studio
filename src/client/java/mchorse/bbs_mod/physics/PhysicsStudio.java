package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import mchorse.bbs_mod.api.client.events.FormPoseEvents;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.film.IKBake;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The physics studio of the film editor: it owns the simulation of the film that is open, hands every simulated
 * replay its pose, and carries out the studio's one-click jobs, which are pushing, blasting and baking.
 *
 * <p>Nothing here costs anything for a film without physics: every entry point looks at the replay's switch
 * first, and the scene is only built once something is switched on.</p>
 */
public final class PhysicsStudio
{
    /** How many ticks one call may simulate; the rest follow on the next frame */
    private static final int BUDGET = 400;
    private static final long CHECK_NANOS = 120_000_000L;

    /** The turn a simulated replay is drawn with, in place of its facing; read by the matrix every actor is drawn under */
    private static final Map<IEntity, Quaternionf[]> TURNS = Collections.synchronizedMap(new WeakHashMap<>());

    private static PhysicsScene scene;
    private static long checkedAt;

    /** A ragdoll's pose for one frame: every bone body's centre and turn, and the animated frames they were pulled from */
    /**
     * A ragdoll's pose: every bone body's centre and turn and the animated frames they were pulled from, at the
     * tick and at the tick before. The render blends the two by its own partial tick, so a fall is as smooth as
     * the frame rate however few ticks the film runs at.
     */
    private record RagdollPose(RagdollSampler sampler, float[] previous, float[] bodies, Map<String, PivotFrame> previousAnimated, Map<String, PivotFrame> animated)
    {
        RagdollPose(RagdollSampler sampler, float[] bodies, Map<String, PivotFrame> animated)
        {
            this(sampler, bodies, bodies, animated, animated);
        }

        RagdollPose at(float transition)
        {
            if (this.previous == this.bodies || transition >= 1F)
            {
                return this;
            }

            Map<String, PivotFrame> frames = new HashMap<>();

            for (Map.Entry<String, PivotFrame> entry : this.animated.entrySet())
            {
                PivotFrame before = this.previousAnimated.get(entry.getKey());
                PivotFrame now = entry.getValue();

                frames.put(entry.getKey(), before == null ? now : new PivotFrame(new Vector3f(before.position()).lerp(now.position(), transition), now.parentRotation(), now.worldRotation(), now.scale()));
            }

            return new RagdollPose(this.sampler, blend(this.previous, this.bodies, Math.max(0F, transition)), frames);
        }
    }

    private static final Map<IEntity, RagdollPose> RAGDOLLS = Collections.synchronizedMap(new WeakHashMap<>());

    static
    {
        /* The bones take the ragdoll's turns at the model's physics stage, where the render already lets solvers
         * write a bone's orientation in place of its channels */
        FormPoseEvents.MODEL_POSE.register((form, entity, model, transition, base, pass) ->
        {
            RagdollPose pose = entity == null ? null : RAGDOLLS.get(entity);

            if (pose != null && model != null && model.model instanceof Model cubic)
            {
                Matrix4f frame = base != null ? base : FilmMatrices.getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);

                orientBones(cubic, pose.at(transition), frame);
            }
        });
    }

    /**
     * Writes the ragdoll onto the bones: each body bone's local orientation is what turns its parent's frame, as
     * the ragdoll moved it, onto the body's own turn, and the top bones are shifted by how far their bodies
     * travelled from where the animation stands them.
     */
    private static void orientBones(Model model, RagdollPose pose, Matrix4f base)
    {
        Map<String, PivotFrame> frames = new HashMap<>();

        ModelPivotFrames.collect(model, pose.sampler().ids, frames, base);

        List<RagdollSampler.Bone> bones = pose.sampler().bones;
        Quaternionf[] simulated = new Quaternionf[bones.size()];
        Quaternionf[] animated = new Quaternionf[bones.size()];

        for (int i = 0; i < bones.size(); i++)
        {
            RagdollSampler.Bone bone = bones.get(i);
            PivotFrame frame = frames.get(bone.id());
            ModelGroup group = model.getGroup(bone.id());
            float[] b = pose.bodies();

            if (frame == null || group == null || b.length < (i + 1) * 7)
            {
                continue;
            }

            Quaternionf world = new Quaternionf(b[i * 7 + 3], b[i * 7 + 4], b[i * 7 + 5], b[i * 7 + 6]).normalize();
            Quaternionf worldAnimated = new Quaternionf(frame.worldRotation()).normalize();
            Quaternionf parentAnimated = new Quaternionf(frame.parentRotation()).normalize();
            /* A bone hanging from a body takes the turn that body took on top of its animated frame; a detached bone's
             * parent in the model stands where the animation put it */
            Quaternionf parent = !bone.detached() && bone.parent() >= 0 && simulated[bone.parent()] != null
                ? new Quaternionf(simulated[bone.parent()]).mul(new Quaternionf(animated[bone.parent()]).conjugate()).mul(parentAnimated)
                : parentAnimated;

            simulated[i] = world;
            animated[i] = worldAnimated;
            group.orient = new Quaternionf(parent).conjugate().mul(world).normalize();

            PivotFrame start = pose.animated().get(bone.id());

            if (bone.detached() && start != null)
            {
                /* The body's pivot is its centre less the bone's own offset to the centre, turned */
                Vector3f scale = start.scale();
                Vector3f center = world.transform(new Vector3f(bone.center()).mul(scale));
                Vector3f pivot = new Vector3f(b[i * 7] - center.x, b[i * 7 + 1] - center.y, b[i * 7 + 2] - center.z);
                Vector3f shift = new Quaternionf(parentAnimated).conjugate().transform(pivot.sub(start.position()));

                shift.div(Math.max(1e-4F, scale.x), Math.max(1e-4F, scale.y), Math.max(1e-4F, scale.z));
                group.offset = shift;
            }
        }
    }

    private PhysicsStudio()
    {}

    /** Whether Jolt came up; false on a platform it cannot run on, and then none of the studio does anything */
    public static boolean available()
    {
        return JoltEngine.available();
    }

    /** The turn of a simulated replay, or null when it is drawn by its keys alone */
    /** The turn of a simulated replay at the render's partial tick, or null when it is drawn by its keys alone */
    public static Quaternionf turnOf(IEntity entity, float transition)
    {
        Quaternionf[] turns = TURNS.get(entity);

        if (turns == null)
        {
            return null;
        }

        return new Quaternionf(turns[0]).slerp(turns[1], Math.max(0F, Math.min(1F, transition)));
    }

    /** How many ticks of the film are simulated and cached, or -1 when nothing is */
    public static int cachedTicks()
    {
        return scene == null ? -1 : scene.computed();
    }

    /** Lets the simulation go, e.g. when the film editor closes */
    public static void release()
    {
        RAGDOLLS.clear();

        if (scene != null)
        {
            scene.close();
            scene = null;
        }

        TURNS.clear();
    }

    private static List<Replay> simulated(Film film)
    {
        List<Replay> list = new ArrayList<>();

        for (Replay replay : film.replays.getList())
        {
            if (replay.physics.enabled.get() && replay.enabled.get())
            {
                list.add(replay);
            }
        }

        return list;
    }

    private static String keyOf(Film film, List<Replay> replays)
    {
        StringBuilder builder = new StringBuilder();

        for (Replay replay : replays)
        {
            builder.append(replay.getId()).append('=').append(replay.physics.settingsKey()).append('#').append(System.identityHashCode(replay.form.get()));

            /* The form's physics mode and its bones' ragdoll settings change the bodies and joints themselves */
            if (replay.form.get() instanceof mchorse.bbs_mod.forms.forms.ModelForm model)
            {
                builder.append('#').append(model.physicsMode.get()).append('#').append(model.bones.toData().toString().hashCode());
            }

            builder.append(';');
        }

        return builder.toString();
    }

    /** The scene of the film, built when physics is on for something and rebuilt when what is simulated changed */
    private static PhysicsScene sceneOf(Film film)
    {
        List<Replay> replays = simulated(film);

        if (replays.isEmpty() || !available())
        {
            release();

            return null;
        }

        String key = keyOf(film, replays);

        if (scene == null || scene.film != film || !scene.key.equals(key))
        {
            release();

            scene = new PhysicsScene(film, key, replays, MinecraftClient.getInstance().world);
            checkedAt = 0L;
        }
        else
        {
            long now = System.nanoTime();

            if (now - checkedAt > CHECK_NANOS)
            {
                checkedAt = now;
                scene.validate();
            }
        }

        return scene;
    }

    /**
     * Lays the simulation over a replay's entity after its keys were applied: where the body is, and the way it
     * is turned. A tick between two whole ones is a blend of the neighbours, so playback is smooth and a scrub
     * to the same tick is always the same picture.
     */
    public static void apply(Film film, Replay replay, float tick, IEntity entity)
    {
        if (film == null || replay == null || entity == null)
        {
            return;
        }

        if (!replay.physics.enabled.get() || !replay.enabled.get())
        {
            TURNS.remove(entity);
            RAGDOLLS.remove(entity);

            return;
        }

        PhysicsScene scene = sceneOf(film);
        int rig = scene == null ? -1 : scene.indexOf(replay);

        if (rig < 0)
        {
            TURNS.remove(entity);

            return;
        }

        float clamped = Math.max(0F, tick);
        int low = (int) Math.floor(clamped);
        int high = low + 1;
        float k = clamped - low;

        /* A whole tick comes from the film's own tick: the pose of the tick before goes in as where the entity was,
         * and the render blends the two by its partial tick. A tick in between is already blended here. */
        boolean whole = k == 0F && low > 0;

        scene.advanceTo(high, BUDGET);

        PhysicsScene.Rig built = scene.rigs.get(rig);

        if (built.ragdoll != null)
        {
            /* A ragdoll stands where its keys stand it; the bones carry the rest */
            TURNS.remove(entity);

            Map<String, PivotFrame> animated = built.target(Math.min(low, scene.computed()));

            if (whole && !scene.live())
            {
                RAGDOLLS.put(entity, new RagdollPose(built.ragdoll, scene.poses(rig, low - 1), scene.poses(rig, low), built.target(Math.min(low - 1, scene.computed())), animated));
            }
            else
            {
                RAGDOLLS.put(entity, new RagdollPose(built.ragdoll, blend(scene.poses(rig, low), scene.poses(rig, high), k), animated));
            }

            return;
        }

        RAGDOLLS.remove(entity);

        float centerY = built.box.centerY();
        float[] held = PhysicsToolController.heldPose(replay);
        float[] now;
        float[] before;

        if (held != null)
        {
            /* A body in the hands of a tool is where the tool holds it, not where the simulation left it */
            now = held;
            before = held;
        }
        else if (whole && !scene.live())
        {
            now = scene.pose(rig, low);
            before = scene.pose(rig, low - 1);
        }
        else
        {
            now = blend(scene.pose(rig, low), scene.pose(rig, high), k);
            before = now;
        }

        Vector3f feet = PhysicsScene.feet(now, centerY);
        Vector3f was = PhysicsScene.feet(before, centerY);

        entity.setPosition(feet.x, feet.y, feet.z);
        entity.setPrevX(was.x);
        entity.setPrevY(was.y);
        entity.setPrevZ(was.z);

        TURNS.put(entity, new Quaternionf[] {new Quaternionf(before[3], before[4], before[5], before[6]).normalize(), new Quaternionf(now[3], now[4], now[5], now[6]).normalize()});
    }

    /** What a ray from the camera hits first among the simulated bodies at a tick: the replay, which of its bodies, and how far along the ray */
    public record Hit(Replay replay, int body, boolean limb, float distance, float[] pose, float centerY)
    {}

    /** Whether the physics studio draws its collision boxes and joints over the preview */
    public static boolean showShapes;

    public static Hit pick(Film film, Vector3d origin, Vector3f direction, int tick)
    {
        PhysicsScene scene = film == null ? null : sceneOf(film);

        if (scene == null)
        {
            return null;
        }

        scene.advanceTo(tick, BUDGET);

        Hit best = null;

        for (int i = 0; i < scene.rigs.size(); i++)
        {
            PhysicsScene.Rig rig = scene.rigs.get(i);
            float[] held = PhysicsToolController.heldPose(rig.replay);
            float[] all = held != null ? held : scene.poses(i, tick);

            for (int b = 0; b < rig.bodyCount() && b * 7 + 6 < all.length; b++)
            {
                float[] pose = java.util.Arrays.copyOfRange(all, b * 7, b * 7 + 7);
                float distance = hit(origin, direction, pose, rig.half(b));

                if (distance >= 0F && (best == null || distance < best.distance()))
                {
                    best = new Hit(rig.replay, b, rig.bones != null, distance, pose, rig.box.centerY());
                }
            }
        }

        return best;
    }

    /** How far along a ray a turned box is met, or -1 when it is missed */
    private static float hit(Vector3d origin, Vector3f direction, float[] pose, float[] half)
    {
        Quaternionf inverse = new Quaternionf(pose[3], pose[4], pose[5], pose[6]).conjugate();
        Vector3f from = inverse.transform(new Vector3f((float) (origin.x - pose[0]), (float) (origin.y - pose[1]), (float) (origin.z - pose[2])));
        Vector3f dir = inverse.transform(new Vector3f(direction));
        float[] o = {from.x, from.y, from.z};
        float[] d = {dir.x, dir.y, dir.z};
        float near = -Float.MAX_VALUE;
        float far = Float.MAX_VALUE;

        for (int a = 0; a < 3; a++)
        {
            if (Math.abs(d[a]) < 1e-6F)
            {
                if (Math.abs(o[a]) > half[a])
                {
                    return -1F;
                }

                continue;
            }

            float t1 = (-half[a] - o[a]) / d[a];
            float t2 = (half[a] - o[a]) / d[a];

            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }

        return near > far || far < 0F ? -1F : Math.max(0F, near);
    }

    /* The live hold of a ragdoll limb */

    public static boolean beginLive(Film film, Replay replay, int body, int tick)
    {
        PhysicsScene scene = film == null ? null : sceneOf(film);
        int rig = scene == null ? -1 : scene.indexOf(replay);

        return rig >= 0 && scene.beginLive(scene.rigs.get(rig), body, tick);
    }

    public static void stepLive(float[] target)
    {
        if (scene != null)
        {
            scene.stepLive(target);
        }
    }

    public static void endLive()
    {
        if (scene != null)
        {
            scene.endLive();
        }
    }

    /**
     * Freezes a ragdoll as the live hold has it: every bone's turn goes into its pose track on the tick, the same way
     * a bake writes it, and the animation handle holds it there. The hold ends afterwards.
     */
    public static boolean freezeLive(Film film, Replay replay, int tick)
    {
        PhysicsScene scene = film == null ? null : sceneOf(film);
        int rig = scene == null ? -1 : scene.indexOf(replay);

        if (rig < 0 || !scene.live() || scene.rigs.get(rig).ragdoll == null)
        {
            endLive();

            return false;
        }

        PhysicsScene.Rig built = scene.rigs.get(rig);
        /* Exactly what is on screen this frame, the live hold's in-between pose included, so nothing shifts on the freeze */
        float[] poses = scene.poses(rig, tick);
        Map<String, PivotFrame> animated = built.target(Math.min(tick, Math.max(0, built.targets.size() - 1)));

        scene.endLive();

        boolean done = IKBake.bakeSolved(film, replay, built.ragdoll.ids, tick, tick, (at, model, world) ->
        {
            if (model.model instanceof Model cubic)
            {
                orientBones(cubic, new RagdollPose(built.ragdoll, poses, animated), RagdollSampler.renderBase(world, replay.form.get()));
            }
        });

        BaseValue.edit(replay.physics, (physics) ->
        {
            if (tick > 0)
            {
                physics.animation.insert(tick - 1, (double) physics.animationAt(tick - 1));
            }

            physics.animation.insert(tick, 1D);
        });

        return done;
    }

    /**
     * The collision boxes of every simulated body at a tick, ten numbers each: centre, turn, half extents; and
     * the joint points of the ragdolls, three numbers each.
     */
    public static List<float[]> shapes(Film film, int tick, List<float[]> joints)
    {
        List<float[]> list = new ArrayList<>();
        PhysicsScene scene = film == null ? null : sceneOf(film);

        if (scene == null)
        {
            return list;
        }

        scene.advanceTo(tick, BUDGET);

        for (int i = 0; i < scene.rigs.size(); i++)
        {
            PhysicsScene.Rig rig = scene.rigs.get(i);
            float[] all = scene.poses(i, tick);

            for (int b = 0; b < rig.bodyCount() && b * 7 + 6 < all.length; b++)
            {
                float[] half = rig.half(b);

                list.add(new float[] {all[b * 7], all[b * 7 + 1], all[b * 7 + 2], all[b * 7 + 3], all[b * 7 + 4], all[b * 7 + 5], all[b * 7 + 6], half[0], half[1], half[2]});

                if (rig.ragdoll != null && joints != null && rig.ragdoll.bones.get(b).parent() >= 0)
                {
                    Quaternionf q = new Quaternionf(all[b * 7 + 3], all[b * 7 + 4], all[b * 7 + 5], all[b * 7 + 6]);
                    Vector3f offset = q.transform(new Vector3f(rig.ragdoll.bones.get(b).center()));

                    joints.add(new float[] {all[b * 7] - offset.x, all[b * 7 + 1] - offset.y, all[b * 7 + 2] - offset.z});
                }
            }
        }

        return list;
    }

    /** Every body of two poses blended: positions straight, turns along the shorter way */
    static float[] blend(float[] a, float[] b, float k)
    {
        float[] out = new float[a.length];

        for (int i = 0; i + 6 < a.length && i + 6 < b.length; i += 7)
        {
            for (int j = 0; j < 3; j++)
            {
                out[i + j] = a[i + j] + (b[i + j] - a[i + j]) * k;
            }

            Quaternionf q = new Quaternionf(a[i + 3], a[i + 4], a[i + 5], a[i + 6]).nlerp(new Quaternionf(b[i + 3], b[i + 4], b[i + 5], b[i + 6]), k);

            out[i + 3] = q.x;
            out[i + 4] = q.y;
            out[i + 5] = q.z;
            out[i + 6] = q.w;
        }

        return out;
    }

    /* The jobs */

    /** A push on the tick: a change of velocity, in blocks per second, keyed on the replay's own tracks */
    public static void push(Replay replay, int tick, float dx, float dy, float dz)
    {
        BaseValue.edit(replay.physics, (physics) ->
        {
            insert(physics.pushX, tick, dx);
            insert(physics.pushY, tick, dy);
            insert(physics.pushZ, tick, dz);
        });
    }

    /** A blast on the tick, from a point, with a strength that is the speed it gives at the point itself */
    public static void blast(Replay replay, int tick, Vector3d origin, float strength)
    {
        BaseValue.edit(replay.physics, (physics) ->
        {
            insert(physics.blast, tick, strength);
            insert(physics.blastX, tick, origin.x);
            insert(physics.blastY, tick, origin.y);
            insert(physics.blastZ, tick, origin.z);
        });
    }

    private static void insert(KeyframeChannel<Double> channel, int tick, double value)
    {
        channel.insert(tick, value);
    }

    /** What a bake wrote, for the message the studio shows */
    public record BakeResult(int keyframes, int ticks)
    {}

    /**
     * Turns the simulation of a replay into keyframes of its own position and facing, over the whole film, and
     * switches the simulation off so the keys are what plays.
     *
     * <p>A stretch where the body does not move becomes two keyframes, its first tick and its last, instead of one
     * per tick; keys the author already placed are left alone; and all of it is one undoable edit.</p>
     */
    public static BakeResult bake(Film film, Replay replay)
    {
        PhysicsScene scene = sceneOf(film);
        int rig = scene == null ? -1 : scene.indexOf(replay);

        if (rig < 0)
        {
            return null;
        }

        int last = Math.max(1, Math.min(PhysicsScene.MAX_TICKS, Math.max(film.camera.calculateDuration(), (int) Math.ceil(replay.getLastKeyframeTick()))));

        scene.advanceTo(last, Integer.MAX_VALUE);

        PhysicsScene.Rig built = scene.rigs.get(rig);

        if (built.ragdoll != null)
        {
            /* Every bone's turn, and the top bone's shift, go into the bones' own pose tracks through the same
             * sampling and arithmetic the IK bake uses */
            PhysicsScene baked = scene;
            int computed = baked.computed();
            boolean done = IKBake.bakeSolved(film, replay, built.ragdoll.ids, 0, computed, (tick, model, world) ->
            {
                if (model.model instanceof Model cubic)
                {
                    orientBones(cubic, new RagdollPose(built.ragdoll, baked.poses(rig, tick), built.target(Math.min(tick, computed))), RagdollSampler.renderBase(world, replay.form.get()));
                }
            });

            if (!done)
            {
                return null;
            }

            BaseValue.edit(replay.physics, (physics) -> physics.enabled.set(false));
            release();

            return new BakeResult(built.ragdoll.ids.size() * (computed + 1), computed + 1);
        }

        int count = scene.computed() + 1;
        double[][] values = new double[count][5];
        float centerY = scene.rigs.get(rig).box.centerY();

        for (int tick = 0; tick < count; tick++)
        {
            float[] pose = scene.pose(rig, tick);
            Vector3f feet = PhysicsScene.feet(pose, centerY);
            Quaternionf q = new Quaternionf(pose[3], pose[4], pose[5], pose[6]);
            Vector3f angles = q.getEulerAnglesYXZ(new Vector3f());
            double yaw = -Math.toDegrees(angles.y);

            values[tick][0] = feet.x;
            values[tick][1] = feet.y;
            values[tick][2] = feet.z;
            values[tick][3] = yaw;
            values[tick][4] = yaw;
        }

        int[] written = new int[1];

        BaseValue.edit(replay, (target) ->
        {
            KeyframeChannel<Double>[] channels = new KeyframeChannel[] {target.keyframes.x, target.keyframes.y, target.keyframes.z, target.keyframes.yaw, target.keyframes.bodyYaw};

            for (int tick = 0; tick < count; tick++)
            {
                boolean first = tick == 0;
                boolean end = tick == count - 1;

                if (!first && !end && same(values[tick], values[tick - 1]) && same(values[tick], values[tick + 1]))
                {
                    continue;
                }

                for (int i = 0; i < channels.length; i++)
                {
                    if (!hasKeyframe(channels[i], tick))
                    {
                        channels[i].insert(tick, values[tick][i]);
                        written[0] += 1;
                    }
                }
            }

            target.physics.enabled.set(false);
        });

        release();

        return new BakeResult(written[0], count);
    }

    private static boolean same(double[] a, double[] b)
    {
        for (int i = 0; i < a.length; i++)
        {
            if (Math.abs(a[i] - b[i]) > 1e-4D)
            {
                return false;
            }
        }

        return true;
    }

    private static boolean hasKeyframe(KeyframeChannel<Double> channel, int tick)
    {
        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (Math.round(keyframe.getTick()) == tick)
            {
                return true;
            }
        }

        return false;
    }
}
