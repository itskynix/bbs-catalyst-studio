package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueRecentData;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.LandingBackdrop;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UIRecentDataList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modern Film Studio Creative Hub / Project Selector Screen.
 * Renders an atmospheric background with soft accent lights and sparks,
 * dynamic safe area centering, large brand typography, integrated search bar
 * with quick-add button, and a modern card grid of recent projects.
 */
public class UIFilmHubScreen extends UILandingScreen
{
    private static final Map<String, Texture> THUMBNAILS = new HashMap<>();
    private static final Set<String> FAILED_THUMBNAILS = new HashSet<>();

    private final UIFilmPanel filmPanel;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIElement card;
    private final UIElement searchContainer;
    private final UITextbox searchBox;
    private final UIElement projectsPanel;
    private final UILabel recentTitle;
    private final UIScrollView cardsScroll;
    private final GridResizer cardsGrid;

    private Set<String> known;
    private String searchQuery = "";

    public UIFilmHubScreen(UIFilmPanel filmPanel)
    {
        super(filmPanel);

        this.filmPanel = filmPanel;
        this.removeAll();

        /* Background atmospheric canvas */
        this.add(new UIRenderable((context) -> this.backdrop.render(context, this.area)));

        /* Main centered card container */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).anchor(0.5F);

        /* 1. Header: BBS CS & ver c1.2 */
        UIElement header = new UIElement();
        header.relative(this.card).x(0).y(8).w(1F).h(34);
        header.add(new UIRenderable((context) -> this.renderHeader(context, header.area)));

        /* 2. Search Container with embedded + button */
        this.searchContainer = new UIElement();
        this.searchContainer.relative(this.card).x(0.5F).y(46).w(340).h(24).anchor(0.5F, 0F);

        this.searchBox = new UITextbox(this::onSearchChanged).placeholder(UIKeys.FILM_HUB_SEARCH);
        this.searchBox.background(false);
        this.searchBox.relative(this.searchContainer).x(24).y(0).w(1F, -48).h(1F);

        UIIcon addBtn = new UIIcon(Icons.ADD, (b) -> this.filmPanel.addNewData(this.getContext()));
        addBtn.tooltip(UIKeys.FILM_HUB_ADD_FILM);
        addBtn.relative(this.searchContainer).x(1F, -22).y(2).wh(20, 20);

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

        this.searchContainer.add(this.searchBox, addBtn);

        /* 3. Subtitle / Badge: Film Editör */
        UILabel badge = UI.label(UIKeys.FILM_HUB_EDITOR_BADGE).color(Colors.setA(Colors.WHITE, 0.45F));
        badge.labelAnchor(0.5F, 0.5F);
        badge.relative(this.card).x(0.5F).y(74).w(1F).h(12).anchor(0.5F, 0F);

