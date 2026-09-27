package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Clickable tab button for Catalyst Editor top bar.
 * Supports icon, localized text label, and dynamic active/hover states.
 */
public class UICatalystTab extends UIClickable<UICatalystTab>
{
    public Icon icon;
    public IKey title;
    public BooleanSupplier active;

    public UICatalystTab(Icon icon, IKey title, BooleanSupplier active, Consumer<UICatalystTab> callback)
    {
        super(callback);
        this.icon = icon;
        this.title = title;
        this.active = active;
        this.h(20);
    }

    @Override
    protected UICatalystTab get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        boolean isAct = this.active != null && this.active.getAsBoolean();
        int primary = BBSSettings.primaryColor.get();

        if (isAct)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), primary | Colors.A75);
            context.batcher.box(this.area.x, this.area.ey() - 2, this.area.ex(), this.area.ey(), Colors.WHITE);
        }
        else if (this.hover)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x22FFFFFF);
        }

        int x = this.area.x + 6;

        if (this.icon != null)
        {
            context.batcher.icon(this.icon, isAct ? Colors.WHITE : (this.hover ? Colors.LIGHTEST_GRAY : Colors.GRAY), x, this.area.my() - 8);
            x += 18;
        }

        FontRenderer font = context.batcher.getFont();
        context.batcher.text(this.title.get(), x, this.area.my() - font.getHeight() / 2, isAct ? Colors.WHITE : (this.hover ? Colors.LIGHTEST_GRAY : Colors.GRAY), false);
    }
}
