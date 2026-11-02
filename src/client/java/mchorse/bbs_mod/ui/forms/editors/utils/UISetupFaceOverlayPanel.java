package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPicker;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

/**
 * Asks what a face is to be built out of before it is built: which rig plays the eyes, how
 * far down the face sits on the head, how deep the eyes are inset, and whether they glow.
 */
public class UISetupFaceOverlayPanel extends UIOverlayPanel
{
    @FunctionalInterface
    public interface SetupFaceCallback
    {
        void apply(String model, double verticalOffset, double depth, boolean glow);
    }

    public UIButton eyesRig;
    public UITrackpad verticalOffset;
    public UITrackpad depth;
    public UIToggle glow;
    public UIButton confirm;

    private String model = "player/eyes";
    private final SetupFaceCallback callback;

    public UISetupFaceOverlayPanel(SetupFaceCallback callback)
    {
        super(UIKeys.FORMS_EDITOR_SETUP_FACE_TITLE);

        this.callback = callback;
        this.eyesRig = new UIButton(IKey.constant(this.model), (b) -> this.pickModel());
        this.verticalOffset = new UITrackpad();
        this.verticalOffset.setValue(0D);
        this.verticalOffset.tooltip(UIKeys.FORMS_EDITOR_SETUP_FACE_VERTICAL_OFFSET_HINT);

        this.depth = new UITrackpad();
        this.depth.setValue(0D);
        this.depth.tooltip(UIKeys.FORMS_EDITOR_SETUP_FACE_DEPTH_HINT);

        this.glow = new UIToggle(UIKeys.FORMS_EDITOR_SETUP_FACE_GLOW, false, null);
        this.glow.tooltip(UIKeys.FORMS_EDITOR_SETUP_FACE_GLOW_HINT);

        this.confirm = new UIButton(UIKeys.GENERAL_CONFIRM, (b) -> this.confirm());

        this.content.column(UIConstants.MARGIN).vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        this.content.add(
            UI.label(UIKeys.FORMS_EDITOR_SETUP_FACE_EYES_RIG), this.eyesRig.marginBottom(UIConstants.SCROLL_PADDING),
            UI.label(UIKeys.FORMS_EDITOR_SETUP_FACE_VERTICAL_OFFSET), this.verticalOffset.marginBottom(UIConstants.SCROLL_PADDING),
            UI.label(UIKeys.FORMS_EDITOR_SETUP_FACE_DEPTH), this.depth.marginBottom(UIConstants.SCROLL_PADDING),
            this.glow.marginBottom(UIConstants.SCROLL_PADDING),
            this.confirm
        );
    }

    private void pickModel()
    {
        UIModelPicker.open(this.getContext(), this.model, (model) ->
        {
            this.model = model;
            this.eyesRig.label = IKey.constant(model);
        });
    }

    @Override
    public void confirm()
    {
        this.callback.apply(this.model, this.verticalOffset.getValue(), this.depth.getValue(), this.glow.getValue());
        this.close();
    }
}
