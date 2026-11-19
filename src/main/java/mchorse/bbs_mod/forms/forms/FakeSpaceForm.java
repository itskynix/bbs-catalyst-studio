package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Supplier;

/**
 * The artificial space scene: a procedural deep space drawn around the camera - a sky dome with a
 * star field and nebula colours, a planet (with a halo and a ring if asked) and a spiral galaxy. None
 * of it exists in the world: no blocks, no collision, no physics, just a picture that sits at an
 * infinite distance, which is what a space shot needs and what building it out of blocks could never
 * give.
 *
 * <p>Everything is a top-level property, so everything can be keyed in the dope sheet - above all the
 * streak factor, which stretches the stars from points into streaks of light for a Big Rip or warp
 * acceleration. The sky is turned by turning the form: the stars and the layers keep their places on it.</p>
 */
public class FakeSpaceForm extends Form
{
    public static final Icon ICON = Icons.FIVE_STAR;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Artificial Space Scene";

    /* The dome and the stars */
    public final ValueBoolean skyDome = new ValueBoolean("sky_dome", true);
    public final ValueColor backgroundColor = new ValueColor("background_color", new Color(0.008F, 0.012F, 0.03F, 1F));
    public final ValueFloat starDensity = new ValueFloat("star_density", 0.6F, 0F, 1F);
    public final ValueFloat starBrightness = new ValueFloat("star_brightness", 1F, 0F, 2F);
    public final ValueFloat starSize = new ValueFloat("star_size", 1F, 0.2F, 3F);
    public final ValueInt starSeed = new ValueInt("star_seed", 1, 0, 9999);
    /** 0 is points, 1 is long streaks of light away from the form's front - the Big Rip and warp stretch */
    public final ValueFloat streakFactor = new ValueFloat("streak_factor", 0F, 0F, 1F);

    /* The nebula colours across the sky */
    public final ValueFloat nebulaAmount = new ValueFloat("nebula_amount", 0.5F, 0F, 1F);
    public final ValueColor nebulaColorA = new ValueColor("nebula_color_a", new Color(0.23F, 0.10F, 0.42F, 1F));
    public final ValueColor nebulaColorB = new ValueColor("nebula_color_b", new Color(0.06F, 0.35F, 0.48F, 1F));
    public final ValueFloat nebulaScale = new ValueFloat("nebula_scale", 1.5F, 0.5F, 4F);

    /* The planet, with its halo and ring; sizes are angles across the sky in degrees */
    public final ValueBoolean planetEnabled = new ValueBoolean("planet_enabled", true);
    public final ValueFloat planetYaw = new ValueFloat("planet_yaw", 30F, -180F, 180F);
    public final ValueFloat planetPitch = new ValueFloat("planet_pitch", 15F, -90F, 90F);
    public final ValueFloat planetSize = new ValueFloat("planet_size", 22F, 1F, 90F);
    public final ValueColor planetColorA = new ValueColor("planet_color_a", new Color(0.18F, 0.42F, 0.75F, 1F));
    public final ValueColor planetColorB = new ValueColor("planet_color_b", new Color(0.85F, 0.79F, 0.63F, 1F));
    public final ValueFloat planetRotationSpeed = new ValueFloat("planet_rotation_speed", 6F, -360F, 360F);
    public final ValueFloat planetLightYaw = new ValueFloat("planet_light_yaw", -60F, -180F, 180F);
    public final ValueFloat planetAtmosphere = new ValueFloat("planet_atmosphere", 0.6F, 0F, 1F);
    public final ValueColor planetAtmosphereColor = new ValueColor("planet_atmosphere_color", new Color(0.49F, 0.78F, 1F, 1F));
    public final ValueBoolean planetRing = new ValueBoolean("planet_ring", false);
    public final ValueColor planetRingColor = new ValueColor("planet_ring_color", new Color(0.80F, 0.73F, 0.60F, 1F));
    public final ValueFloat planetRingTilt = new ValueFloat("planet_ring_tilt", 20F, -90F, 90F);

    /* The spiral galaxy */
    public final ValueBoolean galaxyEnabled = new ValueBoolean("galaxy_enabled", true);
    public final ValueFloat galaxyYaw = new ValueFloat("galaxy_yaw", -110F, -180F, 180F);
    public final ValueFloat galaxyPitch = new ValueFloat("galaxy_pitch", 25F, -90F, 90F);
    public final ValueFloat galaxySize = new ValueFloat("galaxy_size", 32F, 2F, 120F);
    public final ValueFloat galaxyTilt = new ValueFloat("galaxy_tilt", 55F, -90F, 90F);
    public final ValueFloat galaxyRotationSpeed = new ValueFloat("galaxy_rotation_speed", 2F, -360F, 360F);
    public final ValueFloat galaxyBrightness = new ValueFloat("galaxy_brightness", 1F, 0F, 2F);
    public final ValueColor galaxyCoreColor = new ValueColor("galaxy_core_color", new Color(1F, 0.88F, 0.66F, 1F));
    public final ValueColor galaxyArmColor = new ValueColor("galaxy_arm_color", new Color(0.50F, 0.61F, 1F, 1F));

    public FakeSpaceForm()
    {
        super();

        this.starSeed.invisible();

        this.add(this.skyDome);
        this.add(this.backgroundColor);
        this.add(this.starDensity);
        this.add(this.starBrightness);
        this.add(this.starSize);
        this.add(this.starSeed);
        this.add(this.streakFactor);
        this.add(this.nebulaAmount);
        this.add(this.nebulaColorA);
        this.add(this.nebulaColorB);
        this.add(this.nebulaScale);
        this.add(this.planetEnabled);
        this.add(this.planetYaw);
        this.add(this.planetPitch);
        this.add(this.planetSize);
        this.add(this.planetColorA);
        this.add(this.planetColorB);
        this.add(this.planetRotationSpeed);
        this.add(this.planetLightYaw);
        this.add(this.planetAtmosphere);
        this.add(this.planetAtmosphereColor);
        this.add(this.planetRing);
        this.add(this.planetRingColor);
        this.add(this.planetRingTilt);
        this.add(this.galaxyEnabled);
        this.add(this.galaxyYaw);
        this.add(this.galaxyPitch);
        this.add(this.galaxySize);
        this.add(this.galaxyTilt);
        this.add(this.galaxyRotationSpeed);
        this.add(this.galaxyBrightness);
        this.add(this.galaxyCoreColor);
        this.add(this.galaxyArmColor);
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
