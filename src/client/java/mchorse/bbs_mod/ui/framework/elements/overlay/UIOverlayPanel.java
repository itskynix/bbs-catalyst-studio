package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIOverlayCloseEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.windows.GlassChrome;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.framework.elements.windows.UIClipElement;
import mchorse.bbs_mod.ui.framework.elements.windows.UIDesktopWindow;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class UIOverlayPanel extends UIElement
{
    /** Side of the square grip in the bottom right corner. */
    private static final int GRIP = 10;

    /** The title bar, and the width of the rim that sizes the panel. */
    public static final int TITLE_HEIGHT = 24;
    private static final int EDGE = 5;

    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int UP = 4;
    private static final int DOWN = 8;

    /** Nothing useful fits into a panel smaller than this. */
    private static final int MIN_WIDTH = 160;
    private static final int MIN_HEIGHT = 60;

    public UILabel title;
    public UIElement icons;
    public UIIcon close;
    public UIElement content;

    /** What the title bar wears on its left: the kind of window this is. */
    private Icon icon = Icons.LAYOUT;

    private boolean moving;
    private int lastX;
    private int lastY;

    private int initialOffsetX;
    private int initialOffsetY;

    /**
     * Whether the overlay opened this panel without a size. Only then is the panel's own opinion
     * about its height in charge; a call site that passed one meant it.
     */
    private boolean sizeless;

    /** The corner grip, on the panels that offer sizing; null on the ones that don't. */
    private UIDraggable grip;

    /** Whether the user sized this panel, i.e. whether the size is worth remembering. */
    private boolean resized;

    public UIOverlayPanel(IKey title)
    {
        super();

        this.title = UI.label(title);
        this.close = new UIIcon(Icons.CLOSE, (b) -> this.close());
        this.content = new UIClipElement();
        this.icons = new UIElement();

        /* The title sits in the middle of the bar, clear of the icon on the left and the close button */
        this.title.labelAnchor(0.5F, 0.5F).relative(this).x(34).y(0).w(1F, -68).h(TITLE_HEIGHT);
        this.icons.relative(this).x(1F, -20).y(TITLE_HEIGHT).w(20).h(1F, -TITLE_HEIGHT).column(0).stretch();
        this.content.relative(this).xy(0, TITLE_HEIGHT).w(1F, -20).h(1F, -TITLE_HEIGHT);
        this.close.relative(this).x(1F, -29).y(1).wh(28, TITLE_HEIGHT - 1);

        this.add(this.title, this.icons, this.content, this.close);

        this.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
    }

    /** The attention flash: three soft red blinks of the frame, this long each. */
    private static final long FLASH_BLINK = 260_000_000L;
    private static final int FLASH_COUNT = 3;

    /** When the attention flash began; 0 when none is running. */
    private long flashStart;

    /**
     * Whether this window holds the rest of the screen until it is done (a confirmation, a name to
     * type, Film Oluştur). A click beside it doesn't reach what is behind: the window flashes instead.
     * A tool window that returns false lets such a click through to the studio.
     */
    public boolean isModal()
    {
        return true;
    }

    /** Starts the Windows style attention flash; a flash already running starts over. */
    public void flash()
    {
        this.flashStart = System.nanoTime();
    }

    /** How red the frame is now, 0 to 1: each blink fades in and out smoothly. */
    private float flashIntensity()
    {
        if (this.flashStart == 0L)
        {
            return 0F;
        }

        long elapsed = System.nanoTime() - this.flashStart;

        if (elapsed >= FLASH_BLINK * FLASH_COUNT)
        {
            this.flashStart = 0L;

            return 0F;
        }

        double phase = (elapsed % FLASH_BLINK) / (double) FLASH_BLINK;

        return (float) (0.5D - 0.5D * Math.cos(phase * Math.PI * 2D));
    }

    /** Height of the darker action strip along the bottom edge; 0 for a panel without one. */
    private int footerHeight;

    /** Reserves a footer strip of this height: the glass paints it darker and the action buttons sit in it. */
    public UIOverlayPanel footer(int height)
    {
        this.footerHeight = height;
        this.content.h(1F, -TITLE_HEIGHT - height);

        return this;
    }

    public int getFooterHeight()
    {
        return this.footerHeight;
    }

    /** The icon on the left of the title bar. */
    public UIOverlayPanel icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    /** Marks the panel as opened without a size; called by {@link UIOverlay}. */
    public void sizeless()
    {
        this.sizeless = true;
    }

    /**
     * Whether the user may size this panel. A panel that measures itself has nothing to gain from
     * being stretched &mdash; its height gets recomputed from its content anyway &mdash; so only
     * the ones that take the size they were given offer a grip.
     */
    public boolean isResizable()
    {
        return true;
    }

    public boolean wasResized()
    {
        return this.resized;
    }

    /** The smallest the corner grip may size this panel to; a panel with more to fit asks for more. */
    public int getMinWidth()
    {
        return MIN_WIDTH;
    }

    public int getMinHeight()
    {
        return MIN_HEIGHT;
    }

    /**
     * Adds the corner grip. Called by {@link UIOverlay} rather than from the constructor: whether a
     * panel is resizable can depend on fields its subclass sets after {@code super(...)} has run.
     */
    public void setupResize()
    {
        if (this.grip != null || !this.isResizable())
        {
            return;
        }

        /* The bottom right corner keeps its dots; every other edge and corner is an invisible handle */
        this.grip = this.handle(RIGHT | DOWN, GLFW.GLFW_RESIZE_NWSE_CURSOR);
        this.grip.rendering(this::renderGrip);
        this.grip.relative(this).x(1F, -GRIP).y(1F, -GRIP).wh(GRIP, GRIP);

        UIDraggable left = this.handle(LEFT, GLFW.GLFW_HRESIZE_CURSOR);
        UIDraggable right = this.handle(RIGHT, GLFW.GLFW_HRESIZE_CURSOR);
        UIDraggable top = this.handle(UP, GLFW.GLFW_VRESIZE_CURSOR);
        UIDraggable bottom = this.handle(DOWN, GLFW.GLFW_VRESIZE_CURSOR);
        UIDraggable topLeft = this.handle(LEFT | UP, GLFW.GLFW_RESIZE_NWSE_CURSOR);
        UIDraggable topRight = this.handle(RIGHT | UP, GLFW.GLFW_RESIZE_NESW_CURSOR);
        UIDraggable bottomLeft = this.handle(LEFT | DOWN, GLFW.GLFW_RESIZE_NESW_CURSOR);

        left.relative(this).x(0).y(GRIP).w(EDGE).h(1F, -GRIP * 2);
        right.relative(this).x(1F, -EDGE).y(GRIP).w(EDGE).h(1F, -GRIP * 2);

        /* The top edge stops short of the close button, so its upper rim is still the button's */
        top.relative(this).x(GRIP).y(0).w(1F, -GRIP - 34).h(EDGE);
        bottom.relative(this).x(GRIP).y(1F, -EDGE).w(1F, -GRIP * 2).h(EDGE);
        topLeft.relative(this).x(0).y(0).wh(GRIP, GRIP);
        topRight.relative(this).x(1F, -EDGE).y(0).wh(EDGE, EDGE);
        bottomLeft.relative(this).x(0).y(1F, -GRIP).wh(GRIP, GRIP);

        /* Last children: they are offered the click in reverse, so the rim gets it before the
         * content underneath does */
        this.add(left, right, top, bottom, topLeft, topRight, bottomLeft, this.grip);
    }

    private UIDraggable handle(int mask, int cursor)
    {
        UIDraggable handle = new UIDraggable((context) -> this.dragEdges(context, mask));

        handle.rendering((context) -> {});
        handle.cursors(cursor, cursor);
        handle.referenceAxis((mask & (LEFT | RIGHT)) != 0, (mask & (UP | DOWN)) != 0);
        handle.reference(() -> new Vector2i((mask & LEFT) != 0 ? this.area.x : this.area.ex(), (mask & UP) != 0 ? this.area.y : this.area.ey()));

        return handle;
    }

    /**
     * Sizes the panel so the dragged edges follow the hand. The panel is placed by its anchor, so a
     * centered one grows to both sides and an edge travels at half the speed of the size; solving
     * for the edge rather than adding the mouse delta keeps the handle under the hand.
     */
    private void dragEdges(UIContext context, int mask)
    {
        UIElement parent = this.getParent();
        float anchorX = this.flex.x.anchor;
        float anchorY = this.flex.y.anchor;

        /* Past the overlay's own size the bounds resizer starts sliding the panel to keep it on
         * screen, which moves the anchor the corner is solved against, and the handle runs away from
         * the hand */
        int maxWidth = Math.max(parent.area.w, Math.min(this.getMinWidth(), parent.area.w));
        int maxHeight = Math.max(parent.area.h, Math.min(this.getMinHeight(), parent.area.h));
        int minWidth = Math.min(this.getMinWidth(), maxWidth);
        int minHeight = Math.min(this.getMinHeight(), maxHeight);
        int originX = this.area.x + (int) (this.area.w * anchorX);
        int originY = this.area.y + (int) (this.area.h * anchorY);

        if ((mask & RIGHT) != 0 && anchorX < 1F)
        {
            this.setWidth(MathUtils.clamp((int) ((context.mouseX - originX) / (1F - anchorX)), minWidth, maxWidth));
        }
        else if ((mask & LEFT) != 0 && anchorX > 0F)
        {
            this.setWidth(MathUtils.clamp((int) ((originX - context.mouseX) / anchorX), minWidth, maxWidth));
        }

        if ((mask & DOWN) != 0 && anchorY < 1F)
        {
            this.setHeight(MathUtils.clamp((int) ((context.mouseY - originY) / (1F - anchorY)), minHeight, maxHeight));
        }
        else if ((mask & UP) != 0 && anchorY > 0F)
        {
            this.setHeight(MathUtils.clamp((int) ((originY - context.mouseY) / anchorY), minHeight, maxHeight));
        }

        this.resized = true;

        parent.resize();
    }

    private void setWidth(int width)
    {
        this.flex.w.max = 0;
        this.flex.w.set(0F, width);
    }

    private void setHeight(int height)
    {
        this.flex.h.max = 0;
        this.flex.h.set(0F, height);
    }

    private void renderGrip(UIContext context)
    {
        int color = this.grip.area.isInside(context) ? Colors.LIGHTEST_GRAY : Colors.GRAY;
        int x = this.grip.area.x + 1;
        int y = this.grip.area.y + 1;

        /* The lower right triangle of a 3x3 dot grid, i.e. three diagonal ticks */
        for (int row = 0; row < 3; row ++)
        {
            for (int column = 2 - row; column < 3; column ++)
            {
                context.batcher.box(x + column * 3, y + row * 3, x + column * 3 + 2, y + row * 3 + 2, color);
            }
        }
    }

    public void setInitialOffset(int x, int y)
    {
        this.initialOffsetX = x;
        this.initialOffsetY = y;
    }

    public void onClose(Consumer<UIOverlayCloseEvent> callback)
    {
        this.events.register(UIOverlayCloseEvent.class, callback);
    }

    public void close()
    {
        UIElement parent = this.getParent();

        if (parent instanceof UIOverlay)
        {
            ((UIOverlay) parent).closeItself();
        }
    }

    public void confirm()
    {}

    /**
     * The width this panel asks for when it is opened without one; a negative value leaves the
     * overlay's own default in charge.
     */
    public int getPreferredWidth()
    {
        return -1;
    }

    /**
     * The height this panel wants once its children are laid out, or -1 to keep whatever height it
     * was opened with. A panel that knows what is inside it &mdash; a message with one block under
     * it &mdash; returns the sum, so a two line question doesn't get a half screen box with the
     * button parked at the bottom of it.
     */
    public int getContentHeight()
    {
        return -1;
    }

    @Override
    public void resize()
    {
        /* The strip on the right is there only for panels that put icons into it; the close button
         * lives in the title bar now, so an empty strip would be a blank band */
        this.content.w(1F, this.icons.getChildren().isEmpty() ? 0 : -20);

        super.resize();
        this.fitInsideParent();

        int height = this.getContentHeight();

        /* The pass above laid the children out, so their heights are known now: take the sum and
         * lay them out again against it. One extra pass settles it &mdash; the width doesn't change
         * with the height, and neither does the text wrap the sum is made of. */
        if (this.sizeless && height > 0 && height != this.area.h)
        {
            /* The sum counted everything that has to fit, so the screen share's cap no longer
             * applies: capping it here would cut the block off the bottom of a long message */
            this.flex.h.max = 0;
            this.flex.h.set(0F, height);

            super.resize();
        }
    }

    /**
     * A panel larger than the screen it opens on - a fixed size meant for a bigger window, a size
     * remembered from one, or a larger GUI scale - is shrunk to fit, so its corner, and the grip in
     * it, is never pushed off screen.
     */
    private void fitInsideParent()
    {
        UIElement parent = this.getParent();

        if (parent == null || parent.area.w <= 0 || parent.area.h <= 0)
        {
            return;
        }

        boolean changed = false;

        if (this.area.w > parent.area.w)
        {
            this.flex.w.max = parent.area.w;
            changed = true;
        }

        if (this.area.h > parent.area.h)
        {
            this.flex.h.max = parent.area.h;
            changed = true;
        }

        if (changed)
        {
            super.resize();
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseY < this.area.y + TITLE_HEIGHT && !this.close.area.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.flex.x.offset = this.initialOffsetX;
                this.flex.y.offset = this.initialOffsetY;

                this.getParent().resize();

                return true;
            }

            this.moving = true;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.moving = super.subMouseReleased(context);

        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (!context.isFocused() && context.isPressed(Keys.CLOSE))
        {
            this.close();

            return true;
        }

        /* Enter is not gated on focus, unlike the closing escape: children are offered the key
         * first, so whoever spends it on itself (a text area, a list) has already taken it, and
         * what reaches the panel is the Enter of someone done filling the dialog in */
        if (context.isPressed(Keys.CONFIRM) || context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
        {
            this.confirm();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.moving && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;
            int lastX = this.area.x;
            int lastY = this.area.y;

            this.flex.x.offset += dx;
            this.flex.y.offset += dy;

            this.getParent().resize();

            if (lastX == this.area.x) this.flex.x.offset -= dx;
            if (lastY == this.area.y) this.flex.y.offset -= dy;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        this.renderBackground(context);

        super.render(context);

        GlassChrome.frame(context, this.area, true);
        GlassChrome.flash(context, this.area, this.flashIntensity());
    }

    protected void renderBackground(UIContext context)
    {
        GlassChrome.paint(context, this.area, TITLE_HEIGHT, this.area.ey() - this.footerHeight, true);

        if (!this.icons.getChildren().isEmpty())
        {
            context.batcher.box(this.icons.area.x, this.icons.area.y, this.icons.area.ex() - 1, this.icons.area.ey() - 1, 0x14ffffff);
        }

        if (this.close.area.isInside(context))
        {
            /* flush with the panel's corner, so only that corner is round */
            RoundedDraw.band(context.batcher, this.close.area.x, this.close.area.y, this.close.area.ex(), this.close.area.ey(), GlassChrome.RADIUS - 1, RoundedDraw.TR, this.close.area.y, this.close.area.ey(), UIDesktopWindow.CLOSE_HOVER);
        }

        if (this.icon != null)
        {
            context.batcher.icon(this.icon, Colors.setA(Colors.WHITE, 0.9F), this.area.x + 8, this.area.y + 4);
        }
    }

    public void onClose()
    {
        this.events.emit(new UIOverlayCloseEvent(this));
    }
}
