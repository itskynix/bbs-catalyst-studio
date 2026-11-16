package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The small window every smart tool opens for its settings: a few numbers and switches, Apply and
 * Cancel. Enter applies and Esc cancels. The tool gets the values in the order the fields were given,
 * a switch as 1 or 0.
 */
public class UIAiToolOverlayPanel extends UIOverlayPanel
{
    /** One setting: a number between two limits, or a switch ({@code toggle}, its value 1 or 0) */
    public record Field(IKey label, double min, double max, double value, boolean toggle)
    {
        public static Field number(IKey label, double min, double max, double value)
        {
            return new Field(label, min, max, value, false);
        }

        public static Field toggle(IKey label, boolean value)
        {
            return new Field(label, 0D, 1D, value ? 1D : 0D, true);
        }
    }

    private final List<UIElement> inputs = new ArrayList<>();
    private final Consumer<double[]> apply;

    public UIAiToolOverlayPanel(IKey title, List<Field> fields, Consumer<double[]> apply)
    {
        super(title);

        this.apply = apply;

        UIElement container = new UIElement();

        container.relative(this.content).xy(10, 6).w(1F, -20).hTo(this.content.area, 1F, -6);
        container.column(6).vertical().stretch();

        for (Field field : fields)
        {
            if (field.toggle())
            {
                UIToggle toggle = new UIToggle(field.label(), field.value() > 0D, (t) -> {});

                this.inputs.add(toggle);
                container.add(toggle);
            }
            else
            {
                UITrackpad pad = new UITrackpad((v) -> {});

                pad.limit(field.min(), field.max());
                pad.setValue(field.value());
                pad.h(20);
                this.inputs.add(pad);
                container.add(UI.label(field.label()).h(14), pad);
            }
        }

        UIButton ok = new UIButton(UIKeys.CATALYST_NLE_APPLY, (b) -> this.confirm());
        UIButton cancel = new UIButton(UIKeys.SANDBOX_CANCEL, (b) -> this.close.clickItself());

        ok.h(20);
        cancel.h(20);
        container.add(UI.row(ok, cancel).h(20));
        this.content.add(container);
    }

    /** Opens the window sized to its fields */
    public static void open(UIContext context, IKey title, List<Field> fields, Consumer<double[]> apply)
    {
        int rows = 0;

        for (Field field : fields)
        {
            rows += field.toggle() ? 1 : 2;
        }

        UIOverlay.addOverlay(context, new UIAiToolOverlayPanel(title, fields, apply), 360, 84 + rows * 20);
    }

    @Override
    public void confirm()
    {
        double[] values = new double[this.inputs.size()];

        for (int i = 0; i < values.length; i++)
        {
            UIElement input = this.inputs.get(i);

            values[i] = input instanceof UIToggle toggle ? (toggle.getValue() ? 1D : 0D) : ((UITrackpad) input).getValue();
        }

        this.close.clickItself();
        this.apply.accept(values);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyCode() == GLFW.GLFW_KEY_ENTER && context.getKeyAction() == KeyAction.PRESSED)
        {
            this.confirm();

            return true;
        }

        return super.subKeyPressed(context);
    }
}
