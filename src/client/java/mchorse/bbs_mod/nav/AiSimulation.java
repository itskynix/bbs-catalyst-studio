package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.NavigationVolumeForm;
import mchorse.bbs_mod.physics.forms.FormTreeWalk;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * The walks of a film's AI actors, worked out ahead for the whole film and then simply read.
 *
 * <p>An actor with AI goes where it likes inside the film's navigation volume: it picks a place on the
 * mesh, finds its way there (A*), walks it, waits a few seconds, picks the next. Playing that live
 * would make the film different every time and unreadable when scrubbed, so all of the film's AI
 * actors are walked together, tick by tick, from the start, once, with every random choice drawn from
 * a generator seeded by the actor's own seed; the answer for each tick is an array that playing,
 * scrubbing and exporting all read. The same film is the same walk. The walk is made again only when
 * something it depends on changes: an actor's AI settings, its starting place, the film's length, the
 * volume, or the blocks of the mesh.</p>
 *
 * <p>Actors walk together: each is pushed away from the others it gets within a body's width of, so two
 * on one path part rather than pass through each other - as long as the push leaves it on the mesh.</p>
 */
public final class AiSimulation
{
    private static final float DT = 0.05F;
    private static final int MIN_TICKS = 600;
    private static final int MAX_TICKS = 24000;
    private static final float ACCELERATION = 9F;
    private static final float BODY = 0.7F;

    /** What an actor does on each tick of the film */
    public static final class Track
    {
        public final float[] x;
        public final float[] y;
        public final float[] z;
        /** The body's yaw */
        public final float[] yaw;
        /** The head's yaw and pitch, which lead the body */
        public final float[] headYaw;
        public final float[] pitch;

        Track(int length)
        {
            this.x = new float[length];
            this.y = new float[length];
            this.z = new float[length];
            this.yaw = new float[length];
            this.headYaw = new float[length];
            this.pitch = new float[length];
        }

        public int length()
        {
            return this.x.length;
        }
    }

    private static final Map<Film, AiSimulation> CACHE = new WeakHashMap<>();

    private final long key;
    private final Map<String, Track> tracks = new HashMap<>();

    private AiSimulation(long key)
    {
        this.key = key;
    }

    /** The volume of a film: its form, and where the bottom middle of its box is */
    public record Volume(NavigationVolumeForm form, double x, double y, double z) {}

    public static Volume findVolume(Film film)
    {
        for (Replay replay : film == null ? List.<Replay>of() : film.replays.getList())
        {
            Form root = replay.form.get();
            NavigationVolumeForm[] found = new NavigationVolumeForm[1];

            FormTreeWalk.walk(root, (form, path, anchor) ->
            {
                if (found[0] == null && form instanceof NavigationVolumeForm volume)
                {
                    found[0] = volume;
                }

                return found[0] == null;
            });

            if (found[0] != null && replay.enabled.get())
            {
                double x = replay.keyframes.x.interpolate(0F, 0D);
                double y = replay.keyframes.y.interpolate(0F, 0D);
                double z = replay.keyframes.z.interpolate(0F, 0D);

                for (Form form = found[0]; form != null; form = form.getParentForm())
                {
                    Vector3f t = form.transform.get().translate;

                    x += t.x;
                    y += t.y;
                    z += t.z;
                }

                return new Volume(found[0], x, y, z);
            }
        }

        /* Or a volume placed as a model block in the world */
        for (mchorse.bbs_mod.blocks.entities.ModelBlockEntity block : new ArrayList<>(mchorse.bbs_mod.client.BBSRendering.capturedModelBlocks))
        {
            Form root = block.getProperties().getForm();
            NavigationVolumeForm[] found = new NavigationVolumeForm[1];

            FormTreeWalk.walk(root, (form, path, anchor) ->
            {
                if (found[0] == null && form instanceof NavigationVolumeForm volume)
                {
                    found[0] = volume;
                }

                return found[0] == null;
            });

            if (found[0] != null)
            {
                Vector3f t = block.getProperties().getTransform().translate;
                double x = block.getPos().getX() + 0.5D + t.x;
                double y = block.getPos().getY() + t.y;
                double z = block.getPos().getZ() + 0.5D + t.z;

                for (Form form = found[0]; form != null; form = form.getParentForm())
                {
                    Vector3f own = form.transform.get().translate;

                    x += own.x;
                    y += own.y;
                    z += own.z;
                }

                lastBlockVolume = new Volume(found[0], x, y, z);
                lastBlockVolumeTime = System.currentTimeMillis();

                return lastBlockVolume;
            }
        }

        /* The model blocks are gathered over each tick as they update, so one asked about early in a tick may not be in yet */
        if (lastBlockVolume != null && System.currentTimeMillis() - lastBlockVolumeTime < 3000L)
        {
            return lastBlockVolume;
        }

        return null;
    }

