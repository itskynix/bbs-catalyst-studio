package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.film.eye.ActorEyeState;
import mchorse.bbs_mod.film.eye.PovActions;
import mchorse.bbs_mod.film.eye.PovHands;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

import java.util.List;

/**
 * The actor-eye clip as the client plays it: the camera is put where the actor's bone is (through
 * {@link TrackerFrame}, the same frame the tracker clip stands in, anchors included), turned the way
 * the bone is, and trailed behind it as much as the damping says. An actor whose form has no such
 * bone is looked out of from its eyes instead.
 *
 * <p>On top of that come the camera modes and the replay's point of view: without Head Look the
 * body's facing is followed instead of the head; Hardcore Look (the clip's or the replay's) keeps
 * the head within the turn the body allows; the replay's Vision channels tip the eye and set the
 * lens; its hurt jolts the view when both the replay and the clip let the camera shake; and the
 * perspective steps the camera back behind the actor, or out in front of it facing back, the way
 * the game's F5 does, stopping short of a wall in between.</p>
 */
public class ActorEyeClientClip extends ActorEyeClip
{
    /** How far the game holds the camera from the player in the third person */
    private static final double THIRD_PERSON_DISTANCE = 4D;
    /** How far the head may turn from the body under Hardcore Look, in degrees */
    private static final float HARDCORE_TURN = 50F;

    /* The damped camera, and the moment it is of: smoothing needs where it was a moment ago */
    private final Position damped = new Position();
    private float dampedTime = Float.NaN;

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        List<IEntity> entities = this.getEntities(context);

        if (entities.isEmpty())
        {
            return;
        }

        IEntity entity = entities.get(0);
        Replay replay = PovHands.findReplay(this.selector.get());
        ReplayPov pov = replay == null ? null : replay.pov;
        float filmTick = context.ticks + context.transition;
        float replayTick = replay == null ? filmTick : replay.getTick(context.ticks) + context.transition;
        float tick = context.relativeTick + context.transition;
        Point offset = this.offsetAt(tick);

        if (pov != null)
        {
            /* The replay's Vision page moves the eye too, on top of the clip's own offset */
            offset = new Point(offset.x + keyed(pov.eyeOffsetX, replayTick), offset.y + keyed(pov.eyeOffsetY, replayTick), offset.z + keyed(pov.eyeOffsetZ, replayTick));
        }

