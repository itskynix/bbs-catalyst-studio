package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel.UIDrawerTabButton;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout.UIDockSlot;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The edit page's dockable layout, the film editor's dock with Catalyst's own panels: the library,
 * the program monitor, the inspector and the timeline. The monitor and the timeline are core: they
 * have no title bar and cannot be closed or moved. The library and the inspector can be dragged,
 * stacked, closed, or shrunk into a drawer, a tab on the left or right edge that slides the panel
 * over the page when it is clicked.
 */
public class CatalystDock
{
    private static final String LAYOUT = ValueEditorLayout.CATALYST;

    public final UIDockLayout dock = new UIDockLayout();

    private final UIElement host;
    private final Map<String, Drawer> drawers = new LinkedHashMap<>();

    public CatalystDock(UIElement host, UIElement media, UIElement monitor, UIElement inspector, UIElement timeline)
    {
        this.host = host;

        this.dock.relative(host).x(0).y(0).w(1F).h(1F);
        this.dock.source(this.createSource())
            .locked(!BBSSettings.editorLayoutSettings.isDockUnlocked(LAYOUT))
            .frameless(CatalystLayouts.MONITOR)
            .core(CatalystLayouts.MONITOR, CatalystLayouts.TIMELINE)
            .closeHandler(this::closePanel)
            .drawerHandler(this::dockAsDrawer);
        this.dock.addPanel(CatalystLayouts.MEDIA, media, Icons.FOLDER, UIKeys.CATALYST_NLE_PANEL_MEDIA);
        this.dock.addPanel(CatalystLayouts.MONITOR, monitor, Icons.VIDEO_CAMERA, UIKeys.CATALYST_NLE_PANEL_MONITOR);
        this.dock.addPanel(CatalystLayouts.INSPECTOR, inspector, Icons.PROPERTIES, UIKeys.CATALYST_NLE_PANEL_INSPECTOR);
        this.dock.addPanel(CatalystLayouts.TIMELINE, timeline, Icons.FILM, UIKeys.CATALYST_NLE_PANEL_TIMELINE);
        this.dock.mount();

        host.add(this.dock);

        /* The library drawer sits on the left edge, the inspector's on the right */
        this.addDrawer(CatalystLayouts.MEDIA, media, Icons.FOLDER, UIKeys.CATALYST_NLE_PANEL_MEDIA, true);
        this.addDrawer(CatalystLayouts.INSPECTOR, inspector, Icons.PROPERTIES, UIKeys.CATALYST_NLE_PANEL_INSPECTOR, false);
    }

