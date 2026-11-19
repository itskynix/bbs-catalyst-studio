package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Supplier;

/**
 * The artificial space scene: deep space that costs no blocks and has no collision.
 *
 * <p>Two parts. The <b>sky</b> - the deep space colour, the nebula and the star field - is drawn
 * around the camera at a distance nothing reaches, so it reads as infinitely far and never moves as
 * the camera does. The <b>scene</b> - the planets, their rings, the asteroid belt and the galaxy - is
 * real space around the form: placed in blocks relative to it, so the camera, an actor or a ship can
 * fly round a planet, over it, through its ring or between the asteroids.</p>
 *
 * <p>The planets are a list, each with its place, size, tilt, spin, colours or texture with its UV,
 * atmosphere and ring. The other values are top-level properties, so the dope sheet keys them - the
 * streak factor above all, which stretches the stars into streaks of light for a Big Rip or a warp.</p>
 */
public class FakeSpaceForm extends Form
{
    public static final Icon ICON = Icons.FIVE_STAR;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Artificial Space Scene";

    /* The sky: the dome and the stars */
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

    /* The light of the scene: where the sun is, around the horizon and above it */
    public final ValueFloat lightYaw = new ValueFloat("planet_light_yaw", -60F, -180F, 180F);
    public final ValueFloat lightPitch = new ValueFloat("light_pitch", 15F, -90F, 90F);

    /** The planets */
    public final Planets planets = new Planets("planets");

    /* The spiral galaxy: where it is in blocks from the form, how big, which way its disc faces */
    public final ValueBoolean galaxyEnabled = new ValueBoolean("galaxy_enabled", true);
    public final ValueFloat galaxyX = new ValueFloat("galaxy_x", 120F, -4096F, 4096F);
    public final ValueFloat galaxyY = new ValueFloat("galaxy_y", 140F, -4096F, 4096F);
    public final ValueFloat galaxyZ = new ValueFloat("galaxy_z", -260F, -4096F, 4096F);
    public final ValueFloat galaxySize = new ValueFloat("galaxy_size", 90F, 2F, 2048F);
    public final ValueFloat galaxyYaw = new ValueFloat("galaxy_yaw", 30F, -180F, 180F);
    public final ValueFloat galaxyTilt = new ValueFloat("galaxy_tilt", 55F, -90F, 90F);
    public final ValueFloat galaxyRotationSpeed = new ValueFloat("galaxy_rotation_speed", 2F, -360F, 360F);
    public final ValueFloat galaxyBrightness = new ValueFloat("galaxy_brightness", 1F, 0F, 2F);
    public final ValueColor galaxyCoreColor = new ValueColor("galaxy_core_color", new Color(1F, 0.88F, 0.66F, 1F));
    public final ValueColor galaxyArmColor = new ValueColor("galaxy_arm_color", new Color(0.50F, 0.61F, 1F, 1F));

    /* The asteroid belt: a ring of tumbling rocks round the form */
    public final ValueInt asteroidCount = new ValueInt("asteroid_count", 80, 0, 300);
    public final ValueFloat asteroidRadius = new ValueFloat("asteroid_radius", 45F, 2F, 1024F);
    public final ValueFloat asteroidSpeed = new ValueFloat("asteroid_speed", 2F, -60F, 60F);
    public final ValueFloat asteroidSize = new ValueFloat("asteroid_size", 1.4F, 0.1F, 32F);
    public final ValueColor asteroidColor = new ValueColor("asteroid_color", new Color(0.42F, 0.39F, 0.36F, 1F));

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
        this.add(this.lightYaw);
        this.add(this.lightPitch);
        this.add(this.planets);
        this.add(this.galaxyEnabled);
        this.add(this.galaxyX);
        this.add(this.galaxyY);
        this.add(this.galaxyZ);
        this.add(this.galaxySize);
        this.add(this.galaxyYaw);
        this.add(this.galaxyTilt);
        this.add(this.galaxyRotationSpeed);
        this.add(this.galaxyBrightness);
        this.add(this.galaxyCoreColor);
        this.add(this.galaxyArmColor);
        this.add(this.asteroidCount);
        this.add(this.asteroidRadius);
        this.add(this.asteroidSpeed);
        this.add(this.asteroidSize);
        this.add(this.asteroidColor);

