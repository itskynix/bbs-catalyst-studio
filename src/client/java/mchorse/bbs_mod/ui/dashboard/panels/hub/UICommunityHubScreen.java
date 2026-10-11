package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.LandingBackdrop;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Community Asset Store Creative Hub Screen.
 * Features an atmospheric background with soft accent lights and sparks,
 * dynamic safe area centering, integrated search bar, category filter pills,
 * symmetric card grid with status badges, and quick access to details modal.
 */
public class UICommunityHubScreen extends UIElement
{
    public static final HubCategory[] PILL_CATEGORIES = new HubCategory[] {
        HubCategory.ALL,
        HubCategory.MODELS,
        HubCategory.RIGS,
        HubCategory.PARTICLES,
        HubCategory.SOUNDS
    };

    public final UICommunityHubPanel panel;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIElement card;
    private final UIElement searchContainer;
    private final UITextbox searchBox;
    private final UIElement pillsRow;
    private final List<UICategoryPill> pills = new ArrayList<>();
    private final UIElement projectsPanel;
    private final UILabel recentTitle;
    private final UIScrollView cardsScroll;
    private final GridResizer cardsGrid;

    private final List<HubAssetEntry> allAssets = new ArrayList<>();
    private final List<HubAssetEntry> displayedAssets = new ArrayList<>();
    private HubCategory currentCategory = HubCategory.ALL;
    private String searchQuery = "";

    public UICommunityHubScreen(UICommunityHubPanel panel)
    {
        super();

        this.panel = panel;

        /* Background atmospheric canvas */
        this.add(new UIRenderable((context) -> this.backdrop.render(context, this.area)));

        /* Main centered card container */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).anchor(0.5F);

        /* 1. Header: BBS CS & ver c1.2 */
        UIElement header = new UIElement();
        header.relative(this.card).x(0).y(6).w(1F).h(28);
        header.add(new UIRenderable((context) -> this.renderHeader(context, header.area)));

        /* 2. Search Container */
        this.searchContainer = new UIElement();
        this.searchContainer.relative(this.card).x(0.5F).y(38).w(360).h(24).anchor(0.5F, 0F);

        this.searchBox = new UITextbox(this::onSearchChanged).placeholder(UIKeys.COMMUNITY_HUB_SEARCH);
        this.searchBox.background(false);
        this.searchBox.relative(this.searchContainer).x(24).y(0).w(1F, -28).h(1F);

