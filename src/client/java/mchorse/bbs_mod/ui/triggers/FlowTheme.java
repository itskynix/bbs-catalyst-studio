package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The flow editor's colours and shapes, read from BBS's active theme every frame: the surfaces of the
 * Glass Aero, Frutiger Aero, Flat Aero or Classic ladder, the accent colour, and how round the
 * corners are (Classic barely, the Aero glass themes the most, with a gloss on the headers).
 */
public class FlowTheme
{
    private static int opaque(int rgb)
    {
        return 0xff000000 | (rgb & 0xffffff);
    }

    public static boolean light()
    {
        return BBSSettings.lightSurfaces();
    }

    public static int radius()
    {
        return switch (BBSSettings.uiTheme())
        {
            case BBSSettings.THEME_CLASSIC -> 2;
            case BBSSettings.THEME_FLAT_AERO -> 4;
            case BBSSettings.THEME_FRUTIGER_AERO -> 7;
            default -> 6;
        };
    }

    /** Whether headers get a glassy highlight: the two glass themes do. */
    public static boolean gloss()
    {
        int theme = BBSSettings.uiTheme();

        return theme == BBSSettings.THEME_GLASS_AERO || theme == BBSSettings.THEME_FRUTIGER_AERO;
    }

    public static int accent()
    {
        return opaque(BBSSettings.primaryColor.get());
    }

    public static int board()
    {
        return opaque(BBSSettings.sunkenSurface());
    }

    public static int panel()
    {
        /* Solid: the side panels never show the world through them */
        return opaque(BBSSettings.baseSurface());
    }

    public static int chrome()
    {
        return opaque(BBSSettings.chromeSurface());
    }

    public static int body()
    {
        return 0xf2000000 | (BBSSettings.raisedSurface() & 0xffffff);
    }

    public static int field()
    {
        return opaque(BBSSettings.deepSurface());
    }

    public static int border()
    {
        return opaque(BBSSettings.dividerColor());
    }

    public static int gridMinor()
    {
        return light() ? 0x16000000 : 0x14ffffff;
    }

    public static int gridMajor()
    {
        return light() ? 0x30000000 : 0x40000000;
    }

    public static int text()
    {
        return light() ? 0xff1d2228 : Colors.WHITE;
    }

    public static int dim()
    {
        return light() ? 0xff56606c : 0xff8a94a0;
    }

    public static int wire()
    {
        return light() ? 0xff3c4652 : 0xffd8dde4;
    }

    /** A swatch of a type: its colour, or for colours a small rainbow, the type that is every colour. */
    public static void swatch(Batcher2D batcher, float x1, float y1, float x2, float y2, String type)
    {
        if (type.equals("color"))
        {
            batcher.box(x1, y1, x2 - x1, y2 - y1, 0xffff4d4d, 0xff4dff6a, 0xff4d8bff, 0xffffe14d);

            return;
        }

        rounded(batcher, x1, y1, x2, y2, (int) ((x2 - x1) / 2F), typeColor(type), true, true);
    }

    /* Shapes */

    /** How far row {@code i} of a corner of radius {@code r} is pulled in. */
    private static float inset(int r, int i)
    {
        float d = r - i - 0.5F;

        return r - (float) Math.sqrt(Math.max(0F, r * r - d * d));
    }

    /** A rectangle with round corners, top and bottom ones each optional. */
    public static void rounded(Batcher2D batcher, float x1, float y1, float x2, float y2, int r, int color, boolean top, boolean bottom)
    {
        r = (int) Math.min(r, Math.min((x2 - x1) / 2F, (y2 - y1) / 2F));

        for (int i = 0; i < r; i++)
        {
            float in = inset(r, i);

            if (top) batcher.box(x1 + in, y1 + i, x2 - in, y1 + i + 1, color);
            if (bottom) batcher.box(x1 + in, y2 - i - 1, x2 - in, y2 - i, color);
        }

        batcher.box(x1, y1 + (top ? r : 0), x2, y2 - (bottom ? r : 0), color);
    }

    /** A header strip with round top corners, its colour running from left to right. */
    public static void roundedHeader(Batcher2D batcher, float x1, float y1, float x2, float y2, int r, int left, int right)
    {
        r = (int) Math.min(r, Math.min((x2 - x1) / 2F, y2 - y1));

        for (int i = 0; i < r; i++)
        {
            float in = inset(r, i);

            batcher.gradientHBox(x1 + in, y1 + i, x2 - in, y1 + i + 1, left, right);
        }

        batcher.gradientHBox(x1, y1 + r, x2, y2, left, right);
    }

    /** The colour of a value type, on variables, data pins and data wires. */
    public static int typeColor(String type)
    {
        return switch (type)
        {
            case "boolean" -> 0xffd2453f;
            case "integer" -> 0xff2fc4a0;
            case "float" -> 0xff8bd84a;
            case "string" -> 0xffe85ad6;
            case "vec3" -> 0xfff2ec8c;
            case "entity" -> 0xff3aa1e8;
            case "block" -> 0xffe8892c;
            case "item" -> 0xff57dbe6;
            case "sound" -> 0xffd4a017;
            case "particle" -> 0xffb48cf0;
            case "entity_type" -> 0xff5c9be0;
            case "color" -> 0xffd65ce0;
            case "array" -> 0xff7f9cf5;
            default -> 0xff9aa3ad;
        };
    }
}
