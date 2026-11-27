package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.recap.RecapTrack;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.recap.RecapClips;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
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
import net.minecraft.client.MinecraftClient;
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
 * moment on every machine. The viewport draws the world through the recap's camera at the cursor
 * ({@link RecapScene}).</p>
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
    /** The picked clip's properties, a panel of its own beside the timeline's */
    public final UIRecapDock clipDock;
    private final UIElement clipHost = new UIElement();

    private final UIRecapViewport viewport;
    private final UIRecapClipsPanel timeline;
    private final UIRecapInfoPanel info;
    private final UIRecapViewPanel view;

    private final UIRecapSplitter sideSplitter;
    private final UIRecapSplitter timelineSplitter;
    private final UIRecapSplitter innerSplitter;

    private RecapRecord record;
    private RecapTrack track;
    private mchorse.bbs_mod.recap.RecapMotion motion;
    private mchorse.bbs_mod.recap.RecapEvents events;
    private mchorse.bbs_mod.recap.RecapEnvironment environment;
    private boolean sceneShown;
    private Position flight;
    private final Position lastFlown = new Position();
    private long lastFlightEdit;
    private int commitClock;
    private int sceneWidth;
    private int sceneHeight;
    private int cursor;
    private boolean playing;

    private UIRecapDock dragged;

    /* The areas computed by the last layout, for the splitters and the drop targets */
    private final Area body = new Area();
    private final Area side = new Area();
    private final Area main = new Area();
    /** The column the timeline and the viewport share, which stays put while the timeline is resized. */
    private final Area column = new Area();

    public UIRecapEditor(UIRecapPanel panel)
    {
        this.panel = panel;

        UIRecapButton back = new UIRecapButton(() -> Icons.ARROW_LEFT, UIRecapKeys.key("editor.back"), () -> this.panel.openHome());
        UIRecapButton rename = new UIRecapButton(() -> Icons.EDIT, UIRecapKeys.key("editor.rename"), this::rename);
        UIRecapButton undo = new UIRecapButton(() -> Icons.UNDO, UIRecapKeys.key("history.undo"), this::undo).active(this.history::canUndo);
        UIRecapButton redo = new UIRecapButton(() -> Icons.REDO, UIRecapKeys.key("history.redo"), this::redo).active(this.history::canRedo);
        UIRecapButton history = new UIRecapButton(() -> Icons.LIST, UIRecapKeys.key("history.title"), this::openHistoryMenu);

        back.relative(this).x(MARGIN).y(4).w(22).h(18);
        rename.relative(this).x(MARGIN + 26).y(4).w(22).h(18);

        UIRecapButton saveButton = new UIRecapButton(() -> Icons.SAVE, UIRecapKeys.key("editor.save"), this::saveWithNotice);

        saveButton.relative(this).x(MARGIN + 52).y(4).w(22).h(18);
        this.add(saveButton);
        history.relative(this).x(1F, -MARGIN - 22).y(4).w(22).h(18);
        redo.relative(this).x(1F, -MARGIN - 48).y(4).w(22).h(18);
        undo.relative(this).x(1F, -MARGIN - 74).y(4).w(22).h(18);

        this.viewport = new UIRecapViewport(this);
        this.timeline = new UIRecapClipsPanel(this);
        this.info = new UIRecapInfoPanel(this);
        this.view = new UIRecapViewPanel(this::viewChanged);

        this.viewportDock = this.dock("viewport", Icons.VIDEO_CAMERA, this.viewport);
        this.timelineDock = this.dock("timeline", Icons.TIME, this.timeline);
        this.viewportDock.note(this::viewportNote);

        /* F flies the camera, as in the film studio */
        this.keys().register(Keys.FLIGHT, this::toggleFlight).active(() -> this.record != null && this.sceneShown);
        this.infoDock = this.dock("info", Icons.FILE, this.info);
        this.viewDock = this.dock("view", Icons.VISIBLE, this.view);
        this.clipDock = this.dock("clip", Icons.EDIT, this.clipHost);
        this.clipDock.setVisible(false);

        this.sideSplitter = new UIRecapSplitter(true, this::dragSide, this::saveLayout);
        this.timelineSplitter = new UIRecapSplitter(false, this::dragTimeline, this::saveLayout);
        this.innerSplitter = new UIRecapSplitter(false, this::dragInner, this::saveLayout);

        this.add(back, rename, undo, redo, history, this.viewportDock, this.timelineDock, this.infoDock, this.viewDock, this.clipDock,
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

    /**
     * How long the timeline is: the recording's length while it has no clips, else as far as its
     * last clip reaches, so the cursor never runs into time there is nothing in.
     */
    public int getLength()
    {
        if (this.record == null)
        {
            return 0;
        }

        return this.record.clips.get().isEmpty() ? this.record.ticks : Math.max(1, this.record.clips.calculateDuration());
    }

    public boolean isPlaying()
    {
        return this.playing;
    }

    public void open(RecapRecord record)
    {
        this.record = record;
        this.track = RecapStore.loadTrack(record);
        this.motion = RecapStore.loadMotion(record);
        this.events = RecapStore.loadEvents(record);
        this.environment = RecapStore.loadEnvironment(record);
        this.cursor = 0;
        this.stopPlayback();

        /* A recap with no clips starts on an idle camera outside, behind the player, the whole length */
        if (record != null && record.clips.get().isEmpty())
        {
            this.addDefaultCamera(record);
        }
        this.info.fill(record);
        this.view.fill(record);

        if (record != null)
        {
            this.history.reset(UIRecapKeys.text("history.opened"), record, this.layout);
        }

        this.timeline.setRecord(record);

        if (record != null)
        {
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
        RecapScene.setPlaying(true);
    }

    public void stopPlayback()
    {
        this.playing = false;
        RecapScene.setPlaying(false);
    }

    /* The scene */

    public RecapTrack getTrack()
    {
        return this.track;
    }

    /**
     * Shows the recap in the world: the world is drawn at the viewport's frame size into the
     * picture the viewport shows, through the recap's camera when it has one, with its view
     * settings laid over it. Only while the editor is on screen.
     */
    public void showScene()
    {
        if (this.record == null)
        {
            return;
        }

        CameraController controller = BBSModClient.getCameraController();

        RecapScene.show(this.record, this.track, this.motion, this.events, this.environment, this::getCursor);
        RecapScene.setPlaying(this.playing);

        /* Always the recap's camera: the recorded eye, or for a recap kept before the camera was
         * the player's own, and the timeline's clips over either */
        if (!controller.has(RecapScene.INSTANCE))
        {
            controller.add(RecapScene.INSTANCE);
        }

        this.sceneShown = true;
        this.applySceneSize();
    }

    /** Gives the world back to the game: its own camera, size and look. */
    public void hideScene()
    {
        if (!this.sceneShown)
        {
            return;
        }

        this.setFlight(false);
        this.sceneShown = false;
        RecapScene.hide();
        BBSModClient.getCameraController().remove(RecapScene.INSTANCE);
        BBSRendering.setCustomSize(false);
    }

    public boolean isSceneShown()
    {
        return this.sceneShown;
    }

    /** The viewport's frame in real pixels, which the world is drawn at. */
    public void setSceneSize(int width, int height)
    {
        this.sceneWidth = width;
        this.sceneHeight = height;
        this.applySceneSize();
    }

    private void applySceneSize()
    {
        if (this.sceneShown && this.sceneWidth > 1 && this.sceneHeight > 1 && MinecraftClient.getInstance().world != null)
        {
            BBSRendering.setCustomSize(true, this.sceneWidth, this.sceneHeight);
        }
    }

    public void tick()
    {
        /* Values typed into the timeline's properties panel reach the history here, twice a second,
         * between presses so a drag in progress is one step when it is let go */
        if (++this.commitClock % 10 == 0 && GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
        {
            this.commitTimeline();
        }

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

    /**
     * After an edit on the timeline: a step of the history when the clips really changed, and the
     * recap written. Called on every release over the timeline, so it compares before it writes.
     * Tracking clips put down with no one picked are pointed at the player first.
     */
    /**
     * Saves on request, with a notice: everything is written as it is edited anyway, so this is
     * the sure way to know the recap on disk is the one on screen. Also Ctrl S.
     */
    public void saveWithNotice()
    {
        if (this.record == null)
        {
            return;
        }

        this.commitTimeline();

        if (RecapStore.save(this.record))
        {
            this.getContext().notifySuccess(UIRecapKeys.key("editor.saved"));
        }
        else
        {
            this.getContext().notifyError(UIRecapKeys.key("message.failed"));
        }
    }

    /** Puts the picked clip's properties panel in the clip panel, or takes it away for none. */
    public void showClipPanel(UIElement panel)
    {
        this.clipHost.removeAll();

        if (panel != null)
        {
            panel.resetFlex().relative(this.clipHost).w(1F).h(1F);
            this.clipHost.add(panel);
        }

        this.resize();
    }

    /**
     * The idle camera a new recap starts with: outside, behind and above the player, over its
     * whole length; behind it where it first is in the client's dimension, and none while the
     * recording was never in it, as its coordinates would be of another world.
     */
    private void addDefaultCamera(RecapRecord record)
    {
        int first = 0;

        if (!record.dimensions.isEmpty())
        {
            first = record.dimensions.firstTickIn(mchorse.bbs_mod.recap.RecapWorldOverlay.dimensionOf(net.minecraft.client.MinecraftClient.getInstance().world));

            if (first < 0)
            {
                return;
            }
        }

        Position outside = RecapScene.outsideView(record, this.track, this.motion, first);

        if (outside == null)
        {
            return;
        }

        mchorse.bbs_mod.utils.clips.Clip clip = RecapClips.factory().create(mchorse.bbs_mod.resources.Link.bbs("idle"));

        if (clip instanceof mchorse.bbs_mod.camera.clips.overwrite.IdleClip idle)
        {
            idle.tick.set(0);
            idle.duration.set(Math.max(1, record.ticks));
            idle.layer.set(0);
            idle.position.get().set(outside);
            record.clips.addClip(idle);
            RecapStore.save(record);
        }
    }

    /** What the viewport shows, said beside its title. */
    private String viewportNote()
    {
        if (!this.sceneShown)
        {
            return null;
        }

        if (this.isFlying())
        {
            return UIRecapKeys.text("editor.flying_note");
        }

        if (RecapScene.isElsewhere())
        {
            return UIRecapKeys.key("editor.other_dimension").format(UIRecapKeys.dimensionName(RecapScene.recordedDimension())).get();
        }

        if (RecapScene.isFirstPerson())
        {
            return UIRecapKeys.text("editor.live_world");
        }

        return UIRecapKeys.text(RecapScene.isFreeCamera() ? "editor.free_camera" : "editor.clip_camera");
    }

    public void commitTimeline()
    {
        if (this.record == null)
        {
            return;
        }

        if (RecapClips.aimAtPlayer(this.record.clips))
        {
            this.timeline.fillData();
        }

        if (this.history.push(UIRecapKeys.text("history.timeline"), null, this.record, this.layout))
        {
            this.save();
        }
    }

    /* Flight */

    public boolean isFlying()
    {
        return this.flight != null;
    }

    public void toggleFlight()
    {
        this.setFlight(!this.isFlying());
    }

    /**
     * Flying: the camera is flown by hand from where it was, with the keys and the mouse as in the
     * film studio, and while a clip is picked and the recap holds still, where it is flown to is
     * put into that clip at the cursor. Landing gives the camera back to the clips.
     */
    public void setFlight(boolean flight)
    {
        UIDashboard dashboard = this.panel.dashboard;

        if (flight == this.isFlying() || (flight && !this.sceneShown))
        {
            return;
        }

        if (flight)
        {
            this.flight = new Position();
            this.flight.set(RecapScene.getLast());
            this.lastFlown.set(this.flight);
            dashboard.orbit.setup(BBSModClient.getCameraController().camera);
            RecapScene.setManual(this.flight);
        }
        else
        {
            /* With no camera clip under the cursor the flight leaves the free camera where it landed */
            Position landed = this.flight;

            this.flight = null;
            RecapScene.setManual(null);

            if (RecapScene.isFreeCamera())
            {
                RecapScene.setFree(landed);
            }

            this.commitTimeline();
        }

        dashboard.orbitUI.setControl(flight);
    }

    /** Each frame: the flown camera follows the keys, and keys its pose into the picked clip now and then. */
    private void updateFlight()
    {
        UIDashboard dashboard = this.panel.dashboard;

        if (this.flight == null)
        {
            /* Kept on the camera shown, so a flight starts where the picture is */
            if (BBSModClient.getCameraController().camera != null)
            {
                dashboard.orbit.setup(BBSModClient.getCameraController().camera);
            }

            return;
        }

        dashboard.orbit.apply(this.flight);

        long now = System.currentTimeMillis();

        if (!this.playing && this.timeline.getClip() != null && !this.lastFlown.equals(this.flight) && now - this.lastFlightEdit > 100L)
        {
            this.lastFlightEdit = now;
            this.lastFlown.set(this.flight);
            this.timeline.editClip(new Position(this.flight.point, this.flight.angle));
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
        this.timeline.setRecord(this.record);
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

    /**
     * The panels are placed once the editor's own area is known for this pass, never from the one
     * of the pass before: placed from a stale area, a resize or a drag lagged a frame behind and the
     * panels slid over each other or shook.
     */
    @Override
    protected void afterResizeApplied()
    {
        this.applyLayout();
    }

    /** The smallest height a side panel keeps: its header and a few rows. */
    private static final int MIN_DOCK = UIRecapDock.HEADER + 40;
    /** What the viewport keeps beside the side column and above or below the timeline. */
    private static final int MIN_MAIN_W = 240;
    private static final int MIN_MAIN_H = 110;
    /** The smallest the clip panel is made: its header and a few of its fields. */
    private static final int MIN_CLIP = UIRecapDock.HEADER + 120;

    private static int clamp(int value, int min, int max)
    {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }

    /** The side column's width the layout asks for, held inside what the window leaves. */
    private int sideWidth(int width)
    {
        return clamp(this.layout.sideWidth, RecapLayout.MIN_SIDE, Math.min(width * 3 / 5, width - GAP - MIN_MAIN_W));
    }

    /** The timeline's height in a column of {@code height}, held so the viewport keeps its share. */
    private int timelineHeight(int height)
    {
        return clamp(this.layout.timelineHeight, RecapLayout.MIN_TIMELINE, Math.min(height * 7 / 10, height - GAP - MIN_MAIN_H));
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
        int sw = this.sideWidth(width);

        this.body.set(x0, y0, width, height);

        int bx0 = x0, by0 = y0, bx1 = x1, by1 = y1;
        int[] tl = null;

        if (l.timelineFull)
        {
            int th = this.timelineHeight(height);

            this.column.set(x0, y0, width, height);
            tl = l.timelineTop ? new int[] {x0, y0, x1, y0 + th} : new int[] {x0, y1 - th, x1, y1};
            by0 = l.timelineTop ? y0 + th + GAP : y0;
            by1 = l.timelineTop ? y1 : y1 - th - GAP;
        }

        /* The side column holds whichever of the record, the clip and the view settings are
         * shown; with none of them it steps aside and the viewport takes the width */
        boolean clipShown = l.clipVisible && !this.clipHost.getChildren().isEmpty();
        boolean hasSide = l.infoVisible || l.viewVisible || clipShown;
        int[] sideRect;
        int[] mainRect;

        if (hasSide)
        {
            sideRect = l.sideLeft ? new int[] {bx0, by0, bx0 + sw, by1} : new int[] {bx1 - sw, by0, bx1, by1};
            mainRect = l.sideLeft ? new int[] {bx0 + sw + GAP, by0, bx1, by1} : new int[] {bx0, by0, bx1 - sw - GAP, by1};
        }
        else
        {
            sideRect = new int[] {l.sideLeft ? bx0 : bx1, by0, l.sideLeft ? bx0 : bx1, by1};
            mainRect = new int[] {bx0, by0, bx1, by1};
        }

        if (!l.timelineFull)
        {
            /* The timeline shares the viewport's column, which does not move while it is resized */
            int th = this.timelineHeight(mainRect[3] - mainRect[1]);

            this.column.set(mainRect[0], mainRect[1], mainRect[2] - mainRect[0], mainRect[3] - mainRect[1]);
            tl = l.timelineTop ? new int[] {mainRect[0], mainRect[1], mainRect[2], mainRect[1] + th} : new int[] {mainRect[0], mainRect[3] - th, mainRect[2], mainRect[3]};
            mainRect = l.timelineTop ? new int[] {mainRect[0], mainRect[1] + th + GAP, mainRect[2], mainRect[3]} : new int[] {mainRect[0], mainRect[1], mainRect[2], mainRect[3] - th - GAP};
        }

        /* The other side panels, in their order */
        List<UIRecapDock> others = new java.util.ArrayList<>();
        UIRecapDock first = l.infoFirst ? this.infoDock : this.viewDock;
        UIRecapDock second = l.infoFirst ? this.viewDock : this.infoDock;

        if (first == this.infoDock ? l.infoVisible : l.viewVisible)
        {
            others.add(first);
        }

        if (second == this.infoDock ? l.infoVisible : l.viewVisible)
        {
            others.add(second);
        }

        /* The picked clip's properties: between the record and the view settings in the side
         * column, or, when that column has no room for it too, left of the viewport */
        int[] clipRect = null;
        boolean clipInSide = false;
        int sideH = sideRect[3] - sideRect[1];

        if (clipShown)
        {
            int sideNeed = MIN_CLIP + others.size() * (MIN_DOCK + GAP);
            int mainW = mainRect[2] - mainRect[0];
            int clipW = clamp(mainW / 3, 170, 260);

            if (sideH >= sideNeed || mainW - clipW - GAP < MIN_MAIN_W / 2)
            {
                clipInSide = true;
            }
            else
            {
                clipRect = new int[] {mainRect[0], mainRect[1], mainRect[0] + clipW, mainRect[3]};
                mainRect = new int[] {mainRect[0] + clipW + GAP, mainRect[1], mainRect[2], mainRect[3]};
            }
        }

        this.side.set(sideRect[0], sideRect[1], sideRect[2] - sideRect[0], sideRect[3] - sideRect[1]);
        this.main.set(mainRect[0], mainRect[1], mainRect[2] - mainRect[0], mainRect[3] - mainRect[1]);

        this.place(this.viewportDock, mainRect);
        this.place(this.timelineDock, tl);

        int clipH = !clipInSide ? 0 : (others.isEmpty() ? sideH : clamp(Math.round(sideH * 0.42F), Math.min(MIN_CLIP, sideH / 2), sideH - others.size() * (MIN_DOCK + GAP)));
        int band = clipInSide && !others.isEmpty() ? clipH + GAP : 0;
        int room = sideH - band - Math.max(0, others.size() - 1) * GAP;
        int y = sideRect[1];

        this.infoDock.setVisible(others.contains(this.infoDock));
        this.viewDock.setVisible(others.contains(this.viewDock));
        this.innerSplitter.setVisible(others.size() == 2);

        if (others.isEmpty() && clipInSide)
        {
            clipRect = new int[] {sideRect[0], sideRect[1], sideRect[2], sideRect[3]};
        }

        for (int i = 0; i < others.size(); i++)
        {
            UIRecapDock dock = others.get(i);
            int h;

            if (others.size() == 2 && i == 0)
            {
                float upperRatio = dock == this.infoDock ? l.infoRatio : 1F - l.infoRatio;

                h = room < MIN_DOCK * 2 ? room / 2 : clamp(Math.round(room * upperRatio), MIN_DOCK, room - MIN_DOCK);
            }
            else
            {
                h = sideRect[3] - y - (clipInSide && i == 0 && others.size() == 1 ? band : 0);
            }

            this.place(dock, new int[] {sideRect[0], y, sideRect[2], y + h});
            y += h;

            if (i == 0)
            {
                if (others.size() == 2)
                {
                    this.place(this.innerSplitter, new int[] {sideRect[0], y, sideRect[2], y + GAP});
                }

                if (clipInSide)
                {
                    clipRect = new int[] {sideRect[0], y + GAP, sideRect[2], y + GAP + clipH};
                    y += band;
                }
            }

            y += GAP;
        }

        this.clipDock.setVisible(clipRect != null);

        if (clipRect != null)
        {
            this.place(this.clipDock, clipRect);
        }

        /* The gaps that resize */
        int gapX = l.sideLeft ? sideRect[2] : sideRect[0] - GAP;

        this.sideSplitter.setVisible(hasSide);
        this.place(this.sideSplitter, new int[] {gapX, sideRect[1], gapX + GAP, sideRect[3]});

        int gapY = l.timelineTop ? tl[3] : tl[1] - GAP;

        this.place(this.timelineSplitter, new int[] {tl[0], gapY, tl[2], gapY + GAP});
    }

    /** Sets an element's area right away, so the children it lays out next see the new one. */
    private void place(UIElement element, int[] r)
    {
        element.relative(this).x(r[0] - this.area.x).y(r[1] - this.area.y).w(Math.max(1, r[2] - r[0])).h(Math.max(1, r[3] - r[1]));
    }

    /* The drags read the pointer against areas that stay put while they resize, and hold the
     * value to the same bounds the layout uses, so what is stored is what is shown */

    private void dragSide(int mouseX)
    {
        if (this.layouts.locked)
        {
            return;
        }

        int width = this.layout.sideLeft ? mouseX - this.body.x - GAP / 2 : this.body.ex() - mouseX - GAP / 2;

        this.layout.sideWidth = width;
        this.layout.sideWidth = this.sideWidth(this.body.w);
        this.resize();
    }

    private void dragTimeline(int mouseY)
    {
        if (this.layouts.locked)
        {
            return;
        }

        int height = this.layout.timelineTop ? mouseY - this.column.y - GAP / 2 : this.column.ey() - mouseY - GAP / 2;

        this.layout.timelineHeight = height;
        this.layout.timelineHeight = this.timelineHeight(this.column.h);
        this.resize();
    }

    private void dragInner(int mouseY)
    {
        if (this.layouts.locked)
        {
            return;
        }

        float upper = (mouseY - this.side.y - GAP / 2F) / (float) Math.max(1, this.side.h - GAP);

        upper = Math.max(0.1F, Math.min(0.9F, upper));
        this.layout.infoRatio = this.layout.infoFirst ? upper : 1F - upper;
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
        if (this.layouts.locked)
        {
            return;
        }

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

            if (!this.layouts.templates.isEmpty())
            {
                menu.action(Icons.REMOVE, UIRecapKeys.key("layout.delete"), this::openDeleteMenu);
            }
        });
    }

    /* The window and layout menu of the bar on top, as the film studio's */

    public boolean isLayoutLocked()
    {
        return this.layouts.locked;
    }

    /**
     * The same entries as the film studio's window and layout menu, for the recap editor's own
     * panels: which side panels are shown, the layout templates, saving one, putting the layout
     * back as it came, locking it, and where the side column and the timeline go.
     */
    public void fillWindowMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        RecapLayout l = this.layout;

        menu.action(l.infoVisible ? Icons.CHECKMARK : Icons.NONE, UIRecapKeys.key("panel.info"), () -> this.change(() -> l.infoVisible = !l.infoVisible));
        menu.action(l.clipVisible ? Icons.CHECKMARK : Icons.NONE, UIRecapKeys.key("panel.clip"), () -> this.change(() -> l.clipVisible = !l.clipVisible));
        menu.action(l.viewVisible ? Icons.CHECKMARK : Icons.NONE, UIRecapKeys.key("panel.view"), () -> this.change(() -> l.viewVisible = !l.viewVisible));
        menu.action(Icons.LAYOUT, mchorse.bbs_mod.ui.UIKeys.FILM_LAYOUT_PRESETS, this::openLayoutMenu);
        menu.action(Icons.SAVED, mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.film.layout_save_preset"), this::saveTemplate);
        menu.action(Icons.REFRESH, mchorse.bbs_mod.ui.UIKeys.FILM_MENU_RESET_LAYOUT, () -> this.useLayout(new RecapLayout()));
        menu.action(this.layouts.locked ? Icons.UNLOCKED : Icons.LOCKED, this.layouts.locked ? mchorse.bbs_mod.ui.UIKeys.FILM_LAYOUT_UNLOCK : mchorse.bbs_mod.ui.UIKeys.FILM_LAYOUT_LOCK, () ->
        {
            this.layouts.locked = !this.layouts.locked;
            this.layouts.save();
        });
        menu.action(l.sideLeft ? Icons.ARROW_RIGHT : Icons.ARROW_LEFT, UIRecapKeys.key(l.sideLeft ? "layout.side_right" : "layout.side_left"), () -> this.change(() -> l.sideLeft = !l.sideLeft));
        menu.action(l.timelineTop ? Icons.ARROW_DOWN : Icons.ARROW_UP, UIRecapKeys.key(l.timelineTop ? "layout.timeline_bottom" : "layout.timeline_top"), () -> this.change(() -> l.timelineTop = !l.timelineTop));
        menu.action(l.timelineFull ? Icons.CHECKMARK : Icons.NONE, UIRecapKeys.key("layout.timeline_full"), () -> this.change(() -> l.timelineFull = !l.timelineFull));
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

            if (key == GLFW.GLFW_KEY_S)
            {
                this.saveWithNotice();

                return true;
            }

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
        this.updateFlight();

        /* As in the film studio: the mouse is the flown camera's for as long as the flight lasts,
         * hidden and held, and given back while an overlay needs clicking or when the flight ends.
         * With the pointer away nothing of the interface answers to it. */
        this.panel.dashboard.orbitUI.setFreeLook(this.isFlying() && !mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.has(context));

        if (this.panel.dashboard.orbitUI.isFreeLook())
        {
            context.mouseX = context.mouseY = -1;
        }

        RecapStyle.backdrop(context.batcher, this.area);
        RecapStyle.surface(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.y + TOP - 4, 0, false);

        if (this.record != null)
        {
            FontRenderer font = context.batcher.getFont();
            /* Between rename on the left and undo, redo, history and layouts on the right */
            String name = font.limitToWidth(this.record.name, Math.max(20, this.area.w - (MARGIN + 82) - (MARGIN + 82)));

            context.batcher.text(name, this.area.x + MARGIN + 82, this.area.y + 8, RecapStyle.text(), false);
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
