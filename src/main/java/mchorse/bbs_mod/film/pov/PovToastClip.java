package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * A toast sliding in at the top right of the replay's screen: an advancement made, a goal reached,
 * a challenge completed, or a plain notice. The title and the description are the preset's unless
 * written; the icon is the item, or a texture in its place. It slides in at the start and out at the
 * end of the clip, unless the slide channel is keyed (0 out of view, 1 all the way in).
 */
public class PovToastClip extends PovClip
{
    public static final int TASK = 0;
    public static final int GOAL = 1;
    public static final int CHALLENGE = 2;
    public static final int NOTICE = 3;

    public final ValueInt preset = new ValueInt("preset", TASK, TASK, NOTICE);
    public final ValueString titleText = new ValueString("toast_title", "");
    public final ValueString description = new ValueString("description", "");
    public final ValueItemStack item = new ValueItemStack("item");
    public final ValueLink texture = new ValueLink("texture", null);

    public final KeyframeChannel<Double> slide = this.channel("slide", KeyframeFactories.DOUBLE);

    public PovToastClip()
    {
        super();

        this.add(this.preset);
        this.add(this.titleText);
        this.add(this.description);
        this.add(this.item);
        this.add(this.texture);
    }

    @Override
    protected Clip create()
    {
        return new PovToastClip();
    }
}
