package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.panels.UILightFormPanel;

public class UILightForm extends UIForm<LightForm>
{
    public UILightForm()
    {
        super();

        this.defaultPanel = new UILightFormPanel(this);

        this.registerPanel(this.defaultPanel, L10n.lang("bbs.ui.forms.light.title"), LightForm.ICON);
        this.registerDefaultPanels();
    }
}
