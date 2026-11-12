package mchorse.bbs_mod.ui.film.world;

import mchorse.bbs.compat.mtr.MtrCompatHelper;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.film.replays.RecordFrame;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.world.MtrDispatchTrack;
import mchorse.bbs_mod.film.world.WorldEventBlock;
import mchorse.bbs_mod.film.world.WorldTimeline;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.markers.UIMarkerOverlayPanel;
import mchorse.bbs_mod.ui.film.markers.UIMarkersController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Comparator;
import java.util.List;

/**
 * World Timeline canvas inheriting ruler rendering, tick grid, zoom/pan, and scrubbing from {@link UITimelineCanvas}.
 * Provides categorized MTR operation blocks and embedded {@link UISubDopeSheet} editing.
 */
public class UIWorldTimeline extends UITimelineCanvas
{
    private static final int MARGIN = 10;
    private static final int LAYER_HEIGHT_MIN = 12;
    private static final int LAYER_HEIGHT_MAX = 48;
    private static final long DOUBLE_CLICK_MS = 300L;

    private static final Area BLOCK_AREA = new Area();

    public final UIWorldTimelinePanel panel;
    public final Scroll vertical = new Scroll(new Area());

    private WorldTimeline timeline;
    private WorldEventBlock selectedBlock;

    private final UIMarkersController markers = new UIMarkersController(this::getFilmMarkers);

    private UIIcon embeddedClose;
    private UIElement embedded;

    private int layers = 20;
    private int layerHeight = 20;
    private boolean centerScrollOnRender;

    private WorldEventBlock lastClickedBlock;
    private long lastBlockClickTime;

    private boolean scrubbing;
    private boolean grabbing;
    private int grabMode;
    private static WorldEventBlock clipboardBlock;

    private int grabStartTick;
    private int grabStartDuration;
    private int grabStartLayer;
    private int grabKeyframeIndex = -1;
    private int grabStartKeyframeTick;

    public UIWorldTimeline(UIWorldTimelinePanel panel)
    {
        super();

        this.panel = panel;

        this.vertical.smoothScrolling(() -> !BBSSettings.scrollingDisableSmoothnessInEditors.get());
        this.vertical.wheelScrollStep(this::getLayerHeight);

        this.embeddedClose = new UIIcon(Icons.CLOSE, (b) -> this.embedView(null));
        this.embeddedClose.relative(this);

        this.context((menu) ->
        {
            UIContext context = this.getContext();
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;

            if (this.hasEmbeddedView())
            {
                return;
            }

            if (this.addMarkerOptions(menu, mouseX, mouseY))
            {
                return;
            }

            WorldEventBlock underMouse = this.getBlockUnder(mouseX, mouseY);

            if (underMouse != null)
            {
                this.setSelectedBlock(underMouse);
                this.panel.pickBlock(underMouse);
            }

            int clickTick = Math.max(0, this.fromGraphTick(mouseX));
            int clickLayer = Math.max(0, this.fromLayerY(mouseY));
            boolean hasSelected = this.selectedBlock != null;

            /* Top icon bar: Add, Remove, Copy, Paste matching UIClips */
            menu.icon(MenuVerb.ADD, () -> this.showAdds(mouseX, mouseY)).label(UIKeys.CAMERA_TIMELINE_CONTEXT_ADD);
            menu.icon(MenuVerb.REMOVE, () ->
            {
                if (this.selectedBlock != null)
                {
                    this.deleteBlock(this.selectedBlock);
                }
            }).label(UIKeys.CAMERA_TIMELINE_CONTEXT_REMOVE_CLIPS).enabled(hasSelected);

            if (hasSelected)
            {
                WorldEventBlock targetBlock = this.selectedBlock;

                menu.icon(MenuVerb.COPY, () ->
                {
                    clipboardBlock = targetBlock.copy();
                    UIUtils.playClick();
                }).label(UIKeys.WORLD_TIMELINE_CONTEXT_COPY_BLOCK);
            }

            if (clipboardBlock != null)
            {
                menu.icon(MenuVerb.PASTE, () ->
                {
                    this.pasteBlockAt(clickTick, clickLayer);
                }).label(UIKeys.GENERAL_PASTE);
            }

            if (hasSelected)
            {
                WorldEventBlock targetBlock = this.selectedBlock;

                menu.action(Icons.EDIT, UIKeys.WORLD_TIMELINE_CONTEXT_EDIT_SUB_DOPE, () ->
                {
                    this.panel.openSubDopeSheet(targetBlock);
                });
                menu.action(Icons.CUT, UIKeys.CAMERA_TIMELINE_CONTEXT_CUT, () ->
                {
                    this.cutBlockAtCursor(targetBlock);
                });
                menu.action(Icons.MOVE_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT, () ->
                {
                    this.shiftBlockToCursor(targetBlock);
                });
                menu.action(Icons.SHIFT_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, () ->
                {
                    this.extendBlockDurationToCursor(targetBlock);
                });
                menu.action(Icons.TIME, UIKeys.WORLD_TIMELINE_CONTEXT_SET_DURATION, () ->
                {
                    this.promptSetDuration(targetBlock);
                });
            }

            if (this.timeline != null && !this.timeline.getBlocks().isEmpty())
            {
                menu.action(Icons.EXCHANGE, UIKeys.CAMERA_TIMELINE_CONTEXT_REORGANIZE, this::organizeLayers);
            }
        });

        this.keys().register(Keys.KEYFRAMES_MAXIMIZE, this::resetView);
        this.keys().register(Keys.DELETE, () ->
        {
            if (this.selectedBlock != null && !this.hasEmbeddedView())
            {
                this.deleteBlock(this.selectedBlock);
            }
        });
    }

