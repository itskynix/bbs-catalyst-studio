package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormEffectsPanel;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIBlockFormPanel;

public class UIBlockForm extends UIForm<BlockForm>
{
    public UIBlockForm()
    {
        super();

        this.defaultPanel = new UIBlockFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_BLOCK_TITLE, BlockForm.ICON);
        this.registerDefaultPanels();
        this.registerPanel(new mchorse.bbs_mod.ui.forms.editors.panels.UILatticeFormPanel<>(this), UIKeys.FORMS_EDITORS_LATTICE, Icons.BLOCK);
        this.registerMaskPanel();
        this.registerPanel(new UIFormEffectsPanel<BlockForm>(this, (f) -> f.fx), UIKeys.FORMS_EDITORS_EFFECTS, Icons.SHAPES);
    }
}