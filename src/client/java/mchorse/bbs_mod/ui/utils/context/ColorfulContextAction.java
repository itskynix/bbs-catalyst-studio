package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

public class ColorfulContextAction extends ContextAction
{
    public int color;

    public ColorfulContextAction(Icon icon, IKey label, Runnable runnable, int color)
    {
        super(icon, label, runnable);

        this.color = color;
    }

    /** The row is lit in its colour when it is the active one: a soft rounded pill, with no bar down the edge. */
    @Override
    protected void renderBackground(UIContext context, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        RoundedDraw.fill(context.batcher, x + 4, y + 1, x + w - 4, y + h - 1, 4, RoundedDraw.ALL, Colors.setA(this.color, 0.28F));

        super.renderBackground(context, x, y, w, h, hover, selected);
    }
}