    private void showAdds(int mouseX, int mouseY)
    {
        UIContext context = this.getContext();
        int clickTick = Math.max(0, this.fromGraphTick(mouseX));
        int clickLayer = Math.max(0, this.fromLayerY(mouseY));
        int cursorTick = Math.max(0, this.getCursor());
        Runnable reopenAdds = () -> this.showAdds(mouseX, mouseY);

        context.replaceContextMenu((menu) ->
        {
            menu.icon(MenuVerb.BACK, () ->
            {
                UIContextMenu backMenu = this.createContextMenu(context);

                if (backMenu != null && !backMenu.isEmpty())
                {
                    context.setContextMenu(backMenu);
                }
            });

            menu.action(Icons.CURSOR, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_CURSOR, () ->
            {
                this.showClipTypeMenu(clickTick, clickLayer, reopenAdds);
            });
            menu.action(Icons.SHIFT_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_TICK, () ->
            {
                this.showClipTypeMenu(cursorTick, clickLayer, reopenAdds);
            });

            if (this.selectedBlock != null)
            {
                WorldEventBlock topTarget = this.selectedBlock;

                menu.action(Icons.UPLOAD, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_ON_TOP, () ->
                {
                    this.showClipTypeMenu(topTarget.startTick, topTarget.layer + 1, reopenAdds);
                });
            }

            menu.action(Icons.EDITOR, UIKeys.CAMERA_TIMELINE_CONTEXT_FROM_PLAYER_RECORDING, () ->
            {
                this.createBlockFromPlayerRecording(clickTick, clickLayer);
            });
        });
    }

    private void showClipTypeMenu(int startTick, int layer, Runnable onBack)
    {
        UIContext context = this.getContext();

        context.replaceContextMenu(new UIWorldClipTypeMenu((entry) ->
        {
            this.addOperationFromEntry(entry, startTick, layer);
        }, onBack));
    }

    public void addOperationFromEntry(WorldTimelineAddonRegistry.OperationEntry entry, int startTick, int layer)
    {
        if (this.timeline == null || entry == null || entry.factory == null)
        {
            return;
        }

        int defaultDuration = Math.max(120, BBSSettings.getDefaultDuration() * 2);
        WorldEventBlock block = entry.factory.create(startTick, defaultDuration, layer);

        if (block == null)
        {
            return;
        }

        String detectedSiding = MtrCompatHelper.detectNearestVehicleOrSidingId();

        if (detectedSiding != null && !detectedSiding.isEmpty())
        {
            block.subDopeSheet.getPrimaryTrack().sidingId = detectedSiding;
        }

        this.timeline.addBlock(block);
        this.updateLayers();
        this.updateScrollSize();
        this.setSelectedBlock(block);
        this.panel.pickBlock(block);
        UIUtils.playClick();
    }

    public void addMtrOperationBlock(String operationType, int startTick, int layer)
    {
        if (this.timeline == null)
        {
            return;
        }

        int defaultDuration = Math.max(120, BBSSettings.getDefaultDuration() * 2);
        WorldEventBlock block;

        if (WorldEventBlock.TYPE_FLIGHT_OPERATION.equals(operationType))
        {
            block = WorldEventBlock.createFlightOperation(UIKeys.WORLD_TIMELINE_CONTEXT_FLIGHT_OPERATION.get(), startTick, defaultDuration, layer);
        }
        else if (WorldEventBlock.TYPE_STATION_DOORS.equals(operationType))
        {
            block = WorldEventBlock.createStationDoors(UIKeys.WORLD_TIMELINE_CONTEXT_STATION_DOORS.get(), startTick, defaultDuration, layer);
        }
        else
        {
            block = WorldEventBlock.createDispatchTrain(UIKeys.WORLD_TIMELINE_CONTEXT_DISPATCH_TRAIN.get(), startTick, defaultDuration, layer);
        }

        String detectedSiding = MtrCompatHelper.detectNearestVehicleOrSidingId();

        if (detectedSiding != null && !detectedSiding.isEmpty())
        {
            block.subDopeSheet.getPrimaryTrack().sidingId = detectedSiding;
        }

        this.timeline.addBlock(block);
        this.updateLayers();
        this.updateScrollSize();
        this.setSelectedBlock(block);
        this.panel.pickBlock(block);
        UIUtils.playClick();
    }

