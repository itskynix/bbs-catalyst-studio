package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * The viewport panel's content: the stage, a sixteen by nine frame as large as fits, and right under
 * it the transport, as wide as the frame: the cursor's time on its left, previous marker, play and
 * next marker in the middle, adding and removing a marker after them, the length on its right.
 */
public class UIRecapViewport extends UIElement
{
    private static final int ROW = 24;
    private static final int BUTTON = 22;
    private static final int GAP = 3;

    private final UIRecapEditor editor;
    private final UIRecapButton previous;
    private final UIRecapButton play;
    private final UIRecapButton next;
    private final UIRecapButton add;
    private final UIRecapButton remove;

    /* Where the frame was put, for the row under it */
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;

    public UIRecapViewport(UIRecapEditor editor)
    {
        this.editor = editor;

        this.previous = new UIRecapButton(() -> Icons.FRAME_PREV, UIRecapKeys.key("editor.previous_marker"), () -> editor.jump(false));
        this.play = new UIRecapButton(() -> editor.isPlaying() ? Icons.PAUSE : Icons.PLAY, UIRecapKeys.key("editor.play"), editor::togglePlayback).primary(() -> true);
        this.next = new UIRecapButton(() -> Icons.FRAME_NEXT, UIRecapKeys.key("editor.next_marker"), () -> editor.jump(true));
        this.add = new UIRecapButton(() -> Icons.BOOKMARK, UIRecapKeys.key("editor.add_marker"), editor::addMarker);
        this.remove = new UIRecapButton(() -> Icons.TRASH, UIRecapKeys.key("editor.remove_marker"), editor::removeMarker);

        this.add(this.previous, this.play, this.next, this.add, this.remove);
    }

    @Override
    public void resize()
    {
        int width = Math.max(10, this.area.w - 16);
        int height = Math.max(10, this.area.h - ROW - 16);

        this.frameW = Math.min(width, height * 16 / 9);
        this.frameH = this.frameW * 9 / 16;
        this.frameX = this.area.x + (this.area.w - this.frameW) / 2;
        this.frameY = this.area.y + 8 + (height - this.frameH) / 2;

        /* The buttons in the middle of the frame's width, on the row right under it */
        int row = this.frameY + this.frameH + (ROW - BUTTON + 4) / 2;
        int group = BUTTON * 5 + GAP * 4 + 8;
        int x = this.frameX + (this.frameW - group) / 2;

        this.place(this.previous, x, row);
        this.place(this.play, x += BUTTON + GAP, row);
        this.place(this.next, x += BUTTON + GAP, row);
        this.place(this.add, x += BUTTON + GAP + 8, row);
        this.place(this.remove, x + BUTTON + GAP, row);

        super.resize();
    }

    private void place(UIElement element, int x, int y)
    {
        element.relative(this).x(x - this.area.x).y(y - this.area.y).w(BUTTON).h(BUTTON - 4);
    }

    @Override
    public void render(UIContext context)
    {
        RecapRecord record = this.editor.getRecord();

        if (record != null && this.frameW > 20)
        {
            this.renderStage(context, record);
        }

        super.render(context);
    }

    private void renderStage(UIContext context, RecapRecord record)
    {
        FontRenderer font = context.batcher.getFont();
        int x = this.frameX;
        int y = this.frameY;
        int w = this.frameW;
        int h = this.frameH;

        RecapStyle.well(context.batcher, x - 1, y - 1, x + w + 1, y + h + 1);
        context.batcher.box(x, y, x + w, y + h, 0xff000000);

        List<String> lines = font.wrap(UIRecapKeys.text("editor.stage"), Math.max(40, w - 30));
        int ty = y + h / 2 - lines.size() * (font.getHeight() + 3) / 2;

        for (String line : lines)
        {
            context.batcher.text(line, x + (w - font.getWidth(line)) / 2, ty, 0x73ffffff, false);
            ty += font.getHeight() + 3;
        }

        if (record.isOn(RecapOption.THIRDS_GUIDE))
        {
            for (int i = 1; i <= 2; i++)
            {
                context.batcher.box(x + w * i / 3, y, x + w * i / 3 + 1, y + h, 0x60ffffff);
                context.batcher.box(x, y + h * i / 3, x + w, y + h * i / 3 + 1, 0x60ffffff);
            }
        }

        /* The time on the row under the frame: the cursor at its left, the length at its right */
        int rowY = y + h + (ROW - 8) / 2 + 2;
        String cursor = RecapTime.formatWithTicks(this.editor.getCursor());
        String length = RecapTime.formatWithTicks(this.editor.getLength());

        context.batcher.text(cursor, x, rowY, RecapStyle.text(), false);
        context.batcher.text(length, x + w - font.getWidth(length), rowY, RecapStyle.dim(), false);
    }
}
