package mchorse.bbs_mod.ui.dashboard.menu;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UICentralHubPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelActionBar;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UICentralHubScreen;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The application menu bar along the very top of the dashboard — File, Edit, Window &amp; Layout,
 * Tools, Help — and, at its right end, the quick action icons of the editor on screen.
 *
 * <p>There is one bar for every editor. It owns no content of its own: each time the dashboard shows
 * another panel, {@link #setPanel} asks that panel (an {@link IGlobalMenuProvider}) which menus it has,
 * and borrows its row of quick icons until the panel goes. File and Help are never empty: what is
 * true of every editor (back to the hub, exit; What's New, About) is added by the bar itself.</p>
 */
public class UIGlobalMenuBar extends UIElement
{
    public static final int HEIGHT = UIMenuBarItem.HEIGHT;

    private static final int TEXT_PADDING = 14;
    private static final int LEFT_MARGIN = 4;

    private final UIDashboard dashboard;

    private final UIMenuBarItem file;
    private final UIMenuBarItem edit;
    private final UIMenuBarItem window;
    private final UIMenuBarItem tools;
    private final UIMenuBarItem help;

    private UIDashboardPanel panel;
    private IGlobalMenuProvider provider;

    /** The quick icons borrowed from the panel on screen, and where they live when they are not on this bar. */
    private UIPanelActionBar borrowed;
    private UIElement borrowedHome;

    public UIGlobalMenuBar(UIDashboard dashboard)
    {
        this.dashboard = dashboard;

        this.file = this.item(UIKeys.MENU_FILE, this::fillFileMenu);
        this.edit = this.item(UIKeys.MENU_EDIT, this::fillEditMenu);
        this.window = this.item(UIKeys.MENU_WINDOW, this::fillWindowMenu);
        this.tools = this.item(UIKeys.MENU_TOOLS, this::fillToolsMenu);
        this.help = this.item(UIKeys.MENU_HELP, this::fillHelpMenu);

        this.layoutItems();
    }

    private UIMenuBarItem item(IKey label, Consumer<ContextMenuManager> filler)
    {
        UIMenuBarItem item = new UIMenuBarItem(label, (b) ->
        {
            this.getContext().replaceContextMenu(filler, b.area.x, b.area.ey() + 1);
        });

        this.add(item);

        return item;
    }

    /* Following the panel on screen */

    /** The dashboard shows another panel: its menus and its quick icons take over the bar. */
    public void setPanel(UIDashboardPanel panel)
    {
        this.returnBorrowed();

        this.panel = panel;
        this.provider = panel instanceof IGlobalMenuProvider menuProvider ? menuProvider : null;

        UIPanelActionBar actions = this.provider == null ? null : this.provider.getQuickActions();

        if (actions != null)
        {
            this.borrowedHome = actions.getParent();
            this.borrowed = actions;

            actions.attachTo(this);
        }

        this.edit.setVisible(this.provider != null && this.provider.hasEditMenu());
        this.tools.setVisible(this.provider != null && this.provider.hasToolsMenu());

        this.layoutItems();
        this.resize();
    }

    /** Give the quick icons back to the panel they belong to; it still renders them if its own top bar is ever shown. */
    private void returnBorrowed()
    {
        if (this.borrowed != null && this.borrowedHome != null)
        {
            this.borrowed.attachTo(this.borrowedHome);
        }

        this.borrowed = null;
        this.borrowedHome = null;
    }

    /** Titles go left to right, each as wide as its text; the hidden ones take no room. */
    private void layoutItems()
    {
        int x = LEFT_MARGIN;

        for (UIMenuBarItem item : new UIMenuBarItem[] {this.file, this.edit, this.window, this.tools, this.help})
        {
            if (!item.isVisible())
            {
                continue;
            }

            int width = Batcher2D.getDefaultTextRenderer().getWidth(item.label.get()) + TEXT_PADDING;

            item.relative(this).x(x).y(0).wh(width, HEIGHT);

            x += width;
        }
    }

    @Override
    protected void afterResizeApplied()
    {
        if (this.borrowed != null)
        {
            this.borrowed.w(this.borrowed.getContentWidth());
        }

        super.afterResizeApplied();
    }

    /* The menus */

    private void fillFileMenu(ContextMenuManager menu)
    {
        if (this.provider != null)
        {
            this.provider.fillFileMenu(menu);
        }

        if (!(this.panel instanceof UICentralHubPanel))
        {
            menu.action(Icons.LAYOUT, UIKeys.MENU_FILE_BACK_TO_HUB, this.dashboard::returnToHub);
        }

        menu.action(Icons.CLOSE, UIKeys.MENU_FILE_EXIT, this.dashboard::closeThisMenu);
    }

    private void fillEditMenu(ContextMenuManager menu)
    {
        if (this.provider != null)
        {
            this.provider.fillEditMenu(menu);
        }
    }

    /** The editor's own layout when it has one; otherwise a way to move between the editors. */
    private void fillWindowMenu(ContextMenuManager menu)
    {
        if (this.provider != null && this.provider.hasWindowMenu())
        {
            this.provider.fillWindowMenu(menu);

            return;
        }

        menu.action(this.panel instanceof UICentralHubPanel ? Icons.CHECKMARK : Icons.GLOBE, UIKeys.MENU_WINDOW_HUB, this.dashboard::returnToHub);

        for (Class<? extends UIDashboardPanel> target : UICentralHubScreen.workspaces())
        {
            boolean current = this.panel != null && this.panel.getClass() == target;

            menu.action(current ? Icons.CHECKMARK : UICentralHubScreen.iconOf(target), UICentralHubScreen.nameOf(target), () -> this.dashboard.openHubTarget(target));
        }
    }

    private void fillToolsMenu(ContextMenuManager menu)
    {
        if (this.provider != null)
        {
            this.provider.fillToolsMenu(menu);
        }
    }

    private void fillHelpMenu(ContextMenuManager menu)
    {
        if (this.provider != null)
        {
            this.provider.fillHelpMenu(menu);
        }

        menu.action(Icons.FIVE_STAR, UIKeys.MENU_HELP_WHATS_NEW, () -> this.dashboard.openChangelog(this.getContext()));
        menu.action(Icons.HELP, UIKeys.MENU_HELP_ABOUT, () -> this.dashboard.openAbout(this.getContext()));
    }

    /* Drawing */

    @Override
    public void render(UIContext context)
    {
        /* The icons are measured by the panel that owns them, and that can change while it is on screen */
        if (this.borrowed != null && this.borrowed.getContentWidth() != this.borrowed.area.w)
        {
            this.resize();
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.chromeSurface());
        context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

        super.render(context);

        String badge = this.provider == null ? null : this.provider.getMenuBadge();

        if (badge != null)
        {
            int w = context.batcher.getFont().getWidth(badge) + 8;
            int right = this.borrowed != null ? this.borrowed.area.x : this.area.ex();
            int x = right - w - 6;

            context.batcher.box(x, this.area.y + 2, x + w, this.area.ey() - 3, 0xCC8B1A1A);
            context.batcher.textShadow(badge, x + 4, this.area.my(context.batcher.getFont().getHeight()), Colors.WHITE);
        }
    }
}