    private void pasteBlockAt(int startTick, int layer)
    {
        if (this.timeline == null || clipboardBlock == null)
        {
            return;
        }

        WorldEventBlock copy = clipboardBlock.copy();

        copy.startTick = Math.max(0, startTick);
        copy.layer = Math.max(0, layer);

        this.timeline.addBlock(copy);
        this.updateLayers();
        this.updateScrollSize();
        this.setSelectedBlock(copy);
        this.panel.pickBlock(copy);
        UIUtils.playClick();
    }

    private void cutBlockAtCursor(WorldEventBlock block)
    {
        if (this.timeline == null || block == null)
        {
            return;
        }

        int cursor = this.getCursor();

        if (cursor <= block.startTick || cursor >= block.getEndTick())
        {
            return;
        }

        this.markDirtyBeforeEdit();

        int firstDuration = cursor - block.startTick;
        int secondDuration = block.getEndTick() - cursor;

        WorldEventBlock secondHalf = block.copy();

        block.duration = Math.max(1, firstDuration);
        secondHalf.startTick = cursor;
        secondHalf.duration = Math.max(1, secondDuration);

        this.timeline.addBlock(secondHalf);
        this.updateLayers();
        this.updateScrollSize();
        this.setSelectedBlock(secondHalf);
        this.panel.pickBlock(secondHalf);
        this.notifyEdited();
        UIUtils.playClick();
    }

    private void shiftBlockToCursor(WorldEventBlock block)
    {
        if (this.timeline == null || block == null)
        {
            return;
        }

        this.markDirtyBeforeEdit();
        block.startTick = Math.max(0, this.getCursor());
        this.notifyEdited();
        UIUtils.playClick();
    }

    private void extendBlockDurationToCursor(WorldEventBlock block)
    {
        if (this.timeline == null || block == null)
        {
            return;
        }

        int cursor = this.getCursor();

        if (cursor > block.startTick)
        {
            this.markDirtyBeforeEdit();
            block.duration = Math.max(1, cursor - block.startTick);
            this.notifyEdited();
            UIUtils.playClick();
        }
    }

    private void organizeLayers()
    {
        if (this.timeline == null || this.timeline.getBlocks().isEmpty())
        {
            return;
        }

        this.markDirtyBeforeEdit();

        List<WorldEventBlock> blocks = this.timeline.getBlocks();

        blocks.sort(Comparator.comparingInt((WorldEventBlock b) -> b == null ? 0 : b.startTick));

        for (int i = 0; i < blocks.size(); i++)
        {
            WorldEventBlock block = blocks.get(i);

            if (block == null)
            {
                continue;
            }

            int targetLayer = 0;

            for (int j = 0; j < i; j++)
            {
                WorldEventBlock prev = blocks.get(j);

                if (prev != null && prev.layer == targetLayer && prev.getEndTick() > block.startTick && prev.startTick < block.getEndTick())
                {
                    targetLayer = prev.layer + 1;
                }
            }

            block.layer = targetLayer;
        }

        this.updateLayers();
        this.updateScrollSize();
        this.notifyEdited();
        UIUtils.playClick();
    }

    private void createBlockFromPlayerRecording(int fallbackTick, int layer)
    {
        if (this.timeline == null)
        {
            return;
        }

        Film film = this.panel.getFilm();

        if (film != null)
        {
            for (Replay replay : film.replays.getList())
            {
                if (replay == null)
                {
                    continue;
                }

                int duration = film.calculateDuration();
                int firstRideTick = -1;
                int lastRideTick = -1;
                RecordFrame firstFrame = null;

                for (int t = 0; t <= duration; t++)
                {
                    RecordFrame f = replay.keyframes.getMtrFrame(t);

                    if (f != null && f.isMtrRiding && f.mtrVehicleId != 0L)
                    {
                        if (firstRideTick < 0)
                        {
                            firstRideTick = t;
                            firstFrame = f;
                        }

                        lastRideTick = t;
                    }
                }

                if (firstRideTick >= 0 && firstFrame != null)
                {
                    int blockDuration = Math.max(40, lastRideTick - firstRideTick + 1);
                    String title = UIKeys.WORLD_TIMELINE_CONTEXT_DISPATCH_TRAIN.get() + " #" + firstFrame.mtrVehicleId;
                    WorldEventBlock block = WorldEventBlock.createDispatchTrain(title, firstRideTick, blockDuration, layer);
                    MtrDispatchTrack track = block.subDopeSheet.getPrimaryTrack();

                    track.sidingId = Long.toString(firstFrame.mtrVehicleId);

                    if (!Double.isNaN(firstFrame.mtrRailProgress))
                    {
                        track.initialRailProgress = firstFrame.mtrRailProgress;
                        track.initialSpeed = firstFrame.mtrSpeed;
                    }

                    this.timeline.addBlock(block);
                    this.updateLayers();
                    this.updateScrollSize();
                    this.setSelectedBlock(block);
                    this.panel.pickBlock(block);
                    UIUtils.playClick();

                    return;
                }
            }
        }

        this.addMtrOperationBlock(WorldEventBlock.TYPE_DISPATCH_TRAIN, fallbackTick, layer);
    }

