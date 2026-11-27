package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.windows.FrutigerAero;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.framework.elements.windows.UITheme;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * How Recap Studio and its pause menu transport are drawn under each Interface Theme. Not a tint per
 * theme but the surface itself, built from the same pieces the studio's windows use:
 *
 * <ul>
 *     <li><b>Glass Aero</b> — dark see-through glass, the light it gathers along its top, a crystal
 *     rim bright at the top left and ice blue at the bottom right, soft shadows, round corners.</li>
 *     <li><b>Frutiger Aero</b> — bright sky glass: a sky blue gel header with its gloss and horizon
 *     line, ice body, ocean blue rim and a turquoise glow along the bottom; ink coloured text.</li>
 *     <li><b>Flat Aero</b> — matte graphite planes, one solid colour each, no gradient, no gloss,
 *     thin one-pixel edges and barely rounded corners.</li>
 *     <li><b>Classic</b> — the original square, opaque look: hard corners, solid dark panels, black
 *     edges, no shadow.</li>
 * </ul>
 */
public final class RecapStyle
{
    private static final int GLASS = 0x0e131d;
    private static final int ICE = 0x7cc6ff;

    private static final int FLAT_HEADER = 0xff10131a;
    private static final int FLAT_BODY = 0xff151922;
    private static final int FLAT_WELL = 0xff0d1016;
    private static final int FLAT_EDGE = 0xff2a3140;
    private static final int FLAT_EDGE_FRONT = 0xff3a4458;

    private static final int CLASSIC_HEADER = 0xff222222;
    private static final int CLASSIC_BODY = 0xff181818;
    private static final int CLASSIC_WELL = 0xff0e0e0e;
    private static final int CLASSIC_EDGE = 0xff0a0a0a;

    private static final int INK = 0xff0f2742;

    private RecapStyle()
    {}

    public static boolean light()
    {
        return UITheme.get() == BBSSettings.THEME_FRUTIGER_AERO;
    }

    public static int radius()
    {
        if (UITheme.classic())
        {
            return 0;
        }

        return UITheme.flat() ? 2 : 6;
    }

    /* Text */

    public static int text()
    {
        return light() ? INK : Colors.WHITE;
    }

    public static int dim()
    {
        return light() ? Colors.setA(INK, 0.62F) : Colors.setA(Colors.WHITE, 0.55F);
    }

    public static int faint()
    {
        return light() ? Colors.setA(INK, 0.38F) : Colors.setA(Colors.WHITE, 0.32F);
    }

    /** Text on a panel's header: white on the sky blue gel of Frutiger Aero too. */
    public static int header()
    {
        return Colors.WHITE;
    }

    public static boolean headerShadow()
    {
        return light() || UITheme.glass();
    }

    public static int accent()
    {
        return UITheme.accent();
    }

    /** The red of the play head and recording dot, darker on the bright glass so it reads. */
    public static int red()
    {
        return light() ? 0xffd32f2f : 0xffff5252;
    }

    public static int marker()
    {
        return light() ? 0xffe08a00 : 0xffffc857;
    }

    /** A dimension's colour on the timeline: green, red and pale violet for the game's three, a steady one of its own for any other. */
    public static int dimension(String id)
    {
        if (id == null)
        {
            return faint();
        }

        switch (id)
        {
            case "minecraft:overworld": return light() ? 0xff3a9a48 : 0xff5cc46a;
            case "minecraft:the_nether": return light() ? 0xffc0392b : 0xffff6b5b;
            case "minecraft:the_end": return light() ? 0xff8e6cc8 : 0xffc9a8ff;
            default:
            {
                float hue = (id.hashCode() & 0xffff) / 65535F;

                return 0xff000000 | (java.awt.Color.HSBtoRGB(hue, 0.55F, light() ? 0.7F : 0.95F) & 0xffffff);
            }
        }
    }

    /* Backgrounds */

