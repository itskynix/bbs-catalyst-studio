package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.framework.tooltips.TooltipPlacement;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * UICatalystTimeline - Built by replicating BBS Film Editor's UIClips architecture.
 *
 * Provides:
 * - Strict Ruler scrubbing: clicks in ruler only scrub the playhead; clicks in tracks do not move playhead.
 * - Layer selection, multi-selection (Shift + Click / Marquee), and Deselect (clicking empty track area).
 * - Clip moving (grabMode 0) and edge trimming (grabMode 1: left edge, grabMode 2: right edge) with MAGNETIC SNAPPING.
 * - Zoom (Mouse wheel / Ctrl + Wheel) and Pan (Middle click drag / Horizontal wheel).
 * - Cut selected clip at playhead (Key C or split button).
 * - Delete selected clips (Delete key).
 * - Hierarchical Context Menu (Right-click): Add action, Delete, Duplicate, Cut, with Add sub-menu for layer types.
 */
public class UICatalystTimeline extends UITimelineCanvas
{
    private static final int LAYER_ROW_H   = 24;
    private static final int PROP_ROW_H    = 18; /* Height of each property sub-row when twirl is open */
    private static final int SNAP_DISTANCE = 8;  /* pixels tolerance for magnetic snapping */
    private static final Area TEMP_AREA    = new Area();

    /* Property bitmask constants (matches CatalystLayer.expandedProps) */
    public static final int PROP_POSITION  = 1;
    public static final int PROP_SCALE     = 2;
    public static final int PROP_ROTATION  = 4;
    public static final int PROP_OPACITY   = 8;
    public static final int PROP_ANCHOR    = 16;

    /* Vertical scrolling for track lanes */
    public Scroll vertical = new Scroll(new Area());

    /* Data bindings */
    public Supplier<CatalystComposition> compSupplier;
    public IntSupplier                   playheadSupplier;
    public IntConsumer                   playheadSetter;
    public Runnable                      onScrub;
    public Runnable                      onModified;
    public Runnable                      onPreModify;

    /* Snapping toggle */
    public boolean snapping = true;

    /* Gap selection */
    private int selectedGapStart = -1;
    private int selectedGapDuration = -1;

    /* Interaction state replicated from UIClips */
    private boolean scrubbing;
    private boolean grabbing;
    private boolean canGrab;
    private int grabMode = 0; /* 0 = move, 1 = trim left, 2 = trim right */

    private final Set<CatalystLayer> selectedLayers = new LinkedHashSet<>();
    private List<CatalystLayer> grabbedLayers = Collections.emptyList();
    private final List<Vector3i> grabbedData = new ArrayList<>();

    /* Keyframe dragging & selection state */
    private boolean isDraggingKeyframe = false;
    private CatalystLayer draggedKfLayer = null;
    private final List<KeyframeChannel<?>> draggedKfChannels = new ArrayList<>();
    private final List<Keyframe<?>> draggedKeyframes = new ArrayList<>();
    private final Map<Keyframe<?>, Integer> dragStartTicks = new HashMap<>();
    private int dragInitialTick = 0;
    private CatalystLayer lastClickedKfLayer = null;
    private final List<KeyframeChannel<?>> lastClickedChannels = new ArrayList<>();
    private final List<Keyframe<?>> lastClickedKeyframes = new ArrayList<>();

    public UICatalystTimeline(Supplier<CatalystComposition> compSupplier,
                              IntSupplier playheadSupplier,
                              IntConsumer playheadSetter,
                              Runnable onScrub,
                              Runnable onModified)
    {
        super();

        this.compSupplier     = compSupplier;
        this.playheadSupplier = playheadSupplier;
        this.playheadSetter   = playheadSetter;
        this.onScrub          = onScrub;
        this.onModified       = onModified;

        this.vertical.direction = ScrollDirection.VERTICAL;
        this.vertical.cancelScrolling();
        this.vertical.scrollSpeed = 16;
        this.tooltip(IKey.EMPTY);

        this.context((menu) ->
        {
            UIContext context = this.getContext();
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;

            if (this.isInRuler(mouseY)) return;

            CatalystLayer layerUnder = this.getLayerUnder(context, mouseX, mouseY);
            if (layerUnder != null)
            {
                if (!this.isSelected(layerUnder))
                {
                    this.setSelected(layerUnder);
                }
            }

            boolean hasSelected = !this.selectedLayers.isEmpty();

            /* Top Horizontal Action Bar (replicated from UIClips) */
            if (!hasSelected)
            {
                menu.icon(MenuVerb.ADD, () -> this.showAdds(mouseX, mouseY));
            }
            else
            {
                menu.icon(MenuVerb.REMOVE, this::deleteSelected);
                menu.icon(MenuVerb.COPY, this::duplicateSelected);

                /* List items for selected layer */
                menu.action(Icons.CUT, IKey.raw("Cut / Split (C)"), this::cutSelected);
                menu.action(Icons.TRASH, IKey.raw("Ripple Delete (Shift + Del)"), this::rippleDeleteSelected);
                menu.action(Icons.COPY, IKey.raw("Duplicate"), this::duplicateSelected);
                menu.action(Icons.REMOVE, IKey.raw("Delete (Del)"), this::deleteSelected);
            }
        });
    }

    private void showAdds(int mouseX, int mouseY)
    {
        UIContext context = this.getContext();

        context.replaceContextMenu((add) ->
        {
            add.action(Icons.CURSOR, IKey.raw("Add layer at cursor..."), () -> this.showAddTypes(context, Math.max(0, this.fromGraphTick(mouseX))));
            add.action(Icons.SHIFT_TO, IKey.raw("Add layer at current tick..."), () ->
            {
                int playhead = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;
                this.showAddTypes(context, playhead);
            });
        });
    }

    private void showAddTypes(UIContext context, int tick)
    {
        context.replaceContextMenu((add) ->
        {
            UIChoiceMenu.of(CatalystLayer.LayerType.values())
                .icon((type) -> Icons.ADD)
                .label((type) -> IKey.raw(type == CatalystLayer.LayerType.SCENE ? "Film Layer" : (type.name().substring(0, 1) + type.name().substring(1).toLowerCase() + " Layer")))
                .color((type) -> type.defaultColor())
                .build(add, IKey.raw("Layer Types"), (type) -> this.addLayer(type, tick));
        });
    }

    public CatalystLayer getFirstSelectedLayer()
    {
        return this.selectedLayers.isEmpty() ? null : this.selectedLayers.iterator().next();
    }

    public CatalystComposition getComposition()
    {
        return this.compSupplier != null ? this.compSupplier.get() : null;
    }

    public int getLayerHeight()
    {
        return LAYER_ROW_H;
    }

    /** Returns total height occupied by one layer including any expanded property sub-rows. */
    public int getExpandedLayerHeight(CatalystLayer layer)
    {
        if (!layer.expanded) return LAYER_ROW_H;
        int subRows = Integer.bitCount(layer.expandedProps);
        return LAYER_ROW_H + subRows * PROP_ROW_H;
    }

    /** Number of expanded (visible) property rows for a layer. */
    public static int propRowCount(CatalystLayer layer)
    {
        return layer.expanded ? Integer.bitCount(layer.expandedProps) : 0;
    }

    public int getRulerBottom()
    {
        return TimelineRulerRenderer.getRulerBottom(this.area);
    }

    public boolean isInRuler(int mouseY)
    {
        return mouseY >= this.area.y && mouseY < this.getRulerBottom();
    }

    /** Returns the Y pixel coordinate of the top of the given layer index, accounting for expanded predecessors. */
    public int toLayerY(int layerIndex)
    {
        int rulerBottom = this.getRulerBottom();
        int y = rulerBottom - (int) this.vertical.getScroll();
        CatalystComposition comp = this.getComposition();
        if (comp == null || layerIndex <= 0) return y;
        List<CatalystLayer> layers = comp.layers;
        for (int i = 0; i < layerIndex && i < layers.size(); i++)
        {
            y += this.getExpandedLayerHeight(layers.get(i));
        }
        return y;
    }

