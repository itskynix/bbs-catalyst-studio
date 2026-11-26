package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapStore;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The recap editor: a bar of controls on top, the stage in the middle, the timeline with its marked
 * moments below, and what is shown and overridden on the right.
 *
 * <p>The cursor is a tick and playing it advances one tick per game tick, so the editor is on the
 * same moment on every machine. The stage itself is not drawn yet: it shows the cursor and the
 * rule of thirds guide.</p>
 */
public class UIRecapEditor extends UIElement
{
    private static final int TOP = 24;
    private static final int SIDE = 176;
    private static final int TIMELINE = 78;

    private final UIRecapPanel panel;
    private final UIRecapTimeline timeline;
    private final UIRecapViewPanel view;
    private final UIIcon play;

    private RecapRecord record;
    private int cursor;
    private boolean playing;

    public UIRecapEditor(UIRecapPanel panel)
    {
        this.panel = panel;

        UIIcon back = this.button(Icons.ARROW_LEFT, "editor.back", 4, () -> this.panel.openHome());
        UIIcon rename = this.button(Icons.EDIT, "editor.rename", 26, this::rename);
        UIIcon previous = this.button(Icons.FRAME_PREV, "editor.previous_marker", -66, () -> this.jump(false));
        UIIcon next = this.button(Icons.FRAME_NEXT, "editor.next_marker", -22, () -> this.jump(true));
        UIIcon addMarker = this.button(Icons.BOOKMARK, "editor.add_marker", 4, this::addMarker);
        UIIcon removeMarker = this.button(Icons.TRASH, "editor.remove_marker", 26, this::removeMarker);

        this.play = this.button(Icons.PLAY, "editor.play", -44, this::togglePlayback);

        /* Transport in the middle of the bar, markers on its right */
        previous.relative(this).x(0.5F, -33).y(3).wh(18, 18);
        this.play.relative(this).x(0.5F, -9).y(3).wh(18, 18);
        next.relative(this).x(0.5F, 15).y(3).wh(18, 18);
        addMarker.relative(this).x(1F, -SIDE - 48).y(3).wh(18, 18);
        removeMarker.relative(this).x(1F, -SIDE - 26).y(3).wh(18, 18);

        this.view = new UIRecapViewPanel(this::save);
        this.view.relative(this).x(1F, -SIDE).y(TOP).w(SIDE).h(1F, -TOP);

        this.timeline = new UIRecapTimeline(this);
        this.timeline.relative(this).x(0).y(1F, -TIMELINE).w(1F, -SIDE).h(TIMELINE);

        this.add(back, rename, previous, this.play, next, addMarker, removeMarker, this.timeline, this.view);
    }

    private UIIcon button(Icon icon, String tooltip, int x, Runnable action)
    {
        UIIcon button = new UIIcon(icon, (b) -> action.run());

        button.tooltip(UIRecapKeys.key(tooltip));
        button.relative(this).x(x).y(3).wh(18, 18);

        return button;
    }

    public RecapRecord getRecord()
    {
        return this.record;
    }

    public int getCursor()
    {
        return this.cursor;
    }

    public int getLength()
    {
        return this.record == null ? 0 : this.record.ticks;
    }

    public void open(RecapRecord record)
    {
        this.record = record;
        this.cursor = 0;
        this.stopPlayback();
        this.view.fill(record);
        this.view.scroll.setScroll(0);
    }

    public void seek(int tick)
    {
        this.cursor = Math.max(0, Math.min(this.getLength(), tick));
    }

    private void togglePlayback()
    {
        if (this.record == null)
        {
            return;
        }

        if (this.playing)
        {
            this.stopPlayback();

            return;
        }

        if (this.cursor >= this.getLength())
        {
            this.cursor = 0;
        }

        this.playing = true;
        this.play.both(Icons.PAUSE);
    }

    public void stopPlayback()
    {
        this.playing = false;

        if (this.play != null)
        {
            this.play.both(Icons.PLAY);
        }
    }

    public void tick()
    {
        if (!this.playing)
        {
            return;
        }

        this.cursor += 1;

        if (this.cursor >= this.getLength())
        {
            this.cursor = this.getLength();
            this.stopPlayback();
        }
    }

