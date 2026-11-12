package mchorse.bbs_mod.settings.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs.compat.mtr.MtrCompatHelper;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.dev.DevAccessGuard;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.value.ValueKeyCombo;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings window. A bar of module tabs in the title - the core settings, the keybinds and the
 * addons - picks what the sidebar lists: the categories of the chosen module (or, for addons, of
 * every addon, each under its own name). The page on the right is the chosen category, headed by
 * its title and what it is for.
 */
public class UISettingsOverlayPanel extends UIOverlayPanel
{
    private static final int SIDE_WIDTH = 200;

    /** The size it opens at, as long as the screen is big enough and the user has not sized it */
    private static final int PREFERRED_WIDTH = 780;
    private static final int PREFERRED_HEIGHT = 520;

    /** Below this the sidebar, the module tabs and a row of options no longer fit side by side */
    private static final int MIN_WIDTH = 580;
    private static final int MIN_HEIGHT = 400;

    /** What the window keeps clear around itself when it opens at a share of a small screen */
    private static final int SCREEN_MARGIN = 20;
    private static final int BRAND_HEIGHT = 22;

    /** The corner kept clear of the page's scroll bar, for the resize grip */
    private static final int GRIP_CLEARANCE = 10;

    /** How long a page takes to fade and slide in when the tab changes, in nanoseconds */
    private static final long SWITCH_TIME = 120_000_000L;
    private static final float SWITCH_SLIDE = 10F;

    public static final int GROUP_CORE = 0;
    public static final int GROUP_KEYBINDS = 1;
    public static final int GROUP_LIGHTING = 2;
    public static final int GROUP_ADDONS = 3;
    public static final int GROUP_DEVELOPER = 4;

    public UITextbox search;
    public UIScrollView sections;
    public UIScrollView options;
    public UIModuleTabs tabs;
    public UIIcon sortToggle;

    /** Compatibility fields for addons and mixins (IRLights etc.) expecting BBS 2.7.0 structure */
    public Settings settings;
    public ValueGroup category;
    public UIIcon currentModule;
    public final Map<String, UIIcon> moduleButtons = new HashMap<>();

    /** The entries of every group, by group */
    private final List<List<Entry>> groups = new ArrayList<>();
    private int group = GROUP_CORE;
    private Entry current;
    private String filter = "";

    /** A category in the sidebar. */
    public static class Entry
    {
        public final int group;
        public final Settings settings;
        public final ValueGroup category;
        public final IKey title;
        public final IKey description;
        public final Icon icon;

        public Entry(int group, Settings settings, ValueGroup category, IKey title, IKey description, Icon icon)
        {
            this.group = group;
            this.settings = settings;
            this.category = category;
            this.title = title;
            this.description = description;
            this.icon = icon;
        }
    }