        this.searchContainer.add(new UIRenderable((context) ->
        {
            Area a = this.searchContainer.area;
            int bg = 0xd010141e;
            int border = this.searchBox.isFocused() ? (Colors.A100 | BBSSettings.primaryColor.get()) : 0x38ffffff;

            context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 6, Colors.A50, 0);
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);
            context.batcher.icon(Icons.SEARCH, 0x80ffffff, a.x + 6, a.my(), 0F, 0.5F);
        }));

        this.searchContainer.add(this.searchBox);

        /* 3. Subtitle / Badge: Community Asset Store */
        UILabel badge = UI.label(UIKeys.COMMUNITY_HUB_BADGE).color(Colors.setA(Colors.WHITE, 0.45F));
        badge.labelAnchor(0.5F, 0.5F);
        badge.relative(this.card).x(0.5F).y(66).w(1F).h(10).anchor(0.5F, 0F);

        /* 4. Category Filter Pills */
        this.pillsRow = new UIElement();
        this.pillsRow.relative(this.card).x(0.5F).y(80).h(18).anchor(0.5F, 0F);

        int pillW = 62;
        int pillGap = 6;
        this.pillsRow.w(PILL_CATEGORIES.length * pillW + (PILL_CATEGORIES.length - 1) * pillGap);

        for (int i = 0; i < PILL_CATEGORIES.length; i++)
        {
            HubCategory cat = PILL_CATEGORIES[i];
            UICategoryPill pill = new UICategoryPill(this, cat);
            pill.relative(this.pillsRow).x(i * (pillW + pillGap)).y(0).wh(pillW, 18);
            this.pills.add(pill);
            this.pillsRow.add(pill);
        }

        /* 5. Projects / Store Card Grid Panel */
        this.projectsPanel = new UIElement()
        {
            @Override
            protected boolean subMouseScrolled(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseWheel != 0)
                {
                    double speed = cardsScroll.scroll.scrollSpeed * BBSSettings.scrollingSensitivity.get();
                    cardsScroll.scroll.scrollBy((int) (-Math.copySign(speed, context.mouseWheel)));
                    return true;
                }

                return false;
            }
        };
        this.projectsPanel.relative(this.card).x(8).y(104).w(1F, -16).h(1F, -112);
        this.projectsPanel.add(new UIRenderable((context) ->
        {
            Area a = this.projectsPanel.area;
            int bg = 0xf00c1017;
            int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);

            context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 8, Colors.A50, 0);
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);
        }));

        this.recentTitle = UI.label(UIKeys.COMMUNITY_HUB_FEATURED).color(Colors.setA(Colors.WHITE, 0.65F));
        this.recentTitle.labelAnchor(0, 0.5F);
        this.recentTitle.relative(this.projectsPanel).xy(10, 5).w(1F, -80).h(16);

        UIIcon openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(BBSMod.getAssetsFolder()));
        openFolderBtn.tooltip(UIKeys.HUB_OPEN_FOLDER);
        openFolderBtn.relative(this.projectsPanel).x(1F, -62).y(4).wh(18, 18);

        UIIcon refreshBtn = new UIIcon(Icons.REFRESH, (b) -> this.panel.loadCatalog());
        refreshBtn.tooltip(UIKeys.HUB_REFRESH);
        refreshBtn.relative(this.projectsPanel).x(1F, -40).y(4).wh(18, 18);

        UIIcon moreBtn = new UIIcon(Icons.MORE, (b) -> this.panel.showClassicView());
        moreBtn.tooltip(UIKeys.COMMUNITY_HUB_CLASSIC_STORE);
        moreBtn.relative(this.projectsPanel).x(1F, -18).y(4).wh(18, 18);

        this.cardsScroll = new UIScrollView();
        this.cardsScroll.relative(this.projectsPanel).x(6).y(24).w(1F, -12).h(1F, -30);
        this.cardsScroll.scroll.scrollSpeed = 24;
        this.cardsScroll.scroll.scrollItemSize = 1;
        this.cardsGrid = this.cardsScroll.grid(8);
        this.cardsGrid.padding(4);
        this.cardsGrid.center();
        this.cardsGrid.scroll();
        this.cardsGrid.width(136).height(108);

        this.projectsPanel.add(this.recentTitle, openFolderBtn, refreshBtn, moreBtn, this.cardsScroll);
        this.projectsPanel.add(new UIRenderable(this::renderEmptyHint));

        this.card.add(header, this.searchContainer, badge, this.pillsRow, this.projectsPanel);
        this.add(this.card);

        this.refresh();
    }

    public void setCategory(HubCategory category)
    {
        this.currentCategory = category;
        this.refresh();
    }

    private void onSearchChanged(String query)
    {
        this.searchQuery = query == null ? "" : query.trim();
        this.refresh();
    }

    public void setAssets(List<HubAssetEntry> assets)
    {
        this.allAssets.clear();
        this.allAssets.addAll(assets);
        this.refresh();
    }

    public void refresh()
    {
        this.cardsScroll.removeAll();
        this.displayedAssets.clear();

        for (HubAssetEntry entry : this.allAssets)
        {
            if (entry.matches(this.searchQuery, this.currentCategory))
            {
                this.displayedAssets.add(entry);
            }
        }

        if (!this.searchQuery.isEmpty())
        {
            this.recentTitle.label = UIKeys.HUB_SEARCHING.format(this.searchQuery);
        }
        else if (this.currentCategory != HubCategory.ALL)
        {
            this.recentTitle.label = this.currentCategory.title;
        }
        else
        {
            this.recentTitle.label = UIKeys.COMMUNITY_HUB_FEATURED;
        }

        for (HubAssetEntry entry : this.displayedAssets)
        {
            this.cardsScroll.add(new UICommunityAssetCard(this, entry));
        }

        this.updateGridCentering();
        this.cardsScroll.resize();
        this.updateGridCentering();
        this.cardsScroll.scroll.clamp();
    }

    private void updateGridCentering()
    {
        int cardW = 136;
        int gap = 8;
        int scrollW = Math.max(cardW, this.cardsScroll.area.w);
        int cols = Math.max(1, (scrollW + gap) / (cardW + gap));
        int totalW = cols * cardW + (cols - 1) * gap;
        int padX = Math.max(4, (scrollW - totalW) / 2);

        this.cardsGrid.padding(padX, 4);

        int count = this.cardsScroll.getChildren().size();
        int rows = count == 0 ? 0 : (count + cols - 1) / cols;
        int totalH = rows == 0 ? 0 : rows * 108 + (rows - 1) * gap + 8;

        this.cardsScroll.scroll.scrollItemSize = 1;
        this.cardsScroll.scroll.setSize(totalH);
        this.cardsScroll.scroll.clamp();
    }

    @Override
    public void resize()
    {
        int maxW = Math.min(540, Math.max(320, this.area.w - 40));
        int maxH = Math.min(410, Math.max(250, this.area.h - 30));
        this.card.wh(maxW, maxH);

        int searchW = Math.min(360, maxW - 32);
        this.searchContainer.w(searchW);

        super.resize();

        this.updateGridCentering();
        this.cardsScroll.resize();
        this.cardsScroll.scroll.clamp();
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyCode() == GLFW.GLFW_KEY_ENTER && context.getKeyAction() == KeyAction.PRESSED)
        {
            if (!this.cardsScroll.getChildren().isEmpty())
            {
                IUIElement first = this.cardsScroll.getChildren().get(0);

                if (first instanceof UICommunityAssetCard card)
                {
                    UIUtils.playClick();
                    this.openDetailModal(card.entry);
                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

    public void openDetailModal(HubAssetEntry entry)
    {
        if (entry == null)
        {
            return;
        }

        UICommunityAssetDetailOverlay overlay = new UICommunityAssetDetailOverlay(this.panel, entry);
        UIOverlay.addOverlay(this.getContext(), overlay, 360, 360);
    }

    /* Rendering */

    private void renderHeader(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        MatrixStack stack = context.batcher.getContext().getMatrices();

        String title = "BBS CS";
        float scale = 2F;
        int titleW = (int) (font.getWidth(title) * scale);
        int titleH = (int) (font.getHeight() * scale);

        String ver = "ver c1.2";
        int verW = font.getWidth(ver);

        stack.push();
        stack.translate(area.mx() - titleW / 2F, area.y, 0F);
        stack.scale(scale, scale, 1F);
        context.batcher.text(title, 0, 0, Colors.WHITE, true);
        stack.pop();

        int verY = area.y + titleH + 2;
        context.batcher.text(ver, area.mx() - verW / 2, verY, Colors.setA(Colors.WHITE, 0.55F), false);
    }

    private void renderEmptyHint(UIContext context)
    {
        if (this.cardsScroll.getChildren().isEmpty())
        {
            Area a = this.projectsPanel.area;
            IKey key = this.searchQuery.isEmpty() ? UIKeys.COMMUNITY_HUB_EMPTY : UIKeys.COMMUNITY_HUB_NO_RESULTS.format(this.searchQuery);
            String msg = key.get();
            FontRenderer font = context.batcher.getFont();
            int w = font.getWidth(msg);

            context.batcher.text(msg, a.mx() - w / 2, a.my() - 4, Colors.setA(Colors.WHITE, 0.45F));
        }
    }

    /* Sub-components */

    public static class UICategoryPill extends UIClickable<UICategoryPill>
    {
        private final UICommunityHubScreen hub;
        public final HubCategory category;

        public UICategoryPill(UICommunityHubScreen hub, HubCategory category)
        {
            super((b) ->
            {
                UIUtils.playClick();
                hub.setCategory(category);
            });
            this.hub = hub;
            this.category = category;
        }

        @Override
        protected UICategoryPill get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Area a = this.area;
            boolean active = this.hub.currentCategory == this.category;
            boolean hover = this.hover;
            int primary = BBSSettings.primaryColor.get();

            int bg = active ? 0xe01a2436 : (hover ? 0xc0141a24 : 0x700e121a);
            int border = active ? (Colors.A100 | primary) : (hover ? 0x60ffffff : 0x24ffffff);
            int textColor = active ? Colors.WHITE : (hover ? 0xe0ffffff : 0x90ffffff);

            if (active || hover)
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 4, active ? Colors.setA(primary, 0.4F) : Colors.A50, 0);
            }

            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);

            String label = this.category.title.get();
            FontRenderer font = context.batcher.getFont();
            int tw = font.getWidth(label);
            context.batcher.text(label, a.mx() - tw / 2, a.my() - font.getHeight() / 2, textColor, false);
        }
    }

    public static class UICommunityAssetCard extends UIClickable<UICommunityAssetCard>
    {
        private final UICommunityHubScreen hub;
        public final HubAssetEntry entry;

        public UICommunityAssetCard(UICommunityHubScreen hub, HubAssetEntry entry)
        {
            super((c) ->
            {
                UIUtils.playClick();
                hub.openDetailModal(entry);
            });

            this.hub = hub;
            this.entry = entry;

            String tooltipStr = entry.name + " (" + entry.getFormattedSize() + ") - by " + entry.author;
            this.tooltip(IKey.raw(tooltipStr));

            this.context((menu) ->
            {
                menu.action(Icons.MORE, UIKeys.COMMUNITY_HUB_DETAILS, () -> hub.openDetailModal(entry));

                if (entry.hasUpdate())
                {
                    menu.action(Icons.DOWNLOAD, UIKeys.COMMUNITY_HUB_UPDATE_AVAILABLE, () -> hub.panel.startInstall(entry));
                }
                else if (!entry.isInstalled())
                {
                    menu.action(Icons.DOWNLOAD, UIKeys.HUB_INSTALL, () -> hub.panel.startInstall(entry));
                }

                if (entry.isInstalled())
                {
                    menu.action(Icons.FOLDER, UIKeys.COMMUNITY_HUB_OPEN_FILES, () -> UIUtils.openFolder(BBSMod.getAssetsFolder()));
                    menu.action(Icons.REMOVE, UIKeys.HUB_UNINSTALL, () -> hub.panel.startUninstall(entry));
                }
            });
        }

        @Override
        protected UICommunityAssetCard get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Area a = this.area;
            boolean hovered = this.hover;
            int primary = BBSSettings.primaryColor.get();

            int bg = hovered ? 0xe01a2232 : 0xc010141d;
            int border = hovered ? (Colors.A100 | primary) : 0x2affffff;

            if (hovered)
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 8, Colors.setA(primary, 0.35F), 0);
            }
            else
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 4, Colors.A50, 0);
            }

            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);

            /* Preview box */
            int pad = 5;
            int px = a.x + pad;
            int py = a.y + pad;
            int pw = a.w - pad * 2;
            int ph = 64;

            context.batcher.box(px, py, px + pw, py + ph, 0xf0080a10);
            context.batcher.outline(px, py, px + pw, py + ph, hovered ? 0x40ffffff : 0x18ffffff);

            Texture thumb = HubThumbnailManager.getThumbnail(this.entry);

            if (thumb != null && thumb != BBSModClient.getTextures().getError())
            {
                int tw = Math.max(1, thumb.width);
                int th = Math.max(1, thumb.height);
                float scale = Math.min((float) (pw - 4) / tw, (float) (ph - 4) / th);
                int fw = Math.max(1, Math.round(tw * scale));
                int fh = Math.max(1, Math.round(th * scale));
                int fx = px + (pw - fw) / 2;
                int fy = py + (ph - fh) / 2;

                context.batcher.fullTexturedBox(thumb, fx, fy, fw, fh);
            }
            else
            {
                int catColor = this.entry.category.color;
                context.batcher.box(px + 2, py + 2, px + pw - 2, py + ph - 2, 0x2a000000 | (catColor & 0xffffff));
                context.batcher.icon(this.entry.category.icon, px + pw / 2 - 8, py + ph / 2 - 14);

                String catTitle = this.entry.category.title.get().toUpperCase();
                FontRenderer font = context.batcher.getFont();
                int catW = font.getWidth(catTitle);
                context.batcher.text(catTitle, px + pw / 2 - catW / 2, py + ph / 2 + 4, Colors.setA(Colors.WHITE, 0.7F), false);
            }

            /* Status badge overlay on preview box */
            boolean isInstalled = this.entry.isInstalled();
            boolean hasUpdate = this.entry.hasUpdate();

            if (hasUpdate)
            {
                int bw = 46;
                context.batcher.box(px + pw - bw - 2, py + 2, px + pw - 2, py + 13, 0xeee65100);
                context.batcher.text("UPDATE", px + pw - bw + 2, py + 3, Colors.WHITE, false);
            }
            else if (isInstalled)
            {
                int bw = 52;
                context.batcher.box(px + pw - bw - 2, py + 2, px + pw - 2, py + 13, 0xee2e7d32);
                context.batcher.text("INSTALLED", px + pw - bw + 2, py + 3, Colors.WHITE, false);
            }

            /* Labels below preview box */
            FontRenderer font = context.batcher.getFont();
            String name = font.limitToWidth(this.entry.name, pw);
            context.batcher.text(name, a.x + pad + 2, py + ph + 4, Colors.WHITE, false);

            String meta = font.limitToWidth("by " + this.entry.author + " • " + this.entry.getFormattedSize(), pw);
            context.batcher.text(meta, a.x + pad + 2, py + ph + 4 + font.getHeight() + 1, Colors.setA(Colors.WHITE, 0.5F), false);
        }
    }
}
