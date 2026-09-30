package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

public class UICommunityHubPanel extends UIDashboardPanel
{
    private final List<HubAssetEntry> allAssets = new ArrayList<>();
    private final List<HubAssetEntry> displayedAssets = new ArrayList<>();

    public UIElement topBar;
    public UITextbox search;
    public UICirculate categoryCirculate;
    public UICirculate sortCirculate;
    public UIIcon refreshBtn;
    public UIIcon folderBtn;

    public UIHubAssetList assetList;
    public UIElement detailContainer;
    public UIScrollView detailScroll;

    public UIButton installBtn;
    public UIButton uninstallBtn;
    public UILabel statusLabel;

    private HubAssetEntry selectedEntry;
    private float downloadProgress = -1F;
    private String downloadStatusMessage = "";

    public UICommunityHubPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.topBar = new UIElement();
        this.topBar.relative(this).x(6).y(6).w(1F, -12).h(24);

        this.search = new UITextbox((text) -> this.filterAndSort());
        this.search.textbox.setPlaceholder(UIKeys.HUB_SEARCH_PLACEHOLDER);
        this.search.w(190).h(20);

        this.categoryCirculate = new UICirculate((c) -> this.filterAndSort());

        for (HubCategory cat : HubCategory.values())
        {
            this.categoryCirculate.addLabel(cat.title);
        }

        this.categoryCirculate.w(110).h(20);

        this.sortCirculate = new UICirculate((c) -> this.filterAndSort());

        for (HubSortOrder order : HubSortOrder.values())
        {
            this.sortCirculate.addLabel(order.title);
        }

        this.sortCirculate.w(110).h(20);

        this.refreshBtn = new UIIcon(Icons.REFRESH, (b) -> this.loadCatalog());
        this.refreshBtn.tooltip(UIKeys.HUB_REFRESH);
        this.refreshBtn.wh(20, 20);

