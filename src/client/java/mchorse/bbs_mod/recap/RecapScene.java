package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

import java.util.function.IntSupplier;

/**
 * What Recap Studio puts on the world while its viewport shows a recap: the recap's camera at the
 * cursor, reworked by its timeline tracks, and the recap's view settings, the scene ones hiding
 * parts of the world and the overrides replacing its time, weather, fog and lens.
 *
 * <p>All of it is off unless a recap is being shown ({@link #isActive}), so the game and the other
 * editors never see it.</p>
 *
 * <p><b>Time.</b> The timeline time is {@code cursor + transition}; while the recap is held still
 * the transition is left out, so a scrubbed moment looks the same in every frame. The recorded
 * camera is read at the moment the speed and freeze tracks map that time to
 * ({@link RecapTracks#sourceTime}).</p>
 *
 * <p><b>Camera.</b> The recorded eye, or the player's own eye for a recap kept before the camera
 * was, is the base. The camera track puts the camera elsewhere, the orbit track circles the base
 * point, the entity track turns the view onto an entity, and then the lens and the sway are laid
 * on: the tracks first, the view settings where a track is empty.</p>
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

    private final RecapTrack.Sample sample = new RecapTrack.Sample();

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
     * Whether the viewport looks out of the eye, as the player saw it: no camera, orbit or entity
     * track moving it. Only then is the game's interface drawn over the picture.
     */
    public static boolean isFirstPerson()
    {
        return isActive() && !record.tracks.movesCamera(time());
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

        Long tracked = record.tracks.dayTime(time(), on(RecapOption.TIME_OF_DAY) ? record.getValue(RecapOption.TIME_OF_DAY) : baseDayTime);

        if (tracked != null)
        {
            return tracked;
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

    /* Camera */

    @Override
    public int getPriority()
    {
        return 100400;
    }

    /**
     * Where the camera is at timeline {@code time}, before the controller hands it over: also what a
     * new keyframe on the camera and orbit tracks starts from.
     */
    public boolean base(float time, RecapTrack.Sample out)
    {
        if (!isActive())
        {
            return false;
        }

        float source = record.tracks.sourceTime(time);

        if (track != null && track.sample(source, out))
        {
            return true;
        }

        /* A recap kept before its camera was: the player's eye, as it is now */
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

        if (!this.base(time, this.sample))
        {
            return;
        }

        RecapTracks tracks = record.tracks;
        double x = this.sample.x;
        double y = this.sample.y;
        double z = this.sample.z;
        float yaw = this.sample.yaw;
        float pitch = this.sample.pitch;
        float roll = on(RecapOption.CAMERA_ROLL) ? record.getValue(RecapOption.CAMERA_ROLL) : 0F;

        Transform placed = tracks.camera(time);

        if (placed != null)
        {
            x = placed.translate.x;
            y = placed.translate.y;
            z = placed.translate.z;
            pitch = MathUtils.toDeg(placed.rotate.x);
            yaw = MathUtils.toDeg(placed.rotate.y);
            roll = MathUtils.toDeg(placed.rotate.z);
        }

        /* Circling the point the camera would be at: yaw, pitch, distance and height */
        Vector4f orbit = tracks.orbit(time);

        if (orbit != null)
        {
            Vec3d look = Vec3d.fromPolar(orbit.y, orbit.x);
            double cy = y + orbit.w;

            x = x - look.x * orbit.z;
            y = cy - look.y * orbit.z;
            z = z - look.z * orbit.z;
            yaw = orbit.x;
            pitch = orbit.y;
        }

        String followed = tracks.trackedEntity(time);
        Entity target = followed == null ? null : findEntity(followed, x, y, z);

        if (target != null)
        {
            Vec3d at = target.getLerpedPos(transition).add(0, target.getStandingEyeHeight(), 0);
            double dx = at.x - x;
            double dy = at.y - y;
            double dz = at.z - z;

            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }

        Float trackFov = tracks.number(RecapTrackKind.FOV, time);
        float fov = trackFov != null ? trackFov : (on(RecapOption.FOV) ? record.getValue(RecapOption.FOV) : this.sample.fov);

        /* A hand held sway made of the time alone, so it is the same in every frame of the moment */
        Float trackShake = tracks.number(RecapTrackKind.CAMERA_SHAKE, time);
        float shake = trackShake != null ? trackShake : (on(RecapOption.CAMERA_SHAKE) ? record.getValue(RecapOption.CAMERA_SHAKE) : 0F);

        if (shake > 0F)
        {
            float amount = shake / 100F * 1.6F;

            yaw += amount * (float) (Math.sin(time * 0.37F) + 0.5F * Math.sin(time * 0.91F + 1.3F));
            pitch += amount * (float) (Math.sin(time * 0.29F + 2.1F) + 0.5F * Math.sin(time * 1.13F));
        }

        camera.position.set(x, y, z);
        camera.rotation.set(MathUtils.toRad(pitch), MathUtils.toRad(yaw) + MathUtils.PI, MathUtils.toRad(roll));
        camera.setFov(Math.max(1F, Math.min(170F, fov)));
    }

    /** The entity a name, a type or an id names, the nearest to the camera when there are several. */
    private static Entity findEntity(String name, double x, double y, double z)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            return null;
        }

        Entity best = null;
        double distance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities())
        {
            boolean matches = name.equalsIgnoreCase(entity.getName().getString())
                || name.equalsIgnoreCase(entity.getUuidAsString())
                || name.equalsIgnoreCase(net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString());

            if (!matches)
            {
                continue;
            }

            double d = entity.squaredDistanceTo(x, y, z);

            if (d < distance)
            {
                distance = d;
                best = entity;
            }
        }

        return best;
    }
}
