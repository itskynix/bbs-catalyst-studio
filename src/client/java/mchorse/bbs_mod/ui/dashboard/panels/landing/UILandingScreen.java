package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueRecentData.Entry;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an empty tab shows: a menu on the left — new, the list, the folder, the wiki —
 * and on the right what was opened last, so the way back into yesterday's work is one click.
 *
 * <p>Nothing here changes files. Renaming, removing, folders, duplicates all live in the data
 * manager the list entry leads to; this screen only opens things.</p>
 */
public class UILandingScreen extends UIElement
{
    private static final int CARD_W = 440;
    private static final int CARD_H = 360;

    /** The banner is the top half of the card, exactly. */
    private static final int BANNER_H = CARD_H / 2;
    private static final int BANNER_MARGIN = 12;
    private static final int PADDING = 10;
    private static final int HEADER_H = 16;
    private static final int HEADER_MARGIN = 6;
    private static final int MENU_W = 150;
    private static final int GUTTER = 20;
    private static final int GROUP_GAP = 10;

    private static final int RECENT_X = PADDING + MENU_W + GUTTER;
    private static final int CONTENT_Y = BANNER_H + BANNER_MARGIN;
    private static final int LIST_Y = CONTENT_Y + HEADER_H + HEADER_MARGIN;

    /** Section titles sit back a little; the entries under them are what the eye is for. */
    private static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    private static final int MUTED = Colors.setA(Colors.WHITE, 0.5F);

    private static final String BANNER_BRAND = "\u00a7lBBS Catalyst Studio";
    private static final String BANNER_VERSION = "C1.1";

    /* Where the documentation lives; the same in every language, so not in the language files */
    public static final String WIKI_LINK = "https://github.com/itskynix/bbs-catalyst-studio/wiki";

    private final ILandingHost host;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIElement card;
    private final UIElement banner;
    private final UIElement menu;
    private final UILandingRow folder;
    private final UIRecentDataList recent;

    /** Ids the repository reported last; null until it answered, when nothing is filtered out. */
    private Set<String> known;

    public UILandingScreen(ILandingHost host)
    {
        this.host = host;

        /* Centered, with the backdrop showing all around it */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).wh(CARD_W, CARD_H).anchor(0.5F);

        this.banner = new UIElement();
        this.banner.relative(this.card).xy(0, 0).w(1F).h(BANNER_H);
        this.banner.add(new UIRenderable((context) -> this.renderBanner(context, this.banner.area)));

        UILabel title = UI.label(host.getTitle()).color(DIMMED);
        title.labelAnchor(0, 0.5F);
        title.relative(this.card).xy(PADDING, CONTENT_Y).w(MENU_W).h(HEADER_H);

        UILabel recentTitle = UI.label(UIKeys.PANELS_LANDING_RECENT).color(DIMMED);
        recentTitle.labelAnchor(0, 0.5F);
        recentTitle.relative(this.card).xy(RECENT_X, CONTENT_Y).w(CARD_W - RECENT_X - PADDING).h(HEADER_H);

        /* The menu: what leads into the editor first, what leads out of it after a gap */
        IKey createLabel = host.getCreateLabel();
        UILandingRow list = new UILandingRow(Icons.MORE, host.getListLabel(), (b) -> host.openDataManager());
        UIElement gap = new UIElement();
        UILandingRow wiki = new UILandingRow(Icons.HELP, UIKeys.SUPPORTERS_WIKI, (b) -> UIUtils.openWebLink(WIKI_LINK));

        this.folder = new UILandingRow(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, (b) -> this.openFolder());

        gap.h(GROUP_GAP);

        List<UIElement> rows = new ArrayList<>();

        /* Panels backed by assets (the model editor) and by files (the audio editor) have nothing
         * to create; there the list is the way in, and it wears the accent instead */
        if (createLabel == null)
        {
            list.accent();
        }
        else
        {
            UILandingRow create = new UILandingRow(Icons.ADD, createLabel, (b) -> host.addNewData(this.getContext()));

            create.accent();
            rows.add(create);
        }

        rows.add(list);
        rows.add(this.folder);
        rows.add(gap);
        rows.add(wiki);

        this.menu = UI.column(0, rows.toArray(new UIElement[0]));
        this.menu.relative(this.card).xy(PADDING, LIST_Y).w(MENU_W).h(1F, -(LIST_Y + PADDING));

        this.recent = new UIRecentDataList((entries) -> this.open(entries.get(0)), host::getTabIcon);
        this.recent.relative(this.card).xy(RECENT_X, LIST_Y).w(CARD_W - RECENT_X - PADDING).h(1F, -(LIST_Y + PADDING));
        this.recent.context(this::fillRecentMenu);

        this.card.add(new UIRenderable((context) -> this.renderCard(context, this.card.area)));
        this.card.add(this.banner, title, recentTitle, this.menu);
        this.card.add(new UIRenderable(this::renderEmptyHint), this.recent);
        this.add(new UIRenderable(this::renderBackdrop), this.card);

