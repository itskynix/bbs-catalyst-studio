package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * A stack of effects from the {@link PostEffect} library, drawn over the frame in order, top to
 * bottom. Each layer names its effect, can be switched off, and has an intensity and the effect's
 * own parameters, every one of them a value that holds still or a keyframe channel that animates it.
 *
 * <p>Only the layers that are there cost anything: an empty stack asks for no pass, and with no pass
 * the renderer copies and draws nothing.</p>
 */
public class PostEffectStack extends ValueList<PostEffectStack.Layer>
{
    /** The most parameters an effect has */
    public static final int PARAMS = 4;

    public PostEffectStack(String id)
    {
        super(id);
    }

    public Layer add(PostEffect effect)
    {
        Layer layer = new Layer("");

        layer.setup(effect);

        this.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.add(layer);
        this.sync();
        this.postNotify(IValueListener.FLAG_UNMERGEABLE);

        return layer;
    }

    public void remove(Layer layer)
    {
        this.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.list.remove(layer);
        this.sync();
        this.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    /** One place up (-1) or down (1) the stack */
    public void move(Layer layer, int direction)
    {
        int index = this.list.indexOf(layer);
        int target = index + direction;

        if (index < 0 || target < 0 || target >= this.list.size())
        {
            return;
        }

        this.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.list.remove(index);
        this.list.add(target, layer);
        this.sync();
        this.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    @Override
    protected Layer create(String id)
    {
        return new Layer(id);
    }

    /** The passes of the layers that are on at {@code t}, in stack order, scaled by {@code factor} */
    public void collect(float t, float factor, List<FxOverlay> out)
    {
        for (Layer layer : this.list)
        {
            if (!layer.enabled.get())
            {
                continue;
            }

            PostEffect effect = PostEffect.get(layer.effect.get());

            if (effect == null)
            {
                continue;
            }

            double intensity = Math.max(0D, Math.min(1D, layer.value(-1, t))) * factor;

            if (intensity <= 0.001D)
            {
                continue;
            }

            FxOverlay overlay = new FxOverlay(FxOverlay.LIBRARY);

            overlay.set(0, effect.mode).set(1, intensity);

            for (int i = 0; i < PARAMS; i++)
            {
                overlay.set(2 + i, i < effect.params.length ? layer.value(i, t) : 0D);
            }

            overlay.rgb(6, effect.hasColor() ? layer.color.get() : 0xffffff);
            overlay.set(9, t / 20D);
            overlay.set(10, effect.depth ? 1D : 0D);

            out.add(overlay);
        }
    }

    /** Slides every layer's keyframes, for a clip cut in two */
    public void moveX(float offset)
    {
        for (Layer layer : this.list)
        {
            for (KeyframeChannel<Double> channel : layer.channels())
            {
                channel.moveX(offset);
            }
        }
    }

    /**
     * One effect of the stack. The intensity and the parameters each have a value that holds while
     * their channel has no keyframes; once keyed, the channel animates them.
     */
    public static class Layer extends ValueGroup
    {
        public final ValueString effect = new ValueString("effect", "");
        public final ValueBoolean enabled = new ValueBoolean("enabled", true);
        public final ValueInt color = new ValueInt("color", 0xffffff);
        /** The post-process volume's property slot this layer reads its keyframable values from; -1 in a clip */
        public final ValueInt slot = new ValueInt("slot", -1);

        public final ValueFloat intensity = new ValueFloat("intensity_value", 1F, 0F, 1F);
        public final ValueFloat[] values = new ValueFloat[PARAMS];

        public final KeyframeChannel<Double> intensityChannel = new KeyframeChannel<>("intensity", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Double>[] paramChannels;

        @SuppressWarnings("unchecked")
        public Layer(String id)
        {
            super(id);

            this.add(this.effect);
            this.add(this.enabled);
            this.add(this.color);
            this.add(this.slot);
            this.add(this.intensity);
            this.add(this.intensityChannel);

            this.paramChannels = new KeyframeChannel[PARAMS];

            for (int i = 0; i < PARAMS; i++)
            {
                this.values[i] = new ValueFloat("p" + (i + 1) + "_value", 0F);
                this.paramChannels[i] = new KeyframeChannel<>("p" + (i + 1), KeyframeFactories.DOUBLE);

                this.add(this.values[i]);
                this.add(this.paramChannels[i]);
            }
        }

        /** A fresh layer of the effect, at its defaults */
        public void setup(PostEffect effect)
        {
            this.effect.set(effect.id);
            this.color.set(effect.hasColor() ? effect.color : 0xffffff);

            for (int i = 0; i < PARAMS; i++)
            {
                this.values[i].set(i < effect.params.length ? effect.params[i].value() : 0F);
            }
        }

        /** The channel of the intensity (-1) or of a parameter (0 to 3) */
        public KeyframeChannel<Double> channel(int index)
        {
            return index < 0 ? this.intensityChannel : this.paramChannels[index];
        }

        /** The intensity (-1) or a parameter (0 to 3) at {@code tick}: keyed or held */
        public double value(int index, float tick)
        {
            KeyframeChannel<Double> channel = this.channel(index);

            if (!channel.isEmpty())
            {
                return channel.interpolate(tick);
            }

            return index < 0 ? this.intensity.get() : this.values[index].get();
        }

        public List<KeyframeChannel<Double>> channels()
        {
            List<KeyframeChannel<Double>> list = new ArrayList<>();

            list.add(this.intensityChannel);

            for (KeyframeChannel<Double> channel : this.paramChannels)
            {
                list.add(channel);
            }

            return list;
        }
    }
}
