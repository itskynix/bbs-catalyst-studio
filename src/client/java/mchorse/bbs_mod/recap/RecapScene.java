package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.modifiers.FovPunchClip;
import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.function.IntSupplier;

/**
 * What Recap Studio puts on the world while its viewport shows a recap: the recap's camera at the
 * cursor, reworked by its timeline clips, and the recap's view settings, the scene ones hiding
 * parts of the world and the overrides replacing its time, weather, fog and lens.
 *
 * <p>All of it is off unless a recap is being shown ({@link #isActive}), so the game and the other
 * editors never see it.</p>
 *
 * <p><b>Time.</b> The timeline time is {@code cursor + transition}; while the recap is held still
 * the transition is left out, so a scrubbed moment looks the same in every frame. The recorded
 * camera is read at the moment the speed and freeze clips map that time to
 * ({@link RecapClips#sourceTime}).</p>
 *
 * <p><b>Camera.</b> The recorded eye, or the player's own eye for a recap kept before the camera
 * was, with the view settings' lens, roll and sway, is the position the timeline's clips start
 * from; they are applied over it in their layers exactly as the film studio applies them, so an
 * idle or keyframe clip takes the camera elsewhere and a modifier works on what is under it. The
 * effect clips leave their data in the same context, which the frame overlays draw from. While
 * the camera is flown by hand, that camera is shown instead.</p>
 */
public final class RecapScene implements ICameraController
{
    public static final RecapScene INSTANCE = new RecapScene();

    private static boolean active;
    private static RecapRecord record;
    private static RecapTrack track;
    private static IntSupplier cursor = () -> 0;
    private static boolean playing;
    private static float transition;
    private static long baseDayTime;

    private static final CameraClipContext CONTEXT = new CameraClipContext();
    private static final Position LAST = new Position();
    private static Position manual;

    /* How the recorded player moved, when the recap has it; its blocks are the world overlay's */
    private static RecapMotion motion;

    /** The free camera: where the viewport looks from while no camera clip is under the cursor. */
    private static final Position FREE = new Position();
    private static boolean freeSet;

    private final RecapMotion.Frame frame = new RecapMotion.Frame();

    private final RecapTrack.Sample sample = new RecapTrack.Sample();
    private final Position position = new Position();
    private RecapActor actor;
    private final java.util.WeakHashMap<Entity, IEntity> wrapped = new java.util.WeakHashMap<>();

    private RecapScene()
    {}

    public static void show(RecapRecord shown, RecapTrack camera, RecapMotion moves, RecapEvents changes, IntSupplier at)
    {
        /* The blocks of another recap shown before go back to the world's own first */
        RecapWorldOverlay.attach(changes);

        if (record != shown)
        {
            freeSet = false;
        }

        motion = moves;

        MinecraftClient mc = MinecraftClient.getInstance();

        /* The world's own day time, read before anything here can override it, for the timelapse */
        active = false;
        baseDayTime = mc.world == null ? 6000L : mc.world.getTimeOfDay() % 24000L;

        active = shown != null;
        record = shown;
        track = camera;
        cursor = at;
    }

    public static void hide()
    {
        if (active)
        {
            stopSounds();
        }

        /* The blocks the recap set go back to what the world really has now */
        RecapWorldOverlay.detach();

        manual = null;
        active = false;
        record = null;
        track = null;
        motion = null;
        freeSet = false;
        playing = false;
    }

    /**
     * Lets go of everything at a world change: the shown recap, its camera on the controller and,
     * when a recap was being shown, the frame size it asked the world to be drawn at.
     */
    public static void reset()
    {
        boolean wasShown = active;

        /* The world is going: its blocks are let go of with it, none set back */
        RecapWorldOverlay.drop();
        hide();
        mchorse.bbs_mod.BBSModClient.getCameraController().remove(INSTANCE);

        if (wasShown)
        {
            mchorse.bbs_mod.client.BBSRendering.setCustomSize(false);
        }
    }

    public static void setPlaying(boolean isPlaying)
    {
        playing = isPlaying;
    }

    public static boolean isActive()
    {
        return active && record != null && MinecraftClient.getInstance().world != null;
    }

    /* Dimensions */

    /** The dimension the recording was in at the moment drawn, after the speed and freeze clips; null when it kept none. */
    public static String recordedDimension()
    {
        if (!isActive() || record.dimensions.isEmpty())
        {
            return null;
        }

        return record.dimensions.at((int) Math.floor(RecapClips.sourceTime(record.clips, time())));
    }

