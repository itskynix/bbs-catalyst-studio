package mchorse.bbs_mod.forms.ui;

import mchorse.bbs_mod.forms.forms.AudioForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIAudioFormPanel;

public class UIAudioForm extends UIForm<AudioForm>
{
    private UIAudioFormPanel audioFormPanel;

    public UIAudioForm()
    {
        super();

        this.audioFormPanel = new UIAudioFormPanel(this);
        this.defaultPanel = this.audioFormPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_AUDIO_TITLE, AudioForm.AUDIO_ICON);
        this.registerDefaultPanels();
    }

    public UIAudioFormPanel getAudioFormPanel()
    {
        return this.audioFormPanel;
    }
}
