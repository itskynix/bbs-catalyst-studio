package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.FakeSpaceForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.pov.UIPovEntryList;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The artificial space scene's panel: the sky and the stars, the nebula, the light, the planets (a list,
 * each opened in the planet editor), the asteroid belt and the galaxy.
 */
public class UIFakeSpaceFormPanel extends UIFormPanel<FakeSpaceForm>
{
    private final UIPovEntryList<FakeSpaceForm.Planet> planets;

    public UIFakeSpaceFormPanel(UIForm editor)
    {
        super(editor);

        /* Where the scene is, which way it is turned and how big: the planets, the belt and the galaxy all move with it */
        mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform transform = new mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform().withAnchor().callbacks(() -> this.form.transform).barBackground();

        transform.enableHotkeys();
        transform.valueBinding(() -> transform.setTransform(this.form == null ? null : this.form.transform.get()));

        UISection place = this.section(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRANSFORM, "fake_space.transform", true);

        place.fields.add(transform);

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

        UISection nebula = this.section(UIKeys.FORMS_FAKE_SPACE_NEBULA, "fake_space.nebula", false);

        nebula.fields.add(
            this.row("nebula_amount", () -> this.form.nebulaAmount, 0D, 1D),
            this.row("nebula_scale", () -> this.form.nebulaScale, 0.5D, 4D),
            this.color("nebula_color_a", () -> this.form.nebulaColorA),
            this.color("nebula_color_b", () -> this.form.nebulaColorB)
        );

        /* The planets: the list, and what is done to the picked one */
        this.planets = new UIPovEntryList<>(this::planetLabel, (l) -> {});
        this.planets.background();
        this.planets.h(16 * 5);

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addPlanet());
        UIIcon copy = new UIIcon(Icons.COPY, (b) -> this.copyPlanet());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removePlanet());
        UIButton edit = new UIButton(UIKeys.FAKE_SPACE_PLANET_EDIT, (b) -> this.editPlanet());

        add.tooltip(UIKeys.FAKE_SPACE_PLANET_ADD);
        copy.tooltip(UIKeys.FAKE_SPACE_PLANET_COPY);
        remove.tooltip(UIKeys.FAKE_SPACE_PLANET_REMOVE);
        edit.tooltip(UIKeys.FAKE_SPACE_PLANET_EDIT_TOOLTIP);
        this.planets.context((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.FAKE_SPACE_PLANET_ADD, this::addPlanet);

            if (this.picked() != null)
            {
                menu.action(Icons.EDIT, UIKeys.FAKE_SPACE_PLANET_EDIT, this::editPlanet);
                menu.action(Icons.COPY, UIKeys.FAKE_SPACE_PLANET_COPY, this::copyPlanet);
                menu.action(Icons.REMOVE, UIKeys.FAKE_SPACE_PLANET_REMOVE, this::removePlanet);
            }
        });

        UISection planet = this.section(UIKeys.FORMS_FAKE_SPACE_PLANET, "fake_space.planet", true);

        planet.fields.add(
            UI.row(add, copy, remove),
            this.planets,
            edit,
            this.row("planet_light_yaw", () -> this.form.lightYaw, -180D, 180D),
            this.row("light_pitch", () -> this.form.lightPitch, -90D, 90D)
        );

        UISection asteroids = this.section(UIKeys.FORMS_FAKE_SPACE_ASTEROIDS, "fake_space.asteroids", false);

        asteroids.fields.add(
            this.row("asteroid_count", () -> this.form.asteroidCount, 0D, 300D),
            this.row("asteroid_radius", () -> this.form.asteroidRadius, 2D, 1024D),
            this.row("asteroid_speed", () -> this.form.asteroidSpeed, -60D, 60D),
            this.row("asteroid_size", () -> this.form.asteroidSize, 0.1D, 32D),
            this.color("asteroid_color", () -> this.form.asteroidColor)
        );

        UISection galaxy = this.section(UIKeys.FORMS_FAKE_SPACE_GALAXY, "fake_space.galaxy", false);

        galaxy.fields.add(
            this.toggle("galaxy_enabled", () -> this.form.galaxyEnabled),
            this.row("galaxy_x", () -> this.form.galaxyX, -4096D, 4096D),
            this.row("galaxy_y", () -> this.form.galaxyY, -4096D, 4096D),
            this.row("galaxy_z", () -> this.form.galaxyZ, -4096D, 4096D),
            this.row("galaxy_size", () -> this.form.galaxySize, 2D, 2048D),
            this.row("galaxy_yaw", () -> this.form.galaxyYaw, -180D, 180D),
            this.row("galaxy_tilt", () -> this.form.galaxyTilt, -90D, 90D),
            this.row("galaxy_rotation_speed", () -> this.form.galaxyRotationSpeed, -360D, 360D),
            this.row("galaxy_brightness", () -> this.form.galaxyBrightness, 0D, 2D),
            this.color("galaxy_core_color", () -> this.form.galaxyCoreColor),
            this.color("galaxy_arm_color", () -> this.form.galaxyArmColor)
        );

        this.options.add(place, sky, nebula, planet, asteroids, galaxy);
    }

    private String planetLabel(FakeSpaceForm.Planet planet)
    {
        int index = this.form == null ? 0 : this.form.planets.getAllTyped().indexOf(planet) + 1;
        String name = planet.name.get();

        return name.isEmpty() ? UIKeys.FAKE_SPACE_PLANET_DEFAULT_NAME.format(index).get() : name;
    }

    private FakeSpaceForm.Planet picked()
    {
        return this.planets.getCurrentFirst();
    }

    private void refresh(FakeSpaceForm.Planet select)
    {
        this.planets.setList(this.form.planets.getAllTyped());

        if (select != null)
        {
            this.planets.setCurrentScroll(select);
        }
    }

    private void addPlanet()
    {
        this.refresh(this.form.planets.addPlanet());
    }

    private void copyPlanet()
    {
        FakeSpaceForm.Planet planet = this.picked();

        if (planet != null)
        {
            this.refresh(this.form.planets.duplicate(planet));
        }
    }

    private void removePlanet()
    {
        FakeSpaceForm.Planet planet = this.picked();

        if (planet != null)
        {
            this.form.planets.remove(planet);
            this.refresh(null);
        }
    }

    private void editPlanet()
    {
        FakeSpaceForm.Planet planet = this.picked();

        if (planet == null)
        {
            this.getContext().notifyError(UIKeys.FAKE_SPACE_PLANET_NONE);

            return;
        }

        UIPlanetEditorOverlayPanel panel = new UIPlanetEditorOverlayPanel(planet);

        panel.onClose((e) -> this.refresh(planet));
        UIOverlay.addOverlay(this.getContext(), panel, 680, 440);
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

        this.refresh(form.planets.getAllTyped().isEmpty() ? null : form.planets.getAllTyped().get(0));
        this.options.resize();
    }
}