    public UISettingsOverlayPanel()
    {
        super(UIKeys.CONFIG_TITLE);

        /* The strip down the right edge is gone: the close button sits alone in the corner, the
         * module tabs share the title bar with the title, and the page takes the whole width */
        this.icons.relative(this).x(1F, -20).y(0).w(20).h(20);
        this.content.relative(this).xy(0, 20).w(1F).h(1F, -20);
        this.title.w(SIDE_WIDTH - 12);

        this.tabs = new UIModuleTabs(this);
        this.tabs.relative(this).x(SIDE_WIDTH).y(2).w(1F, -(SIDE_WIDTH + 26)).h(16);

        this.search = new UITextbox(100, (str) ->
        {
            this.filter = str.toLowerCase().trim();
            this.refresh();
        });
        this.search.placeholder(UIKeys.SETTINGS_SEARCH);
        this.search.relative(this.content).x(8).y(8).w(SIDE_WIDTH - 38).h(20);

        /* The order of the sidebar: the categories' own, or alphabetical */
        this.sortToggle = new UIIcon(Icons.FILTER, (b) ->
        {
            BBSSettings.settingsSidebarAlphabetical.set(!BBSSettings.settingsSidebarAlphabetical.get());
            this.buildSidebar(this.groups.get(this.group));
        });
        this.sortToggle.relative(this.content).x(SIDE_WIDTH - 28).y(8).wh(20, 20);
        this.sortToggle.tooltip(UIKeys.SETTINGS_SORT_ALPHABETICAL);

        this.sections = new UIScrollView(ScrollDirection.VERTICAL);
        this.sections.relative(this.content).x(4).y(34 + BRAND_HEIGHT).w(SIDE_WIDTH - 8).h(1F, -(38 + BRAND_HEIGHT));
        this.sections.column(2).vertical().stretch().scroll().padding(2).height(22);

        this.options = new UISettingsOptions();
        this.options.scroll.scrollSpeed = 51;
        this.options.relative(this.content).x(SIDE_WIDTH).y(0).w(1F, -SIDE_WIDTH).h(1F, -GRIP_CLEARANCE);
        this.options.column().scroll().vertical().stretch().padding(14).height(20);
        this.options.preRender(this::renderRowHover);

        mchorse.bbs_mod.dev.DevAccessGuard.logDiagnostics();

        this.buildEntries();

        this.content.add(this.search, this.sortToggle, this.sections, this.options);
        this.add(this.tabs);

        this.selectGroup(GROUP_CORE, false);
        this.markContainer();
    }

    @Override
    public int getMinWidth()
    {
        return MIN_WIDTH;
    }

    @Override
    public int getMinHeight()
    {
        return MIN_HEIGHT;
    }

    /**
     * Until the grip has been used the window takes its preferred size, or what the screen has
     * room for when that is less. The sidebar keeps its width either way and the page takes the
     * rest, its scroll view following the new size.
     */
    @Override
    public void resize()
    {
        UIElement parent = this.getParent();

        if (!this.wasResized() && parent != null && parent.area.w > 0 && parent.area.h > 0)
        {
            int width = Math.min(PREFERRED_WIDTH, parent.area.w - SCREEN_MARGIN * 2);
            int height = Math.min(PREFERRED_HEIGHT, parent.area.h - SCREEN_MARGIN * 2);

            this.flex.w.max = this.flex.h.max = 0;
            this.flex.w.set(0F, Math.max(width, Math.min(MIN_WIDTH, parent.area.w)));
            this.flex.h.set(0F, Math.max(height, Math.min(MIN_HEIGHT, parent.area.h)));
        }

        super.resize();
    }

    private void buildEntries()
    {
        for (int i = 0; i < 5; i++)
        {
            this.groups.add(new ArrayList<>());
        }

        Settings main = BBSMod.getSettings().modules.get("bbs");
        Settings keybinds = BBSMod.getSettings().modules.get("keybinds");
        Settings irlights = BBSMod.getSettings().modules.get("irlights");

        this.addCategories(GROUP_CORE, main);
        this.addCategories(GROUP_KEYBINDS, keybinds);
        this.addCategories(GROUP_LIGHTING, irlights);

        for (Settings settings : BBSMod.getSettings().modules.values())
        {
            if ("mtr".equals(settings.getId()) && !MtrCompatHelper.isMtrLoaded())
            {
                continue;
            }

            if (settings != main && settings != keybinds && settings != irlights)
            {
                this.addCategories(GROUP_ADDONS, settings);
            }
        }
    }

    private void addCategories(int group, Settings settings)
    {
        if (settings == null)
        {
            return;
        }

        for (ValueGroup category : settings.categories.values())
        {
            if (category.isVisible())
            {
                Icon icon = category.icon != null ? category.icon : settings.icon;

                this.groups.get(group).add(new Entry(group, settings, category, L10n.lang(UIValueFactory.getCategoryTitleKey(category)), L10n.lang(UIValueFactory.getCategoryTooltipKey(category)), icon));
            }
        }
    }

    public int getGroup()
    {
        return this.group;
    }

