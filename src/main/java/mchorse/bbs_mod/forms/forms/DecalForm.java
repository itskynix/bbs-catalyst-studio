package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

public class DecalForm extends Form
{
    public static final Icon ICON = Icons.BRUSH;

    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueFloat width = new ValueFloat("width", 1.0F, 0.05F, 64.0F);
    public final ValueFloat height = new ValueFloat("height", 1.0F, 0.05F, 64.0F);
    public final ValueFloat depth = new ValueFloat("depth", 0.5F, 0.05F, 16.0F);
    public final ValueFloat normalThreshold = new ValueFloat("normalThreshold", 60.0F, 0.0F, 90.0F);
    public final ValueBoolean doubleSided = new ValueBoolean("doubleSided", false);
    public final ValueBoolean glow = new ValueBoolean("glow", false);
    public final ValueFloat opacity = new ValueFloat("opacity", 1.0F, 0.0F, 1.0F);
    public final ValueFloat offset = new ValueFloat("offset", 0.002F, 0.0F, 0.1F);

    public DecalForm()
    {
        super();

        this.add(this.texture);
        this.add(this.color);
        this.add(this.width);
        this.add(this.height);
        this.add(this.depth);
        this.add(this.normalThreshold);
        this.add(this.doubleSided);
        this.add(this.glow);
        this.add(this.opacity);
        this.add(this.offset);
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link link = this.texture.get();

        return link == null ? "none" : link.toString();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}

