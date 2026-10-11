package mchorse.bbs_mod.ui.utils.resizers.layout;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.resizers.AutomaticResizer;
import mchorse.bbs_mod.ui.utils.resizers.ChildResizer;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;

public class GridResizer extends AutomaticResizer
{
    private int i;
    private int x;
    private int y;
    private int h;

    /**
     * How many elements in a row
     */
    private int items = 2;

    /**
     * If above zero, what is the width of every cell should be,
     * instead of items per row
     */
    private int width = 0;

    /**
     * Whether this resizes changes the bounds of the area
     */
    private boolean resizes = true;

    private int paddingX = -1;
    private int paddingY = -1;
    private boolean center = false;
    private boolean scroll = false;

    public static GridResizer apply(UIElement element, int margin)
    {
        GridResizer resizer = new GridResizer(element, margin);

        element.post(resizer);

        return resizer;
    }

    protected GridResizer(UIElement parent, int margin)
    {
        super(parent, margin);
    }

    public GridResizer padding(int x, int y)
    {
        this.paddingX = x;
        this.paddingY = y;

        return this;
    }

    public GridResizer center()
    {
        this.center = true;

        return this;
    }

    public GridResizer scroll()
    {
        this.scroll = true;
        this.resizes = false;

        return this;
    }

    public int getPaddingX()
    {
        return this.paddingX >= 0 ? this.paddingX : this.padding;
    }

    public int getPaddingY()
    {
        return this.paddingY >= 0 ? this.paddingY : this.padding;
    }

    public GridResizer resizes(boolean resizes)
    {
        this.resizes = resizes;

        return this;
    }

    public GridResizer items(int items)
    {
        this.items = items;

        return this;
    }

    /**
     * If you use this feature, make sure to resize the elements twice, as
     * the it needs parent's width, and it's not available on the first
     * layout resizing pass
     */
    public GridResizer width(int width)
    {
        this.width = width;

        return this;
    }

    @Override
    public void apply(Area area)
    {
        this.i = this.x = this.y = this.h = 0;

        if (this.center && this.width > 0 && this.parent.area.w > 0)
        {
            int availableW = this.parent.area.w;
            int cols = Math.max(1, (availableW + this.margin) / (this.width + this.margin));
            int totalW = cols * this.width + (cols - 1) * this.margin;
            this.paddingX = Math.max(this.padding, (availableW - totalW) / 2);
        }
    }

    @Override
    public void apply(Area area, IResizer resizer, ChildResizer child)
    {
        /* Same as in the column: a hidden child keeps no cell in the grid */
        if (!child.element.isVisible())
        {
            area.set(this.parent.area.x, this.parent.area.y, 0, 0);

            return;
        }

        int px = this.getPaddingX();
        int py = this.getPaddingY();

        int w;
        int h;
        int x;
        int y;

        if (this.width > 0)
        {
            if (this.x + this.width > this.parent.area.w - px * 2)
            {
                this.y += this.h + this.margin;
                this.h = 0;
                this.x = 0;
            }

            w = this.width;
            h = resizer == null ? 0 : resizer.getH();
            x = this.parent.area.x + px + this.x;
            y = this.parent.area.y + py + this.y;

            if (h <= 0)
            {
                h = this.height;
            }

            if (h <= 0)
            {
                h = w;
            }

            this.h = Math.max(this.h, h);

            area.set(x, y, w, h);

            this.x += this.width + this.margin;
        }
        else
        {
            if (this.i != 0 && this.i % this.items == 0)
            {
                this.y += this.h + this.margin;
                this.h = 0;
                this.i = 0;
            }

            w = (this.parent.area.w - px * 2 - this.margin * (this.items - 1)) / this.items;
            h = resizer == null ? 0 : resizer.getH();
            x = this.parent.area.x + px + (w + this.margin) * this.i;
            y = this.parent.area.y + py + this.y;

            if (h <= 0)
            {
                h = this.height;
            }

            if (h <= 0)
            {
                h = w;
            }

            this.h = Math.max(this.h, h);
            this.i ++;

            area.set(x, y, w, h);
        }
    }

    @Override
    public void postApply(Area area)
    {
        if (this.scroll && this.parent.area.scroll != null)
        {
            Scroll scroll = this.parent.area.scroll;

            if (scroll.direction == ScrollDirection.VERTICAL)
            {
                int totalH = this.computeTotalH();

                scroll.scrollItemSize = 1;
                scroll.setSize(totalH);
            }

            scroll.clamp();
        }
    }

    public int computeTotalH()
    {
        int i = 0;
        int x = 0;
        int y = 0;
        int maxH = 0;
        int width = this.parent.area.w;
        int px = this.getPaddingX();
        int py = this.getPaddingY();

        if (this.center && this.width > 0 && width > 0)
        {
            int cols = Math.max(1, (width + this.margin) / (this.width + this.margin));
            int totalW = cols * this.width + (cols - 1) * this.margin;
            px = Math.max(this.padding, (width - totalW) / 2);
        }

        if (this.width > 0)
        {
            for (ChildResizer child : this.getResizers())
            {
                if (!child.element.isVisible())
                {
                    continue;
                }

                if (x + this.width > width - px * 2)
                {
                    y += maxH + this.margin;
                    maxH = 0;
                    x = 0;
                }

                int w = this.width;
                int h = child.resizer == null ? 0 : child.resizer.getH();

                if (h <= 0)
                {
                    h = this.height;
                }

                if (h <= 0)
                {
                    h = w;
                }

                maxH = Math.max(maxH, h);
                x += this.width + this.margin;
            }
        }
        else
        {
            for (ChildResizer child : this.getResizers())
            {
                if (!child.element.isVisible())
                {
                    continue;
                }

                if (i != 0 && i % this.items == 0)
                {
                    y += maxH + this.margin;
                    maxH = 0;
                    i = 0;
                }

                int w = (width - px * 2 - this.margin * (this.items - 1)) / this.items;
                int h = child.resizer == null ? 0 : child.resizer.getH();

                if (h <= 0)
                {
                    h = this.height;
                }

                if (h <= 0)
                {
                    h = w;
                }

                maxH = Math.max(maxH, h);

                i++;
            }
        }

        return y + maxH + py * 2;
    }

    @Override
    public int getH()
    {
        if (this.resizes && !this.scroll)
        {
            return this.computeTotalH();
        }

        return super.getH();
    }
}