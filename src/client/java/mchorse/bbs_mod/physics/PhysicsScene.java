package mchorse.bbs_mod.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.JobSystem;
import com.github.stephengold.joltjni.JobSystemSingleThreaded;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.SphereShape;
import com.github.stephengold.joltjni.StateRecorderImpl;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.TempAllocator;
import com.github.stephengold.joltjni.TempAllocatorImplWithMallocFallback;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAllowedDofs;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.readonly.ConstShape;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One Jolt world for a film: a body for every replay that has physics switched on, the blocks around them as
 * static geometry, and the whole simulation cached tick by tick.
 *
 * <p>The cache is what makes scrubbing honest. Every tick the world's state is saved, and the pose of every
 * body is kept beside it, so any tick already simulated is a lookup and the same tick always gives the same
 * answer, forwards or backwards. When a key or a setting changes, the first tick it touches is found by
 * comparing the hash each tick was simulated with, the saved state just before it is restored, and the
 * simulation carries on from there instead of starting over.</p>
 *
 * <p>Everything here runs on the render thread, one tick at a time, so a scrub to a far tick costs the
 * steps between and nothing else.</p>
 */
final class PhysicsScene implements AutoCloseable
{
    static final float DELTA = 1F / 20F;
    static final int STEPS = 3;
    static final int MAX_TICKS = 12000;

    private static final int LAYER_STATIC = 0;
    private static final int LAYER_MOVING = 1;
    private static final int LAYER_GHOST = 2;
    private static final int LAYERS = 3;

    private static final float MAX_PULL_SPEED = 60F;
    private static final float MAX_PULL_SPIN = 20F;
    private static final int MAX_BLOCKS = 8000;

    /** One simulated replay */
    static final class Rig
    {
        final Replay replay;
        final AutoCollisionBuilder.Box box;
        final Body body;
        final float mass;
        boolean kinematic;
        Body anchor;
        TwoBodyConstraint rope;

        Rig(Replay replay, AutoCollisionBuilder.Box box, Body body, float mass)
        {
            this.replay = replay;
            this.box = box;
            this.body = body;
            this.mass = mass;
        }
    }

    final Film film;
    final String key;
    final List<Rig> rigs = new ArrayList<>();

    private final ObjectLayerPairFilterTable pairs;
    private final BroadPhaseLayerInterfaceTable layers;
    private final ObjectVsBroadPhaseLayerFilterTable layerFilter;
    private final PhysicsSystem system;
    private final BodyInterface bodies;
    private final TempAllocator temp;
    private final JobSystem jobs;

    /* The cache, one entry per simulated tick; entry 0 is the world as it was set up */
    private final List<byte[]> states = new ArrayList<>();
    private final List<float[][]> poses = new ArrayList<>();
    private final List<boolean[]> kinematics = new ArrayList<>();
    private final List<Integer> hashes = new ArrayList<>();

    private final RVec3 scratchPosition = new RVec3();
    private final Quat scratchRotation = new Quat();
    private boolean closed;

    PhysicsScene(Film film, String key, List<Replay> replays, World world)
    {
        this.film = film;
        this.key = key;

        this.pairs = new ObjectLayerPairFilterTable(LAYERS);
        this.pairs.enableCollision(LAYER_STATIC, LAYER_MOVING);
        this.pairs.enableCollision(LAYER_MOVING, LAYER_MOVING);

        this.layers = new BroadPhaseLayerInterfaceTable(LAYERS, 2);
        this.layers.mapObjectToBroadPhaseLayer(LAYER_STATIC, 0);
        this.layers.mapObjectToBroadPhaseLayer(LAYER_MOVING, 1);
        this.layers.mapObjectToBroadPhaseLayer(LAYER_GHOST, 1);

        this.layerFilter = new ObjectVsBroadPhaseLayerFilterTable(this.layers, LAYERS, this.pairs, 2);

        this.system = new PhysicsSystem();
        this.system.init(1024, 0, 2048, 1024, this.layers, this.layerFilter, this.pairs);
        this.system.setGravity(0F, -9.81F, 0F);
        this.bodies = this.system.getBodyInterface();
        this.temp = new TempAllocatorImplWithMallocFallback(4 * 1024 * 1024);
        this.jobs = new JobSystemSingleThreaded(1024);

        float[] region = new float[6];

        for (Replay replay : replays)
        {
            this.rigs.add(this.build(replay));
        }

        this.addGround(world, region);
        this.addRopes();

        this.system.optimizeBroadPhase();
        this.record(0, new byte[0]);
        this.states.set(0, this.saveState());
    }

