package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;

public enum HubSortOrder
{
    POPULAR("popular", UIKeys.HUB_SORT_POPULAR),
    TOP_RATED("top_rated", UIKeys.HUB_SORT_TOP_RATED),
    RECENT("recent", UIKeys.HUB_SORT_RECENT),
    ALPHABETICAL("alphabetical", UIKeys.HUB_SORT_ALPHABETICAL);

    public final String id;
    public final IKey title;

    private HubSortOrder(String id, IKey title)
    {
        this.id = id;
        this.title = title;
    }
}