    private ILayoutSource createSource()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        return new ILayoutSource()
        {
            @Override
            public EditorLayoutNode getRoot()
            {
                return layout.getLayout(LAYOUT, () -> CatalystLayouts.tree(CatalystLayouts.DEFAULT));
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                layout.setLayout(LAYOUT, root);
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return CatalystLayouts.tree(CatalystLayouts.DEFAULT);
            }

            @Override
            public Set<String> getHiddenPanels()
            {
                return layout.getHiddenPanels(LAYOUT);
            }

            @Override
            public void setHiddenPanels(Set<String> hidden)
            {
                layout.setHiddenPanels(LAYOUT, hidden);
            }
        };
    }

    /* ── Panels ── */

    public boolean isPanelVisible(String panelId)
    {
        Drawer drawer = this.drawers.get(panelId);

        return !this.dock.isPanelHidden(panelId) || (drawer != null && drawer.active);
    }

    /** Shows the panel if it is hidden, hides it if it is on screen or in a drawer */
    public void togglePanel(String panelId)
    {
        if (this.isPanelVisible(panelId))
        {
            this.closePanel(panelId);
        }
        else
        {
            this.dock.showPanel(panelId);
        }
    }

    /** The close button of a panel's title bar */
    private void closePanel(String panelId)
    {
        Drawer drawer = this.drawers.get(panelId);

        if (drawer != null && drawer.active)
        {
            this.closeDrawerForGood(drawer);
        }
        else
        {
            this.dock.hidePanel(panelId);
        }
    }

    /* ── Layout actions ── */

    public boolean isLocked()
    {
        return this.dock.isLocked();
    }

    public void toggleLock()
    {
        this.dock.toggleLock();
        BBSSettings.editorLayoutSettings.setDockUnlocked(LAYOUT, !this.dock.isLocked());
    }

    public void resetLayout()
    {
        this.restoreDrawers();
        this.dock.resetLayout();
    }

    public MapType getPresetData()
    {
        return UICatalystLayoutPresetsOverlayPanel.dataOf(this.dock.getLayoutRoot());
    }

    public void applyPreset(MapType data)
    {
        BaseType layout = data == null ? null : data.get(UICatalystLayoutPresetsOverlayPanel.KEY);

        if (layout == null)
        {
            return;
        }

        /* A template shows every panel: the ones that were closed or in a drawer come back */
        this.restoreDrawers();
        this.dock.showPanels(Arrays.asList(CatalystLayouts.MEDIA, CatalystLayouts.INSPECTOR));
        this.dock.applyLayoutRoot(EditorLayoutNode.fromData(layout));
    }

    public void openPresets()
    {
        UIOverlay.addOverlay(this.host.getContext(), new UICatalystLayoutPresetsOverlayPanel(this::getPresetData, this::applyPreset), 560, 0.8F);
    }

    public void savePreset()
    {
        UICatalystLayoutPresetsOverlayPanel.promptSave(this.host.getContext(), this::getPresetData, null);
    }

    /** The Window and Layout menu of the menu bar while the edit page is up */
    public void fillWindowMenu(ContextMenuManager menu)
    {
        menu.action(this.isPanelVisible(CatalystLayouts.MEDIA) ? Icons.CHECKMARK : Icons.NONE, UIKeys.CATALYST_NLE_PANEL_MEDIA, () -> this.togglePanel(CatalystLayouts.MEDIA));
        menu.action(this.isPanelVisible(CatalystLayouts.INSPECTOR) ? Icons.CHECKMARK : Icons.NONE, UIKeys.CATALYST_NLE_PANEL_INSPECTOR, () -> this.togglePanel(CatalystLayouts.INSPECTOR));
        menu.action(Icons.LAYOUT, UIKeys.FILM_LAYOUT_PRESETS, this::openPresets);
        menu.action(Icons.SAVED, L10n.lang("bbs.ui.film.layout_save_preset"), this::savePreset);
        menu.action(Icons.REFRESH, UIKeys.FILM_MENU_RESET_LAYOUT, this::resetLayout);
        menu.action(this.dock.isLocked() ? Icons.UNLOCKED : Icons.LOCKED, this.dock.isLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, this::toggleLock);
    }

    /* ── Drawers ── */

    private void addDrawer(String panelId, UIElement panel, Icon icon, IKey title, boolean left)
    {
        Drawer drawer = new Drawer(panelId, panel, icon, title, left);

        drawer.tab = new UIDrawerTabButton(icon, title, left, (b) -> this.toggleDrawer(drawer));
        drawer.tab.relative(this.host).x(left ? 0F : 1F).y(0.35F).anchorY(0.5F).w(20);

        if (!left)
        {
            drawer.tab.anchorX(1F);
        }

        drawer.tab.setVisible(false);

        drawer.overlay = new DrawerContainer(this, drawer);
        drawer.overlay.relative(this.host).x(left ? 0F : 1F).y(0).w(260).h(1F);

        if (!left)
        {
            drawer.overlay.anchorX(1F);
        }

        drawer.overlay.setVisible(false);

        this.host.add(drawer.tab, drawer.overlay);
        this.drawers.put(panelId, drawer);
    }

    /** "Shrink to drawer" in a panel's menu: the panel leaves the layout for a tab on its edge */
    private void dockAsDrawer(String panelId)
    {
        Drawer drawer = this.drawers.get(panelId);

        if (drawer == null || drawer.active)
        {
            return;
        }

        drawer.active = true;
        this.dock.hidePanel(panelId);
        drawer.tab.setVisible(true);
        this.closeOverlay(drawer);
    }

    private void toggleDrawer(Drawer drawer)
    {
        if (drawer.open)
        {
            this.closeOverlay(drawer);
        }
        else
        {
            this.openOverlay(drawer);
        }
    }

    private void openOverlay(Drawer drawer)
    {
        /* One drawer over the page at a time */
        for (Drawer other : this.drawers.values())
        {
            if (other != drawer && other.open)
            {
                this.closeOverlay(other);
            }
        }

        drawer.open = true;

        UIElement content = drawer.overlay.content;

        if (content.getChildren().isEmpty())
        {
            UIDockSlot slot = this.dock.getSlot(drawer.panelId);

            if (slot != null)
            {
                slot.remove(drawer.panel);
            }

            drawer.panel.relative(content).x(0).y(0).w(1F).h(1F);
            drawer.panel.setVisible(true);
            content.add(drawer.panel);
        }

        drawer.overlay.setVisible(true);
        drawer.overlay.resize();
    }

    private void closeOverlay(Drawer drawer)
    {
        drawer.open = false;
        drawer.overlay.setVisible(false);
    }

    /** The panel goes back into its dock slot, where it sits under the title bar */
    private void returnToSlot(Drawer drawer)
    {
        if (drawer.panel.getParent() == drawer.overlay.content)
        {
            drawer.overlay.content.remove(drawer.panel);
        }

        UIDockSlot slot = this.dock.getSlot(drawer.panelId);

        if (slot != null)
        {
            int inset = slot.dragHandle.isVisible() ? UIDockLayout.TAB_HEADER_HEIGHT_PX : 0;

            /* A drawer that was never opened never took the panel out of its slot */
            if (drawer.panel.getParent() != slot)
            {
                slot.add(drawer.panel);
            }

            drawer.panel.relative(slot).x(0F).y(0F, inset).w(1F).h(1F, -inset);
        }
    }

    private void endDrawer(Drawer drawer)
    {
        drawer.active = false;
        drawer.open = false;
        drawer.tab.setVisible(false);
        drawer.overlay.setVisible(false);
        this.returnToSlot(drawer);
    }

    /** Back into the layout as a panel again */
    public void restoreDrawer(Drawer drawer)
    {
        this.endDrawer(drawer);
        this.dock.showPanel(drawer.panelId);
    }

    /** The drawer and its tab go away and the panel stays closed */
    public void closeDrawerForGood(Drawer drawer)
    {
        this.endDrawer(drawer);
        this.dock.hidePanel(drawer.panelId);
    }

    private void restoreDrawers()
    {
        for (Drawer drawer : new ArrayList<>(this.drawers.values()))
        {
            if (drawer.active)
            {
                this.restoreDrawer(drawer);
            }
        }
    }

    /** Whether a drawer is slid over the page, which the page checks before it handles keys and drops */
    public boolean isDrawerOpen()
    {
        for (Drawer drawer : this.drawers.values())
        {
            if (drawer.open)
            {
                return true;
            }
        }

        return false;
    }

    public static class Drawer
    {
        public final String panelId;
        public final UIElement panel;
        public final Icon icon;
        public final IKey title;
        public final boolean left;

        public boolean active;
        public boolean open;
        public UIDrawerTabButton tab;
        public DrawerContainer overlay;

        public Drawer(String panelId, UIElement panel, Icon icon, IKey title, boolean left)
        {
            this.panelId = panelId;
            this.panel = panel;
            this.icon = icon;
            this.title = title;
            this.left = left;
        }
    }

    /** A drawer's face over the page: a title bar with its buttons, and the panel under it */
    public static class DrawerContainer extends UIElement
    {
        public final UIElement content;
        public final UIElement header;

        public DrawerContainer(CatalystDock dock, Drawer drawer)
        {
            this.header = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xDD1E1E1E);
                    context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0x22FFFFFF);
                    context.batcher.icon(drawer.icon, Colors.WHITE, this.area.x + 4, this.area.y + 2, 16, 16);

                    FontRenderer font = context.batcher.getFont();

                    context.batcher.text(drawer.title.get(), this.area.x + 22, this.area.my(font.getHeight()), 0xFFD0D0D0, false);

                    super.render(context);
                }

                @Override
                protected boolean subMouseClicked(UIContext context)
                {
                    if (context.mouseButton == 1 && this.area.isInside(context))
                    {
                        context.replaceContextMenu((menu) ->
                        {
                            menu.action(Icons.MAXIMIZE, UIKeys.DOCK_RESTORE_TO_LAYOUT, () -> dock.restoreDrawer(drawer));
                            menu.action(Icons.CLOSE, UIKeys.DOCK_CLOSE_PANEL, () -> dock.closeDrawerForGood(drawer));
                        }, context.mouseX, context.mouseY);

                        return true;
                    }

                    return super.subMouseClicked(context);
                }
            };
            this.header.relative(this).x(0).y(0).w(1F).h(20);

            UIIcon restore = new UIIcon(Icons.MAXIMIZE, (b) -> dock.restoreDrawer(drawer));
            UIIcon close = new UIIcon(Icons.CLOSE, (b) -> dock.closeOverlay(drawer))
            {
                @Override
                protected void renderSkin(UIContext context)
                {
                    if (this.hover)
                    {
                        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x66E81123);
                    }

                    super.renderSkin(context);
                }
            };

            restore.tooltip(UIKeys.DOCK_RESTORE_TO_LAYOUT);
            close.tooltip(UIKeys.DOCK_CLOSE_PANEL);
            restore.relative(this.header).x(1F, -36).y(2).w(16).h(16);
            close.relative(this.header).x(1F, -18).y(2).w(16).h(16);
            this.header.add(restore, close);

            this.content = new UIElement();
            this.content.relative(this).x(0).y(20).w(1F).h(1F, -20);

            this.add(this.header, this.content);
        }

        @Override
        public void resize()
        {
            super.resize();

            if (this.getParent() != null && this.getParent().area.w > 0)
            {
                int width = Math.min(260, Math.max(160, (int) (this.getParent().area.w * 0.45F)));

                this.area.w = width;

                if (this.area.x + width > this.getParent().area.ex())
                {
                    this.area.x = this.getParent().area.ex() - width;
                }
            }
        }

        @Override
        public void render(UIContext context)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xF0141922);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x33FFFFFF);

            super.render(context);
        }

        /* Whatever the drawer covers does not see the mouse */
        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            return this.area.isInside(context);
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            return this.area.isInside(context);
        }
    }
}
