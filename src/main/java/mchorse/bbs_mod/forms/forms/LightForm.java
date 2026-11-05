package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * A light placed in the scene: a point light, a spot light or a directional light, as a form, so it
 * stands in a film as a replay, follows a model as a body part and is animated in the dope sheet.
 *
 * <p>Its colour, intensity, radius and cone angle are properties like any other (the
 * {@code light_*} tracks). The form draws nothing the film shows; it hands its light to the
 * client's {@code LightManager} every frame, which is what a lighting back end (irlight, once it
 * is brought in) reads.</p>
 */
public class LightForm extends Form
{
    public static final Icon ICON = Icons.SUN;

    public static final int POINT = 0;
    public static final int SPOT = 1;
    public static final int DIRECTIONAL = 2;

    /** Point, spot or directional */
    public final ValueInt lightType = new ValueInt("light_type", POINT, 0, 2);
    public final ValueColor color = new ValueColor("light_color", new Color(1F, 0.95F, 0.85F, 1F));
    /** How bright; 1 is a torch's worth */
    public final ValueFloat intensity = new ValueFloat("light_intensity", 1F, 0F, 16F);
    /** How far it reaches, in blocks; a directional light has no reach */
    public final ValueFloat radius = new ValueFloat("light_radius", 8F, 0.1F, 128F);
    /** The full opening of a spot light's cone, in degrees */
    public final ValueFloat spotAngle = new ValueFloat("light_spot_angle", 45F, 1F, 170F);
    /** A small marker where the light stands, in the editor only (never in playback, F1 or an export) */
    public final ValueBoolean marker = new ValueBoolean("light_marker", true);

    public LightForm()
    {
        this.marker.invisible();

        this.add(this.lightType);
        this.add(this.color);
        this.add(this.intensity);
        this.add(this.radius);
        this.add(this.spotAngle);
        this.add(this.marker);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return switch (this.lightType.get())
        {
            case SPOT -> "Spot Light";
            case DIRECTIONAL -> "Directional Light";
            default -> "Point Light";
        };
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}
