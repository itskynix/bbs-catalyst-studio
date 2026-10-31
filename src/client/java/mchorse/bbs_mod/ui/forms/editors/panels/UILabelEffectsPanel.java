package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The Effects tab of the text form: the letters burning away and breaking apart. Kept apart from
 * the text's own options, which are about how it is laid out and shaped.
 */
public class UILabelEffectsPanel extends UIFormPanel<LabelForm>
{
    public UIColor burnColor;
    public UICirculate dissolveMode;

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.label." + name);
    }

    public UILabelEffectsPanel(UIForm editor)
    {
        super(editor);

        /* Dissolve */
        this.burnColor = UIValues.color(() -> this.form.dissolveBurnColor);
        this.dissolveMode = new UICirculate((b) -> this.form.dissolveMode.set(b.getValue()));
        this.dissolveMode.addLabel(key("dissolve_mode.linear"));
        this.dissolveMode.addLabel(key("dissolve_mode.perlin"));
        this.dissolveMode.addLabel(key("dissolve_mode.luma"));
        this.dissolveMode.tooltip(key("dissolve_mode_tooltip"));
        UIValues.resettable(this.dissolveMode, () -> this.form.dissolveMode, () -> this.dissolveMode.setValue(this.form.dissolveMode.get()));
        /* Shows what the form holds, not its first label: the circulate starts at 0 whatever the form says */
        this.dissolveMode.valueBinding(() ->
        {
            if (this.form != null && this.dissolveMode.getValue() != this.form.dissolveMode.get())
            {
                this.dissolveMode.setValue(this.form.dissolveMode.get());
            }
        });

        UISection dissolve = new UISection(key("section.dissolve"));

        dissolve.fields.add(
            UI.label(key("dissolve_progress")), this.ranged(() -> this.form.dissolveProgress, 0, 1, key("dissolve_progress_tooltip")),
            UI.labelRow(key("dissolve_mode"), this.dissolveMode),
            UI.label(key("dissolve_edge")), this.ranged(() -> this.form.dissolveEdge, 0, 0.5, key("dissolve_edge_tooltip")),
            UI.labelRow(key("dissolve_burn_color"), this.burnColor)
        );

        /* Shatter */
        UISection shatter = new UISection(key("section.shatter"));

        shatter.fields.add(
            UI.label(key("shatter_progress")), this.ranged(() -> this.form.shatterProgress, 0, 1, key("shatter_progress_tooltip")),
            UI.label(key("shatter_spread")), this.ranged(() -> this.form.shatterSpread, 0, 20, key("shatter_spread_tooltip"))
        );

        this.options.add(dissolve, shatter);
    }

    /** A trackpad for a 0..1-like value, limited to the range its value allows. */
    private UITrackpad ranged(Supplier<ValueFloat> value, double min, double max, IKey tooltip)
    {
        UITrackpad trackpad = UIValues.trackpad(value::get);

        trackpad.limit(min, max).values(0.05F, 0.01F, 0.1F).tooltip(tooltip);

        return trackpad;
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.burnColor.picker.removeFromParent();
    }
}
