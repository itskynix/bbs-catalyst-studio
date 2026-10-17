package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * A clip that does nothing to the camera and instead asks for full-screen passes over the finished
 * frame, the way {@code ImageClip} asks for images. The clip fills in {@link FxOverlay}s; the client
 * draws them, so nothing here knows about OpenGL.
 *
 * <p>Its animated parameters are keyframe channels. A channel with no keyframes reads as its
 * fallback, so a clip that was never keyframed still has a sensible look.</p>
 */
public abstract class FxClip extends CameraClip
{
    public static final String KEY = "fx";

    /** A keyframe channel together with what it reads as while it has no keyframes. */
    public static class Channel
    {
        public final String id;
        public final double fallback;
        public final KeyframeChannel<Double> channel;

        private Channel(String id, double fallback)
        {
            this.id = id;
            this.fallback = fallback;
            this.channel = new KeyframeChannel<>(id, KeyframeFactories.DOUBLE);
        }

        public double get(float tick)
        {
            return this.channel.isEmpty() ? this.fallback : this.channel.interpolate(tick);
        }
    }

    /** The channels in the order the keyframe editor lists them. */
    public final List<Channel> fxChannels = new ArrayList<>();

    public static List<FxOverlay> getOverlays(ClipContext context)
    {
        return context.clipData.get(KEY, ArrayList::new);
    }

    protected Channel channel(String id, double fallback)
    {
        Channel channel = new Channel(id, fallback);

        this.fxChannels.add(channel);
        this.add(channel.channel);

        return channel;
    }

    /** Add the passes this clip wants at {@code tick} (clip-relative) to {@code out}, all scaled by {@code factor}. */
    protected abstract void collect(float tick, float factor, List<FxOverlay> out);

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        float tick = context.relativeTick + context.transition;
        float factor = this.envelope.factorEnabled(this.duration.get(), tick);

        if (factor <= 0F)
        {
            return;
        }

        this.collect(tick, factor, getOverlays(context));
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        for (Channel channel : this.fxChannels)
        {
            channel.channel.moveX(-offset);
        }
    }
}
