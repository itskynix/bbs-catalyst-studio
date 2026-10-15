package mchorse.bbs_mod.ui.dashboard.panels.motion;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * mMotion Hub — Motion Graphics Project & Scene Browser Screen.
 *
 * Provides an atmospheric landing screen with project search, new scene creation,
 * quick access to recent projects, and seamless transitions into the mMotion Studio workspace.
 */
public class UIMMotionHubScreen extends UILandingScreen
{
    private static final Map<String, Texture> THUMBNAILS = new HashMap<>();
    private static final Set<String> FAILED_THUMBNAILS = new HashSet<>();

    public final UIMMotionPanel panel;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIElement card;
    private final UIElement searchContainer;
    private final UITextbox searchBox;
    private final UIElement projectsPanel;
    private final UILabel recentTitle;
    private final UIScrollView cardsScroll;
    private final GridResizer cardsGrid;

    private final List<UIMMotionPanel.MotionScene> scenes = new ArrayList<>();
    private String searchQuery = "";

    public UIMMotionHubScreen(UIMMotionPanel panel)
    {
        super(panel);

        this.panel = panel;
        this.removeAll();

        /* Main centered card container */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).anchor(0.5F);

        /* 1. Header: mMotion Studio Brand */
        UIElement header = new UIElement();
        header.relative(this.card).x(0).y(8).w(1F).h(34);
        header.add(new UIRenderable((context) -> this.renderHeader(context, header.area)));

        /* 2. Search Container with embedded + button */
        this.searchContainer = new UIElement();
        this.searchContainer.relative(this.card).x(0.5F).y(46).w(340).h(24).anchor(0.5F, 0F);

        this.searchBox = new UITextbox(this::onSearchChanged).placeholder(UIKeys.MMOTION_HUB_SEARCH);
        this.searchBox.background(false);
        this.searchBox.relative(this.searchContainer).x(24).y(0).w(1F, -48).h(1F);

        UIIcon addBtn = new UIIcon(Icons.ADD, (b) -> this.openNewSceneDialog());
        addBtn.tooltip(UIKeys.MMOTION_HUB_ADD_SCENE);
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

        /* 3. Subtitle / Badge */
        UILabel badge = UI.label(UIKeys.MMOTION_HUB_BADGE).color(Colors.setA(Colors.WHITE, 0.45F));
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

        this.recentTitle = UI.label(UIKeys.MMOTION_HUB_RECENT_PROJECTS).color(Colors.setA(Colors.WHITE, 0.65F));
        this.recentTitle.labelAnchor(0, 0.5F);
        this.recentTitle.relative(this.projectsPanel).xy(10, 5).w(1F, -60).h(16);

