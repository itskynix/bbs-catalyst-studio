package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.clips.modifiers.ThirdPersonClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.clips.ClipFactory;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.MapFactory;

/**
 * The clips a recap's timeline can hold, and what its time tools make of the timeline.
 *
 * <p><b>The clips.</b> Recap Studio puts the film studio's own clips on its timeline, the same
 * classes with the same ids, so a clip behaves and saves the same in both. Its factory holds only
 * the ones that make sense over a recording: the cameras, the effects and a set of general tools,
 * four of which are its own: speed, timelapse, day time and freeze.</p>
 *
 * <p><b>Time.</b> The tools are read as functions of the timeline tick, summed in order from the
 * start: the moment of the recording shown ({@link #sourceTime}) and the day time
 * ({@link #dayTime}). A scrubbed moment, a played one and a frame of an export land on the same
 * value.</p>
 */
public final class RecapClips
{
    /** Where the timeline shows the player as an entity the tracking clips can follow. */
    public static final String PLAYER = "player";

    public static final String[] CAMERAS = {"idle", "dolly", "path", "keyframe", "pro_camera", "actor_eye", "third_person", "orbit", "fov_punch"};
    public static final String[] EFFECTS = {"cinematic_effects", "color_grade", "compound_fx", "motion_blur", "vignette", "letterbox", "grain", "lens_distortion", "screen_shake", "world_wobble", "screen_shader_graph", "eye"};
    public static final String[] TOOLS = {"tracker", "shake", "recap_speed", "recap_timelapse", "recap_day_time", "recap_freeze", "audio"};

    private static ClipFactory factory;

    private RecapClips()
    {}

    /** The recap timeline's clips: the film studio's own, picked, and the recap's time tools. */
    public static ClipFactory factory()
    {
        if (factory != null)
        {
            return factory;
        }

        MapFactory<Clip, ClipFactoryData> studio = BBSMod.getFactoryCameraClips();
        ClipFactory recap = new ClipFactory();

        for (String[] group : new String[][] {CAMERAS, EFFECTS})
        {
            copy(studio, recap, group);
        }

        copy(studio, recap, new String[] {"tracker", "shake"});

        recap.register(Link.bbs("recap_speed"), SpeedClip.class, new ClipFactoryData(Icons.FRAME_NEXT, 0x3fa7d6));
        recap.register(Link.bbs("recap_timelapse"), TimelapseClip.class, new ClipFactoryData(Icons.STOPWATCH, 0xd6a03f));
        recap.register(Link.bbs("recap_day_time"), DayTimeClip.class, new ClipFactoryData(Icons.TIME, 0xe8c547));
        recap.register(Link.bbs("recap_freeze"), FreezeClip.class, new ClipFactoryData(Icons.PAUSE, 0x7aa3c2));

        copy(studio, recap, new String[] {"audio"});

        factory = recap;

        return factory;
    }

    private static void copy(MapFactory<Clip, ClipFactoryData> from, ClipFactory to, String[] ids)
    {
        /* Before BBS has set its clips up there is nothing to copy: only the recap's own tools */
        if (from == null)
        {
            return;
        }

        for (String id : ids)
        {
            Link link = Link.bbs(id);
            Class<? extends Clip> type = from.getTypeClass(link);

            if (type != null)
            {
                to.register(link, type, from.getData(link));
            }
        }
    }

    public static Clips create()
    {
        return new Clips("clips", factory());
    }

    /** Whether the factory is the recap timeline's, which has its own tabs in the add menu. */
    public static boolean isRecap(mchorse.bbs_mod.utils.factory.IFactory<Clip, ClipFactoryData> factory)
    {
        return factory != null && factory.getKeys().contains(Link.bbs("recap_speed"));
    }

    /**
     * Points the tracking clips put down with no one picked at the player, so they follow someone
     * from the start; true when one was changed.
     */
    public static boolean aimAtPlayer(Clips clips)
    {
        boolean changed = false;

        for (Clip clip : clips.get())
        {
            if (clip instanceof EntityClip entityClip && entityClip.selector.get().isEmpty())
            {
                entityClip.selector.set(PLAYER);
                changed = true;
            }
            else if (clip instanceof ThirdPersonClip thirdPerson && thirdPerson.selector.get().isEmpty())
            {
                thirdPerson.selector.set(PLAYER);
                changed = true;
            }
        }

        return changed;
    }

    /* Time */