        Point angle = this.angleAt(tick);
        boolean headLook = this.headLook.get();
        TrackerFrame frame = headLook ? TrackerFrame.resolve(((CameraClipContext) context).entities, entity, this.bone.get(),
            position.point.x, position.point.y, position.point.z, context.transition) : null;
        float body = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), context.transition) + 180F;

        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        float roll;

        if (frame != null)
        {
            Vector3d point = frame.position(offset);
            Angle turned = frame.angles(angle);

            x = point.x;
            y = point.y;
            z = point.z;
            yaw = turned.yaw;
            pitch = turned.pitch;
            roll = turned.roll;
        }
        else
        {
            /* No such bone (or the head not followed): the actor's eyes, looking where its head
             * looks - or, without Head Look, level and straight ahead of its body */
            x = Lerps.lerp(entity.getPrevX(), entity.getX(), context.transition) + offset.x;
            y = Lerps.lerp(entity.getPrevY(), entity.getY(), context.transition) + entity.getEyeHeight() + offset.y;
            z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), context.transition) + offset.z;
            yaw = (headLook ? Lerps.lerp(entity.getPrevHeadYaw(), entity.getHeadYaw(), context.transition) + 180F : body) + (float) angle.y;
            pitch = (headLook ? Lerps.lerp(entity.getPrevPitch(), entity.getPitch(), context.transition) : 0F) + (float) angle.x;
            roll = (float) angle.z;
        }

        if (this.hardcoreLook.get() || (pov != null && pov.hardcoreLook.get()))
        {
            yaw = body + MathUtils.clamp(wrap(yaw - body), -HARDCORE_TURN, HARDCORE_TURN);
        }

        float fov = this.fov.get();

        if (pov != null)
        {
            yaw += (float) keyed(pov.eyeYaw, replayTick);
            pitch += (float) keyed(pov.eyePitch, replayTick);
            roll += (float) keyed(pov.eyeRoll, replayTick);

            if (!pov.fov.isEmpty())
            {
                fov = pov.fov.interpolate(replayTick, (double) fov).floatValue();
            }

            if (this.outCameraShake.get() && pov.cameraShake.get())
            {
                /* The replay's own hurt, off its damage channel (the take keys the game's hurt time
                 * there, 10 counting down to 0): the actor body's timer only where the channel is empty,
                 * as a body driven by the film does not always keep one */
                float hurt = replay.keyframes.damage.isEmpty()
                    ? entity.getHurtTimer() - context.transition
                    : replay.keyframes.damage.interpolate(replayTick, 0D).floatValue();

                roll += PovActions.hurtRoll(hurt, PovActions.HURT_TICKS);
            }

            /* The POV action clips: the shake clips' hurt, death tilt and tremble, the freeze's narrowing */
            float[] tilt = PovActions.viewTilt(replay, replayTick, this.outPovActions.get() && this.outCameraShake.get(), this.outScreenEffects.get());

            pitch += tilt[0];
            roll += tilt[1];
            fov = (fov + tilt[2]) * tilt[3];
        }

        if (this.outCameraShake.get() && (pov == null || pov.cameraShake.get()))
        {
            /* Walking and running view bobbing (GameRenderer#bobView): added to pitch and roll */
            float[] bob = PovHands.bob(replay, entity, replayTick, context.transition);
            float p = bob[0] * (float) Math.PI;
            float h = bob[1];

            pitch += Math.abs((float) Math.cos(p - 0.2F) * h) * 5.0F;
            roll += (float) Math.sin(p) * h * 3.0F;
        }

        /* Bow and crossbow draw FOV narrowing (AbstractClientPlayerEntity#getFovMultiplier) */
        float pullProgress = pullProgress(replay, entity, replayTick);

        if (pullProgress > 0.0F)
        {
            fov *= 1.0F - pullProgress * 0.15F;
        }

        this.damp(filmTick, x, y, z, yaw, pitch, roll);

        double px = this.damped.point.x;
        double py = this.damped.point.y;
        double pz = this.damped.point.z;
        float pyaw = this.damped.angle.yaw;
        float ppitch = this.damped.angle.pitch;
        int perspective = this.perspective.get();

        if (perspective != PERSPECTIVE_FIRST)
        {
            /* The game's yaw is the camera's turned half way round */
            Vec3d eye = new Vec3d(px, py, pz);
            Vec3d forward = Vec3d.fromPolar(ppitch, pyaw - 180F);
            Vec3d away = perspective == PERSPECTIVE_BACK ? forward.negate() : forward;
            double distance = THIRD_PERSON_DISTANCE;
            World world = entity.getWorld();

            if (world != null)
            {
                BlockHitResult hit = RayTracing.rayTrace(world, eye, away, distance);

                if (hit != null && hit.getType() != HitResult.Type.MISS)
                {
                    distance = Math.max(0D, hit.getPos().distanceTo(eye) - 0.1D);
                }
            }

            px = eye.x + away.x * distance;
            py = eye.y + away.y * distance;
            pz = eye.z + away.z * distance;

            if (perspective == PERSPECTIVE_FRONT)
            {
                pyaw += 180F;
                ppitch = -ppitch;
            }
        }

        position.point.set(px, py, pz);
        position.angle.yaw = pyaw;
        position.angle.pitch = ppitch;
        position.angle.roll = this.damped.angle.roll;
        position.angle.fov = fov;

        ActorEyeState.report(entity, this, replay, px, py, pz, replayTick, perspective == PERSPECTIVE_FIRST);
    }

    private static double keyed(KeyframeChannel<Double> channel, float tick)
    {
        return channel.isEmpty() ? 0D : channel.interpolate(tick, 0D);
    }

    /**
     * How far along a bow or crossbow draw the actor is ({@code 0..1}): squared for a bow as in
     * {@code AbstractClientPlayerEntity#getFovMultiplier}, linear over the charge window for a crossbow.
     */
    private static float pullProgress(Replay replay, IEntity entity, float tick)
    {
        for (int i = 0; i < 2; i++)
        {
            boolean mainHand = i == 0;
            ItemUsePose.Use use = replay == null ? null : ReplayItemUse.compute(replay, tick, mainHand);

            if (use == null && entity != null)
            {
                use = ItemUsePose.get(entity, mainHand);
            }

            if (use == null)
            {
                continue;
            }

            if (use.action() == UseAction.BOW)
            {
                float progress = Math.max(0.0F, use.elapsed() / 20.0F);

                return progress > 1.0F ? 1.0F : progress * progress;
            }

            if (use.action() == UseAction.CROSSBOW)
            {
                return Math.min(1.0F, Math.max(0.0F, use.elapsed() / Math.max(1.0F, use.window())));
            }
        }

        return 0.0F;
    }

    /**
     * Trail the camera behind the bone: each tick it closes {@code 1 - damping} of the gap. A jump in
     * time (a scrub, a cut, the first look) starts it on the bone, so scrubbing never shows a camera
     * catching up from somewhere else.
     */
    private void damp(float time, double x, double y, double z, float yaw, float pitch, float roll)
    {
        float damping = Math.min(this.damping.get(), 0.98F);
        float dt = time - this.dampedTime;

        if (damping <= 0F || Float.isNaN(this.dampedTime) || dt < 0F || dt > 4F)
        {
            this.damped.point.set(x, y, z);
            this.damped.angle.yaw = yaw;
            this.damped.angle.pitch = pitch;
            this.damped.angle.roll = roll;
            this.dampedTime = time;

            return;
        }

        if (dt == 0F)
        {
            return;
        }

        float follow = 1F - (float) Math.pow(damping, dt);
        Position d = this.damped;

        d.point.set(d.point.x + (x - d.point.x) * follow, d.point.y + (y - d.point.y) * follow, d.point.z + (z - d.point.z) * follow);
        d.angle.yaw += wrap(yaw - d.angle.yaw) * follow;
        d.angle.pitch += (pitch - d.angle.pitch) * follow;
        d.angle.roll += wrap(roll - d.angle.roll) * follow;
        this.dampedTime = time;
    }

    /** The shortest way round between two angles, in degrees */
    private static float wrap(float degrees)
    {
        degrees %= 360F;

        if (degrees > 180F)
        {
            degrees -= 360F;
        }
        else if (degrees < -180F)
        {
            degrees += 360F;
        }

        return degrees;
    }

    @Override
    protected Clip create()
    {
        return new ActorEyeClientClip();
    }
}
