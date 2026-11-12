package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Supplier;

/**
 * Height fog: a volume of fog the IRLights-patched shader pack marches. The box is the form's
 * {@code [-width/2, 0, -length/2]} to {@code [width/2, height, length/2]}; inside it the fog is at full
 * density up to the base height (above the form) and thins above it over the falloff height. The
 * noise breaks it into drifting puffs, carried along x by the wind.
 */
public class HeightFogForm extends Form
{
    public static final Icon ICON = Icons.FILTER;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Volumetric Fog";

    public final ValueColor color = new ValueColor("color", new Color(0.72F, 0.78F, 0.82F, 1.0F));
    public final ValueFloat baseHeight = new ValueFloat("baseHeight", 0.0F, -128.0F, 320.0F);
    public final ValueFloat density = new ValueFloat("density", 0.5F, 0.0F, 1.0F);
    public final ValueFloat falloff = new ValueFloat("falloff", 3.0F, 0.1F, 64.0F);
    public final ValueFloat width = new ValueFloat("width", 24.0F, 1.0F, 256.0F);
    public final ValueFloat length = new ValueFloat("length", 24.0F, 1.0F, 256.0F);
    public final ValueFloat height = new ValueFloat("height", 10.0F, 1.0F, 128.0F);
    public final ValueBoolean followCamera = new ValueBoolean("followCamera", false);

    /* The fog's motion: how much the density varies, how big the puffs are (higher = smaller and more
     * of them), and how fast the wind carries them */
    public final ValueFloat noiseIntensity = new ValueFloat("noise_intensity", 0.3F, 0.0F, 1.0F);
    public final ValueFloat noiseScale = new ValueFloat("noise_scale", 1.0F, 0.1F, 5.0F);
    public final ValueFloat windSpeed = new ValueFloat("wind_speed", 0.5F, 0.0F, 5.0F);

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
        this.add(this.followCamera);
        this.add(this.noiseIntensity);
        this.add(this.noiseScale);
        this.add(this.windSpeed);
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
