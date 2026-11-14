package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * A window's body: whatever its children draw is cut to its own rectangle, so a scrolled card, a long
 * label or a list row never spills onto the title bar, the footer or past the frame. Scissors nest, so
 * a scroll view inside still clips to the smaller of the two.
 */
public class UIClipElement extends UIElement
{
    @Override
    public void render(UIContext context)
    {
        if (this.area.w <= 0 || this.area.h <= 0)
        {
            return;
        }

        context.batcher.clip(this.area, context);

        try
        {
            super.render(context);
        }
        finally
        {
            context.batcher.unclip(context);
        }
    }
}
