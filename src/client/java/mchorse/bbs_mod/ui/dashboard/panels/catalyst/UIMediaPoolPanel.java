package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.catalyst.CatalystMediaAsset;
import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.video.VideoPlayer;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * UIMediaPoolPanel - Professional Media Pool (Asset Manager) for Catalyst Studio.
 * Provides file import, visual asset browser, metadata inspection, and drag-to-timeline capability.
 */
public class UIMediaPoolPanel extends UIElement
{
    public enum ViewMode
    {
        LIST, GRID
    }

    public static ViewMode currentViewMode = ViewMode.LIST;

    private final UICatalystPanel panel;
    private final Supplier<CatalystProject> projectSupplier;
    private final Consumer<CatalystMediaAsset> onAssetDoubleClicked;
    private final Consumer<CatalystMediaAsset> onAssetDragStarted;

    public UIElement header;
    public UIIcon importBtn;
    public UIIcon cleanBtn;
    public UIIcon openFolderBtn;
    public UITextbox search;
    public UIIcon listViewBtn;
    public UIIcon gridViewBtn;
    public UIScrollView scroll;

    public CatalystMediaAsset selectedAsset = null;
    private CatalystMediaAsset pressedAsset = null;
    private int pressX = 0;
    private int pressY = 0;
    private long lastClickTime = 0L;
    private CatalystMediaAsset lastClickedAsset = null;

    private final Map<String, Texture> thumbnailTextures = new HashMap<>();

    public UIMediaPoolPanel(UICatalystPanel panel,
                            Supplier<CatalystProject> projectSupplier,
                            Consumer<CatalystMediaAsset> onAssetDoubleClicked,
                            Consumer<CatalystMediaAsset> onAssetDragStarted)
    {
        this.panel = panel;
        this.projectSupplier = projectSupplier;
        this.onAssetDoubleClicked = onAssetDoubleClicked;
        this.onAssetDragStarted = onAssetDragStarted;

        /* Panel background */
        this.add(new UIRenderable((context) ->
        {
            Area a = this.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.sunkenSurface());
            context.batcher.box(a.ex() - 1, a.y, a.ex(), a.ey(), BBSSettings.dividerColor());
        }));

        /* ── Header ── */
        this.header = new UIElement();
        this.header.relative(this).w(1F).h(24);
        this.header.add(new UIRenderable((context) ->
        {
            Area a = this.header.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.chromeSurface());
            context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), BBSSettings.dividerColor());

            CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
            int count = project != null ? project.mediaPool.size() : 0;
            String title = "Media Pool (" + count + ")";
            FontRenderer font = context.batcher.getFont();
            context.batcher.textCard(title, a.x + 6, a.y + (a.h - font.getHeight()) / 2, Colors.WHITE, 0, 0, false);
        }));

        this.openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> this.openMediaFolder());
        this.openFolderBtn.tooltip(IKey.raw("Open Media Folder"));
        this.openFolderBtn.relative(this.header).x(1F, -22).y(2).w(20).h(20);

        this.cleanBtn = new UIIcon(Icons.TRASH, (b) -> this.cleanUnused());
        this.cleanBtn.tooltip(IKey.raw("Clean Unused Media"));
        this.cleanBtn.relative(this.header).x(1F, -44).y(2).w(20).h(20);

        this.importBtn = new UIIcon(Icons.DOWNLOAD, (b) -> this.importMediaDialog());
        this.importBtn.tooltip(IKey.raw("Import Media (.mp4, .mov, .wav, .mp3, .png...)"));
        this.importBtn.relative(this.header).x(1F, -66).y(2).w(20).h(20);

        this.header.add(this.importBtn, this.cleanBtn, this.openFolderBtn);

        /* ── Search bar & view toggle buttons ── */
        this.search = new UITextbox(120, (text) -> this.rebuildList());
        this.search.placeholder(IKey.raw("Filter media..."));
        this.search.relative(this).y(26).x(4).w(1F, -46).h(18);

        this.listViewBtn = new UIIcon(Icons.LIST, (b) -> this.setViewMode(ViewMode.LIST));
        this.listViewBtn.tooltip(IKey.raw("List View"));
        this.listViewBtn.highlight(() -> currentViewMode == ViewMode.LIST, Direction.BOTTOM);
        this.listViewBtn.relative(this).y(26).x(1F, -40).w(18).h(18);

        this.gridViewBtn = new UIIcon(Icons.GALLERY, (b) -> this.setViewMode(ViewMode.GRID));
        this.gridViewBtn.tooltip(IKey.raw("Grid View"));
        this.gridViewBtn.highlight(() -> currentViewMode == ViewMode.GRID, Direction.BOTTOM);
        this.gridViewBtn.relative(this).y(26).x(1F, -20).w(18).h(18);

        /* ── Scroll view for media items ── */
        this.scroll = new UIScrollView(ScrollDirection.VERTICAL)
        {
            private boolean handleAssetClick(UIContext context, CatalystMediaAsset asset)
            {
                if (context.mouseButton == 0)
                {
                    selectedAsset = asset;
                    pressedAsset = asset;
                    pressX = context.mouseX;
                    pressY = context.mouseY;

                    long now = System.currentTimeMillis();
                    if (lastClickedAsset == asset && now - lastClickTime < 400)
                    {
                        if (onAssetDoubleClicked != null)
                        {
                            onAssetDoubleClicked.accept(asset);
                        }
                    }
                    lastClickedAsset = asset;
                    lastClickTime = now;
                    return true;
                }
                else if (context.mouseButton == 1)
                {
                    selectedAsset = asset;
                    showAssetContextMenu(context, asset);
                    return true;
                }
                return false;
            }

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context))
                {
                    CatalystProject project = projectSupplier != null ? projectSupplier.get() : null;
                    if (project != null)
                    {
                        List<CatalystMediaAsset> list = getFilteredAssets(project);
                        int scrollY = (int) this.scroll.getScroll();

                        if (currentViewMode == ViewMode.LIST)
                        {
                            int itemY = this.area.y - scrollY;

                            for (CatalystMediaAsset asset : list)
                            {
                                if (context.mouseY >= itemY && context.mouseY < itemY + 36)
                                {
                                    return this.handleAssetClick(context, asset);
                                }
                                itemY += 38;
                            }
                        }
                        else
                        {
                            int availableW = this.area.w - 12;
                            int spacing = 4;
                            int cols = Math.max(1, availableW / 70);
                            int cardW = cols > 1 ? (availableW - (cols - 1) * spacing) / cols : availableW;
                            int cardH = cardW + 8;

                            for (int i = 0; i < list.size(); i++)
                            {
                                CatalystMediaAsset asset = list.get(i);
                                int col = i % cols;
                                int row = i / cols;
                                int cardX = this.area.x + 4 + col * (cardW + spacing);
                                int cardY = this.area.y - scrollY + 4 + row * (cardH + spacing);

                                if (context.mouseX >= cardX && context.mouseX < cardX + cardW &&
                                    context.mouseY >= cardY && context.mouseY < cardY + cardH)
                                {
                                    return this.handleAssetClick(context, asset);
                                }
                            }
                        }
                    }
                }
                return super.subMouseClicked(context);
            }

            @Override
            protected boolean subMouseReleased(UIContext context)
            {
                pressedAsset = null;
                return super.subMouseReleased(context);
            }

            @Override
            public void render(UIContext context)
            {
                /* Check if mouse moved enough to start drag */
                if (pressedAsset != null && Window.isMouseButtonPressed(0))
                {
                    if (Math.abs(context.mouseX - pressX) > 4 || Math.abs(context.mouseY - pressY) > 4)
                    {
                        if (onAssetDragStarted != null)
                        {
                            onAssetDragStarted.accept(pressedAsset);
                        }
                        pressedAsset = null;
                    }
                }

                super.render(context);
            }
        };

        this.scroll.relative(this).y(46).w(1F).h(1F, -46);
        this.scroll.scroll.scrollSpeed = 24;

        this.scroll.add(new UIRenderable((context) -> this.renderMediaList(context)));

        this.add(this.header, this.search, this.listViewBtn, this.gridViewBtn, this.scroll);
    }

    public void setViewMode(ViewMode mode)
    {
        currentViewMode = mode;
        if (this.scroll != null)
        {
            this.scroll.scroll.setScroll(0);
        }
        this.rebuildList();
    }

    private List<CatalystMediaAsset> getFilteredAssets(CatalystProject project)
    {
        List<CatalystMediaAsset> list = new ArrayList<>();
        if (project == null)
        {
            return list;
        }

        String filter = this.search != null ? this.search.getText().trim().toLowerCase(Locale.ROOT) : "";

        for (CatalystMediaAsset asset : project.mediaPool)
        {
            if (filter.isEmpty() || asset.name.toLowerCase(Locale.ROOT).contains(filter))
            {
                list.add(asset);
            }
        }

        return list;
    }

    public void rebuildList()
    {
        /* Dynamic scroll bounds update handled in renderMediaList */
    }

    public Texture getOrLoadAssetTexture(CatalystMediaAsset asset)
    {
        if (asset == null || asset.type != CatalystMediaAsset.MediaType.IMAGE || asset.path == null)
        {
            return null;
        }

        if (this.thumbnailTextures.containsKey(asset.path))
        {
            return this.thumbnailTextures.get(asset.path);
        }

        File imgFile = new File(asset.path);
        if (imgFile.exists() && imgFile.isFile())
        {
            try (InputStream stream = new FileInputStream(imgFile))
            {
                Pixels pixels = Pixels.fromPNGStream(stream);
                if (pixels != null)
                {
                    Texture tex = Texture.textureFromPixels(pixels, GL11.GL_LINEAR);
                    pixels.delete();
                    this.thumbnailTextures.put(asset.path, tex);
                    return tex;
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        this.thumbnailTextures.put(asset.path, null);
        return null;
    }

    private void renderMediaList(UIContext context)
    {
        CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
        if (project == null)
        {
            return;
        }

        List<CatalystMediaAsset> list = this.getFilteredAssets(project);
        Area a = this.scroll.area;
        int scrollY = (int) this.scroll.scroll.getScroll();
        FontRenderer font = context.batcher.getFont();

        if (currentViewMode == ViewMode.LIST)
        {
            int totalH = list.size() * 38;
            this.scroll.scroll.setSize(totalH);
            this.scroll.scroll.clamp();

            int itemY = a.y - scrollY;

            for (CatalystMediaAsset asset : list)
            {
                if (itemY + 36 >= a.y && itemY < a.ey())
                {
                    boolean isSelected = this.selectedAsset == asset;
                    boolean isHovered = context.mouseX >= a.x + 2 && context.mouseX < a.ex() - 2
                        && context.mouseY >= itemY && context.mouseY < itemY + 36;

                    int bg = isSelected ? 0xDD2A3A55 : (isHovered ? 0xAA222834 : 0x66181C24);
                    context.batcher.box(a.x + 2, itemY, a.ex() - 2, itemY + 36, bg);

                    if (isSelected)
                    {
                        context.batcher.outline(a.x + 2, itemY, a.ex() - 2, itemY + 36, 0xFF55AAFF, 1);
                    }
                    else
                    {
                        context.batcher.box(a.x + 2, itemY + 35, a.ex() - 2, itemY + 36, 0x33FFFFFF);
                    }

                    /* Media Icon */
                    Icon icon = Icons.IMAGE;
                    int iconColor = 0xFFEEAA44; // Image orange
                    String extBadge = "IMG";

                    if (asset.type == CatalystMediaAsset.MediaType.VIDEO)
                    {
                        icon = Icons.FILM;
                        iconColor = 0xFF5599FF; // Video blue
                        extBadge = "VID";
                    }
                    else if (asset.type == CatalystMediaAsset.MediaType.AUDIO)
                    {
                        icon = Icons.SOUND;
                        iconColor = 0xFF44DD88; // Audio green
                        extBadge = "AUD";
                    }

                    context.batcher.icon(icon, iconColor, a.x + 6, itemY + 10);

                    /* Title */
                    String displayName = asset.name;
                    int maxTextW = a.w - 75;
                    if (font.getWidth(displayName) > maxTextW)
                    {
                        while (displayName.length() > 3 && font.getWidth(displayName + "...") > maxTextW)
                        {
                            displayName = displayName.substring(0, displayName.length() - 1);
                        }
                        displayName += "...";
                    }
                    context.batcher.text(displayName, a.x + 26, itemY + 4, Colors.WHITE, false);

                    /* Subtitle: Size & Duration */
                    String subtitle = asset.formatSize();
                    String dur = asset.formatDuration(project.fps);
                    if (!dur.isEmpty() && asset.type != CatalystMediaAsset.MediaType.IMAGE)
                    {
                        subtitle += " • " + dur;
                    }
                    context.batcher.text(subtitle, a.x + 26, itemY + 19, 0xFF8899AA, false);

                    /* Extension Badge */
                    int badgeW = font.getWidth(extBadge) + 6;
                    int badgeX = a.ex() - badgeW - 6;
                    int badgeY = itemY + 11;
                    context.batcher.box(badgeX, badgeY, badgeX + badgeW, badgeY + 14, 0x44000000 | (iconColor & 0xFFFFFF));
                    context.batcher.text(extBadge, badgeX + 3, badgeY + 3, iconColor, false);
                }

                itemY += 38;
            }
        }
        else
        {
            /* GRID / THUMBNAIL MODE */
            int availableW = a.w - 12;
            int spacing = 4;
            int cols = Math.max(1, availableW / 70);
            int cardW = cols > 1 ? (availableW - (cols - 1) * spacing) / cols : availableW;
            int cardH = cardW + 8;
            int rows = (list.size() + cols - 1) / cols;
            int totalH = rows * (cardH + spacing) + 8;
            this.scroll.scroll.setSize(totalH);
            this.scroll.scroll.clamp();

            for (int i = 0; i < list.size(); i++)
            {
                CatalystMediaAsset asset = list.get(i);
                int col = i % cols;
                int row = i / cols;
                int cardX = a.x + 4 + col * (cardW + spacing);
                int cardY = a.y - scrollY + 4 + row * (cardH + spacing);

                if (cardY + cardH >= a.y && cardY < a.ey())
                {
                    boolean isSelected = this.selectedAsset == asset;
                    boolean isHovered = context.mouseX >= cardX && context.mouseX < cardX + cardW
                        && context.mouseY >= cardY && context.mouseY < cardY + cardH;

                    int bg = isSelected ? 0xDD2A3A55 : (isHovered ? 0xAA222834 : 0x66181C24);
                    context.batcher.box(cardX, cardY, cardX + cardW, cardY + cardH, bg);

                    if (isSelected)
                    {
                        context.batcher.outline(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF55AAFF, 1);
                    }
                    else if (isHovered)
                    {
                        context.batcher.outline(cardX, cardY, cardX + cardW, cardY + cardH, 0x44FFFFFF, 1);
                    }
                    else
                    {
                        context.batcher.box(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0x22FFFFFF);
                    }

                    int thumbH = cardH - 18;
                    int thumbX = cardX + 2;
                    int thumbY = cardY + 2;
                    int thumbW = cardW - 4;

                    context.batcher.box(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, 0x55000000);

                    Icon icon = Icons.IMAGE;
                    int iconColor = 0xFFEEAA44; // Image orange
                    String extBadge = "IMG";

                    if (asset.type == CatalystMediaAsset.MediaType.VIDEO)
                    {
                        icon = Icons.FILM;
                        iconColor = 0xFF5599FF; // Video blue
                        extBadge = "VID";
                    }
                    else if (asset.type == CatalystMediaAsset.MediaType.AUDIO)
                    {
                        icon = Icons.SOUND;
                        iconColor = 0xFF44DD88; // Audio green
                        extBadge = "AUD";
                    }

                    boolean renderedThumb = false;
                    if (asset.type == CatalystMediaAsset.MediaType.IMAGE)
                    {
                        Texture tex = this.getOrLoadAssetTexture(asset);
                        if (tex != null && tex.isValid())
                        {
                            float imgAspect = (float) tex.width / Math.max(1, tex.height);
                            float boxAspect = (float) thumbW / (float) thumbH;
                            float drawW, drawH;
                            if (imgAspect > boxAspect)
                            {
                                drawW = thumbW;
                                drawH = drawW / imgAspect;
                            }
                            else
                            {
                                drawH = thumbH;
                                drawW = drawH * imgAspect;
                            }
                            float drawX = thumbX + (thumbW - drawW) / 2.0F;
                            float drawY = thumbY + (thumbH - drawH) / 2.0F;
                            context.batcher.texturedBox(tex, Colors.WHITE, drawX, drawY, drawW, drawH, 0, 0, tex.width, tex.height);
                            renderedThumb = true;
                        }
                    }

                    if (!renderedThumb)
                    {
                        int iconX = thumbX + (thumbW - 16) / 2;
                        int iconY = thumbY + (thumbH - 16) / 2;
                        context.batcher.icon(icon, iconColor, iconX, iconY);
                    }

                    /* Small Type Badge in top-left corner of thumbnail */
                    int badgeW = font.getWidth(extBadge) + 4;
                    context.batcher.box(thumbX + 2, thumbY + 2, thumbX + 2 + badgeW, thumbY + 12, 0x88000000 | (iconColor & 0x00FFFFFF));
                    context.batcher.text(extBadge, thumbX + 4, thumbY + 3, iconColor, false);

                    /* File Name under thumbnail */
                    String displayName = asset.name;
                    int maxNameW = cardW - 4;
                    if (font.getWidth(displayName) > maxNameW)
                    {
                        while (displayName.length() > 3 && font.getWidth(displayName + "...") > maxNameW)
                        {
                            displayName = displayName.substring(0, displayName.length() - 1);
                        }
                        displayName += "...";
                    }
                    int nameX = cardX + (cardW - font.getWidth(displayName)) / 2;
                    context.batcher.text(displayName, nameX, cardY + thumbH + 4, Colors.WHITE, false);
                }
            }
        }

        if (list.isEmpty())
        {
            String emptyMsg = project.mediaPool.isEmpty() ? "No media assets. Click Import or drag files here." : "No matching assets.";
            context.batcher.textCard(emptyMsg, a.x + 10, a.y + 20, 0xFF888899, 0, 0, false);
        }
    }

    private void showAssetContextMenu(UIContext context, CatalystMediaAsset asset)
    {
        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.ADD, IKey.raw("Insert to Timeline (At Playhead)"), () ->
            {
                if (this.onAssetDoubleClicked != null)
                {
                    this.onAssetDoubleClicked.accept(asset);
                }
            });

            menu.action(Icons.REFRESH, IKey.raw("Reload Asset"), () ->
            {
                CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
                if (project != null)
                {
                    Texture oldTex = this.thumbnailTextures.remove(asset.path);
                    if (oldTex != null && oldTex.isValid())
                    {
                        oldTex.delete();
                    }
                    this.probeAsset(asset, project.fps);
                    if (this.panel != null)
                    {
                        this.panel.saveAndRefresh();
                    }
                }
            });

            menu.action(Icons.FOLDER, IKey.raw("Open Containing Folder"), () ->
            {
                File file = new File(asset.path);
                if (file.exists())
                {
                    UIUtils.openFolder(file.getParentFile());
                }
            });

            menu.action(Icons.TRASH, IKey.raw("Delete from Pool"), () ->
            {
                CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
                if (project != null)
                {
                    Texture tex = this.thumbnailTextures.remove(asset.path);
                    if (tex != null && tex.isValid())
                    {
                        tex.delete();
                    }
                    project.removeAsset(asset);
                    if (this.selectedAsset == asset)
                    {
                        this.selectedAsset = null;
                    }
                    if (this.panel != null)
                    {
                        this.panel.saveAndRefresh();
                    }
                    this.rebuildList();
                }
            });
        });
    }

    public void importMediaDialog()
    {
        CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
        if (project == null)
        {
            return;
        }

        File folder = BBSMod.getAssetsPath("catalyst_media");
        if (!folder.exists())
        {
            folder.mkdirs();
        }

        String[] filters = new String[]{"*.mp4", "*.mov", "*.wav", "*.mp3", "*.ogg", "*.png", "*.jpg", "*.jpeg"};
        UIFileDialogs.pickFile(IKey.raw("Import Media"), folder, filters, IKey.raw("Supported Media Files"), (file) ->
        {
            if (file != null && file.exists())
            {
                this.importFile(file);
            }
        });
    }

    public void importFile(File file)
    {
        if (file == null || !file.exists())
        {
            return;
        }

        if (!CatalystMediaAsset.MediaType.isSupported(file.getName()))
        {
            return;
        }

        CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
        if (project == null)
        {
            return;
        }

        File destFolder = BBSMod.getAssetsPath("catalyst_media");
        if (!destFolder.exists())
        {
            destFolder.mkdirs();
        }

        File targetFile = file;

        if (!file.getAbsolutePath().startsWith(destFolder.getAbsolutePath()))
        {
            try
            {
                File copyDest = new File(destFolder, file.getName());
                if (!copyDest.exists() || copyDest.length() != file.length())
                {
                    Files.copy(file.toPath(), copyDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                targetFile = copyDest;
            }
            catch (Exception ignored)
            {
                targetFile = file;
            }
        }

        CatalystMediaAsset asset = project.addAsset(targetFile);

        if (asset != null)
        {
            this.probeAsset(asset, project.fps);
            this.selectedAsset = asset;
        }

        if (this.panel != null)
        {
            this.panel.saveAndRefresh();
        }

        this.rebuildList();
    }

    public void probeAsset(CatalystMediaAsset asset, int fps)
    {
        if (asset.type == CatalystMediaAsset.MediaType.AUDIO)
        {
            try
            {
                File f = new File(asset.path);
                Wave wave = null;
                if (f.exists() && f.isFile())
                {
                    wave = AudioReader.readWave(f);
                }
                if (wave == null)
                {
                    wave = AudioReader.read(BBSMod.getProvider(), Link.create(asset.path));
                }
                if (wave != null && wave.getDuration() > 0)
                {
                    asset.durationFrames = Math.max(1, (int) Math.round(wave.getDuration() * (fps > 0 ? fps : 60)));
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        else if (asset.type == CatalystMediaAsset.MediaType.VIDEO)
        {
            try
            {
                File vf = new File(asset.path);
                if (vf.exists())
                {
                    VideoPlayer vp = new VideoPlayer(vf);
                    vp.ensureProbed();
                    if (vp.getDuration() > 0)
                    {
                        asset.durationFrames = Math.max(1, (int) Math.round(vp.getDuration() * (fps > 0 ? fps : 60)));
                    }
                    if (vp.getWidth() > 0 && vp.getHeight() > 0)
                    {
                        asset.width = vp.getWidth();
                        asset.height = vp.getHeight();
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        else if (asset.type == CatalystMediaAsset.MediaType.IMAGE)
        {
            try
            {
                File imgFile = new File(asset.path);
                if (imgFile.exists() && imgFile.isFile())
                {
                    try (java.io.InputStream stream = new java.io.FileInputStream(imgFile))
                    {
                        mchorse.bbs_mod.utils.resources.Pixels px = mchorse.bbs_mod.utils.resources.Pixels.fromPNGStream(stream);
                        if (px != null)
                        {
                            asset.width = px.width;
                            asset.height = px.height;
                            px.delete();
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    public void cleanUnused()
    {
        CatalystProject project = this.projectSupplier != null ? this.projectSupplier.get() : null;
        if (project != null)
        {
            int removed = project.cleanUnusedMedia();
            if (this.selectedAsset != null && project.getAssetByPath(this.selectedAsset.path) == null)
            {
                this.selectedAsset = null;
            }
            this.thumbnailTextures.entrySet().removeIf(entry ->
            {
                if (project.getAssetByPath(entry.getKey()) == null)
                {
                    if (entry.getValue() != null && entry.getValue().isValid())
                    {
                        entry.getValue().delete();
                    }
                    return true;
                }
                return false;
            });
            if (this.panel != null)
            {
                this.panel.saveAndRefresh();
            }
            this.rebuildList();
        }
    }

    public void openMediaFolder()
    {
        File folder = BBSMod.getAssetsPath("catalyst_media");
        if (!folder.exists())
        {
            folder.mkdirs();
        }
        UIUtils.openFolder(folder);
    }
}
