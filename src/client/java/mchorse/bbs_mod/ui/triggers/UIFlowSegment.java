package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * A row of exclusive choices. As tabs, the picked one gets a bright underline; as a switch, the
 * picked one is a filled pill inside a dark track.
 */
public class UIFlowSegment extends UIElement
{
    private final List<IKey> labels;
    private final List<Icon> icons;
    private final boolean pill;
    private final Consumer<Integer> callback;
    private int value;

    public UIFlowSegment(List<IKey> labels, List<Icon> icons, boolean pill, Consumer<Integer> callback)
    {
        this.labels = labels;
        this.icons = icons;
        this.pill = pill;
        this.callback = callback;
    }

    public int getValue()
    {
        return this.value;
    }

    public void setValue(int value)
    {
        this.value = value;
    }

    /** How wide the row needs to be for its labels. */
    public int preferredWidth(UIContext context)
    {
        int w = 0;

        for (IKey label : this.labels)
        {
            w += context.batcher.getFont().getWidth(label.get()) + 30;
        }

        return w;
    }

    private int cell()
    {
        return this.area.w / Math.max(1, this.labels.size());
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int index = Math.min(this.labels.size() - 1, (context.mouseX - this.area.x) / Math.max(1, this.cell()));

            this.value = index;
            this.callback.accept(index);

            return true;
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        int cell = this.cell();
        int x = this.area.x;
        int y = this.area.y;

        if (this.pill)
        {
            context.batcher.box(x, y, this.area.ex(), this.area.ey(), FlowTheme.field());
            context.batcher.outline(x, y, this.area.ex(), this.area.ey(), FlowTheme.border());
        }

        for (int i = 0; i < this.labels.size(); i++)
        {
            int cx = x + i * cell;
            boolean on = i == this.value;
            boolean hover = context.mouseX >= cx && context.mouseX < cx + cell && this.area.isInside(context);
            String label = this.labels.get(i).get();
            Icon icon = this.icons == null ? null : this.icons.get(i);
            int width = context.batcher.getFont().getWidth(label) + (icon == null ? 0 : 18);
            int tx = cx + (cell - width) / 2;
            int ty = y + (this.area.h - context.batcher.getFont().getHeight()) / 2;

            if (this.pill)
            {
                if (on)
                {
                    context.batcher.box(cx + 2, y + 2, cx + cell - 2, this.area.ey() - 2, FlowTheme.accent());
                }
                else if (hover)
                {
                    context.batcher.box(cx + 2, y + 2, cx + cell - 2, this.area.ey() - 2, 0x20ffffff);
                }
            }
            else
            {
                if (on)
                {
                    context.batcher.box(cx, y, cx + cell, this.area.ey(), FlowTheme.body());
                    context.batcher.box(cx, this.area.ey() - 2, cx + cell, this.area.ey(), FlowTheme.accent());
                }
                else if (hover)
                {
                    context.batcher.box(cx, y, cx + cell, this.area.ey(), 0x18ffffff);
                }
            }

            int color = on ? (this.pill ? Colors.WHITE : FlowTheme.text()) : FlowTheme.dim();

            if (icon != null)
            {
                context.batcher.icon(icon, color, tx, ty - 4);
                tx += 18;
            }

            context.batcher.textShadow(label, tx, ty, color);
        }

        super.render(context);
    }
}
