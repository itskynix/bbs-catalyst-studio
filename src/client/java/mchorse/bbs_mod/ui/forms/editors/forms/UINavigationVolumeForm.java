package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.NavigationVolumeForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UINavigationVolumeFormPanel;

public class UINavigationVolumeForm extends UIForm<NavigationVolumeForm>
{
    public UINavigationVolumeForm()
    {
        super();

        this.defaultPanel = new UINavigationVolumeFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_NAV_VOLUME_TITLE, NavigationVolumeForm.ICON);
        this.registerDefaultPanels();
    }
}
