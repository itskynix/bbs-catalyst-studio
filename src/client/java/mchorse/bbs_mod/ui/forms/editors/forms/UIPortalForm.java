package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.PortalForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.panels.UIPortalFormPanel;

public class UIPortalForm extends UIForm<PortalForm>
{
    public UIPortalForm()
    {
        super();

        this.defaultPanel = new UIPortalFormPanel(this);

        this.registerPanel(this.defaultPanel, L10n.lang("bbs.ui.forms.editors.portal.title"), PortalForm.ICON);
        this.registerDefaultPanels();
    }
}
