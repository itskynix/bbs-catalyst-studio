package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The wide, accent-coloured "create new" banner of the hub screens, placed between the search bar
 * and the recent projects: an add icon and a label, impossible to miss.
 */
public class UIHubCreateButton extends UIClickable<UIHubCreateButton>
{
    private final IKey label;

    public UIHubCreateButton(IKey label, Consumer<UIHubCreateButton> callback)
    {
        super(callback);

        this.label = label;
    }

    @Override
    protected UIHubCreateButton get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        int accent = BBSSettings.primaryColor.get() | Colors.A100;
        int color = this.hover ? Colors.mulRGB(accent, 1.15F) : accent;

        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 6, Colors.A50, 0);
        context.batcher.surfaceBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), color | Colors.A100, true, false);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x40ffffff);

        FontRenderer font = context.batcher.getFont();
        String text = font.limitToWidth(this.label.get(), this.area.w - 40);
        int iconSize = 16;
        int total = iconSize + 6 + font.getWidth(text);
        int x = this.area.mx() - total / 2;

        context.batcher.icon(Icons.ADD, Colors.WHITE, x, this.area.my(), 0F, 0.5F);
        context.batcher.text(text, x + iconSize + 6, this.area.my(font.getHeight()), Colors.WHITE, true);
    }
}
