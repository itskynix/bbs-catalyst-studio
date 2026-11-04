package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The quick director commands of Catalyst AI. Each is nothing but a command line the executor
 * understands, so a button and the same sentence typed by hand do exactly the same thing.
 */
public class CatalystAIActionRegistry
{
    public record QuickAction(String id, Icon icon, IKey label, IKey command)
    {}

    private static final List<QuickAction> ACTIONS = new ArrayList<>();

    static
    {
        register(new QuickAction("dolly", Icons.FRUSTUM, key("dolly"), key("dolly.command")));
        register(new QuickAction("horror", Icons.FILTER, key("horror"), key("horror.command")));
        register(new QuickAction("shake", Icons.EXCHANGE, key("shake"), key("shake.command")));
        register(new QuickAction("twirl", Icons.ARC, key("twirl"), key("twirl.command")));
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.catalyst_ai.quick." + id);
    }

    /** For addons: a command of their own on the quick bar. */
    public static void register(QuickAction action)
    {
        ACTIONS.add(action);
    }

    public static List<QuickAction> getActions()
    {
        return Collections.unmodifiableList(ACTIONS);
    }
}
