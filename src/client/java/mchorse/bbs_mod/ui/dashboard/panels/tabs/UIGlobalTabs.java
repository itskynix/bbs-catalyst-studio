package mchorse.bbs_mod.ui.dashboard.panels.tabs;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UICentralHubPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UICentralHubScreen;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.List;

/**
 * The strip along the very top of the dashboard: one tab per thing open, whichever editor it is
 * in. It owns no documents. A tab is either a Central Hub (an editor not picked yet), or an
 * editor panel together with the {@link DataTab} of that panel's own {@link UITabList} it shows —
 * so what a tab holds, and how it is saved, closed or renamed, stays the panel's business and the
 * strip only keeps the order and points at things.
 *
 * <p>Tabs the panels open by themselves (a scene opened from a list, say) are picked up from the
 * panel the next time the strip is told things changed, so no editor needs to know this exists.</p>
 */
public class UIGlobalTabs implements IUITabs
{
    /** A tab: a Central Hub when {@link #panel} is null. {@link #tab} is null for panels without tabs of their own. */
    private static class Entry
    {
        public UIDashboardPanel panel;
        public DataTab tab;

        public Entry(UIDashboardPanel panel, DataTab tab)
        {
            this.panel = panel;
            this.tab = tab;
        }

        public boolean isHub()
        {
            return this.panel == null;
        }
    }

    public static final IKey CENTRAL_HUB = UIKeys.CENTRAL_HUB_TAB;

    private final UIDashboard dashboard;
    private final List<Entry> entries = new ArrayList<>();

    private UIDataTabs bar;
    private int current;

    /** Set while the strip itself moves between panels, so that does not read as a panel opening a tab. */
    private boolean busy;

    public UIGlobalTabs(UIDashboard dashboard)
    {
        this.dashboard = dashboard;

        this.entries.add(new Entry(null, null));
    }

    public void setBar(UIDataTabs bar)
    {
        this.bar = bar;

        this.sync();
    }

    /* Keeping up with the panels */

    /** The panels changed something (a tab opened, closed, a document loaded): catch up. */
    public void sync()
    {
        if (!this.busy)
        {
            this.reconcile();
        }

        if (this.bar != null)
        {
            this.bar.sync();
        }
    }

    /** The dashboard now shows another panel. */
    public void onPanelChanged()
    {
        this.sync();
    }

    private void reconcile()
    {
        /* A panel closed a tab on its own */
        this.entries.removeIf((entry) -> entry.tab != null && entry.panel instanceof UIEditorDashboardPanel editor && !editor.tabs.contains(entry.tab));

        UIDashboardPanel panel = this.dashboard.getPanels().panel;

        if (panel instanceof UIEditorDashboardPanel editor && UICentralHubScreen.isWorkspace(editor.getClass()))
        {
            /* What the panel on screen holds, and has a name for, belongs in the strip */
            for (DataTab tab : editor.tabs)
            {
                if (tab == editor.tabs.getCurrent() || tab.dataId != null)
                {
                    this.adopt(editor, tab);
                }
            }
        }
        else if (panel instanceof UICentralHubPanel)
        {
            if (this.findHub() < 0)
            {
                this.entries.add(new Entry(null, null));
            }
        }
        else if (panel != null && UICentralHubScreen.isWorkspace(panel.getClass()) && !(panel instanceof UIEditorDashboardPanel))
        {
            if (this.indexOf(panel, null) < 0)
            {
                this.entries.add(new Entry(panel, null));
            }
        }

        this.current = Math.max(0, Math.min(this.current, this.entries.size() - 1));
    }

    private void adopt(UIEditorDashboardPanel editor, DataTab tab)
    {
        if (this.indexOf(editor, tab) < 0)
        {
            this.entries.add(new Entry(editor, tab));
        }
    }

    private int indexOf(UIDashboardPanel panel, DataTab tab)
    {
        for (int i = 0; i < this.entries.size(); i++)
        {
            Entry entry = this.entries.get(i);

            if (entry.panel == panel && entry.tab == tab)
            {
                return i;
            }
        }

        return -1;
    }

    private int findHub()
    {
        for (int i = 0; i < this.entries.size(); i++)
        {
            if (this.entries.get(i).isHub())
            {
                return i;
            }
        }

        return -1;
    }

    private boolean isOwned(DataTab tab)
    {
        for (Entry entry : this.entries)
        {
            if (entry.tab == tab)
            {
                return true;
            }
        }

        return false;
    }

    /** Whether the dashboard is showing what this entry stands for. */
    private boolean isShown(Entry entry)
    {
        UIDashboardPanel shown = this.dashboard.getPanels().panel;

        if (entry.isHub())
        {
            return shown instanceof UICentralHubPanel;
        }

        if (entry.panel != shown)
        {
            return false;
        }

        return entry.tab == null || (entry.panel instanceof UIEditorDashboardPanel editor && editor.tabs.getCurrent() == entry.tab);
    }

    /* The Central Hub: where a tab becomes an editor */

    /** Open a new "Central Hub" tab and go to it — what + and Ctrl+N do. */
    public void openCentralHubTab()
    {
        this.entries.add(new Entry(null, null));
        this.activate(this.entries.size() - 1);
    }

