package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.function.IntConsumer;

/**
 * The gap between two panels of the recap editor, which resizes them when dragged. A vertical one
 * hands over the pointer's x, a horizontal one its y; the editor works out the sizes from it.
 */
public class UIRecapSplitter extends UIElement
{
    private final boolean vertical;
    private final IntConsumer onDrag;
    private final Runnable onRelease;
    private boolean dragging;

    public UIRecapSplitter(boolean vertical, IntConsumer onDrag, Runnable onRelease)
    {
        this.vertical = vertical;
        this.onDrag = onDrag;
        this.onRelease = onRelease;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            this.dragging = true;

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.dragging)
        {
            this.dragging = false;
            this.onRelease.run();

            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging)
        {
            this.onDrag.accept(this.vertical ? context.mouseX : context.mouseY);
        }

        if (this.dragging || this.area.isInside(context))
        {
            int color = this.dragging ? RecapStyle.accent() : (RecapStyle.light() ? 0x802b7ab8 : 0x60ffffff);

            if (this.vertical)
            {
                context.batcher.box(this.area.mx() - 1, this.area.y + 6, this.area.mx() + 1, this.area.ey() - 6, color);
            }
            else
            {
                context.batcher.box(this.area.x + 6, this.area.my() - 1, this.area.ex() - 6, this.area.my() + 1, color);
            }
        }

        super.render(context);
    }
}
