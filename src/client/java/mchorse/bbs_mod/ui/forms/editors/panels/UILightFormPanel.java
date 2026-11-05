package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The light form's tab: its type, colour, intensity, radius and a spot light's cone. All of them are
 * tracks of the dope sheet too.
 */
public class UILightFormPanel extends UIFormPanel<LightForm>
{
    public final UICirculate type;
    public final UIColor color;

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.light." + name);
    }

    private static UITrackpad number(Supplier<? extends BaseValueNumber<?>> value, double min, double max, double step, IKey tooltip)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max).values(step, step / 5D, step * 2D).tooltip(tooltip);

        return trackpad;
    }

    public UILightFormPanel(UIForm editor)
    {
        super(editor);

        this.type = new UICirculate((b) -> this.form.lightType.set(b.getValue()));
        this.type.addLabel(key("type.point"));
        this.type.addLabel(key("type.spot"));
        this.type.addLabel(key("type.directional"));
        this.type.tooltip(key("type_tooltip"));
        this.type.valueBinding(() ->
        {
            if (this.form != null && this.type.getValue() != this.form.lightType.get())
            {
                this.type.setValue(this.form.lightType.get());
            }
        });

        this.color = UIValues.color(() -> this.form.color);

        this.options.add(
            UI.labelRow(key("type"), this.type),
            UI.labelRow(key("color"), this.color),
            UI.label(key("intensity")), number(() -> this.form.intensity, 0, 16, 0.05, key("intensity_tooltip")),
            UI.label(key("radius")), number(() -> this.form.radius, 0.1, 128, 0.25, key("radius_tooltip")),
            UI.label(key("spot_angle")), number(() -> this.form.spotAngle, 1, 170, 1, key("spot_angle_tooltip")),
            UIValues.toggle(key("marker"), () -> this.form.marker)
        );
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.color.picker.removeFromParent();
    }
}
