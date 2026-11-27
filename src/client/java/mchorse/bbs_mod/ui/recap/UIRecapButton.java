package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** An icon button drawn in the Interface Theme's own material, for the recap editor's controls. */
public class UIRecapButton extends UIClickable<UIRecapButton>
{
    private final Supplier<Icon> icon;
    private BooleanSupplier primary = () -> false;
    private BooleanSupplier active;

    public UIRecapButton(Supplier<Icon> icon, IKey tooltip, Runnable action)
    {
        super((b) -> action.run());

        this.icon = icon;
        this.tooltip(tooltip);
    }

    public UIRecapButton primary(BooleanSupplier primary)
    {
        this.primary = primary;

        return this;
    }

    /** Whether it can be pressed, asked again every frame: undo with nothing to undo is dimmed. */
    public UIRecapButton active(BooleanSupplier active)
    {
        this.active = active;

        return this;
    }

    @Override
    protected UIRecapButton get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        if (this.active != null)
        {
            this.setEnabled(this.active.getAsBoolean());
        }

        boolean enabled = this.isEnabled();
        boolean primary = enabled && this.primary.getAsBoolean();

        RecapStyle.button(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), primary, enabled && this.hover);
        context.batcher.icon(this.icon.get(), RecapStyle.buttonIcon(primary, enabled), this.area.mx(), this.area.my(), 0.5F, 0.5F);
    }
}
