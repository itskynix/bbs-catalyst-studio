package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * UIList component for Catalyst projects.
 * Displays project icon, name, frame rate, duration, and resolution per row.
 */
public class UICatalystProjectList extends UIList<CatalystProject>
{
    private Consumer<CatalystProject> onOpenCallback;

    public UICatalystProjectList(Consumer<List<CatalystProject>> callback)
    {
        super(callback);
        this.scroll.scrollItemSize = 34;
    }

    public UICatalystProjectList onOpen(Consumer<CatalystProject> onOpenCallback)
    {
        this.onOpenCallback = onOpenCallback;
        return this;
    }

    @Override
    protected boolean onOpen(CatalystProject item)
    {
        if (this.onOpenCallback != null && item != null)
        {
            this.onOpenCallback.accept(item);
            return true;
        }

        return super.onOpen(item);
    }

    @Override
    protected void renderElementPart(UIContext context, CatalystProject element, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();

        /* Render project icon */
        context.batcher.icon(Icons.FILM, selected ? Colors.WHITE : (hover ? Colors.LIGHTEST_GRAY : Colors.GRAY), x + 6, y + 9);

        /* Render title */
        int textX = x + 26;
        int textY = y + 4;
        int titleColor = RowStyle.textColor(hover || selected);
        context.batcher.text(element.name, textX, textY, titleColor, false);

        /* Render subtitle with fps, duration, resolution */
        String subtitle = String.format("%.1fs (%df @ %d FPS) • %d×%d", element.durationSeconds, element.duration, element.fps, element.width, element.height);
        context.batcher.text(subtitle, textX, textY + font.getHeight() + 2, Colors.GRAY, false);
    }
}