    /** Returns the layer index under the given mouseY, accounting for expanded predecessors. */
    public int fromLayerY(int mouseY)
    {
        int rulerBottom = this.getRulerBottom();
        if (mouseY < rulerBottom) return -1;
        CatalystComposition comp = this.getComposition();
        if (comp == null) return (mouseY - rulerBottom + (int) this.vertical.getScroll()) / LAYER_ROW_H;
        int y = rulerBottom - (int) this.vertical.getScroll();
        List<CatalystLayer> layers = comp.layers;
        for (int i = 0; i < layers.size(); i++)
        {
            int h = this.getExpandedLayerHeight(layers.get(i));
            if (mouseY < y + h) return i;
            y += h;
        }
        return layers.size(); // past end
    }

    public int getLayerIndex(CatalystLayer layer)
    {
        CatalystComposition comp = this.getComposition();
        return comp != null ? comp.layers.indexOf(layer) : -1;
    }

    public int fromGraphTick(int mouseX)
    {
        return (int) Math.round(this.fromGraphX(mouseX));
    }

    public Area getLayerArea(CatalystLayer layer, Area outArea, int h)
    {
        int index = this.getLayerIndex(layer);
        int tick = layer.startFrame;
        int x = this.toGraphX(tick);
        int y = this.toLayerY(index);
        int w = this.toGraphX(tick + layer.duration) - x;

        outArea.set(x, y, w, h);
        return outArea;
    }

    public int getLayerHandle(CatalystLayer layer, UIContext context, int h)
    {
        Area layerArea = this.getLayerArea(layer, TEMP_AREA, h);
        int separation = Math.min(layerArea.w / 2, 6);

        if (layerArea.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                return 0;
            }

            if (context.mouseX - layerArea.x < separation)
            {
                return 1;
            }
            else if (context.mouseX - layerArea.ex() >= -separation)
            {
                return 2;
            }

            return 0;
        }

