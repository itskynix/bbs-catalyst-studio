package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

/**
 * Modal detail overlay for a community asset in the Modern Store Hub.
 */
public class UICommunityAssetDetailOverlay extends UIOverlayPanel
{
    private final UICommunityHubPanel panel;
    private final HubAssetEntry entry;

    public UICommunityAssetDetailOverlay(UICommunityHubPanel panel, HubAssetEntry entry)
    {
        super(IKey.raw(entry.name));

        this.panel = panel;
        this.entry = entry;

        UIScrollView scroll = UI.scrollView(8);
        scroll.full(this.content);
        this.content.add(scroll);

        boolean isInstalled = entry.isInstalled();
        boolean hasUpdate = entry.hasUpdate();

        /* Subtitle */
        String metaSubtitle = "v" + entry.version + " • by " + entry.author + " • " + entry.getFormattedSize();
        UILabel subtitleLabel = UI.label(IKey.raw(metaSubtitle), 12, Colors.A75 | Colors.WHITE);

        /* Banner block */
        UIElement bannerBlock = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int x = this.area.x;
                int y = this.area.y;
                int w = this.area.w;
                int h = this.area.h;

                context.batcher.box(x, y, x + w, y + h, 0xf0080a10);
                context.batcher.outline(x, y, x + w, y + h, 0x22ffffff);

                Texture thumb = HubThumbnailManager.getThumbnail(entry);

                if (thumb != null)
                {
                    context.batcher.fullTexturedBox(thumb, x + 2, y + 2, w - 4, h - 4);
                }
                else
                {
                    int catColor = entry.category.color;
                    context.batcher.box(x + 2, y + 2, x + w - 2, y + h - 2, 0x2a000000 | (catColor & 0xffffff));
                    context.batcher.icon(entry.category.icon, x + w / 2 - 8, y + h / 2 - 14);
                    String catTitle = entry.category.title.get().toUpperCase();
                    int catW = context.batcher.getFont().getWidth(catTitle);
                    context.batcher.text(catTitle, x + w / 2 - catW / 2, y + h / 2 + 4, Colors.setA(Colors.WHITE, 0.7F), false);
                }

                super.render(context);
            }
        };
        bannerBlock.h(120);

        /* Stats */
        String statsStr = "Downloads: " + entry.downloads + "   |   Likes: " + entry.likes + "   |   Files: " + entry.files.size();
        UILabel statsLabel = UI.label(IKey.raw(statsStr), 12, Colors.A75 | Colors.WHITE);

        /* Action buttons row */
        IKey installText = hasUpdate ? IKey.raw("Update to v" + entry.version) : (isInstalled ? UIKeys.HUB_REINSTALL : UIKeys.HUB_INSTALL);
        UIButton installBtn = new UIButton(installText, (b) ->
        {
            this.close();
            panel.startInstall(entry);
        });
        installBtn.h(20);

        UIButton uninstallBtn = new UIButton(UIKeys.HUB_UNINSTALL, (b) ->
        {
            this.close();
            panel.startUninstall(entry);
        });
        uninstallBtn.h(20);
        uninstallBtn.setEnabled(isInstalled);

        UIElement buttonsRow = UI.row(6, installBtn, uninstallBtn);

        /* Description */
        UIElement descBlock = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                FontRenderer font = context.batcher.getFont();
                List<String> lines = font.wrap(entry.description, this.area.w - 8);
                int y = this.area.y + 4;

                for (String line : lines)
                {
                    context.batcher.text(line, this.area.x + 4, y, Colors.WHITE, false);
                    y += font.getLineHeight();
                }

                super.render(context);
            }
        };
        int descLines = entry.description.length() / 35 + 2;
        descBlock.h(Math.max(30, descLines * 12));

        /* Tags */
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
        tagsContainer.h(entry.tags.isEmpty() ? 0 : 20);

        /* Files Breakdown */
        UILabel filesTitle = UI.label(UIKeys.HUB_FILES_INCLUDED, 14, Colors.WHITE);
        UIElement filesBlock = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int y = this.area.y;

                for (String relFile : entry.files)
                {
                    context.batcher.text("- " + relFile, this.area.x + 6, y, Colors.A75 | Colors.WHITE, false);
                    y += 12;
                }

                super.render(context);
            }
        };
        filesBlock.h(Math.max(16, entry.files.size() * 12 + 4));

        scroll.add(
            subtitleLabel,
            bannerBlock,
            statsLabel,
            buttonsRow,
            descBlock
        );

        if (!entry.tags.isEmpty())
        {
            scroll.add(UI.label(UIKeys.HUB_TAGS, 12, Colors.WHITE), tagsContainer);
        }

        if (!entry.files.isEmpty())
        {
            scroll.add(filesTitle, filesBlock);
        }

        scroll.resize();
    }
}
