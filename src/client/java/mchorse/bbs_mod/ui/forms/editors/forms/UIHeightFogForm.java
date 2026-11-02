package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.HeightFogForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIHeightFogFormPanel;

public class UIHeightFogForm extends UIForm<HeightFogForm>
{
    private UIHeightFogFormPanel heightFogPanel;

    public UIHeightFogForm()
    {
        super();

        this.heightFogPanel = new UIHeightFogFormPanel(this);
        this.defaultPanel = this.heightFogPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_HEIGHT_FOG_TITLE, HeightFogForm.ICON);
        this.registerDefaultPanels();
    }
}

