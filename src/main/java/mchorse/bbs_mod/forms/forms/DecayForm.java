package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Supplier;

/**
 * Procedural decay: cracks, rust, moss or a stain that spreads over a surface as the progress climbs.
 *
 * <p>Two ways to use it. {@link #MODE_SURFACE}: a box in the world, and the blocks' own surfaces inside
 * it - walls, floors, ceilings, stairs and slabs - wear the pattern, growing outwards from the centre of
 * the box. {@link #MODE_OBJECT}: the form is a part of another form (a model, a block, an item), and the
 * pattern is laid over a shell of the box round that form, moving with it.</p>
 *
 * <p>The numbers that change during a shot - how far the decay has spread, how sharp its edge is, the
 * scale of the pattern, the opacity - are top-level properties on purpose: only top-level properties
 * are tracks, so the dope sheet keys them. The mode, the preset and the seed are a setup, not a track.</p>
 */
public class DecayForm extends Form
{
    public static final Icon ICON = Icons.BUCKET;

    public static final int MODE_SURFACE = 0;
    public static final int MODE_OBJECT = 1;

    public static final int PRESET_CRACKS = 0;
    public static final int PRESET_RUST = 1;
    public static final int PRESET_MOSS = 2;
    public static final int PRESET_STAIN = 3;
    public static final int PRESET_CUSTOM = 4;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Decay";

    public final ValueInt mode = new ValueInt("mode", MODE_SURFACE, MODE_SURFACE, MODE_OBJECT);
    public final ValueInt preset = new ValueInt("preset", PRESET_CRACKS, PRESET_CRACKS, PRESET_CUSTOM);
    /** The picture the Custom Texture preset lays over the mask */
    public final ValueLink texture = new ValueLink("texture", null);

    public final ValueFloat decayProgress = new ValueFloat("decay_progress", 0.5F, 0F, 1F);
    public final ValueFloat edgeSoftness = new ValueFloat("edge_softness", 0.12F, 0.01F, 0.5F);
    public final ValueFloat noiseScale = new ValueFloat("noise_scale", 2.5F, 0.2F, 10F);
    public final ValueFloat opacity = new ValueFloat("opacity", 0.95F, 0F, 1F);

    public final ValueFloat width = new ValueFloat("width", 3F, 0.2F, 64F);
    public final ValueFloat length = new ValueFloat("length", 3F, 0.2F, 64F);
    public final ValueFloat height = new ValueFloat("height", 3F, 0.2F, 64F);

    public final ValueColor primaryColor = new ValueColor("primary_color", new Color(0.075F, 0.065F, 0.058F, 1F));
    public final ValueColor edgeColor = new ValueColor("edge_color", new Color(0.33F, 0.30F, 0.25F, 1F));

    public final ValueInt seed = new ValueInt("seed", 1, 0, 100000);
    /** How dark the cracks go towards their middle, the fake depth */
    public final ValueFloat depthInset = new ValueFloat("depth_inset", 0F, 0F, 0.2F);

    public final ValueBoolean showGuides = new ValueBoolean("show_guides", true);

    public DecayForm()
    {
        super();

        this.mode.invisible();
        this.preset.invisible();
        this.seed.invisible();

        this.add(this.mode);
        this.add(this.preset);
        this.add(this.texture);
        this.add(this.decayProgress);
        this.add(this.edgeSoftness);
        this.add(this.noiseScale);
        this.add(this.opacity);
        this.add(this.width);
        this.add(this.length);
        this.add(this.height);
        this.add(this.primaryColor);
        this.add(this.edgeColor);
        this.add(this.seed);
        this.add(this.depthInset);
        this.add(this.showGuides);
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

    /** The picture of the Custom Texture preset, or null when none is picked */
    public Link getCustomTexture()
    {
        return this.texture.get();
    }
}
