package mchorse.bbs_mod.physics.client.scene;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.physics.engine.JoltRelease;
import mchorse.bbs_mod.physics.engine.PhysicsLayers;
import mchorse.bbs_mod.physics.engine.PhysicsWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * The world's own living things inside a film's physics: players and mobs near the actors, each a
 * kinematic box that follows them, so a ragdoll is knocked aside by whoever walks into it and
 * someone walking past a character the animation still holds can shove it off balance.
 *
 * <p><b>Observed, then kept.</b> Where they stood is read off the world once per tick of the film
 * and kept ({@link #observed}): re-simulating a tick, after a scrub or for a video, uses what was
 * observed for it, never the world as it happens to be later, so the recording stays the same
 * however often it is made. Only a film played forwards in real time looks at the world anew:
 * the first time someone is near, the recording from that tick on is taken back
 * ({@link #liveTick}), and while anyone stays near, the scene records no further than the cursor,
 * so every tick is made with the world as it is at that moment. A scrub or a video ends that, and
 * reads what was kept.</p>
 *
 * <p><b>Fixed in number.</b> {@link #SLOTS} bodies, made with the scene and never removed, parked
 * far under the world while unused: the set of bodies never changes, so the scene's checkpoints
 * stay valid. Each slot's size follows its entity in tenths of a block, so a change of shape is
 * rare, and what a tick does to a slot depends on that tick's and the previous tick's observation
 * alone, which is what makes a tick re-simulated after a checkpoint come out the same.</p>
 *
 * <p><b>Pushes.</b> A body the animation fully holds is kinematic, and two kinematic bodies pass
 * through each other: so an entity moving into an actor's ragdoll also gives it a push the size
 * of its own speed, the way an impulse clip does. With active physics on, that push takes the
 * character from the animation and staggers it; without, it only moves what physics already owns.</p>
 */
final class EntityProxies
{
    /** How many entities a scene follows at once: the nearest to the actors, by entity id. */
    static final int SLOTS = 8;

    /** Floats per entity in an observation: x, y and z of its middle, half width, half height. */
    private static final int STRIDE = 5;

    /** How far around the actors entities are looked for, in blocks. */
    private static final double REACH = 6D;

    /** How long, in ticks, the scene stays live after the last time anyone was near. */
    private static final int LIVE_HOLD = 40;

    /** An entity that moved further than this in a tick was not walking: it is put there, not dragged. */
    private static final float TELEPORT = 4F;

    /** Slower than this, in blocks per second, an entity does not push anything. */
    private static final float PUSH_SPEED = 0.5F;
    private static final float MAX_PUSH = 10F;

    private static final float[] NONE = new float[0];
    private static final float PARK_Y = -10000F;

    private final int[] ids = new int[SLOTS];
    private final float[] shapeW = new float[SLOTS];
    private final float[] shapeH = new float[SLOTS];
    private final TreeMap<Integer, float[]> observed = new TreeMap<>();
    private boolean live;
    private int lastSeen = Integer.MIN_VALUE / 2;

    private final RVec3 position = new RVec3();
    private final Quat identity = new Quat();
    private final Vec3 zero = new Vec3(0F, 0F, 0F);
    private final Vector3f direction = new Vector3f();

    EntityProxies(PhysicsWorld world)
    {
        BodyInterface bodies = world.getBodies();

        for (int i = 0; i < SLOTS; i++)
        {
            BoxShape shape = new BoxShape(new Vec3(0.3F, 0.9F, 0.3F), 0.05F);
            BodyCreationSettings settings = new BodyCreationSettings(shape, new RVec3(i * 4D, PARK_Y, 0D), new Quat(), EMotionType.Kinematic, PhysicsLayers.MOVING);

            this.ids[i] = bodies.createAndAddBody(settings, EActivation.DontActivate);
            this.shapeW[i] = 0.3F;
            this.shapeH[i] = 0.9F;
            JoltRelease.release(settings, shape);
        }
    }

    boolean isLive()
    {
        return this.live;
    }

    /**
     * Once a film tick, from the scene's clock: whether the film is being played forwards in real
     * time with someone near, which makes it live. Returns the tick the recording is to be taken
     * back from when it just became live, or -1.
     */
    int liveTick(FilmScene scene, int tick, boolean advanced, boolean jumped)
    {
        /* A scrub or a video reads what was kept; a paused film asking for the same tick changes nothing */
        if (jumped)
        {
            this.live = false;

            return -1;
        }

        if (!advanced)
        {
            return -1;
        }

        boolean anyone = this.observe(scene).length > 0;

        if (anyone)
        {
            this.lastSeen = tick;
        }

        if (!this.live && anyone)
        {
            this.live = true;
            this.observed.tailMap(tick, true).clear();

            return tick;
        }

        if (this.live && tick - this.lastSeen > LIVE_HOLD)
        {
            this.live = false;
        }

        return -1;
    }

    /**
     * Before the drives of a tick: every slot stands where its entity was observed on this tick,
     * moved there kinematically from the last, put there outright when it just appeared or jumped,
     * parked when its entity is gone.
     */
    void pose(FilmScene scene, BodyInterface bodies, int tick, float deltaTime)
    {
        float[] now = this.observation(scene, tick);
        float[] before = this.observed.getOrDefault(tick - 1, NONE);

        for (int i = 0; i < SLOTS; i++)
        {
            int at = i * STRIDE;
            int id = this.ids[i];

            if (at >= now.length)
            {
                if (at < before.length)
                {
                    this.position.set(i * 4D, PARK_Y, 0D);
                    bodies.setPositionAndRotation(id, this.position, this.identity, EActivation.DontActivate);
                    bodies.setLinearVelocity(id, this.zero);
                }

                continue;
            }

            float w = now[at + 3];
            float h = now[at + 4];

            if (w != this.shapeW[i] || h != this.shapeH[i])
            {
                BoxShape shape = new BoxShape(new Vec3(w, h, w), Math.min(0.05F, Math.min(w, h)));

                bodies.setShape(id, shape, false, EActivation.Activate);
                JoltRelease.release(shape);
                this.shapeW[i] = w;
                this.shapeH[i] = h;
            }

            this.position.set(now[at], now[at + 1], now[at + 2]);

            boolean appeared = at >= before.length;
            boolean jumped = !appeared && distance(now, before, at) > TELEPORT;

            if (appeared || jumped)
            {
                bodies.setPositionAndRotation(id, this.position, this.identity, EActivation.Activate);
                bodies.setLinearVelocity(id, this.zero);
            }
            else
            {
                bodies.moveKinematic(id, this.position, this.identity, deltaTime);
            }
        }
    }

    /**
     * After the drives of a tick: an entity moving into an actor's ragdoll pushes it, along the way
     * it moves and as fast. Only for ragdolls within its reach, so a crowd far off makes nothing.
     */
    void push(FilmScene scene, int tick)
    {
        float[] now = this.observed.getOrDefault(tick, NONE);
        float[] before = this.observed.getOrDefault(tick - 1, NONE);
        PhysicsWorld world = scene.getWorld();

        for (int at = 0; at + STRIDE <= now.length && at + STRIDE <= before.length; at += STRIDE)
        {
            this.direction.set(now[at] - before[at], now[at + 1] - before[at + 1], now[at + 2] - before[at + 2]);

            float speed = this.direction.length() / PhysicsWorld.TICK;

            if (speed < PUSH_SPEED || this.direction.length() > TELEPORT)
            {
                continue;
            }

            float reach = now[at + 3] + 1F;
            SceneImpulse push = null;

            for (SceneActor actor : scene.getActors())
            {
                for (SceneRig rig : actor.getRigs())
                {
                    if (!(rig instanceof RagdollRig ragdoll) || ragdoll.impactDistance(world, now[at], now[at + 1], now[at + 2]) > reach * reach)
                    {
                        continue;
                    }

                    if (push == null)
                    {
                        push = SceneImpulse.of(now[at], now[at + 1], now[at + 2], reach, Math.min(speed, MAX_PUSH), new Vector3f(this.direction));
                    }

                    if (push != null)
                    {
                        ragdoll.impulse(world, push);
                    }
                }
            }
        }
    }

    /** The observation of a tick: kept, or made now — from the world while live, empty otherwise — and kept. */
    private float[] observation(FilmScene scene, int tick)
    {
        float[] kept = this.observed.get(tick);

        if (kept == null)
        {
            kept = this.live ? this.observe(scene) : NONE;
            this.observed.put(tick, kept);
        }

        return kept;
    }

    /** The living things near the scene's actors now, in the scene's coordinates, by entity id. */
    private float[] observe(FilmScene scene)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null || scene.getActors().isEmpty())
        {
            return NONE;
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (SceneActor actor : scene.getActors())
        {
            IEntity entity = actor.getEntity();

            if (entity == null)
            {
                continue;
            }

            minX = Math.min(minX, entity.getX());
            minY = Math.min(minY, entity.getY());
            minZ = Math.min(minZ, entity.getZ());
            maxX = Math.max(maxX, entity.getX());
            maxY = Math.max(maxY, entity.getY());
            maxZ = Math.max(maxZ, entity.getZ());
        }

        if (minX == Double.MAX_VALUE)
        {
            return NONE;
        }

        boolean editor = scene.isEditor();
        Box box = new Box(minX - REACH, minY - REACH, minZ - REACH, maxX + REACH, maxY + REACH + 2D, maxZ + REACH);
        List<Entity> near = world.getOtherEntities(null, box, (e) -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
            && !(e instanceof ActorEntity) && !(editor && e == mc.player));

        if (near.isEmpty())
        {
            return NONE;
        }

        near.sort(Comparator.comparingInt(Entity::getId));

        int count = Math.min(SLOTS, near.size());
        float[] out = new float[count * STRIDE];

        for (int i = 0; i < count; i++)
        {
            Entity entity = near.get(i);
            float halfW = Math.max(0.1F, Math.round(entity.getWidth() * 5F) / 10F);
            float halfH = Math.max(0.1F, Math.round(entity.getHeight() * 5F) / 10F);
            int at = i * STRIDE;

            out[at] = (float) (entity.getX() - scene.getOriginX());
            out[at + 1] = (float) (entity.getY() + halfH - scene.getOriginY());
            out[at + 2] = (float) (entity.getZ() - scene.getOriginZ());
            out[at + 3] = halfW;
            out[at + 4] = halfH;
        }

        return out;
    }

    private static float distance(float[] a, float[] b, int at)
    {
        float dx = a[at] - b[at];
        float dy = a[at + 1] - b[at + 1];
        float dz = a[at + 2] - b[at + 2];

        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Everything observed is let go of: a scene starting from nothing again. */
    void forget()
    {
        this.observed.clear();
        this.live = false;
    }
}
