package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A picker of the POV clips' presets: every entry a cell of a five column grid, with a search over
 * them on top, and over it, when the subclass sorts its entries, a strip of category tabs. The panel
 * can be sized; the cells grow and shrink with it. What an entry is, how its cell looks and what
 * picking it does are the subclass's.
 */
public abstract class UIPovGridPicker<T> extends UIOverlayPanel
{
    private static final int COLUMNS = 5;
    private static final int GAP = 4;

    public final UITextbox search;
    public final Grid grid;
    public final Tabs tabs;

    protected UIPovGridPicker(IKey title)
    {
        super(title);

        this.grid = new Grid();
        this.tabs = new Tabs();
        this.search = new UITextbox(100, (text) -> this.grid.filter(text));
        this.search.placeholder(L10n.lang("bbs.ui.film.pov_clips.gui.search"));

        this.search.relative(this.content).x(6).y(4).w(1F, -12).h(20);
        this.grid.relative(this.content).x(6).y(28).w(1F, -12).h(1F, -34);
        this.content.add(this.search, this.grid);
    }

    /** Fill the grid (and lay out the tabs, when there are any); called by the subclass once its own fields are set */
    protected void refresh()
    {
        if (!this.getTabs().isEmpty() && !this.tabs.hasParent())
        {
            this.tabs.relative(this.content).x(6).y(4).w(1F, -12).h(18);
            this.search.y(26);
            this.grid.y(50).h(1F, -56);
            this.content.add(this.tabs);
        }

        this.grid.filter(this.search.textbox.getText());
    }

    /** The category tabs over the search, none by default; a search looks through all of them */
    protected List<IKey> getTabs()
    {
        return Collections.emptyList();
    }

    /** Whether an entry is filed under a tab */
    protected boolean inTab(T entry, int tab)
    {
        return true;
    }

    /** The colour a tab's name and underline have */
    protected int tabColor(int tab)
    {
        return BBSSettings.primaryColor.get();
    }

    protected abstract List<T> getEntries();

    /** Whether an entry answers the search, already lowercase and trimmed */
    protected abstract boolean matches(T entry, String needle);

    protected abstract void renderCell(UIContext context, T entry, int x, int y, int w, int h, boolean hovered);

    protected abstract void pick(T entry);

    /** The entry the clip has now, outlined in the grid */
    protected boolean isCurrent(T entry)
    {
        return false;
    }

    /** What the card under the cursor says, or null for none */
    protected String describe(T entry)
    {
        return null;
    }

    /** How tall a cell is against its width */
    protected float cellAspect()
    {
        return 0.9F;
    }

    /** The category tabs: one of them open, the open one underlined; searching looks past them */
    public class Tabs extends UIElement
    {
        private int tab;

        private int tabAt(UIContext context)
        {
            int count = getTabs().size();

            if (!this.area.isInside(context) || count == 0)
            {
                return -1;
            }

            return Math.min(count - 1, (context.mouseX - this.area.x) * count / Math.max(1, this.area.w));
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            int tab = context.mouseButton == 0 ? this.tabAt(context) : -1;

            if (tab >= 0)
            {
                UIUtils.playClick();
                this.tab = tab;
                search.setText("");
                grid.filter("");

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            List<IKey> labels = getTabs();
            int count = labels.size();
            int hover = this.tabAt(context);
            boolean searching = !search.textbox.getText().trim().isEmpty();

            for (int i = 0; i < count; i++)
            {
                int x1 = this.area.x + this.area.w * i / count;
                int x2 = this.area.x + this.area.w * (i + 1) / count;
                int color = Colors.A100 | tabColor(i);
                boolean active = i == this.tab && !searching;

                context.batcher.box(x1, this.area.y, x2 - 1, this.area.ey(), active ? Colors.setA(color, 0.18F) : i == hover ? 0x22ffffff : 0x44000000);

                if (active)
                {
                    context.batcher.box(x1 + 2, this.area.ey() - 2, x2 - 3, this.area.ey(), color);
                }

                String label = context.batcher.getFont().limitToWidth(labels.get(i).get(), x2 - x1 - 6);
                int tx = (x1 + x2 - context.batcher.getFont().getWidth(label)) / 2;

                context.batcher.textShadow(label, tx, this.area.y + (this.area.h - context.batcher.getFont().getHeight()) / 2, active ? Colors.WHITE : color);
            }

            super.render(context);
        }
    }

