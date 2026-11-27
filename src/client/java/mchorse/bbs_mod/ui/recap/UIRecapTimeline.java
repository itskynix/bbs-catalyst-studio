package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The recap's timeline: a ruler, the marked moments as flags and the cursor. A click or a drag
 * on the ruler moves the cursor, a click on a flag jumps onto its moment, and the body under them
 * does not scrub; the cursor is a tick, so what it points at never depends on the frame rate.
 */
public class UIRecapTimeline extends UIElement
{
    private static final int SIDE = 10;
    private static final int RULER = 16;
    private static final int FLAG_W = 7;

    private final UIRecapEditor editor;
    private boolean dragging;

    public UIRecapTimeline(UIRecapEditor editor)
    {
        this.editor = editor;
    }

    private int left()
    {
        return this.area.x + SIDE;
    }

    private int width()
    {
        return Math.max(1, this.area.w - SIDE * 2);
    }

    private int toX(int tick)
    {
        int length = this.editor.getLength();

        return this.left() + (length <= 0 ? 0 : (int) ((long) tick * (this.width() - 1) / length));
    }

    private int toTick(int x)
    {
        int length = this.editor.getLength();
        int at = Math.max(0, Math.min(this.width() - 1, x - this.left()));

        return this.width() <= 1 ? 0 : (int) Math.round((double) at * length / (this.width() - 1));
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null || context.mouseButton != 0 || !this.area.isInside(context))
        {
            return false;
        }

        /* A flag clicked jumps exactly onto its moment */
        if (context.mouseY >= this.area.y + RULER && context.mouseY < this.area.y + RULER + 18)
        {
            for (int marker : record.markers)
            {
                int x = this.toX(marker);

                if (context.mouseX >= x - 1 && context.mouseX < x + FLAG_W)
                {
                    this.editor.seek(marker);

                    return true;
                }
            }
        }

        /* Only the ruler scrubs: a click on the body under it moves nothing */
        if (!this.isOnRuler(context))
        {
            return false;
        }

        this.dragging = true;
        this.editor.seek(this.toTick(context.mouseX));

        return true;
    }

    /** The band along the top with the time marks, the one place the cursor is dragged from. */
    private boolean isOnRuler(UIContext context)
    {
        return this.area.isInside(context) && context.mouseY < this.area.y + RULER;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.dragging = false;

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        RecapRecord record = this.editor.getRecord();

        RecapStyle.well(context.batcher, this.area.x + 4, this.area.y + 2, this.area.ex() - 4, this.area.ey() - 2);

        /* The ruler band, lit under the pointer or while dragged, so it reads as the scrub handle */
        boolean ruler = this.dragging || this.isOnRuler(context);
        int band = RecapStyle.light() ? (ruler ? 0x502b7ab8 : 0x302b7ab8) : (ruler ? 0x26ffffff : 0x12ffffff);

        context.batcher.box(this.area.x + 5, this.area.y + 3, this.area.ex() - 5, this.area.y + RULER, band);
        context.batcher.box(this.area.x + 5, this.area.y + RULER, this.area.ex() - 5, this.area.y + RULER + 1, RecapStyle.divider());

        if (record == null)
        {
            super.render(context);

            return;
        }

        if (this.dragging)
        {
            this.editor.seek(this.toTick(context.mouseX));
        }

        FontRenderer font = context.batcher.getFont();
        int accent = RecapStyle.accent();
        boolean light = RecapStyle.light();
        int length = this.editor.getLength();
        int top = this.area.y + RULER;
        int bottom = this.area.ey() - 6;

        this.renderRuler(context, font, length, top, bottom);

        /* The recorded span */
        context.batcher.box(this.left(), top + 22, this.left() + this.width(), bottom, light ? 0x302b7ab8 : 0xff1b212e);
        context.batcher.box(this.left(), top + 22, this.left() + this.width(), top + 24, Colors.setA(accent, 0.7F));

        for (int marker : record.markers)
        {
            int x = this.toX(marker);
            int color = marker == this.editor.getCursor() ? (light ? RecapStyle.text() : Colors.WHITE) : RecapStyle.marker();

            context.batcher.box(x, top + 4, x + 1, bottom, Colors.setA(color, 0.6F));
            context.batcher.box(x + 1, top + 4, x + FLAG_W, top + 11, color);
        }

        int cursor = this.toX(this.editor.getCursor());

        int red = RecapStyle.red();

        context.batcher.box(cursor, this.area.y + 2, cursor + 1, bottom, red);
        context.batcher.box(cursor - 3, this.area.y + 2, cursor + 4, this.area.y + 6, red);

        super.render(context);
    }

    /** Time marks, spaced so the labels never touch. */
    private void renderRuler(UIContext context, FontRenderer font, int length, int top, int bottom)
    {
        if (length <= 0)
        {
            return;
        }

        int[] steps = {1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600};
        int step = steps[steps.length - 1];

        for (int candidate : steps)
        {
            if ((long) candidate * 20 * this.width() / length >= 48)
            {
                step = candidate;

                break;
            }
        }

        for (int tick = 0; tick <= length; tick += step * 20)
        {
            int x = this.toX(tick);
            String label = RecapTime.format(tick);

            context.batcher.box(x, top - 4, x + 1, bottom, RecapStyle.light() ? 0x200f2742 : 0x18ffffff);

            if (x + font.getWidth(label) < this.area.ex())
            {
                context.batcher.text(label, x + 2, this.area.y + 4, RecapStyle.dim(), false);
            }
        }
    }
}