    private static Volume lastBlockVolume;
    private static long lastBlockVolumeTime;

    /** Where an actor starts: its first keyframe, or the middle of the volume when it has none */
    private static double[] start(Replay replay, Volume volume)
    {
        if (replay.keyframes.x.isEmpty() && replay.keyframes.z.isEmpty())
        {
            return new double[] {volume.x(), volume.y(), volume.z()};
        }

        return new double[] {replay.keyframes.x.interpolate(0F, 0D), replay.keyframes.y.interpolate(0F, 0D), replay.keyframes.z.interpolate(0F, 0D)};
    }

    /** The walk of an actor with AI, or null when it has none to make: no volume, no mesh, no AI */
    public static Track track(Film film, Replay replay)
    {
        if (film == null || replay == null || replay.form.get() == null || !replay.form.get().hasAi.get())
        {
            return null;
        }

        Volume volume = findVolume(film);

        if (volume == null)
        {
            return null;
        }

        NavGrid grid = NavGrids.get(volume.form(), volume.x(), volume.y(), volume.z());

        if (grid == null || grid.getNodes().isEmpty())
        {
            return null;
        }

        int ticks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, film.calculateDuration() + 20));
        List<Replay> actors = new ArrayList<>();
        long key = 17L * 31L + grid.getSignature();

        key = key * 31L + ticks;

        for (Replay other : film.replays.getList())
        {
            Form settings = other.form.get();

            if (settings != null && settings.hasAi.get() && other.enabled.get())
            {
                double[] at = start(other, volume);

                actors.add(other);
                key = key * 31L + other.getId().hashCode();
                key = key * 31L + Float.hashCode(settings.aiIdleMin.get());
                key = key * 31L + Float.hashCode(settings.aiIdleMax.get());
                key = key * 31L + Float.hashCode(settings.aiSpeed.get());
                key = key * 31L + settings.aiSeed.get();
                key = key * 31L + (settings.aiAvoidance.get() ? 1 : 0);
                key = key * 31L + (settings.orientToMovement.get() ? 1 : 0);
                key = key * 31L + (settings.useControllerDesiredRotation.get() ? 1 : 0);
                key = key * 31L + Float.hashCode(settings.rotationRate.get());
                key = key * 31L + Math.round(at[0] * 100D);
                key = key * 31L + Math.round(at[1] * 100D);
                key = key * 31L + Math.round(at[2] * 100D);
            }
        }

        AiSimulation cached = CACHE.get(film);

        if (cached == null || cached.key != key)
        {
            cached = simulate(actors, volume, grid, ticks, key);
            CACHE.put(film, cached);
        }

        return cached.tracks.get(replay.getId());
    }

    static final class Agent
    {
        /** Who it is, for telling two on one spot apart */
        String id = "";
        Replay replay;
        Form settings;
        Track track;
        Random random;
        double x;
        double y;
        double z;
        /** The body's yaw; the head leads it */
        float yaw;
        float head;
        float pitch;
        /* Looking about while it waits: how far off the body, how far up or down, and for how long */
        float lookOffset;
        float lookPitch;
        int lookTimer;
        float speed;
        List<NavGrid.Node> path;
        int next;
        int idle;
        double goalX;
        double goalZ;
        boolean moving;
    }

    private static AiSimulation simulate(List<Replay> actors, Volume volume, NavGrid grid, int ticks, long key)
    {
        AiSimulation result = new AiSimulation(key);
        List<Agent> agents = new ArrayList<>();

        for (Replay replay : actors)
        {
            Agent agent = new Agent();
            double[] at = start(replay, volume);
            NavGrid.Node node = grid.nearest(at[0], at[1], at[2]);

            if (node == null)
            {
                continue;
            }

            agent.replay = replay;
            agent.id = replay.getId();
            agent.settings = replay.form.get();
            agent.track = new Track(ticks);
            agent.random = new Random(replay.getId().hashCode() * 31L + agent.settings.aiSeed.get() * 7919L + 12345L);
            agent.x = node.centerX();
            agent.y = node.y;
            agent.z = node.centerZ();
            agent.yaw = replay.keyframes.yaw.isEmpty() ? 0F : (float) replay.keyframes.yaw.interpolate(0F, 0D).doubleValue();
            agent.head = agent.yaw;
            agent.idle = 1 + agent.random.nextInt(20);
            agents.add(agent);
            result.tracks.put(replay.getId(), agent.track);
        }

        for (int tick = 0; tick < ticks; tick++)
        {
            for (Agent agent : agents)
            {
                step(agent, agents, grid);

                agent.track.x[tick] = (float) agent.x;
                agent.track.y[tick] = (float) agent.y;
                agent.track.z[tick] = (float) agent.z;
                agent.track.yaw[tick] = agent.yaw;
                agent.track.headYaw[tick] = agent.head;
                agent.track.pitch[tick] = agent.pitch;
            }
        }

        return result;
    }

    static void step(Agent agent, List<Agent> agents, NavGrid grid)
    {
        Form replay = agent.settings;
        float wanted = 0F;
        double dirX = 0D;
        double dirZ = 0D;

        if (agent.moving && agent.path != null && agent.next < agent.path.size())
        {
            NavGrid.Node target = agent.path.get(agent.next);
            double dx = target.centerX() - agent.x;
            double dz = target.centerZ() - agent.z;
            double distance = Math.hypot(dx, dz);

            if (distance < 0.12D)
            {
                agent.next += 1;
                agent.y = agent.y + (target.y - agent.y) * 0.5D;
            }
            else
            {
                dirX = dx / distance;
                dirZ = dz / distance;
                wanted = replay.aiSpeed.get();

                /* Slow down for the last block of the way */
                if (agent.next == agent.path.size() - 1)
                {
                    wanted *= (float) Math.max(0.25D, Math.min(1D, distance));
                }
            }

            if (agent.next >= agent.path.size())
            {
                agent.moving = false;
                agent.path = null;
                float min = Math.min(replay.aiIdleMin.get(), replay.aiIdleMax.get());
                float max = Math.max(replay.aiIdleMin.get(), replay.aiIdleMax.get());

                agent.idle = Math.max(1, Math.round((min + agent.random.nextFloat() * (max - min)) * 20F));
            }
        }
        else if (agent.idle > 0)
        {
            agent.idle -= 1;
        }
        else
        {
            choose(agent, grid);
        }

        agent.speed += Math.max(-ACCELERATION * DT, Math.min(ACCELERATION * DT, wanted - agent.speed));

        double vx = dirX * agent.speed;
        double vz = dirZ * agent.speed;

        /* The push of the others, kept only where it leaves the actor on the mesh */
        double px = 0D;
        double pz = 0D;

        if (replay.aiAvoidance.get())
        {
            for (Agent other : agents)
            {
                if (other == agent)
                {
                    continue;
                }

                double dx = agent.x - other.x;
                double dz = agent.z - other.z;
                double distance = Math.hypot(dx, dz);

                if (distance < BODY * 1.4D && Math.abs(agent.y - other.y) < 1.5D)
                {
                    if (distance < 1.0E-4D)
                    {
                        /* On the very same spot: the one drawn first gives way to the left */
                        dx = agent.id.compareTo(other.id) < 0 ? 1D : -1D;
                        dz = 0D;
                        distance = 1D;
                    }

                    double push = (BODY * 1.4D - distance) / (BODY * 1.4D) * 2.2D;

                    px += dx / distance * push;
                    pz += dz / distance * push;
                }
            }
        }

        double nx = agent.x + (vx + px) * DT;
        double nz = agent.z + (vz + pz) * DT;
        double ground = grid.floorAt(nx, agent.y, nz);

        if (Double.isNaN(ground))
        {
            /* Off the mesh: the push is dropped, the walk alone is kept */
            nx = agent.x + vx * DT;
            nz = agent.z + vz * DT;
            ground = grid.floorAt(nx, agent.y, nz);
        }

        if (!Double.isNaN(ground))
        {
            agent.x = nx;
            agent.z = nz;
            agent.y += Math.max(-8D * DT, Math.min(8D * DT, ground - agent.y));
        }

        turn(agent, replay, vx, vz);
    }

    /**
     * Turning the way a Minecraft mob does: the head first, the body after it.
     *
     * <p>The head (its look control) turns quickly toward what it wants to see - where the actor is
     * heading, or where the controller wants it to look - and, while the actor waits, now and then to
     * something beside it. It never turns more than 75 degrees off the body. The body (its body control)
     * follows at the rotation rate: while walking it turns to the way it walks (or to where the controller
     * wants), and while standing it stays put until the head is more than 40 degrees off, then catches
     * up with it. So a turn starts with a look and ends with the shoulders, never all of it at once.</p>
     */
    private static void turn(Agent agent, Form settings, double vx, double vz)
    {
        double speed = Math.hypot(vx, vz);
        boolean walking = speed > 0.1D;
        float rate = settings.rotationRate.get() * DT;
        Float wanted = null;

        if (settings.useControllerDesiredRotation.get() && agent.moving)
        {
            wanted = (float) Math.toDegrees(Math.atan2(-(agent.goalX - agent.x), agent.goalZ - agent.z));
        }
        else if (settings.orientToMovement.get() && walking)
        {
            wanted = (float) Math.toDegrees(Math.atan2(-vx, vz));
        }

        /* The head: where it is going, or a look about while it waits */
        float headGoal;
        float pitchGoal = 0F;

        if (wanted != null)
        {
            headGoal = wanted;
            agent.lookTimer = 0;
        }
        else
        {
            if (--agent.lookTimer <= 0)
            {
                boolean look = agent.random.nextFloat() < 0.45F;

                agent.lookOffset = look ? (agent.random.nextFloat() * 2F - 1F) * 70F : 0F;
                agent.lookPitch = look ? (agent.random.nextFloat() * 2F - 1F) * 15F : 0F;
                agent.lookTimer = 30 + agent.random.nextInt(70);
            }

            headGoal = agent.yaw + agent.lookOffset;
            pitchGoal = agent.lookPitch;
        }

        float headRate = Math.max(rate * 2.5F, 9F);

        agent.head = wrap(agent.head + clamp(wrap(headGoal - agent.head), headRate));
        agent.pitch += clamp(pitchGoal - agent.pitch, headRate * 0.6F);

        /* The body */
        if (walking)
        {
            float bodyGoal = wanted != null ? wanted : agent.head;

            agent.yaw = wrap(agent.yaw + clamp(wrap(bodyGoal - agent.yaw), rate));
        }
        else if (Math.abs(wrap(agent.head - agent.yaw)) > 40F)
        {
            agent.yaw = wrap(agent.yaw + clamp(wrap(agent.head - agent.yaw), rate));
        }

        /* The neck has its limit: past it, the body is dragged round */
        float neck = wrap(agent.head - agent.yaw);

        if (Math.abs(neck) > 75F)
        {
            agent.yaw = wrap(agent.head - Math.signum(neck) * 75F);
        }
    }

    private static float clamp(float value, float most)
    {
        return Math.max(-most, Math.min(most, value));
    }

    /** Picks somewhere to go that can be reached, or waits a little and tries again */
    static void choose(Agent agent, NavGrid grid)
    {
        NavGrid.Node here = grid.nearest(agent.x, agent.y, agent.z);

        for (int attempt = 0; attempt < 6; attempt++)
        {
            NavGrid.Node goal = grid.random(agent.random);

            if (goal == null || goal == here || Math.hypot(goal.x - agent.x, goal.z - agent.z) < 2D)
            {
                continue;
            }

            List<NavGrid.Node> path = grid.path(here, goal);

            if (path != null && path.size() > 1)
            {
                agent.path = path;
                agent.next = 1;
                agent.moving = true;
                agent.goalX = goal.centerX();
                agent.goalZ = goal.centerZ();

                return;
            }
        }

        agent.idle = 20;
    }

    private static float wrap(float degrees)
    {
        degrees %= 360F;

        if (degrees >= 180F)
        {
            degrees -= 360F;
        }
        else if (degrees < -180F)
        {
            degrees += 360F;
        }

        return degrees;
    }
}