    /**
     * Whether the recording is in another dimension than the client at the moment drawn: then its
     * camera and its player are somewhere this world does not have, and neither is shown.
     */
    public static boolean isElsewhere()
    {
        String at = recordedDimension();

        return at != null && !at.equals(RecapWorldOverlay.dimensionOf(MinecraftClient.getInstance().world));
    }

    /** Whether the recap has a recorded camera to look through. */
    public static boolean hasCamera()
    {
        return isActive() && track != null && !track.isEmpty();
    }

    /** The timeline time drawn this frame: the cursor, plus the frame's share of a tick while playing. */
    public static float time()
    {
        return cursor.getAsInt() + (playing ? transition : 0F);
    }

    private static boolean on(RecapOption option)
    {
        return isActive() && record.isOn(option);
    }

    /* Scene */

    public static boolean hidesBlocks()
    {
        return isActive() && !record.isOn(RecapOption.BLOCKS);
    }

    public static boolean hidesSky()
    {
        return isActive() && !record.isOn(RecapOption.SKY);
    }

    public static boolean hidesParticles()
    {
        return isActive() && !record.isOn(RecapOption.PARTICLES);
    }

    public static boolean hidesLabels()
    {
        return isActive() && !record.isOn(RecapOption.NAME_TAGS);
    }

    public static boolean hidesBeacons()
    {
        return isActive() && !record.isOn(RecapOption.BEACON_BEAMS);
    }

    public static boolean hides(Entity entity)
    {
        if (!isActive())
        {
            return false;
        }

        return entity instanceof PlayerEntity ? !record.isOn(RecapOption.PLAYERS) : !record.isOn(RecapOption.ENTITIES);
    }

    /* Interface */

    /** Whether a clip puts the camera somewhere: everything but the effects, tools, sound, sway and lens kicks. */
    public static boolean placesCamera(Clip clip)
    {
        return !(clip instanceof FxClip || clip instanceof RecapClips.ToolClip || clip instanceof AudioClip
            || clip instanceof ShakeClip || clip instanceof FovPunchClip);
    }

    private static boolean on(Clip clip, int tick)
    {
        return clip.enabled.get() && tick >= clip.tick.get() && tick < clip.tick.get() + clip.duration.get();
    }

