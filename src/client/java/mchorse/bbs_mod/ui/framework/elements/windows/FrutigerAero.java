package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The Frutiger Aero look (Windows Vista and 7): glossy glass the colour of a clear sky over water,
 * with emerald and turquoise gel buttons. Switched on in Settings, Personalization, and read every
 * frame, so it takes over the moment it is ticked.
 *
 * <p>Its signature is the gloss: every glassy surface is lighter in its top half and has a sharp
 * horizontal light just under the middle, the horizon of a curved glass catching the sky. Under that
 * light everything is a vertical gradient. Rims are a crisp ocean blue with a fine white highlight
 * inside and a faint turquoise glow along the bottom.</p>
 *
 * <p>Everything here draws through the rounded batch, so a whole window is still one batch of quads.
 * The light surfaces make the white text and icons of the studio turn a dark ocean navy (see
 * {@link BBSSettings#lightSurfaces()} and the batcher), so they read on the glass.</p>
 */
public final class FrutigerAero
{
    /* The window */
    public static final int BODY_TOP = 0xdbe6f4ff;
    public static final int BODY_BOTTOM = 0xdbcbe6fa;
    public static final int TITLE_TOP = 0xf28fd3ff;
    public static final int TITLE_BOTTOM = 0xf23ba4e6;
    public static final int FOOTER = 0xe6d4ecfc;
    public static final int RIM = 0xff2b7ab8;
    public static final int RIM_BACK = 0xff6fa3cc;
    public static final int CAUSTIC = 0xff5fe0f0;

    /** The dark ocean navy that white text and icons turn into on the glass */
    public static final int INK = 0x0f2742;

    /* Gel and glass controls */
    public static final int TURQUOISE_TOP = 0xff29b6f6;
    public static final int TURQUOISE_BOTTOM = 0xff0288d1;
    public static final int EMERALD_TOP = 0xff38d39f;
    public static final int EMERALD_BOTTOM = 0xff0f9d58;
    public static final int GLASS_TOP = 0xfff5fbff;
    public static final int GLASS_BOTTOM = 0xffd6ecfa;
    public static final int GLASS_RIM = 0xff8cb4d2;
    public static final int GLASS_RIM_HOVER = 0xff4da6e0;
    public static final int FROST = 0xffc4dfee;
    public static final int MENU = 0xf2f0f8ff;

    private FrutigerAero()
    {}

    public static boolean on()
    {
        return BBSSettings.frutigerAero();
    }

    /** A straight blend of two colours, alpha included */
    public static int mix(int a, int b, float t)
    {
        t = Math.max(0F, Math.min(1F, t));

        int aa = a >>> 24, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = b >>> 24, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;

        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /**
     * A vertical gradient over rows {@code top} to {@code bottom} of a rounded shape, in bands of a
     * couple of rows: at most about 24 of them, whatever the height.
     */
    public static void gradient(Batcher2D batcher, int x1, int y1, int x2, int y2, int r, int corners, int top, int bottom, int from, int to)
    {
        int h = bottom - top;

        if (h <= 0)
        {
            return;
        }

        int step = Math.max(1, h / 24);

        for (int y = top; y < bottom; y += step)
        {
            float t = h <= 1 ? 0F : (y - top) / (float) (h - 1);

            RoundedDraw.band(batcher, x1, y1, x2, y2, r, corners, y, Math.min(bottom, y + step), mix(from, to, t));
        }
    }

    /**
     * The gloss of curved glass over rows {@code top} to {@code bottom}: the upper part lighter, fading
     * down to a sharp horizontal light at 48 percent of the height.
     */
    public static void gloss(Batcher2D batcher, int x1, int y1, int x2, int y2, int r, int corners, int top, int bottom, float strength)
    {
        int h = bottom - top;

        if (h < 4)
        {
            return;
        }

        int horizon = top + Math.round(h * 0.48F);

        for (int y = top; y < horizon; y++)
        {
            float t = (y - top) / (float) Math.max(1, horizon - top);

            RoundedDraw.band(batcher, x1, y1, x2, y2, r, corners, y, y + 1, Colors.setA(Colors.WHITE, strength * (0.55F - 0.3F * t)));
        }

        RoundedDraw.band(batcher, x1, y1, x2, y2, r, corners, horizon, horizon + 1, Colors.setA(Colors.WHITE, strength * 0.75F));
    }

    /* Windows */

    /** Everything a window draws under its content: shadow, title, body, footer and their seams */
    public static void chrome(UIContext context, Area a, int titleHeight, int footerStart, boolean front)
    {
        int r = GlassChrome.RADIUS;
        int titleEnd = a.y + titleHeight;
        Batcher2D b = context.batcher;
        boolean opened = RoundedDraw.begin(b);

        GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), front ? 12 : 8, front ? 0.35F : 0.22F);

        gradient(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y, titleEnd, front ? TITLE_TOP : mix(TITLE_TOP, BODY_TOP, 0.5F), front ? TITLE_BOTTOM : mix(TITLE_BOTTOM, BODY_BOTTOM, 0.5F));
        gloss(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y, titleEnd, 1F);
        gradient(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, titleEnd, footerStart, BODY_TOP, BODY_BOTTOM);

        if (footerStart < a.ey())
        {
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, footerStart, a.ey(), FOOTER);
            b.box(a.x + 1, footerStart, a.ex() - 1, footerStart + 1, 0xff9cc8e8);
            b.box(a.x + 1, footerStart + 1, a.ex() - 1, footerStart + 2, Colors.setA(Colors.WHITE, 0.9F));
        }

        /* The seam between the sky of the title and the ice of the body */
        b.box(a.x + 1, titleEnd, a.ex() - 1, titleEnd + 1, 0x992b7ab8);
        b.box(a.x + 1, titleEnd + 1, a.ex() - 1, titleEnd + 2, Colors.setA(Colors.WHITE, 0.55F));

        RoundedDraw.end(b, opened);
    }

    /** The rim over the content: a fine white light inside, the ocean blue frame, a turquoise glow along the bottom */
    public static void frame(UIContext context, Area a, boolean front)
    {
        int r = GlassChrome.RADIUS;
        Batcher2D b = context.batcher;
        boolean opened = RoundedDraw.begin(b);

        RoundedDraw.band(b, a.x + 1, a.y + 1, a.ex() - 1, a.ey() - 1, r - 1, RoundedDraw.ALL, a.ey() - 4, a.ey() - 1, Colors.setA(CAUSTIC, front ? 0.35F : 0.18F));
        RoundedDraw.outline(b, a.x + 1, a.y + 1, a.ex() - 1, a.ey() - 1, r - 1, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.75F));
        RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, front ? RIM : RIM_BACK);
        RoundedDraw.end(b, opened);
    }

    /** The red crystal close capsule of Vista and 7: soft at rest, a glowing ruby under the mouse */
    public static void close(Batcher2D batcher, Area a, boolean hover)
    {
        int x1 = a.x + 3;
        int y1 = a.y + 3;
        int x2 = a.ex() - 3;
        int y2 = a.ey() - 3;
        int r = Math.min(4, (y2 - y1) / 2);
        boolean opened = RoundedDraw.begin(batcher);

        gradient(batcher, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, y2, hover ? 0xffff3b30 : 0xffe8786e, hover ? 0xffc81e1e : 0xffb8372b);
        gloss(batcher, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, y2, hover ? 1F : 0.8F);
        RoundedDraw.outline(batcher, x1, y1, x2, y2, r, RoundedDraw.ALL, hover ? 0xff8b0f0f : 0xff9a3a33);
        RoundedDraw.end(batcher, opened);
    }

    /* Buttons */

    /**
     * A button's body: the primary one a turquoise gel capsule, the secondary one bright ice glass with a
     * blue-grey crystal rim; both with the gloss and a soft glow along the bottom.
     */
    public static void button(Batcher2D batcher, Area a, int radius, boolean primary, boolean hover)
    {
        boolean opened = RoundedDraw.begin(batcher);
        int top = primary ? (hover ? 0xff4fc3f7 : TURQUOISE_TOP) : (hover ? 0xffffffff : GLASS_TOP);
        int bottom = primary ? (hover ? 0xff039be5 : TURQUOISE_BOTTOM) : (hover ? 0xffe3f2fd : GLASS_BOTTOM);

        gradient(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y, a.ey(), top, bottom);
        gloss(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y, a.ey(), primary ? 0.85F : 0.9F);
        RoundedDraw.band(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.ey() - 3, a.ey() - 1, Colors.setA(primary ? 0xb3e5fc : 0xffffff, 0.45F));
        RoundedDraw.outline(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, primary ? 0xcc01579b : (hover ? GLASS_RIM_HOVER : GLASS_RIM));
        RoundedDraw.end(batcher, opened);
    }

    /* Switches, sliders and text boxes */

    /** The switch's well: frosted glass off, an emerald gel on */
    public static void toggleWell(Batcher2D batcher, int x, int y, int w, int h, boolean on, boolean hover)
    {
        int r = h / 2;

        if (on)
        {
            gradient(batcher, x, y, x + w, y + h, r, RoundedDraw.ALL, y, y + h, hover ? 0xff52e0b0 : EMERALD_TOP, EMERALD_BOTTOM);
            gloss(batcher, x, y, x + w, y + h, r, RoundedDraw.ALL, y, y + h, 0.8F);
            RoundedDraw.outline(batcher, x, y, x + w, y + h, r, RoundedDraw.ALL, 0xcc0b6b3c);
        }
        else
        {
            RoundedDraw.fill(batcher, x, y, x + w, y + h, r, RoundedDraw.ALL, hover ? 0xffd2e8f4 : FROST);
            RoundedDraw.band(batcher, x, y, x + w, y + h, r, RoundedDraw.ALL, y, y + 2, 0x30000000);
            RoundedDraw.outline(batcher, x, y, x + w, y + h, r, RoundedDraw.ALL, 0xff7fa9c6);
        }
    }

    /** A white pearl: round, with a light on its top and a fine rim */
    public static void pearl(Batcher2D batcher, int x, int y, int size)
    {
        int r = size / 2;

        RoundedDraw.fill(batcher, x, y, x + size, y + size, r, RoundedDraw.ALL, 0xfff7fbff);
        RoundedDraw.band(batcher, x, y, x + size, y + size, r, RoundedDraw.ALL, y + size / 2, y + size, 0xffdcebf5);
        RoundedDraw.band(batcher, x, y, x + size, y + size, r, RoundedDraw.ALL, y + 1, y + 1 + Math.max(1, size / 4), 0xffffffff);
        RoundedDraw.outline(batcher, x, y, x + size, y + size, r, RoundedDraw.ALL, 0xff9db8cc);
    }

    /** A slider: a frosted rail filled with water blue up to the thumb, and a glass sphere for a thumb */
    public static void slider(Batcher2D batcher, int start, int end, int cy, int thumbX, boolean lit, int base)
    {
        RoundedDraw.fill(batcher, start, cy - 2, end, cy + 2, 2, RoundedDraw.ALL, FROST);
        RoundedDraw.outline(batcher, start, cy - 2, end, cy + 2, 2, RoundedDraw.ALL, 0xff7fa9c6);

        if (thumbX > start)
        {
            gradient(batcher, start, cy - 2, thumbX, cy + 2, 2, RoundedDraw.ALL, cy - 2, cy + 2, TURQUOISE_TOP, TURQUOISE_BOTTOM);
        }

        int size = lit ? base + 2 : base;
        int x = thumbX - size / 2;
        int y = cy - size / 2;

        RoundedDraw.fill(batcher, x, y, x + size, y + size, size / 2, RoundedDraw.ALL, RIM);
        gradient(batcher, x + 1, y + 1, x + size - 1, y + size - 1, size / 2 - 1, RoundedDraw.ALL, y + 1, y + size - 1, 0xffffffff, 0xffbfe3f7);
        gloss(batcher, x + 1, y + 1, x + size - 1, y + size - 1, size / 2 - 1, RoundedDraw.ALL, y + 1, y + size - 1, 0.9F);
    }

    /** A text box: clean ice white, a blue-grey rim; focused, a turquoise rim with a soft aura round it */
    public static void textbox(Batcher2D batcher, Area a, int radius, boolean focused)
    {
        boolean opened = RoundedDraw.begin(batcher);

        if (focused)
        {
            RoundedDraw.outline(batcher, a.x - 1, a.y - 1, a.ex() + 1, a.ey() + 1, radius + 1, RoundedDraw.ALL, 0x5529b6f6);
            RoundedDraw.outline(batcher, a.x - 2, a.y - 2, a.ex() + 2, a.ey() + 2, radius + 2, RoundedDraw.ALL, 0x2229b6f6);
        }

        RoundedDraw.fill(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, focused ? 0xffffffff : 0xf2f8fcff);
        RoundedDraw.band(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y, a.y + 2, 0x18000000);
        RoundedDraw.outline(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, focused ? TURQUOISE_TOP : GLASS_RIM);
        RoundedDraw.end(batcher, opened);
    }

    /* Menus */

    /** A context menu's ground: frosted bright glass with the gloss along its top */
    public static void menu(UIContext context, Area a, int radius)
    {
        Batcher2D b = context.batcher;
        boolean opened = RoundedDraw.begin(b);

        GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), 10, 0.3F);
        gradient(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y, a.ey(), MENU, 0xf2e2f1fc);
        gloss(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y, Math.min(a.ey(), a.y + 40), 0.5F);
        RoundedDraw.end(b, opened);
    }

    /** The rim of a context menu */
    public static void menuFrame(Batcher2D batcher, Area a, int radius)
    {
        RoundedDraw.outline(batcher, a.x + 1, a.y + 1, a.ex() - 1, a.ey() - 1, radius - 1, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.8F));
        RoundedDraw.outline(batcher, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, 0xff7fb2da);
    }

    /** The row under the mouse: a water blue glass pill */
    public static void pill(Batcher2D batcher, int x1, int y1, int x2, int y2)
    {
        gradient(batcher, x1, y1, x2, y2, 4, RoundedDraw.ALL, y1, y2, 0xffcdeeff, 0xff8fd3ff);
        gloss(batcher, x1, y1, x2, y2, 4, RoundedDraw.ALL, y1, y2, 0.7F);
        RoundedDraw.outline(batcher, x1, y1, x2, y2, 4, RoundedDraw.ALL, 0xff4da6e0);
    }

    /** A menu separator on the glass */
    public static int separator()
    {
        return Colors.setA(INK, 0.15F);
    }
}
