package mchorse.bbs_mod.ui.dashboard.menu;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/** One title in the menu bar — File, Edit... — that opens its drop-down on a click. */
public class UIMenuBarItem extends UIClickable<UIMenuBarItem>
{
    public static final int HEIGHT = 18;

    public IKey label;

    public UIMenuBarItem(IKey label, Consumer<UIMenuBarItem> callback)
    {
        super(callback);

        this.label = label;
        this.h(HEIGHT);
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.label != null)
        {
            this.area.w = Batcher2D.getDefaultTextRenderer().getWidth(this.label.get()) + 14;
            this.area.h = HEIGHT;
        }
    }

    @Override
    protected UIMenuBarItem get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        if (this.hover)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x22FFFFFF);
        }

        if (this.label != null)
        {
            FontRenderer font = context.batcher.getFont();
            String str = this.label.get();

            context.batcher.text(str, this.area.mx(font.getWidth(str)), this.area.my(font.getHeight()), this.hover ? Colors.WHITE : 0xFFCCCCCC, false);
        }
    }
}
