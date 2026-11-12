package mchorse.bbs_mod.ui.film.world;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Categorized World Timeline clip/operation picker menu modeled directly after {@code UIClipTypeMenu}.
 * Includes a top category tab strip, search bar, and an Addons (Eklentiler) tab that dynamically
 * scans and filters registered World Timeline addon providers (such as MTR).
 */
public class UIWorldClipTypeMenu extends UIContextMenu
{
    private static final int MENU_WIDTH = 240;
    private static final int MAX_MENU_HEIGHT = 270;
    private static final int TAB_HEIGHT = 22;
    private static final int SEARCH_HEIGHT = 20;
    private static final int ADDON_BAR_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;

    private static final Icon[] TAB_ICONS = new Icon[]
    {
        Icons.FORWARD,
        Icons.PLANE,
        Icons.TIME,
        Icons.POSE
    };

    private static final IKey[] TAB_LABELS = new IKey[]
    {
        UIKeys.WORLD_TIMELINE_MENU_TAB_TRANSIT,
        UIKeys.WORLD_TIMELINE_MENU_TAB_FLIGHT,
        UIKeys.WORLD_TIMELINE_MENU_TAB_STATION,
        UIKeys.WORLD_TIMELINE_MENU_TAB_ADDONS
    };

    private static int lastSelectedTab = WorldTimelineAddonRegistry.CATEGORY_TRANSIT;
    private static String lastSelectedAddonId = "";

    private final Consumer<WorldTimelineAddonRegistry.OperationEntry> callback;
    private final Runnable onBack;

    private UITextbox searchBox;
    private UIScrollView scrollArea;
    private UIElement addonFilterBar;
    private UIButton addonFilterButton;

    private int activeTab;
    private String activeAddonId;
    private String searchQuery = "";
    private final List<UICategoryTab> tabButtons = new ArrayList<>();

    public UIWorldClipTypeMenu(Consumer<WorldTimelineAddonRegistry.OperationEntry> callback)
    {
        this(callback, null);
    }

    public UIWorldClipTypeMenu(Consumer<WorldTimelineAddonRegistry.OperationEntry> callback, Runnable onBack)
    {
        super();

        this.callback = callback;
        this.onBack = onBack;
        this.activeTab = lastSelectedTab;
        this.activeAddonId = lastSelectedAddonId;

        WorldTimelineAddonRegistry.ensureInitialized();

        /* 1. Top strip: optional Back button + Category Tabs */
        int backW = this.onBack != null ? TAB_HEIGHT : 0;

        if (this.onBack != null)
        {
            UIIcon back = new UIIcon(Icons.ARROW_LEFT, (b) -> this.onBack.run());

            back.tooltip(UIKeys.GENERAL_BACK);
            back.relative(this).x(0).y(0).w(backW).h(TAB_HEIGHT);
            this.add(back);
        }

        UIElement tabStrip = UI.row(0);

        tabStrip.relative(this).x(backW).y(0).w(1F, -backW).h(TAB_HEIGHT);

        for (int i = 0; i < TAB_ICONS.length; i++)
        {
            final int tabIdx = i;
            UICategoryTab tab = new UICategoryTab(TAB_ICONS[i], TAB_LABELS[i], () -> this.selectTab(tabIdx));

            tab.h(TAB_HEIGHT);
            this.tabButtons.add(tab);
            tabStrip.add(tab);
        }

        this.add(tabStrip);

        /* 2. Search Bar */
        this.searchBox = new UITextbox(100, (text) ->
        {
            this.searchQuery = text.trim().toLowerCase(Locale.ROOT);
            this.rebuildList();
        });
        this.searchBox.placeholder(L10n.lang("bbs.ui.camera.fx.search"));
        this.searchBox.relative(this).x(4).y(TAB_HEIGHT + 2).w(1F, -8).h(SEARCH_HEIGHT);
        this.add(this.searchBox);

        /* 3. Dynamic Addon Selector Bar (shown when Addons tab is active) */
        this.addonFilterBar = new UIElement();
        this.addonFilterBar.relative(this).x(4).y(TAB_HEIGHT + SEARCH_HEIGHT + 4).w(1F, -8).h(ADDON_BAR_HEIGHT);

        this.addonFilterButton = new UIButton(this.getSelectedAddonLabel(), (b) -> this.cycleAddonFilter());
        this.addonFilterButton.full(this.addonFilterBar);
        this.addonFilterBar.add(this.addonFilterButton);
        this.add(this.addonFilterBar);

        /* 4. Scrollable Operation List */
        this.scrollArea = UI.scrollView(2, 4);
        this.scrollArea.relative(this).x(0).w(1F);
        this.add(this.scrollArea);

        this.updateTabVisuals();
        this.rebuildList();
    }

