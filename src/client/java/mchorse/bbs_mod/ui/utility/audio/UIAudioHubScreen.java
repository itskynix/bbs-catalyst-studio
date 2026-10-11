package mchorse.bbs_mod.ui.utility.audio;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
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
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modern Audio Editor Creative Hub / Sound Selector Screen.
 * Renders an atmospheric background with soft accent lights and sparks,
 * dynamic safe area centering, brand typography, full-width search bar
 * (no quick-add button per design specs), and a modern card grid of recent sounds.
 */
public class UIAudioHubScreen extends UILandingScreen
{
    private final UIAudioEditorPanel audioPanel;
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

    public UIAudioHubScreen(UIAudioEditorPanel audioPanel)
    {
        super(audioPanel);

        this.audioPanel = audioPanel;
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

        /* 2. Search Container (Full-width, NO quick-add button) */
        this.searchContainer = new UIElement();
        this.searchContainer.relative(this.card).x(0.5F).y(46).w(340).h(24).anchor(0.5F, 0F);

        this.searchBox = new UITextbox(this::onSearchChanged).placeholder(UIKeys.AUDIO_HUB_SEARCH);
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

        /* 3. Subtitle / Badge: Audio Editor */
        UILabel badge = UI.label(UIKeys.AUDIO_HUB_BADGE).color(Colors.setA(Colors.WHITE, 0.45F));
        badge.labelAnchor(0.5F, 0.5F);
        badge.relative(this.card).x(0.5F).y(74).w(1F).h(12).anchor(0.5F, 0F);

        /* 4. Recent Audio Card Grid Panel */
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

        this.recentTitle = UI.label(UIKeys.AUDIO_HUB_RECENT).color(Colors.setA(Colors.WHITE, 0.65F));
        this.recentTitle.labelAnchor(0, 0.5F);
        this.recentTitle.relative(this.projectsPanel).xy(10, 5).w(1F, -60).h(16);

        UIIcon openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(this.audioPanel.getDataFolder()));
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
            this.audioPanel.requestNames();
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
        this.cardsScroll.removeAll();

        List<ValueRecentData.Entry> recentList = BBSSettings.recentData.get("audio");
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
            this.recentTitle.label = UIKeys.AUDIO_HUB_ALL;
        }
        else
        {
            this.recentTitle.label = UIKeys.AUDIO_HUB_RECENT;
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

        for (String id : candidates)
        {
            long time = recentTimes.getOrDefault(id, 0L);
            this.cardsScroll.add(new UIAudioCard(this, id, time));
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

                if (first instanceof UIAudioCard card)
                {
                    UIUtils.playClick();
                    this.audioPanel.pickData(card.soundId);
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
            ? UIKeys.AUDIO_HUB_NO_SEARCH_RESULTS.format(this.searchQuery).get()
            : UIKeys.AUDIO_HUB_EMPTY_RECENT.get();

        int iconY = a.my() - 16;
        context.batcher.icon(searching ? Icons.SEARCH : Icons.SOUND, Colors.setA(Colors.WHITE, 0.35F), a.mx(), iconY, 0.5F, 0.5F);

        int textY = iconY + 14;
        List<String> lines = font.wrap(text, Math.max(120, a.w - 30));
        int lineH = font.getHeight() + 2;

        for (String line : lines)
        {
            context.batcher.text(line, a.mx() - font.getWidth(line) / 2, textY, Colors.setA(Colors.WHITE, 0.45F), false);
            textY += lineH;
        }
    }

    /**
     * Card component representing an audio file.
     */
    public static class UIAudioCard extends UIClickable<UIAudioCard>
    {
        private final UIAudioHubScreen hub;
        public final String soundId;
        private final String displayName;
        private final long recentTime;

        public UIAudioCard(UIAudioHubScreen hub, String soundId, long recentTime)
        {
            super((c) ->
            {
                UIUtils.playClick();
                hub.audioPanel.pickData(soundId);
            });

            this.hub = hub;
            this.soundId = soundId;
            this.recentTime = recentTime;

            DataPath path = new DataPath(soundId);
            this.displayName = path.getLast();

            String ago = recentTime > 0 ? UIRecentDataList.ago(recentTime) : "";
            String tooltipStr = soundId + (ago.isEmpty() ? "" : " (" + ago + ")");
            this.tooltip(IKey.raw(tooltipStr));

            this.context((menu) ->
            {
                menu.action(Icons.SOUND, UIKeys.PANELS_LANDING_OPEN, () -> hub.audioPanel.pickData(soundId));
                menu.action(Icons.MORE, UIKeys.PANELS_LANDING_SHOW_IN_MANAGER, () -> hub.audioPanel.showInList(soundId));
                menu.action(Icons.REMOVE, UIKeys.PANELS_LANDING_FORGET, () ->
                {
                    BBSSettings.recentData.forget("audio", soundId);
                    hub.refresh();
                });
            });
        }

        @Override
        protected UIAudioCard get()
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

            /* Preview box (16:9) with sound wave icon */
            int pad = 5;
            int px = a.x + pad;
            int py = a.y + pad;
            int pw = a.w - pad * 2;
            int ph = Math.max(30, (pw * 9) / 16);

            context.batcher.box(px, py, px + pw, py + ph, 0xf0080a10);
            context.batcher.outline(px, py, px + pw, py + ph, hovered ? 0x40ffffff : 0x18ffffff);

            int iconColor = hovered ? (Colors.A100 | primary) : Colors.setA(Colors.WHITE, 0.45F);
            context.batcher.icon(Icons.SOUND, iconColor, px + pw / 2, py + ph / 2, 0.5F, 0.5F);

            /* Labels */
            FontRenderer font = context.batcher.getFont();
            String trimmed = font.limitToWidth(this.displayName, a.w - 12);
            int nameX = a.x + pad;
            int nameY = py + ph + 3;
            int nameColor = hovered ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.9F);
            context.batcher.text(trimmed, nameX, nameY, nameColor, false);

            /* Subtitle / Meta row: format (.ogg / .wav) and ago */
            String format = this.soundId.toLowerCase().endsWith(".wav") ? ".wav" : ".ogg";
            String ago = this.recentTime > 0 ? UIRecentDataList.ago(this.recentTime) : "";
            String meta = ago.isEmpty() ? format : format + " \u2022 " + ago;
            String trimmedMeta = font.limitToWidth(meta, a.w - 12);
            int metaY = nameY + font.getHeight() + 1;
            context.batcher.text(trimmedMeta, nameX, metaY, Colors.setA(Colors.WHITE, 0.5F), false);
        }
    }
}
