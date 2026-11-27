package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapStore;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

/**
 * The recap editor, laid out like a studio: a slim bar on top, and four panels under it — the
 * viewport with its transport right below the frame, the timeline, the record and the view
 * settings — each with its title on a header.
 *
 * <p><b>Panels move and resize.</b> The gaps between them are handles that resize them; a panel's
 * header is a handle that moves it: the side column to the left or the right, the timeline above or
 * below, the record above or below the view settings. Ready made layouts and the author's own saved
 * ones are a click away in the layout menu, and the layout in use is kept across sessions
 * ({@link RecapLayout}).</p>
 *
 * <p><b>History.</b> Every edit, to a view setting, a marker, the name or the layout, is a step
 * of {@link RecapHistory}: undo and redo on the top bar and with Ctrl Z and Ctrl Y, and the history
 * menu goes back to any step at once.</p>
 *
 * <p>The cursor is a tick, and playing advances it a tick per game tick, so the editor is on the same
 * moment on every machine. The stage is not drawn yet: it shows the frame and the thirds guide.</p>
 */
public class UIRecapEditor extends UIElement
{
    private static final int TOP = 26;
    private static final int MARGIN = 6;
    private static final int GAP = 6;

    private final UIRecapPanel panel;
    private final RecapLayout.Store layouts = RecapLayout.Store.load();
    private final RecapLayout layout = this.layouts.current;
    private final RecapHistory history = new RecapHistory();

    public final UIRecapDock viewportDock;
    public final UIRecapDock timelineDock;
    public final UIRecapDock infoDock;
    public final UIRecapDock viewDock;

    private final UIRecapViewport viewport;
    private final UIRecapTimeline timeline;
    private final UIRecapInfoPanel info;
    private final UIRecapViewPanel view;

    private final UIRecapSplitter sideSplitter;
    private final UIRecapSplitter timelineSplitter;
    private final UIRecapSplitter innerSplitter;

    private RecapRecord record;
    private int cursor;
    private boolean playing;

    private UIRecapDock dragged;

    /* The areas computed by the last layout, for the splitters and the drop targets */
    private final Area body = new Area();
    private final Area side = new Area();
    private final Area main = new Area();

    public UIRecapEditor(UIRecapPanel panel)
    {
        this.panel = panel;

        UIRecapButton back = new UIRecapButton(() -> Icons.ARROW_LEFT, UIRecapKeys.key("editor.back"), () -> this.panel.openHome());
        UIRecapButton rename = new UIRecapButton(() -> Icons.EDIT, UIRecapKeys.key("editor.rename"), this::rename);
        UIRecapButton layouts = new UIRecapButton(() -> Icons.LAYOUT, UIRecapKeys.key("layout.menu"), this::openLayoutMenu);
        UIRecapButton undo = new UIRecapButton(() -> Icons.UNDO, UIRecapKeys.key("history.undo"), this::undo).active(this.history::canUndo);
        UIRecapButton redo = new UIRecapButton(() -> Icons.REDO, UIRecapKeys.key("history.redo"), this::redo).active(this.history::canRedo);
        UIRecapButton history = new UIRecapButton(() -> Icons.LIST, UIRecapKeys.key("history.title"), this::openHistoryMenu);

        back.relative(this).x(MARGIN).y(4).w(22).h(18);
        rename.relative(this).x(MARGIN + 26).y(4).w(22).h(18);
        layouts.relative(this).x(1F, -MARGIN - 22).y(4).w(22).h(18);
        history.relative(this).x(1F, -MARGIN - 52).y(4).w(22).h(18);
        redo.relative(this).x(1F, -MARGIN - 78).y(4).w(22).h(18);
        undo.relative(this).x(1F, -MARGIN - 104).y(4).w(22).h(18);

        this.viewport = new UIRecapViewport(this);
        this.timeline = new UIRecapTimeline(this);
        this.info = new UIRecapInfoPanel(this);
        this.view = new UIRecapViewPanel(this::viewChanged);

        this.viewportDock = this.dock("viewport", Icons.VIDEO_CAMERA, this.viewport);
        this.timelineDock = this.dock("timeline", Icons.TIME, this.timeline);
        this.infoDock = this.dock("info", Icons.FILE, this.info);
        this.viewDock = this.dock("view", Icons.VISIBLE, this.view);

        this.sideSplitter = new UIRecapSplitter(true, this::dragSide, this::saveLayout);
        this.timelineSplitter = new UIRecapSplitter(false, this::dragTimeline, this::saveLayout);
        this.innerSplitter = new UIRecapSplitter(false, this::dragInner, this::saveLayout);

        this.add(back, rename, undo, redo, history, layouts, this.viewportDock, this.timelineDock, this.infoDock, this.viewDock,
            this.sideSplitter, this.timelineSplitter, this.innerSplitter);
    }