    /** Everything behind the editor's panels. */
    public static void backdrop(Batcher2D b, Area a)
    {
        if (light())
        {
            b.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0xff9fd4f5, 0xffd9eefb);
        }
        else if (UITheme.flat())
        {
            b.box(a.x, a.y, a.ex(), a.ey(), 0xff0b0d12);
        }
        else if (UITheme.classic())
        {
            b.box(a.x, a.y, a.ex(), a.ey(), 0xff101010);
        }
        else
        {
            b.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0xff0c111b, 0xff070a10);
        }
    }

    /**
     * A panel: its header band ({@code header} rows, none at 0) over its body, in the theme's own
     * material. {@code front} lifts it a little: the panel the pointer is over, or being dragged.
     */
    public static void surface(Batcher2D b, int x1, int y1, int x2, int y2, int header, boolean front)
    {
        int r = radius();
        int split = Math.min(y2, y1 + header);
        boolean opened = RoundedDraw.begin(b);

        if (light())
        {
            shadow(b, x1, y1, x2, y2, front ? 10 : 6, front ? 0.3F : 0.2F);

            if (header > 0)
            {
                FrutigerAero.gradient(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, split, FrutigerAero.TITLE_TOP, FrutigerAero.TITLE_BOTTOM);
                FrutigerAero.gloss(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, split, 1F);
                b.box(x1 + 1, split, x2 - 1, split + 1, 0x992b7ab8);
                b.box(x1 + 1, split + 1, x2 - 1, split + 2, Colors.setA(Colors.WHITE, 0.55F));
            }

            FrutigerAero.gradient(b, x1, y1, x2, y2, r, RoundedDraw.ALL, split + (header > 0 ? 2 : 0), y2, FrutigerAero.BODY_TOP, FrutigerAero.BODY_BOTTOM);
            RoundedDraw.band(b, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), RoundedDraw.ALL, y2 - 4, y2 - 1, Colors.setA(FrutigerAero.CAUSTIC, front ? 0.35F : 0.2F));
            RoundedDraw.outline(b, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.75F));
            RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, front ? FrutigerAero.RIM : FrutigerAero.RIM_BACK);
        }
        else if (UITheme.flat())
        {
            shadow(b, x1, y1, x2, y2, front ? 6 : 4, front ? 0.3F : 0.2F);

            if (header > 0)
            {
                RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, split, FLAT_HEADER);
                b.box(x1 + 1, split, x2 - 1, split + 1, FLAT_EDGE);
            }

            RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, split + (header > 0 ? 1 : 0), y2, FLAT_BODY);
            RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, front ? FLAT_EDGE_FRONT : FLAT_EDGE);
        }
        else if (UITheme.classic())
        {
            if (header > 0)
            {
                b.box(x1, y1, x2, split, CLASSIC_HEADER);
                b.box(x1, split, x2, split + 1, CLASSIC_EDGE);
            }

            b.box(x1, split + (header > 0 ? 1 : 0), x2, y2, CLASSIC_BODY);
            RoundedDraw.outline(b, x1, y1, x2, y2, 0, RoundedDraw.ALL, front ? (accent() & 0xffffff | 0xff000000) : CLASSIC_EDGE);
        }
        else
        {
            shadow(b, x1, y1, x2, y2, front ? 12 : 8, front ? 0.42F : 0.28F);

            if (header > 0)
            {
                RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, split, Colors.setA(GLASS, 0.7F));
            }

            RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, split, y2, Colors.setA(GLASS, 0.58F));

            /* The light the curved glass gathers along its top */
            int sheen = Math.min(40, y2 - y1);

            for (int k = 0; k < sheen; k += 2)
            {
                float fade = 1F - k / (float) sheen;

                RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y1 + k, y1 + k + 2, Colors.setA(Colors.WHITE, 0.06F * fade * fade));
            }

            if (header > 0)
            {
                b.box(x1 + 1, split, x2 - 1, split + 1, Colors.setA(Colors.WHITE, 0.1F));
            }

            UITheme.specularRim(b, x1, y1, x2, y2, r, front ? 1F : 0.7F);

            if (front)
            {
                RoundedDraw.outline(b, x1 - 1, y1 - 1, x2 + 1, y2 + 1, r + 1, RoundedDraw.ALL, Colors.setA(accent(), 0.35F));
            }
        }

        RoundedDraw.end(b, opened);
    }

    public static void surface(Batcher2D b, Area a, int header, boolean front)
    {
        surface(b, a.x, a.y, a.ex(), a.ey(), header, front);
    }

    /** A sunken area inside a panel: a list, the stage, the timeline lane. */
    public static void well(Batcher2D b, int x1, int y1, int x2, int y2)
    {
        int r = Math.max(0, radius() - 2);
        boolean opened = RoundedDraw.begin(b);

        if (light())
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, r, RoundedDraw.ALL, 0x99ffffff);
            RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y1, y1 + 2, 0x332b7ab8);
            RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, FrutigerAero.GLASS_RIM);
        }
        else if (UITheme.flat())
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, r, RoundedDraw.ALL, FLAT_WELL);
            RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, FLAT_EDGE);
        }
        else if (UITheme.classic())
        {
            b.box(x1, y1, x2, y2, CLASSIC_WELL);
            RoundedDraw.outline(b, x1, y1, x2, y2, 0, RoundedDraw.ALL, CLASSIC_EDGE);
        }
        else
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, r, RoundedDraw.ALL, 0x66000000);
            RoundedDraw.band(b, x1, y1, x2, y2, r, RoundedDraw.ALL, y2 - 1, y2, Colors.setA(ICE, 0.25F));
            RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, 0x30ffffff);
        }

        RoundedDraw.end(b, opened);
    }

    public static void well(Batcher2D b, Area a)
    {
        well(b, a.x, a.y, a.ex(), a.ey());
    }

    /** A control: the theme's own button, glass capsule, gel, matte plate or square block. */
    public static void button(Batcher2D b, int x1, int y1, int x2, int y2, boolean primary, boolean hover)
    {
        Area area = new Area();

        area.set(x1, y1, x2 - x1, y2 - y1);
        UITheme.button(b, area, Math.min(radius(), (y2 - y1) / 2), primary, hover);
    }

    /** What an icon on a button is drawn in. */
    public static int buttonIcon(boolean primary, boolean enabled)
    {
        if (!enabled)
        {
            return light() ? Colors.setA(INK, 0.3F) : 0x50ffffff;
        }

        if (light())
        {
            return primary ? Colors.WHITE : INK;
        }

        return Colors.WHITE;
    }

    /** A row of a list: picked, under the pointer, or at rest. */
    public static void row(Batcher2D b, int x1, int y1, int x2, int y2, boolean picked, boolean hover)
    {
        int r = Math.max(0, radius() - 2);
        boolean opened = RoundedDraw.begin(b);

        if (picked)
        {
            UITheme.pill(b, x1, y1, x2, y2);

            if (!light())
            {
                RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, Colors.setA(accent(), 0.8F));
            }
        }
        else if (light())
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, r, RoundedDraw.ALL, hover ? 0xccffffff : 0x80ffffff);
            RoundedDraw.outline(b, x1, y1, x2, y2, r, RoundedDraw.ALL, hover ? FrutigerAero.GLASS_RIM_HOVER : 0x608cb4d2);
        }
        else if (UITheme.flat())
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, r, RoundedDraw.ALL, hover ? 0xff1e2330 : 0xff171b24);
        }
        else if (UITheme.classic())
        {
            b.box(x1, y1, x2, y2, hover ? 0xff2a2a2a : 0xff1e1e1e);
        }
        else
        {
            RoundedDraw.fill(b, x1, y1, x2, y2, r, RoundedDraw.ALL, Colors.setA(Colors.WHITE, hover ? 0.09F : 0.04F));
        }

        RoundedDraw.end(b, opened);
    }

    /** A thin divider inside a panel. */
    public static int divider()
    {
        return UITheme.separator();
    }

    /** A soft shadow under a rounded shape; nothing under Classic. */
    public static void shadow(Batcher2D b, int x1, int y1, int x2, int y2, int size, float alpha)
    {
        if (!UITheme.shadows() || size <= 0)
        {
            return;
        }

        int rings = 4;
        int step = Math.max(1, (size + rings - 1) / rings);
        int r = radius();

        for (int k = 0, d = 0; k < rings && d < size; k++, d += step)
        {
            float fade = 1F - k / (float) rings;

            RoundedDraw.ring(b, x1 - d, y1 - d, x2 + d, y2 + d, r + d, step, Colors.setA(0, alpha * fade * fade));
        }
    }
}
