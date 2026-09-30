package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public enum HubCategory
{
    ALL("all", UIKeys.HUB_CATEGORY_ALL, Icons.GLOBE, 0x5A9EFF),
    MODELS("models", UIKeys.HUB_CATEGORY_MODELS, Icons.POSE, 0xFF7043),
    PARTICLES("particles", UIKeys.HUB_CATEGORY_PARTICLES, Icons.PARTICLE, 0x42A5F5),
    ANIMATIONS("animations", UIKeys.HUB_CATEGORY_ANIMATIONS, Icons.FILM, 0xAB47BC),
    SOUNDS("sounds", UIKeys.HUB_CATEGORY_SOUNDS, Icons.SOUND, 0x26A69A),
    RIGS("rigs", UIKeys.HUB_CATEGORY_RIGS, Icons.LIMB, 0xFFCA28),
    STRUCTURES("structures", UIKeys.HUB_CATEGORY_STRUCTURES, Icons.STRUCTURE, 0x8D6E63);

    public final String id;
    public final IKey title;
    public final Icon icon;
    public final int color;

    private HubCategory(String id, IKey title, Icon icon, int color)
    {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.color = color;
    }

    public static HubCategory fromId(String id)
    {
        if (id == null)
        {
            return ALL;
        }

        for (HubCategory category : values())
        {
            if (category.id.equalsIgnoreCase(id))
            {
                return category;
            }
        }

        return ALL;
    }
}
