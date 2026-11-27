package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

/** The recap studio's strings, all under {@code bbs.recap}. */
public final class UIRecapKeys
{
    private UIRecapKeys()
    {}

    public static IKey key(String id)
    {
        return L10n.lang("bbs.recap." + id);
    }

    public static String text(String id)
    {
        return key(id).get();
    }

    /** A dimension's name: the three of the game by their own strings, any other by its id. */
    public static String dimensionName(String id)
    {
        if (id == null || id.isEmpty())
        {
            return "?";
        }

        switch (id)
        {
            case "minecraft:overworld": return text("dimension.overworld");
            case "minecraft:the_nether": return text("dimension.the_nether");
            case "minecraft:the_end": return text("dimension.the_end");
            default: return id;
        }
    }
}
