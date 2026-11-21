package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.NavigationVolumeForm;
import mchorse.bbs_mod.physics.forms.FormTreeWalk;
import mchorse.bbs_mod.utils.clips.Clip;
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

    /** Whether any actor follows something, so that the walk depends on where that goes; and what that was when it was worked out */
    private boolean follows;
    private long signature;
    private long checked;

    private AiSimulation(long key)
    {
        this.key = key;
    }

    /** How often the places the followed things go are looked at again, to see whether the walk has to be made again, in milliseconds */
    private static final long RECHECK = 500L;

    /** How high a camera sits above the feet of someone standing where it is */
    private static final double EYE = 1.62D;

    /** Where something an actor follows is on a tick of the film: its feet, in out as x, y, z */
    @FunctionalInterface
    interface Target
    {
        void at(int tick, double[] out);
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

    /**
     * The mesh an AI actor of the film walks on: the navigation volume's, when there is one, joined
     * with what the navigation wand left in the scene. Null when there is nothing to walk on.
     */
    public static NavGrid sceneGrid(Film film)
    {
        return sceneGrid(film, findVolume(film));
    }

    private static NavGrid sceneGrid(Film film, Volume volume)
    {
        NavGrid grid = volume == null ? null : NavGrids.get(volume.form(), volume.x(), volume.y(), volume.z());

        return NavigationNetworks.scene(film, grid);
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
        NavGrid grid = sceneGrid(film, volume);

        if (grid == null || grid.getNodes().isEmpty())
        {
            return null;
        }

        /* Without a volume the actors that have no place of their own start on the first floor of the mesh */
        if (volume == null)
        {
            NavGrid.Node first = grid.getNodes().get(0);

            volume = new Volume(null, first.centerX(), first.y, first.centerZ());
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
                key = key * 31L + other.aiFollowTarget.get().hashCode();
                key = key * 31L + Float.hashCode(other.aiFollowDistance.get());
                key = key * 31L + (settings.orientToMovement.get() ? 1 : 0);
                key = key * 31L + (settings.useControllerDesiredRotation.get() ? 1 : 0);
                key = key * 31L + Float.hashCode(settings.rotationRate.get());
                key = key * 31L + Math.round(at[0] * 100D);
                key = key * 31L + Math.round(at[1] * 100D);
                key = key * 31L + Math.round(at[2] * 100D);
            }
        }

        AiSimulation cached = CACHE.get(film);
        long now = System.currentTimeMillis();

        if (cached == null || cached.key != key)
        {
            cached = simulate(film, actors, volume, grid, ticks, key, targetSignature(film, actors));
            cached.checked = now;
            CACHE.put(film, cached);
        }
        else if (cached.follows && now - cached.checked > RECHECK)
        {
            /* What is followed may have been moved in the timeline since: the walk is made again when it has */
            long signature = targetSignature(film, actors);

            cached.checked = now;

            if (signature != cached.signature)
            {
                cached = simulate(film, actors, volume, grid, ticks, key, signature);
                cached.checked = now;
                CACHE.put(film, cached);
            }
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
        int lookTimer = 60;
        int lookHold;
        float speed;
        List<NavGrid.Node> path;
        int next;
        int idle;
        double goalX;
        double goalZ;
        boolean moving;

        /* Following something: what, where it was last seen, whether it stands near it looking at it */
        Target target;
        boolean watching;
        double watchX;
        double watchY;
        double watchZ;
        int followTimer;
        int tick;

        /* What it knows and is doing, for the decisions of a walk of its own (see AiDecisionEngine) */
        final AiBlackboard bb = new AiBlackboard();

        /** How far above the floor a hop has it */
        double hop;

        /** Whether its path is one it is making up a node at a time, so that arriving at the end is not the end */
        boolean wandering;
    }

    /** The camera of the film on every tick: x, y, z of where it is, or null when it has no clips */
    private static double[][] cameraTrack(Film film, int ticks)
    {
        double[][] out = new double[3][ticks + 1];
        Position position = new Position();
        CameraClipContext context = new CameraClipContext();

        context.clips = film.camera;

        for (int t = 0; t <= ticks; t++)
        {
            cameraAt(context, position, t);

            out[0][t] = position.point.x;
            out[1][t] = position.point.y;
            out[2][t] = position.point.z;
        }

        return out;
    }

    private static void cameraAt(CameraClipContext context, Position position, int tick)
    {
        context.clipData.clear();
        context.setup(tick, 0F);

        for (Clip clip : context.clips.getClips(tick))
        {
            context.apply(clip, position);
        }

        context.currentLayer = 0;
    }

    private static boolean isCamera(String reference)
    {
        return reference.equalsIgnoreCase("camera") || reference.equalsIgnoreCase("kamera");
    }

    /** The actor of the film a reference names, by its id or its name, other than the follower itself */
    private static Replay findActor(Film film, Replay self, String reference)
    {
        for (Replay replay : film.replays.getList())
        {
            if (replay != self && (replay.getId().equals(reference) || replay.getName().equalsIgnoreCase(reference)))
            {
                return replay;
            }
        }

        return null;
    }

    /**
     * A number that changes when the places followed change: the camera and the actors that are
     * followed, a few places along every ten ticks. Zero when nothing follows anything.
     */
    private static long targetSignature(Film film, List<Replay> actors)
    {
        long hash = 7L;
        CameraClipContext context = null;
        Position position = new Position();
        int ticks = film.calculateDuration() + 20;

        for (Replay actor : actors)
        {
            String reference = actor.aiFollowTarget.get().trim();

            if (reference.isEmpty())
            {
                continue;
            }

            hash = hash * 31L + reference.hashCode();

            if (isCamera(reference))
            {
                if (context == null)
                {
                    context = new CameraClipContext();
                    context.clips = film.camera;
                }

                for (int t = 0; t <= ticks; t += 10)
                {
                    cameraAt(context, position, t);

                    hash = hash * 31L + Math.round(position.point.x * 20D);
                    hash = hash * 31L + Math.round(position.point.z * 20D);
                }
            }
            else
            {
                Replay target = findActor(film, actor, reference);

                if (target == null)
                {
                    continue;
                }

                for (int t = 0; t <= ticks; t += 10)
                {
                    hash = hash * 31L + Math.round(target.keyframes.x.interpolate(t, 0D) * 20D);
                    hash = hash * 31L + Math.round(target.keyframes.z.interpolate(t, 0D) * 20D);
                }
            }
        }

        return hash;
    }

    private static AiSimulation simulate(Film film, List<Replay> actors, Volume volume, NavGrid grid, int ticks, long key, long signature)
    {
        AiSimulation result = new AiSimulation(key);

        result.signature = signature;
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
            /* On its way within the first few ticks */
            agent.idle = 1 + agent.random.nextInt(4);
            agents.add(agent);
            result.tracks.put(replay.getId(), agent.track);
        }

        /* What each follows: the camera, an actor with AI that is walked along with it, or an actor's keyframes */
        double[][] camera = null;

        for (Agent agent : agents)
        {
            String reference = agent.replay == null ? "" : agent.replay.aiFollowTarget.get().trim();

            if (reference.isEmpty())
            {
                continue;
            }

            if (isCamera(reference))
            {
                if (camera == null)
                {
                    camera = cameraTrack(film, ticks);
                }

                double[][] points = camera;

                agent.target = (tick, out) ->
                {
                    int i = Math.max(0, Math.min(ticks, tick));

                    out[0] = points[0][i];
                    out[1] = points[1][i] - EYE;
                    out[2] = points[2][i];
                };
                result.follows = true;

                continue;
            }

            Replay target = findActor(film, agent.replay, reference);

            if (target == null)
            {
                continue;
            }

            Agent walking = null;

            for (Agent other : agents)
            {
                if (other.replay == target)
                {
                    walking = other;
                }
            }

            if (walking != null)
            {
                Agent other = walking;

                agent.target = (tick, out) ->
                {
                    out[0] = other.x;
                    out[1] = other.y;
                    out[2] = other.z;
                };
            }
            else
            {
                agent.target = (tick, out) ->
                {
                    out[0] = target.keyframes.x.interpolate(tick, 0D);
                    out[1] = target.keyframes.y.interpolate(tick, 0D);
                    out[2] = target.keyframes.z.interpolate(tick, 0D);
                };
            }

            result.follows = true;
        }

        for (int tick = 0; tick < ticks; tick++)
        {
            for (Agent agent : agents)
            {
                agent.tick = tick;

                step(agent, agents, grid);

                agent.track.x[tick] = (float) agent.x;
                agent.track.y[tick] = (float) (agent.y + agent.hop);
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

        AiDecisionEngine.tick(agent);

        if (agent.target != null)
        {
            plan(agent, grid);
        }

        if (agent.moving && agent.path != null && agent.next < agent.path.size())
        {
            NavGrid.Node target = agent.path.get(agent.next);
            double dx = target.centerX() - agent.x;
            double dz = target.centerZ() - agent.z;
            double distance = Math.hypot(dx, dz);

            if (distance < 0.12D)
            {
                agent.next += 1;
                AiDecisionEngine.reached(agent, target);
                agent.y = agent.y + (target.y - agent.y) * 0.5D;

                /* At the end of a path it is making up as it goes, it decides what next: stop, or another node on */
                if (agent.wandering && agent.next >= agent.path.size() && agent.target == null)
                {
                    AiDecisionEngine.arrive(agent, grid, target);
                }
            }
            else
            {
                dirX = dx / distance;
                dirZ = dz / distance;
                wanted = replay.aiSpeed.get() * AiDecisionEngine.speedFactor(agent);

                /* Slow down for the last block of the way: not on a way it is making up as it goes */
                if (agent.next == agent.path.size() - 1 && !agent.wandering)
                {
                    wanted *= (float) Math.max(0.25D, Math.min(1D, distance));
                }
            }

            if (agent.path != null && agent.next >= agent.path.size())
            {
                agent.moving = false;
                agent.wandering = false;
                agent.path = null;
                float min = Math.min(replay.aiIdleMin.get(), replay.aiIdleMax.get());
                float max = Math.max(replay.aiIdleMin.get(), replay.aiIdleMax.get());

                agent.idle = Math.max(1, Math.round((min + agent.random.nextFloat() * (max - min)) * 20F));
            }
        }
        else if (agent.target != null)
        {
            /* Following and standing: it is near what it follows and has nothing else to do but watch it */
        }
        else if (agent.idle > 0)
        {
            agent.idle -= 1;
        }
        else
        {
            AiDecisionEngine.choose(agent, grid);
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

    /** How far the head turns in a tick at most, as a mob's look control: yaw and pitch */
    private static final float HEAD_YAW_SPEED = 10F;
    private static final float HEAD_PITCH_SPEED = 6F;
    /** How far the head may be off the body before the body has to come round, standing */
    private static final float BODY_SLACK = 45F;
    /** How far the head may be off the body at all */
    private static final float NECK = 75F;

    /**
     * Turning the way a Minecraft mob does - its look control turning the head, its body control
     * bringing the body after it.
     *
     * <p>The angles are never wrapped into -180..180 here: each one moves by the shortest way round to
     * its goal (the wrapped difference), at most so far a tick, and is otherwise left to run on. A head
     * at 179 and a body at -179 are then 179 and 181, two degrees apart as they really are, and nothing
     * downstream - the blend between two ticks, the head bone turned by head minus body - ever takes the
     * long way round.</p>
     *
     * <ul>
     * <li><b>The head</b> looks where it is going while it walks: the next point of its path, at eye
     * height, or where the controller wants it to look. Standing, it looks ahead, and every 50 to 120
     * ticks glances for 20 to 40 ticks at something up to 45 degrees aside and 15 up or down, then
     * looks ahead again.</li>
     * <li><b>The body</b> turns to the way it walks (or to the controller's wish) at the rotation rate
     * while walking. Standing, it stays put while the head is within 45 degrees of it, and comes round
     * after the head at the rotation rate once it is not.</li>
     * <li><b>The neck</b> never turns the head more than 75 degrees off the body.</li>
     * </ul>
     */
    private static void turn(Agent agent, Form settings, double vx, double vz)
    {
        boolean walking = Math.hypot(vx, vz) > 0.1D;
        float rate = Math.max(settings.rotationRate.get() * DT, 0.5F);
        Float wanted = null;

        if (settings.useControllerDesiredRotation.get() && agent.moving)
        {
            wanted = yawTo(agent.goalX - agent.x, agent.goalZ - agent.z);
        }
        else if (settings.orientToMovement.get() && walking)
        {
            wanted = yawTo(vx, vz);
        }

        float headGoal;
        float pitchGoal = 0F;

        if (agent.moving && agent.path != null && agent.next < agent.path.size())
        {
            /* Where it is going: the next point of the way, at eye height */
            NavGrid.Node point = agent.path.get(agent.next);
            double dx = point.centerX() - agent.x;
            double dz = point.centerZ() - agent.z;
            double flat = Math.hypot(dx, dz);

            headGoal = wanted != null ? wanted : (flat > 0.05D ? yawTo(dx, dz) : agent.head);
            pitchGoal = flat > 0.05D ? (float) Math.max(-30D, Math.min(30D, -Math.toDegrees(Math.atan2(point.y - agent.y, flat)))) : 0F;
            agent.lookTimer = 50 + agent.random.nextInt(71);
            agent.lookHold = 0;
        }
        else if (agent.target != null && agent.watching)
        {
            /* Standing near what it follows: looking at it */
            double dx = agent.watchX - agent.x;
            double dz = agent.watchZ - agent.z;
            double flat = Math.hypot(dx, dz);

            headGoal = flat > 0.05D ? yawTo(dx, dz) : agent.head;
            pitchGoal = flat > 0.05D ? (float) Math.max(-30D, Math.min(30D, -Math.toDegrees(Math.atan2(agent.watchY - agent.y, flat)))) : 0F;
            agent.lookHold = 0;
            agent.lookTimer = 50 + agent.random.nextInt(71);
        }
        else if (agent.bb.activeAction == AiBlackboard.Action.IDLE_LOOK || agent.bb.activeAction == AiBlackboard.Action.HESITATE_TURN_BACK)
        {
            /* A pause of its own accord: looking where the decision put its head */
            headGoal = agent.yaw + agent.bb.lookYawOffset;
            pitchGoal = agent.bb.lookPitchOffset;
            agent.lookHold = 0;
        }
        else
        {
            /* Standing: ahead, with a glance aside now and then */
            if (agent.lookHold > 0)
            {
                agent.lookHold -= 1;

                if (agent.lookHold == 0)
                {
                    agent.lookOffset = 0F;
                    agent.lookPitch = 0F;
                    agent.lookTimer = 50 + agent.random.nextInt(71);
                }
            }
            else if (--agent.lookTimer <= 0)
            {
                agent.lookOffset = (agent.random.nextFloat() * 2F - 1F) * 45F;
                agent.lookPitch = (agent.random.nextFloat() * 2F - 1F) * 15F;
                agent.lookHold = 20 + agent.random.nextInt(21);
            }

            headGoal = (wanted != null ? wanted : agent.yaw) + agent.lookOffset;
            pitchGoal = agent.lookPitch;
        }

        /* The head, by the short way round, no faster than a mob's */
        agent.head += clamp(wrapDegrees(headGoal - agent.head), Math.max(HEAD_YAW_SPEED, rate * 1.5F));
        agent.pitch += clamp(pitchGoal - agent.pitch, HEAD_PITCH_SPEED);

        /* The body */
        if (walking)
        {
            float bodyGoal = wanted != null ? wanted : yawTo(vx, vz);

            agent.yaw += clamp(wrapDegrees(bodyGoal - agent.yaw), rate);
        }
        else
        {
            float off = wrapDegrees(agent.head - agent.yaw);

            if (Math.abs(off) > BODY_SLACK)
            {
                agent.yaw += clamp(off - Math.signum(off) * BODY_SLACK * 0.5F, rate);
            }
        }

        /* The neck: never more than so far off the body; past it the body is brought round */
        float neck = wrapDegrees(agent.head - agent.yaw);

        if (Math.abs(neck) > NECK)
        {
            agent.yaw += neck - Math.signum(neck) * NECK;
        }
    }

    /** The game's yaw of a direction: 0 faces +Z and turns toward -X */
    private static float yawTo(double dx, double dz)
    {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The shortest turn between two yaws, in -180..180 */
    private static float wrapDegrees(float degrees)
    {
        return degrees - 360F * (float) Math.floor((degrees + 180F) / 360F);
    }

    private static float clamp(float value, float most)
    {
        return Math.max(-most, Math.min(most, value));
    }

    /**
     * What an actor that follows does about it: sees where the followed thing is, stands and watches
     * when it is within the follow distance, and otherwise finds its way to it on the mesh (A*), every
     * few ticks, to the last node that is still the follow distance away. Whatever path it was on
     * is replaced; the walking itself is the same walk of every actor with AI.
     */
    static void plan(Agent agent, NavGrid grid)
    {
        double[] at = new double[3];

        agent.target.at(agent.tick, at);

        agent.watchX = at[0];
        agent.watchY = at[1];
        agent.watchZ = at[2];

        double min = Math.max(0D, agent.replay == null ? 1.2D : agent.replay.aiFollowDistance.get());

        if (Math.hypot(at[0] - agent.x, at[2] - agent.z) <= min + 0.25D)
        {
            agent.moving = false;
            agent.path = null;
            agent.watching = true;

            return;
        }

        agent.watching = false;

        if (agent.moving && agent.path != null && --agent.followTimer > 0)
        {
            return;
        }

        agent.followTimer = 5;

        NavGrid.Node here = grid.nearest(agent.x, agent.y, agent.z);
        NavGrid.Node goal = grid.nearest(at[0], at[1], at[2]);
        List<NavGrid.Node> path = grid.path(here, goal);

        /* Stop short: the nodes that are nearer than the follow distance are not walked to */
        while (path != null && path.size() > 1 && Math.hypot(path.get(path.size() - 1).centerX() - at[0], path.get(path.size() - 1).centerZ() - at[2]) < Math.max(min, 0.6D))
        {
            path.remove(path.size() - 1);
        }

        if (path == null || path.size() < 2)
        {
            agent.moving = false;
            agent.path = null;
            agent.watching = true;

            return;
        }

        agent.path = path;
        agent.next = 1;
        agent.moving = true;
        agent.wandering = false;
        agent.goalX = at[0];
        agent.goalZ = at[2];

        /* From afar it runs, near it walks */
        agent.bb.running = Math.hypot(at[0] - agent.x, at[2] - agent.z) > 8D;
        agent.bb.runTimer = 40;
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
