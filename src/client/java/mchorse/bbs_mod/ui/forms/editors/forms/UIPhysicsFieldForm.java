package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.PhysicsFieldForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIPhysicsFieldFormPanel;

public class UIPhysicsFieldForm extends UIForm<PhysicsFieldForm>
{
    public UIPhysicsFieldForm()
    {
        super();

        this.defaultPanel = new UIPhysicsFieldFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_PHYSICS_FIELD_TITLE, PhysicsFieldForm.ICON);
        this.registerDefaultPanels();
    }
}
