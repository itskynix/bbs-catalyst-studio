package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.DecalForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIDecalFormPanel;

public class UIDecalForm extends UIForm<DecalForm>
{
    private UIDecalFormPanel decalFormPanel;

    public UIDecalForm()
    {
        super();

        this.decalFormPanel = new UIDecalFormPanel(this);
        this.defaultPanel = this.decalFormPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_DECAL_TITLE, DecalForm.ICON);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.decalFormPanel)
            {
                this.setPanel(this.decalFormPanel);
            }

            this.decalFormPanel.pick.clickItself();
        });
    }
}

