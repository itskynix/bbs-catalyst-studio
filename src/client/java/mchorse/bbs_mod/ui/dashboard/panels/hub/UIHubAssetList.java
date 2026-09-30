package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

public class UIHubAssetList extends UIList<HubAssetEntry>
{
    public UIHubAssetList(Consumer<List<HubAssetEntry>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = 74;
    }

    @Override
    protected boolean canScaleRows()
    {
        return false;
    }

    @Override
    public void renderListElement(UIContext context, HubAssetEntry element, int i, int x, int y, boolean hover, boolean selected)
    {
        int h = this.scroll.scrollItemSize;
        int cardY = y + 2;
        int cardH = h - 4;
        int w = this.area.w;

        /* Modern card container styling */
        if (selected)
        {
            context.batcher.box(x + 2, cardY, x + w - 2, cardY + cardH, 0x661E2838);
            context.batcher.outline(x + 2, cardY, x + w - 2, cardY + cardH, Colors.A100 | BBSSettings.primaryColor.get());
            context.batcher.box(x + 2, cardY, x + 5, cardY + cardH, Colors.A100 | BBSSettings.primaryColor.get());
        }
        else if (hover)
        {
            context.batcher.box(x + 2, cardY, x + w - 2, cardY + cardH, 0x44262E3B);
            context.batcher.outline(x + 2, cardY, x + w - 2, cardY + cardH, 0x44FFFFFF);
        }
        else
        {
            context.batcher.box(x + 2, cardY, x + w - 2, cardY + cardH, 0x33141820);
            context.batcher.outline(x + 2, cardY, x + w - 2, cardY + cardH, 0x1AFFFFFF);
        }

        /* Left thumbnail preview box */
        int thumbX = x + 7;
        int thumbY = cardY + 4;
        int thumbSize = 62;

        context.batcher.box(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0x66080B10);
        context.batcher.outline(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0x22FFFFFF);

        Texture thumb = HubThumbnailManager.getThumbnail(element);

        if (thumb != null)
        {
            context.batcher.fullTexturedBox(thumb, thumbX + 1, thumbY + 1, thumbSize - 2, thumbSize - 2);
        }
        else
        {
            /* Elegant category fallback banner */
            int catColor = element.category.color;

            context.batcher.box(thumbX + 1, thumbY + 1, thumbX + thumbSize - 1, thumbY + thumbSize - 1, 0x33000000 | (catColor & 0xFFFFFF));
            context.batcher.icon(element.category.icon, thumbX + (thumbSize / 2) - 8, thumbY + (thumbSize / 2) - 13);

            String catShort = element.category.title.get();
            int catSW = context.batcher.getFont().getWidth(catShort);

            context.batcher.text(catShort, thumbX + (thumbSize / 2) - (catSW / 2), thumbY + 42, Colors.A75 | Colors.WHITE);
        }

        /* Text content to the right of thumbnail */
        int contentX = thumbX + thumbSize + 9;
        int maxTitleW = Math.max(50, w - (contentX - x) - 82);

        /* Row 1: Title, Version, Action Pill */
        String titleStr = context.batcher.getFont().limitToWidth(element.name, maxTitleW);

        context.batcher.text(titleStr, contentX, cardY + 7, Colors.WHITE);

        int titleW = context.batcher.getFont().getWidth(titleStr);

        context.batcher.textCard("v" + element.version, contentX + titleW + 6, cardY + 7, Colors.WHITE, 0x44333333, 2);

        if (element.hasUpdate())
        {
            context.batcher.textCard("UPDATE", x + w - 68, cardY + 7, Colors.WHITE, 0xEEF57C00, 3);
        }
        else if (element.isInstalled())
        {
            context.batcher.textCard("INSTALLED", x + w - 76, cardY + 7, Colors.WHITE, 0xEE2E7D32, 3);
        }
        else
        {
            context.batcher.textCard("GET", x + w - 44, cardY + 7, Colors.WHITE, 0xEE1976D2, 3);
        }

        /* Row 2: Category Badge, Author, File Size */
        int catColor = element.category.color;
        String catName = element.category.title.get();
        int catBadgeW = context.batcher.getFont().getWidth(catName) + 10;

        context.batcher.box(contentX, cardY + 23, contentX + catBadgeW, cardY + 36, 0x44000000 | (catColor & 0xFFFFFF));
        context.batcher.box(contentX, cardY + 23, contentX + 2, cardY + 36, 0xFF000000 | (catColor & 0xFFFFFF));
        context.batcher.text(catName, contentX + 5, cardY + 25, Colors.WHITE);

        int metaX = contentX + catBadgeW + 8;
        String authorStr = "by " + element.author;

        context.batcher.text(authorStr, metaX, cardY + 25, Colors.A75 | Colors.WHITE);

        int authorW = context.batcher.getFont().getWidth(authorStr);

        context.batcher.text("*  " + element.getFormattedSize(), metaX + authorW + 6, cardY + 25, Colors.A50 | Colors.WHITE);

        /* Row 3: Description excerpt */
        String desc = context.batcher.getFont().limitToWidth(element.description, w - (contentX - x) - 10);

        context.batcher.text(desc, contentX, cardY + 40, Colors.A50 | Colors.WHITE);

        /* Row 4: Download / Like statistics */
        String stats = element.downloads + " downloads  *  " + element.likes + " likes";

        context.batcher.text(stats, contentX, cardY + 54, Colors.A50 | Colors.WHITE);
    }

    @Override
    protected String elementToString(UIContext context, int i, HubAssetEntry element)
    {
        return element.name;
    }
}
