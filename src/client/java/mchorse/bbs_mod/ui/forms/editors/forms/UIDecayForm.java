package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.DecayForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIDecayFormPanel;

public class UIDecayForm extends UIForm<DecayForm>
{
    private UIDecayFormPanel decayFormPanel;

    public UIDecayForm()
    {
        super();

        this.decayFormPanel = new UIDecayFormPanel(this);
        this.defaultPanel = this.decayFormPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_DECAY_TITLE, DecayForm.ICON);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.decayFormPanel)
            {
                this.setPanel(this.decayFormPanel);
            }

            this.decayFormPanel.pick.clickItself();
        });
    }
}
