package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.PostProcessForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIPostProcessFormPanel;

public class UIPostProcessForm extends UIForm<PostProcessForm>
{
    public UIPostProcessForm()
    {
        super();

        this.defaultPanel = new UIPostProcessFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_POST_VOLUME_TITLE, PostProcessForm.ICON);
        this.registerDefaultPanels();
    }
}
