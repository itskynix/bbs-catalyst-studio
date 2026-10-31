package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIVideoClip;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Consumer;

public class UIVideoFormPanel extends UIFormPanel<VideoForm>
{
    public UIToggle extrude;
    /** Depth, alpha cutoff and side shading: only there while the video is extruded */
    public UIElement extrudeOptions;

    public UIVideoFormPanel(UIForm editor)
    {
        super(editor);

        UIButton pickVideo = new UIButton(UIKeys.CAMERA_PANELS_VIDEO_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.CAMERA_PANELS_VIDEO_PICK, UIVideoClip.getVideoLinks(), (l) -> this.form.video.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.video.get()));
        });

        this.options.add(pickVideo);
        this.options.add(
            UIValues.toggle(UIKeys.CAMERA_PANELS_VIDEO_LOOP, () -> this.form.loop),
            UIValues.toggle(UIKeys.FORMS_EDITORS_VIDEO_BILLBOARD, () -> this.form.billboard)
        );
        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_VIDEO_PLAYBACK).marginTop(UIConstants.SECTION_GAP),
            UIValues.trackpad(() -> this.form.videoOffset).tooltip(UIKeys.FORMS_EDITORS_VIDEO_OFFSET)
        );

        this.extrude = UIValues.toggle(UIKeys.FORMS_EDITORS_VIDEO_EXTRUDE, () -> this.form.extrude);
        this.extrude.tooltip(UIKeys.FORMS_EDITORS_VIDEO_EXTRUDE_TOOLTIP);

        Consumer<UIToggle> set = this.extrude.callback;

        this.extrude.callback = (b) ->
        {
            set.accept(b);
            this.updateExtrude();
        };

        this.extrudeOptions = UI.column(
            UI.labelRow(UIKeys.FORMS_EDITORS_VIDEO_DEPTH, UIValues.trackpad(() -> this.form.depth).limit(0.01, 5).increment(0.01).values(0.01, 0.001, 0.1).tooltip(UIKeys.FORMS_EDITORS_VIDEO_DEPTH_TOOLTIP)),
            UI.labelRow(UIKeys.FORMS_EDITORS_VIDEO_ALPHA_THRESHOLD, UIValues.trackpad(() -> this.form.alphaThreshold).limit(0, 1).increment(0.05).values(0.05, 0.01, 0.1).tooltip(UIKeys.FORMS_EDITORS_VIDEO_ALPHA_THRESHOLD_TOOLTIP)),
            UIValues.toggle(UIKeys.FORMS_EDITORS_VIDEO_SIDE_SHADING, () -> this.form.sideShading).tooltip(UIKeys.FORMS_EDITORS_VIDEO_SIDE_SHADING_TOOLTIP)
        );

        this.options.add(this.extrude.marginTop(UIConstants.SECTION_GAP), this.extrudeOptions);
    }

    @Override
    public void startEdit(VideoForm form)
    {
        super.startEdit(form);

        this.updateExtrude();
    }

    private void updateExtrude()
    {
        this.extrudeOptions.setVisible(this.form != null && this.form.extrude.get());

        if (this.options.hasParent())
        {
            this.options.resize();
        }
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();
    }
}
