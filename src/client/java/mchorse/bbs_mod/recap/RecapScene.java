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

    private final RecapTrack.Sample sample = new RecapTrack.Sample();
    private final Position position = new Position();
    private RecapActor actor;
    private final java.util.WeakHashMap<Entity, IEntity> wrapped = new java.util.WeakHashMap<>();

    private RecapScene()
    {}

    public static void show(RecapRecord shown, RecapTrack camera, IntSupplier at)
    {
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

        manual = null;
        active = false;
        record = null;
        track = null;
        playing = false;
    }

    /**
     * Lets go of everything at a world change: the shown recap, its camera on the controller and,
     * when a recap was being shown, the frame size it asked the world to be drawn at.
     */
    public static void reset()
    {
        boolean wasShown = active;

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

    /**
     * Whether the viewport looks out of the eye, as the player saw it: not flying, and no clip under
     * the cursor that places the camera. Only then is the game's interface drawn over the picture;
     * the effects, the time tools, the sound and the lens and sway clips leave the eye where it is.
     */
    public static boolean isFirstPerson()
    {
        if (!isActive() || manual != null)
        {
            return false;
        }

        int tick = cursor.getAsInt();

        for (Clip clip : record.clips.get())
        {
            boolean on = clip.enabled.get() && tick >= clip.tick.get() && tick < clip.tick.get() + clip.duration.get();

            if (on && !(clip instanceof FxClip || clip instanceof RecapClips.ToolClip || clip instanceof AudioClip
                || clip instanceof ShakeClip || clip instanceof FovPunchClip))
            {
                return false;
            }
        }

        return true;
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

        if (manual != null)
        {
            manual.apply(camera);
            LAST.set(manual);

            return;
        }

        float time = time();

        if (!this.base(time, this.sample))
        {
            return;
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

        this.position.point.set(this.sample.x, this.sample.y, this.sample.z);
        this.position.angle.set(yaw + 180F, pitch, roll, fov);

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

        this.actor.place(track, RecapClips.sourceTime(record.clips, tick), RecapClips.sourceTime(record.clips, tick + 1));
        CONTEXT.entities.put(RecapClips.PLAYER, this.actor.isPlaced() ? this.actor : this.wrap(mc.player));

        for (PlayerEntity player : mc.world.getPlayers())
        {
            if (player != mc.player)
            {
                CONTEXT.entities.put(player.getName().getString(), this.wrap(player));
            }
        }
    }

    private IEntity wrap(Entity entity)
    {
        return this.wrapped.computeIfAbsent(entity, MCEntity::new);
    }

    /**
     * Draws the recorded player in the world, where and as turned as the recording had it, while
     * the camera is not its own eye: the player's own model, so it looks as they do. Its walk is
     * not recorded, it holds its pose.
     */
    public static void renderActor(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive() || track == null || track.isEmpty() || mc.player == null || isFirstPerson() || !record.isOn(RecapOption.PLAYERS)
            || context.matrixStack() == null || context.consumers() == null)
        {
            return;
        }

        float time = time();

        if (!track.sample(RecapClips.sourceTime(record.clips, time), INSTANCE.sample))
        {
            return;
        }

        RecapTrack.Sample at = INSTANCE.sample;
        net.minecraft.client.network.ClientPlayerEntity player = mc.player;
        net.minecraft.client.render.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3d camera = context.camera().getPos();
        double feet = at.y - player.getStandingEyeHeight();

        /* The turn is read off the entity by its renderer, so it is lent the recorded one and given back */
        float yaw = player.getYaw(), prevYaw = player.prevYaw, pitch = player.getPitch(), prevPitch = player.prevPitch;
        float bodyYaw = player.bodyYaw, prevBodyYaw = player.prevBodyYaw, headYaw = player.headYaw, prevHeadYaw = player.prevHeadYaw;
        boolean shadows = mc.options.getEntityShadows().getValue();

        player.setYaw(at.yaw);
        player.prevYaw = at.yaw;
        player.setPitch(at.pitch);
        player.prevPitch = at.pitch;
        player.bodyYaw = player.prevBodyYaw = at.yaw;
        player.headYaw = player.prevHeadYaw = at.yaw;

        try
        {
            /* Its shadow would be cast where the player really stands */
            dispatcher.setRenderShadows(false);

            int light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y, at.z));

            dispatcher.render(player, at.x - camera.x, feet - camera.y, at.z - camera.z, at.yaw, context.tickDelta(), context.matrixStack(), context.consumers(), light);
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