        /* 4. Recent Projects Card Grid Panel */
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
        this.projectsPanel.relative(this.card).x(8).y(92).w(1F, -16).h(1F, -100);
        this.projectsPanel.add(new UIRenderable((context) ->
        {
            Area a = this.projectsPanel.area;
            int bg = 0xf00c1017;
            int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);

            context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 8, Colors.A50, 0);
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);
        }));

        this.recentTitle = UI.label(UIKeys.FILM_HUB_RECENT_PROJECTS).color(Colors.setA(Colors.WHITE, 0.65F));
        this.recentTitle.labelAnchor(0, 0.5F);
        this.recentTitle.relative(this.projectsPanel).xy(10, 5).w(1F, -60).h(16);

        UIIcon openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(BBSMod.getFilms().getFolder()));
        openFolderBtn.tooltip(UIKeys.HUB_OPEN_FOLDER);
        openFolderBtn.relative(this.projectsPanel).x(1F, -44).y(4).wh(18, 18);

        UIIcon refreshBtn = new UIIcon(Icons.REFRESH, (b) -> this.refresh());
        refreshBtn.tooltip(UIKeys.HUB_REFRESH);
        refreshBtn.relative(this.projectsPanel).x(1F, -22).y(4).wh(18, 18);

        this.cardsScroll = new UIScrollView();
        this.cardsScroll.relative(this.projectsPanel).x(6).y(24).w(1F, -12).h(1F, -30);
        this.cardsScroll.scroll.scrollSpeed = 24;
        this.cardsScroll.scroll.scrollItemSize = 1;
        this.cardsGrid = this.cardsScroll.grid(8);
        this.cardsGrid.padding(4);
        this.cardsGrid.center();
        this.cardsGrid.scroll();
        this.cardsGrid.width(136).height(108);

        this.projectsPanel.add(this.recentTitle, openFolderBtn, refreshBtn, this.cardsScroll);
        this.projectsPanel.add(new UIRenderable(this::renderEmptyHint));

        this.card.add(header, this.searchContainer, badge, this.projectsPanel);
        this.add(this.card);

        this.refresh();
    }

    @Override
    public UIElement getCard()
    {
        return this.card;
    }

    private void onSearchChanged(String query)
    {
        this.searchQuery = query == null ? "" : query.trim();
        this.refresh();
    }

    @Override
    public void setVisible(boolean visible)
    {
        boolean wasVisible = this.isVisible();
        super.setVisible(visible);

        if (visible && !wasVisible)
        {
            this.refresh();
            this.filmPanel.requestNames();
        }
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        this.known = new HashSet<>(names);
        this.refresh();
    }

    public void refresh()
    {
        FAILED_THUMBNAILS.clear();
        this.cardsScroll.removeAll();

        List<ValueRecentData.Entry> recentList = BBSSettings.recentData.get("films");
        Map<String, Long> recentTimes = new HashMap<>();

        for (ValueRecentData.Entry entry : recentList)
        {
            recentTimes.put(entry.id, entry.time);
        }

        if (!this.searchQuery.isEmpty())
        {
            this.recentTitle.label = UIKeys.HUB_SEARCHING.format(this.searchQuery);
        }
        else if (recentList.isEmpty())
        {
            this.recentTitle.label = UIKeys.FILM_HUB_ALL;
        }
        else
        {
            this.recentTitle.label = UIKeys.FILM_HUB_RECENT_PROJECTS;
        }

        Set<String> candidates = new LinkedHashSet<>();

        if (this.searchQuery.isEmpty())
        {
            for (ValueRecentData.Entry entry : recentList)
            {
                if (this.known == null || this.known.contains(entry.id))
                {
                    candidates.add(entry.id);
                }
            }

            if (candidates.isEmpty() && this.known != null)
            {
                candidates.addAll(this.known);
            }
        }
        else
        {
            String q = this.searchQuery.toLowerCase();

            for (ValueRecentData.Entry entry : recentList)
            {
                if (entry.id.toLowerCase().contains(q))
                {
                    candidates.add(entry.id);
                }
            }

            if (this.known != null)
            {
                for (String id : this.known)
                {
                    if (id.toLowerCase().contains(q))
                    {
                        candidates.add(id);
                    }
                }
            }
        }

        for (String filmId : candidates)
        {
            long time = recentTimes.getOrDefault(filmId, 0L);
            this.cardsScroll.add(new UIFilmCard(this, filmId, time));
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

                if (first instanceof UIFilmCard card)
                {
                    UIUtils.playClick();
                    this.filmPanel.pickData(card.filmId);
                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
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

        stack.push();
        stack.translate(area.mx() - titleW / 2F, area.y, 0F);
        stack.scale(scale, scale, 1F);
        context.batcher.text(title, 0, 0, Colors.WHITE, true);
        stack.pop();

        String version = "ver c1.2";
        int verW = font.getWidth(version);
        int verY = area.y + titleH + 2;
        context.batcher.text(version, area.mx() - verW / 2, verY, Colors.setA(Colors.WHITE, 0.55F), false);
    }

    private void renderEmptyHint(UIContext context)
    {
        if (!this.cardsScroll.getChildren().isEmpty())
        {
            return;
        }

        Area a = this.cardsScroll.area;
        FontRenderer font = context.batcher.getFont();
        boolean searching = !this.searchQuery.isEmpty();
        String text = searching
            ? UIKeys.FILM_HUB_NO_SEARCH_RESULTS.format(this.searchQuery).get()
            : UIKeys.FILM_HUB_EMPTY_RECENT.get();

        int iconY = a.my() - 16;
        context.batcher.icon(searching ? Icons.SEARCH : Icons.FILM, Colors.setA(Colors.WHITE, 0.35F), a.mx(), iconY, 0.5F, 0.5F);

        int textY = iconY + 14;
        List<String> lines = font.wrap(text, Math.max(120, a.w - 30));
        int lineH = font.getHeight() + 2;

        for (String line : lines)
        {
            context.batcher.text(line, a.mx() - font.getWidth(line) / 2, textY, Colors.setA(Colors.WHITE, 0.45F), false);
            textY += lineH;
        }
    }

    public static Texture getThumbnail(String filmId)
    {
        if (filmId == null || filmId.isEmpty() || FAILED_THUMBNAILS.contains(filmId))
        {
            return null;
        }

        Texture existing = THUMBNAILS.get(filmId);

        if (existing != null)
        {
            return existing;
        }

        File folder = BBSMod.getFilms() != null ? BBSMod.getFilms().getFolder() : null;

        if (folder == null)
        {
            return null;
        }

        File screenshots = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
        File[] candidates = new File[] {
            new File(folder, "thumbnails/" + filmId + ".png"),
            new File(folder, filmId + ".png"),
            new File(folder, ".thumbnails/" + filmId + ".png"),
            new File(folder, filmId + "/thumbnail.png"),
            new File(screenshots, filmId + ".png")
        };

        File found = null;

        for (File c : candidates)
        {
            if (c.exists() && c.isFile() && c.length() > 0)
            {
                found = c;
                break;
            }
        }

        if (found == null)
        {
            FAILED_THUMBNAILS.add(filmId);
            return null;
        }

        try (InputStream in = new FileInputStream(found))
        {
            Link link = new Link("film_thumb", filmId.replace('/', '_') + "_" + found.getName());
            Texture tex = BBSModClient.getTextures().createTexture(link);

            tex.bind();
            tex.uploadTexture(Pixels.fromPNGStream(in));
            THUMBNAILS.put(filmId, tex);

            return tex;
        }
        catch (Exception e)
        {
            FAILED_THUMBNAILS.add(filmId);
            return null;
        }
    }

    /**
     * Invalidate cached thumbnail texture for the given film so it reloads fresh.
     * Ensures texture deletion and cache modification run safely on Minecraft's render thread.
     */
    public static void invalidateThumbnail(String filmId)
    {
        if (filmId == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return;
        }

        if (mc.isOnThread())
        {
            Texture tex = THUMBNAILS.remove(filmId);

            if (tex != null)
            {
                tex.delete();
            }

            FAILED_THUMBNAILS.remove(filmId);
        }
        else
        {
            mc.execute(() -> invalidateThumbnail(filmId));
        }
    }

    /**
     * Card component representing a film project.
     */
    public static class UIFilmCard extends UIClickable<UIFilmCard>
    {
        private final UIFilmHubScreen hub;
        public final String filmId;
        private final String displayName;
        private final long recentTime;

        public UIFilmCard(UIFilmHubScreen hub, String filmId, long recentTime)
        {
            super((c) ->
            {
                UIUtils.playClick();
                hub.filmPanel.pickData(filmId);
            });

            this.hub = hub;
            this.filmId = filmId;
            this.recentTime = recentTime;

            DataPath path = new DataPath(filmId);
            this.displayName = path.getLast();

            String ago = recentTime > 0 ? UIRecentDataList.ago(recentTime) : "";
            String tooltipStr = filmId + (ago.isEmpty() ? "" : " (" + ago + ")");
            this.tooltip(IKey.raw(tooltipStr));

            this.context((menu) ->
            {
                menu.action(Icons.FILM, UIKeys.PANELS_LANDING_OPEN, () -> hub.filmPanel.pickData(filmId));
                menu.action(Icons.MORE, UIKeys.PANELS_LANDING_SHOW_IN_MANAGER, () -> hub.filmPanel.showInList(filmId));
                menu.action(Icons.REMOVE, UIKeys.PANELS_LANDING_FORGET, () ->
                {
                    BBSSettings.recentData.forget("films", filmId);
                    hub.refresh();
                });
            });
        }

        @Override
        protected UIFilmCard get()
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

            /* Preview box (16:9) */
            int pad = 5;
            int px = a.x + pad;
            int py = a.y + pad;
            int pw = a.w - pad * 2;
            int ph = Math.max(30, (pw * 9) / 16);

            context.batcher.box(px, py, px + pw, py + ph, 0xf0080a10);
            context.batcher.outline(px, py, px + pw, py + ph, hovered ? 0x40ffffff : 0x18ffffff);

            Texture thumb = getThumbnail(this.filmId);

            if (thumb != null)
            {
                context.batcher.texturedBox(thumb, Colors.WHITE, px, py, pw, ph, 0, 0, thumb.width, thumb.height, thumb.width, thumb.height);
            }
            else
            {
                int iconColor = hovered ? (Colors.A100 | primary) : Colors.setA(Colors.WHITE, 0.45F);
                context.batcher.icon(Icons.FILM, iconColor, px + pw / 2, py + ph / 2, 0.5F, 0.5F);
            }

            /* Labels */
            FontRenderer font = context.batcher.getFont();
            String trimmed = font.limitToWidth(this.displayName, a.w - 12);
            int nameX = a.x + pad;
            int nameY = py + ph + 3;
            int nameColor = hovered ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.9F);
            context.batcher.text(trimmed, nameX, nameY, nameColor, false);

            /* Subtitle / Meta row: e.g. ago */
            String ago = this.recentTime > 0 ? UIRecentDataList.ago(this.recentTime) : "—";
            String trimmedMeta = font.limitToWidth(ago, a.w - 12);
            int metaY = nameY + font.getHeight() + 1;
            context.batcher.text(trimmedMeta, nameX, metaY, Colors.setA(Colors.WHITE, 0.5F), false);
        }
    }
}