    private IKey getSelectedAddonLabel()
    {
        if (this.activeAddonId != null && !this.activeAddonId.isEmpty())
        {
            for (WorldTimelineAddonRegistry.AddonDescriptor addon : WorldTimelineAddonRegistry.getAddons())
            {
                if (this.activeAddonId.equals(addon.id))
                {
                    return addon.title;
                }
            }
        }

        return UIKeys.WORLD_TIMELINE_MENU_ALL_ADDONS;
    }

    private void cycleAddonFilter()
    {
        List<WorldTimelineAddonRegistry.AddonDescriptor> addons = WorldTimelineAddonRegistry.getAddons();

        if (addons.isEmpty())
        {
            this.activeAddonId = "";
        }
        else if (this.activeAddonId == null || this.activeAddonId.isEmpty())
        {
            this.activeAddonId = addons.get(0).id;
        }
        else
        {
            int idx = -1;

            for (int i = 0; i < addons.size(); i++)
            {
                if (this.activeAddonId.equals(addons.get(i).id))
                {
                    idx = i;
                    break;
                }
            }

            if (idx >= 0 && idx + 1 < addons.size())
            {
                this.activeAddonId = addons.get(idx + 1).id;
            }
            else
            {
                this.activeAddonId = "";
            }
        }

        lastSelectedAddonId = this.activeAddonId;
        this.addonFilterButton.label = this.getSelectedAddonLabel();
        this.rebuildList();
        UIUtils.playClick();
    }

    private void selectTab(int tabIdx)
    {
        this.activeTab = tabIdx;
        lastSelectedTab = tabIdx;
        this.updateTabVisuals();
        this.rebuildList();
        UIUtils.playClick();
    }

    private void updateTabVisuals()
    {
        for (int i = 0; i < this.tabButtons.size(); i++)
        {
            this.tabButtons.get(i).setActive(i == this.activeTab);
        }

        boolean showAddonBar = this.activeTab == WorldTimelineAddonRegistry.CATEGORY_ADDONS && this.searchQuery.isEmpty();

        this.addonFilterBar.setVisible(showAddonBar);
        this.addonFilterButton.label = this.getSelectedAddonLabel();
    }

    private void rebuildList()
    {
        this.scrollArea.removeAll();

        boolean showAddonBar = this.activeTab == WorldTimelineAddonRegistry.CATEGORY_ADDONS && this.searchQuery.isEmpty();

        this.addonFilterBar.setVisible(showAddonBar);

        int topPad = TAB_HEIGHT + SEARCH_HEIGHT + 4 + (showAddonBar ? ADDON_BAR_HEIGHT + 4 : 0);

        this.scrollArea.y(topPad).h(1F, -topPad);

        List<WorldTimelineAddonRegistry.OperationEntry> allEntries = this.searchQuery.isEmpty()
            ? WorldTimelineAddonRegistry.getOperationsForTab(this.activeTab, this.activeAddonId)
            : WorldTimelineAddonRegistry.getOperations();

        for (WorldTimelineAddonRegistry.OperationEntry entry : allEntries)
        {
            String name = entry.title.get();

            if (!this.searchQuery.isEmpty())
            {
                boolean matchesName = name.toLowerCase(Locale.ROOT).contains(this.searchQuery);
                boolean matchesId = entry.id.toLowerCase(Locale.ROOT).contains(this.searchQuery);

                if (!matchesName && !matchesId)
                {
                    continue;
                }
            }

            UIOperationRow row = new UIOperationRow(entry, () ->
            {
                if (this.callback != null)
                {
                    this.callback.accept(entry);
                }

                this.removeFromParent();
                UIUtils.playClick();
            });

            this.scrollArea.add(row);
        }

        this.resize();
    }