    private UIRecapDock dock(String id, mchorse.bbs_mod.ui.utils.icons.Icon icon, UIElement content)
    {
        UIRecapDock dock = new UIRecapDock(this, id, UIRecapKeys.key("panel." + id), icon);

        content.relative(dock.content).w(1F).h(1F);
        dock.content.add(content);

        return dock;
    }

    /* State */

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

    public boolean isPlaying()
    {
        return this.playing;
    }

    public void open(RecapRecord record)
    {
        this.record = record;
        this.cursor = 0;
        this.stopPlayback();
        this.info.fill(record);
        this.view.fill(record);

        if (record != null)
        {
            this.history.reset(UIRecapKeys.text("history.opened"), record, this.layout);
        }
    }

    public void seek(int tick)
    {
        this.cursor = Math.max(0, Math.min(this.getLength(), tick));
    }

    public void togglePlayback()
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
    }

    public void stopPlayback()
    {
        this.playing = false;
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

    public void jump(boolean forward)
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

    public void addMarker()
    {
        if (this.record != null)
        {
            this.record.addMarker(this.cursor);
            this.save();
            this.info.fill(this.record);
            this.remember(UIRecapKeys.text("history.marker_add") + " " + RecapTime.format(this.cursor), null);
        }
    }

    public void removeMarker()
    {
        if (this.record != null && this.record.markers.remove(Integer.valueOf(this.cursor)))
        {
            this.save();
            this.info.fill(this.record);
            this.remember(UIRecapKeys.text("history.marker_remove") + " " + RecapTime.format(this.cursor), null);
        }
    }

    /** A view setting changed: written into the recap, and one step per setting in a row. */
    private void viewChanged(RecapOption option)
    {
        this.save();
        this.remember(UIRecapKeys.text("history.view") + ": " + UIRecapKeys.text("view." + option.id), "view." + option.id);
    }

    /* History */

    /** Adds what is on screen now to the history, after an edit. */
    private void remember(String label, String merge)
    {
        if (this.record != null)
        {
            this.history.push(label, merge, this.record, this.layout);
        }
    }

    public void undo()
    {
        this.apply(this.history.undo());
    }

    public void redo()
    {
        this.apply(this.history.redo());
    }

    /** Puts a state of the history back on screen: the recap's editable side and the layout. */
    private void apply(RecapHistory.State state)
    {
        if (state == null || this.record == null)
        {
            return;
        }

        this.record.setEditable(state.record());
        this.layout.set(state.layout());
        this.save();
        this.layouts.save();
        this.info.fill(this.record);
        this.view.fill(this.record);
        this.resize();
    }

    /** Every step since the recap was opened, newest on top; the one on screen is ticked. */
    private void openHistoryMenu()
    {
        List<RecapHistory.State> states = this.history.getStates();
        int current = this.history.getIndex();

        this.getContext().replaceContextMenu((menu) ->
        {
            for (int i = states.size() - 1; i >= 0; i--)
            {
                int index = i;
                String label = (i + 1) + ". " + states.get(i).label();

                menu.action(i == current ? Icons.CHECKMARK : (i > current ? Icons.REDO : Icons.TIME), IKey.constant(label), () -> this.apply(this.history.jump(index)));
            }
        });
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
                this.remember(UIRecapKeys.text("history.rename"), null);
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

    /* Layout */

    @Override
    public void resize()
    {
        this.applyLayout();
        super.resize();
    }

    /** Places every panel and gap after the layout. */
    private void applyLayout()
    {
        RecapLayout l = this.layout;
        int x0 = this.area.x + MARGIN;
        int y0 = this.area.y + TOP;
        int x1 = this.area.ex() - MARGIN;
        int y1 = this.area.ey() - MARGIN;
        int width = Math.max(1, x1 - x0);
        int height = Math.max(1, y1 - y0);
        int sw = Math.max(RecapLayout.MIN_SIDE, Math.min(width / 2, l.sideWidth));
        int th = Math.max(RecapLayout.MIN_TIMELINE, Math.min(height * 3 / 5, l.timelineHeight));

        this.body.set(x0, y0, width, height);

        int bx0 = x0, by0 = y0, bx1 = x1, by1 = y1;
        int[] tl = null;

        if (l.timelineFull)
        {
            tl = l.timelineTop ? new int[] {x0, y0, x1, y0 + th} : new int[] {x0, y1 - th, x1, y1};
            by0 = l.timelineTop ? y0 + th + GAP : y0;
            by1 = l.timelineTop ? y1 : y1 - th - GAP;
        }

        int[] sideRect = l.sideLeft ? new int[] {bx0, by0, bx0 + sw, by1} : new int[] {bx1 - sw, by0, bx1, by1};
        int[] mainRect = l.sideLeft ? new int[] {bx0 + sw + GAP, by0, bx1, by1} : new int[] {bx0, by0, bx1 - sw - GAP, by1};

        if (!l.timelineFull)
        {
            tl = l.timelineTop ? new int[] {mainRect[0], mainRect[1], mainRect[2], mainRect[1] + th} : new int[] {mainRect[0], mainRect[3] - th, mainRect[2], mainRect[3]};
            mainRect = l.timelineTop ? new int[] {mainRect[0], mainRect[1] + th + GAP, mainRect[2], mainRect[3]} : new int[] {mainRect[0], mainRect[1], mainRect[2], mainRect[3] - th - GAP};
        }

        this.side.set(sideRect[0], sideRect[1], sideRect[2] - sideRect[0], sideRect[3] - sideRect[1]);
        this.main.set(mainRect[0], mainRect[1], mainRect[2] - mainRect[0], mainRect[3] - mainRect[1]);

        this.place(this.viewportDock, mainRect);
        this.place(this.timelineDock, tl);

        /* The side column: the record and the view settings, in their order and share */
        if (l.infoVisible)
        {
            int share = (int) ((sideRect[3] - sideRect[1] - GAP) * l.infoRatio);
            int split = sideRect[1] + share;
            int[] upper = {sideRect[0], sideRect[1], sideRect[2], split};
            int[] lower = {sideRect[0], split + GAP, sideRect[2], sideRect[3]};

            this.place(l.infoFirst ? this.infoDock : this.viewDock, upper);
            this.place(l.infoFirst ? this.viewDock : this.infoDock, lower);
            this.infoDock.setVisible(true);
            this.innerSplitter.setVisible(true);
            this.place(this.innerSplitter, new int[] {sideRect[0], split, sideRect[2], split + GAP});
        }
        else
        {
            this.place(this.viewDock, sideRect);
            this.infoDock.setVisible(false);
            this.innerSplitter.setVisible(false);
        }

        /* The gaps that resize */
        int gapX = l.sideLeft ? sideRect[2] : sideRect[0] - GAP;

        this.place(this.sideSplitter, new int[] {gapX, sideRect[1], gapX + GAP, sideRect[3]});

        int gapY = l.timelineTop ? tl[3] : tl[1] - GAP;

        this.place(this.timelineSplitter, new int[] {tl[0], gapY, tl[2], gapY + GAP});
    }

    private void place(UIElement element, int[] r)
    {
        element.relative(this).x(r[0] - this.area.x).y(r[1] - this.area.y).w(Math.max(1, r[2] - r[0])).h(Math.max(1, r[3] - r[1]));
    }

    private void dragSide(int mouseX)
    {
        int width = this.layout.sideLeft ? mouseX - this.body.x - GAP / 2 : this.body.ex() - mouseX - GAP / 2;

        this.layout.sideWidth = Math.max(RecapLayout.MIN_SIDE, Math.min(this.body.w / 2, width));
        this.resize();
    }

    private void dragTimeline(int mouseY)
    {
        int top = this.layout.timelineFull ? this.body.y : this.main.y;
        int bottom = this.layout.timelineFull ? this.body.ey() : this.main.ey();
        int height = this.layout.timelineTop ? mouseY - top - GAP / 2 : bottom - mouseY - GAP / 2;

        this.layout.timelineHeight = Math.max(RecapLayout.MIN_TIMELINE, Math.min(this.body.h * 3 / 5, height));
        this.resize();
    }

    private void dragInner(int mouseY)
    {
        float ratio = (mouseY - this.side.y) / (float) Math.max(1, this.side.h);

        if (!this.layout.infoFirst)
        {
            ratio = 1F - ratio;
        }

        this.layout.infoRatio = Math.max(0.15F, Math.min(0.85F, ratio));
        this.resize();
    }

    private void saveLayout()
    {
        this.layouts.save();
        this.remember(UIRecapKeys.text("history.layout"), null);
    }

    /* Moving panels */

    public UIRecapDock getDragged()
    {
        return this.dragged;
    }

    public void startDrag(UIRecapDock dock)
    {
        this.dragged = dock;
    }

    /** A panel let go: where it lands decides the layout. */
    public void dropDock(UIRecapDock dock, int mouseX, int mouseY)
    {
        this.dragged = null;

        if (!this.area.isInside(mouseX, mouseY))
        {
            return;
        }

        RecapLayout l = this.layout;
        boolean left = mouseX < this.body.mx();

        if (dock == this.timelineDock)
        {
            l.timelineTop = mouseY < this.body.my();
        }
        else if (dock == this.viewportDock)
        {
            /* The viewport goes where it was dropped, so the side column goes to the other side */
            l.sideLeft = !left;
        }
        else
        {
            UIRecapDock other = dock == this.infoDock ? this.viewDock : this.infoDock;

            if (l.infoVisible && other.isVisible() && other.area.isInside(mouseX, mouseY))
            {
                l.infoFirst = !l.infoFirst;
            }
            else
            {
                l.sideLeft = left;
            }
        }

        this.resize();
        this.saveLayout();
    }

    /** Where a dragged panel would land under the pointer, to draw it before it is let go. */
    private Area dropTarget(int mouseX, int mouseY)
    {
        Area target = new Area();
        boolean left = mouseX < this.body.mx();

        if (this.dragged == this.timelineDock)
        {
            boolean top = mouseY < this.body.my();

            target.set(this.body.x, top ? this.body.y : this.body.ey() - this.timelineDock.area.h, this.body.w, this.timelineDock.area.h);
        }
        else if (this.dragged == this.viewportDock)
        {
            int w = this.main.w;

            target.set(left ? this.body.x : this.body.ex() - w, this.main.y, w, this.main.h);
        }
        else
        {
            UIRecapDock other = this.dragged == this.infoDock ? this.viewDock : this.infoDock;

            if (this.layout.infoVisible && other.isVisible() && other.area.isInside(mouseX, mouseY))
            {
                target.copy(other.area);
            }
            else
            {
                target.set(left ? this.body.x : this.body.ex() - this.side.w, this.side.y, this.side.w, this.side.h);
            }
        }

        return target;
    }

    /* Layout templates */

    private void openLayoutMenu()
    {
        UIContext context = this.getContext();

        context.replaceContextMenu((menu) ->
        {
            for (RecapLayout.Preset preset : RecapLayout.presets())
            {
                menu.action(this.layout.same(preset.layout()) ? Icons.CHECKMARK : Icons.LAYOUT, UIRecapKeys.key("layout.preset." + preset.key()), () -> this.useLayout(preset.layout()));
            }

            for (Map.Entry<String, RecapLayout> entry : this.layouts.templates.entrySet())
            {
                menu.action(this.layout.same(entry.getValue()) ? Icons.CHECKMARK : Icons.FAVORITE, IKey.constant(entry.getKey()), () -> this.useLayout(entry.getValue()));
            }

            menu.action(Icons.SAVE, UIRecapKeys.key("layout.save"), this::saveTemplate);

            if (!this.layouts.templates.isEmpty())
            {
                menu.action(Icons.REMOVE, UIRecapKeys.key("layout.delete"), this::openDeleteMenu);
            }

            menu.action(this.layout.sideLeft ? Icons.ARROW_RIGHT : Icons.ARROW_LEFT, UIRecapKeys.key(this.layout.sideLeft ? "layout.side_right" : "layout.side_left"), () -> this.change(() -> this.layout.sideLeft = !this.layout.sideLeft));
            menu.action(this.layout.timelineTop ? Icons.ARROW_DOWN : Icons.ARROW_UP, UIRecapKeys.key(this.layout.timelineTop ? "layout.timeline_bottom" : "layout.timeline_top"), () -> this.change(() -> this.layout.timelineTop = !this.layout.timelineTop));
            menu.action(this.layout.timelineFull ? Icons.CHECKMARK : Icons.HORIZONTAL, UIRecapKeys.key("layout.timeline_full"), () -> this.change(() -> this.layout.timelineFull = !this.layout.timelineFull));
            menu.action(this.layout.infoVisible ? Icons.CHECKMARK : Icons.FILE, UIRecapKeys.key("layout.info_visible"), () -> this.change(() -> this.layout.infoVisible = !this.layout.infoVisible));
        });
    }

    private void openDeleteMenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            for (String name : this.layouts.templates.keySet())
            {
                menu.action(Icons.REMOVE, IKey.constant(name), () ->
                {
                    this.layouts.templates.remove(name);
                    this.saveLayout();
                });
            }
        });
    }

    private void change(Runnable change)
    {
        change.run();
        this.resize();
        this.saveLayout();
    }

    private void useLayout(RecapLayout layout)
    {
        this.change(() -> this.layout.set(layout));
    }

    private void saveTemplate()
    {
        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(UIRecapKeys.key("layout.save"), UIRecapKeys.key("layout.save_message"), (name) ->
        {
            String trimmed = name.trim();

            if (!trimmed.isEmpty())
            {
                this.layouts.templates.put(trimmed, this.layout.copy());
                this.saveLayout();
                this.getContext().notifySuccess(UIRecapKeys.key("layout.saved"));
            }
        });

        UIOverlay.addOverlay(this.getContext(), prompt);
    }

    /* Input */

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyAction() == KeyAction.PRESSED && context.getKeyCode() == GLFW.GLFW_KEY_SPACE && !context.isFocused())
        {
            this.togglePlayback();

            return true;
        }

        /* Ctrl Z steps back, Ctrl Y or Ctrl Shift Z forward; a focused text field keeps its own */
        if (context.getKeyAction() != KeyAction.RELEASED && !context.isFocused() && Window.isCtrlPressed())
        {
            int key = context.getKeyCode();

            if (key == GLFW.GLFW_KEY_Z && !Window.isShiftPressed())
            {
                this.undo();

                return true;
            }

            if (key == GLFW.GLFW_KEY_Y || (key == GLFW.GLFW_KEY_Z && Window.isShiftPressed()))
            {
                this.redo();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        RecapStyle.backdrop(context.batcher, this.area);
        RecapStyle.surface(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.y + TOP - 4, 0, false);

        if (this.record != null)
        {
            FontRenderer font = context.batcher.getFont();
            String name = font.limitToWidth(this.record.name, Math.max(30, this.area.w - 140));

            context.batcher.text(name, this.area.x + MARGIN + 56, this.area.y + 8, RecapStyle.text(), false);
        }

        super.render(context);

        if (this.dragged != null)
        {
            Area target = this.dropTarget(context.mouseX, context.mouseY);
            int accent = RecapStyle.accent();

            context.batcher.box(target.x, target.y, target.ex(), target.ey(), Colors.setA(accent, 0.18F));
            context.batcher.outline(target.x, target.y, target.ex(), target.ey(), accent, 2);
        }
    }
}