    public void duplicateBlock(WorldEventBlock source)
    {
        if (this.timeline == null || source == null)
        {
            return;
        }

        WorldEventBlock copy = source.copy();

        copy.startTick = source.getEndTick() + 10;
        this.timeline.addBlock(copy);
        this.updateLayers();
        this.updateScrollSize();
        this.setSelectedBlock(copy);
        this.panel.pickBlock(copy);
        UIUtils.playClick();
    }

    public void deleteBlock(WorldEventBlock block)
    {
        if (this.timeline == null || block == null)
        {
            return;
        }

        if (this.hasEmbeddedView())
        {
            this.embedView(null);
        }

        this.timeline.removeBlock(block);

        if (this.selectedBlock == block)
        {
            this.selectedBlock = null;
            this.panel.pickBlock(null);
        }

        this.updateLayers();
        this.updateScrollSize();
        UIUtils.playClick();
    }

    public void promptSetDuration(WorldEventBlock block)
    {
        if (block == null)
        {
            return;
        }

        UINumberOverlayPanel overlay = new UINumberOverlayPanel(
            UIKeys.WORLD_TIMELINE_CONTEXT_SET_DURATION,
            UIKeys.CAMERA_PANELS_DURATION,
            (val) ->
            {
                if (val.intValue() >= 1)
                {
                    this.markDirtyBeforeEdit();
                    block.duration = val.intValue();
                    this.notifyEdited();
                }
            }
        );

        overlay.value.integer().limit(1).setValue((double) block.duration);
        UIOverlay.addOverlay(this.getContext(), overlay);
    }

    public void markDirtyBeforeEdit()
    {
        if (this.timeline != null)
        {
            this.timeline.preNotify();
        }
    }

    public void notifyEdited()
    {
        if (this.timeline != null)
        {
            this.timeline.postNotify();
        }

        this.refreshBlockPanel();
    }

    public void refreshBlockPanel()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    public WorldTimeline getTimeline()
    {
        return this.timeline;
    }

    public void setTimeline(WorldTimeline timeline)
    {
        this.timeline = timeline;
        this.selectedBlock = null;
        this.embedView(null);
        this.updateLayers();
        this.updateScrollSize();
        this.centerScrollOnRender = true;
        this.resetView();
    }

    public WorldEventBlock getSelectedBlock()
    {
        return this.selectedBlock;
    }

    public void setSelectedBlock(WorldEventBlock block)
    {
        this.selectedBlock = block;
    }

    public boolean hasEmbeddedView()
    {
        return this.embedded != null;
    }

    public void embedView(UIElement element)
    {
        if (this.embedded != null)
        {
            this.embedded.removeFromParent();
            this.embeddedClose.removeFromParent();
        }

        this.embedded = element;

        if (this.embedded != null)
        {
            int topOffset = TimelineRulerRenderer.RULER_BLOCK_HEIGHT + 22;

            this.embedded.relative(this).y(topOffset).w(1F).h(1F, -topOffset);
            this.embeddedClose.x(1F, -20).y(topOffset + 2);

            this.add(this.embedded, this.embeddedClose);
            this.resize();
        }
    }

    public int getCursor()
    {
        return this.panel.getCursor();
    }

    public void setCursor(float tick)
    {
        this.panel.setCursor(tick);
    }

    public float getTimelineCursor(float transition)
    {
        return this.panel.isRunning() ? this.panel.getCursor(transition) : this.panel.getCursor();
    }

    public int getFilmDuration()
    {
        Film film = this.panel.getFilm();

        return film == null ? 0 : film.calculateDuration();
    }

    private FilmMarkers getFilmMarkers()
    {
        Film film = this.panel.getFilm();

        return film == null ? null : film.markers;
    }

    public void resetView()
    {
        this.xAxis.anchor(0F);

        int duration = this.getFilmDuration();

        if (duration > 0)
        {
            this.xAxis.view(0, duration);
        }
        else
        {
            this.xAxis.set(0, 1);
        }
    }

    private boolean isInRuler(int mouseY)
    {
        return mouseY >= this.area.y && mouseY < TimelineRulerRenderer.getRulerBottom(this.area);
    }

    public int getLayerHeight()
    {
        return this.layerHeight;
    }

    public int fromLayerY(int mouseY)
    {
        int bottom = this.area.ey() - MARGIN;

        if (mouseY > bottom)
        {
            return -1;
        }

        mouseY -= this.getScroll();

        return (bottom - mouseY) / this.getLayerHeight();
    }

