package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * The burning-away and the breaking-apart that model, block, item, image and extruded forms share:
 * six properties, saved with the form and animatable like any other, that cost nothing at their
 * defaults. A form creates one and hands itself over, which puts the values in its own property list.
 */
public class FormFxValues
{
    public static final int DISSOLVE_LINEAR = 0;
    public static final int DISSOLVE_PERLIN = 1;
    public static final int DISSOLVE_LUMA = 2;

    /** 0 leaves the form whole, 1 has burnt all of it away. */
    public final ValueFloat dissolveProgress = new ValueFloat("dissolveProgress", 0F, 0F, 1F);
    public final ValueInt dissolveMode = new ValueInt("dissolveMode", DISSOLVE_PERLIN, 0, 2);
    /** Width of the glowing edge where the burning has just reached, as a share of the whole run. 0 has none. */
    public final ValueFloat dissolveEdge = new ValueFloat("dissolveEdge", 0.1F, 0F, 0.5F);
    public final ValueColor dissolveBurnColor = new ValueColor("dissolveBurnColor", new Color(1F, 0.48F, 0.1F));

    /** 0 leaves the form whole, 1 has all of it flown apart and gone. */
    public final ValueFloat shatterProgress = new ValueFloat("shatterProgress", 0F, 0F, 1F);
    /** How far, in form units, the pieces fly by the end. */
    public final ValueFloat shatterSpread = new ValueFloat("shatterSpread", 4F, 0F, 20F);

    public FormFxValues(Form form)
    {
        form.add(this.dissolveProgress);
        form.add(this.dissolveMode);
        form.add(this.dissolveEdge);
        form.add(this.dissolveBurnColor);
        form.add(this.shatterProgress);
        form.add(this.shatterSpread);
    }

    public boolean isDissolving()
    {
        return this.dissolveProgress.get() > 0F;
    }

    public boolean isShattering()
    {
        return this.shatterProgress.get() > 0F;
    }
}
