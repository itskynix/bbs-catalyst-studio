package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
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
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.textures.TextureFiles;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.GifFrames;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modern Texture Manager & Media Library Creative Hub Screen.
 * Features an atmospheric background with soft accent lights and sparks,
 * dynamic safe area centering, quick import via native OS file dialog,
 * category filter pills (All, Skins, VFX, UI, Animated),
 * symmetric card grid with checkerboard pattern preview, and rich context menu actions.
 */
public class UITexturesHubScreen extends UILandingScreen
{
    public enum TextureCategory
    {
        ALL(UIKeys.TEXTURES_HUB_CAT_ALL),
        SKINS(UIKeys.TEXTURES_HUB_CAT_SKINS),
        VFX(UIKeys.TEXTURES_HUB_CAT_VFX),
        UI(UIKeys.TEXTURES_HUB_CAT_UI),
        ANIMATED(UIKeys.TEXTURES_HUB_CAT_ANIMATED);

        public final IKey label;

        TextureCategory(IKey label)
        {
            this.label = label;
        }

        public boolean matches(Link link)
        {
            if (this == ALL)
            {
                return true;
            }

            String p = link.path.toLowerCase();
            String src = link.source.toLowerCase();

            if (this == SKINS)
            {
                return src.equals("player") || p.contains("skin") || p.contains("player") || p.contains("entity") || p.contains("steve") || p.contains("alex");
            }

            if (this == VFX)
            {
                return p.contains("particle") || p.contains("vfx") || p.contains("effect") || p.contains("spark") || p.contains("fire") || p.contains("smoke");
            }

            if (this == UI)
            {
                return p.contains("gui") || p.contains("ui") || p.contains("hud") || p.contains("icon") || p.contains("widget") || p.contains("button") || p.contains("font");
            }

            if (this == ANIMATED)
            {
                if (GifFrames.isGif(link) || p.contains("anim"))
                {
                    return true;
                }

                File f = TextureFiles.file(link);
                if (f != null && f.getParentFile() != null)
                {
                    File meta = new File(f.getParentFile(), f.getName() + ".mcmeta");
                    return meta.exists();
                }

                return false;
            }

            return true;
        }
    }

    private final UITextureManagerPanel texturePanel;
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

    private Set<String> known;
    private String searchQuery = "";
    private TextureCategory currentCategory = TextureCategory.ALL;

    public UITexturesHubScreen(UITextureManagerPanel texturePanel)
    {
        super(texturePanel);

        this.texturePanel = texturePanel;
        this.removeAll();

        /* Background atmospheric canvas */
        this.add(new UIRenderable((context) -> this.backdrop.render(context, this.area)));

        /* Main centered card container */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).anchor(0.5F);

        /* 1. Header: BBS CS & ver c1.2 */
        UIElement header = new UIElement();
        header.relative(this.card).x(0).y(6).w(1F).h(28);
        header.add(new UIRenderable((context) -> this.renderHeader(context, header.area)));

        /* 2. Search Container with embedded + (Import) button */
        this.searchContainer = new UIElement();
        this.searchContainer.relative(this.card).x(0.5F).y(38).w(360).h(24).anchor(0.5F, 0F);

        this.searchBox = new UITextbox(this::onSearchChanged).placeholder(UIKeys.TEXTURES_HUB_SEARCH);
        this.searchBox.background(false);
        this.searchBox.relative(this.searchContainer).x(24).y(0).w(1F, -48).h(1F);

        UIIcon importBtn = new UIIcon(Icons.ADD, (b) -> this.promptImport());
        importBtn.tooltip(UIKeys.TEXTURES_HUB_ADD);
        importBtn.relative(this.searchContainer).x(1F, -22).y(2).wh(20, 20);

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

        this.searchContainer.add(this.searchBox, importBtn);

        /* 3. Subtitle / Badge: Texture Library */
        UILabel badge = UI.label(UIKeys.TEXTURES_HUB_BADGE).color(Colors.setA(Colors.WHITE, 0.45F));
        badge.labelAnchor(0.5F, 0.5F);
        badge.relative(this.card).x(0.5F).y(66).w(1F).h(10).anchor(0.5F, 0F);