    public int toLayerY(int layer)
    {
        int h = this.getLayerHeight();

        return this.area.ey() - MARGIN - (layer + 1) * h + this.getScroll();
    }

    private int getScroll()
    {
        if (this.vertical.scrollSize < this.vertical.area.h)
        {
            return 0;
        }

        return this.vertical.scrollSize - this.vertical.area.h - (int) this.vertical.getScroll();
    }

    public void updateLayers()
    {
        this.layers = 20;

        if (this.timeline != null)
        {
            for (WorldEventBlock block : this.timeline.getBlocks())
            {
                if (block != null)
                {
                    this.layers = Math.max(this.layers, block.layer + 2);
                }
            }
        }
    }

    public void updateScrollSize()
    {
        int offset = TimelineRulerRenderer.getRulerBottom(this.area) - this.area.y;

        this.vertical.area.copy(this.area);
        this.vertical.area.y += offset;
        this.vertical.area.h -= offset + MARGIN;
        this.vertical.scrollSize = this.layers * this.getLayerHeight();
        this.vertical.clamp();
    }

    private void centerScroll()
    {
        if (this.timeline == null || this.timeline.getBlocks().isEmpty())
        {
            this.vertical.scrollToEnd();
            this.vertical.updateTarget();

            return;
        }

        int minLayer = Integer.MAX_VALUE;
        int maxLayer = 0;

        for (WorldEventBlock block : this.timeline.getBlocks())
        {
            if (block != null)
            {
                minLayer = Math.min(minLayer, block.layer);
                maxLayer = Math.max(maxLayer, block.layer);
            }
        }

        double scroll = this.vertical.scrollSize - (this.vertical.area.h + this.getLayerHeight() * (minLayer + maxLayer + 1)) / 2D;

        this.vertical.setScroll(scroll);
        this.vertical.updateTarget();
    }

    public int fromGraphTick(int mouseX)
    {
        return (int) Math.round(this.fromGraphX(mouseX));
    }

    @Override
    public void resize()
    {
        super.resize();

        this.xAxis.area.copy(this.area);
        this.updateScrollSize();
    }

    private WorldEventBlock getBlockUnder(int mouseX, int mouseY)
    {
        if (this.timeline == null || this.isInRuler(mouseY))
        {
            return null;
        }

        int h = this.getLayerHeight();
        List<WorldEventBlock> blocks = this.timeline.getBlocks();

        for (int i = blocks.size() - 1; i >= 0; i--)
        {
            WorldEventBlock block = blocks.get(i);

            if (block == null)
            {
                continue;
            }

            Area blockArea = this.getBlockArea(block, BLOCK_AREA, h);

            if (blockArea.isInside(mouseX, mouseY))
            {
                return block;
            }
        }

        return null;
    }

    private int getBlockHandle(WorldEventBlock block, int mouseX, int mouseY, int h)
    {
        Area blockArea = this.getBlockArea(block, BLOCK_AREA, h);
        int separation = Math.min(blockArea.w / 2, 5);

        if (blockArea.isInside(mouseX, mouseY))
        {
            if (mouseX - blockArea.x < separation)
            {
                return 1;
            }
            else if (mouseX - blockArea.ex() >= -separation)
            {
                return 2;
            }

            return 0;
        }

        return -1;
    }

    private int getBlockKeyframeHit(WorldEventBlock block, int mouseX, int mouseY, int h)
    {
        if (block == null || block.subDopeSheet == null)
        {
            return -1;
        }

        Area blockArea = this.getBlockArea(block, BLOCK_AREA, h);

        if (blockArea.w <= 24 || !blockArea.isInside(mouseX, mouseY))
        {
            return -1;
        }

        int cy = blockArea.ey() - (this.hasEmbeddedView() ? 4 : 5);

        if (Math.abs(mouseY - cy) > 5)
        {
            return -1;
        }

        MtrDispatchTrack track = block.subDopeSheet.getPrimaryTrack();
        int[] kfLocal = new int[] {track.dispatchTick, track.openDoorsTick, track.closeDoorsTick, track.hornTick};

        for (int k = 0; k < kfLocal.length; k++)
        {
            if (kfLocal[k] < 0)
            {
                continue;
            }

            int kx = this.toGraphX(block.startTick + kfLocal[k]);

            if (Math.abs(mouseX - kx) <= 4)
            {
                return k;
            }
        }

        return -1;
    }

    private Area getBlockArea(WorldEventBlock block, Area area, int h)
    {
        int x = this.toGraphX(block.startTick);
        int y = this.heightWhenEmbedded(block, h);
        int w = Math.max(4, this.toGraphX(block.getEndTick()) - x);

        area.set(x, y, w, h);

        return area;
    }

