package mchorse.bbs_mod.ui.film.viewport;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.Area;

/** How the film preview is cut into panes, from one to four */
public enum ViewportLayout
{
    /** One picture: the preview as it has always been */
    SINGLE(1, UIKeys.FILM_VIEWPORT_LAYOUT_SINGLE),
    /** Two side by side, left and right */
    SPLIT_VERTICAL(2, UIKeys.FILM_VIEWPORT_LAYOUT_VERTICAL),
    /** Two one above the other, top and bottom */
    SPLIT_HORIZONTAL(2, UIKeys.FILM_VIEWPORT_LAYOUT_HORIZONTAL),
    /** A main pane on the left and two stacked on the right */
    SPLIT_THREE(3, UIKeys.FILM_VIEWPORT_LAYOUT_THREE),
    /** Four in a two by two grid */
    QUAD(4, UIKeys.FILM_VIEWPORT_LAYOUT_QUAD);

    private static final int GAP = 2;

    public final int count;
    public final IKey label;

    ViewportLayout(int count, IKey label)
    {
        this.count = count;
        this.label = label;
    }

    /** Places the panes' rectangles inside the bounds, as many as the layout has */
    public void place(Area bounds, Area[] out)
    {
        int x = bounds.x;
        int y = bounds.y;
        int w = bounds.w;
        int h = bounds.h;
        int halfW = (w - GAP) / 2;
        int halfH = (h - GAP) / 2;

        switch (this)
        {
            case SPLIT_VERTICAL ->
            {
                out[0].set(x, y, halfW, h);
                out[1].set(x + halfW + GAP, y, w - halfW - GAP, h);
            }
            case SPLIT_HORIZONTAL ->
            {
                out[0].set(x, y, w, halfH);
                out[1].set(x, y + halfH + GAP, w, h - halfH - GAP);
            }
            case SPLIT_THREE ->
            {
                int main = Math.round((w - GAP) * 0.6F);
                int rest = w - main - GAP;

                out[0].set(x, y, main, h);
                out[1].set(x + main + GAP, y, rest, halfH);
                out[2].set(x + main + GAP, y + halfH + GAP, rest, h - halfH - GAP);
            }
            case QUAD ->
            {
                out[0].set(x, y, halfW, halfH);
                out[1].set(x + halfW + GAP, y, w - halfW - GAP, halfH);
                out[2].set(x, y + halfH + GAP, halfW, h - halfH - GAP);
                out[3].set(x + halfW + GAP, y + halfH + GAP, w - halfW - GAP, h - halfH - GAP);
            }
            default -> out[0].set(x, y, w, h);
        }
    }
}
