package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

public class ContextAction
{
    public Icon icon;
    public IKey label;
    public Runnable runnable;

    public IKey keyCategory;
    public int[] keys;
    public int order = -1;

    public ContextAction(Icon icon, IKey label, Runnable runnable)
    {
        this.icon = icon;
        this.label = label;
        this.runnable = runnable;
    }

    public ContextAction key(IKey keyCategory, int... keys)
    {
        this.keyCategory = keyCategory;

        return this.key(keys);
    }

    public ContextAction key(int... keys)
    {
        this.keys = keys;

        return this;
    }

    public ContextAction order(int order)
    {
        this.order = order;

        return this;
    }

    /** The shortcut shown on the right of the row: only a real, registered one, not the numbers a menu hands out. */
    public String getShortcut()
    {
        if (this.keyCategory == null || this.keys == null || this.keys.length == 0)
        {
            return null;
        }

        StringBuilder label = new StringBuilder(KeyCodes.getName(this.keys[0]));

        for (int i = 1; i < this.keys.length; i++)
        {
            label.insert(0, KeyCodes.getName(this.keys[i]) + "+");
        }

        return label.toString();
    }

    public int getWidth(FontRenderer font)
    {
        String shortcut = this.getShortcut();

        return 36 + font.getWidth(this.label.get()) + (shortcut == null ? 8 : 24 + font.getWidth(shortcut));
    }

    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        this.renderBackground(context, x, y, w, h, hover, selected);

        /* Icon, label, then the shortcut faint on the right: the row of a Windows 11 menu */
        context.batcher.icon(this.icon, Colors.WHITE, x + 14, y + h / 2F, 0.5F, 0.5F);
        context.batcher.text(this.label.get(), x + 28, y + (h - font.getHeight()) / 2 + 1, Colors.WHITE, false);

        String shortcut = this.getShortcut();

        if (shortcut != null)
        {
            context.batcher.text(shortcut, x + w - 10 - font.getWidth(shortcut), y + (h - font.getHeight()) / 2 + 1, Colors.setA(Colors.WHITE, 0.45F), false);
        }
    }

    /** A soft rounded pill under the row the mouse is on. */
    protected void renderBackground(UIContext context, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        if (hover)
        {
            RoundedDraw.fill(context.batcher, x + 4, y + 1, x + w - 4, y + h - 1, 4, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.09F));
        }
    }
}
