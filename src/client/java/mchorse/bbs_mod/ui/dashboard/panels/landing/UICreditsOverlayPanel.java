package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Who made what this studio stands on: the four names of its lineage and what each did, with the
 * licences they gave it under.
 */
public class UICreditsOverlayPanel extends UIOverlayPanel
{
    /** Name, role key, licence key (empty for none) */
    private static final String[][] CREDITS = {
        {"McHorse", "mchorse", "mit"},
        {"Wemppy", "wemppy", ""},
        {"qualett", "qualett", "mit"},
        {"Kynix", "kynix", ""}
    };

    private static final int[] COLORS = {0xFF4C9CFF, 0xFF6CE08A, 0xFFFFB347, 0xFFB48CFF};

    public UICreditsOverlayPanel()
    {
        super(L10n.lang("bbs.credits.title"));

        this.content.add(new UIRenderable((context) ->
        {
            int x = this.content.area.x + 14;
            int w = this.content.area.w - 28;
            int y = this.content.area.y + 12;

            for (int i = 0; i < CREDITS.length; i++)
            {
                String[] credit = CREDITS[i];
                int height = 52;

                context.batcher.box(x, y, x + w, y + height, 0xFF181D2A);
                context.batcher.box(x, y, x + 3, y + height, COLORS[i]);
                context.batcher.text("§l" + credit[0], x + 12, y + 7, Colors.WHITE, false);

                int used = context.batcher.wallText(L10n.lang("bbs.credits." + credit[1]).get(), x + 12, y + 20, Colors.setA(Colors.WHITE, 0.7F), w - 24, 11);

                if (!credit[2].isEmpty())
                {
                    context.batcher.text(L10n.lang("bbs.credits.license." + credit[2]).get(), x + 12, y + 22 + used, Colors.setA(Colors.WHITE, 0.4F), false);
                }

                y += height + 6;
            }

            context.batcher.wallText(L10n.lang("bbs.credits.more").get(), x, y + 2, Colors.setA(Colors.WHITE, 0.45F), w, 11);
        }));
    }

    public static IKey title()
    {
        return L10n.lang("bbs.credits.title");
    }
}
