package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * One end of a two-way portal. Two portal forms of the same {@link #frequency} are linked: what is
 * seen through one is the scene as seen from the other, and whoever walks in at one comes out of the
 * other, momentum kept. At most two portals may share a frequency.
 *
 * <p>The window faces along the form's +Z axis (the arrow shown with the frame): that is the side it
 * is entered from, and the direction whoever comes out of it is thrown in.</p>
 */
public class PortalForm extends Form
{
    public static final Icon ICON = Icons.FRUSTUM;

    public static final int SHAPE_RECTANGLE = 0;
    public static final int SHAPE_CIRCLE = 1;

    public final ValueFloat width = new ValueFloat("width", 2F, 0F, 1000F);
    public final ValueFloat height = new ValueFloat("height", 3F, 0F, 1000F);
    /** A rectangle, or the ellipse that fits in it (a circle when both sides are equal). */
    public final ValueInt shape = new ValueInt("shape", SHAPE_RECTANGLE, 0, 1);
    /** The pairing: the two portals of one frequency are each other's other end. */
    public final ValueInt frequency = new ValueInt("frequency", 1, 1, 99);
    /** The window's outline and the arrow along its facing, for placing it; off, the window shows only its view. */
    public final ValueBoolean showFrame = new ValueBoolean("showFrame", false);
    public final ValueColor frameColor = new ValueColor("frameColor", new Color(1F, 1F, 1F, 0.8F));
    /**
     * The resolution of what is seen through the window, in percent of the screen's: 10 is a handful of
     * large pixels and cheap, 100 the screen's own, 250 two and a half times it, averaged down.
     */
    public final ValueFloat qualityScale = new ValueFloat("qualityScale", 100F, 10F, 250F);
    /**
     * A true mirror: the window shows what stands in front of it, reflected in its own plane. A mirror
     * has no other end - it takes no place on its frequency and nobody goes through it.
     */
    public final ValueBoolean mirror = new ValueBoolean("mirror", false);

    public PortalForm()
    {
        super();

        this.add(this.width);
        this.add(this.height);
        this.add(this.shape);
        this.add(this.frequency);
        this.add(this.showFrame);
        this.add(this.frameColor);
        this.add(this.qualityScale);
        this.add(this.mirror);
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}