    private int heightWhenEmbedded(WorldEventBlock block, int h)
    {
        if (this.hasEmbeddedView())
        {
            return TimelineRulerRenderer.getRulerBottom(this.area);
        }

        return this.toLayerY(block.layer);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.hasEmbeddedView() && this.embedded != null && this.embedded.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        if (context.mouseButton == 2)
        {
            this.navigating = true;
            this.setMouse(mouseX, mouseY);

            return true;
        }

        if (context.mouseButton == 0)
        {
            this.setMouse(mouseX, mouseY);

            if (this.isInRuler(mouseY))
            {
                FilmMarker marker = this.hasEmbeddedView() ? null : this.getMarkerAt(mouseX, mouseY);

                if (marker != null)
                {
                    this.markers.beginDrag(marker);
                    this.setCursor(marker.tick.get());

                    return true;
                }

                this.scrubbing = true;
                this.setCursor(Math.max(0, this.fromGraphTick(mouseX)));

                return true;
            }

            WorldEventBlock clicked = this.getBlockUnder(mouseX, mouseY);

            if (clicked != null)
            {
                long now = System.currentTimeMillis();

                if (clicked == this.lastClickedBlock && now - this.lastBlockClickTime <= DOUBLE_CLICK_MS)
                {
                    this.lastClickedBlock = null;
                    this.setSelectedBlock(clicked);
                    this.panel.pickBlock(clicked);
                    this.panel.openSubDopeSheet(clicked);
                    UIUtils.playClick();

                    return true;
                }

                this.lastClickedBlock = clicked;
                this.lastBlockClickTime = now;

                this.setSelectedBlock(clicked);
                this.panel.pickBlock(clicked);

                this.grabbing = true;

                int kfHit = this.getBlockKeyframeHit(clicked, mouseX, mouseY, this.getLayerHeight());

                if (kfHit >= 0)
                {
                    MtrDispatchTrack track = clicked.subDopeSheet.getPrimaryTrack();

                    this.grabMode = 3;
                    this.grabKeyframeIndex = kfHit;
                    this.grabStartKeyframeTick = kfHit == 0 ? track.dispatchTick : (kfHit == 1 ? track.openDoorsTick : (kfHit == 2 ? track.closeDoorsTick : track.hornTick));
                }
                else
                {
                    this.grabMode = Math.max(0, this.getBlockHandle(clicked, mouseX, mouseY, this.getLayerHeight()));
                    this.grabKeyframeIndex = -1;
                }

                this.grabStartTick = clicked.startTick;
                this.grabStartDuration = clicked.duration;
                this.grabStartLayer = clicked.layer;
                this.markDirtyBeforeEdit();
                UIUtils.playClick();

                return true;
            }
            else
            {
                this.setSelectedBlock(null);
                this.panel.pickBlock(null);
                this.scrubbing = true;
                this.setCursor(Math.max(0, this.fromGraphTick(mouseX)));

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context) || this.hasEmbeddedView())
        {
            return super.subMouseScrolled(context);
        }

        if (context.mouseWheelHorizontal != 0D)
        {
            this.panTime(context.mouseWheelHorizontal);

            return true;
        }

        if (Window.isAltPressed())
        {
            if (Window.isShiftPressed())
            {
                this.layerHeight = MathUtils.clamp(this.layerHeight + (int) Math.copySign(2, context.mouseWheel), LAYER_HEIGHT_MIN, LAYER_HEIGHT_MAX);
                this.updateScrollSize();
            }
            else
            {
                this.vertical.mouseScroll(context);
            }

            return true;
        }

        if (context.mouseWheel != 0D && !Window.isCtrlPressed())
        {
            this.zoomTimeAt(context, context.mouseWheel);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    private FilmMarker getMarkerAt(int mouseX, int mouseY)
    {
        return this.markers.getMarkerAt(this.area, this.xAxis, mouseX, mouseY, 0);
    }

    private boolean addMarkerOptions(ContextMenuManager menu, int mouseX, int mouseY)
    {
        FilmMarkers markers = this.getFilmMarkers();

        if (markers == null || this.hasEmbeddedView() || !this.markers.isInRuler(this.area, mouseX, mouseY))
        {
            return false;
        }

        FilmMarker marker = this.getMarkerAt(mouseX, mouseY);

        if (marker == null)
        {
            int tick = Math.max(0, this.fromGraphTick(mouseX));

            menu.action(Icons.ADD, UIKeys.FILM_MARKERS_ADD, () -> this.editMarker(markers.addMarker(tick)));
        }
        else
        {
            menu.action(Icons.EDIT, UIKeys.FILM_MARKERS_EDIT, () -> this.editMarker(marker));
            menu.action(Icons.REMOVE, UIKeys.FILM_MARKERS_REMOVE, () -> markers.remove(marker));
        }

        return true;
    }

    private void commitMarkerDrag()
    {
        FilmMarker marker = this.markers.getDragged();

        if (marker == null)
        {
            return;
        }

        int tick = this.markers.getDragTick();

        this.markers.stopDrag();

        if (tick != marker.tick.get())
        {
            marker.tick.set(tick);
        }
    }

    public void editMarker(FilmMarker marker)
    {
        FilmMarkers markers = this.getFilmMarkers();

        if (markers != null && marker != null)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMarkerOverlayPanel(markers, marker), 220, 190);
        }
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.vertical.mouseReleased(context);
        this.commitMarkerDrag();

        if (this.grabbing)
        {
            this.grabbing = false;
            this.grabMode = 0;
            this.grabKeyframeIndex = -1;
            this.updateLayers();
            this.updateScrollSize();
            this.notifyEdited();
        }

        this.scrubbing = false;
        this.navigating = false;

        return super.subMouseReleased(context);
    }

