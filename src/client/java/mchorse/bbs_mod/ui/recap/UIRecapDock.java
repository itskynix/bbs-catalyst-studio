package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * One panel of the recap editor: a header with its icon and title, and its content below. The
 * header is also its handle: dragged, the panel is moved, and dropped it takes its new place (see
 * {@link UIRecapEditor#dropDock}).
 */
public class UIRecapDock extends UIElement
{
    public static final int HEADER = 18;

    public final String id;
    private final IKey title;
    private final Icon icon;
    private final UIRecapEditor editor;
    public final UIElement content = new UIElement();

    private boolean pressed;
    private int pressX;
    private int pressY;

    public UIRecapDock(UIRecapEditor editor, String id, IKey title, Icon icon)
    {
        this.editor = editor;
        this.id = id;
        this.title = title;
        this.icon = icon;

        this.content.relative(this).x(1).y(HEADER + 1).w(1F, -2).h(1F, -HEADER - 2);
        this.add(this.content);
    }

    public boolean isHeader(UIContext context)
    {
        return this.area.isInside(context) && context.mouseY < this.area.y + HEADER;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.isHeader(context))
        {
            this.pressed = true;
            this.pressX = context.mouseX;
            this.pressY = context.mouseY;

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.pressed)
        {
            this.pressed = false;

            if (this.editor.getDragged() == this)
            {
                this.editor.dropDock(this, context.mouseX, context.mouseY);
            }

            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        /* Moving a few pixels with the header held starts the drag, so a click alone does nothing */
        if (this.pressed && this.editor.getDragged() != this && Math.abs(context.mouseX - this.pressX) + Math.abs(context.mouseY - this.pressY) > 6)
        {
            this.editor.startDrag(this);
        }

        boolean front = this.editor.getDragged() == this || this.area.isInside(context);

        RecapStyle.surface(context.batcher, this.area, HEADER, front);

        int color = RecapStyle.header();
        boolean shadow = RecapStyle.headerShadow();

        context.batcher.icon(this.icon, color, this.area.x + 5, this.area.y + HEADER / 2F, 0F, 0.5F);
        context.batcher.text(context.batcher.getFont().limitToWidth(this.title.get(), this.area.w - 44), this.area.x + 23, this.area.y + (HEADER - 8) / 2F, color, shadow);

        if (this.isHeader(context))
        {
            context.batcher.icon(Icons.ALL_DIRECTIONS, RecapStyle.light() ? 0xccffffff : 0x90ffffff, this.area.ex() - 5, this.area.y + HEADER / 2F, 1F, 0.5F);
        }

        super.render(context);
    }
}