    /** Switches the module tab: the sidebar lists the group's categories and the first is shown. */
    public void selectGroup(int group, boolean animate)
    {
        this.group = group;

        List<Entry> entries = this.groups.get(group);

        this.buildSidebar(entries);
        this.select(entries.isEmpty() ? null : entries.get(0), animate);
    }

    private void buildSidebar(List<Entry> entries)
    {
        this.sections.removeAll();

        boolean alphabetical = BBSSettings.settingsSidebarAlphabetical.get();

        this.sortToggle.tooltip(alphabetical ? UIKeys.SETTINGS_SORT_DEFAULT : UIKeys.SETTINGS_SORT_ALPHABETICAL);

        if (alphabetical)
        {
            /* By title, but an addon's categories stay together under its name */
            List<Entry> original = entries;
            List<Entry> sorted = new ArrayList<>(entries);

            sorted.sort((a, b) ->
            {
                if (a.settings != b.settings)
                {
                    return Integer.compare(original.indexOf(a), original.indexOf(b));
                }

                return a.title.get().compareToIgnoreCase(b.title.get());
            });
            entries = sorted;
        }

        Settings module = null;

        for (Entry entry : entries)
        {
            /* Addons are many modules in one tab: each one's categories go under its own name */
            if (entry.group == GROUP_ADDONS && entry.settings != module)
            {
                module = entry.settings;
                this.sections.add(new UISidebarHeading(L10n.lang(UIValueFactory.getTitleKey(module))));
            }

            this.sections.add(new UISectionButton(this, entry));
        }

        if (entries.isEmpty() && this.group != GROUP_DEVELOPER)
        {
            this.sections.add(new UISidebarHeading(UIKeys.SETTINGS_ADDONS_EMPTY));
        }

        this.sections.resize();
    }

    public boolean isCurrent(Entry entry)
    {
        return this.filter.isEmpty() && this.current == entry;
    }

    public boolean isCurrent(ValueGroup category)
    {
        return this.filter.isEmpty() && (this.category == category || (this.current != null && this.current.category == category));
    }

    public void select(Entry entry, boolean animate)
    {
        this.current = entry;
        this.settings = entry == null ? null : entry.settings;
        this.category = entry == null ? null : entry.category;

        if (!this.filter.isEmpty())
        {
            this.filter = "";
            this.search.setText("");
        }

        if (animate && this.options instanceof UISettingsOptions settingsOptions)
        {
            settingsOptions.restart();
        }

        this.refresh();
    }

    public void selectCategory(ValueGroup category)
    {
        this.category = category;

        for (List<Entry> entries : this.groups)
        {
            for (Entry entry : entries)
            {
                if (entry.category == category)
                {
                    this.select(entry, true);
                    return;
                }
            }
        }

        this.refresh();
    }

    public void selectConfig(String mod, UIIcon button)
    {
        this.currentModule = button;

        for (List<Entry> entries : this.groups)
        {
            for (Entry entry : entries)
            {
                if (entry.settings.getId().equals(mod))
                {
                    this.select(entry, true);
                    return;
                }
            }
        }
    }

    public void showCategory(String mod, String categoryId)
    {
        for (List<Entry> entries : this.groups)
        {
            for (Entry entry : entries)
            {
                if (entry.settings.getId().equals(mod) && entry.category.getId().equals(categoryId))
                {
                    if (entry.group != this.group)
                    {
                        this.group = entry.group;
                        this.buildSidebar(entries);
                    }

                    this.select(entry, false);

                    return;
                }
            }
        }
    }

