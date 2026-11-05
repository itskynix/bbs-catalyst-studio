package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A clip on a replay's POV action timeline: something laid over what the replay sees (an open
 * interface, a boss bar, a toast, the chat, a screen effect...) or done to its view (a shake,
 * particles in front of the eyes). The data only; the client draws it, while a Point of View clip
 * looks out of the replay and lets it through.
 *
 * <p>Keyframe channels are in the clip's own time: tick 0 is the clip's first tick.</p>
 */
public abstract class PovClip extends Clip
{
    /** Add a keyframe channel to a group; the clip's own or one of its entries' */
    public static <T> KeyframeChannel<T> channel(ValueGroup group, String id, IKeyframeFactory<T> factory)
    {
        KeyframeChannel<T> channel = new KeyframeChannel<>(id, factory);

        group.add(channel);

        return channel;
    }

    /** The keyframe channels right inside a group, in the order they were added */
    public static List<KeyframeChannel<?>> channelsOf(ValueGroup group)
    {
        List<KeyframeChannel<?>> channels = new ArrayList<>();

        for (BaseValue value : group.getAll())
        {
            if (value instanceof KeyframeChannel<?> channel)
            {
                channels.add(channel);
            }
        }

        return channels;
    }

    public static double value(KeyframeChannel<Double> channel, float tick, double fallback)
    {
        return channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);
    }

    public static boolean flag(KeyframeChannel<Boolean> channel, float tick, boolean fallback)
    {
        return channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);
    }

    public static <T> T keyed(KeyframeChannel<T> channel, float tick, T fallback)
    {
        return channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);
    }

    protected <T> KeyframeChannel<T> channel(String id, IKeyframeFactory<T> factory)
    {
        return channel(this, id, factory);
    }

    public List<KeyframeChannel<?>> getChannels()
    {
        return channelsOf(this);
    }
}