        UIIcon openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(getScenesFolder()));
        openFolderBtn.tooltip(UIKeys.MMOTION_HUB_OPEN_FOLDER);
        openFolderBtn.relative(this.projectsPanel).x(1F, -44).y(4).wh(18, 18);

        UIIcon refreshBtn = new UIIcon(Icons.REFRESH, (b) -> this.refresh());
        refreshBtn.tooltip(UIKeys.MMOTION_HUB_REFRESH);
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

        this.initDefaultScenes();
        this.refresh();
    }

    public static File getScenesFolder()
    {
        File folder = BBSMod.getAssetsPath("motion_scenes");
        if (!folder.exists())
        {
            folder.mkdirs();
        }
        return folder;
    }

    private void initDefaultScenes()
    {
        this.scenes.clear();
        this.scenes.addAll(MotionSceneIO.loadAll());
    }

    public List<UIMMotionPanel.MotionScene> getScenes()
    {
        return this.scenes;
    }

    /** Scenes are looked up by their unique id only: names may repeat. */
    public UIMMotionPanel.MotionScene getScene(String id)
    {
        if (id == null)
        {
            return null;
        }

        for (UIMMotionPanel.MotionScene s : this.scenes)
        {
            if (id.equals(s.id))
            {
                return s;
            }
        }

        return null;
    }

    @Override
    public void setVisible(boolean visible)
    {
        boolean wasVisible = this.isVisible();
        super.setVisible(visible);

        if (visible && !wasVisible)
        {
            this.refresh();
            this.panel.requestNames();
        }
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        this.refresh();
    }

    @Override
    public void render(UIContext context)
    {
        /* 1. Guaranteed solid background fill so it's NEVER pitch black / unrendered */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF10141D);

        /* 2. Atmospheric backdrop with lights & sparks */
        if (this.area.w > 0 && this.area.h > 0)
        {
            this.backdrop.render(context, this.area);
        }

        /* 3. Render Hub UI components */
        super.render(context);
    }

    private void onSearchChanged(String query)
    {
        this.searchQuery = query == null ? "" : query.trim();
        this.refresh();
    }

    public void openNewSceneDialog()
    {
        UIOverlay.addOverlay(this.getContext(), new UIMMotionNewProjectDialog(this.panel), 360, 265);
    }

    public void duplicateScene(UIMMotionPanel.MotionScene scene)
    {
        UIMMotionPanel.MotionScene copy = scene.copy();
        MotionSceneIO.save(copy, copy.layers);
        invalidateThumbnail(copy.id);
        int idx = this.scenes.indexOf(scene);
        if (idx >= 0)
        {
            this.scenes.add(idx + 1, copy);
        }
        else
        {
            this.scenes.add(copy);
        }
        this.refresh();
        UIUtils.playClick();
    }

    public void deleteScene(UIMMotionPanel.MotionScene scene)
    {
        this.scenes.remove(scene);
        MotionSceneIO.delete(scene);
        invalidateThumbnail(scene.id);

        /* Close the tabs of this deleted scene (by id: another scene may share its name) */
        for (int i = this.panel.tabs.size() - 1; i >= 0; i--)
        {
            if (scene.id.equals(this.panel.tabs.get(i).dataId))
            {
                this.panel.tabs.closeTab(i);
            }
        }

        this.refresh();
        UIUtils.playClick();
    }

    public void refresh()
    {
        /* Missing thumbnails stay remembered: only invalidateThumbnail() sends a scene back to the disk */
        this.cardsScroll.removeAll();

        if (!this.searchQuery.isEmpty())
        {
            this.recentTitle.label = UIKeys.HUB_SEARCHING.format(this.searchQuery);
        }
        else
        {
            this.recentTitle.label = UIKeys.CATALYST_HUB_ALL;
        }

        String q = this.searchQuery.toLowerCase(Locale.ROOT);

        for (UIMMotionPanel.MotionScene scene : this.scenes)
        {
            if (q.isEmpty() || scene.name.toLowerCase(Locale.ROOT).contains(q))
            {
                this.cardsScroll.add(new UIMMotionCard(this, scene));
            }
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
        if (this.area.w <= 0 || this.area.h <= 0)
        {
            super.resize();
            return;
        }

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
                if (first instanceof UIMMotionCard card)
                {
                    UIUtils.playClick();
                    this.panel.openScene(card.scene);
                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

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
            ? UIKeys.CATALYST_HUB_NO_SEARCH_RESULTS.format(this.searchQuery).get()
            : UIKeys.CATALYST_HUB_EMPTY_RECENT.get();

        int iconY = a.my() - 16;
        context.batcher.icon(searching ? Icons.SEARCH : Icons.CURVES, Colors.setA(Colors.WHITE, 0.35F), a.mx(), iconY, 0.5F, 0.5F);

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
     * Forget what is cached for a scene's thumbnail (found or missing), so the next lookup reads the
     * disk again. Called when a scene is deleted, copied or rendered.
     */
    public static void invalidateThumbnail(String sceneId)
    {
        if (sceneId == null)
        {
            return;
        }

        FAILED_THUMBNAILS.remove(sceneId);
        THUMBNAILS.remove(sceneId);
    }

    public static Texture getThumbnail(String sceneId)
    {
        if (sceneId == null || sceneId.isEmpty() || FAILED_THUMBNAILS.contains(sceneId))
        {
            return null;
        }

        Texture existing = THUMBNAILS.get(sceneId);
        if (existing != null)
        {
            return existing;
        }

        File folder = getScenesFolder();
        File found = new File(folder, "thumbnails/" + sceneId + ".png");
        if (!found.exists() || !found.isFile() || found.length() == 0)
        {
            found = new File(folder, sceneId + ".png");
        }

        if (!found.exists() || !found.isFile() || found.length() == 0)
        {
            FAILED_THUMBNAILS.add(sceneId);
            return null;
        }

        try (InputStream in = new FileInputStream(found))
        {
            Link link = new Link("mmotion_thumb", sceneId.replace('/', '_') + "_" + found.getName());
            Texture tex = BBSModClient.getTextures().createTexture(link);
            tex.bind();
            tex.uploadTexture(Pixels.fromPNGStream(in));
            THUMBNAILS.put(sceneId, tex);
            return tex;
        }
        catch (Exception e)
        {
            FAILED_THUMBNAILS.add(sceneId);
            return null;
        }
    }

    /**
     * Card component representing an mMotion Scene.
     */
    public static class UIMMotionCard extends UIClickable<UIMMotionCard>
    {
        private final UIMMotionHubScreen hub;
        public final UIMMotionPanel.MotionScene scene;

        public UIMMotionCard(UIMMotionHubScreen hub, UIMMotionPanel.MotionScene scene)
        {
            super((c) ->
            {
                UIUtils.playClick();
                hub.panel.openScene(scene);
            });

            this.hub = hub;
            this.scene = scene;

            String layersStr = UIKeys.MMOTION_HUB_LAYERS.format(scene.layers.size()).get();
            String durStr = (scene.duration / Math.max(1, scene.fps)) + "s";
            String ago = scene.lastModified > 0 ? UIRecentDataList.ago(scene.lastModified) : "";
            String tooltipStr = scene.name + "\n" + layersStr + " • " + durStr + (ago.isEmpty() ? "" : " • " + ago);
            this.tooltip(IKey.raw(tooltipStr));

            this.context((menu) ->
            {
                menu.action(Icons.PLAY, UIKeys.MMOTION_HUB_OPEN_SCENE, () ->
                {
                    hub.panel.openScene(scene);
                });
                menu.action(Icons.COPY, UIKeys.MMOTION_HUB_DUPLICATE_SCENE, () ->
                {
                    hub.duplicateScene(scene);
                });
                menu.action(Icons.FOLDER, UIKeys.MMOTION_HUB_OPEN_FOLDER, () ->
                {
                    UIUtils.openFolder(UIMMotionHubScreen.getScenesFolder());
                });
                menu.action(Icons.REMOVE, UIKeys.MMOTION_HUB_DELETE_SCENE, () ->
                {
                    UIOverlay.addOverlay(hub.getContext(), new UIConfirmOverlayPanel(
                        UIKeys.MMOTION_HUB_DELETE_SCENE,
                        UIKeys.MMOTION_HUB_DELETE_CONFIRM.format(scene.name),
                        (confirmed) ->
                        {
                            if (confirmed)
                            {
                                hub.deleteScene(scene);
                            }
                        }
                    ));
                });
            });
        }

        @Override
        protected UIMMotionCard get()
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

            Texture thumb = UIMMotionHubScreen.getThumbnail(this.scene.id);
            if (thumb != null)
            {
                context.batcher.texturedBox(thumb, Colors.WHITE, px, py, pw, ph, 0, 0, thumb.width, thumb.height, thumb.width, thumb.height);
            }
            else
            {
                int iconColor = hovered ? (Colors.A100 | primary) : Colors.setA(Colors.WHITE, 0.55F);
                context.batcher.icon(Icons.CURVES, iconColor, px + pw / 2, py + ph / 2, 0.5F, 0.5F);

                /* Mini layer badges in corner */
                context.batcher.box(px + 3, py + ph - 12, px + 42, py + ph - 2, 0x88000000);
                FontRenderer font = context.batcher.getFont();
                context.batcher.text(this.scene.layers.size() + " L", px + 6, py + ph - 11, 0xFFE0E0E0, false);
            }

            /* Labels */
            FontRenderer font = context.batcher.getFont();
            String trimmed = font.limitToWidth(this.scene.name, a.w - 12);
            int nameX = a.x + pad;
            int nameY = py + ph + 3;
            int nameColor = hovered ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.9F);
            context.batcher.text(trimmed, nameX, nameY, nameColor, false);

            /* Subtitle / Meta row: e.g. "3 Layers • 10s" */
            String meta = UIKeys.MMOTION_HUB_LAYERS.format(this.scene.layers.size()).get() + " • " + (this.scene.duration / Math.max(1, this.scene.fps)) + "s";
            String trimmedMeta = font.limitToWidth(meta, a.w - 12);
            int metaY = nameY + font.getHeight() + 1;
            context.batcher.text(trimmedMeta, nameX, metaY, Colors.setA(Colors.WHITE, 0.5F), false);
        }
    }
}