    public void refresh()
    {
        this.options.removeAll();

        if (this.filter.isEmpty())
        {
            if (this.current != null)
            {
                this.options.add(new UIPageHeader(this, this.current.title, this.current.description, this.current.category));

                if (this.current.category.getId().equals("video"))
                {
                    this.options.add(new UIDeliverBanner(this));
                }
                this.appendValues(this.current.category, false);

                if (this.group == GROUP_CORE && this.current.category.getId().equals("general"))
                {
                    this.options.add(new UICreditsBanner());
                }
            }
            else if (this.group == GROUP_ADDONS)
            {
                this.options.add(new UIPageHeader(this, UIKeys.SETTINGS_ADDONS, UIKeys.SETTINGS_ADDONS_EMPTY_DESCRIPTION, null));
            }
            else if (this.group == GROUP_DEVELOPER && DevAccessGuard.hasAccess())
            {
                this.options.add(new UIPageHeader(this, UIKeys.SETTINGS_TAB_DEVELOPER, UIKeys.SETTINGS_TAB_DEVELOPER_DESCRIPTION, null));

                for (UIElement element : UIDevSettingsPage.create())
                {
                    this.options.add(element);
                }
            }
        }
        else
        {
            boolean first = true;

            for (Settings settings : BBSMod.getSettings().modules.values())
            {
                if ("mtr".equals(settings.getId()) && !MtrCompatHelper.isMtrLoaded())
                {
                    continue;
                }

                for (ValueGroup category : settings.categories.values())
                {
                    if (!category.isVisible() || !this.hasMatch(category))
                    {
                        continue;
                    }

                    UISectionHeader header = new UISectionHeader(this, category);

                    if (!first)
                    {
                        header.marginTop(16);
                    }

                    this.options.add(header);
                    this.appendValues(category, true);

                    first = false;
                }
            }
        }

        this.options.resize();
    }

    /** A row lights up faintly under the cursor; headers and the page title do not. */
    private void renderRowHover(UIContext context)
    {
        for (IUIElement child : this.options.getChildren())
        {
            if (child instanceof UIElement element && !(child instanceof UISectionHeader) && !(child instanceof UIPageHeader)
                && element.isVisible() && element.area.isInside(context))
            {
                context.batcher.box(element.area.x - 6, element.area.y - 1, element.area.ex() + 6, element.area.ey() + 1, Colors.setA(Colors.WHITE, 0.045F));

                return;
            }
        }
    }

    /**
     * Presets only promise a resolution and 60 FPS (see their labels) - the encoder
     * arguments are the user's own and must survive.
     */
    public void applyVideoPreset(int width, int height)
    {
        BBSSettings.videoWidth.set(width);
        BBSSettings.videoHeight.set(height);
        BBSSettings.videoFrameRate.set(60);

        this.refresh();
    }

