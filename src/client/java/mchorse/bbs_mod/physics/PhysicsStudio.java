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

import java.util.ArrayList;
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
    private static final Map<IEntity, Quaternionf> TURNS = Collections.synchronizedMap(new WeakHashMap<>());

    private static PhysicsScene scene;
    private static long checkedAt;

    private PhysicsStudio()
    {}

    /** Whether Jolt came up; false on a platform it cannot run on, and then none of the studio does anything */
    public static boolean available()
    {
        return JoltEngine.available();
    }

    /** The turn of a simulated replay, or null when it is drawn by its keys alone */
    public static Quaternionf turnOf(IEntity entity)
    {
        return TURNS.get(entity);
    }

    /** How many ticks of the film are simulated and cached, or -1 when nothing is */
    public static int cachedTicks()
    {
        return scene == null ? -1 : scene.computed();
    }

    /** Lets the simulation go, e.g. when the film editor closes */
    public static void release()
    {
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
            builder.append(replay.getId()).append('=').append(replay.physics.settingsKey()).append('#').append(System.identityHashCode(replay.form.get())).append(';');
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

        scene.advanceTo(high, BUDGET);

        float[] a = scene.pose(rig, low);
        float[] b = scene.pose(rig, high);
        float k = clamped - low;
        float[] pose = new float[7];

        for (int i = 0; i < 3; i++)
        {
            pose[i] = a[i] + (b[i] - a[i]) * k;
        }

        Quaternionf q = new Quaternionf(a[3], a[4], a[5], a[6]).nlerp(new Quaternionf(b[3], b[4], b[5], b[6]), k).normalize();

        pose[3] = q.x;
        pose[4] = q.y;
        pose[5] = q.z;
        pose[6] = q.w;

        Vector3f feet = PhysicsScene.feet(pose, scene.rigs.get(rig).box.centerY());

        entity.setPosition(feet.x, feet.y, feet.z);
        entity.setPrevX(feet.x);
        entity.setPrevY(feet.y);
        entity.setPrevZ(feet.z);

        TURNS.put(entity, q);
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
