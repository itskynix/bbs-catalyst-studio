package mchorse.bbs_mod.ui.film.export;

import mchorse.bbs_mod.camera.export.RenderJob;
import mchorse.bbs_mod.camera.export.RenderQueue;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * UIList rendering the queued export jobs with status, badges, and progress.
 */
public class UIRenderJobList extends UIList<RenderJob>
{
    public UIRenderJobList(Consumer<List<RenderJob>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = 32;
    }

    private boolean updating = false;

    public void updateList()
    {
        if (this.updating)
        {
            return;
        }

        this.updating = true;
        try
        {
            this.setList(RenderQueue.getJobs());
        }
        finally
        {
            this.updating = false;
        }
    }

    @Override
    protected void renderElementPart(UIContext context, RenderJob element, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        int h = this.scroll.scrollItemSize;

        // 1. Status Icon
        int iconY = y + (h - 16) / 2;
        RenderJob.Status status = element.getStatus();

        if (status == RenderJob.Status.RENDERING)
        {
            context.batcher.icon(Icons.PLAY, Colors.ACTIVE, x + 4, iconY);
        }
        else if (status == RenderJob.Status.COMPLETED)
        {
            context.batcher.icon(Icons.SAVED, Colors.POSITIVE, x + 4, iconY);
        }
        else if (status == RenderJob.Status.FAILED)
        {
            context.batcher.icon(Icons.CLOSE, Colors.NEGATIVE, x + 4, iconY);
        }
        else if (status == RenderJob.Status.CANCELLED)
        {
            context.batcher.icon(Icons.CLOSE, Colors.GRAY, x + 4, iconY);
        }
        else
        {
            context.batcher.icon(Icons.TIME, Colors.GRAY, x + 4, iconY);
        }

        // 2. Job Title
        String title = element.getTitle();
        context.batcher.textShadow(title, x + 24, y + 4, RowStyle.textColor(hover || selected));

        // 3. Subtitle: Profile Name + Resolution
        String sub = element.getProfile().getName() + " • " + element.getProfile().getFormat().name();
        context.batcher.text(sub, x + 24, y + 17, Colors.LIGHTEST_GRAY);

        // 4. Status label or progress on the right side
        String statusText;
        int statusColor;

        if (status == RenderJob.Status.RENDERING)
        {
            int pct = (int) (element.getProgress() * 100);
            statusText = pct + "%";
            statusColor = Colors.ACTIVE;

            // Small inline progress bar
            int barW = 50;
            int barH = 4;
            int barX = x + this.area.w - barW - 35;
            int barY = y + (h - barH) / 2;

            context.batcher.box(barX, barY, barX + barW, barY + barH, Colors.A50 | Colors.GRAY);
            context.batcher.box(barX, barY, barX + (int) (barW * element.getProgress()), barY + barH, Colors.ACTIVE);
        }
        else if (status == RenderJob.Status.COMPLETED)
        {
            statusText = "Done";
            statusColor = Colors.POSITIVE;
        }
        else if (status == RenderJob.Status.FAILED)
        {
            statusText = "Failed";
            statusColor = Colors.NEGATIVE;
        }
        else if (status == RenderJob.Status.CANCELLED)
        {
            statusText = "Cancelled";
            statusColor = Colors.GRAY;
        }
        else
        {
            statusText = "Pending";
            statusColor = Colors.GRAY;
        }

        int textW = font.getWidth(statusText);
        context.batcher.textShadow(statusText, x + this.area.w - textW - 8, y + (h - font.getHeight()) / 2, statusColor);
    }

    @Override
    protected String elementToString(UIContext context, int i, RenderJob element)
    {
        return element.getTitle();
    }
}
