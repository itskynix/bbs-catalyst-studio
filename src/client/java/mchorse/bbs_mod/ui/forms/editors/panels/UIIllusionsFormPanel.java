package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The Illusions tab every form has: how many ghost copies of it are drawn, how far apart and in
 * which directions, how they fade, what each adds to the one before it, how much they shiver, and
 * how many ticks they trail behind the actor in a film. The count, the spread and the opacity are
 * also tracks of the dope sheet, so a form can multiply and gather again as the film runs.
 */
public class UIIllusionsFormPanel <T extends Form> extends UIFormPanel<T>
{
    public final UIPropTransform offset;

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.illusions." + name);
    }

    private static UITrackpad number(Supplier<? extends BaseValueNumber<?>> value, double min, double max, double step, boolean integer, IKey tooltip)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max).values(step, step / 5D, step * 2D).tooltip(tooltip);

        if (integer)
        {
            trackpad.integer();
        }

        return trackpad;
    }

    /** A choice kept in a form value: shows what the form holds, writes what is picked */
    private UICirculate circulate(Supplier<ValueInt> value, String id, String... labels)
    {
        UICirculate circulate = new UICirculate((b) -> value.get().set(b.getValue()));

        for (String label : labels)
        {
            circulate.addLabel(key(id + "." + label));
        }

        circulate.tooltip(key(id + "_tooltip"));
        circulate.valueBinding(() ->
        {
            ValueInt current = this.form == null ? null : value.get();

            if (current != null && circulate.getValue() != current.get())
            {
                circulate.setValue(current.get());
            }
        });

        return circulate;
    }

    public UIIllusionsFormPanel(UIForm editor)
    {
        super(editor);

        UISection copies = new UISection(key("section.copies"));

        copies.fields.add(
            UI.label(key("count")), number(() -> this.form.illusions.count, 0, 64, 1, true, key("count_tooltip")),
            UI.label(key("spread")), number(() -> this.form.illusions.spread, 0, 32, 0.05, false, key("spread_tooltip")),
            UI.label(key("opacity")), number(() -> this.form.illusions.opacity, 0, 1, 0.05, false, key("opacity_tooltip"))
        );
        copies.setExpanded(true);

        UISection pattern = new UISection(key("section.pattern"));

        pattern.fields.add(
            UI.labelRow(key("pattern"), circulate(() -> this.form.illusions.pattern, "pattern", "linear", "circular", "cross", "grid")),
            UI.labelRow(key("plane"), circulate(() -> this.form.illusions.plane, "plane", "xz", "xy", "yz")),
            UI.labelRow(key("face"), circulate(() -> this.form.illusions.face, "face", "none", "center", "outward"))
        );
        pattern.setExpanded(true);

        UISection direction = new UISection(key("section.direction"));

        direction.fields.add(
            UI.row(UIValues.toggle(key("front"), () -> this.form.illusions.front), UIValues.toggle(key("back"), () -> this.form.illusions.back)),
            UI.row(UIValues.toggle(key("left"), () -> this.form.illusions.left), UIValues.toggle(key("right"), () -> this.form.illusions.right)),
            UI.row(UIValues.toggle(key("up"), () -> this.form.illusions.up), UIValues.toggle(key("down"), () -> this.form.illusions.down)),
            UIValues.toggle(key("diagonal"), () -> this.form.illusions.diagonal)
        );
        direction.setExpanded(true);

        UISection fade = new UISection(key("section.fade"));

        fade.fields.add(
            UIValues.toggle(key("fade_out"), () -> this.form.illusions.fadeOut),
            UIValues.toggle(key("invert_fade"), () -> this.form.illusions.invertFade)
        );
        fade.setExpanded(false);

        this.offset = new UIPropTransform().callbacks(() -> this.form.illusions.offset);
        this.offset.valueBinding(() -> this.offset.setTransform(this.form == null ? null : this.form.illusions.offset.get()));

        UISection step = new UISection(key("section.step"));

        step.fields.add(UI.label(key("step_hint")), UIValues.toggle(key("cumulative"), () -> this.form.illusions.cumulative), this.offset);
        step.setExpanded(false);

        UISection motion = new UISection(key("section.motion"));

        motion.fields.add(
            UI.label(key("jitter")), number(() -> this.form.illusions.jitter, 0, 2, 0.02, false, key("jitter_tooltip")),
            UI.label(key("delay")), number(() -> this.form.illusions.delay, 0, 40, 1, true, key("delay_tooltip"))
        );
        motion.setExpanded(false);

        this.options.add(copies, pattern, direction, fade, step, motion);
    }
}
