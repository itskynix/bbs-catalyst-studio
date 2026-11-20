package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * A desktop window in the studio's dark look with a Windows 11 title bar: a title strip with the
 * window's icon and name and a wide close button, a body, and an optional footer with a primary and
 * a cancel button. It is dragged by its title bar and kept inside the element it lives in, comes to
 * the front when clicked, and the front one closes on Escape.
 *
 * <p>Put whatever the window shows into {@link #content}. Windows are siblings in a host element
 * that is as big as the area they may move in; the order of the host's children is the stacking
 * order, the last one being on top.</p>
 */
public class UIDesktopWindow extends UIElement
{
    public static final int TITLE_HEIGHT = 24;
    public static final int FOOTER_HEIGHT = 36;

    public static final int RADIUS = 6;
    public static final int GRIP = 5;

    public static final int BORDER = 0xff2a3444;

    /* The glass: the studio's dark blues at about 88 percent, so what is behind shows faintly */
    public static final int BODY = 0xe011161f;
    public static final int TITLE_BAR = 0xe6161d29;
    public static final int FOOTER = 0xe60d1118;
    public static final int CLOSE_HOVER = 0xffe81123;

    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int UP = 4;
    private static final int DOWN = 8;

    public final UIElement content = new UIClipElement();

    private final CloseButton close;

    private IKey title = IKey.EMPTY;
    private Icon icon = Icons.LAYOUT;
    private int width = 360;
    private int height = 220;
    private int minWidth = 240;
    private int minHeight = 120;

    private UIElement footer;
    private Runnable onClose;

    private boolean dragging;
    private int dragX;
    private int dragY;

    private int resizing;
    private int startMouseX;
    private int startMouseY;
    private int startX;
    private int startY;
    private int startWidth;
    private int startHeight;

    public UIDesktopWindow(IKey title)
    {
        super();

        this.title = title;

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE);

        this.close = new CloseButton(this);
        this.close.relative(this).x(1F, -29).y(1).wh(28, TITLE_HEIGHT - 1);

        this.content.relative(this).x(1).y(TITLE_HEIGHT + 1).w(1F, -2).h(1F, -(TITLE_HEIGHT + 2));

        this.add(this.content, this.close);
    }

    public UIDesktopWindow icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    public UIDesktopWindow size(int width, int height)
    {
        this.width = width;
        this.height = height;

        return this;
    }

    /** How small dragging an edge may make the window. */
    public UIDesktopWindow minSize(int minWidth, int minHeight)
    {
        this.minWidth = minWidth;
        this.minHeight = minHeight;

        return this;
    }

    public UIDesktopWindow onClose(Runnable onClose)
    {
        this.onClose = onClose;

        return this;
    }

    /**
     * The footer bar: the primary button on the right (it runs {@code primary} and closes the
     * window) and, when {@code cancel} has a label, a cancel button beside it that just closes.
     */
    public UIDesktopWindow footer(IKey primaryLabel, Runnable primary, IKey cancelLabel)
    {
        if (this.footer != null)
        {
            this.footer.removeFromParent();
        }

        this.footer = new UIElement();
        this.footer.relative(this).x(1).y(1F, -(FOOTER_HEIGHT + 1)).w(1F, -2).h(FOOTER_HEIGHT);

        UIButton ok = new UIGlassButton(primaryLabel, true, (b) ->
        {
            if (primary != null)
            {
                primary.run();
            }

            this.close();
        });

        ok.relative(this.footer).x(1F, -106).y(6).wh(98, 24);
        this.footer.add(ok);

        if (cancelLabel != null)
        {
            UIButton cancel = new UIGlassButton(cancelLabel, false, (b) -> this.close());

            cancel.relative(this.footer).x(1F, -208).y(6).wh(98, 24);
            this.footer.add(cancel);
        }

        this.content.h(1F, -(TITLE_HEIGHT + FOOTER_HEIGHT + 3));
        this.add(this.footer);

        return this;
    }

    /** Puts the window on {@code host} at {@code x}, {@code y} (kept inside the host) and in front of the others. */
    public UIDesktopWindow open(UIElement host, int x, int y)
    {
        this.relative(host).wh(this.width, this.height);
        this.x(x).y(y);

        host.add(this);

        if (host.area.w > 0)
        {
            this.moveTo(host, x, y);
        }

        return this;
    }

    public void close()
    {
        if (this.hasParent())
        {
            this.removeFromParent();

            if (this.onClose != null)
            {
                this.onClose.run();
            }
        }
    }

    public boolean isFront()
    {
        UIElement parent = this.getParent();

        if (parent == null)
        {
            return false;
        }

        java.util.List<IUIElement> children = parent.getChildren();

        return !children.isEmpty() && children.get(children.size() - 1) == this;
    }

    /** Last among its siblings is on top: the window goes there. */
    private void bringToFront()
    {
        UIElement parent = this.getParent();

        if (parent != null && !this.isFront())
        {
            java.util.List<IUIElement> children = parent.getChildren();

            children.remove(this);
            children.add(this);
        }
    }

    /** The window keeps inside its host, whatever is asked of it. */
    private void moveTo(UIElement host, int x, int y)
    {
        int maxX = Math.max(0, host.area.w - this.width);
        int maxY = Math.max(0, host.area.h - this.height);

        this.x(Math.max(0, Math.min(maxX, x)));
        this.y(Math.max(0, Math.min(maxY, y)));
        this.resize();
    }

    /** Which edges the mouse is on, as a mask: the 5 pixel rim of the window, except over the close button. */
    private int zoneAt(UIContext context)
    {
        Area a = this.area;

        if (!a.isInside(context) || this.close.area.isInside(context))
        {
            return 0;
        }

        int zone = 0;

        zone |= context.mouseX < a.x + GRIP ? LEFT : 0;
        zone |= context.mouseX >= a.ex() - GRIP ? RIGHT : 0;
        zone |= context.mouseY < a.y + GRIP ? UP : 0;
        zone |= context.mouseY >= a.ey() - GRIP ? DOWN : 0;

        return zone;
    }

    private static int cursorFor(int zone)
    {
        boolean horizontal = (zone & (LEFT | RIGHT)) != 0;
        boolean vertical = (zone & (UP | DOWN)) != 0;

        if (horizontal && vertical)
        {
            boolean backslash = (zone & (LEFT | UP)) == (LEFT | UP) || (zone & (RIGHT | DOWN)) == (RIGHT | DOWN);

            return backslash ? GLFW.GLFW_RESIZE_NWSE_CURSOR : GLFW.GLFW_RESIZE_NESW_CURSOR;
        }

        return horizontal ? GLFW.GLFW_HRESIZE_CURSOR : GLFW.GLFW_VRESIZE_CURSOR;
    }

    /** A window drawn later is over this one where the mouse is: it gets the cursor, not this one. */
    private boolean coveredAt(UIContext context)
    {
        UIElement parent = this.getParent();

        if (parent == null)
        {
            return false;
        }

        java.util.List<IUIElement> children = parent.getChildren();

        for (int i = children.indexOf(this) + 1; i < children.size(); i++)
        {
            if (children.get(i) instanceof UIDesktopWindow other && other.area.isInside(context))
            {
                return true;
            }
        }

        return false;
    }

    private void resizeTo(UIElement host, UIContext context)
    {
        int dx = context.mouseX - this.startMouseX;
        int dy = context.mouseY - this.startMouseY;
        int x = this.startX;
        int y = this.startY;
        int w = this.startWidth;
        int h = this.startHeight;

        if ((this.resizing & LEFT) != 0)
        {
            w = Math.max(this.minWidth, this.startWidth - dx);
            x = this.startX + this.startWidth - w;

            if (x < 0)
            {
                w += x;
                x = 0;
            }
        }
        else if ((this.resizing & RIGHT) != 0)
        {
            w = Math.min(Math.max(this.minWidth, this.startWidth + dx), Math.max(this.minWidth, host.area.w - x));
        }

        if ((this.resizing & UP) != 0)
        {
            h = Math.max(this.minHeight, this.startHeight - dy);
            y = this.startY + this.startHeight - h;

            if (y < 0)
            {
                h += y;
                y = 0;
            }
        }
        else if ((this.resizing & DOWN) != 0)
        {
            h = Math.min(Math.max(this.minHeight, this.startHeight + dy), Math.max(this.minHeight, host.area.h - y));
        }

        this.width = w;
        this.height = h;
        this.x(x).y(y).wh(w, h);
        this.resize();
    }

    @Override
    protected IUIElement childrenMouseClicked(UIContext context)
    {
        if (this.area.isInside(context))
        {
            this.bringToFront();

            int zone = this.zoneAt(context);

            if (context.mouseButton == 0 && zone != 0)
            {
                this.resizing = zone;
                this.startMouseX = context.mouseX;
                this.startMouseY = context.mouseY;
                this.startX = this.area.x - this.getParent().area.x;
                this.startY = this.area.y - this.getParent().area.y;
                this.startWidth = this.width;
                this.startHeight = this.height;

                return this;
            }
        }

        return super.childrenMouseClicked(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        if (context.mouseButton != 0)
        {
            /* the right button belongs to the context menu, if the window has one */
            return false;
        }

        if (context.mouseY < this.area.y + TITLE_HEIGHT)
        {
            this.dragging = true;
            this.dragX = context.mouseX - this.area.x;
            this.dragY = context.mouseY - this.area.y;
        }

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.dragging = false;
        this.resizing = 0;

        return false;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE) && this.isFront())
        {
            this.close();

            return true;
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        UIElement host = this.getParent();

        if ((this.dragging || this.resizing != 0) && host != null)
        {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();

            if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
            {
                this.dragging = false;
                this.resizing = 0;
            }
            else if (this.resizing != 0)
            {
                this.resizeTo(host, context);
            }
            else
            {
                this.moveTo(host, context.mouseX - this.dragX - host.area.x, context.mouseY - this.dragY - host.area.y);
            }
        }

        if (this.resizing != 0)
        {
            context.requestCursor(cursorFor(this.resizing));
        }
        else if (!this.dragging && !this.coveredAt(context))
        {
            int zone = this.zoneAt(context);

            if (zone != 0)
            {
                context.requestCursor(cursorFor(zone));
            }
        }

        Area a = this.area;
        boolean front = this.isFront();
        int footerStart = this.footer == null ? a.ey() : a.ey() - FOOTER_HEIGHT - 1;

        GlassChrome.paint(context, a, TITLE_HEIGHT, footerStart, front);

        FontRenderer font = context.batcher.getFont();
        int side = 34;
        String text = font.limitToWidth(this.title.get(), "...", Math.max(10, a.w - side * 2));

        context.batcher.icon(this.icon, Colors.setA(Colors.WHITE, front ? 0.9F : 0.5F), a.x + 8, a.y + 4);
        context.batcher.text(text, a.mx() - font.getWidth(text) / 2, a.y + 1 + (TITLE_HEIGHT - font.getHeight()) / 2, front ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.6F), false);

        super.render(context);

        GlassChrome.frame(context, a, front);
    }

    /** The wide close button of the title bar: neutral, red under the cursor. */
    public static class CloseButton extends UIClickable<CloseButton>
    {
        public CloseButton(UIDesktopWindow window)
        {
            super((b) -> window.close());
        }

        @Override
        protected CloseButton get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Area a = this.area;

            if (FrutigerAero.on())
            {
                /* The red crystal capsule, with the cross kept white on it */
                FrutigerAero.close(context.batcher, a, this.hover);
                Batcher2D.keepWhite += 1;
                context.batcher.icon(Icons.CLOSE, Colors.WHITE, a.mx(), a.my(), 0.5F, 0.5F);
                Batcher2D.keepWhite -= 1;

                return;
            }

            if (this.hover)
            {
                /* flush with the window's corner, so only that corner is round */
                RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS - 1, RoundedDraw.TR, a.y, a.ey(), UIDesktopWindow.CLOSE_HOVER);
            }

            context.batcher.icon(Icons.CLOSE, this.hover ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.75F), a.mx(), a.my(), 0.5F, 0.5F);
        }
    }
}
