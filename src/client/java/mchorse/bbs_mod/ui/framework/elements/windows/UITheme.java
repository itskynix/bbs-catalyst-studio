package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The four looks of the studio's windows and controls, picked in Settings, Personalization, and
 * read every frame so switching needs no restart:
 *
 * <ul>
 *     <li><b>Glass Aero</b>, the default: deep see-through liquid glass over the blurred scene, with
 *     a crystal rim that catches the light, bright white along the top and left, a faint ice blue
 *     along the bottom and right.</li>
 *     <li><b>Frutiger Aero</b>: glossy sky blue glass and gel capsules, see {@link FrutigerAero}.</li>
 *     <li><b>Flat Aero</b>: no gradient, gloss or glow; flat matte graphite like an editing suite.</li>
 *     <li><b>Classic</b>: square and opaque dark grey like the original BBS, without glass or
 *     shadows (the square corners come from {@link RoundedDraw#radius(int)}).</li>
 * </ul>
 *
 * <p>Every window, button, switch, slider, text box and context menu draws its ground through here,
 * each in one rounded batch as before.</p>
 */
public final class UITheme
{
    /* Glass Aero */
    private static final int GLASS = 0x0e131d;
    private static final int GLASS_RIM_OUT = 0x99050810;
    private static final int GLASS_ICE = 0x7cc6ff;

    /* Flat Aero */
    private static final int FLAT_BODY = 0xff151922;
    private static final int FLAT_TITLE = 0xff10131a;
    private static final int FLAT_EDGE = 0xff2a3140;
    private static final int FLAT_EDGE_FRONT = 0xff3a4458;
    private static final int FLAT_CONTROL = 0xff1e2330;
    private static final int FLAT_CONTROL_HOVER = 0xff272d3c;

    /* Classic */
    private static final int CLASSIC_BODY = 0xff181818;
    private static final int CLASSIC_TITLE = 0xff222222;
    private static final int CLASSIC_EDGE = 0xff0a0a0a;
    private static final int CLASSIC_CONTROL = 0xff2a2a2a;
    private static final int CLASSIC_CONTROL_HOVER = 0xff353535;

    private static final int CLOSE_RED = 0xffe81123;

    private UITheme()
    {}

    public static int get()
    {
        return BBSSettings.uiTheme();
    }

    public static boolean glass()
    {
        return get() == BBSSettings.THEME_GLASS_AERO;
    }

    public static boolean flat()
    {
        return get() == BBSSettings.THEME_FLAT_AERO;
    }

    public static boolean classic()
    {
        return get() == BBSSettings.THEME_CLASSIC;
    }

    /** Whether windows cast a soft shadow: all but Classic */
    public static boolean shadows()
    {
        return !classic();
    }

    public static int accent()
    {
        return Colors.A100 | BBSSettings.primaryColor.get();
    }

    /* Windows */

    /** Everything a window draws under its content. {@code footerStart} is the row its footer begins on */
    public static void chrome(UIContext context, Area a, int titleHeight, int footerStart, boolean front)
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.chrome(context, a, titleHeight, footerStart, front);

            return;
        }

        int r = GlassChrome.RADIUS;
        int titleEnd = a.y + titleHeight;
        Batcher2D b = context.batcher;
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), front ? 8 : 6, front ? 0.35F : 0.25F);
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y, titleEnd, FLAT_TITLE);
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, titleEnd, footerStart, FLAT_BODY);
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, footerStart, a.ey(), FLAT_TITLE);
            b.box(a.x + 1, titleEnd, a.ex() - 1, titleEnd + 1, FLAT_EDGE);

            if (footerStart < a.ey())
            {
                b.box(a.x + 1, footerStart, a.ex() - 1, footerStart + 1, FLAT_EDGE);
            }
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            b.box(a.x, a.y, a.ex(), titleEnd, CLASSIC_TITLE);
            b.box(a.x, titleEnd, a.ex(), footerStart, CLASSIC_BODY);
            b.box(a.x, footerStart, a.ex(), a.ey(), CLASSIC_TITLE);
            b.box(a.x, titleEnd, a.ex(), titleEnd + 1, CLASSIC_EDGE);

            if (footerStart < a.ey())
            {
                b.box(a.x, footerStart, a.ex(), footerStart + 1, CLASSIC_EDGE);
            }
        }
        else
        {
            /* Liquid glass: thin enough that the blurred scene behind flows through it */
            GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), front ? 12 : 8, front ? 0.45F : 0.3F);
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y, titleEnd, Colors.setA(GLASS, front ? 0.64F : 0.6F));
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, titleEnd, footerStart, Colors.setA(GLASS, 0.54F));
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, footerStart, a.ey(), Colors.setA(GLASS, 0.62F));

            /* The faint light the curved glass gathers at its top */
            int sheen = Math.min(48, a.h);

            for (int k = 0; k < sheen; k += 2)
            {
                float fade = 1F - k / (float) sheen;

                RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), r, RoundedDraw.ALL, a.y + k, a.y + k + 2, Colors.setA(Colors.WHITE, 0.06F * fade * fade));
            }

            b.box(a.x + 1, titleEnd, a.ex() - 1, titleEnd + 1, Colors.setA(Colors.WHITE, 0.1F));

            if (footerStart < a.ey())
            {
                b.box(a.x + 1, footerStart, a.ex() - 1, footerStart + 1, Colors.setA(Colors.WHITE, 0.08F));
            }
        }

        RoundedDraw.end(b, opened);
    }

    /** The frame over a window's content */
    public static void frame(UIContext context, Area a, boolean front)
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.frame(context, a, front);

            return;
        }

        Batcher2D b = context.batcher;
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), GlassChrome.RADIUS, RoundedDraw.ALL, front ? FLAT_EDGE_FRONT : FLAT_EDGE);
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), 0, RoundedDraw.ALL, front ? (accent() & 0xffffff | 0xff000000) : CLASSIC_EDGE);
        }
        else
        {
            specularRim(b, a.x, a.y, a.ex(), a.ey(), GlassChrome.RADIUS, front ? 1F : 0.7F);

            if (front)
            {
                RoundedDraw.outline(b, a.x - 1, a.y - 1, a.ex() + 1, a.ey() + 1, GlassChrome.RADIUS + 1, RoundedDraw.ALL, Colors.setA(accent(), 0.35F));
            }
        }

        RoundedDraw.end(b, opened);
    }

    /**
     * The edge of liquid glass: a see-through dark rim outside, and inside it light bent by the edge:
     * bright white along the top and down the left, thinning as it goes, and a faint ice blue along the
     * bottom and up the right.
     */
    public static void specularRim(Batcher2D b, int x1, int y1, int x2, int y2, int r, float strength)
    {
        int ix1 = x1 + 1;
        int iy1 = y1 + 1;
        int ix2 = x2 - 1;
        int iy2 = y2 - 1;
        int ir = Math.max(0, r - 1);
        int w = ix2 - ix1;
        int h = iy2 - iy1;

        RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, GLASS_RIM_OUT);
        RoundedDraw.outline(b, ix1, iy1, ix2, iy2, ir, RoundedDraw.ALL, Colors.setA(GLASS_ICE, 0.2F * strength));

        if (w <= ir * 2 || h <= ir * 2)
        {
            return;
        }

        /* The top light, brightest at the left and thinning to the right */
        int segments = 8;
        int span = w - ir * 2;

        for (int k = 0; k < segments; k++)
        {
            int sx1 = ix1 + ir + span * k / segments;
            int sx2 = ix1 + ir + span * (k + 1) / segments;
            float t = k / (float) segments;

            b.box(sx1, iy1, sx2, iy1 + 1, Colors.setA(Colors.WHITE, strength * (0.45F - 0.25F * t)));
        }

        /* Down the left, from the same brightness to nothing */
        int down = h - ir * 2;

        for (int k = 0; k < segments; k++)
        {
            int sy1 = iy1 + ir + down * k / segments;
            int sy2 = iy1 + ir + down * (k + 1) / segments;
            float t = k / (float) segments;

            b.box(ix1, sy1, ix1 + 1, sy2, Colors.setA(Colors.WHITE, strength * 0.38F * (1F - t)));
        }

        /* The corner between them, so the light turns round it */
        for (int k = 0; k < ir; k++)
        {
            b.box(ix1 + k, iy1 + ir - k - 1, ix1 + k + 1, iy1 + ir - k, Colors.setA(Colors.WHITE, strength * 0.4F));
        }
    }

    /** The close button: the red crystal capsule of Frutiger Aero, a red band flush with the corner elsewhere */
    public static void close(Batcher2D batcher, Area a, boolean hover)
    {
        if (FrutigerAero.on())
        {
            FrutigerAero.close(batcher, a, hover);

            return;
        }

        if (!hover)
        {
            return;
        }

        int color = glass() ? 0xd9e81123 : CLOSE_RED;

        RoundedDraw.band(batcher, a.x, a.y, a.ex(), a.ey(), GlassChrome.RADIUS - 1, RoundedDraw.TR, a.y, a.ey(), color);
    }

    /* Buttons */

    /** A button's body. The primary one is the thing to press, the secondary one never looks like it */
    public static void button(Batcher2D b, Area a, int radius, boolean primary, boolean hover)
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.button(b, a, radius, primary, hover);

            return;
        }

        int accent = accent();
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            int fill = primary ? (hover ? Colors.mulRGB(accent, 0.88F) : accent) : (hover ? FLAT_CONTROL_HOVER : FLAT_CONTROL);

            RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), Math.min(radius, 2), RoundedDraw.ALL, fill);

            if (!primary)
            {
                RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), Math.min(radius, 2), RoundedDraw.ALL, hover ? FLAT_EDGE_FRONT : FLAT_EDGE);
            }
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            int fill = primary ? (hover ? Colors.mulRGB(accent, 0.88F) : accent) : (hover ? CLASSIC_CONTROL_HOVER : CLASSIC_CONTROL);

            b.box(a.x, a.y, a.ex(), a.ey(), fill);
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), 0, RoundedDraw.ALL, CLASSIC_EDGE);
        }
        else
        {
            /* A capsule of liquid glass: the accent glowing through it for the primary one */
            if (primary)
            {
                if (hover)
                {
                    RoundedDraw.outline(b, a.x - 1, a.y - 1, a.ex() + 1, a.ey() + 1, radius + 1, RoundedDraw.ALL, Colors.setA(accent, 0.45F));
                    RoundedDraw.outline(b, a.x - 2, a.y - 2, a.ex() + 2, a.ey() + 2, radius + 2, RoundedDraw.ALL, Colors.setA(accent, 0.18F));
                }

                RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, Colors.setA(accent, hover ? 0.82F : 0.7F));
            }
            else
            {
                RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, Colors.setA(Colors.WHITE, hover ? 0.16F : 0.09F));
            }

            int glint = Math.max(2, a.h / 2 - 1);

            for (int k = 0; k < glint; k++)
            {
                float fade = 1F - k / (float) glint;

                RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y + k, a.y + k + 1, Colors.setA(Colors.WHITE, (primary ? 0.16F : 0.08F) * fade));
            }

            specularRim(b, a.x, a.y, a.ex(), a.ey(), radius, primary ? 0.9F : 0.75F);
        }

        RoundedDraw.end(b, opened);
    }

    /** The colour of a button's label for the themes other than Frutiger Aero */
    public static int buttonText(boolean primary, int contrast)
    {
        if (glass())
        {
            return 0xfff4f8ff;
        }

        return primary ? contrast : Colors.WHITE;
    }

    /* Switches */

    /** A switch's well and knob; {@code slide} goes from 0, off, to 1, on */
    public static void toggle(Batcher2D b, int x, int y, int w, int h, boolean on, boolean hover, float slide)
    {
        int theme = get();
        int accent = accent();
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            int pearl = h - 4 + (hover ? 1 : 0);
            int px = x + 2 + Math.round((w - h) * slide);

            FrutigerAero.toggleWell(b, x, y, w, h, on, hover);
            FrutigerAero.pearl(b, px, y + h / 2 - pearl / 2, pearl);
        }
        else if (theme == BBSSettings.THEME_FLAT_AERO || theme == BBSSettings.THEME_CLASSIC)
        {
            boolean flat = theme == BBSSettings.THEME_FLAT_AERO;
            int r = flat ? h / 2 : 0;

            RoundedDraw.fill(b, x, y, x + w, y + h, r, RoundedDraw.ALL, on ? (hover ? Colors.mulRGB(accent, 0.9F) : accent) : (flat ? (hover ? FLAT_CONTROL_HOVER : FLAT_CONTROL) : 0xff111111));
            RoundedDraw.outline(b, x, y, x + w, y + h, r, RoundedDraw.ALL, on ? Colors.mulRGB(accent, 0.75F) : (flat ? FLAT_EDGE_FRONT : 0xff3a3a3a));

            int knob = h - 6;
            int kx = x + 3 + Math.round((w - h) * slide);

            RoundedDraw.fill(b, kx, y + 3, kx + knob, y + 3 + knob, flat ? knob / 2 : 0, RoundedDraw.ALL, on ? 0xffeef1f6 : (flat ? 0xff9aa3b2 : 0xffbbbbbb));
        }
        else
        {
            int r = h / 2;

            RoundedDraw.fill(b, x, y, x + w, y + h, r, RoundedDraw.ALL, on ? Colors.setA(accent, hover ? 0.85F : 0.72F) : Colors.setA(Colors.WHITE, hover ? 0.14F : 0.08F));
            specularRim(b, x, y, x + w, y + h, r, 0.8F);

            /* A lens for a knob: clear glass with a bright crescent on top and a rim of light */
            int knob = (on ? h - 4 : h - 6) + (hover ? 1 : 0);
            int kx = x + (h - knob) / 2 + Math.round((w - h) * slide);
            int ky = y + (h - knob) / 2;
            int kr = knob / 2;

            RoundedDraw.fill(b, kx, ky, kx + knob, ky + knob, kr, RoundedDraw.ALL, on ? 0xfff4f8ff : 0xd9dfe6f0);
            RoundedDraw.band(b, kx, ky, kx + knob, ky + knob, kr, RoundedDraw.ALL, ky + knob / 2, ky + knob, Colors.setA(GLASS_ICE, 0.25F));
            RoundedDraw.band(b, kx, ky, kx + knob, ky + knob, kr, RoundedDraw.ALL, ky + 1, ky + 2, Colors.WHITE);
            RoundedDraw.outline(b, kx, ky, kx + knob, ky + knob, kr, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.9F));
        }

        RoundedDraw.end(b, opened);
    }

    /* Sliders */

    /** A slider's rail, filled up to the thumb, and its thumb; {@code base} is the thumb's size */
    public static void slider(Batcher2D b, int start, int end, int cy, int thumbX, boolean lit, int base)
    {
        int theme = get();
        int accent = accent();
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.slider(b, start, end, cy, thumbX, lit, base);
        }
        else if (theme == BBSSettings.THEME_FLAT_AERO || theme == BBSSettings.THEME_CLASSIC)
        {
            boolean flat = theme == BBSSettings.THEME_FLAT_AERO;

            b.box(start, cy - 1, end, cy + 1, flat ? FLAT_EDGE_FRONT : 0xff111111);

            if (thumbX > start)
            {
                b.box(start, cy - 1, thumbX, cy + 1, accent);
            }

            int size = (lit ? base : base - 2) - 2;
            int x = thumbX - size / 2;
            int y = cy - size / 2;

            if (flat)
            {
                RoundedDraw.fill(b, x, y, x + size, y + size, size / 2, RoundedDraw.ALL, lit ? 0xffffffff : 0xffdfe3ea);
            }
            else
            {
                int tw = Math.max(4, size / 2);

                b.box(thumbX - tw / 2, cy - size / 2, thumbX - tw / 2 + tw, cy + size / 2, lit ? 0xffdddddd : 0xffaaaaaa);
                RoundedDraw.outline(b, thumbX - tw / 2, cy - size / 2, thumbX - tw / 2 + tw, cy + size / 2, 0, RoundedDraw.ALL, CLASSIC_EDGE);
            }
        }
        else
        {
            RoundedDraw.fill(b, start, cy - 2, end, cy + 2, 2, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.14F));

            if (thumbX > start)
            {
                RoundedDraw.fill(b, start, cy - 2, thumbX, cy + 2, 2, RoundedDraw.ALL, Colors.setA(accent, 0.9F));
                RoundedDraw.band(b, start, cy - 2, thumbX, cy + 2, 2, RoundedDraw.ALL, cy - 2, cy - 1, Colors.setA(Colors.WHITE, 0.3F));
            }

            /* A glass lens for a thumb */
            int size = lit ? base : base - 2;
            int x = thumbX - size / 2;
            int y = cy - size / 2;
            int r = size / 2;

            RoundedDraw.fill(b, x, y, x + size, y + size, r, RoundedDraw.ALL, 0xe6eef3fa);
            RoundedDraw.band(b, x, y, x + size, y + size, r, RoundedDraw.ALL, y + size / 2, y + size, Colors.setA(GLASS_ICE, 0.3F));
            RoundedDraw.band(b, x, y, x + size, y + size, r, RoundedDraw.ALL, y + 1, y + 1 + Math.max(1, size / 4), Colors.WHITE);
            RoundedDraw.fill(b, x + size / 2 - 1, y + size / 2 - 1, x + size / 2 + 2, y + size / 2 + 2, 1, RoundedDraw.ALL, accent);
            RoundedDraw.outline(b, x, y, x + size, y + size, r, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.9F));
        }

        RoundedDraw.end(b, opened);
    }

    /* Text boxes */

    public static void textbox(Batcher2D b, Area a, int radius, boolean focused)
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.textbox(b, a, radius, focused);

            return;
        }

        int accent = accent();
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), Math.min(radius, 2), RoundedDraw.ALL, 0xff0e1117);
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), Math.min(radius, 2), RoundedDraw.ALL, focused ? accent : FLAT_EDGE);
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            b.box(a.x, a.y, a.ex(), a.ey(), 0xff0a0a0a);
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), 0, RoundedDraw.ALL, focused ? accent : 0xff333333);
        }
        else
        {
            if (focused)
            {
                RoundedDraw.outline(b, a.x - 1, a.y - 1, a.ex() + 1, a.ey() + 1, radius + 1, RoundedDraw.ALL, Colors.setA(accent, 0.3F));
            }

            RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, focused ? 0x8c080b12 : 0x66080b12);
            RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y, a.y + 2, 0x33000000);
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, focused ? accent : Colors.setA(Colors.WHITE, 0.18F));

            /* The light the glass catches along its bottom edge */
            RoundedDraw.band(b, a.x + 1, a.y + 1, a.ex() - 1, a.ey() - 1, Math.max(0, radius - 1), RoundedDraw.ALL, a.ey() - 2, a.ey() - 1, focused ? accent : Colors.setA(GLASS_ICE, 0.2F));
        }

        RoundedDraw.end(b, opened);
    }

    /* Context menus */

    public static void menu(UIContext context, Area a, int radius)
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.menu(context, a, radius);

            return;
        }

        Batcher2D b = context.batcher;
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), 6, 0.35F);
            RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, FLAT_BODY);
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            b.box(a.x, a.y, a.ex(), a.ey(), CLASSIC_TITLE);
        }
        else
        {
            GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), 10, 0.45F);
            RoundedDraw.fill(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, Colors.setA(GLASS, 0.72F));

            for (int k = 0; k < Math.min(32, a.h); k += 2)
            {
                float fade = 1F - k / 32F;

                RoundedDraw.band(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, a.y + k, a.y + k + 2, Colors.setA(Colors.WHITE, 0.06F * fade * fade));
            }
        }

        RoundedDraw.end(b, opened);
    }

    public static void menuFrame(Batcher2D b, Area a, int radius)
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.menuFrame(b, a, radius);

            return;
        }

        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), radius, RoundedDraw.ALL, FLAT_EDGE);
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            RoundedDraw.outline(b, a.x, a.y, a.ex(), a.ey(), 0, RoundedDraw.ALL, CLASSIC_EDGE);
        }
        else
        {
            specularRim(b, a.x, a.y, a.ex(), a.ey(), radius, 0.85F);
        }

        RoundedDraw.end(b, opened);
    }

    /** The row under the mouse in a context menu */
    public static void pill(Batcher2D b, int x1, int y1, int x2, int y2)
    {
        int theme = get();
        boolean opened = RoundedDraw.begin(b);

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            FrutigerAero.pill(b, x1, y1, x2, y2);
        }
        else if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, 2, RoundedDraw.ALL, 0xff232938);
        }
        else if (theme == BBSSettings.THEME_CLASSIC)
        {
            b.box(x1, y1, x2, y2, Colors.setA(accent(), 0.45F));
        }
        else
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, 4, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.11F));
            RoundedDraw.band(b, x1, y1, x2, y2, 4, RoundedDraw.ALL, y1, y1 + 1, Colors.setA(Colors.WHITE, 0.18F));
        }

        RoundedDraw.end(b, opened);
    }

    /** The colour of a separator line in a context menu */
    public static int separator()
    {
        int theme = get();

        if (theme == BBSSettings.THEME_FRUTIGER_AERO)
        {
            return FrutigerAero.separator();
        }

        if (theme == BBSSettings.THEME_FLAT_AERO)
        {
            return FLAT_EDGE;
        }

        if (theme == BBSSettings.THEME_CLASSIC)
        {
            return 0xff333333;
        }

        return Colors.setA(Colors.WHITE, 0.12F);
    }
}
