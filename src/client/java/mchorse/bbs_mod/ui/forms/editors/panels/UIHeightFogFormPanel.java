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

public class UIHeightFogFormPanel extends UIFormPanel<HeightFogForm>
{
    public UIHeightFogFormPanel(UIForm editor)
    {
        super(editor);

        UITrackpad baseHeight = UIValues.trackpad(() -> this.form.baseHeight);
        baseHeight.limit(-128D, 320D);

        UITrackpad density = UIValues.trackpad(() -> this.form.density);
        density.limit(0D, 1D);

        UITrackpad falloff = UIValues.trackpad(() -> this.form.falloff);
        falloff.limit(0.1D, 64D);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        width.limit(1D, 256D);

        UITrackpad length = UIValues.trackpad(() -> this.form.length);
        length.limit(1D, 256D);

        UITrackpad height = UIValues.trackpad(() -> this.form.height);
        height.limit(1D, 128D);

        UITrackpad slices = UIValues.trackpad(() -> this.form.slices);
        slices.limit(4D, 64D);

        UIToggle followCamera = UIValues.toggle(UIKeys.FORMS_EDITORS_HEIGHT_FOG_FOLLOW_CAMERA, () -> this.form.followCamera);

        this.options.add(
            UIValues.color(() -> this.form.color).direction(Direction.LEFT),
            UI.label(UIKeys.FORMS_EDITORS_HEIGHT_FOG_DENSITY).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_BASE_HEIGHT, baseHeight),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_DENSITY, density),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_FALLOFF, falloff),
            UI.label(UIKeys.FORMS_EDITORS_HEIGHT_FOG_SIZE).marginTop(UIConstants.SECTION_GAP),
            UI.row(width, length),
            UI.labelRow(UIKeys.VIDEO_SETTINGS_HEIGHT, height),
            UI.labelRow(UIKeys.FORMS_EDITORS_HEIGHT_FOG_SLICES, slices),
            followCamera
        );
    }
}

