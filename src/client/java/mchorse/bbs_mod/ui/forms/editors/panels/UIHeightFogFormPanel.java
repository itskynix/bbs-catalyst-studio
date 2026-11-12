package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.HeightFogForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIHeightFogFormPanel extends UIFormPanel<HeightFogForm>
{
    public UIHeightFogFormPanel(UIForm editor)
    {
        super(editor);

        UITrackpad baseHeight = UIValues.trackpad(() -> this.form.baseHeight);
        baseHeight.limit(-128D, 320D).tooltip(UIKeys.FORMS_EDITORS_HEIGHT_FOG_BASE_HEIGHT_TOOLTIP);

        UITrackpad density = UIValues.trackpad(() -> this.form.density);
        density.limit(0D, 1D).tooltip(UIKeys.FORMS_EDITORS_HEIGHT_FOG_DENSITY_TOOLTIP);

        UITrackpad falloff = UIValues.trackpad(() -> this.form.falloff);
        falloff.limit(0.1D, 64D).tooltip(UIKeys.FORMS_EDITORS_HEIGHT_FOG_FALLOFF_TOOLTIP);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        width.limit(1D, 256D);

        UITrackpad length = UIValues.trackpad(() -> this.form.length);
        length.limit(1D, 256D);

        UITrackpad height = UIValues.trackpad(() -> this.form.height);
        height.limit(1D, 128D);

        UIToggle followCamera = UIValues.toggle(UIKeys.FORMS_EDITORS_HEIGHT_FOG_FOLLOW_CAMERA, () -> this.form.followCamera);

        UITrackpad noiseIntensity = UIValues.trackpad(() -> this.form.noiseIntensity);
        noiseIntensity.limit(0D, 1D).tooltip(UIKeys.FORMS_EDITORS_HEIGHT_FOG_NOISE_INTENSITY_TOOLTIP);

        UITrackpad noiseScale = UIValues.trackpad(() -> this.form.noiseScale);
        noiseScale.limit(0.1D, 5D).tooltip(UIKeys.FORMS_EDITORS_HEIGHT_FOG_NOISE_SCALE_TOOLTIP);

        UITrackpad windSpeed = UIValues.trackpad(() -> this.form.windSpeed);
        windSpeed.limit(0D, 5D).tooltip(UIKeys.FORMS_EDITORS_HEIGHT_FOG_WIND_SPEED_TOOLTIP);

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_HEIGHT_FOG_SHADERS_ONLY, 20, Colors.A100 | Colors.INACTIVE),
            UIValues.color(() -> this.form.color).direction(Direction.LEFT),
            UI.label(UIKeys.FORMS_EDITORS_HEIGHT_FOG_DENSITY).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_DENSITY, density),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_BASE_HEIGHT, baseHeight),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_FALLOFF, falloff),
            UI.label(UIKeys.FORMS_EDITORS_HEIGHT_FOG_SIZE).marginTop(UIConstants.SECTION_GAP),
            UI.row(width, length),
            UI.labelRow(UIKeys.VIDEO_SETTINGS_HEIGHT, height),
            followCamera,
            UI.label(UIKeys.FORMS_EDITORS_HEIGHT_FOG_MOTION).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_NOISE_INTENSITY, noiseIntensity),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_NOISE_SCALE, noiseScale),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_WIND_SPEED, windSpeed)
        );
    }
}