        /* A fresh scene starts with a blue world and a ringed giant */
        Planet blue = this.planets.addPlanet();

        blue.name.set("Blue");
        blue.z.set(90F);
        blue.y.set(10F);
        blue.radius.set(24F);

        Planet giant = this.planets.addPlanet();

        giant.name.set("Giant");
        giant.x.set(-110F);
        giant.y.set(30F);
        giant.z.set(-70F);
        giant.radius.set(18F);
        giant.tilt.set(18F);
        giant.colorA.set(new Color(0.78F, 0.62F, 0.42F, 1F));
        giant.colorB.set(new Color(0.93F, 0.86F, 0.70F, 1F));
        giant.atmosphere.set(0.25F);
        giant.ring.set(true);
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        /* A scene saved before the planets were a list had one planet on the sky; it is put into the scene where it was seen */
        if (data instanceof MapType map && map.has("planet_enabled") && !map.has("planets"))
        {
            this.planets.clearPlanets();

            if (map.getBool("planet_enabled", true))
            {
                Planet planet = this.planets.addPlanet();
                double yaw = Math.toRadians(map.getFloat("planet_yaw", 30F));
                double pitch = Math.toRadians(map.getFloat("planet_pitch", 15F));
                double size = Math.toRadians(Math.max(1F, map.getFloat("planet_size", 22F)) / 2F);
                float distance = 120F;

                planet.x.set((float) (Math.sin(yaw) * Math.cos(pitch) * distance));
                planet.y.set((float) (Math.sin(pitch) * distance));
                planet.z.set((float) (Math.cos(yaw) * Math.cos(pitch) * distance));
                planet.radius.set((float) Math.max(0.5D, Math.tan(size) * distance));
                planet.rotationSpeed.set(map.getFloat("planet_rotation_speed", 6F));
                planet.atmosphere.set(map.getFloat("planet_atmosphere", 0.6F));
                planet.ring.set(map.getBool("planet_ring", false));

                for (String id : new String[] {"planet_color_a", "planet_color_b", "planet_atmosphere_color", "planet_ring_color"})
                {
                    if (map.has(id))
                    {
                        String target = id.equals("planet_color_a") ? "color_a" : id.equals("planet_color_b") ? "color_b" : id.equals("planet_atmosphere_color") ? "atmosphere_color" : "ring_color";
                        mchorse.bbs_mod.settings.values.base.BaseValue value = planet.get(target);

                        if (value != null)
                        {
                            value.fromData(map.get(id));
                        }
                    }
                }
            }
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

    /** The planets of the scene */
    public static class Planets extends ValueList<Planet>
    {
        public Planets(String id)
        {
            super(id);
        }

        /** A new planet at its defaults, at the end of the list */
        public Planet addPlanet()
        {
            Planet planet = new Planet(String.valueOf(this.list.size()));

            this.preNotify(IValueListener.FLAG_UNMERGEABLE);
            this.add(planet);
            this.sync();
            this.postNotify(IValueListener.FLAG_UNMERGEABLE);

            return planet;
        }

        /** A copy of a planet, nudged aside so both can be seen, right after it */
        public Planet duplicate(Planet planet)
        {
            Planet copy = new Planet("");

            copy.fromData(planet.toData());
            copy.x.set(copy.x.get() + copy.radius.get() * 2.5F);
            copy.name.set(planet.name.get().isEmpty() ? "" : planet.name.get() + " 2");

            int index = this.list.indexOf(planet);

            this.preNotify(IValueListener.FLAG_UNMERGEABLE);
            this.list.add(index < 0 ? this.list.size() : index + 1, copy);
            this.sync();
            this.postNotify(IValueListener.FLAG_UNMERGEABLE);

            return copy;
        }

        public void remove(Planet planet)
        {
            this.preNotify(IValueListener.FLAG_UNMERGEABLE);
            this.list.remove(planet);
            this.sync();
            this.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        public void clearPlanets()
        {
            this.list.clear();
            this.sync();
        }

        /** Always written, so a scene whose planets were all taken away does not get the starting ones back */
        @Override
        public boolean isDefault()
        {
            return false;
        }

        @Override
        protected Planet create(String id)
        {
            return new Planet(id);
        }
    }

    /**
     * One planet: where it is in blocks from the form, its radius in blocks, how its axis leans and how
     * fast it turns about it, its surface - two procedural colours, or a texture laid on with an offset
     * and a scale - its atmosphere, and its ring, whose sizes are times the planet's radius.
     */
    public static class Planet extends ValueGroup
    {
        public final ValueString name = new ValueString("name", "");
        public final ValueFloat x = new ValueFloat("x", 0F, -4096F, 4096F);
        public final ValueFloat y = new ValueFloat("y", 0F, -4096F, 4096F);
        public final ValueFloat z = new ValueFloat("z", 60F, -4096F, 4096F);
        public final ValueFloat radius = new ValueFloat("radius", 16F, 0.5F, 1024F);
        public final ValueFloat tilt = new ValueFloat("tilt", 10F, -90F, 90F);
        public final ValueFloat rotationSpeed = new ValueFloat("rotation_speed", 6F, -360F, 360F);

        public final ValueColor colorA = new ValueColor("color_a", new Color(0.18F, 0.42F, 0.75F, 1F));
        public final ValueColor colorB = new ValueColor("color_b", new Color(0.85F, 0.79F, 0.63F, 1F));
        public final ValueLink texture = new ValueLink("texture", null);
        public final ValueFloat uvOffsetX = new ValueFloat("uv_offset_x", 0F, -2F, 2F);
        public final ValueFloat uvOffsetY = new ValueFloat("uv_offset_y", 0F, -2F, 2F);
        public final ValueFloat uvScaleX = new ValueFloat("uv_scale_x", 1F, 0.1F, 8F);
        public final ValueFloat uvScaleY = new ValueFloat("uv_scale_y", 1F, 0.1F, 8F);

        public final ValueFloat atmosphere = new ValueFloat("atmosphere", 0.6F, 0F, 1F);
        public final ValueColor atmosphereColor = new ValueColor("atmosphere_color", new Color(0.49F, 0.78F, 1F, 1F));

        public final ValueBoolean ring = new ValueBoolean("ring", false);
        public final ValueFloat ringInner = new ValueFloat("ring_inner", 1.35F, 1.01F, 6F);
        public final ValueFloat ringOuter = new ValueFloat("ring_outer", 2.3F, 1.05F, 10F);
        public final ValueColor ringColor = new ValueColor("ring_color", new Color(0.80F, 0.73F, 0.60F, 1F));
        public final ValueLink ringTexture = new ValueLink("ring_texture", null);

        public Planet(String id)
        {
            super(id);

            this.add(this.name);
            this.add(this.x);
            this.add(this.y);
            this.add(this.z);
            this.add(this.radius);
            this.add(this.tilt);
            this.add(this.rotationSpeed);
            this.add(this.colorA);
            this.add(this.colorB);
            this.add(this.texture);
            this.add(this.uvOffsetX);
            this.add(this.uvOffsetY);
            this.add(this.uvScaleX);
            this.add(this.uvScaleY);
            this.add(this.atmosphere);
            this.add(this.atmosphereColor);
            this.add(this.ring);
            this.add(this.ringInner);
            this.add(this.ringOuter);
            this.add(this.ringColor);
            this.add(this.ringTexture);
        }
    }
}
