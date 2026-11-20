package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The look every desktop window and modal shares: a drop shadow, dark glass in three tints (title,
 * body, footer), the faint light from the top, the seams between the three and, after the content,
 * a frame with a fine highlight just inside it. Corners are 6 pixels round.
 */
public class GlassChrome
{
    public static final int RADIUS = 6;
    public static final int BORDER = 0xff2a3444;

    /* The glass: the studio's dark blues at about 88 to 90 percent, so what is behind shows faintly */
    public static final int BODY = 0xe011161f;
    public static final int TITLE_BAR = 0xe6161d29;
    public static final int FOOTER = 0xe60d1118;

    private static final int SEAM = 0x66000000 | (BORDER & 0xffffff);

    /**
     * Everything that goes under the content. {@code footerStart} is the row the footer begins on;
     * pass the area's bottom for a window without one.
     */
    public static void paint(UIContext context, Area a, int titleHeight, int footerStart, boolean front)
    {
        if (FrutigerAero.on())
        {
            FrutigerAero.chrome(context, a, titleHeight, footerStart, front);

            return;
        }

        int titleEnd = a.y + titleHeight;
        int r = RADIUS;

        /* The whole chrome goes to the GPU as one batch of quads */
        boolean opened = RoundedDraw.begin(context.batcher);

        shadow(context, a.x, a.y, a.ex(), a.ey(), front ? 12 : 8, front ? 0.55F : 0.35F);

        RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y, titleEnd, TITLE_BAR);
        RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, titleEnd, footerStart, BODY);
        RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, footerStart, a.ey(), FOOTER);

        /* Aero sheen: a faint light from the top that fades over the first stretch */
        int sheen = Math.min(72, a.h);

        for (int k = 0; k < sheen; k += SHEEN_STEP)
        {
            float fade = 1F - k / (float) sheen;

            RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y + k, a.y + k + SHEEN_STEP, Colors.setA(Colors.WHITE, 0.075F * fade * fade));
        }

        context.batcher.box(a.x + 1, titleEnd, a.ex() - 1, titleEnd + 1, SEAM);

        if (footerStart < a.ey())
        {
            context.batcher.box(a.x + 1, footerStart, a.ex() - 1, footerStart + 1, SEAM);
        }

        RoundedDraw.end(context.batcher, opened);
    }

    /** The sheen fades in steps this many rows tall; finer is invisible at its strength */
    private static final int SHEEN_STEP = 2;

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

    /** The inner highlight and the frame; drawn over the content so nothing covers them. */
    public static void frame(UIContext context, Area a, boolean front)
    {
        if (FrutigerAero.on())
        {
            FrutigerAero.frame(context, a, front);

            return;
        }

        int border = front ? (Colors.A100 | BBSSettings.primaryColor.get()) : BORDER;
        boolean opened = RoundedDraw.begin(context.batcher);

        RoundedDraw.outline(context.batcher, a.x + 1, a.y + 1, a.ex() - 1, a.ey() - 1, RADIUS - 1, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.10F));
        RoundedDraw.outline(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS, RoundedDraw.ALL, border);
        RoundedDraw.end(context.batcher, opened);
    }
}
