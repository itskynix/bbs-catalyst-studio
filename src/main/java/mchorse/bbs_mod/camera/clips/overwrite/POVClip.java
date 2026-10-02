package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

/**
 * Native First-Person / POV Camera Clip for BBS CS.
 *
 * <p>Locks camera position and rotation smoothly to a target replay actor's
 * eye position and head look angles with transition interpolation.
 * Supports First Person, Third Person (Back), and Third Person (Front) perspectives
 * with collider raytracing collision detection.</p>
 */
public class POVClip extends CameraClip
{
    public static final int PERSPECTIVE_FIRST_PERSON = 0;
    public static final int PERSPECTIVE_THIRD_PERSON_BACK = 1;
    public static final int PERSPECTIVE_THIRD_PERSON_FRONT = 2;

    private static final float DISTANCE = 4.0F;
    private static final float WALL_MARGIN = 0.1F;

    public final ValueString selector = new ValueString("selector", "");
    public final ValuePoint offset = new ValuePoint("offset", new Point(0, 0, 0));
    public final ValueInt perspective = new ValueInt("perspective", PERSPECTIVE_FIRST_PERSON, 0, 2);
    public final ValueBoolean headLook = new ValueBoolean("head_look", true);
    public final ValueFloat fov = new ValueFloat("fov", 70F, 10F, 150F);
    public final ValueBoolean povOutput = new ValueBoolean("pov_output", true);
    public final ValueBoolean renderHands = new ValueBoolean("render_hands", true);
    public final ValueBoolean leftHandVisible = new ValueBoolean("left_hand", true);
    public final ValueBoolean rightHandVisible = new ValueBoolean("right_hand", true);
    public final ValueBoolean bobbing = new ValueBoolean("bobbing", true);
    public final ValueFloat bobStrength = new ValueFloat("bob_strength", 1.0F, 0.0F, 2.0F);

    public POVClip()
    {
        super();

        this.add(this.selector);
        this.add(this.offset);
        this.add(this.perspective);
        this.add(this.headLook);
        this.add(this.fov);
        this.add(this.povOutput);
        this.add(this.renderHands);
        this.add(this.leftHandVisible);
        this.add(this.rightHandVisible);
        this.add(this.bobbing);
        this.add(this.bobStrength);
    }

    public boolean isFirstPerson()
    {
        return this.perspective.get() == PERSPECTIVE_FIRST_PERSON;
    }

    public IEntity getActor(ClipContext context)
    {
        if (!(context instanceof CameraClipContext cameraClipContext))
        {
            return null;
        }

        String id = this.selector.get();

        if (id.isEmpty())
        {
            return null;
        }

        IEntity entity = cameraClipContext.entities.get(id);

        if (entity != null)
        {
            return entity;
        }

        /* Fallback: if id is a replay index or name */
        if (context.clips != null && context.clips.getParent() instanceof Film film)
        {
            List<Replay> replays = film.replays.getList();

            try
            {
                int index = Integer.parseInt(id);

                if (index >= 0 && index < replays.size())
                {
                    return cameraClipContext.entities.get(replays.get(index).getId());
                }
            }
            catch (NumberFormatException ignored)
            {}

            for (Replay replay : replays)
            {
                if (id.equalsIgnoreCase(replay.getName()))
                {
                    return cameraClipContext.entities.get(replay.getId());
                }
            }
        }

        return null;
    }

    public Replay getReplay(ClipContext context)
    {
        if (context.clips != null && context.clips.getParent() instanceof Film film)
        {
            List<Replay> replays = film.replays.getList();
            String id = this.selector.get();

            if (id.isEmpty())
            {
                return null;
            }

            try
            {
                int index = Integer.parseInt(id);

                if (index >= 0 && index < replays.size())
                {
                    return replays.get(index);
                }
            }
            catch (NumberFormatException ignored)
            {}

            for (Replay replay : replays)
            {
                if (id.equals(replay.getId()) || id.equalsIgnoreCase(replay.getName()))
                {
                    return replay;
                }
            }
        }

        return null;
    }

    public List<IEntity> getEntities(ClipContext context)
    {
        IEntity actor = this.getActor(context);

        return actor == null ? Collections.emptyList() : Collections.singletonList(actor);
    }

