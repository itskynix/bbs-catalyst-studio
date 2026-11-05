package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The status effect icons in the top right of the replay's screen, as the game shows them: the
 * beneficial ones in the first row, the harmful ones under them, each blinking out as its time
 * runs short. Each effect keys its own time left (in ticks) and how much of it shows.
 */
public class PovStatusEffectsClip extends PovClip
{
    public final Effects effects = new Effects("effects");

    public PovStatusEffectsClip()
    {
        super();

        this.add(this.effects);
    }

    @Override
    protected Clip create()
    {
        return new PovStatusEffectsClip();
    }

    public static class Effects extends ValueList<Effect>
    {
        public Effects(String id)
        {
            super(id);
        }

        public Effect add(String effect)
        {
            Effect entry = new Effect("");

            entry.effect.set(effect);

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

        /** One place up or down the order the icons are drawn in */
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
        public final ValueString effect = new ValueString("effect", "minecraft:speed");
        /** The paler background of an effect from a beacon */
        public final ValueBoolean ambient = new ValueBoolean("ambient", false);

        public final KeyframeChannel<Double> duration = channel(this, "duration", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Double> alpha = channel(this, "alpha", KeyframeFactories.DOUBLE);

        public Effect(String id)
        {
            super(id);

            this.add(this.effect);
            this.add(this.ambient);
        }
    }
}
