package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The look every desktop window and modal shares: a drop shadow, the ground in three tints (title,
 * body, footer) with the seams between them and, after the content, a frame. How each looks comes
 * from the interface theme, see {@link UITheme}. Corners are 6 pixels round, square under Classic.
 */
public class GlassChrome
{
    public static final int RADIUS = 6;
    public static final int BORDER = 0xff2a3444;

    /* The glass: the studio's dark blues at about 88 to 90 percent, so what is behind shows faintly */
    public static final int BODY = 0xe011161f;
    public static final int TITLE_BAR = 0xe6161d29;
    public static final int FOOTER = 0xe60d1118;

    /**
     * Everything that goes under the content. {@code footerStart} is the row the footer begins on;
     * pass the area's bottom for a window without one.
     */
    public static void paint(UIContext context, Area a, int titleHeight, int footerStart, boolean front)
    {
        /* The whole chrome goes to the GPU as one batch of quads, in the look of the picked theme */
        UITheme.chrome(context, a, titleHeight, footerStart, front);
    }

    /** The drop shadow is this many rings, however wide it is */
    private static final int SHADOW_RINGS = 4;

    /**
     * A soft shadow that follows the window's round corners: {@link #SHADOW_RINGS} rings side by side
     * around the shape, each as thick as a share of the size and as much rounder as it is far out,
     * fading with the square of the distance. The rings tile without overlap, so the glass above is
     * not darkened and no square corner pokes out past the round one.
     */
    public static void shadow(UIContext context, int x1, int y1, int x2, int y2, int size, float alpha)
    {
        if (!UITheme.shadows())
        {
            return;
        }

        int step = Math.max(1, (size + SHADOW_RINGS - 1) / SHADOW_RINGS);
        boolean opened = RoundedDraw.begin(context.batcher);

        for (int k = 0, d = 0; k < SHADOW_RINGS && d < size; k++, d += step)
        {
            float fade = 1F - k / (float) SHADOW_RINGS;
            int color = Colors.setA(0, alpha * fade * fade);

            RoundedDraw.ring(context.batcher, x1 - d, y1 - d, x2 + d, y2 + d, RADIUS + d, step, color);
        }

        RoundedDraw.end(context.batcher, opened);
    }

    /** The red of the Windows attention flash. */
    public static final int FLASH = 0xe81123;

    /**
     * The attention flash over a window's frame: the border turns red and one pixel thicker, by
     * {@code intensity} from 0 (the usual frame) to 1 (full red).
     */
    public static void flash(UIContext context, Area a, float intensity)
    {
        if (intensity <= 0F)
        {
            return;
        }

        int color = Colors.setA(FLASH, Math.min(1F, intensity));
        boolean opened = RoundedDraw.begin(context.batcher);

        RoundedDraw.outline(context.batcher, a.x - 1, a.y - 1, a.ex() + 1, a.ey() + 1, RADIUS + 1, RoundedDraw.ALL, color);
        RoundedDraw.outline(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS, RoundedDraw.ALL, color);
        RoundedDraw.end(context.batcher, opened);
    }

    /** The frame and its inner light, in the look of the picked theme; drawn over the content so nothing covers them. */
    public static void frame(UIContext context, Area a, boolean front)
    {
        UITheme.frame(context, a, front);
    }
}