    private void jump(boolean forward)
    {
        if (this.record == null)
        {
            return;
        }

        List<Integer> markers = this.record.markers;

        if (forward)
        {
            for (int marker : markers)
            {
                if (marker > this.cursor)
                {
                    this.seek(marker);

                    return;
                }
            }

            this.seek(this.getLength());

            return;
        }

        for (int i = markers.size() - 1; i >= 0; i--)
        {
            if (markers.get(i) < this.cursor)
            {
                this.seek(markers.get(i));

                return;
            }
        }

        this.seek(0);
    }

    private void addMarker()
    {
        if (this.record != null)
        {
            this.record.addMarker(this.cursor);
            this.save();
        }
    }

    private void removeMarker()
    {
        if (this.record != null && this.record.markers.remove(Integer.valueOf(this.cursor)))
        {
            this.save();
        }
    }

    private void rename()
    {
        if (this.record == null)
        {
            return;
        }

        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(UIRecapKeys.key("home.rename"), UIRecapKeys.key("home.rename_message"), (name) ->
        {
            if (!name.trim().isEmpty())
            {
                this.record.name = name.trim();
                this.save();
            }
        });

        prompt.text.setText(this.record.name);
        UIOverlay.addOverlay(this.getContext(), prompt);
    }

    private void save()
    {
        if (this.record != null && !RecapStore.save(this.record))
        {
            this.getContext().notifyError(UIRecapKeys.key("message.failed"));
        }
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyAction() == KeyAction.PRESSED && context.getKeyCode() == GLFW.GLFW_KEY_SPACE && !context.isFocused())
        {
            this.togglePlayback();

            return true;
        }

        return super.subKeyPressed(context);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff0d1016);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + TOP, BBSSettings.deepSurface());
        context.batcher.box(this.area.x, this.area.y + TOP - 1, this.area.ex(), this.area.y + TOP, BBSSettings.dividerColor());
        context.batcher.box(this.view.area.x, this.view.area.y, this.view.area.ex(), this.view.area.ey(), BBSSettings.deepSurface());
        context.batcher.box(this.view.area.x, this.view.area.y, this.view.area.x + 1, this.view.area.ey(), BBSSettings.dividerColor());

        if (this.record != null)
        {
            FontRenderer font = context.batcher.getFont();
            int x = this.area.x + 52;
            int width = Math.max(30, this.area.w / 2 - 100);

            context.batcher.text(font.limitToWidth(this.record.name, width), x, this.area.y + 8, Colors.WHITE, false);
            this.renderStage(context, font);
        }

        super.render(context);
    }

    private void renderStage(UIContext context, FontRenderer font)
    {
        Area a = this.area;
        int left = a.x + 12;
        int top = a.y + TOP + 12;
        int right = a.ex() - SIDE - 12;
        int bottom = a.ey() - TIMELINE - 20;
        int width = right - left;
        int height = bottom - top;

        if (width < 40 || height < 30)
        {
            return;
        }

        /* A sixteen by nine frame inside the space there is */
        int w = Math.min(width, height * 16 / 9);
        int h = w * 9 / 16;
        int x = left + (width - w) / 2;
        int y = top + (height - h) / 2;

        context.batcher.box(x, y, x + w, y + h, 0xff000000);
        context.batcher.outline(x, y, x + w, y + h, 0x40ffffff);

        List<String> lines = font.wrap(UIRecapKeys.text("editor.stage"), Math.max(40, w - 30));
        int ty = y + h / 2 - lines.size() * (font.getHeight() + 3) / 2;

        for (String line : lines)
        {
            context.batcher.text(line, x + (w - font.getWidth(line)) / 2, ty, Colors.setA(Colors.WHITE, 0.45F), false);
            ty += font.getHeight() + 3;
        }

        if (this.record.isOn(RecapOption.THIRDS_GUIDE))
        {
            for (int i = 1; i <= 2; i++)
            {
                context.batcher.box(x + w * i / 3, y, x + w * i / 3 + 1, y + h, 0x60ffffff);
                context.batcher.box(x, y + h * i / 3, x + w, y + h * i / 3 + 1, 0x60ffffff);
            }
        }

        String time = RecapTime.formatWithTicks(this.cursor) + "  /  " + RecapTime.formatWithTicks(this.getLength());

        context.batcher.text(time, x + (w - font.getWidth(time)) / 2, y + h + 5, Colors.WHITE, false);
    }
}
