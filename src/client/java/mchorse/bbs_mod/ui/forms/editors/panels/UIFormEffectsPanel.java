package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormFxSections;

import java.util.function.Function;

/**
 * The Effects tab of the forms that burn away and break apart (model, block, item, billboard,
 * video, extruded): the dissolve and the shatter, apart from the form's own options. The text form
 * has its own ({@link UILabelEffectsPanel}), its effects being drawn its own way.
 */
public class UIFormEffectsPanel <T extends Form> extends UIFormPanel<T>
{
    public final UIFormFxSections fx;

    public UIFormEffectsPanel(UIForm editor, Function<T, FormFxValues> values)
    {
        super(editor);

        this.fx = new UIFormFxSections(() -> values.apply(this.form));
        this.fx.dissolve.setExpanded(true);
        this.fx.shatter.setExpanded(true);

        this.options.add(this.fx.dissolve, this.fx.shatter);
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.fx.finishEdit();
    }
}
