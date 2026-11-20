package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.windows.UITheme;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * A right click menu drawn the way Windows 11 draws its own: rounded, dark glass with a thin frame, a
 * strip of icon-only quick actions on top, rows with an icon, a label and a faint shortcut or a
 * submenu arrow, a soft pill under the row the mouse is on, and fine lines between groups of
 * commands. A demo piece for the UI sandbox for now; rows run their action and close the menu.
 */
public class UIWin11ContextMenu extends UIContextMenu
{
    private static final int WIDTH = 188;
    private static final int PAD = 3;
    private static final int ROW = 20;
    private static final int SEPARATOR = 7;
    private static final int QUICK = 28;
    private static final int RADIUS = 6;

    private final List<Entry> quick = new ArrayList<>();
    private final List<Entry> rows = new ArrayList<>();

    /** One thing the menu can do; a row without a label is a separator. */
    private static class Entry
    {
        public Icon icon;
        public IKey label;
        public String shortcut;
        public boolean submenu;
        public Runnable action;

        /* where it was drawn last, for the mouse */
        public int x1;
        public int y1;
        public int x2;
        public int y2;

        public boolean separator()
        {
            return this.label == null;
        }
    }

    /** An icon-only button of the strip along the top. */
    public UIWin11ContextMenu quick(Icon icon, IKey label, Runnable action)
    {
        Entry entry = new Entry();

        entry.icon = icon;
        entry.label = label;
        entry.action = action;
        this.quick.add(entry);

        return this;
    }

    public UIWin11ContextMenu item(Icon icon, IKey label, String shortcut, Runnable action)
    {
        Entry entry = new Entry();

        entry.icon = icon;
        entry.label = label;
        entry.shortcut = shortcut;
        entry.action = action;
        this.rows.add(entry);

        return this;
    }

    /** A row that would open a submenu: it shows the arrow. */
    public UIWin11ContextMenu submenu(Icon icon, IKey label, Runnable action)
    {
        this.item(icon, label, null, action);
        this.rows.get(this.rows.size() - 1).submenu = true;

        return this;
    }

    public UIWin11ContextMenu separator()
    {
        if (!this.rows.isEmpty() && !this.rows.get(this.rows.size() - 1).separator())
        {
            this.rows.add(new Entry());
        }

        return this;
    }

    @Override
    public boolean isEmpty()
    {
        return this.quick.isEmpty() && this.rows.isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        int h = PAD * 2;

        if (!this.quick.isEmpty())
        {
            h += QUICK + SEPARATOR;
        }

        for (Entry entry : this.rows)
        {
            h += entry.separator() ? SEPARATOR : ROW;
        }

        /* a separator left dangling at the end takes no room */
        if (!this.rows.isEmpty() && this.rows.get(this.rows.size() - 1).separator())
        {
            h -= SEPARATOR;
            this.rows.remove(this.rows.size() - 1);
        }

        this.set(context.mouseX(), context.mouseY(), WIDTH, h).bounds(context.menu.overlay, 5);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        if (context.mouseButton == 0)
        {
            Entry hit = this.hit(context);

            if (hit != null && hit.action != null)
            {
                hit.action.run();
                this.dismiss();
            }
        }

        return true;
    }

    private Entry hit(UIContext context)
    {
        for (Entry entry : this.quick)
        {
            if (this.inside(entry, context))
            {
                return entry;
            }
        }

        for (Entry entry : this.rows)
        {
            if (!entry.separator() && this.inside(entry, context))
            {
                return entry;
            }
        }

        return null;
    }

    private boolean inside(Entry entry, UIContext context)
    {
        return context.mouseX >= entry.x1 && context.mouseX < entry.x2 && context.mouseY >= entry.y1 && context.mouseY < entry.y2;
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        UITheme.menu(context, this.area, RADIUS);
    }

    @Override
    public void render(UIContext context)
    {
        this.renderBackground(context);

        Area a = this.area;
        FontRenderer font = context.batcher.getFont();
        int y = a.y + PAD;

        if (!this.quick.isEmpty())
        {
            int cell = (a.w - PAD * 2) / this.quick.size();

            for (int i = 0; i < this.quick.size(); i++)
            {
                Entry entry = this.quick.get(i);

                entry.x1 = a.x + PAD + i * cell;
                entry.x2 = entry.x1 + cell;
                entry.y1 = y;
                entry.y2 = y + QUICK;

                if (this.inside(entry, context))
                {
                    this.pill(context, entry.x1 + 1, entry.y1 + 1, entry.x2 - 1, entry.y2 - 1);
                }

                context.batcher.icon(entry.icon, Colors.setA(Colors.WHITE, 0.92F), (entry.x1 + entry.x2) / 2F, (entry.y1 + entry.y2) / 2F, 0.5F, 0.5F);
            }

            y += QUICK;
            this.line(context, y + SEPARATOR / 2);
            y += SEPARATOR;
        }

        for (Entry entry : this.rows)
        {
            if (entry.separator())
            {
                this.line(context, y + SEPARATOR / 2);
                y += SEPARATOR;

                continue;
            }

            entry.x1 = a.x + PAD;
            entry.x2 = a.ex() - PAD;
            entry.y1 = y;
            entry.y2 = y + ROW;

            if (this.inside(entry, context))
            {
                this.pill(context, entry.x1, entry.y1 + 1, entry.x2, entry.y2 - 1);
            }

            context.batcher.icon(entry.icon, Colors.WHITE, entry.x1 + 14, y + ROW / 2F, 0.5F, 0.5F);
            context.batcher.text(entry.label.get(), entry.x1 + 28, y + (ROW - font.getHeight()) / 2, Colors.WHITE, false);

            if (entry.submenu)
            {
                context.batcher.icon(Icons.ARROW_RIGHT, Colors.setA(Colors.WHITE, 0.6F), entry.x2 - 11, y + ROW / 2F, 0.5F, 0.5F);
            }
            else if (entry.shortcut != null)
            {
                context.batcher.text(entry.shortcut, entry.x2 - 8 - font.getWidth(entry.shortcut), y + (ROW - font.getHeight()) / 2, Colors.setA(Colors.WHITE, 0.45F), false);
            }

            y += ROW;
        }

        UITheme.menuFrame(context.batcher, a, RADIUS);
    }

    private void pill(UIContext context, int x1, int y1, int x2, int y2)
    {
        UITheme.pill(context.batcher, x1, y1, x2, y2);
    }

    private void line(UIContext context, int y)
    {
        context.batcher.box(this.area.x + PAD + 3, y, this.area.ex() - PAD - 3, y + 1, UITheme.separator());
    }
}