    /** Whether a camera clip is under the cursor; without one the free camera is shown. */
    public static boolean hasCameraClip()
    {
        if (!isActive())
        {
            return false;
        }

        int tick = cursor.getAsInt();

        for (Clip clip : record.clips.get())
        {
            if (on(clip, tick) && placesCamera(clip))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the viewport looks out of the recorded player's eyes: a point of view clip under the
     * cursor, and no flight. Then the player's own body is not drawn, and the game's interface is,
     * after the view settings.
     */
    public static boolean isFirstPerson()
    {
        if (!isActive() || manual != null || isElsewhere())
        {
            return false;
        }

        int tick = cursor.getAsInt();

        for (Clip clip : record.clips.get())
        {
            if (on(clip, tick) && clip instanceof mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the viewport starts from the free camera: no flight, and no camera clip under the
     * cursor or the recording in another dimension, whose eye is nowhere in this world.
     */
    public static boolean isFreeCamera()
    {
        return isActive() && manual == null && (!hasCameraClip() || isElsewhere());
    }

    /** Puts the free camera somewhere, as a flight leaves it. */
    public static void setFree(Position position)
    {
        FREE.set(position);
        freeSet = true;
    }

    /** Whether a piece of the interface is drawn in the viewport: first person, and switched on. */
    public static boolean showsHud(RecapOption option)
    {
        return option.group == RecapOption.Group.HUD && isFirstPerson() && record.isOn(option);
    }

    /* Overrides */

    public static Long timeOfDay()
    {
        if (!isActive())
        {
            return null;
        }

        Long clipped = RecapClips.dayTime(record.clips, time(), on(RecapOption.TIME_OF_DAY) ? record.getValue(RecapOption.TIME_OF_DAY) : baseDayTime);

        if (clipped != null)
        {
            return clipped;
        }

        return on(RecapOption.TIME_OF_DAY) ? (long) record.getValue(RecapOption.TIME_OF_DAY) : null;
    }

    public static Double weather()
    {
        return on(RecapOption.WEATHER) ? (record.getValue(RecapOption.WEATHER) > 0 ? 1D : 0D) : null;
    }

    public static Integer fogColor()
    {
        return on(RecapOption.FOG_COLOR) ? record.getValue(RecapOption.FOG_COLOR) : null;
    }

    public static Float fogDistance()
    {
        return on(RecapOption.FOG) ? (float) record.getValue(RecapOption.FOG) : null;
    }

    public static boolean nightVision()
    {
        return on(RecapOption.NIGHT_VISION);
    }

    /* Flight */

    /** A camera flown by hand, which the viewport shows instead of the clips while it is set. */
    public static void setManual(Position position)
    {
        manual = position;
    }

    /** What the camera showed in the last frame, after the clips: where a flight starts from. */
    public static Position getLast()
    {
        return LAST;
    }

    /** The clips' context of the last frame: what the effects are drawn from. */
    public static CameraClipContext getContext()
    {
        return CONTEXT;
    }

    /* Camera */

    @Override
    public int getPriority()
    {
        return 100400;
    }

    /**
     * Where the recording's eye is at timeline {@code time}, after the speed and freeze clips: the
     * recorded camera, or the player's own eye for a recap kept before the camera was.
     */
    public boolean base(float time, RecapTrack.Sample out)
    {
        if (!isActive())
        {
            return false;
        }

        float source = RecapClips.sourceTime(record.clips, time);

        if (track != null && track.sample(source, out))
        {
            return true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return false;
        }

        Vec3d eye = mc.player.getCameraPosVec(1F);

        out.x = eye.x;
        out.y = eye.y;
        out.z = eye.z;
        out.yaw = mc.player.getYaw();
        out.pitch = mc.player.getPitch();
        out.fov = mc.options.getFov().getValue();

        return true;
    }

    @Override
    public void setup(Camera camera, float frameTransition)
    {
        transition = Math.max(0F, Math.min(1F, frameTransition));

        float time = time();
        int now = cursor.getAsInt();

        /* The world's blocks as the recording had them at this moment, of this dimension only;
         * also while flying, so the cursor moved under a flight shows its blocks */
        if (isActive())
        {
            RecapWorldOverlay.update(MinecraftClient.getInstance().world, (int) Math.floor(RecapClips.sourceTime(record.clips, now)));
        }

        if (manual != null)
        {
            manual.apply(camera);
            LAST.set(manual);

            return;
        }

        if (!this.base(time, this.sample))
        {
            return;
        }

        boolean elsewhere = isElsewhere();
        boolean free = !hasCameraClip() || elsewhere;

        /* The free camera, outside, where it was left; first seen behind the player, or where the
         * client is when the recording is in another dimension */
        if (free && !freeSet)
        {
            Position outside = elsewhere ? liveView() : outsideView(record, track, motion, time);

            if (outside != null)
            {
                setFree(outside);
            }
        }

        /* The eye as recorded, with the view settings' lens, roll and sway; a position's yaw
         * is the game's turned half way round */
        float yaw = this.sample.yaw;
        float pitch = this.sample.pitch;
        float roll = on(RecapOption.CAMERA_ROLL) ? record.getValue(RecapOption.CAMERA_ROLL) : 0F;
        float fov = on(RecapOption.FOV) ? record.getValue(RecapOption.FOV) : this.sample.fov;

        if (on(RecapOption.CAMERA_SHAKE))
        {
            float amount = record.getValue(RecapOption.CAMERA_SHAKE) / 100F * 1.6F;

            yaw += amount * (float) (Math.sin(time * 0.37F) + 0.5F * Math.sin(time * 0.91F + 1.3F));
            pitch += amount * (float) (Math.sin(time * 0.29F + 2.1F) + 0.5F * Math.sin(time * 1.13F));
        }

        if (free && freeSet)
        {
            this.position.set(FREE);
            this.position.angle.roll += roll;
            this.position.angle.fov = on(RecapOption.FOV) ? record.getValue(RecapOption.FOV) : this.position.angle.fov;
        }
        else
        {
            this.position.point.set(this.sample.x, this.sample.y, this.sample.z);
            this.position.angle.set(yaw + 180F, pitch, roll, fov);
        }

        /* Then the clips under the cursor, in their layers, as the film studio applies them */
        int tick = cursor.getAsInt();
        float sub = playing ? transition : 0F;

        this.fillEntities();
        CONTEXT.clips = record.clips;
        CONTEXT.playing = playing;
        CONTEXT.clipData.clear();
        CONTEXT.setup(tick, sub);

        for (Clip clip : record.clips.getClips(tick))
        {
            CONTEXT.apply(clip, this.position);
        }

        AudioClientClip.manageSounds(CONTEXT);
        CONTEXT.currentLayer = 0;

        this.position.angle.fov = Math.max(1F, Math.min(170F, this.position.angle.fov));
        this.position.apply(camera);
        LAST.set(this.position);
    }

    /** The entities the tracking clips can follow: the player, and the other players by name. */
    private void fillEntities()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        CONTEXT.entities.clear();

        if (mc.world == null || mc.player == null)
        {
            return;
        }

        /* The recorded player, standing where the recording had it, for the clips that follow it */
        if (this.actor == null || this.actor.getMcEntity() != mc.player)
        {
            this.actor = new RecapActor(mc.player);
        }

        int tick = cursor.getAsInt();

        /* Not while the recording is in another dimension: the clips that follow it hold still */
        if (!isElsewhere())
        {
            this.actor.place(track, motion, RecapClips.sourceTime(record.clips, tick), RecapClips.sourceTime(record.clips, tick + 1));
            CONTEXT.entities.put(RecapClips.PLAYER, this.actor.isPlaced() ? this.actor : this.wrap(mc.player));
        }

        for (PlayerEntity player : mc.world.getPlayers())
        {
            if (player != mc.player)
            {
                CONTEXT.entities.put(player.getName().getString(), this.wrap(player));
            }
        }
    }

    /**
     * The recorded player at a moment of the recording: its own frame when its movement was kept,
     * else made from the camera, feet an eye height under it. False when there is neither.
     */
    public static boolean frameAt(RecapTrack camera, RecapMotion moves, float source, RecapMotion.Frame out, double eyeHeight)
    {
        if (moves != null && moves.sample(source, out))
        {
            return true;
        }

        RecapTrack.Sample sample = new RecapTrack.Sample();

        if (camera == null || !camera.sample(source, sample))
        {
            return false;
        }

        out.x = sample.x;
        out.y = sample.y - eyeHeight;
        out.z = sample.z;
        out.bodyYaw = out.headYaw = sample.yaw;
        out.pitch = sample.pitch;
        out.limbPos = out.limbSpeed = out.swing = out.lean = 0F;
        out.pose = net.minecraft.entity.EntityPose.STANDING;
        out.flags = RecapMotion.ON_GROUND;

        return true;
    }

    /**
     * A camera outside, behind and above the recorded player at {@code time}, looking at its head:
     * where the free camera starts and what the first camera clip of a new recap holds.
     */
    public static Position outsideView(RecapRecord shown, RecapTrack camera, RecapMotion moves, float time)
    {
        RecapMotion.Frame at = new RecapMotion.Frame();
        float source = shown == null ? time : RecapClips.sourceTime(shown.clips, time);

        if (!frameAt(camera, moves, source, at, 1.62D))
        {
            return null;
        }

        double headY = at.y + 1.62D;
        double yawRad = Math.toRadians(at.headYaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double cx = at.x - fx * 4.5D;
        double cy = headY + 1.2D;
        double cz = at.z - fz * 4.5D;
        double dx = at.x - cx;
        double dy = headY - cy;
        double dz = at.z - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        Position position = new Position();

        position.point.set(cx, cy, cz);
        position.angle.set(yaw + 180F, pitch, 0F, 70F);

        return position;
    }

    /** Where the client's own camera is: the free camera's start while the recording is elsewhere. */
    private static Position liveView()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return null;
        }

        Vec3d eye = mc.player.getCameraPosVec(1F);
        Position position = new Position();

        position.point.set(eye.x, eye.y, eye.z);
        position.angle.set(mc.player.getYaw() + 180F, mc.player.getPitch(), 0F, 70F);

        return position;
    }

    private IEntity wrap(Entity entity)
    {
        return this.wrapped.computeIfAbsent(entity, MCEntity::new);
    }

    /**
     * Draws the recorded player in the world, where, as turned and as moving as the recording had
     * it: the player's own model, with the recorded stride, arm swing, crouch, swim and glide lent
     * to it for the draw and given back after. Not while the viewport looks out of its eyes.
     */
    public static void renderActor(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive() || mc.player == null || isFirstPerson() || isElsewhere() || !record.isOn(RecapOption.PLAYERS)
            || context.matrixStack() == null || context.consumers() == null)
        {
            return;
        }

        if ((track == null || track.isEmpty()) && (motion == null || motion.isEmpty()))
        {
            return;
        }

        net.minecraft.client.network.ClientPlayerEntity player = mc.player;
        RecapMotion.Frame at = INSTANCE.frame;

        if (!frameAt(track, motion, RecapClips.sourceTime(record.clips, time()), at, player.getStandingEyeHeight()))
        {
            return;
        }

        net.minecraft.client.render.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3d camera = context.camera().getPos();
        mchorse.bbs_mod.mixin.client.RecapLimbAccessor limbs = (mchorse.bbs_mod.mixin.client.RecapLimbAccessor) player.limbAnimator;
        mchorse.bbs_mod.mixin.client.RecapLivingAccessor living = (mchorse.bbs_mod.mixin.client.RecapLivingAccessor) player;
        mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker flags = (mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker) player;

        /* Everything the renderer reads off the entity, kept to be given back */
        float yaw = player.getYaw(), prevYaw = player.prevYaw, pitch = player.getPitch(), prevPitch = player.prevPitch;
        float bodyYaw = player.bodyYaw, prevBodyYaw = player.prevBodyYaw, headYaw = player.headYaw, prevHeadYaw = player.prevHeadYaw;
        float limbPos = player.limbAnimator.getPos(), limbSpeed = player.limbAnimator.getSpeed(), limbPrev = limbs.bbs$getPrevSpeed();
        float swing = player.handSwingProgress, lastSwing = player.lastHandSwingProgress;
        float lean = living.bbs$getLeaningPitch(), lastLean = living.bbs$getLastLeaningPitch();
        net.minecraft.entity.EntityPose pose = player.getPose();
        boolean sneaking = player.isSneaking(), sprinting = player.isSprinting(), swimming = player.isSwimming();
        boolean gliding = flags.bbs$getFlag(RecapMotion.GLIDE_FLAG);
        boolean shadows = mc.options.getEntityShadows().getValue();

        try
        {
            player.setYaw(at.headYaw);
            player.prevYaw = at.headYaw;
            player.setPitch(at.pitch);
            player.prevPitch = at.pitch;
            player.bodyYaw = player.prevBodyYaw = at.bodyYaw;
            player.headYaw = player.prevHeadYaw = at.headYaw;
            limbs.bbs$setPos(at.limbPos);
            limbs.bbs$setSpeed(at.limbSpeed);
            limbs.bbs$setPrevSpeed(at.limbSpeed);
            player.handSwingProgress = player.lastHandSwingProgress = at.swing;
            living.bbs$setLeaningPitch(at.lean);
            living.bbs$setLastLeaningPitch(at.lean);
            player.setPose(at.pose);
            player.setSneaking(at.is(RecapMotion.SNEAKING));
            player.setSprinting(at.is(RecapMotion.SPRINTING));
            player.setSwimming(at.is(RecapMotion.SWIMMING));
            flags.bbs$setFlag(RecapMotion.GLIDE_FLAG, at.is(RecapMotion.GLIDING));

            /* Its shadow would be cast where the player really stands */
            dispatcher.setRenderShadows(false);

            int light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y + 1D, at.z));

            /* The animation is already at its moment: drawn with no further transition */
            dispatcher.render(player, at.x - camera.x, at.y - camera.y, at.z - camera.z, at.headYaw, 1F, context.matrixStack(), context.consumers(), light);
        }
        finally
        {
            dispatcher.setRenderShadows(shadows);
            player.setYaw(yaw);
            player.prevYaw = prevYaw;
            player.setPitch(pitch);
            player.prevPitch = prevPitch;
            player.bodyYaw = bodyYaw;
            player.prevBodyYaw = prevBodyYaw;
            player.headYaw = headYaw;
            player.prevHeadYaw = prevHeadYaw;
            limbs.bbs$setPos(limbPos);
            limbs.bbs$setSpeed(limbSpeed);
            limbs.bbs$setPrevSpeed(limbPrev);
            player.handSwingProgress = swing;
            player.lastHandSwingProgress = lastSwing;
            living.bbs$setLeaningPitch(lean);
            living.bbs$setLastLeaningPitch(lastLean);
            player.setPose(pose);
            player.setSneaking(sneaking);
            player.setSprinting(sprinting);
            player.setSwimming(swimming);
            flags.bbs$setFlag(RecapMotion.GLIDE_FLAG, gliding);
        }
    }

    /** Lets the sounds of the audio clips go when the recap is no longer shown. */
    public static void stopSounds()
    {
        CONTEXT.playing = false;

        if (CONTEXT.clips != null)
        {
            AudioClientClip.manageSounds(CONTEXT);
        }

        CONTEXT.shutdown();
        CONTEXT.clips = null;
        INSTANCE.wrapped.clear();
        INSTANCE.actor = null;
    }
}