    private static boolean active(Clip clip, int tick)
    {
        return clip.enabled.get() && tick >= clip.tick.get() && tick < clip.tick.get() + clip.duration.get();
    }

    /** How fast the recording runs at a tick of the timeline: nought under a freeze, else the top speed clip's. */
    public static float rateAt(Clips clips, int tick)
    {
        float rate = 1F;
        int layer = Integer.MIN_VALUE;

        for (Clip clip : clips.get())
        {
            if (!active(clip, tick))
            {
                continue;
            }

            if (clip instanceof FreezeClip)
            {
                return 0F;
            }

            if (clip instanceof SpeedClip speed && clip.layer.get() >= layer)
            {
                layer = clip.layer.get();
                rate = Math.max(0F, speed.speed.get());
            }
        }

        return rate;
    }

    private static boolean hasTimeClips(Clips clips)
    {
        for (Clip clip : clips.get())
        {
            if (clip.enabled.get() && (clip instanceof SpeedClip || clip instanceof FreezeClip))
            {
                return true;
            }
        }

        return false;
    }

    /** The moment of the recording shown at {@code time} of the timeline. */
    public static float sourceTime(Clips clips, float time)
    {
        if (time <= 0F)
        {
            return 0F;
        }

        if (clips == null || !hasTimeClips(clips))
        {
            return time;
        }

        int whole = (int) Math.floor(time);
        double sum = 0D;

        for (int i = 0; i < whole; i++)
        {
            sum += rateAt(clips, i);
        }

        sum += (time - whole) * rateAt(clips, whole);

        return (float) sum;
    }

    /**
     * The day time at {@code time}, or null when no day time or timelapse clip is on the timeline:
     * the top day time clip under the cursor, or {@code base} without one, plus every timelapse
     * clip's rate for each tick before {@code time}.
     */
    public static Long dayTime(Clips clips, float time, long base)
    {
        if (clips == null)
        {
            return null;
        }

        boolean any = false;
        int tick = (int) Math.floor(Math.max(0F, time));
        double day = base;
        double lapsed = 0D;
        int layer = Integer.MIN_VALUE;

        /* The starting day time and what the timelapses add are summed apart, so the order the
         * clips are listed in never changes the result */
        for (Clip clip : clips.get())
        {
            if (!clip.enabled.get())
            {
                continue;
            }

            if (clip instanceof DayTimeClip dayClip)
            {
                any = true;

                if (active(clip, tick) && clip.layer.get() >= layer)
                {
                    layer = clip.layer.get();
                    day = dayClip.time.get();
                }
            }
            else if (clip instanceof TimelapseClip lapse)
            {
                any = true;

                int from = clip.tick.get();
                int to = Math.min(tick, from + clip.duration.get());

                if (to > from)
                {
                    lapsed += (double) lapse.rate.get() * (to - from);
                }

                if (active(clip, tick))
                {
                    lapsed += lapse.rate.get() * (time - tick);
                }
            }
        }

        day += lapsed;

        return any ? (long) Math.floor(day) : null;
    }

    /* The time tools: they move no camera, the timeline reads them */

    public abstract static class ToolClip extends CameraClip
    {
        @Override
        protected void applyClip(ClipContext context, Position position)
        {}
    }

    /** The recording runs at {@link #speed} times its pace while the clip is on. */
    public static class SpeedClip extends ToolClip
    {
        public final ValueFloat speed = new ValueFloat("speed", 1F, 0F, 16F);

        public SpeedClip()
        {
            this.add(this.speed);
        }

        @Override
        protected Clip create()
        {
            return new SpeedClip();
        }
    }

    /** Day time runs on by {@link #rate} ticks with every tick of the clip. */
    public static class TimelapseClip extends ToolClip
    {
        public final ValueFloat rate = new ValueFloat("rate", 40F, -2000F, 2000F);

        public TimelapseClip()
        {
            this.add(this.rate);
        }

        @Override
        protected Clip create()
        {
            return new TimelapseClip();
        }
    }

    /** The day time is {@link #time} while the clip is on. */
    public static class DayTimeClip extends ToolClip
    {
        public final ValueFloat time = new ValueFloat("day_time", 6000F, 0F, 24000F);

        public DayTimeClip()
        {
            this.add(this.time);
        }

        @Override
        protected Clip create()
        {
            return new DayTimeClip();
        }
    }

    /** The recording holds still while the clip is on; the camera clips over it go on moving. */
    public static class FreezeClip extends ToolClip
    {
        @Override
        protected Clip create()
        {
            return new FreezeClip();
        }
    }
}
