package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.FakeSpaceForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

import java.util.function.Supplier;

/**
 * The artificial space scene's panel: the dome and the stars, the nebula, the planet with its halo
 * and ring, the galaxy. Every value is a track of the form, so the dope sheet keys any of them; the
 * streak factor is the one made for it, taking the stars from points to streaks of light.
 */
public class UIFakeSpaceFormPanel extends UIFormPanel<FakeSpaceForm>
{
    public UIFakeSpaceFormPanel(UIForm editor)
    {
        super(editor);

        UISection sky = this.section(UIKeys.FORMS_FAKE_SPACE_SKY, "fake_space.sky", true);

        sky.fields.add(
            this.toggle("sky_dome", () -> this.form.skyDome),
            this.color("background_color", () -> this.form.backgroundColor),
            this.row("star_density", () -> this.form.starDensity, 0D, 1D),
            this.row("star_brightness", () -> this.form.starBrightness, 0D, 2D),
            this.row("star_size", () -> this.form.starSize, 0.2D, 3D),
            this.row("star_seed", () -> this.form.starSeed, 0D, 9999D),
            this.row("streak_factor", () -> this.form.streakFactor, 0D, 1D)
        );

        UISection nebula = this.section(UIKeys.FORMS_FAKE_SPACE_NEBULA, "fake_space.nebula", true);

        nebula.fields.add(
            this.row("nebula_amount", () -> this.form.nebulaAmount, 0D, 1D),
            this.row("nebula_scale", () -> this.form.nebulaScale, 0.5D, 4D),
            this.color("nebula_color_a", () -> this.form.nebulaColorA),
            this.color("nebula_color_b", () -> this.form.nebulaColorB)
        );

        UISection planet = this.section(UIKeys.FORMS_FAKE_SPACE_PLANET, "fake_space.planet", true);

        planet.fields.add(
            this.toggle("planet_enabled", () -> this.form.planetEnabled),
            this.row("planet_yaw", () -> this.form.planetYaw, -180D, 180D),
            this.row("planet_pitch", () -> this.form.planetPitch, -90D, 90D),
            this.row("planet_size", () -> this.form.planetSize, 1D, 90D),
            this.row("planet_rotation_speed", () -> this.form.planetRotationSpeed, -360D, 360D),
            this.row("planet_light_yaw", () -> this.form.planetLightYaw, -180D, 180D),
            this.color("planet_color_a", () -> this.form.planetColorA),
            this.color("planet_color_b", () -> this.form.planetColorB),
            this.row("planet_atmosphere", () -> this.form.planetAtmosphere, 0D, 1D),
            this.color("planet_atmosphere_color", () -> this.form.planetAtmosphereColor),
            this.toggle("planet_ring", () -> this.form.planetRing),
            this.row("planet_ring_tilt", () -> this.form.planetRingTilt, -90D, 90D),
            this.color("planet_ring_color", () -> this.form.planetRingColor)
        );

        UISection galaxy = this.section(UIKeys.FORMS_FAKE_SPACE_GALAXY, "fake_space.galaxy", true);

        galaxy.fields.add(
            this.toggle("galaxy_enabled", () -> this.form.galaxyEnabled),
            this.row("galaxy_yaw", () -> this.form.galaxyYaw, -180D, 180D),
            this.row("galaxy_pitch", () -> this.form.galaxyPitch, -90D, 90D),
            this.row("galaxy_size", () -> this.form.galaxySize, 2D, 120D),
            this.row("galaxy_tilt", () -> this.form.galaxyTilt, -90D, 90D),
            this.row("galaxy_rotation_speed", () -> this.form.galaxyRotationSpeed, -360D, 360D),
            this.row("galaxy_brightness", () -> this.form.galaxyBrightness, 0D, 2D),
            this.color("galaxy_core_color", () -> this.form.galaxyCoreColor),
            this.color("galaxy_arm_color", () -> this.form.galaxyArmColor)
        );

        this.options.add(sky, nebula, planet, galaxy);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.fake_space." + id);
    }

    private UIElement row(String id, Supplier<? extends BaseValueNumber<?>> value, double min, double max)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max);
        trackpad.tooltip(L10n.lang("bbs.ui.forms.editors.fake_space." + id + "-tooltip"));

        return UI.labelRow(key(id), trackpad);
    }

    private UIElement toggle(String id, Supplier<ValueBoolean> value)
    {
        UIElement toggle = UIValues.toggle(key(id), value);

        toggle.tooltip(L10n.lang("bbs.ui.forms.editors.fake_space." + id + "-tooltip"));

        return toggle;
    }

    private UIElement color(String id, Supplier<ValueColor> value)
    {
        UIElement color = UIValues.color(value);

        color.tooltip(L10n.lang("bbs.ui.forms.editors.fake_space." + id + "-tooltip"));

        return UI.labelRow(key(id), color);
    }

    @Override
    public void startEdit(FakeSpaceForm form)
    {
        super.startEdit(form);

        this.options.resize();
    }
}
