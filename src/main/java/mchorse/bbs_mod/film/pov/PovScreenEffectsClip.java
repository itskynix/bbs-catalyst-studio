package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Screen effects of the replay's point of view, a list of them, each keyed on its own: the
 * vignette and the pumpkin's blur (environment and vision), the powder snow freeze and the nausea
 * sway (hazards), the totem of undying springing up (status and pop-ups). Not to be mixed up with
 * the camera's effect clips: these are what the game draws over the player's own view.
 */
public class PovScreenEffectsClip extends PovClip
{
    public static final List<String> VISION = Collections.unmodifiableList(Arrays.asList("vignette", "pumpkin"));
    public static final List<String> HAZARDS = Collections.unmodifiableList(Arrays.asList("freeze", "nausea"));
    public static final List<String> POPUPS = Collections.unmodifiableList(Arrays.asList("totem"));

    public final Effects effects = new Effects("effects");

    public PovScreenEffectsClip()
    {
        super();

        this.add(this.effects);
    }

    @Override
    protected Clip create()
    {
        return new PovScreenEffectsClip();
    }

    /** The channels an effect of a type is driven by; the others it has are left alone */
    public static List<String> channelsFor(String type)
    {
        return switch (type)
        {
            case "vignette" -> Arrays.asList("opacity", "color");
            case "totem" -> Arrays.asList("progress", "particles");
            case "freeze" -> Arrays.asList("opacity", "progress");
            case "nausea" -> Arrays.asList("strength", "speed");
            default -> Collections.singletonList("opacity");
        };
    }

    public static class Effects extends ValueList<Effect>
    {
        public Effects(String id)
        {
            super(id);
        }

        public Effect add(String type)
        {
            Effect entry = new Effect("");

            entry.type.set(type);

            this.preNotify();
            this.add(entry);
            this.sync();
            this.postNotify();

            return entry;
        }

        public void remove(Effect entry)
        {
            this.preNotify(IValueListener.FLAG_UNMERGEABLE);
            this.list.remove(entry);
            this.sync();
            this.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        /** One place up or down the order the effects are drawn in */
        public void move(Effect entry, int direction)
        {
            int index = this.list.indexOf(entry);
            int target = index + direction;

            if (index < 0 || target < 0 || target >= this.list.size())
            {
                return;
            }

            this.preNotify(IValueListener.FLAG_UNMERGEABLE);
            this.list.remove(index);
            this.list.add(target, entry);
            this.sync();
            this.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        @Override
        protected Effect create(String id)
        {
            return new Effect(id);
        }
    }

    public static class Effect extends ValueGroup
    {
        public final ValueString type = new ValueString("type", "vignette");
        /** What springs up for the totem; empty, the totem of undying */
        public final ValueItemStack item = new ValueItemStack("item");

        public final KeyframeChannel<Double> opacity = channel(this, "opacity", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Color> color = channel(this, "color", KeyframeFactories.COLOR);
        /** The totem's spring, 0 to 1; the freeze's setting in, 0 to 1 */
        public final KeyframeChannel<Double> progress = channel(this, "progress", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Boolean> leftHand = channel(this, "left_hand", KeyframeFactories.BOOLEAN);
        public final KeyframeChannel<Boolean> particles = channel(this, "particles", KeyframeFactories.BOOLEAN);
        /** The nausea's sway: how strong, and how fast */
        public final KeyframeChannel<Double> strength = channel(this, "strength", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Double> speed = channel(this, "speed", KeyframeFactories.DOUBLE);

        public Effect(String id)
        {
            super(id);

            this.add(this.type);
            this.add(this.item);
        }
    }
}
