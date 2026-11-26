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
}