    @Override
    public void fromCamera(Camera camera)
    {
        super.fromCamera(camera);

        this.fov.set((float) Math.toDegrees(camera.fov));
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        if (!this.povOutput.get())
        {
            POVClientState.clear();
            return;
        }

        IEntity actor = this.getActor(context);

        if (actor == null)
        {
            POVClientState.clear();
            return;
        }

        float transition = context.transition;
        Point off = this.offset.get();

        double px = Lerps.lerp(actor.getPrevX(), actor.getX(), transition) + off.x;
        double py = Lerps.lerp(actor.getPrevY(), actor.getY(), transition) + actor.getEyeHeight() + off.y;
        double pz = Lerps.lerp(actor.getPrevZ(), actor.getZ(), transition) + off.z;

        float yaw = (float) Lerps.lerpYaw(actor.getPrevHeadYaw(), actor.getHeadYaw(), transition);
        float pitch = Lerps.lerp(actor.getPrevPitch(), actor.getPitch(), transition);
        float roll = actor.getRoll();

        /* Dynamic Walking and Camera Sway Bobbing (Smooth Sine Calibration) */
        float strength = this.bobStrength.get();
        if (this.bobbing.get() && strength > 0.001F)
        {
            float limbPos = actor.getLimbPos(transition);
            float limbSpeed = actor.getLimbSpeed(transition);

            if (limbSpeed > 0.001F)
            {
                /* Smoothstep ease-in damping on movement speed */
                float speed = Math.min(limbSpeed, 1.0F);
                float smoothSpeed = speed * speed * (3.0F - 2.0F * speed);
                float bobFactor = smoothSpeed * strength;

                /* Stride cycle frequency matching Minecraft's native leg cycle (0.6662F) */
                float stepCycle = limbPos * 0.6662F;
                float rollSway = (float) Math.sin(stepCycle);
                float pitchDip = (float) (Math.cos(stepCycle * 2.0F) * 0.5 + 0.5);

                /* Gentle cinematic sway and tilt */
                roll += rollSway * bobFactor * 0.5F;
                pitch += pitchDip * bobFactor * 0.35F;

                /* Subtle lateral and vertical stepping offsets */
                double bobY = -pitchDip * bobFactor * 0.012;
                double bobSide = rollSway * bobFactor * 0.010;

                float yawRad = (float) Math.toRadians(yaw);
                px += Math.cos(yawRad) * bobSide;
                pz += Math.sin(yawRad) * bobSide;
                py += bobY;
            }
        }

        int viewMode = this.perspective.get();

        if (viewMode == PERSPECTIVE_FIRST_PERSON)
        {
            Replay replay = this.getReplay(context);
            POVClientState.setActive(this, actor, replay, transition);

            position.point.set(px, py, pz);

            if (this.headLook.get())
            {
                position.angle.set(yaw + 180F, pitch, roll, this.fov.get());
            }
            else
            {
                position.angle.fov = this.fov.get();
            }
        }
        else
        {
            POVClientState.clear();

            if (viewMode == PERSPECTIVE_THIRD_PERSON_BACK)
            {
                float pitchRad = (float) Math.toRadians(pitch);
                float yawRad = (float) Math.toRadians(yaw);

                /* Direction vector pointing backward from actor's gaze */
                double bx = Math.sin(yawRad) * Math.cos(pitchRad);
                double by = Math.sin(pitchRad);
                double bz = -Math.cos(yawRad) * Math.cos(pitchRad);

                float dist = DISTANCE;
                World world = actor.getWorld();

                if (world != null)
                {
                    Vec3d start = new Vec3d(px, py, pz);
                    Vec3d dir = new Vec3d(bx, by, bz);
                    BlockHitResult hit = RayTracing.rayTrace(world, start, dir, dist);

                    if (hit != null && hit.getType() == HitResult.Type.BLOCK)
                    {
                        dist = (float) Math.max(0.2, start.distanceTo(hit.getPos()) - WALL_MARGIN);
                    }
                }

                position.point.set(px + bx * dist, py + by * dist, pz + bz * dist);

                if (this.headLook.get())
                {
                    position.angle.set(yaw + 180F, pitch, roll, this.fov.get());
                }
                else
                {
                    position.angle.fov = this.fov.get();
                }
            }
            else if (viewMode == PERSPECTIVE_THIRD_PERSON_FRONT)
            {
                float pitchRad = (float) Math.toRadians(pitch);
                float yawRad = (float) Math.toRadians(yaw);

                /* Direction vector pointing forward from actor's gaze */
                double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
                double fy = -Math.sin(pitchRad);
                double fz = Math.cos(yawRad) * Math.cos(pitchRad);

                float dist = DISTANCE;
                World world = actor.getWorld();

                if (world != null)
                {
                    Vec3d start = new Vec3d(px, py, pz);
                    Vec3d dir = new Vec3d(fx, fy, fz);
                    BlockHitResult hit = RayTracing.rayTrace(world, start, dir, dist);

                    if (hit != null && hit.getType() == HitResult.Type.BLOCK)
                    {
                        dist = (float) Math.max(0.2, start.distanceTo(hit.getPos()) - WALL_MARGIN);
                    }
                }

                position.point.set(px + fx * dist, py + fy * dist, pz + fz * dist);

                if (this.headLook.get())
                {
                    position.angle.set(yaw, -pitch, -roll, this.fov.get());
                }
                else
                {
                    position.angle.fov = this.fov.get();
                }
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new POVClip();
    }
}
