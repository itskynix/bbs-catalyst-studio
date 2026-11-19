package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Supplier;

/**
 * A navigation volume, the way an engine's nav mesh bounds volume is: a box in the world, from
 * {@code [-width/2, 0, -length/2]} to {@code [width/2, height, length/2]} of the form, inside which the
 * world's blocks are scanned for where an agent of a given size can walk. Characters with AI in the film
 * find their way about on what it finds.
 *
 * <p>Everything here is a setup, none of it a track: the mesh is a property of the place, not of a
 * moment. The box is not turned or scaled with the form; put the form where the volume should be and
 * leave its own offset and turning alone.</p>
 */
public class NavigationVolumeForm extends Form
{
    public static final Icon ICON = Icons.FULLSCREEN;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Navigation Volume";

    public final ValueFloat width = new ValueFloat("width", 16F, 1F, 128F);
    public final ValueFloat length = new ValueFloat("length", 16F, 1F, 128F);
    public final ValueFloat height = new ValueFloat("height", 8F, 1F, 64F);

    /** How wide the agent is: half of it is the clearance kept from walls and drops, in blocks */
    public final ValueFloat agentRadius = new ValueFloat("agent_radius", 0.3F, 0.1F, 1.5F);
    /** How tall the agent is: a ceiling lower than this is an obstacle */
    public final ValueFloat agentHeight = new ValueFloat("agent_height", 1.8F, 0.5F, 4F);
    /** The tallest step the agent climbs or drops: a slab is 0.5, a stair or a block 1 */
    public final ValueFloat maxStepHeight = new ValueFloat("max_step_height", 1F, 0.1F, 1.25F);

    /** Whether the mesh is scanned again every couple of seconds, so changes to the blocks show up */
    public final ValueBoolean autoUpdate = new ValueBoolean("auto_update", true);
    /** Raised by the rebuild button; a different number is a mesh scanned afresh */
    public final ValueInt rebuild = new ValueInt("rebuild", 0, 0, Integer.MAX_VALUE);

    /* The preview, shown with the P key */
    public final ValueBoolean showBorders = new ValueBoolean("show_borders", true);
    public final ValueFloat previewOpacity = new ValueFloat("preview_opacity", 0.4F, 0.1F, 1F);
    public final ValueBoolean showGuides = new ValueBoolean("show_guides", true);

    public NavigationVolumeForm()
    {
        super();

        for (var value : new mchorse.bbs_mod.settings.values.base.BaseValue[] {this.width, this.length, this.height, this.agentRadius, this.agentHeight,
            this.maxStepHeight, this.autoUpdate, this.rebuild, this.showBorders, this.previewOpacity, this.showGuides})
        {
            value.invisible();
            this.add(value);
        }
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
}
