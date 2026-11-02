package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.DecalForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.Direction;

public class UIDecalFormPanel extends UIFormPanel<DecalForm>
{
    public UIButton pick;

    public UIDecalFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_DECAL_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });

        UIValues.resettable(this.pick, () -> this.form.texture, null);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        UITrackpad height = UIValues.trackpad(() -> this.form.height);
        UITrackpad depth = UIValues.trackpad(() -> this.form.depth);
        UITrackpad threshold = UIValues.trackpad(() -> this.form.normalThreshold);
        threshold.limit(0D, 90D);

        UITrackpad opacity = UIValues.trackpad(() -> this.form.opacity);
        opacity.limit(0D, 1D);

        UITrackpad offset = UIValues.trackpad(() -> this.form.offset);
        offset.limit(0D, 0.1D);

        UIToggle doubleSided = UIValues.toggle(UIKeys.FORMS_EDITORS_DECAL_DOUBLE_SIDED, () -> this.form.doubleSided);
        UIToggle glow = UIValues.toggle(UIKeys.FORMS_EDITORS_DECAL_GLOW, () -> this.form.glow);

        this.options.add(
            this.pick,
            UIValues.color(() -> this.form.color).direction(Direction.LEFT).withAlpha(),
            UI.label(UIKeys.FORMS_EDITORS_DECAL_SIZE).marginTop(UIConstants.SECTION_GAP),
            UI.row(width, height),
            UI.labelRow(UIKeys.FORMS_EDITORS_DECAL_DEPTH, depth),
            UI.labelRow(UIKeys.FORMS_EDITORS_DECAL_THRESHOLD, threshold),
            UI.labelRow(UIKeys.FORMS_EDITORS_DECAL_OPACITY, opacity),
            UI.labelRow(UIKeys.FORMS_EDITORS_DECAL_OFFSET, offset),
            doubleSided,
            glow
        );
    }
}