    /** A card was picked: the Central Hub tab being looked at turns into that editor. */
    public void pick(Class<? extends UIDashboardPanel> target)
    {
        UIDashboardPanel panel = this.dashboard.getPanel(target);

        if (panel == null)
        {
            return;
        }

        int index = this.current >= 0 && this.current < this.entries.size() && this.entries.get(this.current).isHub() ? this.current : this.findHub();

        if (index < 0)
        {
            this.entries.add(new Entry(null, null));

            index = this.entries.size() - 1;
        }

        Entry entry = this.entries.get(index);

        entry.panel = panel;
        entry.tab = panel instanceof UIEditorDashboardPanel editor ? editor.tabs.takeEmptyTab((tab) -> !this.isOwned(tab)) : null;

        this.activate(index);

        /* Its own landing screen, not what was open there last */
        if (entry.tab != null && entry.tab.dataId == null && panel instanceof UIEditorDashboardPanel editor)
        {
            editor.syncLanding();
        }
    }

    private void activate(int index)
    {
        Entry entry = this.entries.get(index);

        this.busy = true;

        try
        {
            UIDashboardPanel target = entry.isHub() ? this.dashboard.getPanel(UICentralHubPanel.class) : entry.panel;

            if (target != null && this.dashboard.getPanels().panel != target)
            {
                this.dashboard.setPanel(target);
            }

            if (entry.tab != null && entry.panel instanceof UIEditorDashboardPanel editor)
            {
                editor.tabs.switchTab(entry.tab);
            }

            this.current = index;
        }
        finally
        {
            this.busy = false;
        }

        this.sync();
    }

    /* Removing */

    private void remove(Entry entry)
    {
        this.entries.remove(entry);

        if (entry.tab != null && entry.panel instanceof UIEditorDashboardPanel editor)
        {
            editor.tabs.closeTab(entry.tab);
        }
    }

    private void afterRemoval(int preferred)
    {
        if (this.entries.isEmpty())
        {
            this.entries.add(new Entry(null, null));
        }

        this.activate(Math.max(0, Math.min(preferred, this.entries.size() - 1)));
    }

    /* IUITabs */

    @Override
    public boolean areTabsEnabled()
    {
        return true;
    }

    @Override
    public int getTabCount()
    {
        return this.entries.size();
    }

    @Override
    public int getCurrentTab()
    {
        if (this.current >= 0 && this.current < this.entries.size() && this.isShown(this.entries.get(this.current)))
        {
            return this.current;
        }

        for (int i = 0; i < this.entries.size(); i++)
        {
            if (this.isShown(this.entries.get(i)))
            {
                return i;
            }
        }

        return -1;
    }

    @Override
    public IKey getTabLabel(int index)
    {
        Entry entry = this.entries.get(index);

        if (entry.isHub())
        {
            return CENTRAL_HUB;
        }

        if (entry.panel instanceof UIEditorDashboardPanel editor && entry.tab != null)
        {
            return entry.tab.dataId == null ? editor.getNewTabLabel() : editor.getTabLabel(entry.tab.dataId);
        }

        return IKey.constant(UIKeys.CENTRAL_HUB_TAB_SUFFIX.get().replace("%s", UICentralHubScreen.nameOf(entry.panel.getClass()).get()));
    }

    @Override
    public IKey getTabTooltip(int index)
    {
        Entry entry = this.entries.get(index);

        if (entry.panel instanceof UIEditorDashboardPanel editor && entry.tab != null)
        {
            return editor.getTabTooltip(entry.tab.dataId);
        }

        return null;
    }

    @Override
    public Icon getTabIcon(int index)
    {
        Entry entry = this.entries.get(index);

        if (entry.isHub())
        {
            return Icons.LAYOUT;
        }

        if (entry.panel instanceof UIEditorDashboardPanel editor && entry.tab != null && entry.tab.dataId != null)
        {
            return editor.getTabIcon(entry.tab.dataId);
        }

        return UICentralHubScreen.iconOf(entry.panel.getClass());
    }

    @Override
    public IKey getNewTabLabel()
    {
        return CENTRAL_HUB;
    }

    /** Never "new": the + button is always there, wherever you are. */
    @Override
    public boolean isNewTab(int index)
    {
        return false;
    }

    @Override
    public boolean canCloseTab(int index)
    {
        return index >= 0 && index < this.entries.size();
    }

    @Override
    public void addTab()
    {
        this.openCentralHubTab();
    }

    @Override
    public void switchTab(int index)
    {
        if (index >= 0 && index < this.entries.size())
        {
            this.activate(index);
        }
    }

    @Override
    public void closeTab(int index)
    {
        if (index < 0 || index >= this.entries.size())
        {
            return;
        }

        this.busy = true;

        try
        {
            this.remove(this.entries.get(index));
        }
        finally
        {
            this.busy = false;
        }

        this.afterRemoval(index > 0 ? index - 1 : 0);
    }

    @Override
    public void closeOtherTabs(int index)
    {
        this.closeWhere(index, (i) -> i != index);
    }

    @Override
    public void closeTabsLeft(int index)
    {
        this.closeWhere(index, (i) -> i < index);
    }

    @Override
    public void closeTabsRight(int index)
    {
        this.closeWhere(index, (i) -> i > index);
    }

    private void closeWhere(int keep, java.util.function.IntPredicate close)
    {
        if (keep < 0 || keep >= this.entries.size())
        {
            return;
        }

        Entry kept = this.entries.get(keep);
        List<Entry> doomed = new ArrayList<>();

        for (int i = 0; i < this.entries.size(); i++)
        {
            if (close.test(i))
            {
                doomed.add(this.entries.get(i));
            }
        }

        this.busy = true;

        try
        {
            for (Entry entry : doomed)
            {
                this.remove(entry);
            }
        }
        finally
        {
            this.busy = false;
        }

        this.afterRemoval(this.entries.indexOf(kept));
    }
}