    /* Building */

    private Rig build(Replay replay)
    {
        ReplayPhysics physics = replay.physics;
        AutoCollisionBuilder.Box box = AutoCollisionBuilder.build(replay.form.get());
        PhysicsMaterial material = PhysicsMaterial.byId(physics.material.get());

        float mass = physics.mass.get() > 0F ? physics.mass.get() : material.density * box.volume();

        mass = Math.max(0.05F, Math.min(5000F, mass));

        float[] start = this.target(replay, 0F, box);
        BoxShape shape = new BoxShape(new Vec3(box.halfX(), box.halfY(), box.halfZ()), Math.min(0.05F, Math.min(box.halfX(), Math.min(box.halfY(), box.halfZ())) * 0.9F));
        BodyCreationSettings settings = new BodyCreationSettings(shape, new RVec3(start[0], start[1], start[2]),
            new Quat(start[3], start[4], start[5], start[6]), EMotionType.Dynamic, LAYER_MOVING);

        settings.setFriction(physics.friction.get());
        settings.setRestitution(physics.restitution.get());
        settings.setGravityFactor(physics.balloon.get() ? -Math.max(0.01F, physics.lift.get()) : 1F);
        settings.setLinearDamping(physics.balloon.get() ? 0.8F : 0.02F);
        settings.setAngularDamping(physics.balloon.get() ? 0.8F : 0.05F);
        settings.setMotionQuality(EMotionQuality.LinearCast);

        int dofs = EAllowedDofs.All;

        if (!physics.ragdoll.get())
        {
            /* Upright: it falls and slides, and only turns about its own vertical axis */
            dofs &= ~EAllowedDofs.RotationX;
            dofs &= ~EAllowedDofs.RotationZ;
        }

        settings.setAllowedDofs(dofs);

        MassProperties properties = new MassProperties().setMass(mass);

        settings.setMassPropertiesOverride(properties);
        settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);

        Body body = this.bodies.createBody(settings);

        this.bodies.addBody(body.getId(), EActivation.Activate);

