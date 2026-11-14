package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Rounded rectangles out of plain boxes: a row at a time in the corners, one box for the rest.
 * Which corners are rounded is a bit mask, so a title bar can round its top and a footer its bottom.
 * Shapes are given by their corners like {@link Batcher2D#box}; bands cut a shape to a stretch of rows,
 * which is how one rounded window gets its title, body and footer in three colours without overlap.
 *
 * <p>Every shape goes out as one batch of quads: {@link Batcher2D#box} is otherwise a draw call of its
 * own, and a rounded window is a few dozen boxes. Only the corner rows are drawn a row at a time; the
 * straight stretch between them is one box (fill) or two (outline, ring), whatever the height. Nothing
 * here allocates.</p>
 */
public class RoundedDraw
{
    public static final int TL = 1;
    public static final int TR = 2;
    public static final int BL = 4;
    public static final int BR = 8;
    public static final int TOP = TL | TR;
    public static final int BOTTOM = BL | BR;
    public static final int ALL = TOP | BOTTOM;

    /** Insets of the arc by row, worked out once per radius; radii past this are computed on the spot */
    private static final int CACHED = 32;
    private static final int[][] INSETS = new int[CACHED + 1][];

    static
    {
        for (int r = 0; r <= CACHED; r++)
        {
            INSETS[r] = new int[r];

            for (int row = 0; row < r; row++)
            {
                INSETS[r][row] = computeInset(row, r);
            }
        }
    }

    private static int computeInset(int row, int radius)
    {
        double dy = radius - row - 0.5D;

        return (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
    }

    /** How far in the arc starts on row {@code row} (0 is the outermost row). */
    private static int inset(int row, int radius)
    {
        if (row >= radius || row < 0)
        {
            return 0;
        }

        return radius <= CACHED ? INSETS[radius][row] : computeInset(row, radius);
    }

    private static int left(int y, int y1, int y2, int r, int corners)
    {
        int top = y - y1;
        int bottom = y2 - 1 - y;
        int value = 0;

        if ((corners & TL) != 0 && top < r)
        {
            value = Math.max(value, inset(top, r));
        }

        if ((corners & BL) != 0 && bottom < r)
        {
            value = Math.max(value, inset(bottom, r));
        }

        return value;
    }

    private static int right(int y, int y1, int y2, int r, int corners)
    {
        int top = y - y1;
        int bottom = y2 - 1 - y;
        int value = 0;

        if ((corners & TR) != 0 && top < r)
        {
            value = Math.max(value, inset(top, r));
        }

        if ((corners & BR) != 0 && bottom < r)
        {
            value = Math.max(value, inset(bottom, r));
        }

        return value;
    }

    /** Opens a quad batch unless one is open already; the result goes back into {@link #end}. */
    public static boolean begin(Batcher2D batcher)
    {
        if (batcher.isBatching())
        {
            return false;
        }

        batcher.beginBatch();

        return true;
    }

    public static void end(Batcher2D batcher, boolean opened)
    {
        if (opened)
        {
            batcher.endBatch();
        }
    }

    public static void fill(Batcher2D batcher, int x1, int y1, int x2, int y2, int r, int corners, int color)
    {
        band(batcher, x1, y1, x2, y2, r, corners, y1, y2, color);
    }

    /** The part of the rounded shape that lies between rows {@code by1} and {@code by2}. */
    public static void band(Batcher2D batcher, int x1, int y1, int x2, int y2, int r, int corners, int by1, int by2, int color)
    {
        by1 = Math.max(by1, y1);
        by2 = Math.min(by2, y2);

        if (by2 <= by1 || x2 <= x1 || Colors.getA(color) <= 0F)
        {
            return;
        }

        boolean opened = begin(batcher);
        int top = y1 + r;
        int bottom = y2 - r;
        int from = Math.max(by1, top);
        int to = Math.min(by2, bottom);

        if (to > from)
        {
            batcher.box(x1, from, x2, to, color);
        }

        for (int y = by1; y < Math.min(by2, top); y++)
        {
            batcher.box(x1 + left(y, y1, y2, r, corners), y, x2 - right(y, y1, y2, r, corners), y + 1, color);
        }

        for (int y = Math.max(by1, Math.max(bottom, top)); y < by2; y++)
        {
            batcher.box(x1 + left(y, y1, y2, r, corners), y, x2 - right(y, y1, y2, r, corners), y + 1, color);
        }

        end(batcher, opened);
    }

    /** A one pixel line round the shape. */
    public static void outline(Batcher2D batcher, int x1, int y1, int x2, int y2, int r, int corners, int color)
    {
        if (x2 <= x1 || y2 <= y1 || Colors.getA(color) <= 0F)
        {
            return;
        }

        boolean opened = begin(batcher);

        batcher.box(x1 + left(y1, y1, y2, r, corners), y1, x2 - right(y1, y1, y2, r, corners), y1 + 1, color);
        batcher.box(x1 + left(y2 - 1, y1, y2, r, corners), y2 - 1, x2 - right(y2 - 1, y1, y2, r, corners), y2, color);

        /* Past the rows next to a corner the sides are plain one pixel lines: one box each */
        int straightFrom = Math.min(y2 - 1, y1 + r + 1);
        int straightTo = Math.max(straightFrom, y2 - r - 1);

        for (int y = y1 + 1; y < y2 - 1; y++)
        {
            if (y == straightFrom && straightTo > straightFrom)
            {
                batcher.box(x1, straightFrom, x1 + 1, straightTo, color);
                batcher.box(x2 - 1, straightFrom, x2, straightTo, color);

                y = straightTo - 1;

                continue;
            }

            int il = left(y, y1, y2, r, corners);
            int ir = right(y, y1, y2, r, corners);
            int wl = Math.max(1, Math.max(left(y - 1, y1, y2, r, corners) - il, left(y + 1, y1, y2, r, corners) - il));
            int wr = Math.max(1, Math.max(right(y - 1, y1, y2, r, corners) - ir, right(y + 1, y1, y2, r, corners) - ir));

            batcher.box(x1 + il, y, x1 + il + wl, y + 1, color);
            batcher.box(x2 - ir - wr, y, x2 - ir, y + 1, color);
        }

        end(batcher, opened);
    }

    /**
     * A ring {@code thickness} wide around the rounded shape, its outer corners {@code thickness}
     * rounder: what is between the shape grown by the thickness and the shape itself. Rings of
     * neighbouring thicknesses tile without overlap, which is how the drop shadow is layered.
     */
    public static void ring(Batcher2D batcher, int x1, int y1, int x2, int y2, int r, int thickness, int color)
    {
        if (thickness <= 0 || x2 <= x1 || y2 <= y1 || Colors.getA(color) <= 0F)
        {
            return;
        }

        boolean opened = begin(batcher);
        int t = thickness;
        int ox1 = x1 - t;
        int oy1 = y1 - t;
        int ox2 = x2 + t;
        int oy2 = y2 + t;
        int or = r + t;

        /* Rows where neither shape curves: two boxes for the whole stretch */
        int straightFrom = y1 + r;
        int straightTo = Math.max(straightFrom, y2 - r);

        for (int y = oy1; y < oy2; y++)
        {
            if (y == straightFrom && straightTo > straightFrom)
            {
                batcher.box(ox1, straightFrom, x1, straightTo, color);
                batcher.box(x2, straightFrom, ox2, straightTo, color);

                y = straightTo - 1;

                continue;
            }

            int lo = ox1 + left(y, oy1, oy2, or, ALL);
            int ro = ox2 - right(y, oy1, oy2, or, ALL);

            if (y < y1 || y >= y2)
            {
                batcher.box(lo, y, ro, y + 1, color);
            }
            else
            {
                int li = x1 + left(y, y1, y2, r, ALL);
                int ri = x2 - right(y, y1, y2, r, ALL);

                batcher.box(lo, y, li, y + 1, color);
                batcher.box(ri, y, ro, y + 1, color);
            }
        }

        end(batcher, opened);
    }
}
