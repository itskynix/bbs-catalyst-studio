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
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

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
    private static final int LAYER_ROW_H = 24;
    private static final int SNAP_DISTANCE = 6; // pixels tolerance for magnetic snapping
    private static final Area TEMP_AREA = new Area();

    /* Vertical scrolling for track lanes */
    public Scroll vertical = new Scroll(new Area());

    /* Data bindings */
    public Supplier<CatalystComposition> compSupplier;
    public IntSupplier                   playheadSupplier;
    public IntConsumer                   playheadSetter;
    public Runnable                      onScrub;
    public Runnable                      onModified;
    public Runnable                      onPreModify;

    /* Interaction state replicated from UIClips */
    private boolean scrubbing;
    private boolean grabbing;
    private boolean canGrab;
    private int grabMode = 0; // 0 = move, 1 = trim left, 2 = trim right

    private final Set<CatalystLayer> selectedLayers = new LinkedHashSet<>();
    private List<CatalystLayer> grabbedLayers = Collections.emptyList();
    private final List<Vector3i> grabbedData = new ArrayList<>();

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

    public int getRulerBottom()
    {
        return TimelineRulerRenderer.getRulerBottom(this.area);
    }

    public boolean isInRuler(int mouseY)
    {
        return mouseY >= this.area.y && mouseY < this.getRulerBottom();
    }

    public int toLayerY(int layerIndex)
    {
        int rulerBottom = this.getRulerBottom();
        return rulerBottom + layerIndex * this.getLayerHeight() - (int) this.vertical.getScroll();
    }

    public int fromLayerY(int mouseY)
    {
        int rulerBottom = this.getRulerBottom();
        if (mouseY < rulerBottom) return -1;
        return (mouseY - rulerBottom + (int) this.vertical.getScroll()) / this.getLayerHeight();
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
        int count = comp != null ? comp.layers.size() : 0;
        this.vertical.scrollSize = count * this.getLayerHeight();
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
        /* 1. Track Area Click (NOT in Ruler) */
        if (!this.isInRuler(mouseY))
        {
            CatalystLayer layer = this.getLayerUnder(context, mouseX, mouseY);

            if (layer != null)
            {
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
                    this.grabbedData.add(new Vector3i(layer.startFrame, this.getLayerIndex(layer), layer.duration));
                }
                else
                {
                    if (shift)
                    {
                        this.toggleSelected(layer);
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
                        this.grabbedData.add(new Vector3i(sel.startFrame, this.getLayerIndex(sel), sel.duration));
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

                this.marquee.press(mouseX, mouseY);
                this.setMouse(mouseX, mouseY);
                return true;
            }
        }

        /* 2. Ruler Area Click -> Scrubbing */
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
        if (context.isPressed(GLFW.GLFW_KEY_C))
        {
            this.cutSelected();
            return true;
        }
        if (context.isPressed(GLFW.GLFW_KEY_DELETE))
        {
            this.deleteSelected();
            return true;
        }

        return super.subKeyPressed(context);
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

        comp.layers.removeAll(this.selectedLayers);
        this.clearSelection();
        if (this.onModified != null)
        {
            this.onModified.run();
        }
    }

    private int snapTick(int targetTick)
    {
        CatalystComposition comp = this.getComposition();
        if (comp == null || Window.isAltPressed())
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
            if (this.grabbedLayers.contains(other)) continue;
            points.add(other.startFrame);
            points.add(other.startFrame + other.duration);
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
            }

            for (int i = 0; i < this.grabbedLayers.size(); i++)
            {
                CatalystLayer layer = this.grabbedLayers.get(i);
                Vector3i data = this.grabbedData.get(i);
                layer.startFrame = Math.max(0, data.x() + dx);
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
            /* Trim Left with magnetic snapping */
            for (int i = 0; i < this.grabbedLayers.size(); i++)
            {
                CatalystLayer layer = this.grabbedLayers.get(i);
                Vector3i data = this.grabbedData.get(i);
                int end = data.x() + data.z();
                int desiredStart = data.x() + rawDx;
                int snappedStart = this.snapTick(desiredStart);
                int newStart = Math.max(0, Math.min(snappedStart, end - 1));
                layer.startFrame = newStart;
                layer.duration = end - newStart;
            }
        }
        else if (this.grabMode == 2)
        {
            /* Trim Right with magnetic snapping */
            for (int i = 0; i < this.grabbedLayers.size(); i++)
            {
                CatalystLayer layer = this.grabbedLayers.get(i);
                Vector3i data = this.grabbedData.get(i);
                int desiredEnd = data.x() + data.z() + rawDx;
                int snappedEnd = this.snapTick(desiredEnd);
                int newDuration = Math.max(1, snappedEnd - data.x());
                if (layer.mediaDuration > 0)
                {
                    newDuration = Math.min(newDuration, layer.mediaDuration);
                }
                layer.duration = newDuration;
            }
        }
    }

    private void handleInput(int mouseX, int mouseY)
    {
        if (this.scrubbing)
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
        if (this.grabbing || this.scrubbing || this.navigating || this.marquee.isPressed())
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

        super.render(context);
    }

    private void renderTracks(UIContext context, CatalystComposition comp)
    {
        int h = this.getLayerHeight();
        int layerCount = comp != null ? comp.layers.size() : 0;
        int leftEdge = this.toGraphX(0);

        /* Zebra row backgrounds */
        for (int i = 0; i < layerCount; i++)
        {
            int ly = this.toLayerY(i);
            if (i % 2 != 0)
            {
                context.batcher.box(leftEdge, ly, this.area.ex(), ly + h, BBSSettings.baseSurface());
            }
            context.batcher.box(this.area.x, ly + h - 1, this.area.ex(), ly + h, 0xFF222228);
        }

        /* Clips */
        if (comp != null)
        {
            for (CatalystLayer layer : comp.layers)
            {
                Area clipArea = this.getLayerArea(layer, TEMP_AREA, h);
                /* Horizontal and Vertical Culling */
                if (clipArea.ex() < this.area.x || clipArea.x > this.area.ex() ||
                    clipArea.ey() < this.area.y + 20 || clipArea.y > this.area.ey())
                {
                    continue;
                }

                boolean selected = this.isSelected(layer);
                int clipColor = layer.visible ? (layer.color | 0xCC000000) : 0x66666688;

                context.batcher.box(clipArea.x, clipArea.y + 2, clipArea.ex(), clipArea.ey() - 2, clipColor);

                /* Waveform rendering for AUDIO layers (Cached & Lazy-loaded) */
                if (layer.layerType == CatalystLayer.LayerType.AUDIO)
                {
                    if (layer.cachedWaveform instanceof Waveform wf && wf.isCreated())
                    {
                        int fps = (comp != null && comp.fps > 0) ? comp.fps : 60;
                        float startTime = (float) layer.audioOffset / fps;
                        float endTime = (float) (layer.audioOffset + layer.duration) / fps;
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
                Link link = Link.create(path);
                SoundBuffer buffer = BBSModClient.getSounds().get(link, true);
                if (buffer != null && buffer.getWaveform() != null && buffer.getWaveform().isCreated())
                {
                    layer.cachedWaveform = buffer.getWaveform();
                }
                else
                {
                    Wave wave = AudioReader.read(BBSMod.getProvider(), link);
                    if (wave != null)
                    {
                        Waveform wf = new Waveform();
                        wf.generate(wave, null, BBSSettings.audioWaveformDensity.get(), 40);
                        layer.cachedWaveform = wf;
                    }
                }
            }
            catch (Throwable ignored)
            {
                // Silently swallow EOFException, FileNotFoundException, etc.
            }
            finally
            {
                layer.isWaveformLoading = false;
            }
        }, "CatalystWaveformLoader-" + layer.id).start();
    }
}