    @Override
    public boolean isEmpty()
    {
        return WorldTimelineAddonRegistry.getOperations().isEmpty();
    }

    @Override
    public void setMouse(UIContext context)
    {
        boolean showAddonBar = this.activeTab == WorldTimelineAddonRegistry.CATEGORY_ADDONS && this.searchQuery.isEmpty();
        int headerH = TAB_HEIGHT + SEARCH_HEIGHT + (showAddonBar ? ADDON_BAR_HEIGHT + 4 : 0) + 12;
        int itemCount = this.scrollArea.getChildren().size();
        int contentH = headerH + itemCount * (ROW_HEIGHT + 2);
        int finalH = Math.min(MAX_MENU_HEIGHT, Math.max(100, contentH));

        this.xy(context.mouseX(), context.mouseY()).w(MENU_WIDTH).h(finalH).bounds(context.menu.overlay, 5);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.removeFromParent();
            UIUtils.playClick();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int lineY = this.area.y + TAB_HEIGHT;

        context.batcher.box(this.area.x, lineY, this.area.ex(), lineY + 1, Colors.A50);
    }

    private static class UICategoryTab extends UIElement
    {
        private final Icon icon;
        private final Runnable onClick;
        private boolean active;

        public UICategoryTab(Icon icon, IKey tooltip, Runnable onClick)
        {
            super();

            this.icon = icon;
            this.onClick = onClick;
            this.tooltip(tooltip);
        }

        public void setActive(boolean active)
        {
            this.active = active;
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                this.onClick.run();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            boolean hovered = this.area.isInside(context);
            int primary = BBSSettings.primaryColor.get();

            if (this.active)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50 | primary);
                context.batcher.box(this.area.x, this.area.ey() - 2, this.area.ex(), this.area.ey(), Colors.A100 | primary);
            }
            else if (hovered)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.setA(Colors.WHITE, 0.08F));
            }

            int iconColor = this.active ? Colors.WHITE : (hovered ? Colors.LIGHTEST_GRAY : Colors.GRAY);

            context.batcher.icon(this.icon, iconColor, this.area.mx(), this.area.my(), 0.5F, 0.5F);

            super.render(context);
        }
    }

    private static class UIOperationRow extends UIElement
    {
        private final WorldTimelineAddonRegistry.OperationEntry entry;
        private final Runnable onPick;

        public UIOperationRow(WorldTimelineAddonRegistry.OperationEntry entry, Runnable onPick)
        {
            super();

            this.entry = entry;
            this.onPick = onPick;
            this.h(ROW_HEIGHT);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                this.onPick.run();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            boolean hovered = this.area.isInside(context);
            FontRenderer font = context.batcher.getFont();

            if (hovered)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50 | BBSSettings.primaryColor.get());
            }

            /* Left color bar matching UIClipTypeMenu */
            context.batcher.box(this.area.x, this.area.y + 2, this.area.x + 3, this.area.ey() - 2, Colors.A100 | this.entry.color);

            int iconColor = hovered ? Colors.WHITE : (Colors.A100 | this.entry.color);

            context.batcher.icon(this.entry.icon, iconColor, this.area.x + 12, this.area.my(), 0.5F, 0.5F);

            String label = font.limitToWidth(this.entry.title.get(), Math.max(20, this.area.w - 30));
            int textColor = hovered ? Colors.WHITE : Colors.LIGHTEST_GRAY;

            context.batcher.textShadow(label, this.area.x + 24, this.area.my() - font.getHeight() / 2, textColor);

            super.render(context);
        }
    }
}