    private void handleInteraction(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.markers.isDragging())
        {
            this.markers.dragTo(this.fromGraphTick(mouseX));

            return;
        }

        if (this.navigating)
        {
            this.dragTimeBy(mouseX - this.lastX);
            this.vertical.scrollBy(this.lastY - mouseY);
            this.vertical.clamp();

            this.lastX = mouseX;
            this.lastY = mouseY;

            return;
        }

        if (this.scrubbing)
        {
            this.setCursor(Math.max(0, this.fromGraphTick(mouseX)));

            return;
        }

        if (this.grabbing && this.selectedBlock != null)
        {
            int deltaTicks = this.fromGraphTick(mouseX) - this.fromGraphTick(this.initialX);

            if (this.grabMode == 0)
            {
                this.selectedBlock.startTick = Math.max(0, this.grabStartTick + deltaTicks);

                if (!this.hasEmbeddedView())
                {
                    int newLayer = this.fromLayerY(mouseY);

                    if (newLayer >= 0)
                    {
                        this.selectedBlock.layer = newLayer;
                    }
                }
            }
            else if (this.grabMode == 1)
            {
                int endTick = this.grabStartTick + this.grabStartDuration;
                int newStart = MathUtils.clamp(this.grabStartTick + deltaTicks, 0, endTick - 1);

                this.selectedBlock.startTick = newStart;
                this.selectedBlock.duration = Math.max(1, endTick - newStart);
            }
            else if (this.grabMode == 2)
            {
                this.selectedBlock.duration = Math.max(1, this.grabStartDuration + deltaTicks);
            }
            else if (this.grabMode == 3 && this.selectedBlock.subDopeSheet != null)
            {
                MtrDispatchTrack track = this.selectedBlock.subDopeSheet.getPrimaryTrack();
                int newLocalTick = MathUtils.clamp(this.grabStartKeyframeTick + deltaTicks, 0, Math.max(1, this.selectedBlock.duration));

                if (this.grabKeyframeIndex == 0)
                {
                    track.setDispatchTick(newLocalTick);
                }
                else if (this.grabKeyframeIndex == 1)
                {
                    track.setOpenDoorsTick(newLocalTick);
                }
                else if (this.grabKeyframeIndex == 2)
                {
                    track.setCloseDoorsTick(newLocalTick);
                }
                else if (this.grabKeyframeIndex == 3)
                {
                    track.setHornTick(newLocalTick);
                }
            }

            this.refreshBlockPanel();
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.centerScrollOnRender && this.area.h > 0)
        {
            this.centerScrollOnRender = false;
            this.updateScrollSize();
            this.centerScroll();
        }

