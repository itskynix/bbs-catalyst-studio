package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.clips.modifiers.ThirdPersonClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.clips.ClipFactory;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.MapFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

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
    private static Clips cachedClips;
    private static RecapTimeMap cachedTimeMap = RecapTimeMap.IDENTITY;

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

    /** Returns a compiled {@code O(log K)} time map for {@code clips}, rebuilding only when time clips change. */
    public static synchronized RecapTimeMap timeMap(Clips clips)
    {
        if (clips == null)
        {
            return RecapTimeMap.IDENTITY;
        }

        long stamp = RecapTimeMap.stampOf(clips);

        if (stamp == 0L)
        {
            return RecapTimeMap.IDENTITY;
        }

        if (clips == cachedClips && cachedTimeMap.matches(stamp))
        {
            return cachedTimeMap;
        }

        RecapTimeMap compiled = RecapTimeMap.compile(clips);

        cachedClips = clips;
        cachedTimeMap = compiled;

        return compiled;
    }

    /** How fast the recording runs at a tick of the timeline: nought under a freeze, else the top speed clip's. */
    public static float rateAt(Clips clips, int tick)
    {
        return rateAt(clips, (float) tick);
    }

    /** Instantaneous playback rate at fractional timeline {@code time}. */
    public static float rateAt(Clips clips, float time)
    {
        return timeMap(clips).rateAt(time);
    }

    /** The moment of the recording shown at {@code time} of the timeline, evaluated in {@code O(log K)}. */
    public static float sourceTime(Clips clips, float time)
    {
        if (time <= 0F)
        {
            return 0F;
        }

        return timeMap(clips).sourceTime(time);
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
                    day = dayClip.timeAt(time - dayClip.tick.get());
                }
            }
            else if (clip instanceof TimelapseClip lapse)
            {
                any = true;

                lapsed += lapse.lapsed(time - clip.tick.get());
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

        /**
         * The tool's keyed value over the clip's own ticks, empty until it is keyed: while it is,
         * the plain value holds over the whole clip.
         */
        public KeyframeChannel<Double> keys()
        {
            return null;
        }

        @Override
        protected void breakDownClip(Clip original, int offset)
        {
            super.breakDownClip(original, offset);

            KeyframeChannel<Double> keys = this.keys();

            if (keys != null)
            {
                keys.moveX(-offset);
            }
        }

        protected double keyed(float rel, double fallback, double min, double max)
        {
            KeyframeChannel<Double> keys = this.keys();
            double value = keys == null || keys.isEmpty() ? fallback : keys.interpolate(rel, fallback);

            return Math.max(min, Math.min(max, value));
        }

        /** A number that changes with anything the keys would draw differently, zero without keys. */
        long keysStamp()
        {
            KeyframeChannel<Double> keys = this.keys();

            if (keys == null || keys.isEmpty())
            {
                return 0L;
            }

            long hash = 1125899906842597L;

            for (Keyframe<Double> key : keys.getKeyframes())
            {
                Double value = key.getValue();

                hash = 31L * hash + Float.floatToRawIntBits(key.getTick());
                hash = 31L * hash + Double.doubleToRawLongBits(value == null ? 0D : value);
                hash = 31L * hash + System.identityHashCode(key.getInterpolation().getInterp());
                hash = 31L * hash + Float.floatToRawIntBits(key.lx);
                hash = 31L * hash + Float.floatToRawIntBits(key.ly);
                hash = 31L * hash + Float.floatToRawIntBits(key.rx);
                hash = 31L * hash + Float.floatToRawIntBits(key.ry);
            }

            return hash == 0L ? 1L : hash;
        }
    }

    /** The recording runs at {@link #speed} times its pace while the clip is on, with optional smooth fade. */
    public static class SpeedClip extends ToolClip
    {
        public final ValueFloat speed = new ValueFloat("speed", 1F, 0F, 16F);
        public final ValueInt fade = new ValueInt("fade", 0, 0, 40);
        public final KeyframeChannel<Double> speedKeys = new KeyframeChannel<>("speed_keys", KeyframeFactories.DOUBLE);

        public SpeedClip()
        {
            this.add(this.speed);
            this.add(this.fade);
            this.add(this.speedKeys);
        }

        @Override
        public KeyframeChannel<Double> keys()
        {
            return this.speedKeys;
        }

        public float speedAt(float rel)
        {
            return (float) this.keyed(rel, this.speed.get(), 0D, 16D);
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
        public final KeyframeChannel<Double> rateKeys = new KeyframeChannel<>("rate_keys", KeyframeFactories.DOUBLE);

        /* The day time added up to each tick of the clip, for keyed rates */
        private double[] sums;
        private long sumsStamp;

        public TimelapseClip()
        {
            this.add(this.rate);
            this.add(this.rateKeys);
        }

        @Override
        public KeyframeChannel<Double> keys()
        {
            return this.rateKeys;
        }

        public double rateAt(float rel)
        {
            return this.keyed(rel, this.rate.get(), -2000D, 2000D);
        }

        /** The day time added from the clip's start to {@code rel} ticks into it, held at its end. */
        public double lapsed(float rel)
        {
            int length = Math.max(0, this.duration.get());
            float until = Math.max(0F, Math.min(length, rel));

            if (this.rateKeys.isEmpty())
            {
                return (double) this.rate.get() * until;
            }

            long stamp = 31L * this.keysStamp() + length;

            if (this.sums == null || this.sumsStamp != stamp)
            {
                this.sums = new double[length + 1];

                for (int i = 0; i < length; i++)
                {
                    this.sums[i + 1] = this.sums[i] + this.rateAt(i + 0.5F);
                }

                this.sumsStamp = stamp;
            }

            int whole = (int) until;

            return whole >= length ? this.sums[length] : this.sums[whole] + this.rateAt(whole + 0.5F) * (until - whole);
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
        public final KeyframeChannel<Double> timeKeys = new KeyframeChannel<>("day_time_keys", KeyframeFactories.DOUBLE);

        public DayTimeClip()
        {
            this.add(this.time);
            this.add(this.timeKeys);
        }

        @Override
        public KeyframeChannel<Double> keys()
        {
            return this.timeKeys;
        }

        public double timeAt(float rel)
        {
            return this.keyed(rel, this.time.get(), 0D, 24000D);
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
        public final ValueInt fade = new ValueInt("fade", 0, 0, 40);

        public FreezeClip()
        {
            this.add(this.fade);
        }

        @Override
        protected Clip create()
        {
            return new FreezeClip();
        }
    }
}