        /* 4. Category Filter Pills */
        this.pillsRow = new UIElement();
        this.pillsRow.relative(this.card).x(0.5F).y(80).h(18).anchor(0.5F, 0F);

        int pillW = 64;
        int pillGap = 6;
        TextureCategory[] categories = TextureCategory.values();
        this.pillsRow.w(categories.length * pillW + (categories.length - 1) * pillGap);

        for (int i = 0; i < categories.length; i++)
        {
            TextureCategory cat = categories[i];
            UICategoryPill pill = new UICategoryPill(this, cat);
            pill.relative(this.pillsRow).x(i * (pillW + pillGap)).y(0).wh(pillW, 18);
            this.pills.add(pill);
            this.pillsRow.add(pill);
        }

        /* 5. Projects / Textures Card Grid Panel */
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

        this.recentTitle = UI.label(UIKeys.TEXTURES_HUB_RECENT).color(Colors.setA(Colors.WHITE, 0.65F));
        this.recentTitle.labelAnchor(0, 0.5F);
        this.recentTitle.relative(this.projectsPanel).xy(10, 5).w(1F, -80).h(16);

        UIIcon openFolderBtn = new UIIcon(Icons.FOLDER, (b) ->
        {
            File assetsDir = TextureFiles.file(Link.assets(""));
            if (assetsDir != null)
            {
                UIUtils.openFolder(assetsDir);
            }
        });
        openFolderBtn.tooltip(UIKeys.HUB_OPEN_FOLDER);
        openFolderBtn.relative(this.projectsPanel).x(1F, -62).y(4).wh(18, 18);

        UIIcon refreshBtn = new UIIcon(Icons.REFRESH, (b) ->
        {
            this.known = null;
            this.refresh();
        });
        refreshBtn.tooltip(UIKeys.HUB_REFRESH);
        refreshBtn.relative(this.projectsPanel).x(1F, -40).y(4).wh(18, 18);

        UIIcon moreBtn = new UIIcon(Icons.MORE, (b) -> this.texturePanel.showPicker());
        moreBtn.tooltip(UIKeys.PANELS_KEYS_OPEN_DATA_MANAGER);
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

    @Override
    public UIElement getCard()
    {
        return this.card;
    }

