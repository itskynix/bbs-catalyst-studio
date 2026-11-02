package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class FramebufferForm extends Form
{
    public static final Icon ICON = Icons.CAMERA;

    public static final int TARGET_NONE = 0;
    public static final int TARGET_CAMERA = 1;
    public static final int TARGET_ACTOR = 2;

    public final ValueInt width = new ValueInt("width", 512);
    public final ValueInt height = new ValueInt("height", 512);
    public final ValueFloat scale = new ValueFloat("scale", 0.5F);

    /* Autonomous Gaze Tracking & Micro-Saccades */
    public final ValueBoolean glow = new ValueBoolean("glow", false);
    public final ValueFloat inset = new ValueFloat("inset", 0.005F, -0.5F, 0.5F);
    public final ValueInt targetType = new ValueInt("targetType", TARGET_NONE, 0, 2);
    public final ValueString targetActor = new ValueString("targetActor", "");
    public final ValueFloat saccadeFrequency = new ValueFloat("saccadeFrequency", 1.5F, 0F, 20F);
    public final ValueFloat saccadeAmplitude = new ValueFloat("saccadeAmplitude", 0.15F, 0F, 2F);
    public final ValueFloat maxLookOffset = new ValueFloat("maxLookOffset", 0.45F, 0.05F, 1.5F);

    public FramebufferForm()
    {
        this.width.invisible();
        this.height.invisible();

        this.add(this.width);
        this.add(this.height);
        this.add(this.scale);
        this.add(this.glow);
        this.add(this.inset);
        this.add(this.targetType);
        this.add(this.targetActor);
        this.add(this.saccadeFrequency);
        this.add(this.saccadeAmplitude);
        this.add(this.maxLookOffset);
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}