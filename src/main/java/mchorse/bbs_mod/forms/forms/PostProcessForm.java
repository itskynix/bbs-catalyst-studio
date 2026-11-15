package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.camera.clips.fx.PostEffectStack;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * A post-process volume, the way an engine's is: a box in the world, from
 * {@code [-width/2, 0, -length/2]} to {@code [width/2, height, length/2]} of the form, with a stack of
 * effects from the post-process library that is laid over the frame while the camera is inside. Past
 * the box the effects fade out over the blend radius; an unbound volume covers the whole world.
 *
 * <p>The stack's layers keep what effect they are, whether they are on and their colour. What a
 * replay keyframes - each layer's intensity and parameters - lives in {@link #SLOTS} slots of
 * top-level properties, because only top-level properties are tracks. A layer holds on to its slot,
 * so moving it up or down the stack takes its keyframes along, and only the slots in use (and only
 * the parameters their effect has) are visible, so the dope sheet lists nothing for effects that
 * are not in the stack.</p>
 */
public class PostProcessForm extends Form
{
    public static final Icon ICON = Icons.SPARKLES;

    /** How many effects one volume stacks */
    public static final int SLOTS = 8;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Post-Process Volume";

    /** A slot track's name from the effect id and the parameter (-1 for the intensity); the client puts the translated one in */
    public static BiFunction<String, Integer, String> trackNameSupplier = (effect, param) -> effect + (param < 0 ? " intensity" : " p" + (param + 1));

    public final ValueFloat width = new ValueFloat("width", 10F, 0.5F, 512F);
    public final ValueFloat length = new ValueFloat("length", 10F, 0.5F, 512F);
    public final ValueFloat height = new ValueFloat("height", 10F, 0.5F, 512F);
    public final ValueFloat blendRadius = new ValueFloat("blend_radius", 2F, 0F, 64F);
    public final ValueBoolean unbound = new ValueBoolean("unbound", false);
    public final ValueInt priority = new ValueInt("priority", 0, -1000, 1000);
    public final ValueFloat intensity = new ValueFloat("intensity", 1F, 0F, 1F);
    public final ValueBoolean showGuides = new ValueBoolean("show_guides", true);

    public final PostEffectStack stack = new PostEffectStack("effect_stack");

    public final SlotValue[] slotIntensity = new SlotValue[SLOTS];
    public final SlotValue[][] slotParams = new SlotValue[SLOTS][PostEffectStack.PARAMS];

    public PostProcessForm()
    {
        super();

        this.add(this.width);
        this.add(this.length);
        this.add(this.height);
        this.add(this.blendRadius);
        this.add(this.unbound);
        this.add(this.priority);
        this.add(this.intensity);
        this.add(this.showGuides);
        this.add(this.stack);

        for (int s = 0; s < SLOTS; s++)
        {
            this.slotIntensity[s] = new SlotValue(this, "fx" + s + "_intensity", s, -1, 1F);
            this.add(this.slotIntensity[s]);

            for (int p = 0; p < PostEffectStack.PARAMS; p++)
            {
                this.slotParams[s][p] = new SlotValue(this, "fx" + s + "_p" + (p + 1), s, p, 0F);
                this.add(this.slotParams[s][p]);
            }
        }
    }

    /** The layer that holds a slot, or null when the slot is free */
    public PostEffectStack.Layer layerInSlot(int slot)
    {
        for (PostEffectStack.Layer layer : this.stack.getAllTyped())
        {
            if (layer.slot.get() == slot)
            {
                return layer;
            }
        }

        return null;
    }

    /** Adds an effect at its defaults in the first free slot; null when every slot is taken */
    public PostEffectStack.Layer addEffect(PostEffect effect)
    {
        int free = -1;

        for (int s = 0; s < SLOTS && free < 0; s++)
        {
            if (this.layerInSlot(s) == null)
            {
                free = s;
            }
        }

        if (free < 0)
        {
            return null;
        }

        this.slotIntensity[free].set(1F);

        for (int p = 0; p < PostEffectStack.PARAMS; p++)
        {
            this.slotParams[free][p].set(p < effect.params.length ? effect.params[p].value() : 0F);
        }

        PostEffectStack.Layer layer = this.stack.add(effect);

        layer.slot.set(free);

        return layer;
    }

    /** What the dope sheet calls a slot's track: the effect in it, and which of its values */
    public String slotTrackName(SlotValue value)
    {
        PostEffectStack.Layer layer = this.layerInSlot(value.slot);

        return layer == null ? value.getId() : trackNameSupplier.apply(layer.effect.get(), value.param);
    }

    @Override
    public String getTrackName(String property)
    {
        String leaf = property.substring(property.lastIndexOf('/') + 1);
        BaseValueSlot slot = this.parseSlot(leaf);

        if (slot != null)
        {
            PostEffectStack.Layer layer = this.layerInSlot(slot.slot);

            if (layer != null)
            {
                String name = trackNameSupplier.apply(layer.effect.get(), slot.param);
                String owner = this.getTrackLabel();

                return owner.isEmpty() ? name : owner + "/" + name;
            }
        }

        return super.getTrackName(property);
    }

    private record BaseValueSlot(int slot, int param) {}

    private BaseValueSlot parseSlot(String id)
    {
        if (!id.startsWith("fx") || id.indexOf('_') < 3)
        {
            return null;
        }

        try
        {
            int slot = Integer.parseInt(id.substring(2, id.indexOf('_')));
            String rest = id.substring(id.indexOf('_') + 1);

            if (rest.equals("intensity"))
            {
                return new BaseValueSlot(slot, -1);
            }

            if (rest.startsWith("p"))
            {
                return new BaseValueSlot(slot, Integer.parseInt(rest.substring(1)) - 1);
            }
        }
        catch (NumberFormatException e)
        {}

        return null;
    }

    @Override
    public String getDefaultDisplayName()
    {
        return displayNameSupplier.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

    /**
     * A keyframable value of one slot: the intensity (param -1) or one parameter of the effect the
     * slot's layer is. It is a track only while it means something - its slot holds a layer, and
     * that layer's effect has this parameter.
     */
    public static class SlotValue extends ValueFloat
    {
        private final PostProcessForm form;
        public final int slot;
        public final int param;

        public SlotValue(PostProcessForm form, String id, int slot, int param, float value)
        {
            super(id, value);

            this.form = form;
            this.slot = slot;
            this.param = param;
        }

        @Override
        public boolean isVisible()
        {
            if (!super.isVisible())
            {
                return false;
            }

            PostEffectStack.Layer layer = this.form.layerInSlot(this.slot);

            if (layer == null)
            {
                return false;
            }

            if (this.param < 0)
            {
                return true;
            }

            PostEffect effect = PostEffect.get(layer.effect.get());

            return effect != null && this.param < effect.params.length;
        }
    }
}