    public void setCategory(TextureCategory category)
    {
        this.currentCategory = category;
        this.refresh();
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
        }
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        this.known = new HashSet<>(names);
        this.refresh();
    }

    public Set<String> collectAllTextures()
    {
        Set<String> textures = new LinkedHashSet<>();

        for (String source : BBSMod.getProvider().getSourceKeys())
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(new Link(source, ""), true))
            {
                if (TextureFiles.isTexture(link) && !link.path.contains("textures/banners/"))
                {
                    textures.add(link.toString());
                }
            }
        }

        return textures;
    }

    public void refresh()
    {
        this.cardsScroll.removeAll();

        if (this.known == null)
        {
            this.known = this.collectAllTextures();
        }

        List<ValueRecentData.Entry> recentList = BBSSettings.recentData.get("textures");
        Map<String, Long> recentTimes = new HashMap<>();

        for (ValueRecentData.Entry entry : recentList)
        {
            recentTimes.put(entry.id, entry.time);
        }

        if (!this.searchQuery.isEmpty())
        {
            this.recentTitle.label = UIKeys.HUB_SEARCHING.format(this.searchQuery);
        }
        else if (recentList.isEmpty() || this.currentCategory != TextureCategory.ALL)
        {
            this.recentTitle.label = this.currentCategory == TextureCategory.ALL ? UIKeys.TEXTURES_HUB_ALL : this.currentCategory.label;
        }
        else
        {
            this.recentTitle.label = UIKeys.TEXTURES_HUB_RECENT;
        }

        Set<String> candidates = new LinkedHashSet<>();

        if (this.searchQuery.isEmpty())
        {
            if (this.currentCategory == TextureCategory.ALL)
            {
                for (ValueRecentData.Entry entry : recentList)
                {
                    Link link = Link.create(entry.id);
                    if (TextureFiles.isTexture(link) && (this.known.isEmpty() || this.known.contains(entry.id)))
                    {
                        candidates.add(entry.id);
                    }
                }

                if (candidates.isEmpty() && !this.known.isEmpty())
                {
                    candidates.addAll(this.known);
                }
            }
            else
            {
                for (String id : this.known)
                {
                    Link link = Link.create(id);
                    if (this.currentCategory.matches(link))
                    {
                        candidates.add(id);
                    }
                }
            }
        }
        else
        {
            String q = this.searchQuery.toLowerCase();

            for (String id : this.known)
            {
                Link link = Link.create(id);
                if (this.currentCategory.matches(link))
                {
                    String fileName = StringUtils.fileName(link.path).toLowerCase();
                    if (id.toLowerCase().contains(q) || fileName.contains(q))
                    {
                        candidates.add(id);
                    }
                }
            }
        }

        int count = 0;
        int maxItems = 300;

        for (String textureId : candidates)
        {
            if (count++ >= maxItems)
            {
                break;
            }

            Link link = Link.create(textureId);
            long time = recentTimes.getOrDefault(textureId, 0L);
            this.cardsScroll.add(new UITextureCard(this, link, time));
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

                if (first instanceof UITextureCard card)
                {
                    UIUtils.playClick();
                    this.texturePanel.openTexture(card.link);
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
            IKey key = this.searchQuery.isEmpty() ? UIKeys.TEXTURES_HUB_EMPTY_RECENT : UIKeys.TEXTURES_HUB_NO_SEARCH_RESULTS.format(this.searchQuery);
            String msg = key.get();
            FontRenderer font = context.batcher.getFont();
            int w = font.getWidth(msg);

            context.batcher.text(msg, a.mx() - w / 2, a.my() - 4, Colors.setA(Colors.WHITE, 0.45F));
        }
    }

    /* Operations & Dialogs */

    public void promptImport()
    {
        File into = TextureFiles.file(Link.assets(""));

        if (into != null && !into.exists())
        {
            into.mkdirs();
        }

        if (into == null || !into.isDirectory())
        {
            return;
        }

        UIFileDialogs.pickFile(UIKeys.TEXTURES_BROWSER_IMPORT_TITLE, into, new String[] {"*.png", "*.gif"}, UIKeys.TEXTURES_BROWSER_IMPORT_FILTER, (file) ->
        {
            if (file == null || !file.isFile())
            {
                return;
            }

            try
            {
                File target = new File(into, file.getName());
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                BBSResources.markAssetsChanged();

                Link imported = new Link(Link.ASSETS, file.getName());
                BBSSettings.recentData.touch("textures", imported.toString());

                this.known = null;
                this.refresh();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    public void promptRename(Link link)
    {
        String name = StringUtils.fileName(link.path).replace("/", "");
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(UIKeys.TEXTURES_BROWSER_RENAME_TITLE, UIKeys.TEXTURES_BROWSER_RENAME_DESCRIPTION, (newName) ->
        {
            Link renamed = TextureFiles.rename(link, newName);

            if (renamed != null)
            {
                BBSSettings.recentData.rename("textures", link.toString(), renamed.toString());
                this.known = null;
                this.refresh();
            }
        });

        panel.text.setText(name);
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    public void promptDelete(Link link)
    {
        String what = StringUtils.fileName(link.path);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.TEXTURES_BROWSER_DELETE_TITLE, UIKeys.TEXTURES_BROWSER_DELETE_DESCRIPTION.format(what), (confirm) ->
        {
            if (confirm)
            {
                TextureFiles.delete(link);
                BBSSettings.recentData.forget("textures", link.toString());
                this.known = null;
                this.refresh();
            }
        });

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    public void duplicate(Link link)
    {
        Link dupe = TextureFiles.duplicate(link);

        if (dupe != null)
        {
            BBSSettings.recentData.touch("textures", dupe.toString());
            this.known = null;
            this.refresh();
        }
    }

    /* Sub-components */

    public static class UICategoryPill extends UIClickable<UICategoryPill>
    {
        private final UITexturesHubScreen hub;
        public final TextureCategory category;

        public UICategoryPill(UITexturesHubScreen hub, TextureCategory category)
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

            String label = this.category.label.get();
            FontRenderer font = context.batcher.getFont();
            int tw = font.getWidth(label);
            context.batcher.text(label, a.mx() - tw / 2, a.my() - font.getHeight() / 2, textColor, false);
        }
    }

    public static class UITextureCard extends UIClickable<UITextureCard>
    {
        private final UITexturesHubScreen hub;
        public final Link link;
        private final String displayName;
        private final long recentTime;

        public UITextureCard(UITexturesHubScreen hub, Link link, long recentTime)
        {
            super((c) ->
            {
                UIUtils.playClick();
                hub.texturePanel.openTexture(link);
            });

            this.hub = hub;
            this.link = link;
            this.recentTime = recentTime;
            this.displayName = StringUtils.fileName(link.path);

            String ago = recentTime > 0 ? UIRecentDataList.ago(recentTime) : "";
            String tooltipStr = link.toString() + (ago.isEmpty() ? "" : " (" + ago + ")");
            this.tooltip(IKey.raw(tooltipStr));

            this.context((menu) ->
            {
                menu.action(Icons.EDIT, UIKeys.TEXTURES_HUB_OPEN_IN_EDITOR, () -> hub.texturePanel.openTexture(link));

                File file = TextureFiles.file(link);
                if (file != null && file.exists())
                {
                    menu.action(Icons.FOLDER, UIKeys.TEXTURES_HUB_SHOW_IN_EXPLORER, () -> UIUtils.openFolder(file.getParentFile()));
                }

                if (TextureFiles.canModify(link))
                {
                    menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> hub.promptRename(link));
                    menu.action(Icons.DUPE, UIKeys.TEXTURES_HUB_DUPLICATE, () -> hub.duplicate(link));
                }

                if (TextureFiles.canDelete(link))
                {
                    menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, () -> hub.promptDelete(link));
                }
            });
        }

        @Override
        protected UITextureCard get()
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

            Texture texture = BBSModClient.getTextures().getTexture(this.link);

            if (texture != null && texture != BBSModClient.getTextures().getError())
            {
                int tw = Math.max(1, texture.width);
                int th = Math.max(1, texture.height);
                float scale = Math.min((float) (pw - 4) / tw, (float) (ph - 4) / th);
                int fw = Math.max(1, Math.round(tw * scale));
                int fh = Math.max(1, Math.round(th * scale));
                int fx = px + (pw - fw) / 2;
                int fy = py + (ph - fh) / 2;

                context.batcher.iconArea(Icons.CHECKBOARD, fx, fy, fw, fh);
                context.batcher.fullTexturedBox(texture, fx, fy, fw, fh);
            }
            else
            {
                context.batcher.icon(Icons.IMAGE, Colors.GRAY, px + pw / 2, py + ph / 2, 0.5F, 0.5F);
            }

            /* Labels below preview box */
            FontRenderer font = context.batcher.getFont();
            String name = font.limitToWidth(this.displayName, pw);
            context.batcher.text(name, a.x + pad + 2, py + ph + 4, Colors.WHITE, false);

            String meta;
            if (texture != null && texture != BBSModClient.getTextures().getError())
            {
                meta = texture.width + "x" + texture.height;
            }
            else
            {
                meta = this.link.source.isEmpty() ? "assets" : this.link.source;
            }

            if (this.recentTime > 0)
            {
                String ago = UIRecentDataList.ago(this.recentTime);
                if (!ago.isEmpty())
                {
                    meta += " • " + ago;
                }
            }

            String metaTrimmed = font.limitToWidth(meta, pw);
            context.batcher.text(metaTrimmed, a.x + pad + 2, py + ph + 4 + font.getHeight() + 1, Colors.setA(Colors.WHITE, 0.45F), false);
        }
    }
}
