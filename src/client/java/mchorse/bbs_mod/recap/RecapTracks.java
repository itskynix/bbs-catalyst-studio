package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The timeline tracks of one recap: a keyframe channel for each {@link RecapTrackKind}, the order
 * the timeline lists them in and which are hidden from it. It is part of the recap, so it is saved
 * with it and every edit is a step of the editor's history.
 *
 * <p>Everything read from it is a function of the timeline time alone, {@code ticks + transition}:
 * the time the recording is at ({@link #sourceTime}) and the day time ({@link #dayTime}) are sums
 * over the ticks before it, taken in order from the start, so a scrubbed moment, a played one and
 * a frame of an export land on the same value.</p>
 */
public class RecapTracks
{
    private final Map<RecapTrackKind, KeyframeChannel<?>> channels = new EnumMap<>(RecapTrackKind.class);
    private final List<RecapTrackKind> order = new ArrayList<>();
    private final Set<RecapTrackKind> hidden = EnumSet.noneOf(RecapTrackKind.class);

    public RecapTracks()
    {
        for (RecapTrackKind kind : RecapTrackKind.values())
        {
            this.channels.put(kind, new KeyframeChannel<>(kind.id, kind.factory));
            this.order.add(kind);
        }
    }

    /* Structure */

    @SuppressWarnings("unchecked")
    public <T> KeyframeChannel<T> get(RecapTrackKind kind)
    {
        return (KeyframeChannel<T>) this.channels.get(kind);
    }

    public List<RecapTrackKind> getOrder()
    {
        return this.order;
    }

    public boolean isHidden(RecapTrackKind kind)
    {
        return this.hidden.contains(kind);
    }

    public void setHidden(RecapTrackKind kind, boolean hide)
    {
        if (hide)
        {
            this.hidden.add(kind);
        }
        else
        {
            this.hidden.remove(kind);
        }
    }

    /** The tracks the timeline shows, in its order. */
    public List<RecapTrackKind> getShown()
    {
        List<RecapTrackKind> shown = new ArrayList<>();

        for (RecapTrackKind kind : this.order)
        {
            if (!this.hidden.contains(kind))
            {
                shown.add(kind);
            }
        }

        return shown;
    }

    /** Moves a track up (-1) or down (+1) the list; false when it is already at that end. */
    public boolean move(RecapTrackKind kind, int direction)
    {
        int index = this.order.indexOf(kind);
        int to = index + direction;

        if (index < 0 || to < 0 || to >= this.order.size())
        {
            return false;
        }

        this.order.remove(index);
        this.order.add(to, kind);

        return true;
    }

    public boolean isEmpty(RecapTrackKind kind)
    {
        return this.channels.get(kind).isEmpty();
    }

    /* Reading */

    /** How fast the recording runs at tick {@code tick} of the timeline: nought while frozen. */
    public float rateAt(float tick)
    {
        KeyframeChannel<Boolean> freeze = this.get(RecapTrackKind.FREEZE);

        if (!freeze.isEmpty() && Boolean.TRUE.equals(freeze.interpolate(tick)))
        {
            return 0F;
        }

        KeyframeChannel<?> speed = this.channels.get(RecapTrackKind.SPEED);

        return speed.isEmpty() ? 1F : Math.max(0F, (float) speed.interpolateDouble(tick));
    }

    /**
     * The moment of the recording shown at {@code time} of the timeline: the time itself while
     * speed and freeze are empty, otherwise the speed summed tick by tick from the start.
     */
    public float sourceTime(float time)
    {
        if (time <= 0F)
        {
            return 0F;
        }

        if (this.isEmpty(RecapTrackKind.SPEED) && this.isEmpty(RecapTrackKind.FREEZE))
        {
            return time;
        }

        int whole = (int) Math.floor(time);
        double sum = 0D;

        for (int i = 0; i < whole; i++)
        {
            sum += this.rateAt(i);
        }

        sum += (time - whole) * this.rateAt(whole);

        return (float) sum;
    }

    /**
     * The day time at {@code time}, or null when neither the time of day track nor the timelapse
     * track has keyframes. The time of day track (or {@code base} without one) is where it starts,
     * and the timelapse adds its rate for every tick before {@code time}.
     */
    public Long dayTime(float time, long base)
    {
        boolean hasDay = !this.isEmpty(RecapTrackKind.TIME_OF_DAY);
        boolean hasLapse = !this.isEmpty(RecapTrackKind.TIMELAPSE);

        if (!hasDay && !hasLapse)
        {
            return null;
        }

        double day = hasDay ? this.channels.get(RecapTrackKind.TIME_OF_DAY).interpolateDouble(time) : base;

        if (hasLapse)
        {
            KeyframeChannel<?> lapse = this.channels.get(RecapTrackKind.TIMELAPSE);
            int whole = (int) Math.floor(Math.max(0F, time));

            for (int i = 0; i < whole; i++)
            {
                day += lapse.interpolateDouble(i);
            }

            day += (time - whole) * lapse.interpolateDouble(whole);
        }

        return (long) Math.floor(day);
    }

    /** The value of a number track at {@code time}, or null when it has no keyframes. */
    public Float number(RecapTrackKind kind, float time)
    {
        KeyframeChannel<?> channel = this.channels.get(kind);

        return channel.isEmpty() ? null : (float) channel.interpolateDouble(time);
    }

    public Transform camera(float time)
    {
        KeyframeChannel<Transform> channel = this.get(RecapTrackKind.CAMERA);

        return channel.isEmpty() ? null : channel.interpolate(time);
    }

    public Vector4f orbit(float time)
    {
        KeyframeChannel<Vector4f> channel = this.get(RecapTrackKind.CAMERA_ORBIT);

        return channel.isEmpty() ? null : channel.interpolate(time);
    }

    /** The entity the camera looks at, or null; a string track holds its value up to the next one. */
    public String trackedEntity(float time)
    {
        KeyframeChannel<String> channel = this.get(RecapTrackKind.TRACK_ENTITY);

        if (channel.isEmpty())
        {
            return null;
        }

        String name = channel.interpolate(time);

        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    /** Whether something other than the recorded eye places the camera at {@code time}. */
    public boolean movesCamera(float time)
    {
        return !this.isEmpty(RecapTrackKind.CAMERA) || !this.isEmpty(RecapTrackKind.CAMERA_ORBIT) || this.trackedEntity(time) != null;
    }

    /* Data */

    public MapType toData()
    {
        MapType data = new MapType();
        MapType channels = new MapType();
        ListType order = new ListType();
        ListType hidden = new ListType();

        for (RecapTrackKind kind : RecapTrackKind.values())
        {
            channels.put(kind.id, this.channels.get(kind).toData());
        }

        for (RecapTrackKind kind : this.order)
        {
            order.addString(kind.id);
        }

        for (RecapTrackKind kind : RecapTrackKind.values())
        {
            if (this.hidden.contains(kind))
            {
                hidden.addString(kind.id);
            }
        }

        data.put("channels", channels);
        data.put("order", order);
        data.put("hidden", hidden);

        return data;
    }

    /** Reads the tracks into the channels already here, so a timeline showing them stays bound. */
    public void fromData(MapType data)
    {
        MapType channels = data == null ? null : data.getMap("channels");

        for (RecapTrackKind kind : RecapTrackKind.values())
        {
            KeyframeChannel<?> channel = this.channels.get(kind);
            BaseType saved = channels == null ? null : channels.get(kind.id);

            if (saved != null && saved.isMap())
            {
                channel.fromData(saved);
            }
            else
            {
                channel.removeAll();
            }
        }

        this.order.clear();
        this.hidden.clear();

        if (data != null)
        {
            for (BaseType entry : data.getList("order"))
            {
                RecapTrackKind kind = entry.isString() ? RecapTrackKind.byId(entry.asString()) : null;

                if (kind != null && !this.order.contains(kind))
                {
                    this.order.add(kind);
                }
            }

            for (BaseType entry : data.getList("hidden"))
            {
                RecapTrackKind kind = entry.isString() ? RecapTrackKind.byId(entry.asString()) : null;

                if (kind != null)
                {
                    this.hidden.add(kind);
                }
            }
        }

        /* A track added after the recap was saved goes at the end */
        for (RecapTrackKind kind : RecapTrackKind.values())
        {
            if (!this.order.contains(kind))
            {
                this.order.add(kind);
            }
        }
    }
}