    /** The cells: the entries that match the search, five to a row, scrolled with the wheel */
    public class Grid extends UIElement
    {
        private final List<T> entries = new ArrayList<>();
        private float scroll;

        public void filter(String text)
        {
            String needle = text.toLowerCase(Locale.ROOT).trim();

            this.entries.clear();

            boolean tabbed = !getTabs().isEmpty();

            for (T entry : getEntries())
            {
                if (needle.isEmpty() ? !tabbed || inTab(entry, tabs.tab) : matches(entry, needle))
                {
                    this.entries.add(entry);
                }
            }

            this.scroll = 0F;
        }

        private int cellWidth()
        {
            return Math.max(40, (this.area.w - GAP * (COLUMNS - 1)) / COLUMNS);
        }

        private int cellHeight()
        {
            return Math.max(56, (int) (this.cellWidth() * cellAspect()));
        }

        private int maxScroll()
        {
            int rows = (this.entries.size() + COLUMNS - 1) / COLUMNS;

            return Math.max(0, rows * (this.cellHeight() + GAP) - GAP - this.area.h);
        }

        private T entryAt(UIContext context)
        {
            if (!this.area.isInside(context))
            {
                return null;
            }

            int w = this.cellWidth();
            int h = this.cellHeight();
            int x = context.mouseX - this.area.x;
            int y = context.mouseY - this.area.y + (int) this.scroll;
            int column = x / (w + GAP);
            int row = y / (h + GAP);
            int index = row * COLUMNS + column;

            if (column >= COLUMNS || x % (w + GAP) >= w || y % (h + GAP) >= h || index < 0 || index >= this.entries.size())
            {
                return null;
            }

            return this.entries.get(index);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            T entry = context.mouseButton == 0 ? this.entryAt(context) : null;

            if (entry != null)
            {
                UIUtils.playClick();
                pick(entry);
                UIPovGridPicker.this.close();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            if (this.area.isInside(context))
            {
                this.scroll = Math.max(0F, Math.min(this.maxScroll(), this.scroll - (float) context.mouseWheel * 30F));

                return true;
            }

            return super.subMouseScrolled(context);
        }

        @Override
        public void render(UIContext context)
        {
            this.scroll = Math.min(this.scroll, this.maxScroll());

            int w = this.cellWidth();
            int h = this.cellHeight();
            T hovered = this.entryAt(context);

            context.batcher.clip(this.area, context);

            for (int i = 0; i < this.entries.size(); i++)
            {
                T entry = this.entries.get(i);
                int x = this.area.x + (i % COLUMNS) * (w + GAP);
                int y = this.area.y + (i / COLUMNS) * (h + GAP) - (int) this.scroll;

                if (y + h < this.area.y || y > this.area.ey())
                {
                    continue;
                }

                boolean over = entry == hovered;
                int border = isCurrent(entry) ? Colors.A100 | BBSSettings.primaryColor.get() : over ? Colors.WHITE : Colors.A50 | 0x333333;

                context.batcher.box(x, y, x + w, y + h, border);
                context.batcher.box(x + 1, y + 1, x + w - 1, y + h - 1, over ? 0xff2a2f3a : 0xff1b1e26);
                renderCell(context, entry, x, y, w, h, over);
            }

            context.batcher.unclip(context);

            String card = hovered == null ? null : describe(hovered);

            if (card != null)
            {
                context.batcher.textCard(card, context.mouseX + 8, context.mouseY + 8);
            }

            super.render(context);
        }
    }
}
