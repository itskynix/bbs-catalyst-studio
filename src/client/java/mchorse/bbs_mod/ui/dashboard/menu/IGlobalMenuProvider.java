package mchorse.bbs_mod.ui.dashboard.menu;

import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelActionBar;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;

/**
 * What a dashboard panel tells the application menu bar ({@link UIGlobalMenuBar}) about itself.
 *
 * <p>The bar is one for the whole dashboard; as the panel on screen changes, its menus are asked
 * from the new panel. A panel that does not implement this still gets the File and Help menus, which
 * hold what is true of every editor. Everything here is optional: a menu with nothing to put in it is
 * left out of the bar rather than shown empty.</p>
 *
 * <p>Help is the one menu a panel cannot take away: its {@link #fillHelpMenu} entries only come
 * first, ahead of What's New and About.</p>
 */
public interface IGlobalMenuProvider
{
    /** Entries ahead of the ones every panel has (back to the hub, exit) in the File menu. */
    default void fillFileMenu(ContextMenuManager menu)
    {}

    default boolean hasEditMenu()
    {
        return false;
    }

    default void fillEditMenu(ContextMenuManager menu)
    {}

    default boolean hasWindowMenu()
    {
        return false;
    }

    default void fillWindowMenu(ContextMenuManager menu)
    {}

    default boolean hasToolsMenu()
    {
        return false;
    }

    default void fillToolsMenu(ContextMenuManager menu)
    {}

    default void fillHelpMenu(ContextMenuManager menu)
    {}

    /**
     * The row of quick action icons for the right end of the bar, or null for none. The bar borrows
     * the element while the panel is on screen and gives it back when it is not.
     */
    default UIPanelActionBar getQuickActions()
    {
        return null;
    }

    /** A short note in the middle of the bar's right side — a film locked by another player — or null. */
    default String getMenuBadge()
    {
        return null;
    }
}