    public void addVideoPresets(ContextMenuManager menu)
    {
        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_720p, () -> this.applyVideoPreset(1280, 720));
        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1080P, () -> this.applyVideoPreset(1920, 1080));
        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_SHORTS_1080P, () -> this.applyVideoPreset(1080, 1920));
        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1440P, () -> this.applyVideoPreset(2560, 1440));
        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_4K, () -> this.applyVideoPreset(3840, 2160));
    }

    private void appendValues(ValueGroup category, boolean filtered)
    {
        Set<BaseValue> drawn = new HashSet<>();

        for (BaseValue value : category.getAll())
        {
            if (drawn.contains(value))
            {
                continue;
            }

            UISettingsLayout.IValueRow row = UISettingsLayout.getRow(value);
            List<BaseValue> values = row == null ? Collections.singletonList(value) : row.getValues();

            drawn.addAll(values);

            if (!this.isVisible(values) || (filtered && !this.matches(values)))
            {
                continue;
            }

            for (UIElement element : row == null ? UIValueMap.create(value, this) : row.create(this))
            {
                this.options.add(element);
            }
        }
    }

    private boolean isVisible(List<BaseValue> values)
    {
        for (BaseValue value : values)
        {
            if (this.isValueVisible(value))
            {
                return true;
            }
        }

        return false;
    }

    private boolean matches(List<BaseValue> values)
    {
        for (BaseValue value : values)
        {
            if (this.matches(value))
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasMatch(ValueGroup category)
    {
        for (BaseValue value : category.getAll())
        {
            if (this.isValueVisible(value) && this.matches(value))
            {
                return true;
            }
        }

        return false;
    }

    private boolean matches(BaseValue value)
    {
        IKey key = L10n.lang(UIValueFactory.getValueLabelKey(value));

        if (value instanceof ValueKeyCombo combo)
        {
            key = combo.get().label;
        }

        String label = key.get().toLowerCase();

        return label.contains(this.filter) || value.getId().toLowerCase().contains(this.filter);
    }

    private boolean isValueVisible(BaseValue value)
    {
        if (!value.isVisible())
        {
            return false;
        }

        if (value == BBSSettings.editorPreviewCustomWidth || value == BBSSettings.editorPreviewCustomHeight)
        {
            return BBSSettings.editorPreviewSizeMode.get() == 1;
        }
        else if (value == BBSSettings.editorPreviewResolutionScale)
        {
            return BBSSettings.editorPreviewSizeMode.get() == 2;
        }

        return true;
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int x = this.content.area.x;
        int y = this.content.area.y;
        int ey = this.content.area.ey();

        /* The title bar runs the width of the window, not just over the close button */
        context.batcher.box(this.area.x, this.area.y, this.area.ex() - 20, y, BBSSettings.chromeSurface());
        context.batcher.box(x, y, x + SIDE_WIDTH, ey, BBSSettings.chromeSurface());
        context.batcher.box(x + SIDE_WIDTH - 1, y, x + SIDE_WIDTH, ey, BBSSettings.dividerColor());

        /* The mod's name under the search box */
        FontRenderer font = context.batcher.getFont();
        String brand = UIKeys.SETTINGS_BRAND.get();

        context.batcher.icon(Icons.SETTINGS, Colors.WHITE, x + 12, y + 34 + BRAND_HEIGHT / 2 - 1, 0F, 0.5F);
        context.batcher.text(font.limitToWidth(brand, SIDE_WIDTH - 40), x + 32, y + 34 + (BRAND_HEIGHT - font.getHeight()) / 2 - 1, Colors.WHITE, true);
    }

    /** A filled rectangle with its corners cut round, for the pill behind the chosen tab. */
    private static void pill(Batcher2D batcher, int x1, int y1, int x2, int y2, int radius, int color)
    {
        int r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2);

        for (int row = 0; row < y2 - y1; row++)
        {
            int edge = Math.min(row, y2 - y1 - 1 - row);
            int inset = 0;

            if (edge < r)
            {
                double dy = r - edge - 0.5D;

                inset = r - (int) Math.round(Math.sqrt(Math.max(r * r - dy * dy, 0D)));
            }

            batcher.box(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
        }
    }

    /**
     * The page's scroll view. When the tab changes it restarts a short clock, and until the clock
     * runs out the page is drawn a little low and a little faint, rising to rest.
     */
    public static class UISettingsOptions extends UIScrollView
    {
        private long switchStart = Long.MIN_VALUE / 2;

        public UISettingsOptions()
        {
            super(ScrollDirection.VERTICAL);
        }

        public void restart()
        {
            this.switchStart = System.nanoTime();
        }

        @Override
        public void render(UIContext context)
        {
            double t = (System.nanoTime() - this.switchStart) / (double) SWITCH_TIME;

            if (t >= 1D)
            {
                super.render(context);

                return;
            }

            float eased = (float) (1D - Math.pow(1D - Math.max(t, 0D), 3D));
            MatrixStack stack = context.batcher.getContext().getMatrices();

            context.batcher.flush();
            stack.push();
            stack.translate(0F, (1F - eased) * SWITCH_SLIDE, 0F);
            RenderSystem.setShaderColor(1F, 1F, 1F, eased);

            try
            {
                super.render(context);
                context.batcher.flush();
            }
            finally
            {
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                stack.pop();
            }
        }
    }

    /**
     * The module tabs in the title bar: the core settings, the keybinds and the addons, side by
     * side as pills - the chosen one filled, the one under the cursor faintly lit.
     */
    public static class UIModuleTabs extends UIElement
    {
        private static final int PADDING = 10;
        private static final int GAP = 4;

        private static final IKey[] LABELS = {UIKeys.SETTINGS_TAB_CORE, UIKeys.SETTINGS_TAB_KEYBINDS, UIKeys.SETTINGS_TAB_LIGHTING, UIKeys.SETTINGS_ADDONS, UIKeys.SETTINGS_TAB_DEVELOPER};
        private static final Icon[] ICONS = {Icons.SETTINGS, Icons.KEY_CAP, Icons.LIGHT, Icons.MORE, Icons.CODE};

        /** The developer tab is the last one and exists only behind the developer gate */
        private static int count()
        {
            return DevAccessGuard.hasAccess() ? LABELS.length : LABELS.length - 1;
        }

        private final UISettingsOverlayPanel panel;

        public UIModuleTabs(UISettingsOverlayPanel panel)
        {
            this.panel = panel;
        }

        /** Where tab {@code i} sits: x and width, or null when the tabs ran out of room */
        private int[] tab(FontRenderer font, int i)
        {
            int x = this.area.x;

            for (int j = 0; j <= i; j++)
            {
                int w = 16 + font.getWidth(LABELS[j].get()) + PADDING * 2;

                if (j == i)
                {
                    return x + w > this.area.ex() ? null : new int[] {x, w};
                }

                x += w + GAP;
            }

            return null;
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                FontRenderer font = context.batcher.getFont();

                for (int i = 0; i < count(); i++)
                {
                    int[] tab = this.tab(font, i);

                    if (tab != null && context.mouseX >= tab[0] && context.mouseX < tab[0] + tab[1])
                    {
                        if (i != this.panel.getGroup())
                        {
                            this.panel.selectGroup(i, true);
                        }

                        UIUtils.playClick();

                        return true;
                    }
                }
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();

            for (int i = 0; i < count(); i++)
            {
                int[] tab = this.tab(font, i);

                if (tab == null)
                {
                    break;
                }

                boolean current = i == this.panel.getGroup();
                boolean hover = this.area.isInside(context) && context.mouseX >= tab[0] && context.mouseX < tab[0] + tab[1];

                if (current)
                {
                    pill(context.batcher, tab[0], this.area.y, tab[0] + tab[1], this.area.ey(), 7, Colors.setA(BBSSettings.primaryColor.get(), 0.4F));
                }
                else if (hover)
                {
                    pill(context.batcher, tab[0], this.area.y, tab[0] + tab[1], this.area.ey(), 7, Colors.setA(Colors.WHITE, 0.08F));
                }

                boolean lit = current || hover;

                context.batcher.icon(ICONS[i], RowStyle.iconColor(lit), tab[0] + PADDING - 2, this.area.my(), 0F, 0.5F);
                context.batcher.text(LABELS[i].get(), tab[0] + PADDING + 16, this.area.my(font.getHeight()), RowStyle.textColor(lit), true);
            }

            super.render(context);
        }
    }

    /**
     * A tab of the sidebar: icon and the whole of its title, and when it is the chosen one, a
     * rounded pill behind it.
     */
    public static class UISectionButton extends UIClickable<UISectionButton>
    {
        private final UISettingsOverlayPanel panel;
        private final Entry entry;

        public UISectionButton(UISettingsOverlayPanel panel, Entry entry)
        {
            super(null);

            this.panel = panel;
            this.entry = entry;
            this.callback = (b) -> panel.select(entry, true);

            this.tooltip(entry.description, Direction.RIGHT);
        }

        @Override
        protected UISectionButton get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean current = this.panel.isCurrent(this.entry);
            int x1 = this.area.x + 2;
            int x2 = this.area.ex() - 2;

            if (current)
            {
                pill(context.batcher, x1, this.area.y, x2, this.area.ey(), 5, Colors.setA(BBSSettings.primaryColor.get(), 0.32F));
            }
            else if (this.hover)
            {
                pill(context.batcher, x1, this.area.y, x2, this.area.ey(), 5, Colors.setA(Colors.WHITE, 0.07F));
            }

            boolean lit = this.hover || current;

            context.batcher.icon(this.entry.icon, RowStyle.iconColor(lit), this.area.x + 10, this.area.my(), 0F, 0.5F);

            FontRenderer font = context.batcher.getFont();

            /* The title is shown whole: the sidebar is wide enough, and a title that still does not
             * fit is cut at the last word that does */
            context.batcher.text(font.limitToWidth(this.entry.title.get(), this.area.w - 38), this.area.x + 30, this.area.my(font.getHeight()), RowStyle.textColor(lit), true);
        }
    }

    /** The small heading and thin line that set the addons apart from the main settings. */
    public static class UISidebarHeading extends UIElement
    {
        private final IKey label;

        public UISidebarHeading(IKey label)
        {
            this.label = label;

            this.h(26);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();

            context.batcher.box(this.area.x + 6, this.area.y + 5, this.area.ex() - 6, this.area.y + 6, BBSSettings.dividerColor());
            context.batcher.text(this.label.get(), this.area.x + 8, this.area.y + 10, Colors.GRAY, false);

            super.render(context);
        }
    }

    /**
     * The top of a page: the title large, and under it a line on what the page is for.
     */
    public static class UIPageHeader extends UIElement
    {
        private static final float TITLE_SCALE = 1.5F;

        private final IKey title;
        private final IKey description;

        public UIPageHeader(UISettingsOverlayPanel panel, IKey title, IKey description, ValueGroup category)
        {
            this.title = title;
            this.description = description;

            this.h(48);
            this.marginBottom(6);

        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            MatrixStack stack = context.batcher.getContext().getMatrices();

            context.batcher.flush();
            stack.push();
            stack.translate(this.area.x, this.area.y + 2, 0F);
            stack.scale(TITLE_SCALE, TITLE_SCALE, 1F);
            context.batcher.text(this.title.get(), 0, 0, Colors.WHITE, true);
            context.batcher.flush();
            stack.pop();

            /* One line, cut with an ellipsis only when the page is too narrow for the sentence */
            String line = font.limitToWidth(this.description.get(), this.area.w);

            context.batcher.text(line, this.area.x, this.area.y + 26, Colors.GRAY, false);
            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

            super.render(context);
        }
    }

    /**
     * Card displaying creator, contributor and third-party credits in the General settings page.
     */
    public static class UICreditsBanner extends UIElement
    {
        private static final int PAD = 10;

        public UICreditsBanner()
        {
            this.h(92);
            this.marginTop(14);
            this.marginBottom(8);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();

            pill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 6, Colors.setA(BBSSettings.primaryColor.get(), 0.12F));
            context.batcher.box(this.area.x, this.area.y + 6, this.area.x + 2, this.area.ey() - 6, Colors.A100 | BBSSettings.primaryColor.get());

            int y = this.area.y + PAD;

            context.batcher.icon(Icons.USER, Colors.WHITE, this.area.x + PAD, y + 4, 0F, 0.5F);
            context.batcher.text(UIKeys.SETTINGS_CREDITS_TITLE.get(), this.area.x + PAD + 18, y, Colors.WHITE, true);

            y += font.getHeight() + 6;

            context.batcher.text("• " + UIKeys.SETTINGS_CREDITS_KYNIX.get(), this.area.x + PAD + 8, y, 0xFFE0E0E0, true);
            y += font.getHeight() + 3;

            context.batcher.text("• " + UIKeys.SETTINGS_CREDITS_MCHORSE.get(), this.area.x + PAD + 8, y, Colors.GRAY, false);
            y += font.getHeight() + 2;

            context.batcher.text("• " + UIKeys.SETTINGS_CREDITS_WEMPPY.get() + "  |  " + UIKeys.SETTINGS_CREDITS_QUALETT.get(), this.area.x + PAD + 8, y, Colors.GRAY, false);
            y += font.getHeight() + 2;

            context.batcher.text("• " + UIKeys.SETTINGS_CREDITS_GLAXIUM.get(), this.area.x + PAD + 8, y, Colors.GRAY, false);

            super.render(context);
        }
    }

    /**
     * The card at the top of the render engine page: what a render looks like is set where a film
     * is delivered, and the button goes there - the Deliver panel of the film editor, when a film
     * is open in it.
     */
    public static class UIDeliverBanner extends UIElement
    {
        private static final int PAD = 10;
        private static final int BUTTON_H = 20;

        private final IKey text = UIKeys.SETTINGS_DELIVER_BANNER;
        public final UIButton open;
        /** What the button was last set up for; null before the first frame */
        private Boolean available;

        public UIDeliverBanner(UISettingsOverlayPanel panel)
        {
            this.open = new UIButton(UIKeys.SETTINGS_DELIVER_OPEN, (b) ->
            {
                UIFilmPanel film = filmPanel();

                if (film != null)
                {
                    panel.close();
                    film.preview.recordVideo.clickItself();
                }
            });
            this.open.relative(this).x(PAD).y(1F, -(PAD + BUTTON_H)).w(220).h(BUTTON_H);
            this.open.tooltip(UIKeys.SETTINGS_DELIVER_UNAVAILABLE);
            this.add(this.open);

            this.h(76);
            this.marginBottom(8);
        }

        /** The film editor, when it is the panel on screen with a film open in it */
        private static UIFilmPanel filmPanel()
        {
            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel film && film.getData() != null)
            {
                return film;
            }

            return null;
        }

        @Override
        public void render(UIContext context)
        {
            boolean available = filmPanel() != null;

            /* Only when it changes: a tooltip is an object, and this runs every frame */
            if (this.available == null || available != this.available)
            {
                this.available = available;
                this.open.setEnabled(available);

                if (available)
                {
                    this.open.removeTooltip();
                }
                else
                {
                    this.open.tooltip(UIKeys.SETTINGS_DELIVER_UNAVAILABLE);
                }
            }

            FontRenderer font = context.batcher.getFont();

            pill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 6, Colors.setA(BBSSettings.primaryColor.get(), 0.14F));
            context.batcher.box(this.area.x, this.area.y + 6, this.area.x + 2, this.area.ey() - 6, Colors.A100 | BBSSettings.primaryColor.get());

            List<String> lines = font.wrap(this.text.get(), this.area.w - PAD * 2);
            int y = this.area.y + PAD;

            for (int i = 0; i < lines.size() && i < 3; i++)
            {
                context.batcher.text(lines.get(i), this.area.x + PAD, y, Colors.WHITE, true);
                y += font.getHeight() + 3;
            }

            super.render(context);
        }
    }

    /**
     * The heading over a group of options on a page that holds several (a module's categories, or
     * the search results) - icon, title and a divider underline.
     */
    public static class UISectionHeader extends UIElement
    {
        private static final Area HEADER = new Area();

        private final ValueGroup category;
        private final IKey label;

        public UISectionHeader(UISettingsOverlayPanel panel, ValueGroup category)
        {
            this.category = category;
            this.label = L10n.lang(UIValueFactory.getCategoryTitleKey(category));

            this.h(18);

        }

        @Override
        public void render(UIContext context)
        {
            /* Icon and title sit one row above the centre of the 18px, clear of the divider */
            HEADER.set(this.area.x, this.area.y - 1, this.area.w, this.area.h);
            UISection.renderHeader(context, HEADER, this.label, this.category.icon, null, Colors.WHITE);

            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

            super.render(context);
        }
    }
}
