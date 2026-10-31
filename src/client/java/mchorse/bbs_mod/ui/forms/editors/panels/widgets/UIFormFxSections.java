package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The "Dissolve" and "Shatter" folds that model, block, item, image and extruded form editors
 * share: the same six controls over the same {@link FormFxValues}, wherever the form keeps them.
 */
public class UIFormFxSections
{
    public final UISection dissolve;
    public final UISection shatter;

    /** The swatch is a field: leaving the panel has to dismiss a picker left standing. */
    public final UIColor burnColor;

    private final UICirculate mode;

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.fx." + name);
    }

    private static UITrackpad ranged(Supplier<ValueFloat> value, double min, double max, IKey tooltip)
    {
        UITrackpad trackpad = UIValues.trackpad(value::get);

        trackpad.limit(min, max).values(0.05F, 0.01F, 0.1F).tooltip(tooltip);

        return trackpad;
    }

    public UIFormFxSections(Supplier<FormFxValues> fx)
    {
        this.burnColor = UIValues.color(() -> fx.get().dissolveBurnColor);
        this.mode = new UICirculate((b) -> fx.get().dissolveMode.set(b.getValue()));
        this.mode.addLabel(key("dissolve_mode.linear"));
        this.mode.addLabel(key("dissolve_mode.perlin"));
        this.mode.addLabel(key("dissolve_mode.luma"));
        this.mode.tooltip(key("dissolve_mode_tooltip"));
        UIValues.resettable(this.mode, () -> fx.get().dissolveMode, () -> this.mode.setValue(fx.get().dissolveMode.get()));
        /* Shows what the form holds, not its first label (the circulate starts at 0 whatever the form says) */
        this.mode.valueBinding(() ->
        {
            FormFxValues values = fx.get();

            if (values != null && this.mode.getValue() != values.dissolveMode.get())
            {
                this.mode.setValue(values.dissolveMode.get());
            }
        });

        this.dissolve = new UISection(key("section.dissolve"));
        this.dissolve.fields.add(
            UI.label(key("dissolve_progress")), ranged(() -> fx.get().dissolveProgress, 0, 1, key("dissolve_progress_tooltip")),
            UI.labelRow(key("dissolve_mode"), this.mode),
            UI.label(key("dissolve_edge")), ranged(() -> fx.get().dissolveEdge, 0, 0.5, key("dissolve_edge_tooltip")),
            UI.labelRow(key("dissolve_burn_color"), this.burnColor)
        );
        this.dissolve.setExpanded(false);

        this.shatter = new UISection(key("section.shatter"));
        this.shatter.fields.add(
            UI.label(key("shatter_progress")), ranged(() -> fx.get().shatterProgress, 0, 1, key("shatter_progress_tooltip")),
            UI.label(key("shatter_spread")), ranged(() -> fx.get().shatterSpread, 0, 20, key("shatter_spread_tooltip"))
        );
        this.shatter.setExpanded(false);
    }

    /** Called from the panel's finishEdit. */
    public void finishEdit()
    {
        this.burnColor.picker.removeFromParent();
    }
}