        return -1;
    }

    public CatalystLayer getLayerUnder(UIContext context, int mouseX, int mouseY)
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || comp.layers.isEmpty()) return null;

        int h = this.getLayerHeight();
        Area tempArea = new Area();
        List<CatalystLayer> list = comp.layers;

        /* 1. Check edge handles first */
        for (int i = list.size() - 1; i >= 0; i--)
        {
            CatalystLayer l = list.get(i);
            int handle = this.getLayerHandle(l, context, h);
            if (handle == 1 || handle == 2)
            {
                return l;
            }
        }

        /* 2. Check inside visual box */
        for (int i = list.size() - 1; i >= 0; i--)
        {
            CatalystLayer l = list.get(i);
            this.getLayerArea(l, tempArea, h);
            if (tempArea.isInside(context))
            {
                return l;
            }
        }

        return null;
    }

    /* ─── Selection Management ──────────────────────── */

    public Set<CatalystLayer> getSelectedLayers()
    {
        return this.selectedLayers;
    }

    public boolean isSelected(CatalystLayer layer)
    {
        return this.selectedLayers.contains(layer);
    }

    public void setSelected(CatalystLayer layer)
    {
        this.selectedLayers.clear();
        if (layer != null)
        {
            this.selectedLayers.add(layer);
        }
    }

    public void addSelected(CatalystLayer layer)
    {
        if (layer != null)
        {
            this.selectedLayers.add(layer);
        }
    }

    public void toggleSelected(CatalystLayer layer)
    {
        if (layer != null)
        {
            if (this.selectedLayers.contains(layer))
            {
                this.selectedLayers.remove(layer);
            }
            else
            {
                this.selectedLayers.add(layer);
            }
        }
    }

    public void clearSelection()
    {
        this.selectedLayers.clear();
        this.lastClickedKeyframes.clear();
        this.lastClickedChannels.clear();
        this.lastClickedKfLayer = null;
        this.dragStartTicks.clear();
    }

    private void captureSelection(Area selectionArea)
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null) return;

        if (!Window.isShiftPressed())
        {
            this.clearSelection();
        }

        int h = this.getLayerHeight();
        Area tempArea = new Area();

        for (CatalystLayer layer : comp.layers)
        {
            this.getLayerArea(layer, tempArea, h);
            if (selectionArea.intersects(tempArea))
            {
                this.addSelected(layer);
            }
        }
    }

    /* ─── Layout & Sizing ───────────────────────────── */

    @Override
    public void resize()
    {
        super.resize();

        int rulerBottom = this.getRulerBottom();
        this.vertical.area.copy(this.area);
        this.vertical.area.y = rulerBottom;
        this.vertical.area.h = Math.max(0, this.area.ey() - rulerBottom);

        this.xAxis.area = this.area;
    }

    public void updateScrollSize()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || comp.layers.isEmpty())
        {
            this.vertical.scrollSize = 0;
        }
        else
        {
            int total = 0;
            for (CatalystLayer layer : comp.layers)
            {
                total += this.getExpandedLayerHeight(layer);
            }
            this.vertical.scrollSize = total;
        }
        this.vertical.clamp();
    }

    /* ─── Mouse Input Handling (UIClips Style) ──────── */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context)) this.xAxis.stopZoom();
        if (this.vertical.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context))
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            boolean ctrl = Window.isCtrlPressed();
            boolean shift = Window.isShiftPressed();
            boolean alt = Window.isAltPressed();

            if (context.mouseButton == 0 && this.handleLeftClick(context, mouseX, mouseY, ctrl, shift, alt))
            {
                return true;
            }
            else if (context.mouseButton == 2 && this.handleMiddleClick(mouseX, mouseY))
            {
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    private boolean handleLeftClick(UIContext context, int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt)
    {
        /* Magnet button click check */
        int btnX = this.area.x + 4;
        int btnY = this.area.y + 2;
        int btnW = 18;
        int btnH = 16;
        if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH)
        {
            this.snapping = !this.snapping;
            return true;
        }

        /* 0. Keyframe Diamond Click (inside expanded property sub-rows) */
        CatalystComposition comp = this.getComposition();
        if (comp != null && !this.isInRuler(mouseY))
        {
            for (CatalystLayer l : comp.layers)
            {
                if (!l.expanded || l.expandedProps == 0) continue;

                int ly = this.toLayerY(this.getLayerIndex(l));
                int py = ly + LAYER_ROW_H;
                int bits = l.expandedProps;
                int[] propBits = {PROP_POSITION, PROP_SCALE, PROP_ROTATION, PROP_OPACITY, PROP_ANCHOR};

                for (int p = 0; p < propBits.length; p++)
                {
                    if ((bits & propBits[p]) == 0) continue;

                    int midY = py + PROP_ROW_H / 2;
                    if (Math.abs(mouseY - midY) <= 6)
                    {
                        KeyframeChannel<?>[] channels = getChannelsForProp(l, propBits[p]);
                        for (KeyframeChannel<?> ch : channels)
                        {
                            if (ch == null) continue;
                            for (Keyframe<?> kf : ch.getKeyframes())
                            {
                                int kfTick = Math.round(kf.getTick());
                                int kx = this.toGraphX(kfTick);
                                if (Math.abs(mouseX - kx) <= 6)
                                {
                                    /* Hit keyframe diamond! */
                                    if (this.onPreModify != null)
                                    {
                                        this.onPreModify.run();
                                    }

                                    boolean isAlreadySelected = (this.lastClickedKfLayer == l)
                                        && this.lastClickedKeyframes.stream().anyMatch(item -> Math.round(item.getTick()) == kfTick);

                                    if (shift)
                                    {
                                        if (this.lastClickedKfLayer != l)
                                        {
                                            this.lastClickedKfLayer = l;
                                            this.lastClickedChannels.clear();
                                            this.lastClickedKeyframes.clear();
                                        }

                                        if (isAlreadySelected)
                                        {
                                            /* Toggle off clicked keyframes */
                                            for (KeyframeChannel<?> c : channels)
                                            {
                                                if (c == null)
                                                {
                                                    continue;
                                                }
                                                for (Keyframe<?> item : c.getKeyframes())
                                                {
                                                    if (Math.round(item.getTick()) == kfTick)
                                                    {
                                                        this.lastClickedKeyframes.remove(item);
                                                    }
                                                }
                                            }
                                            if (this.lastClickedKeyframes.isEmpty())
                                            {
                                                this.lastClickedChannels.clear();
                                                this.lastClickedKfLayer = null;
                                            }
                                            this.setSelected(l);
                                            return true;
                                        }
                                        else
                                        {
                                            /* Add clicked keyframes to selection */
                                            for (KeyframeChannel<?> c : channels)
                                            {
                                                if (c == null)
                                                {
                                                    continue;
                                                }
                                                if (!this.lastClickedChannels.contains(c))
                                                {
                                                    this.lastClickedChannels.add(c);
                                                }
                                                for (Keyframe<?> item : c.getKeyframes())
                                                {
                                                    if (Math.round(item.getTick()) == kfTick && !this.lastClickedKeyframes.contains(item))
                                                    {
                                                        this.lastClickedKeyframes.add(item);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else if (!isAlreadySelected)
                                    {
                                        /* Single click on an unselected keyframe selects only this keyframe */
                                        this.lastClickedKfLayer = l;
                                        this.lastClickedChannels.clear();
                                        this.lastClickedKeyframes.clear();

                                        for (KeyframeChannel<?> c : channels)
                                        {
                                            if (c == null)
                                            {
                                                continue;
                                            }
                                            this.lastClickedChannels.add(c);
                                            for (Keyframe<?> item : c.getKeyframes())
                                            {
                                                if (Math.round(item.getTick()) == kfTick)
                                                {
                                                    this.lastClickedKeyframes.add(item);
                                                }
                                            }
                                        }
                                    }

                                    /* Start dragging all selected keyframes */
                                    this.isDraggingKeyframe = true;
                                    this.draggedKfLayer = l;
                                    this.draggedKfChannels.clear();
                                    this.draggedKeyframes.clear();
                                    this.dragStartTicks.clear();
                                    this.dragInitialTick = kfTick;

                                    for (KeyframeChannel<?> c : this.lastClickedChannels)
                                    {
                                        if (c != null && !this.draggedKfChannels.contains(c))
                                        {
                                            this.draggedKfChannels.add(c);
                                        }
                                    }
                                    for (Keyframe<?> item : this.lastClickedKeyframes)
                                    {
                                        this.draggedKeyframes.add(item);
                                        this.dragStartTicks.put(item, Math.round(item.getTick()));
                                    }

                                    this.setSelected(l);
                                    this.setMouse(mouseX, mouseY);
                                    return true;
                                }
                            }
                        }
                    }
                    py += PROP_ROW_H;
                }
            }
        }

        /* Clicking outside keyframe diamonds clears selected keyframes */
        this.lastClickedKeyframes.clear();
        this.lastClickedChannels.clear();
        this.lastClickedKfLayer = null;
        this.dragStartTicks.clear();

        /* 1. Track Area Click (NOT in Ruler) */
        if (!this.isInRuler(mouseY))
        {
            CatalystLayer layer = this.getLayerUnder(context, mouseX, mouseY);

            if (layer != null)
            {
                this.selectedGapStart = -1;
                this.selectedGapDuration = -1;

                int handle = this.getLayerHandle(layer, context, this.getLayerHeight());
                if (this.onPreModify != null)
                {
                    this.onPreModify.run();
                }
                this.grabMode = handle;
                this.canGrab = false;
                this.grabbing = true;

                if (this.grabMode != 0)
                {
                    this.setSelected(layer);
                    this.grabbedLayers = Collections.singletonList(layer);
                    this.grabbedData.clear();
                    this.grabbedData.add(new Vector3i(layer.startFrame, layer.mediaOffset, layer.duration));
                }
                else
                {
                    if (shift)
                    {
                        if (!this.isSelected(layer))
                        {
                            this.addSelected(layer);
                        }
                    }
                    else
                    {
                        if (!this.isSelected(layer))
                        {
                            this.setSelected(layer);
                        }
                    }

                    this.grabbedLayers = new ArrayList<>(this.selectedLayers);
                    this.grabbedData.clear();
                    for (CatalystLayer sel : this.grabbedLayers)
                    {
                        this.grabbedData.add(new Vector3i(sel.startFrame, sel.mediaOffset, sel.duration));
                    }
                }

                this.setMouse(mouseX, mouseY);
                return true;
            }
            else
            {
                /* Deselect: Clicking empty track area clears selection unless Shift is held */
                if (!shift)
                {
                    this.clearSelection();
                }

                /* Check for gap selection */
                int clickTick = Math.max(0, this.fromGraphTick(mouseX));
                if (comp != null)
                {
                    int prevEnd = 0;
                    int nextStart = comp.duration;
                    boolean insideAny = false;

                    for (CatalystLayer l : comp.layers)
                    {
                        if (clickTick >= l.startFrame && clickTick < l.startFrame + l.duration)
                        {
                            insideAny = true;
                            break;
                        }
                        if (l.startFrame + l.duration <= clickTick)
                        {
                            if (l.startFrame + l.duration > prevEnd)
                            {
                                prevEnd = l.startFrame + l.duration;
                            }
                        }
                        if (l.startFrame > clickTick)
                        {
                            if (l.startFrame < nextStart)
                            {
                                nextStart = l.startFrame;
                            }
                        }
                    }

                    if (!insideAny && nextStart > prevEnd && clickTick >= prevEnd && clickTick < nextStart)
                    {
                        this.selectedGapStart = prevEnd;
                        this.selectedGapDuration = nextStart - prevEnd;
                    }
                    else
                    {
                        this.selectedGapStart = -1;
                        this.selectedGapDuration = -1;
                    }
                }

                this.marquee.press(mouseX, mouseY);
                this.setMouse(mouseX, mouseY);
                return true;
            }
        }

        /* 2. Ruler Area Click -> Scrubbing */
        this.selectedGapStart = -1;
        this.selectedGapDuration = -1;
        this.scrubbing = true;
        if (this.onScrub != null)
        {
            this.onScrub.run();
        }
        this.setPlayheadFromMouse(mouseX);

        return true;
    }

    private boolean handleMiddleClick(int mouseX, int mouseY)
    {
        this.navigating = true;
        this.setMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.vertical.mouseReleased(context);

        if (this.scrubbing)
        {
            this.setPlayheadFromMouse(context.mouseX);
        }

        if (this.marquee.isActive())
        {
            this.captureSelection(this.marquee.getArea());
        }

        if (this.isDraggingKeyframe)
        {
            this.isDraggingKeyframe = false;
            for (KeyframeChannel<?> ch : this.draggedKfChannels)
            {
                ch.sort();
            }
            this.draggedKeyframes.clear();
            this.draggedKfChannels.clear();
            this.dragStartTicks.clear();
            this.draggedKfLayer = null;
            if (this.onModified != null)
            {
                this.onModified.run();
            }
        }

        if (this.grabbing && this.canGrab)
        {
            if (this.onModified != null)
            {
                this.onModified.run();
            }
        }

        this.grabMode = 0;
        this.grabbing = false;
        this.marquee.reset();
        this.scrubbing = false;
        this.navigating = false;
        this.grabbedLayers = Collections.emptyList();
        this.grabbedData.clear();

        return super.subMouseReleased(context);
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.navigating)
        {
            if (context.mouseWheelHorizontal != 0D)
            {
                this.panTime(context.mouseWheelHorizontal);
            }
            else if (Window.isShiftPressed() && !Window.isCtrlPressed())
            {
                this.vertical.mouseScroll(context);
            }
            else if (context.mouseWheel != 0D)
            {
                this.zoomTimeAt(context, context.mouseWheel);
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isFocused())
        {
            return super.subKeyPressed(context);
        }

        if (context.isPressed(GLFW.GLFW_KEY_LEFT))
        {
            int step = Window.isShiftPressed() ? 10 : 1;
            int cur = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;
            int newFrame = Math.max(0, cur - step);

            if (this.onScrub != null)
            {
                this.onScrub.run();
            }
            if (this.playheadSetter != null)
            {
                this.playheadSetter.accept(newFrame);
            }

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_RIGHT))
        {
            int step = Window.isShiftPressed() ? 10 : 1;
            int cur = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;
            CatalystComposition comp = this.getComposition();
            int maxFrame = comp != null ? Math.max(0, comp.duration - 1) : Integer.MAX_VALUE;
            int newFrame = Math.min(maxFrame, cur + step);

            if (this.onScrub != null)
            {
                this.onScrub.run();
            }
            if (this.playheadSetter != null)
            {
                this.playheadSetter.accept(newFrame);
            }

            return true;
        }

        if (this.area.isInside(context) && context.isPressed(GLFW.GLFW_KEY_N) && !Window.isCtrlPressed())
        {
            this.snapping = !this.snapping;
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_C))
        {
            this.cutSelected();
            return true;
        }
        if (context.isPressed(GLFW.GLFW_KEY_DELETE) || context.isPressed(GLFW.GLFW_KEY_BACKSPACE))
        {
            if (this.deleteSelectedKeyframes())
            {
                return true;
            }
            if (Window.isShiftPressed())
            {
                this.rippleDeleteSelected();
                return true;
            }
            if (this.selectedGapStart >= 0 && this.selectedGapDuration > 0)
            {
                this.rippleDeleteGap();
                return true;
            }
            this.deleteSelected();
            return true;
        }
        if (context.isPressed(GLFW.GLFW_KEY_M))
        {
            this.addMarkerAtPlayhead();
            return true;
        }
        if (context.isPressed(GLFW.GLFW_KEY_J))
        {
            this.jumpToKeyframe(false);
            return true;
        }
        if (context.isPressed(GLFW.GLFW_KEY_K))
        {
            this.jumpToKeyframe(true);
            return true;
        }

        return super.subKeyPressed(context);
    }

    public static KeyframeChannel<?>[] getChannelsForProp(CatalystLayer layer, int propBit)
    {
        if (propBit == PROP_POSITION) return new KeyframeChannel<?>[]{layer.channelPosX, layer.channelPosY};
        if (propBit == PROP_SCALE) return new KeyframeChannel<?>[]{layer.channelScaleX, layer.channelScaleY};
        if (propBit == PROP_ROTATION) return new KeyframeChannel<?>[]{layer.channelRotation};
        if (propBit == PROP_OPACITY) return new KeyframeChannel<?>[]{layer.channelOpacity};
        return new KeyframeChannel<?>[]{layer.channelAnchorX, layer.channelAnchorY};
    }

    /**
     * AE-standard keyframe navigation:
     * J: jumps playhead to previous keyframe.
     * K: jumps playhead to next keyframe.
     */
    public void jumpToKeyframe(boolean next)
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null)
        {
            return;
        }
        int current = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;

        TreeSet<Integer> kfFrames = new TreeSet<>();

        if (!this.lastClickedChannels.isEmpty() && this.lastClickedKfLayer != null)
        {
            /* 1. If user clicked a specific property channel, prioritize navigating that channel */
            for (KeyframeChannel<?> ch : this.lastClickedChannels)
            {
                if (ch == null)
                {
                    continue;
                }
                for (Keyframe<?> kf : ch.getKeyframes())
                {
                    kfFrames.add(Math.round(kf.getTick()));
                }
            }
        }
        else if (!this.selectedLayers.isEmpty())
        {
            /* 2. Navigate within selected layer(s) */
            int[] propBits = {PROP_POSITION, PROP_SCALE, PROP_ROTATION, PROP_OPACITY, PROP_ANCHOR};

            for (CatalystLayer layer : this.selectedLayers)
            {
                List<KeyframeChannel<?>> channelsToScan = new ArrayList<>();

                if (layer.expanded && layer.expandedProps != 0)
                {
                    /* Only scan visible expanded property channels */
                    for (int pBit : propBits)
                    {
                        if ((layer.expandedProps & pBit) != 0)
                        {
                            KeyframeChannel<?>[] propChannels = getChannelsForProp(layer, pBit);
                            for (KeyframeChannel<?> ch : propChannels)
                            {
                                if (ch != null)
                                {
                                    channelsToScan.add(ch);
                                }
                            }
                        }
                    }
                }
                else
                {
                    /* Scan all channels of the selected layer */
                    KeyframeChannel<?>[] allChannels = {
                        layer.channelPosX, layer.channelPosY,
                        layer.channelScaleX, layer.channelScaleY,
                        layer.channelRotation, layer.channelOpacity,
                        layer.channelAnchorX, layer.channelAnchorY
                    };
                    for (KeyframeChannel<?> ch : allChannels)
                    {
                        if (ch != null)
                        {
                            channelsToScan.add(ch);
                        }
                    }
                }

                for (KeyframeChannel<?> ch : channelsToScan)
                {
                    for (Keyframe<?> kf : ch.getKeyframes())
                    {
                        kfFrames.add(Math.round(kf.getTick()));
                    }
                }
            }
        }
        else
        {
            /* 3. Global fallback: scan all layers in the composition */
            for (CatalystLayer layer : comp.layers)
            {
                KeyframeChannel<?>[] allChannels = {
                    layer.channelPosX, layer.channelPosY,
                    layer.channelScaleX, layer.channelScaleY,
                    layer.channelRotation, layer.channelOpacity,
                    layer.channelAnchorX, layer.channelAnchorY
                };
                for (KeyframeChannel<?> ch : allChannels)
                {
                    if (ch == null)
                    {
                        continue;
                    }
                    for (Keyframe<?> kf : ch.getKeyframes())
                    {
                        kfFrames.add(Math.round(kf.getTick()));
                    }
                }
            }
        }

        if (kfFrames.isEmpty())
        {
            return;
        }

        Integer targetFrame = next ? kfFrames.higher(current) : kfFrames.lower(current);
        if (targetFrame != null && this.playheadSetter != null)
        {
            this.playheadSetter.accept(targetFrame);
        }
    }

    /** Adds a marker at the current playhead position. If one already exists there, removes it (toggle). */
    public void addMarkerAtPlayhead()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null) return;
        int frame = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;

        /* Toggle: if a marker already exists at this exact frame, remove it */
        for (int i = 0; i < comp.markers.size(); i++)
        {
            if (comp.markers.get(i).frame == frame)
            {
                comp.markers.remove(i);
                if (this.onModified != null) this.onModified.run();
                return;
            }
        }

        /* Add new marker with amber color */
        comp.markers.add(new CatalystComposition.CatalystMarker(frame, "", 0xFFE8C43A));
        if (this.onModified != null) this.onModified.run();
    }

    /* ─── Clip Operations (Cut, Delete, Drag) ────────── */

    public void cutSelected()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || this.selectedLayers.isEmpty()) return;
        if (this.onPreModify != null) this.onPreModify.run();

        int playhead = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;
        List<CatalystLayer> newlyCreated = new ArrayList<>();

        for (CatalystLayer layer : new ArrayList<>(this.selectedLayers))
        {
            if (playhead > layer.startFrame && playhead < layer.startFrame + layer.duration)
            {
                int oldDuration = layer.duration;
                int firstDuration = playhead - layer.startFrame;
                int secondDuration = oldDuration - firstDuration;

                layer.duration = firstDuration;

                /* Keep exact original name for second split part */
                CatalystLayer second = new CatalystLayer(layer.name, layer.layerType, layer.color);
                second.startFrame = playhead;
                second.duration = secondDuration;
                second.visible = layer.visible;
                second.locked = layer.locked;
                second.opacity = layer.opacity;
                second.blendMode = layer.blendMode;
                second.textColor = layer.textColor;
                second.fontSize = layer.fontSize;
                second.lineWrapping = layer.lineWrapping;
                second.shadow = layer.shadow;
                second.resourcePath = layer.resourcePath;
                second.volume = layer.volume;
                second.audioOffset = layer.audioOffset;
                second.mediaOffset = layer.mediaOffset + firstDuration;
                second.mediaDuration = layer.mediaDuration;
                second.posX = layer.posX;
                second.posY = layer.posY;
                second.scaleX = layer.scaleX;
                second.scaleY = layer.scaleY;
                second.rotation = layer.rotation;
                second.anchorX = layer.anchorX;
                second.anchorY = layer.anchorY;

                /* Split and transfer keyframes between the two halves */
                layer.splitChannelsAt(playhead, second);

                /* After Effects style: Insert immediately ABOVE the split layer! */
                int originalIndex = comp.layers.indexOf(layer);
                if (originalIndex >= 0)
                {
                    comp.layers.add(originalIndex, second);
                }
                else
                {
                    comp.layers.add(second);
                }
                newlyCreated.add(second);
            }
        }

        if (!newlyCreated.isEmpty())
        {
            this.clearSelection();
            for (CatalystLayer added : newlyCreated)
            {
                this.addSelected(added);
            }
            if (this.onModified != null)
            {
                this.onModified.run();
            }
        }
    }

    public void duplicateSelected()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || this.selectedLayers.isEmpty()) return;
        if (this.onPreModify != null) this.onPreModify.run();

        List<CatalystLayer> duplicates = new ArrayList<>();
        for (CatalystLayer layer : this.selectedLayers)
        {
            CatalystLayer dup = new CatalystLayer(layer.name + " Copy", layer.layerType, layer.color);
            dup.startFrame = layer.startFrame;
            dup.duration = layer.duration;
            dup.visible = layer.visible;
            dup.locked = layer.locked;
            dup.opacity = layer.opacity;
            dup.blendMode = layer.blendMode;
            dup.textColor = layer.textColor;
            dup.fontSize = layer.fontSize;
            dup.lineWrapping = layer.lineWrapping;
            dup.shadow = layer.shadow;
            dup.resourcePath = layer.resourcePath;
            dup.volume = layer.volume;
            dup.audioOffset = layer.audioOffset;
            dup.mediaOffset = layer.mediaOffset;
            dup.mediaDuration = layer.mediaDuration;
            dup.posX = layer.posX;
            dup.posY = layer.posY;
            dup.scaleX = layer.scaleX;
            dup.scaleY = layer.scaleY;
            dup.rotation = layer.rotation;
            dup.anchorX = layer.anchorX;
            dup.anchorY = layer.anchorY;

            /* Deep copy all keyframe channels and animation flags */
            layer.copyChannelsTo(dup);

            int idx = comp.layers.indexOf(layer);
            if (idx >= 0)
            {
                comp.layers.add(idx, dup);
            }
            else
            {
                comp.layers.add(dup);
            }
            duplicates.add(dup);
        }

        this.clearSelection();
        for (CatalystLayer d : duplicates)
        {
            this.addSelected(d);
        }

        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    public void addLayer(CatalystLayer.LayerType type, int tick)
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null) return;
        if (this.onPreModify != null) this.onPreModify.run();

        String typeStr = (type == CatalystLayer.LayerType.SCENE) ? "Film" : (type.name().substring(0, 1) + type.name().substring(1).toLowerCase());
        String name = typeStr + " " + (comp.layers.size() + 1);
        CatalystLayer layer = new CatalystLayer(name, type, type.defaultColor());
        layer.startFrame = tick;
        layer.duration = Math.max(60, comp.duration - tick);

        comp.layers.add(0, layer);
        this.setSelected(layer);

        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    public void deleteSelected()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || this.selectedLayers.isEmpty()) return;
        if (this.onPreModify != null) this.onPreModify.run();

        for (CatalystLayer sel : this.selectedLayers)
        {
            UICatalystPanel.cleanupLayerResources(sel);
        }

        comp.layers.removeAll(this.selectedLayers);
        this.clearSelection();
        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    public void rippleDeleteSelected()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || this.selectedLayers.isEmpty())
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        int minStart = Integer.MAX_VALUE;
        int maxEnd = Integer.MIN_VALUE;

        for (CatalystLayer sel : this.selectedLayers)
        {
            minStart = Math.min(minStart, sel.startFrame);
            maxEnd = Math.max(maxEnd, sel.startFrame + sel.duration);
        }

        int shiftAmount = maxEnd - minStart;

        for (CatalystLayer sel : this.selectedLayers)
        {
            UICatalystPanel.cleanupLayerResources(sel);
        }

        comp.layers.removeAll(this.selectedLayers);
        this.clearSelection();

        for (CatalystLayer remaining : comp.layers)
        {
            if (remaining.startFrame >= maxEnd)
            {
                remaining.startFrame = Math.max(minStart, remaining.startFrame - shiftAmount);
            }
        }

        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    public void rippleDeleteGap()
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || this.selectedGapStart < 0 || this.selectedGapDuration <= 0)
        {
            return;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        int gapStart = this.selectedGapStart;
        int gapDur = this.selectedGapDuration;

        for (CatalystLayer layer : comp.layers)
        {
            if (layer.startFrame >= gapStart + gapDur)
            {
                layer.startFrame = Math.max(gapStart, layer.startFrame - gapDur);
            }
        }

        this.selectedGapStart = -1;
        this.selectedGapDuration = -1;

        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    public boolean deleteSelectedKeyframes()
    {
        if (this.lastClickedKeyframes.isEmpty() || this.lastClickedChannels.isEmpty())
        {
            return false;
        }

        if (this.onPreModify != null)
        {
            this.onPreModify.run();
        }

        boolean deleted = false;
        for (KeyframeChannel<?> ch : this.lastClickedChannels)
        {
            if (ch == null)
            {
                continue;
            }

            List<? extends Keyframe<?>> keyframes = ch.getKeyframes();
            for (int i = keyframes.size() - 1; i >= 0; i--)
            {
                Keyframe<?> kf = keyframes.get(i);
                if (this.lastClickedKeyframes.contains(kf))
                {
                    ch.remove(i);
                    deleted = true;
                }
            }
            ch.sort();
        }

        if (this.lastClickedKfLayer != null)
        {
            this.lastClickedKfLayer.animPosX = !this.lastClickedKfLayer.channelPosX.isEmpty();
            this.lastClickedKfLayer.animPosY = !this.lastClickedKfLayer.channelPosY.isEmpty();
            this.lastClickedKfLayer.animScaleX = !this.lastClickedKfLayer.channelScaleX.isEmpty();
            this.lastClickedKfLayer.animScaleY = !this.lastClickedKfLayer.channelScaleY.isEmpty();
            this.lastClickedKfLayer.animRotation = !this.lastClickedKfLayer.channelRotation.isEmpty();
            this.lastClickedKfLayer.animOpacity = !this.lastClickedKfLayer.channelOpacity.isEmpty();
            this.lastClickedKfLayer.animAnchorX = !this.lastClickedKfLayer.channelAnchorX.isEmpty();
            this.lastClickedKfLayer.animAnchorY = !this.lastClickedKfLayer.channelAnchorY.isEmpty();
        }

        this.lastClickedKeyframes.clear();
        this.lastClickedChannels.clear();
        this.lastClickedKfLayer = null;

        if (deleted && this.onModified != null)
        {
            this.onModified.run();
        }

        return deleted;
    }

    public int snapTick(int targetTick)
    {
        CatalystComposition comp = this.getComposition();
        boolean snapActive = this.snapping ? !Window.isAltPressed() : Window.isAltPressed();

        if (comp == null || !snapActive)
        {
            return targetTick;
        }

        int targetX = this.toGraphX(targetTick);
        int bestDistance = SNAP_DISTANCE + 1;
        int snappedTick = targetTick;

        List<Integer> points = new ArrayList<>();
        int playhead = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;
        points.add(playhead);
        points.add(0);
        points.add(comp.duration);

        for (CatalystLayer other : comp.layers)
        {
            if (this.grabbedLayers.contains(other))
            {
                continue;
            }

            points.add(other.startFrame);
            points.add(other.startFrame + other.duration);

            /* Keyframe points of other layers */
            KeyframeChannel<?>[] allChannels = {
                other.channelPosX, other.channelPosY,
                other.channelScaleX, other.channelScaleY,
                other.channelRotation, other.channelOpacity,
                other.channelAnchorX, other.channelAnchorY
            };

            for (KeyframeChannel<?> ch : allChannels)
            {
                if (ch != null && !ch.isEmpty())
                {
                    for (Keyframe<?> kf : ch.getKeyframes())
                    {
                        points.add(Math.round(kf.getTick()));
                    }
                }
            }
        }

        for (int p : points)
        {
            int pX = this.toGraphX(p);
            int dist = Math.abs(targetX - pX);
            if (dist < bestDistance)
            {
                bestDistance = dist;
                snappedTick = p;
            }
        }

        return snappedTick;
    }

    private void dragLayers(int mouseX, int mouseY)
    {
        int rawDx = this.fromGraphTick(mouseX) - this.fromGraphTick(this.initialX);

        if (this.grabMode == 0)
        {
            /* Move with magnetic snapping */
            int dx = rawDx;

            if (!this.grabbedLayers.isEmpty() && !this.grabbedData.isEmpty())
            {
                Vector3i primaryData = this.grabbedData.get(0);
                int desiredStart = Math.max(0, primaryData.x() + rawDx);
                int desiredEnd   = desiredStart + primaryData.z();

                int snappedStart = this.snapTick(desiredStart);
                dx = snappedStart - primaryData.x();

                if (snappedStart == desiredStart)
                {
                    int snappedEnd = this.snapTick(desiredEnd);
                    if (snappedEnd != desiredEnd)
                    {
                        dx = (snappedEnd - primaryData.z()) - primaryData.x();
                    }
                }

                /* Multi-layer relative offset preservation: no layer can go below frame 0 */
                int minOriginalStart = Integer.MAX_VALUE;
                for (Vector3i data : this.grabbedData)
                {
                    if (data.x() < minOriginalStart)
                    {
                        minOriginalStart = data.x();
                    }
                }
                if (minOriginalStart + dx < 0)
                {
                    dx = -minOriginalStart;
                }
            }

            for (int i = 0; i < this.grabbedLayers.size(); i++)
            {
                CatalystLayer layer = this.grabbedLayers.get(i);
                Vector3i data = this.grabbedData.get(i);
                layer.startFrame = data.x() + dx;
            }

            /* Y-axis (Vertical) Layer Reordering */
            if (this.grabbedLayers.size() == 1)
            {
                CatalystLayer single = this.grabbedLayers.get(0);
                CatalystComposition comp = this.getComposition();
                if (comp != null && comp.layers.size() > 1)
                {
                    int targetRow = this.fromLayerY(mouseY);
                    if (targetRow >= 0 && targetRow < comp.layers.size())
                    {
                        int currentIndex = comp.layers.indexOf(single);
                        if (currentIndex >= 0 && currentIndex != targetRow)
                        {
                            comp.layers.remove(currentIndex);
                            comp.layers.add(targetRow, single);
                        }
                    }
                }
            }
        }
        else if (this.grabMode == 1)
        {
            /* Trim Left / Trim-In with media offset synchronization and magnetic snapping */
            for (int i = 0; i < this.grabbedLayers.size(); i++)
            {
                CatalystLayer layer = this.grabbedLayers.get(i);
                Vector3i data = this.grabbedData.get(i);
                int initialStart = data.x();
                int initialMediaOffset = data.y();
                int initialDuration = data.z();
                int initialEnd = initialStart + initialDuration;

                int desiredStart = initialStart + rawDx;
                int snappedStart = this.snapTick(desiredStart);

                /* Minimum startFrame bound by available source media before in-point */
                boolean isMedia = (layer.layerType == CatalystLayer.LayerType.VIDEO || layer.layerType == CatalystLayer.LayerType.AUDIO);
                int minStart = (isMedia && layer.mediaDuration > 0) ? Math.max(0, initialStart - initialMediaOffset) : 0;
                int maxStart = initialEnd - 1; /* Minimum duration 1 frame */

                int newStart = Math.max(minStart, Math.min(snappedStart, maxStart));
                int deltaFrames = newStart - initialStart;

                layer.startFrame = newStart;
                layer.duration = initialEnd - newStart;
                layer.mediaOffset = Math.max(0, initialMediaOffset + deltaFrames);
            }
        }
        else if (this.grabMode == 2)
        {
            /* Trim Right with physical media boundary clamping and magnetic snapping */
            for (int i = 0; i < this.grabbedLayers.size(); i++)
            {
                CatalystLayer layer = this.grabbedLayers.get(i);
                Vector3i data = this.grabbedData.get(i);
                int initialStart = data.x();
                int initialDuration = data.z();

                int desiredEnd = initialStart + initialDuration + rawDx;
                int snappedEnd = this.snapTick(desiredEnd);
                int newDuration = Math.max(1, snappedEnd - initialStart);

                boolean isMedia = (layer.layerType == CatalystLayer.LayerType.VIDEO || layer.layerType == CatalystLayer.LayerType.AUDIO);
                if (isMedia && layer.mediaDuration > 0)
                {
                    /* Physical media boundary: mediaOffset + duration <= mediaDuration */
                    int maxDuration = Math.max(1, layer.mediaDuration - layer.mediaOffset);
                    newDuration = Math.min(newDuration, maxDuration);
                }

                layer.duration = newDuration;
            }
        }
    }

    private void handleInput(int mouseX, int mouseY)
    {
        if (this.isDraggingKeyframe)
        {
            int rawTick = Math.max(0, this.fromGraphTick(mouseX));
            int snappedTick = this.snapTick(rawTick);
            int delta = snappedTick - this.dragInitialTick;

            /* Check boundary: ensure no keyframe goes below frame 0 */
            for (Keyframe<?> kf : this.draggedKeyframes)
            {
                Integer startTick = this.dragStartTicks.get(kf);
                if (startTick != null && startTick + delta < 0)
                {
                    delta = -startTick;
                }
            }

            for (Keyframe<?> kf : this.draggedKeyframes)
            {
                Integer startTick = this.dragStartTicks.get(kf);
                if (startTick != null)
                {
                    kf.setTick(startTick + delta);
                }
            }
            this.lastX = mouseX;
            this.lastY = mouseY;
        }
        else if (this.scrubbing)
        {
            this.setPlayheadFromMouse(mouseX);
        }
        else if (this.marquee.isPressed())
        {
            if (this.marquee.update(mouseX, mouseY))
            {
                this.captureSelection(this.marquee.getArea());
            }
        }
        else if (this.grabbing)
        {
            if (this.canGrab)
            {
                this.dragLayers(mouseX, mouseY);
                this.lastX = mouseX;
                this.lastY = mouseY;
            }
            else if (Math.abs(mouseX - this.initialX) > 1 || Math.abs(mouseY - this.initialY) > 1)
            {
                this.canGrab = true;
            }
        }
        else if (this.navigating)
        {
            this.dragTimeBy(mouseX - this.lastX);
            this.vertical.scrollBy(-(mouseY - this.lastY));
            this.vertical.clamp();

            this.lastX = mouseX;
            this.lastY = mouseY;
        }
    }

    private void setPlayheadFromMouse(int mouseX)
    {
        int frame = Math.max(0, this.fromGraphTick(mouseX));
        CatalystComposition comp = this.getComposition();
        if (comp != null && comp.duration > 0)
        {
            frame = Math.min(frame, comp.duration - 1);
        }

        if (this.playheadSetter != null)
        {
            this.playheadSetter.accept(frame);
        }
    }

    /* ─── Rendering (UIClips Style) ─────────────────── */

    @Override
    public void render(UIContext context)
    {
        if (this.grabbing || this.scrubbing || this.navigating || this.marquee.isPressed() || this.isDraggingKeyframe)
        {
            this.xAxis.stopZoom();
        }
        else
        {
            this.xAxis.updateZoom();
        }

        this.updateScrollSize();
        this.vertical.drag(context);
        this.handleInput(context.mouseX, context.mouseY);

        CatalystComposition comp = this.getComposition();

        /* Background */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.deepSurface());

        /* Tracks Area with Scissors */
        int rulerBottom = this.getRulerBottom();
        context.batcher.clipBox(this.vertical.area.x, rulerBottom, this.vertical.area.ex(), this.vertical.area.ey(), context);
        this.renderTracks(context, comp);
        context.batcher.unclip(context);

        /* Ruler */
        this.renderRuler(context, comp);

        /* Playhead Cursor */
        this.renderPlayhead(context, comp);

        /* Marquee Selection */
        this.renderMarquee(context);

        /* Vertical Scrollbar */
        context.batcher.clip(this.vertical.area, context);
        this.vertical.renderScrollbar(context.batcher);
        context.batcher.unclip(context);

        /* Edge hover & trim cursor */
        if (this.grabMode == 1 || this.grabMode == 2)
        {
            context.requestCursor(GLFW.GLFW_HRESIZE_CURSOR);
        }
        else if (!this.grabbing && !this.scrubbing && !this.marquee.isPressed())
        {
            CatalystLayer layerUnder = this.getLayerUnder(context, context.mouseX, context.mouseY);
            if (layerUnder != null)
            {
                int handle = this.getLayerHandle(layerUnder, context, this.getLayerHeight());
                if (handle == 1 || handle == 2)
                {
                    context.requestCursor(GLFW.GLFW_HRESIZE_CURSOR);
                }
            }
        }

        super.render(context);
    }

    private void renderTracks(UIContext context, CatalystComposition comp)
    {
        /* Selected gap highlight */
        if (this.selectedGapStart >= 0 && this.selectedGapDuration > 0)
        {
            int gx1 = this.toGraphX(this.selectedGapStart);
            int gx2 = this.toGraphX(this.selectedGapStart + this.selectedGapDuration);
            int top = this.getRulerBottom();
            int bot = this.vertical.area.ey();
            context.batcher.box(gx1, top, gx2, bot, 0x3355AAFF);
            context.batcher.outline(gx1, top, gx2, bot, 0xAA55AAFF, 1);
        }

        int layerCount = comp != null ? comp.layers.size() : 0;
        int leftEdge = this.toGraphX(0);

        /* Zebra row backgrounds (now accounting for expanded layers) */
        for (int i = 0; i < layerCount; i++)
        {
            CatalystLayer layer = comp.layers.get(i);
            int ly = this.toLayerY(i);
            int rowH = this.getExpandedLayerHeight(layer);
            if (i % 2 != 0)
            {
                context.batcher.box(leftEdge, ly, this.area.ex(), ly + LAYER_ROW_H, BBSSettings.baseSurface());
            }
            context.batcher.box(this.area.x, ly + LAYER_ROW_H - 1, this.area.ex(), ly + LAYER_ROW_H, 0xFF222228);

            /* Property sub-row backgrounds & Keyframe Diamonds */
            if (layer.expanded && layer.expandedProps != 0)
            {
                int py = ly + LAYER_ROW_H;
                int bits = layer.expandedProps;
                String[] propNames = {"Position", "Scale", "Rotation", "Opacity", "Anchor"};
                int[] propBits   = {PROP_POSITION, PROP_SCALE, PROP_ROTATION, PROP_OPACITY, PROP_ANCHOR};
                for (int p = 0; p < propBits.length; p++)
                {
                    if ((bits & propBits[p]) == 0) continue;
                    context.batcher.box(leftEdge, py, this.area.ex(), py + PROP_ROW_H - 1, 0xFF16161A);
                    context.batcher.box(this.area.x, py + PROP_ROW_H - 1, this.area.ex(), py + PROP_ROW_H, 0xFF222228);
                    /* Property name label (clipped by left edge of graph) */
                    context.batcher.text(propNames[p], leftEdge + 4, py + (PROP_ROW_H - context.batcher.getFont().getHeight()) / 2, 0xFF8899AA, false);

                    /* Keyframe Diamonds (◆) for this property */
                    KeyframeChannel<?>[] channels = getChannelsForProp(layer, propBits[p]);

                    java.util.Set<Integer> renderedTicks = new java.util.HashSet<>();
                    for (KeyframeChannel<?> ch : channels)
                    {
                        if (ch == null) continue;
                        for (Keyframe<?> kf : ch.getKeyframes())
                        {
                            int kfTick = Math.round(kf.getTick());
                            if (!renderedTicks.add(kfTick)) continue;

                            int kx = this.toGraphX(kfTick);
                            if (kx >= this.area.x - 6 && kx <= this.area.ex() + 6)
                            {
                                int midY = py + PROP_ROW_H / 2;
                                boolean isDragged = this.isDraggingKeyframe && this.draggedKeyframes.stream().anyMatch(k -> Math.round(k.getTick()) == kfTick);
                                boolean isSelected = this.lastClickedKfLayer == layer && this.lastClickedKeyframes.stream().anyMatch(k -> Math.round(k.getTick()) == kfTick);
                                int diamondCol = isDragged ? 0xFFFFCC00 : (isSelected ? 0xFFFFEE55 : 0xFF55AAFF);
                                /* Draw diamond ◆ */
                                context.batcher.box(kx, midY - 4, kx + 1, midY + 5, diamondCol);
                                context.batcher.box(kx - 1, midY - 3, kx + 2, midY + 4, diamondCol);
                                context.batcher.box(kx - 2, midY - 2, kx + 3, midY + 3, diamondCol);
                                context.batcher.box(kx - 3, midY - 1, kx + 4, midY + 2, diamondCol);
                                context.batcher.box(kx - 4, midY, kx + 5, midY + 1, diamondCol);
                            }
                        }
                    }

                    py += PROP_ROW_H;
                }
            }
        }

        /* Clips */
        if (comp != null)
        {
            for (CatalystLayer layer : comp.layers)
            {
                int h = LAYER_ROW_H;
                Area clipArea = this.getLayerArea(layer, TEMP_AREA, h);
                /* Horizontal and Vertical Culling */
                if (clipArea.ex() < this.area.x || clipArea.x > this.area.ex() ||
                    clipArea.ey() < this.area.y + 20 || clipArea.y > this.area.ey())
                {
                    continue;
                }

                boolean selected = this.isSelected(layer);
                int clipColor = layer.visible ? (layer.color | 0xCC000000) : 0x66666688;
                /* Dim muted clips */
                if (layer.muted) clipColor = (clipColor & 0xFFFFFF) | 0x55000000;

                context.batcher.box(clipArea.x, clipArea.y + 2, clipArea.ex(), clipArea.ey() - 2, clipColor);

                /* Waveform rendering for AUDIO layers (Cached & Lazy-loaded) */
                if (layer.layerType == CatalystLayer.LayerType.AUDIO)
                {
                    if (layer.cachedWaveform instanceof Waveform wf && wf.isCreated())
                    {
                        int fps = (comp != null && comp.fps > 0) ? comp.fps : 60;
                        float startTime = (float) (layer.mediaOffset + layer.audioOffset) / fps;
                        float endTime = (float) (layer.mediaOffset + layer.audioOffset + layer.duration) / fps;
                        try
                        {
                            wf.render(context.batcher, 0x66FFFFFF, clipArea.x, clipArea.y + 2, clipArea.w, clipArea.h - 4, startTime, endTime);
                        }
                        catch (Exception ignored) {}
                    }
                    else
                    {
                        ensureWaveformLoaded(layer);

                        int midY = clipArea.y + clipArea.h / 2;
                        for (int bx = clipArea.x + 2; bx < clipArea.ex() - 2; bx += 4)
                        {
                            int barH = (int) (Math.sin((bx + layer.startFrame) * 0.15) * 5 + 6);
                            context.batcher.box(bx, midY - barH, bx + 2, midY + barH, 0x33FFFFFF);
                        }
                    }
                }

                if (selected)
                {
                    context.batcher.outline(clipArea.x, clipArea.y + 2, clipArea.ex(), clipArea.ey() - 2, Colors.WHITE, 1);
                }
                else if (clipArea.isInside(context) && !this.grabbing && !this.marquee.isPressed())
                {
                    context.batcher.outline(clipArea.x, clipArea.y + 2, clipArea.ex(), clipArea.ey() - 2, 0x88FFFFFF, 1);
                }

                /* Edge handles */
                int handle = this.getLayerHandle(layer, context, h);
                int handleColor = this.grabMode != 0 ? Colors.WHITE : 0x88FFFFFF;

                if (handle == 1 || (selected && this.grabMode == 1))
                {
                    context.batcher.icon(Icons.CLIP_HANLDE_LEFT, handleColor, clipArea.x, clipArea.y + 10, 0F, 0.5F);
                }
                else if (handle == 2 || (selected && this.grabMode == 2))
                {
                    context.batcher.icon(Icons.CLIP_HANLDE_RIGHT, handleColor, clipArea.ex(), clipArea.y + 10, 1F, 0.5F);
                }

                /* Clip Label */
                FontRenderer font = context.batcher.getFont();
                int textX = clipArea.x + 4;
                int textY = clipArea.y + (h - font.getHeight()) / 2;
                if (clipArea.w > 20)
                {
                    String clipped = font.limitToWidth(layer.name, clipArea.w - 8);
                    context.batcher.text(clipped, textX, textY, Colors.WHITE, false);
                }
            }
        }
    }

    private void renderRuler(UIContext context, CatalystComposition comp)
    {
        int duration = comp != null ? comp.duration : 300;

        TimelineRulerRenderer.render(
            context,
            this.area,
            (int) this.xAxis.getMinValue(),
            duration,
            this::toGraphX,
            (tick) ->
            {
                int fps = (comp != null && comp.fps > 0) ? comp.fps : 60;
                int s = tick / fps;
                int f = tick % fps;
                return s + ":" + String.format("%02d", f);
            }
        );

        /* Draw timeline markers above the ruler baseline */
        if (comp != null && !comp.markers.isEmpty())
        {
            int rulerBottom = this.getRulerBottom();
            FontRenderer font = context.batcher.getFont();
            for (CatalystComposition.CatalystMarker marker : comp.markers)
            {
                int mx = this.toGraphX(marker.frame);
                if (mx < this.area.x || mx > this.area.ex()) continue;

                int mc = marker.color | Colors.A100;

                /* Thin vertical stripe from ruler top to bottom */
                context.batcher.box(mx, this.area.y, mx + 1, rulerBottom, mc);

                /* Small filled triangle / flag head at the top */
                context.batcher.box(mx, this.area.y, mx + 6, this.area.y + 4, mc);
                context.batcher.box(mx, this.area.y + 4, mx + 4, this.area.y + 6, mc);
                context.batcher.box(mx, this.area.y + 6, mx + 2, this.area.y + 8, mc);

                /* Label to the right of the flag (only if non-empty) */
                if (marker.label != null && !marker.label.isEmpty())
                {
                    context.batcher.text(marker.label, mx + 3, this.area.y + 1, mc, false);
                }
            }
        }

        /* Snapping Magnet Toolbar Button */
        int btnX = this.area.x + 4;
        int btnY = this.area.y + 2;
        int btnW = 18;
        int btnH = 16;
        boolean btnHovered = context.mouseX >= btnX && context.mouseX < btnX + btnW && context.mouseY >= btnY && context.mouseY < btnY + btnH;

        int btnBg = this.snapping ? (btnHovered ? 0x6655AAFF : 0x3355AAFF) : (btnHovered ? 0x44FFFFFF : 0x22000000);
        int btnBorder = this.snapping ? 0xFF55AAFF : 0x66FFFFFF;
        int btnIcon = this.snapping ? Colors.WHITE : 0x77FFFFFF;

        context.batcher.box(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
        context.batcher.outline(btnX, btnY, btnX + btnW, btnY + btnH, btnBorder, 1);
        context.batcher.icon(Icons.MAGNET, btnIcon, btnX + 1, btnY);
    }

    @Override
    public void renderTooltip(UIContext context, Area area)
    {
        int btnX = this.area.x + 4;
        int btnY = this.area.y + 2;
        int btnW = 18;
        int btnH = 16;

        if (context.mouseX >= btnX && context.mouseX < btnX + btnW && context.mouseY >= btnY && context.mouseY < btnY + btnH)
        {
            String text = "Snapping (N)";
            FontRenderer font = context.batcher.getFont();
            Area card = TooltipPlacement.nearMouse(context, font.getWidth(text) + 6, font.getHeight() + 6, 2, true, 0, Area.SHARED);
            context.batcher.textCard(text, card.x + 3, card.y + 3);
            return;
        }

        super.renderTooltip(context, area);
    }


    private void renderPlayhead(UIContext context, CatalystComposition comp)
    {
        int playhead = this.playheadSupplier != null ? this.playheadSupplier.getAsInt() : 0;
        int phX = this.toGraphX(playhead);

        if (phX >= this.area.x && phX < this.area.ex())
        {
            int primary = BBSSettings.primaryColor.get() | Colors.A100;
            int rulerBottom = this.getRulerBottom();

            /* Vertical line across tracks */
            context.batcher.box(phX, this.area.y, phX + 1, this.area.ey(), primary);

            /* Triangle cap at ruler */
            context.batcher.box(phX - 3, rulerBottom - 5, phX + 4, rulerBottom, primary);

            /* SMPTE time label */
            int fps = (comp != null && comp.fps > 0) ? comp.fps : 60;
            int duration = comp != null ? comp.duration : 300;
            int secs = playhead / fps;
            int frames = playhead % fps;
            String label = String.format("%d:%02d [%d/%d]", secs, frames, playhead, duration);

            UITimelineCanvas.renderCursor(context, label, this.area, phX);
        }
    }

    public static void ensureWaveformLoaded(CatalystLayer layer)
    {
        if (layer.layerType != CatalystLayer.LayerType.AUDIO) return;
        if (layer.resourcePath == null || layer.resourcePath.trim().isEmpty())
        {
            layer.cachedWaveform = null;
            layer.cachedWaveformPath = null;
            return;
        }

        String path = layer.resourcePath.trim();
        if (path.equals(layer.cachedWaveformPath) && (layer.cachedWaveform != null || layer.isWaveformLoading))
        {
            return;
        }

        layer.cachedWaveformPath = path;
        layer.cachedWaveform = null;
        layer.isWaveformLoading = true;

        new Thread(() ->
        {
            try
            {
                File f = new File(path);
                Wave wave = null;
                if (f.exists() && f.isFile())
                {
                    wave = AudioReader.readWave(f);
                }
                if (wave == null)
                {
                    Link link = Link.create(path);
                    SoundBuffer buffer = BBSModClient.getSounds().get(link, true);
                    if (buffer != null && buffer.getWaveform() != null && buffer.getWaveform().isCreated())
                    {
                        layer.cachedWaveform = buffer.getWaveform();
                        return;
                    }
                    wave = AudioReader.read(BBSMod.getProvider(), link);
                }
                if (wave != null)
                {
                    Waveform wf = new Waveform();
                    wf.generate(wave, null, BBSSettings.audioWaveformDensity.get(), 40);
                    layer.cachedWaveform = wf;
                }
            }
            catch (Throwable ignored)
            {
                /* Silently swallow EOFException, FileNotFoundException, etc. */
            }
            finally
            {
                layer.isWaveformLoading = false;
            }
        }, "CatalystWaveformLoader-" + layer.id).start();
    }
}
