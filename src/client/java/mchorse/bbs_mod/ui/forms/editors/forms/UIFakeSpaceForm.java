package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.FakeSpaceForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFakeSpaceFormPanel;

public class UIFakeSpaceForm extends UIForm<FakeSpaceForm>
{
    public UIFakeSpaceForm()
    {
        super();

        this.defaultPanel = new UIFakeSpaceFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_FAKE_SPACE_TITLE, FakeSpaceForm.ICON);
        this.registerDefaultPanels();
    }
}
