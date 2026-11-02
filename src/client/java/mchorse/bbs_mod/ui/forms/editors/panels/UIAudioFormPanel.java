package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.AudioForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.Direction;

import java.io.File;

public class UIAudioFormPanel extends UIFormPanel<AudioForm>
{
    public UIButton pick;
    public UIIcon toggleWireframe;
    public UIIcon openFolder;

    public UIToggle loop;
    public UIToggle muffle;
    public UIToggle doppler;
    public UIToggle wireframeSwitch;

    public UITrackpad volume;
    public UITrackpad pitch;
    public UITrackpad offset;
    public UITrackpad pan;
    public UITrackpad radius;
    public UITrackpad occlusion;
    public UITrackpad dopplerScale;

    public UIAudioFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_AUDIO_PICK_SOUND, (b) ->
        {
            UISoundOverlayPanel panel = new UISoundOverlayPanel((l) -> this.form.sound.set(l), this.getContext());

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.sound.get()));
        });

        UIValues.resettable(this.pick, () -> this.form.sound, null);

        this.toggleWireframe = new UIIcon(Icons.SPHERE, (b) ->
        {
            if (this.form != null)
            {
                boolean next = !this.form.showWireframe.get();

                this.form.showWireframe.set(next);
                this.toggleWireframe.active(next);
                this.wireframeSwitch.setValue(next);
            }
        });
        this.toggleWireframe.tooltip(UIKeys.FORMS_AUDIO_SHOW_WIREFRAME, Direction.BOTTOM);

        this.openFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            Link link = this.form.sound.get();
            File file = BBSMod.getAudioFolder();

            if (link != null)
            {
                File audioFile = BBSMod.getProvider().getFile(link);

                if (audioFile != null && audioFile.exists())
                {
                    file = audioFile.getParentFile();
                }
            }

            UIUtils.openFolder(file);
        });
        this.openFolder.tooltip(UIKeys.FORMS_AUDIO_OPEN_FOLDER, Direction.BOTTOM);

        this.loop = UIValues.toggle(UIKeys.FORMS_AUDIO_LOOP, () -> this.form.loop);
        this.muffle = UIValues.toggle(UIKeys.FORMS_AUDIO_MUFFLE, () -> this.form.muffle);
        this.doppler = UIValues.toggle(UIKeys.FORMS_AUDIO_DOPPLER, () -> this.form.doppler);
        this.wireframeSwitch = new UIToggle(UIKeys.FORMS_AUDIO_SHOW_WIREFRAME, (b) ->
        {
            if (this.form != null)
            {
                this.form.showWireframe.set(b.getValue());
                this.toggleWireframe.active(b.getValue());
            }
        });
        UIValues.resettable(this.wireframeSwitch, () -> this.form.showWireframe, null);

        this.volume = UIValues.trackpad(() -> this.form.volume)
            .limit(0.0D, 2.0D)
            .increment(0.05D)
            .values(0.05D, 0.01D, 0.1D);
        this.volume.tooltip(UIKeys.FORMS_AUDIO_VOLUME_TOOLTIP);

        this.pitch = UIValues.trackpad(() -> this.form.pitch)
            .limit(0.5D, 2.0D)
            .increment(0.05D)
            .values(0.05D, 0.01D, 0.1D);
        this.pitch.tooltip(UIKeys.FORMS_AUDIO_PITCH_TOOLTIP);

        this.offset = UIValues.trackpad(() -> this.form.offset)
            .increment(0.1D)
            .values(0.05D, 0.01D, 0.5D);
        this.offset.tooltip(UIKeys.FORMS_AUDIO_OFFSET_TOOLTIP);

        this.pan = UIValues.trackpad(() -> this.form.pan)
            .limit(-1.0D, 1.0D)
            .increment(0.05D)
            .values(0.05D, 0.01D, 0.1D);
        this.pan.tooltip(UIKeys.FORMS_AUDIO_PAN_TOOLTIP);

        this.radius = UIValues.trackpad(() -> this.form.radius)
            .limit(0.1D, 256.0D)
            .increment(1.0D)
            .values(0.5D, 0.1D, 2.0D);
        this.radius.tooltip(UIKeys.FORMS_AUDIO_RADIUS_TOOLTIP);

        this.occlusion = UIValues.trackpad(() -> this.form.occlusionFactor)
            .limit(0.0D, 2.0D)
            .increment(0.05D)
            .values(0.05D, 0.01D, 0.1D);
        this.occlusion.tooltip(UIKeys.FORMS_AUDIO_OCCLUSION_TOOLTIP);

        this.dopplerScale = UIValues.trackpad(() -> this.form.dopplerScale)
            .limit(0.0D, 5.0D)
            .increment(0.1D)
            .values(0.1D, 0.01D, 0.5D);
        this.dopplerScale.tooltip(UIKeys.FORMS_AUDIO_DOPPLER_SCALE_TOOLTIP);

        this.options.add(
            UI.row(this.pick, this.toggleWireframe, this.openFolder),
            this.loop,
            this.muffle,
            this.doppler,
            this.wireframeSwitch,
            UI.labelRow(UIKeys.FORMS_AUDIO_VOLUME, this.volume).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_AUDIO_PITCH, this.pitch),
            UI.labelRow(UIKeys.FORMS_AUDIO_OFFSET, this.offset),
            UI.labelRow(UIKeys.FORMS_AUDIO_PAN, this.pan),
            UI.labelRow(UIKeys.FORMS_AUDIO_RADIUS, this.radius),
            UI.labelRow(UIKeys.FORMS_AUDIO_OCCLUSION, this.occlusion),
            UI.labelRow(UIKeys.FORMS_AUDIO_DOPPLER_SCALE, this.dopplerScale)
        );
    }

    @Override
    public void startEdit(AudioForm form)
    {
        super.startEdit(form);

        boolean show = form != null && form.showWireframe.get();

        this.toggleWireframe.active(show);
        this.wireframeSwitch.setValue(show);
    }
}
