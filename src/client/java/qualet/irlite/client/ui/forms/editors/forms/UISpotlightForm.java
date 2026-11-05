package qualet.irlite.client.ui.forms.editors.forms;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import qualet.irlite.client.ui.forms.editors.panels.UILightEffectsPanel;
import qualet.irlite.client.ui.forms.editors.panels.UISpotlightFormPanel;
import qualet.irlite.forms.SpotlightForm;

public class UISpotlightForm extends UIForm<SpotlightForm>
{
    public UISpotlightForm()
    {
        super();

        this.defaultPanel = new UISpotlightFormPanel(this);

        this.registerPanel(this.defaultPanel, L10n.lang("irlights.forms.spotlight", "Spotlight"), Icons.FRUSTUM);
        this.registerPanel(new UILightEffectsPanel<>(this, true), L10n.lang("irlights.forms.tab.volumetric", "Volumetric (this light)"), Icons.SUN);
        this.registerPanel(new UILightEffectsPanel<>(this, false), L10n.lang("irlights.forms.tab.outline", "Outline (this light)"), Icons.OUTLINE);
        this.registerDefaultPanels();
    }
}
