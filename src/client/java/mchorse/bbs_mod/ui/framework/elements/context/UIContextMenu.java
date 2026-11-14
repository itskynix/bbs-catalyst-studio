package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.ui.framework.elements.windows.GlassChrome;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import org.lwjgl.glfw.GLFW;

public abstract class UIContextMenu extends UIElement
{
    public UIContextMenu()
    {
        super();

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE);
    }

    public abstract boolean isEmpty();

    /**
     * Close this menu now that whatever was picked in it has run — unless that very thing has
     * already opened another menu in its place.
     *
     * <p>Picking a row ends the menu, so every path that runs an action closes it afterwards.
     * But some actions <em>are</em> "open the next menu", and by then this one is no longer the
     * menu on screen, it is the one parked behind the new one. Tearing it down there would
     * leave the step back with nowhere to return to.</p>
     */
    public void dismiss()
    {
        UIContext context = this.getContext();

        if (context == null || context.contextMenu == this)
        {
            this.removeFromParent();
        }
    }

    /**
     * Leaving the screen takes the whole chain along: a menu this one was opened over is parked
     * out of sight and has no way of its own to notice that it is no longer wanted.
     */
    @Override
    public void removeFromParent()
    {
        UIContext context = this.getContext();

        super.removeFromParent();

        if (context != null)
        {
            context.dismissContextMenu(this);
        }
    }

    /**
     * Set mouse coordinate
     *
     * In this method for subclasses, you should setup the resizer
     */
    public abstract void setMouse(UIContext context);

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            this.removeFromParent();
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.removeFromParent();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.renderBackground(context);

        super.render(context);

        RoundedDraw.outline(context.batcher, this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, GlassChrome.RADIUS - 1, RoundedDraw.ALL, Colors.setA(Colors.WHITE, 0.08F));
        RoundedDraw.outline(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), GlassChrome.RADIUS, RoundedDraw.ALL, GlassChrome.BORDER);
    }

    /** Dark glass with round corners, the same as every desktop window: every context menu has it. */
    protected void renderBackground(UIContext context)
    {
        Area a = this.area;

        boolean opened = RoundedDraw.begin(context.batcher);

        GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), 10, 0.5F);
        RoundedDraw.fill(context.batcher, a.x, a.y, a.ex(), a.ey(), GlassChrome.RADIUS, RoundedDraw.ALL, 0xeb171d29);

        for (int k = 0; k < Math.min(40, a.h); k += 2)
        {
            float fade = 1F - k / 40F;

            RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), GlassChrome.RADIUS, RoundedDraw.ALL, a.y + k, a.y + k + 2, Colors.setA(Colors.WHITE, 0.06F * fade * fade));
        }

        RoundedDraw.end(context.batcher, opened);
    }
}
