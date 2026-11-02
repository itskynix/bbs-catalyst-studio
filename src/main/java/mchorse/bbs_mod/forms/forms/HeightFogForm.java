package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

public class HeightFogForm extends Form
{
    public static final Icon ICON = Icons.FILTER;

    public final ValueColor color = new ValueColor("color", new Color(0.72F, 0.78F, 0.82F, 1.0F));
    public final ValueFloat baseHeight = new ValueFloat("baseHeight", 0.0F, -128.0F, 320.0F);
    public final ValueFloat density = new ValueFloat("density", 0.5F, 0.0F, 1.0F);
    public final ValueFloat falloff = new ValueFloat("falloff", 3.0F, 0.1F, 64.0F);
    public final ValueFloat width = new ValueFloat("width", 24.0F, 1.0F, 256.0F);
    public final ValueFloat length = new ValueFloat("length", 24.0F, 1.0F, 256.0F);
    public final ValueFloat height = new ValueFloat("height", 10.0F, 1.0F, 128.0F);
    public final ValueInt slices = new ValueInt("slices", 24, 4, 64);
    public final ValueBoolean followCamera = new ValueBoolean("followCamera", false);

    public HeightFogForm()
    {
        super();

        this.add(this.color);
        this.add(this.baseHeight);
        this.add(this.density);
        this.add(this.falloff);
        this.add(this.width);
        this.add(this.length);
        this.add(this.height);
        this.add(this.slices);
        this.add(this.followCamera);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return "height_fog";
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}