        return new Rig(replay, box, body, mass);
    }

    /** The blocks around the replays as one static body, and a floor under them when there are none */
    private void addGround(World world, float[] region)
    {
        if (this.rigs.isEmpty())
        {
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        int duration = Math.max(20, this.film.camera.calculateDuration());

        for (Rig rig : this.rigs)
        {
            for (int tick = 0; tick <= duration; tick += 4)
            {
                float[] pose = this.target(rig.replay, tick, rig.box);

                minX = Math.min(minX, pose[0]);
                minY = Math.min(minY, pose[1]);
                minZ = Math.min(minZ, pose[2]);
                maxX = Math.max(maxX, pose[0]);
                maxY = Math.max(maxY, pose[1]);
                maxZ = Math.max(maxZ, pose[2]);
            }
        }

        int x0 = (int) Math.floor(minX) - 5;
        int x1 = (int) Math.floor(maxX) + 5;
        int y0 = (int) Math.floor(minY) - 4;
        int y1 = (int) Math.floor(maxY) + 8;
        int z0 = (int) Math.floor(minZ) - 5;
        int z1 = (int) Math.floor(maxZ) + 5;

        /* A film that roams far is not given the whole stretch: the box is held to a size Jolt builds in a blink */
        x1 = Math.min(x1, x0 + 64);
        z1 = Math.min(z1, z0 + 64);
        y1 = Math.min(y1, y0 + 40);

        StaticCompoundShapeSettings compound = new StaticCompoundShapeSettings();
        int count = 0;

        if (world != null)
        {
            BlockPos.Mutable pos = new BlockPos.Mutable();

            for (int x = x0; x <= x1 && count < MAX_BLOCKS; x++)
            {
                for (int y = y0; y <= y1 && count < MAX_BLOCKS; y++)
                {
                    for (int z = z0; z <= z1 && count < MAX_BLOCKS; z++)
                    {
                        pos.set(x, y, z);

                        BlockState state = world.getBlockState(pos);

                        if (state.isAir())
                        {
                            continue;
                        }

                        VoxelShape shape = state.getCollisionShape(world, pos);

                        if (shape.isEmpty())
                        {
                            continue;
                        }

                        for (Box box : shape.getBoundingBoxes())
                        {
                            float hx = (float) (box.maxX - box.minX) / 2F;
                            float hy = (float) (box.maxY - box.minY) / 2F;
                            float hz = (float) (box.maxZ - box.minZ) / 2F;

                            if (hx < 0.001F || hy < 0.001F || hz < 0.001F)
                            {
                                continue;
                            }

                            float radius = Math.max(0.0005F, Math.min(0.05F, Math.min(hx, Math.min(hy, hz)) * 0.9F));
                            BoxShape leaf = new BoxShape(new Vec3(hx, hy, hz), radius);

                            compound.addShape(new Vec3(x + (float) box.minX + hx, y + (float) box.minY + hy, z + (float) box.minZ + hz), Quat.sIdentity(), leaf);
                            leaf.close();

                            count += 1;
                        }
                    }
                }
            }
        }

        if (count == 0)
        {
            /* No world to stand on: a slab under the lowest foot, so nothing falls forever */
            BoxShape floor = new BoxShape(new Vec3(64F, 0.5F, 64F), 0.05F);

            compound.addShape(new Vec3((minX + maxX) / 2F, minY - 0.5F, (minZ + maxZ) / 2F), Quat.sIdentity(), floor);
            floor.close();
        }

        ShapeResult result = compound.create();

        if (result.hasError())
        {
            BBSMod.LOGGER.warn("Physics could not build the ground: {}", result.getError());
            result.close();
            compound.close();

            return;
        }

        ShapeRefC reference = result.get();
        ConstShape ground = reference.getPtr();
        BodyCreationSettings settings = new BodyCreationSettings(ground, new RVec3(0D, 0D, 0D), Quat.sIdentity(), EMotionType.Static, LAYER_STATIC);

        settings.setFriction(0.6F);

        this.bodies.createAndAddBody(settings, EActivation.DontActivate);

        settings.close();
        reference.close();
        result.close();
        compound.close();
    }

    /** Ropes: a distance limit from the body to a replay's body, or to a fixed point above it */
    private void addRopes()
    {
        for (Rig rig : this.rigs)
        {
            ReplayPhysics physics = rig.replay.physics;

            if (!physics.rope.get())
            {
                continue;
            }

            float length = Math.max(0.2F, physics.ropeLength.get());
            Rig partner = null;

            for (Rig other : this.rigs)
            {
                if (other != rig && other.replay.getId().equals(physics.ropeAnchor.get()))
                {
                    partner = other;
                }
            }

            Body end;

            if (partner != null)
            {
                end = partner.body;
            }
            else
            {
                /* A tiny body that collides with nothing: kinematic when it follows a replay, still when it hangs from a point */
                Replay follow = this.replay(physics.ropeAnchor.get());
                float[] at = this.target(follow != null ? follow : rig.replay, 0F, AutoCollisionBuilder.build(follow != null ? follow.form.get() : rig.replay.form.get()));
                float up = follow != null ? 0F : length;
                SphereShape tiny = new SphereShape(0.05F);
                BodyCreationSettings settings = new BodyCreationSettings(tiny, new RVec3(at[0], at[1] + up, at[2]), Quat.sIdentity(), EMotionType.Kinematic, LAYER_GHOST);

                end = this.bodies.createBody(settings);
                this.bodies.addBody(end.getId(), EActivation.Activate);
                settings.close();
                tiny.close();

                rig.anchor = end;
            }

            DistanceConstraintSettings joint = new DistanceConstraintSettings();

            joint.setSpace(EConstraintSpace.LocalToBodyCom);
            joint.setPoint1(new RVec3(0D, rig.box.halfY(), 0D));
            joint.setPoint2(new RVec3(0D, 0D, 0D));
            joint.setMinDistance(0F);
            joint.setMaxDistance(length);

            rig.rope = joint.create(rig.body, end);
            this.system.addConstraint(rig.rope);
            joint.close();
        }
    }

    private Replay replay(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        for (Replay replay : this.film.replays.getList())
        {
            if (replay.getId().equals(id))
            {
                return replay;
            }
        }

        return null;
    }

    /* Targets: where the keys say a replay's centre and turn are at a tick */

    /** Centre x, y, z and the turn as a quaternion (x, y, z, w) the keyframes ask for at a tick */
    float[] target(Replay replay, float tick, AutoCollisionBuilder.Box box)
    {
        double x = replay.keyframes.x.interpolate(tick);
        double y = replay.keyframes.y.interpolate(tick);
        double z = replay.keyframes.z.interpolate(tick);
        double yaw = replay.keyframes.bodyYaw.interpolate(tick);

        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(-yaw));

        return new float[] {(float) x, (float) y + box.centerY(), (float) z, turn.x, turn.y, turn.z, turn.w};
    }

    /* Simulation */

    /** How far the cache reaches: the last tick that has a pose */
    int computed()
    {
        return this.poses.size() - 1;
    }

    /** Simulates up to a tick, a bounded number of ticks at a time */
    void advanceTo(int tick, int budget)
    {
        int limit = Math.min(tick, MAX_TICKS);

        while (this.computed() < limit && budget-- > 0 && !this.closed)
        {
            this.advance(this.computed() + 1);
        }
    }

    private void advance(int tick)
    {
        for (Rig rig : this.rigs)
        {
            this.drive(rig, tick);
            this.events(rig, tick);
        }

        for (Rig rig : this.rigs)
        {
            if (rig.anchor != null)
            {
                Replay follow = this.replay(rig.replay.physics.ropeAnchor.get());

                if (follow != null)
                {
                    float[] at = this.target(follow, tick, AutoCollisionBuilder.build(follow.form.get()));

                    this.bodies.moveKinematic(rig.anchor.getId(), new RVec3(at[0], at[1], at[2]), Quat.sIdentity(), DELTA);
                }
            }
        }

        this.system.update(DELTA, STEPS, this.temp, this.jobs);

        this.record(tick, this.saveState());
    }

    /** Puts the body where the keys want it, with the force the animation handle and the muscles give */
    private void drive(Rig rig, int tick)
    {
        ReplayPhysics physics = rig.replay.physics;
        float animation = physics.animationAt(tick);
        float muscles = physics.musclesAt(tick);
        float strength = 1F - (1F - animation) * (1F - muscles);
        float[] target = this.target(rig.replay, tick, rig.box);
        int id = rig.body.getId();

        if (animation >= 0.999F)
        {
            if (!rig.kinematic)
            {
                this.bodies.setMotionType(id, EMotionType.Kinematic, EActivation.Activate);
                rig.kinematic = true;
            }

            this.bodies.moveKinematic(id, new RVec3(target[0], target[1], target[2]), new Quat(target[3], target[4], target[5], target[6]), DELTA);

            return;
        }

        if (rig.kinematic)
        {
            /* Let go: Jolt keeps the velocity the last move gave it, which is the throw */
            this.bodies.setMotionType(id, EMotionType.Dynamic, EActivation.Activate);
            rig.kinematic = false;
        }

        if (strength <= 0.001F)
        {
            return;
        }

        this.bodies.getPositionAndRotation(id, this.scratchPosition, this.scratchRotation);

        Vec3 velocity = this.bodies.getLinearVelocity(id);
        Vec3 spin = this.bodies.getAngularVelocity(id);

        float homeX = (target[0] - (float) this.scratchPosition.xx()) / DELTA;
        float homeY = (target[1] - (float) this.scratchPosition.yy()) / DELTA;
        float homeZ = (target[2] - (float) this.scratchPosition.zz()) / DELTA;
        float speed = (float) Math.sqrt(homeX * homeX + homeY * homeY + homeZ * homeZ);

        if (speed > MAX_PULL_SPEED)
        {
            float scale = MAX_PULL_SPEED / speed;

            homeX *= scale;
            homeY *= scale;
            homeZ *= scale;
        }

        Quaternionf wanted = new Quaternionf(target[3], target[4], target[5], target[6]).normalize();
        Quaternionf current = new Quaternionf(this.scratchRotation.getX(), this.scratchRotation.getY(), this.scratchRotation.getZ(), this.scratchRotation.getW()).conjugate();
        Quaternionf delta = wanted.mul(current, new Quaternionf());

        if (delta.w < 0F)
        {
            delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        }

        float w = Math.min(delta.w, 1F);
        float sinHalfSquared = 1F - w * w;
        float turn = 0F;
        float ax = 0F;
        float ay = 0F;
        float az = 0F;

        if (sinHalfSquared > 1e-12F)
        {
            float inverse = (float) (1D / Math.sqrt(sinHalfSquared));

            turn = Math.min(2F * (float) Math.acos(w) / DELTA, MAX_PULL_SPIN);
            ax = delta.x * inverse;
            ay = delta.y * inverse;
            az = delta.z * inverse;
        }

        Vec3 nextVelocity = new Vec3(mix(velocity.getX(), homeX, strength), mix(velocity.getY(), homeY, strength), mix(velocity.getZ(), homeZ, strength));
        Vec3 nextSpin = new Vec3(mix(spin.getX(), ax * turn, strength), mix(spin.getY(), ay * turn, strength), mix(spin.getZ(), az * turn, strength));

        if (nextVelocity.isFinite() && nextSpin.isFinite())
        {
            this.bodies.setLinearAndAngularVelocity(id, nextVelocity, nextSpin);
            this.bodies.activateBody(id);
        }
    }

    private static float mix(float a, float b, float t)
    {
        return a + (b - a) * t;
    }

    /** Pushes and blasts keyed on this tick */
    private void events(Rig rig, int tick)
    {
        ReplayPhysics physics = rig.replay.physics;

        if (hasKey(physics.pushX, tick) || hasKey(physics.pushY, tick) || hasKey(physics.pushZ, tick))
        {
            this.kick(rig, physics.pushX.interpolate(tick).floatValue(), physics.pushY.interpolate(tick).floatValue(), physics.pushZ.interpolate(tick).floatValue(), 0F, 0F, 0F);
        }

        if (hasKey(physics.blast, tick))
        {
            float strength = physics.blast.interpolate(tick).floatValue();
            float ox = physics.blastX.interpolate(tick).floatValue();
            float oy = physics.blastY.interpolate(tick).floatValue();
            float oz = physics.blastZ.interpolate(tick).floatValue();
            float radius = Math.max(0.5F, physics.blastRadius.get());

            for (Rig other : this.rigs)
            {
                this.bodies.getPositionAndRotation(other.body.getId(), this.scratchPosition, this.scratchRotation);

                float dx = (float) this.scratchPosition.xx() - ox;
                float dy = (float) this.scratchPosition.yy() - oy;
                float dz = (float) this.scratchPosition.zz() - oz;
                float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance >= radius)
                {
                    continue;
                }

                float fall = 1F - distance / radius;
                float inverse = distance < 1e-4F ? 0F : 1F / distance;
                float push = strength * fall;

                /* Away from the blast with a lift, and a turn about the axis across the push so a body tumbles */
                this.kick(other, dx * inverse * push, (dy * inverse + 0.35F) * push, dz * inverse * push, dz * inverse * push * 0.5F, 0F, -dx * inverse * push * 0.5F);
            }
        }
    }

    /** A change of velocity, and of spin, for a body that is free to take it */
    private void kick(Rig rig, float vx, float vy, float vz, float sx, float sy, float sz)
    {
        if (rig.kinematic)
        {
            return;
        }

        int id = rig.body.getId();
        Vec3 velocity = this.bodies.getLinearVelocity(id);
        Vec3 spin = this.bodies.getAngularVelocity(id);
        float scale = rig.replay.physics.ragdoll.get() ? 1F : 0F;

        this.bodies.setLinearAndAngularVelocity(id,
            new Vec3(velocity.getX() + vx, velocity.getY() + vy, velocity.getZ() + vz),
            new Vec3(spin.getX() + sx * scale, spin.getY() + sy * scale, spin.getZ() + sz * scale));
        this.bodies.activateBody(id);
    }

    static boolean hasKey(KeyframeChannel<Double> channel, int tick)
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

    /* The cache */

    private byte[] saveState()
    {
        try (StateRecorderImpl recorder = new StateRecorderImpl())
        {
            this.system.saveState(recorder);

            return recorder.getData();
        }
    }

    private boolean restoreState(byte[] state)
    {
        try (StateRecorderImpl recorder = new StateRecorderImpl())
        {
            recorder.writeBytes(state);

            return this.system.restoreState(recorder);
        }
    }

    /** Keeps the state and the poses of the tick that was just simulated */
    private void record(int tick, byte[] state)
    {
        float[][] pose = new float[this.rigs.size()][7];
        boolean[] kinematic = new boolean[this.rigs.size()];

        for (int i = 0; i < this.rigs.size(); i++)
        {
            Rig rig = this.rigs.get(i);

            this.bodies.getPositionAndRotation(rig.body.getId(), this.scratchPosition, this.scratchRotation);

            pose[i][0] = (float) this.scratchPosition.xx();
            pose[i][1] = (float) this.scratchPosition.yy();
            pose[i][2] = (float) this.scratchPosition.zz();
            pose[i][3] = this.scratchRotation.getX();
            pose[i][4] = this.scratchRotation.getY();
            pose[i][5] = this.scratchRotation.getZ();
            pose[i][6] = this.scratchRotation.getW();
            kinematic[i] = rig.kinematic;
        }

        if (tick < this.poses.size())
        {
            this.poses.set(tick, pose);
            this.states.set(tick, state);
            this.kinematics.set(tick, kinematic);
            this.hashes.set(tick, this.hash(tick));
        }
        else
        {
            this.poses.add(pose);
            this.states.add(state);
            this.kinematics.add(kinematic);
            this.hashes.add(this.hash(tick));
        }
    }

    /** Everything the keys and the handles say about a tick: when this changes, the tick has to be simulated again */
    int hash(int tick)
    {
        int hash = 1;

        for (Rig rig : this.rigs)
        {
            ReplayPhysics physics = rig.replay.physics;
            float[] target = this.target(rig.replay, tick, rig.box);

            hash = hash * 31 + Objects.hash(target[0], target[1], target[2], target[3], target[4], target[5], target[6],
                physics.animationAt(tick), physics.musclesAt(tick));

            if (hasKey(physics.pushX, tick) || hasKey(physics.pushY, tick) || hasKey(physics.pushZ, tick))
            {
                hash = hash * 31 + Objects.hash(physics.pushX.interpolate(tick), physics.pushY.interpolate(tick), physics.pushZ.interpolate(tick));
            }

            if (hasKey(physics.blast, tick))
            {
                hash = hash * 31 + Objects.hash(physics.blast.interpolate(tick), physics.blastX.interpolate(tick), physics.blastY.interpolate(tick), physics.blastZ.interpolate(tick), physics.blastRadius.get());
            }
        }

        return hash;
    }

    /**
     * Finds the first tick whose keys changed since it was simulated and winds the world back to just before it.
     * The ticks from there on are simulated again when they are asked for.
     */
    void validate()
    {
        int computed = this.computed();

        for (int tick = 1; tick <= computed; tick++)
        {
            if (this.hashes.get(tick) != this.hash(tick))
            {
                this.rewind(tick - 1);

                return;
            }
        }
    }

    private void rewind(int tick)
    {
        if (!this.restoreState(this.states.get(tick)))
        {
            BBSMod.LOGGER.warn("Physics could not restore its state at tick {}, so the cache starts over.", tick);
            tick = 0;

            if (!this.restoreState(this.states.get(0)))
            {
                return;
            }
        }

        boolean[] kinematic = this.kinematics.get(tick);

        for (int i = 0; i < this.rigs.size(); i++)
        {
            Rig rig = this.rigs.get(i);

            /* The saved state holds where bodies are and how fast they move, not whether Jolt moves them or the keys do */
            if (rig.kinematic != kinematic[i])
            {
                this.bodies.setMotionType(rig.body.getId(), kinematic[i] ? EMotionType.Kinematic : EMotionType.Dynamic, EActivation.DontActivate);
                rig.kinematic = kinematic[i];
            }
        }

        while (this.poses.size() > tick + 1)
        {
            int last = this.poses.size() - 1;

            this.poses.remove(last);
            this.states.remove(last);
            this.kinematics.remove(last);
            this.hashes.remove(last);
        }
    }

    /** The pose of a rig at a tick the cache has: centre then quaternion */
    float[] pose(int rig, int tick)
    {
        int at = Math.max(0, Math.min(tick, this.computed()));

        return this.poses.get(at)[rig];
    }

    int indexOf(Replay replay)
    {
        for (int i = 0; i < this.rigs.size(); i++)
        {
            if (this.rigs.get(i).replay == replay)
            {
                return i;
            }
        }

        return -1;
    }

    /** Where a rig's feet are, from a pose */
    static Vector3f feet(float[] pose, float centerY)
    {
        Quaternionf q = new Quaternionf(pose[3], pose[4], pose[5], pose[6]);
        Vector3f offset = q.transform(new Vector3f(0F, centerY, 0F));

        return new Vector3f(pose[0] - offset.x, pose[1] - offset.y, pose[2] - offset.z);
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.closed = true;

        for (Rig rig : this.rigs)
        {
            if (rig.rope != null)
            {
                this.system.removeConstraint(rig.rope);
            }
        }

        this.system.close();
        this.temp.close();
        this.jobs.close();
        this.layerFilter.close();
        this.layers.close();
        this.pairs.close();
    }
}