        this.vertical.drag(context);
        this.handleInteraction(context);

        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        Area area = this.area;
        int h = this.getLayerHeight();
        int leftEdge = this.toGraphX(0);
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);
        int filmDuration = this.getFilmDuration();

        area.render(batcher, BBSSettings.deepSurface());

        batcher.clipBox(this.vertical.area.x, rulerBottom, this.vertical.area.ex(), this.vertical.area.ey(), context);

        for (int i = 0; i < this.layers; i++)
        {
            int ly = this.toLayerY(i);

            if (i % 2 != 0)
            {
                batcher.box(leftEdge, ly, area.ex(), ly + h, BBSSettings.baseSurface());
            }
        }

        if (leftEdge > area.x)
        {
            batcher.box(area.x, area.y, Math.min(leftEdge, area.ex()), area.ey(), BBSSettings.sunkenSurface());
        }

        if (filmDuration > 0)
        {
            int rightEdge = this.toGraphX(filmDuration);

            if (rightEdge < area.ex())
            {
                batcher.box(Math.max(rightEdge, area.x), area.y, area.ex(), area.ey(), BBSSettings.sunkenSurface());
            }
        }

        batcher.unclip(context);
        batcher.clip(area, context);

        TimelineRulerRenderer.render(
            context,
            area,
            (int) this.xAxis.getMinValue(),
            filmDuration,
            this::toGraphX,
            TimeUtils::formatTime
        );

        batcher.unclip(context);
        batcher.clipBox(this.vertical.area.x, rulerBottom, this.vertical.area.ex(), this.vertical.area.ey(), context);

        if (BBSSettings.editorTimelineGrid.get())
        {
            TimelineRulerRenderer.renderGrid(
                context,
                area,
                rulerBottom,
                (int) this.xAxis.getMinValue(),
                filmDuration,
                this::toGraphX,
                TimeUtils::formatTime
            );
        }

        if (this.timeline != null)
        {
            List<WorldEventBlock> blocks = this.timeline.getBlocks();

            for (int i = 0; i < blocks.size(); i++)
            {
                WorldEventBlock block = blocks.get(i);

                if (block == null)
                {
                    continue;
                }

                if (this.hasEmbeddedView() && block != this.selectedBlock)
                {
                    continue;
                }

                Area blockArea = this.getBlockArea(block, BLOCK_AREA, h);

                if (blockArea.ex() < area.x - 20 || blockArea.x > area.ex() + 20)
                {
                    continue;
                }

                if (!this.hasEmbeddedView())
                {
                    blockArea.y += 1;
                    blockArea.h -= 2;
                }

                boolean selected = block == this.selectedBlock;
                int baseColor = block.enabled ? block.color : Colors.GRAY;

                batcher.box(blockArea.x, blockArea.y, blockArea.ex(), blockArea.ey(), Colors.setA(baseColor, 0.85F));
                batcher.box(blockArea.x, blockArea.y, blockArea.ex(), blockArea.y + 3, Colors.setA(Colors.WHITE, 0.25F));

                /* Draw mini keyframe diamonds inside the block */
                MtrDispatchTrack track = block.subDopeSheet != null ? block.subDopeSheet.getPrimaryTrack() : null;

                if (track != null && blockArea.w > 24)
                {
                    int[] kfLocal = new int[] {track.dispatchTick, track.openDoorsTick, track.closeDoorsTick, track.hornTick};
                    int cy = blockArea.ey() - 4;

                    for (int k = 0; k < kfLocal.length; k++)
                    {
                        if (kfLocal[k] < 0)
                        {
                            continue;
                        }

                        int kx = this.toGraphX(block.startTick + kfLocal[k]);

                        if (kx >= blockArea.x + 3 && kx <= blockArea.ex() - 3)
                        {
                            boolean activeKf = selected && this.grabMode == 3 && this.grabKeyframeIndex == k;

                            batcher.box(kx - 2, cy - 2, kx + 2, cy + 2, activeKf ? Colors.ACTIVE : Colors.WHITE);
                        }
                    }
                }

                Icon icon = WorldEventBlock.TYPE_FLIGHT_OPERATION.equals(block.operationType)
                    ? Icons.PLANE
                    : (WorldEventBlock.TYPE_STATION_DOORS.equals(block.operationType) ? Icons.TIME : Icons.FORWARD);

                int textX = blockArea.x + 4;

                if (blockArea.w > 22)
                {
                    batcher.icon(icon, Colors.WHITE, blockArea.x + 2, blockArea.my() - 8);
                    textX += 16;
                }

                if (blockArea.w > 40)
                {
                    String label = block.title == null || block.title.isEmpty() ? "MTR Block" : block.title;

                    batcher.clip(blockArea, context);
                    batcher.textShadow(label, textX, blockArea.my() - font.getHeight() / 2, Colors.WHITE);
                    batcher.unclip(context);
                }

                if (selected)
                {
                    batcher.outline(blockArea.x, blockArea.y, blockArea.ex(), blockArea.ey(), Colors.WHITE, 2);
                }
                else if (!this.grabbing && blockArea.isInside(context))
                {
                    batcher.outline(blockArea.x, blockArea.y, blockArea.ex(), blockArea.ey(), Colors.WHITE);
                }

                int handle = this.getBlockHandle(block, context.mouseX, context.mouseY, h);
                int handleColor = this.grabMode != 0 ? Colors.WHITE : Colors.A50;

                if (handle == 1 || (selected && this.grabMode == 1))
                {
                    batcher.icon(Icons.CLIP_HANLDE_LEFT, handleColor, blockArea.x, blockArea.y + 10, 0F, 0.5F);
                }
                else if (handle == 2 || (selected && this.grabMode == 2))
                {
                    batcher.icon(Icons.CLIP_HANLDE_RIGHT, handleColor, blockArea.ex(), blockArea.y + 10, 1F, 0.5F);
                }
            }
        }

        batcher.unclip(context);
        batcher.clip(area, context);

        this.markers.render(context, area, this.xAxis, 0);

        float cursor = this.getTimelineCursor(context.getTransition());
        String label = TimeUtils.formatCursorTime(cursor) + "/" + TimeUtils.formatTime(filmDuration);

        renderCursor(context, label, area, this.toGraphX(cursor));

        batcher.unclip(context);
        batcher.clip(this.vertical.area, context);

        this.vertical.renderScrollbar(batcher);

        batcher.unclip(context);

        super.render(context);
    }
}