        this.refresh();
    }

    /** The card in the middle — what a tour points at when it points at the landing screen. */
    public UIElement getCard()
    {
        return this.card;
    }

    /** "BBS CS 2.6.0" — the mod's own version, without the Minecraft version the build appends. */
    public static String getVersion()
    {
        String version = getReleaseVersion();

        return "BBS CS" + (version.isEmpty() ? "" : " " + version);
    }

    private static String getReleaseVersion()
    {
        return FabricLoader.getInstance().getModContainer(BBSMod.MOD_ID)
            .map((mod) ->
            {
                String version = mod.getMetadata().getVersion().getFriendlyString();
                int dash = version.lastIndexOf('-');

                return dash > 0 ? version.substring(0, dash) : version;
            })
            .orElse("");
    }

    @Override
    public void setVisible(boolean visible)
    {
        boolean wasVisible = this.isVisible();

        super.setVisible(visible);

        if (visible && !wasVisible)
        {
            this.refresh();
            this.host.requestNames();
        }
    }

    /** The repository answered: whatever it no longer has drops out of the list. */
    public void fillNames(Collection<String> names)
    {
        this.known = new HashSet<>(names);

        this.refresh();
    }

    /**
     * Rebuild from the registry. The list is drawn from the settings right away, without waiting
     * for the repository — over the network that answer takes a moment, and the screen must not
     * flash empty every time a tab is emptied.
     */
    private void refresh()
    {
        boolean hasFolder = this.host.getDataFolder() != null;

        if (this.folder.isVisible() != hasFolder)
        {
            this.folder.setVisible(hasFolder);
            this.menu.resize();
        }

        List<Entry> entries = new ArrayList<>();

        for (Entry entry : BBSSettings.recentData.get(this.host.getRecentType()))
        {
            if (this.known == null || this.known.contains(entry.id))
            {
                entries.add(entry);
            }
        }

        this.recent.setList(entries);
        this.recent.deselect();
    }

    private void open(Entry entry)
    {
        this.host.pickData(entry.id);
    }

    private void openFolder()
    {
        File folder = this.host.getDataFolder();

        if (folder != null)
        {
            UIUtils.openFolder(folder);
        }
    }

    private void fillRecentMenu(ContextMenuManager menu)
    {
        Entry entry = this.recent.getEntryAtCursor(this.getContext());

        if (entry == null)
        {
            return;
        }

        menu.action(this.host.getTabIcon(entry.id), UIKeys.PANELS_LANDING_OPEN, () -> this.open(entry));
        menu.action(Icons.MORE, UIKeys.PANELS_LANDING_SHOW_IN_MANAGER, () -> this.host.showInList(entry.id));
        menu.action(Icons.REMOVE, UIKeys.PANELS_LANDING_FORGET, () -> this.forget(entry));
    }

    private void forget(Entry entry)
    {
        BBSSettings.recentData.forget(this.host.getRecentType(), entry.id);
        this.refresh();
    }

    /* Rendering */

    private void renderBackdrop(UIContext context)
    {
        this.backdrop.render(context, this.area);
    }

    private void renderCard(UIContext context, Area area)
    {
        int bg = BBSSettings.raisedSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 14, Colors.A50, 0);
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);
    }

    private void renderEmptyHint(UIContext context)
    {
        if (!this.recent.getList().isEmpty())
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        Area area = this.recent.area;
        List<String> lines = font.wrap(UIKeys.PANELS_LANDING_RECENT_EMPTY.get(), area.w - PADDING * 2);
        int lineH = font.getHeight() + 2;
        int y = area.my() - lines.size() * lineH / 2;

        for (String line : lines)
        {
            context.batcher.text(line, area.mx() - font.getWidth(line) / 2, y, MUTED, false);

            y += lineH;
        }
    }

    private void renderBanner(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        int brandWidth = font.getWidth(BANNER_BRAND);
        int versionWidth = font.getWidth(BANNER_VERSION) + 16;
        int x = area.x + PADDING;
        int height = font.getHeight() + 14;
        int y = area.ey() - PADDING - height;
        int width = brandWidth + versionWidth + 18;
        int textY = y + 7;
        int dividerX = x + 9 + brandWidth + 7;

        /* The banner is dark in either theme, so the text uses fixed light ink. */
        int ink = 0xfff2f4f8;
        int secondary = 0xffb4bccb;

        context.batcher.gradientVBox(area.x, area.y, area.ex(), area.ey(), 0xff0b0e16, 0xff141a28);
        context.batcher.box(area.x, area.ey() - 2, area.ex(), area.ey(), Colors.A100 | BBSSettings.primaryColor.get());
        context.batcher.box(x, y, x + width, y + height, 0xc010141d);
        context.batcher.outline(x, y, x + width, y + height, 0x28f2f4f8);
        context.batcher.box(x, y + 4, x + 2, y + height - 4, Colors.A100 | BBSSettings.primaryColor.get());
        context.batcher.text(BANNER_BRAND, x + 9, textY, ink, false);
        context.batcher.box(dividerX, textY, dividerX + 1, textY + font.getHeight(), 0x40b4bccb);
        context.batcher.text(BANNER_VERSION, dividerX + 8, textY, secondary, false);
    }
}
