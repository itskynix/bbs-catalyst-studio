package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.function.IntSupplier;

/**
 * What Recap Studio puts on the world while its viewport shows a recap: the recap's camera at the
 * cursor and the recap's view settings, the scene ones hiding parts of the world and the overrides
 * replacing its time, weather, fog and lens.
 *
 * <p>All of it is off unless a recap is being shown ({@link #isActive}), so the game and the other
 * editors never see it. The camera comes from the recap's {@link RecapTrack} at
 * {@code cursor + transition}; while the recap is held still the transition is left out, so a
 * scrubbed moment looks the same in every frame.</p>
 */
public final class RecapScene implements ICameraController
{
    public static final RecapScene INSTANCE = new RecapScene();

    private static boolean active;
    private static RecapRecord record;
    private static RecapTrack track;
    private static IntSupplier cursor = () -> 0;
    private static boolean playing;

    private final RecapTrack.Sample sample = new RecapTrack.Sample();

    private RecapScene()
    {}

    public static void show(RecapRecord shown, RecapTrack camera, IntSupplier at)
    {
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

    public static void setPlaying(boolean isPlaying)
    {
        playing = isPlaying;
    }

    public static boolean isActive()
    {
        return active && record != null && MinecraftClient.getInstance().world != null;
    }

    /** Whether the recap has a camera to look through. */
    public static boolean hasCamera()
    {
        return isActive() && track != null && !track.isEmpty();
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

    /* Overrides */

    public static Long timeOfDay()
    {
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

    @Override
    public void setup(Camera camera, float transition)
    {
        if (!hasCamera())
        {
            return;
        }

        float time = cursor.getAsInt() + (playing ? Math.max(0F, Math.min(1F, transition)) : 0F);

        if (!track.sample(time, this.sample))
        {
            return;
        }

        float yaw = this.sample.yaw;
        float pitch = this.sample.pitch;
        float roll = on(RecapOption.CAMERA_ROLL) ? record.getValue(RecapOption.CAMERA_ROLL) : 0F;
        float fov = on(RecapOption.FOV) ? record.getValue(RecapOption.FOV) : this.sample.fov;

        /* A hand held sway made of the time alone, so it is the same in every frame of the moment */
        if (on(RecapOption.CAMERA_SHAKE))
        {
            float amount = record.getValue(RecapOption.CAMERA_SHAKE) / 100F * 1.6F;

            yaw += amount * (float) (Math.sin(time * 0.37F) + 0.5F * Math.sin(time * 0.91F + 1.3F));
            pitch += amount * (float) (Math.sin(time * 0.29F + 2.1F) + 0.5F * Math.sin(time * 1.13F));
        }

        camera.position.set(this.sample.x, this.sample.y, this.sample.z);
        camera.rotation.set(MathUtils.toRad(pitch), MathUtils.toRad(yaw) + MathUtils.PI, MathUtils.toRad(roll));
        camera.setFov(fov);
    }
}