        this.folderBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(BBSMod.getAssetsFolder()));
        this.folderBtn.tooltip(UIKeys.HUB_OPEN_FOLDER);
        this.folderBtn.wh(20, 20);

        this.topBar.row(6).height(20);
        this.topBar.add(this.search, this.categoryCirculate, this.sortCirculate, this.refreshBtn, this.folderBtn);

        /* Left asset card list */
        this.assetList = new UIHubAssetList((selected) ->
        {
            this.selectedEntry = this.assetList.getCurrentFirst();
            this.rebuildDetails();
        });
        this.assetList.relative(this).x(6).y(34).w(0.55F, -9).hTo(this.flex, 1F, -58);

        /* Right inspection pane container */
        this.detailContainer = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x5511141A);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x1AFFFFFF);

                super.render(context);
            }
        };
        this.detailContainer.relative(this).x(0.55F, 3).y(34).w(0.45F, -9).hTo(this.flex, 1F, -58);

        this.detailScroll = UI.scrollView(10);
        this.detailScroll.full(this.detailContainer);
        this.detailContainer.add(this.detailScroll);

        /* Bottom status bar */
        this.statusLabel = UI.label(UIKeys.HUB_NO_ASSETS, 16);
        this.statusLabel.relative(this).x(8).y(1F, -20).w(1F, -16).h(16);

        this.add(this.topBar, this.assetList, this.detailContainer, this.statusLabel);

        this.onOpen(this::loadCatalog);
        this.onAppear(this::filterAndSort);
    }

    public void loadCatalog()
    {
        this.statusLabel.label = IKey.raw("Refreshing community hub catalog...");

        HubAssetDownloader.fetchCatalog((assets) ->
        {
            this.allAssets.clear();
            this.allAssets.addAll(assets);
            this.filterAndSort();
        });
    }

    public void filterAndSort()
    {
        String query = this.search.textbox.getText();
        int catIndex = this.categoryCirculate.getValue();
        HubCategory filterCat = (catIndex >= 0 && catIndex < HubCategory.values().length)
            ? HubCategory.values()[catIndex]
            : HubCategory.ALL;

        this.displayedAssets.clear();

        for (HubAssetEntry entry : this.allAssets)
        {
            if (entry.matches(query, filterCat))
            {
                this.displayedAssets.add(entry);
            }
        }

        int sortIndex = this.sortCirculate.getValue();

        if (sortIndex == HubSortOrder.POPULAR.ordinal())
        {
            this.displayedAssets.sort((a, b) -> Long.compare(b.downloads, a.downloads));
        }
        else if (sortIndex == HubSortOrder.TOP_RATED.ordinal())
        {
            this.displayedAssets.sort((a, b) -> Long.compare(b.likes, a.likes));
        }
        else if (sortIndex == HubSortOrder.ALPHABETICAL.ordinal())
        {
            this.displayedAssets.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }

        this.assetList.setList(this.displayedAssets);

        if (this.selectedEntry == null || !this.displayedAssets.contains(this.selectedEntry))
        {
            if (!this.displayedAssets.isEmpty())
            {
                this.assetList.setIndex(0);
                this.selectedEntry = this.displayedAssets.get(0);
            }
            else
            {
                this.assetList.deselect();
                this.selectedEntry = null;
            }
        }
        else
        {
            this.assetList.setCurrent(this.selectedEntry);
        }

        this.rebuildDetails();
        this.updateStatusText();
    }

    private void updateStatusText()
    {
        if (this.downloadProgress >= 0F && this.downloadProgress <= 1F)
        {
            this.statusLabel.label = IKey.raw(this.downloadStatusMessage + " (" + (int) (this.downloadProgress * 100F) + "%)");
        }
        else if (!this.downloadStatusMessage.isEmpty())
        {
            this.statusLabel.label = IKey.raw(this.downloadStatusMessage);
        }
        else
        {
            this.statusLabel.label = IKey.raw("BBS Hub: " + this.displayedAssets.size() + " of " + this.allAssets.size() + " community assets available.");
        }
    }

    private void rebuildDetails()
    {
        this.detailScroll.removeAll();

        if (this.selectedEntry == null)
        {
            this.detailScroll.add(UI.label(UIKeys.HUB_SELECT_ASSET, 20));
            this.detailScroll.resize();

            return;
        }

        HubAssetEntry entry = this.selectedEntry;
        boolean isInstalled = entry.isInstalled();
        boolean hasUpdate = entry.hasUpdate();

        /* Large Title */
        UILabel titleLabel = UI.label(IKey.raw(entry.name), 20, Colors.WHITE);

        /* Subtitle */
        String metaSubtitle = "v" + entry.version + "  *  by " + entry.author + "  *  " + entry.getFormattedSize();
        UILabel subtitleLabel = UI.label(IKey.raw(metaSubtitle), 14, Colors.A75 | Colors.WHITE);

        /* Large Preview Banner (140px) */
        UIElement bannerBlock = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int x = this.area.x;
                int y = this.area.y;
                int w = this.area.w;
                int h = this.area.h;

                context.batcher.box(x, y, x + w, y + h, 0x66080B10);
                context.batcher.outline(x, y, x + w, y + h, 0x22FFFFFF);

                Texture thumb = HubThumbnailManager.getThumbnail(entry);

                if (thumb != null)
                {
                    context.batcher.fullTexturedBox(thumb, x + 2, y + 2, w - 4, h - 4);
                }
                else
                {
                    /* Modern aesthetic category backdrop */
                    int catColor = entry.category.color;

                    context.batcher.box(x + 2, y + 2, x + w - 2, y + h - 2, 0x2A000000 | (catColor & 0xFFFFFF));
                    context.batcher.icon(entry.category.icon, x + (w / 2) - 8, y + (h / 2) - 16);

                    String catTitle = entry.category.title.get().toUpperCase();
                    int catW = context.batcher.getFont().getWidth(catTitle);

                    context.batcher.text(catTitle, x + (w / 2) - (catW / 2), y + (h / 2) + 6, Colors.WHITE);
                }

                /* Category badge overlay on banner */
                int catColor = entry.category.color;
                String catText = entry.category.title.get();
                int catW = context.batcher.getFont().getWidth(catText) + 24;

                context.batcher.box(x + 6, y + 6, x + 6 + catW, y + 22, 0x55000000 | (catColor & 0xFFFFFF));
                context.batcher.box(x + 6, y + 6, x + 9, y + 22, 0xFF000000 | (catColor & 0xFFFFFF));
                context.batcher.icon(entry.category.icon, x + 10, y + 8);
                context.batcher.text(catText, x + 24, y + 9, Colors.WHITE);

                /* Status badge overlay */
                if (hasUpdate)
                {
                    context.batcher.textCard("UPDATE AVAILABLE", x + w - 120, y + 6, Colors.WHITE, 0xEEF57C00, 2);
                }
                else if (isInstalled)
                {
                    context.batcher.textCard("INSTALLED", x + w - 76, y + 6, Colors.WHITE, 0xEE2E7D32, 2);
                }
                else
                {
                    context.batcher.textCard("NOT INSTALLED", x + w - 96, y + 6, Colors.WHITE, 0xEE757575, 2);
                }

                super.render(context);
            }
        };
        bannerBlock.h(140);

        /* Statistics row */
        String statsStr = "Downloads: " + entry.downloads + "   |   Likes: " + entry.likes + "   |   Files: " + entry.files.size();
        UILabel statsLabel = UI.label(IKey.raw(statsStr), 14, Colors.A75 | Colors.WHITE);

        /* Action buttons row */
        IKey installText = hasUpdate ? IKey.raw("Update to v" + entry.version) : (isInstalled ? UIKeys.HUB_REINSTALL : UIKeys.HUB_INSTALL);

        this.installBtn = new UIButton(installText, (b) -> this.startInstall(entry));
        this.installBtn.h(22);

        this.uninstallBtn = new UIButton(UIKeys.HUB_UNINSTALL, (b) -> this.startUninstall(entry));
        this.uninstallBtn.h(22);
        this.uninstallBtn.setEnabled(isInstalled);

        UIElement buttonsRow = UI.row(8, this.installBtn, this.uninstallBtn);

        /* Description block */
        List<String> wrappedLines = Batcher2D.getDefaultTextRenderer().wrap(entry.description, Math.max(120, this.detailContainer.area.w - 24));
        int descH = Math.max(30, (wrappedLines.size() + 1) * Batcher2D.getDefaultTextRenderer().getLineHeight() + 8);

        UIElement descBlock = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                List<String> lines = context.batcher.getFont().wrap(entry.description, this.area.w - 8);
                int y = this.area.y + 4;

                for (String line : lines)
                {
                    context.batcher.text(line, this.area.x + 4, y, Colors.WHITE);
                    y += context.batcher.getFont().getLineHeight();
                }

                super.render(context);
            }
        };
        descBlock.h(descH);

        /* Tags row */
        UIElement tagsContainer = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int curX = this.area.x;
                int curY = this.area.y;

                for (String tag : entry.tags)
                {
                    String tagLabel = "#" + tag;
                    int tagW = context.batcher.getFont().getWidth(tagLabel) + 8;

                    if (curX + tagW > this.area.ex())
                    {
                        curX = this.area.x;
                        curY += 16;
                    }

                    context.batcher.textCard(tagLabel, curX, curY, Colors.WHITE, 0x55333333, 2);
                    curX += tagW + 4;
                }

                super.render(context);
            }
        };
        tagsContainer.h(20);

        /* Included Files Breakdown */
        UILabel filesTitle = UI.label(UIKeys.HUB_FILES_INCLUDED, 16, Colors.WHITE);
        UIElement filesBlock = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int y = this.area.y;

                for (String relFile : entry.files)
                {
                    context.batcher.text("- " + relFile, this.area.x + 6, y, Colors.A75 | Colors.WHITE);
                    y += 12;
                }

                super.render(context);
            }
        };
        filesBlock.h(Math.max(16, entry.files.size() * 12 + 4));

        this.detailScroll.add(
            titleLabel,
            subtitleLabel,
            bannerBlock,
            statsLabel,
            buttonsRow,
            descBlock,
            UI.label(UIKeys.HUB_TAGS, 14, Colors.WHITE),
            tagsContainer,
            filesTitle,
            filesBlock
        );

        this.detailScroll.resize();
    }

    private void startInstall(HubAssetEntry entry)
    {
        if (entry == null)
        {
            return;
        }

        this.downloadProgress = 0.05F;
        this.downloadStatusMessage = "Connecting to download server...";
        this.updateStatusText();

        if (this.installBtn != null)
        {
            this.installBtn.setEnabled(false);
        }

        HubAssetDownloader.downloadAndInstall(entry, (status) ->
        {
            this.downloadProgress = status.done ? -1F : status.progress;
            this.downloadStatusMessage = status.message;

            this.updateStatusText();

            if (status.done)
            {
                this.filterAndSort();
            }
        });
    }

    private void startUninstall(HubAssetEntry entry)
    {
        if (entry == null)
        {
            return;
        }

        this.downloadProgress = 0.20F;
        this.downloadStatusMessage = "Uninstalling " + entry.name + "...";
        this.updateStatusText();

        if (this.uninstallBtn != null)
        {
            this.uninstallBtn.setEnabled(false);
        }

        HubAssetDownloader.uninstall(entry, (status) ->
        {
            this.downloadProgress = status.done ? -1F : status.progress;
            this.downloadStatusMessage = status.message;

            this.updateStatusText();

            if (status.done)
            {
                this.filterAndSort();
            }
        });
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* Smooth animated bottom download progress bar */
        if (this.downloadProgress >= 0F && this.downloadProgress <= 1F)
        {
            int bx = this.area.x + 8;
            int by = this.area.ey() - 24;
            int bw = this.area.w - 16;

            context.batcher.box(bx, by, bx + bw, by + 3, Colors.A50);
            context.batcher.box(bx, by, bx + (int) (bw * this.downloadProgress), by + 3, Colors.A100 | BBSSettings.primaryColor.get());
        }
    }
}
