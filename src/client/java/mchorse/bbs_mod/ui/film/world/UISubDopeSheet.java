package mchorse.bbs_mod.ui.film.world;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.world.MtrDispatchTrack;
import mchorse.bbs_mod.film.world.WorldEventBlock;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Embedded Sub-Dope Sheet editor for a {@link WorldEventBlock}.
 * Modeled directly after {@code UIReplaysEditor} (POV Editor) and {@code UIKeyframeDopeSheet}
 * with collapsible section headers, RowStyle track rows, visibility/lock toggles, hold bars,
 * and genuine diamond keyframes.
 */
public class UISubDopeSheet extends UITimelineCanvas
{
    private static final int CATEGORY_BAR_WIDTH = 20;
    private static final int LABEL_WIDTH = 216;
    private static final int ROW_HEIGHT = 20;
    private static final int SECTION_GAP = 8;
    private static final int ICON_SLOT_SIZE = 16;

    private static final String SECTION_DISPATCH = "mtr_dispatch";
    private static final String SECTION_ACTIONS = "mtr_actions";

    private static final IKey[] TRACK_TITLES = new IKey[]
    {
        UIKeys.WORLD_TIMELINE_TRACK_DISPATCH,
        UIKeys.WORLD_TIMELINE_TRACK_OPEN_DOORS,
        UIKeys.WORLD_TIMELINE_TRACK_CLOSE_DOORS,
        UIKeys.WORLD_TIMELINE_TRACK_STATION_ARRIVE,
        UIKeys.WORLD_TIMELINE_TRACK_HORN
    };

    private static final Icon[] TRACK_ICONS = new Icon[]
    {
        Icons.FORWARD,
        Icons.TIME,
        Icons.SHIFT_FORWARD,
        Icons.DOWNLOAD,
        Icons.SOUND
    };

    private static final int[] TRACK_COLORS = new int[]
    {
        0xFF1E88E5,
        0xFF43A047,
        0xFFFB8C00,
        0xFF00ACC1,
        0xFFAB47BC
    };

    public final UIWorldTimeline parentTimeline;
    public final WorldEventBlock block;

    public final Scroll dopeSheet = new Scroll(new Area());
    public final Area graphArea = new Area();

    private final FoldState<String> foldState = new FoldState<>(true);
    private final List<RowEntry> rows = new ArrayList<>();

    private UIIcon backButton;
    private UIIcon toggleFoldsButton;
    private UIIcon maximizeButton;

    private int selectedTrack = MtrDispatchTrack.CHANNEL_DISPATCH;
    private int draggingTrack = -1;
    private boolean scrubbing;

    public UISubDopeSheet(UIWorldTimeline parentTimeline, WorldEventBlock block)
    {
        super();

        this.parentTimeline = parentTimeline;
        this.block = block;

        this.dopeSheet.smoothScrolling(() -> !BBSSettings.scrollingDisableSmoothnessInEditors.get());
        this.dopeSheet.wheelScrollStep(() -> ROW_HEIGHT);

        this.foldState.onChange(this::rebuildRows);

        this.backButton = new UIIcon(Icons.ARROW_LEFT, (b) -> this.parentTimeline.embedView(null));
        this.backButton.tooltip(UIKeys.FILM_REPLAY_POV_BACK);
        this.backButton.relative(this).x(0).y(0).wh(CATEGORY_BAR_WIDTH, 20);

        this.toggleFoldsButton = new UIIcon(Icons.COLLAPSE_ALL, (b) ->
        {
            if (this.foldState.isExpanded(SECTION_DISPATCH) || this.foldState.isExpanded(SECTION_ACTIONS))
            {
                this.foldState.collapseAll();
                this.toggleFoldsButton.both(Icons.EXPAND_ALL);
            }
            else
            {
                this.foldState.expandAll(Arrays.asList(SECTION_DISPATCH, SECTION_ACTIONS));
                this.toggleFoldsButton.both(Icons.COLLAPSE_ALL);
            }
        });
        this.toggleFoldsButton.tooltip(UIKeys.FORMS_LIST_COLLAPSE_ALL);
        this.toggleFoldsButton.relative(this).x(0).y(22).wh(CATEGORY_BAR_WIDTH, 20);

        this.maximizeButton = new UIIcon(Icons.MAXIMIZE, (b) -> this.resetView());
        this.maximizeButton.tooltip(UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE);
        this.maximizeButton.relative(this).x(0).y(44).wh(CATEGORY_BAR_WIDTH, 20);

        this.add(this.backButton, this.toggleFoldsButton, this.maximizeButton);

        this.context((menu) ->
        {
            UIContext context = this.getContext();
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;

            RowEntry hitRow = this.getRowAt(mouseY);
            int targetChannel = hitRow != null && !hitRow.isSection ? hitRow.channelIndex : this.selectedTrack;
            int localTick = Math.max(0, Math.round((float) this.fromGraphX(mouseX)) - this.block.startTick);
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            menu.icon(MenuVerb.BACK, () -> this.parentTimeline.embedView(null)).label(UIKeys.FILM_REPLAY_POV_BACK);

            if (targetChannel >= 0 && !track.isChannelLocked(targetChannel))
            {
                menu.icon(MenuVerb.ADD, () ->
                {
                    this.parentTimeline.markDirtyBeforeEdit();
                    this.selectedTrack = targetChannel;
                    track.setChannelTick(targetChannel, localTick);
                    this.parentTimeline.notifyEdited();
                    UIUtils.playClick();
                }).label(UIKeys.KEYFRAMES_EMPTY_ADD);

                if (targetChannel == MtrDispatchTrack.CHANNEL_STATION_ARRIVE || targetChannel == MtrDispatchTrack.CHANNEL_HORN)
                {
                    boolean hasKeyframe = track.getChannelTick(targetChannel) >= 0;

                    menu.icon(MenuVerb.REMOVE, () ->
                    {
                        this.parentTimeline.markDirtyBeforeEdit();
                        track.setChannelTick(targetChannel, -1);
                        this.parentTimeline.notifyEdited();
                        UIUtils.playClick();
                    }).label(UIKeys.KEYFRAMES_CONTEXT_REMOVE).enabled(hasKeyframe);
                }
            }

            menu.action(Icons.MAXIMIZE, UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, this::resetView);
        });

        this.keys().register(Keys.KEYFRAMES_MAXIMIZE, this::resetView);
        this.keys().register(Keys.DELETE, () ->
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

            if (!track.isChannelLocked(this.selectedTrack)
                && (this.selectedTrack == MtrDispatchTrack.CHANNEL_STATION_ARRIVE || this.selectedTrack == MtrDispatchTrack.CHANNEL_HORN))
            {
                this.parentTimeline.markDirtyBeforeEdit();
                track.setChannelTick(this.selectedTrack, -1);
                this.parentTimeline.notifyEdited();
                UIUtils.playClick();
            }
        });

        this.rebuildRows();
    }

    private void rebuildRows()
    {
        this.rows.clear();

        int yOffset = 0;

        /* Section 1: MTR Dispatch Channels */
        this.rows.add(RowEntry.section(SECTION_DISPATCH, UIKeys.WORLD_TIMELINE_SECTION_DISPATCH, 0xFF1E88E5, yOffset));
        yOffset += ROW_HEIGHT;

        if (this.foldState.isExpanded(SECTION_DISPATCH))
        {
            for (int ch = MtrDispatchTrack.CHANNEL_DISPATCH; ch <= MtrDispatchTrack.CHANNEL_CLOSE_DOORS; ch++)
            {
                this.rows.add(RowEntry.track(ch, TRACK_TITLES[ch], TRACK_ICONS[ch], TRACK_COLORS[ch], yOffset));
                yOffset += ROW_HEIGHT;
            }
        }

        yOffset += SECTION_GAP;

        /* Section 2: Alert & Action Channels */
        this.rows.add(RowEntry.section(SECTION_ACTIONS, UIKeys.WORLD_TIMELINE_SECTION_ACTIONS, 0xFF8E24AA, yOffset));
        yOffset += ROW_HEIGHT;

        if (this.foldState.isExpanded(SECTION_ACTIONS))
        {
            for (int ch = MtrDispatchTrack.CHANNEL_STATION_ARRIVE; ch <= MtrDispatchTrack.CHANNEL_HORN; ch++)
            {
                this.rows.add(RowEntry.track(ch, TRACK_TITLES[ch], TRACK_ICONS[ch], TRACK_COLORS[ch], yOffset));
                yOffset += ROW_HEIGHT;
            }
        }

        this.updateScrollSize();
    }

    public void resetView()
    {
        int start = Math.max(0, this.block.startTick - 10);
        int end = Math.max(start + 20, this.block.getEndTick() + 10);

        this.xAxis.view(start, end);
    }

    private void updateScrollSize()
    {
        if (this.graphArea.h <= 0)
        {
            return;
        }

        int rulerBottom = TimelineRulerRenderer.getRulerBottom(this.graphArea);
        int contentHeight = 0;

        if (!this.rows.isEmpty())
        {
            RowEntry last = this.rows.get(this.rows.size() - 1);

            contentHeight = last.yOffset + ROW_HEIGHT + 8;
        }

        this.dopeSheet.area.set(this.area.x + CATEGORY_BAR_WIDTH, rulerBottom, this.area.w - CATEGORY_BAR_WIDTH, Math.max(1, this.area.ey() - rulerBottom));
        this.dopeSheet.scrollSize = contentHeight;
        this.dopeSheet.clamp();
    }

    @Override
    public void resize()
    {
        super.resize();

        int leftOffset = CATEGORY_BAR_WIDTH + LABEL_WIDTH;

        this.graphArea.set(
            this.area.x + leftOffset,
            this.area.y,
            Math.max(20, this.area.w - leftOffset),
            this.area.h
        );
        this.xAxis.area.copy(this.graphArea);
        this.updateScrollSize();

        if (this.xAxis.getMaxValue() <= this.xAxis.getMinValue() + 1D)
        {
            this.resetView();
        }
    }

    private int getDopeSheetTop()
    {
        return TimelineRulerRenderer.getRulerBottom(this.graphArea) - (int) this.dopeSheet.getScroll();
    }

    private RowEntry getRowAt(int mouseY)
    {
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(this.graphArea);

        if (mouseY < rulerBottom || mouseY > this.area.ey())
        {
            return null;
        }

        int top = this.getDopeSheetTop();

        for (RowEntry entry : this.rows)
        {
            int ry = top + entry.yOffset;

            if (mouseY >= ry && mouseY < ry + ROW_HEIGHT)
            {
                return entry;
            }
        }

        return null;
    }

    private int getVisibilityIconX()
    {
        return this.area.x + CATEGORY_BAR_WIDTH + LABEL_WIDTH - 2 - ICON_SLOT_SIZE * 2 - 2;
    }

    private int getLockIconX()
    {
        return this.area.x + CATEGORY_BAR_WIDTH + LABEL_WIDTH - 2 - ICON_SLOT_SIZE;
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

        if (mouseX < this.area.x + CATEGORY_BAR_WIDTH)
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

            int rulerBottom = TimelineRulerRenderer.getRulerBottom(this.graphArea);

            if (mouseY >= this.area.y && mouseY < rulerBottom && mouseX >= this.graphArea.x)
            {
                this.scrubbing = true;
                this.parentTimeline.setCursor(Math.max(0, (int) Math.round(this.fromGraphX(mouseX))));

                return true;
            }

            RowEntry row = this.getRowAt(mouseY);

            if (row != null)
            {
                MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

                if (mouseX < this.graphArea.x)
                {
                    if (row.isSection)
                    {
                        this.foldState.toggle(row.sectionKey);
                        UIUtils.playClick();

                        return true;
                    }

                    int visX = this.getVisibilityIconX();
                    int lockX = this.getLockIconX();

                    if (mouseX >= visX && mouseX < visX + ICON_SLOT_SIZE)
                    {
                        this.parentTimeline.markDirtyBeforeEdit();
                        track.toggleChannelVisible(row.channelIndex);
                        this.parentTimeline.notifyEdited();
                        UIUtils.playClick();

                        return true;
                    }

                    if (mouseX >= lockX && mouseX < lockX + ICON_SLOT_SIZE)
                    {
                        this.parentTimeline.markDirtyBeforeEdit();
                        track.toggleChannelLocked(row.channelIndex);
                        this.parentTimeline.notifyEdited();
                        UIUtils.playClick();

                        return true;
                    }

                    this.selectedTrack = row.channelIndex;
                    int kfTick = track.getChannelTick(row.channelIndex);

                    if (kfTick >= 0)
                    {
                        this.parentTimeline.setCursor(this.block.startTick + kfTick);
                    }

                    UIUtils.playClick();

                    return true;
                }

                if (!row.isSection)
                {
                    this.selectedTrack = row.channelIndex;

                    if (track.isChannelLocked(row.channelIndex))
                    {
                        return true;
                    }

                    int kfLocal = track.getChannelTick(row.channelIndex);

                    if (kfLocal >= 0)
                    {
                        int kx = this.toGraphX(this.block.startTick + kfLocal);

                        if (Math.abs(mouseX - kx) <= 7)
                        {
                            this.draggingTrack = row.channelIndex;
                            this.parentTimeline.markDirtyBeforeEdit();
                            UIUtils.playClick();

                            return true;
                        }
                    }

                    /* Double-click or Shift+click inserts/moves keyframe at clicked tick */
                    if (Window.isShiftPressed() || kfLocal < 0)
                    {
                        int clickedLocal = Math.max(0, (int) Math.round(this.fromGraphX(mouseX)) - this.block.startTick);

                        this.parentTimeline.markDirtyBeforeEdit();
                        track.setChannelTick(row.channelIndex, clickedLocal);
                        this.draggingTrack = row.channelIndex;
                        this.parentTimeline.notifyEdited();
                        UIUtils.playClick();

                        return true;
                    }
                }
            }

            if (mouseX >= this.graphArea.x)
            {
                this.scrubbing = true;
                this.parentTimeline.setCursor(Math.max(0, (int) Math.round(this.fromGraphX(mouseX))));

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseScrolled(context);
        }

        if (context.mouseX < this.graphArea.x)
        {
            this.dopeSheet.mouseScroll(context);

            return true;
        }

        if (context.mouseWheelHorizontal != 0D)
        {
            this.panTime(context.mouseWheelHorizontal);

            return true;
        }

        if (Window.isAltPressed())
        {
            this.dopeSheet.mouseScroll(context);

            return true;
        }

        if (context.mouseWheel != 0D && !Window.isCtrlPressed())
        {
            this.zoomTimeAt(context, context.mouseWheel);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.dopeSheet.mouseReleased(context);

        if (this.draggingTrack >= 0)
        {
            this.draggingTrack = -1;
            this.parentTimeline.notifyEdited();
        }

        this.scrubbing = false;
        this.navigating = false;

        return super.subMouseReleased(context);
    }

    private void handleInteraction(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.navigating)
        {
            this.dragTimeBy(mouseX - this.lastX);
            this.dopeSheet.scrollBy(this.lastY - mouseY);
            this.dopeSheet.clamp();
            this.lastX = mouseX;
            this.lastY = mouseY;

            return;
        }

        if (this.scrubbing)
        {
            this.parentTimeline.setCursor(Math.max(0, (int) Math.round(this.fromGraphX(mouseX))));

            return;
        }

        if (this.draggingTrack >= 0)
        {
            MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();
            int newMasterTick = Math.max(this.block.startTick, (int) Math.round(this.fromGraphX(mouseX)));
            int localTick = MathUtils.clamp(newMasterTick - this.block.startTick, 0, Math.max(1, this.block.duration));

            track.setChannelTick(this.draggingTrack, localTick);
            this.parentTimeline.refreshBlockPanel();
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.dopeSheet.drag(context);
        this.handleInteraction(context);

        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(this.graphArea);
        int labelX = this.area.x + CATEGORY_BAR_WIDTH;
        int top = this.getDopeSheetTop();
        MtrDispatchTrack track = this.block.subDopeSheet.getPrimaryTrack();

        /* 1. Left category action bar background & right graph surface */
        batcher.box(this.area.x, this.area.y, labelX, this.area.ey(), BBSSettings.sunkenSurface());
        batcher.box(labelX, this.area.y, this.graphArea.x, this.area.ey(), BBSSettings.baseSurface());
        this.graphArea.render(batcher, BBSSettings.deepSurface());

        /* 2. Top-left header matching UIKeyframes.renderPartHeader */
        batcher.box(labelX, this.area.y, this.graphArea.x, rulerBottom, BBSSettings.baseSurface());
        batcher.box(labelX, rulerBottom - 1, this.graphArea.x, rulerBottom, Colors.setA(Colors.WHITE, 0.08F));

        String headerTitle = this.block.title == null || this.block.title.isEmpty()
            ? UIKeys.WORLD_TIMELINE_SUB_DOPE_TITLE.get()
            : this.block.title;

        batcher.clip(labelX + 4, this.area.y, LABEL_WIDTH - 8, rulerBottom - this.area.y, context);
        batcher.icon(Icons.POSE, Colors.ACTIVE | Colors.A100, labelX + 4, this.area.y + (rulerBottom - this.area.y - 16) / 2);
        batcher.textShadow(headerTitle, labelX + 22, this.area.y + (rulerBottom - this.area.y - font.getHeight()) / 2, Colors.WHITE);
        batcher.unclip(context);

        /* 3. Graph rows & out-of-bounds shading */
        batcher.clipBox(this.graphArea.x, rulerBottom, this.graphArea.ex(), this.graphArea.ey(), context);

        int blockStartX = this.toGraphX(this.block.startTick);
        int blockEndX = this.toGraphX(this.block.getEndTick());

        if (blockStartX > this.graphArea.x)
        {
            batcher.box(this.graphArea.x, rulerBottom, Math.min(blockStartX, this.graphArea.ex()), this.graphArea.ey(), BBSSettings.sunkenSurface());
        }

        if (blockEndX < this.graphArea.ex())
        {
            batcher.box(Math.max(blockEndX, this.graphArea.x), rulerBottom, this.graphArea.ex(), this.graphArea.ey(), BBSSettings.sunkenSurface());
        }

        for (int i = 0; i < this.rows.size(); i++)
        {
            RowEntry row = this.rows.get(i);
            int ry = top + row.yOffset;

            if (ry + ROW_HEIGHT < rulerBottom || ry > this.graphArea.ey())
            {
                continue;
            }

            if (row.isSection)
            {
                int secBg = Colors.setA(row.color, 0.14F);

                batcher.box(this.graphArea.x, ry, this.graphArea.ex(), ry + ROW_HEIGHT, secBg);
                batcher.box(this.graphArea.x, ry + ROW_HEIGHT - 1, this.graphArea.ex(), ry + ROW_HEIGHT, Colors.setA(row.color, 0.35F));
            }
            else
            {
                boolean picked = row.channelIndex == this.selectedTrack;
                int rowSurface = i % 2 == 0 ? BBSSettings.deepSurface() : BBSSettings.baseSurface();

                batcher.box(this.graphArea.x, ry, this.graphArea.ex(), ry + ROW_HEIGHT, rowSurface);

                if (picked)
                {
                    batcher.box(this.graphArea.x, ry, this.graphArea.ex(), ry + ROW_HEIGHT, Colors.setA(row.color, 0.12F));
                }

                /* Hold bar between openDoorsTick and closeDoorsTick */
                if (row.channelIndex == MtrDispatchTrack.CHANNEL_OPEN_DOORS || row.channelIndex == MtrDispatchTrack.CHANNEL_CLOSE_DOORS)
                {
                    int openX = this.toGraphX(this.block.startTick + track.openDoorsTick);
                    int closeX = this.toGraphX(this.block.startTick + track.closeDoorsTick);

                    if (closeX > openX)
                    {
                        batcher.box(openX, ry + ROW_HEIGHT / 2 - 2, closeX, ry + ROW_HEIGHT / 2 + 2, Colors.setA(row.color, 0.45F));
                    }
                }
                else if (row.channelIndex == MtrDispatchTrack.CHANNEL_DISPATCH)
                {
                    int dispX = this.toGraphX(this.block.startTick + track.dispatchTick);
                    int openX = this.toGraphX(this.block.startTick + track.openDoorsTick);

                    if (openX > dispX)
                    {
                        batcher.box(dispX, ry + ROW_HEIGHT / 2 - 1, openX, ry + ROW_HEIGHT / 2 + 1, Colors.setA(row.color, 0.35F));
                    }
                }
            }
        }

        if (BBSSettings.editorTimelineGrid.get())
        {
            TimelineRulerRenderer.renderGrid(
                context,
                this.graphArea,
                rulerBottom,
                (int) this.xAxis.getMinValue(),
                this.block.getEndTick(),
                this::toGraphX,
                TimeUtils::formatTime
            );
        }

        /* 4. Render genuine BBS diamond keyframes */
        this.renderDiamondKeyframes(context, track, top, rulerBottom);

        batcher.unclip(context);

        /* 5. Render Timeline Ruler */
        batcher.clip(this.graphArea, context);

        TimelineRulerRenderer.render(
            context,
            this.graphArea,
            (int) this.xAxis.getMinValue(),
            this.block.getEndTick(),
            this::toGraphX,
            TimeUtils::formatTime
        );

        float cursor = this.parentTimeline.getTimelineCursor(context.getTransition());
        String cursorLabel = TimeUtils.formatCursorTime(cursor) + "/" + TimeUtils.formatTime(this.block.getEndTick());

        renderCursor(context, cursorLabel, this.graphArea, this.toGraphX(cursor));

        batcher.unclip(context);

        /* 6. Render Left Track Labels with RowStyle, Fold Arrows, Icons, Visibility & Lock Toggles */
        this.renderLeftTrackColumn(context, track, labelX, top, rulerBottom);

        /* Vertical separator line between label column and timeline canvas */
        batcher.box(this.graphArea.x - 1, this.area.y, this.graphArea.x, this.area.ey(), Colors.A50);
        batcher.box(labelX - 1, this.area.y, labelX, this.area.ey(), Colors.A50);

        batcher.clip(this.dopeSheet.area, context);
        this.dopeSheet.renderScrollbar(batcher);
        batcher.unclip(context);

        super.render(context);
    }

    private void renderDiamondKeyframes(UIContext context, MtrDispatchTrack track, int top, int rulerBottom)
    {
        IKeyframeShapeRenderer diamond = KeyframeShapeRenderers.SHAPES.get(KeyframeShape.DIAMOND);

        if (diamond == null)
        {
            return;
        }

        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        boolean hasVertices = false;

        for (RowEntry row : this.rows)
        {
            if (row.isSection)
            {
                /* Section summary dots when collapsed */
                int sy = top + row.yOffset + ROW_HEIGHT / 2;

                if (sy < rulerBottom || sy > this.graphArea.ey())
                {
                    continue;
                }

                int startCh = SECTION_DISPATCH.equals(row.sectionKey) ? MtrDispatchTrack.CHANNEL_DISPATCH : MtrDispatchTrack.CHANNEL_STATION_ARRIVE;
                int endCh = SECTION_DISPATCH.equals(row.sectionKey) ? MtrDispatchTrack.CHANNEL_CLOSE_DOORS : MtrDispatchTrack.CHANNEL_HORN;

                for (int ch = startCh; ch <= endCh; ch++)
                {
                    int kfLocal = track.getChannelTick(ch);

                    if (kfLocal < 0)
                    {
                        continue;
                    }

                    int kx = this.toGraphX(this.block.startTick + kfLocal);

                    if (kx >= this.graphArea.x - 6 && kx <= this.graphArea.ex() + 6)
                    {
                        int dotColor = row.color | Colors.A100;

                        context.batcher.fillRect(builder, matrix, kx - 2, sy - 2, 4, 4, dotColor, dotColor, dotColor, dotColor);
                        hasVertices = true;
                    }
                }

                continue;
            }

            if (!track.isChannelVisible(row.channelIndex))
            {
                continue;
            }

            int kfLocal = track.getChannelTick(row.channelIndex);

            if (kfLocal < 0)
            {
                continue;
            }

            int ry = top + row.yOffset + ROW_HEIGHT / 2;

            if (ry < rulerBottom - 6 || ry > this.graphArea.ey() + 6)
            {
                continue;
            }

            int kx = this.toGraphX(this.block.startTick + kfLocal);

            if (kx < this.graphArea.x - 10 || kx > this.graphArea.ex() + 10)
            {
                continue;
            }

            boolean selected = row.channelIndex == this.selectedTrack || row.channelIndex == this.draggingTrack;
            boolean locked = track.isChannelLocked(row.channelIndex);
            int outerColor = locked ? Colors.GRAY | Colors.A100 : (selected ? Colors.WHITE : (row.color | Colors.A100));
            int innerColor = selected ? (Colors.ACTIVE | Colors.A100) : Colors.mulRGB(row.color | Colors.A100, 0.55F);

            diamond.renderKeyframe(context, builder, matrix, kx, ry, 4, outerColor);
            diamond.renderKeyframe(context, builder, matrix, kx, ry, 2, innerColor);
            hasVertices = true;
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Bufs.draw(builder);
    }

    private void renderLeftTrackColumn(UIContext context, MtrDispatchTrack track, int labelX, int top, int rulerBottom)
    {
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        batcher.clipBox(labelX, rulerBottom, labelX + LABEL_WIDTH, this.area.ey(), context);

        for (RowEntry row : this.rows)
        {
            int ry = top + row.yOffset;

            if (ry + ROW_HEIGHT < rulerBottom || ry > this.area.ey())
            {
                continue;
            }

            boolean hover = mouseX >= labelX && mouseX < labelX + LABEL_WIDTH && mouseY >= ry && mouseY < ry + ROW_HEIGHT;

            if (row.isSection)
            {
                boolean unfolded = this.foldState.isExpanded(row.sectionKey);

                RowStyle.row(batcher, labelX, ry, LABEL_WIDTH, ROW_HEIGHT, row.color, true, hover, false);
                this.renderFoldArrow(context, labelX + 10F, ry + ROW_HEIGHT / 2F, unfolded);
                batcher.textShadow(row.title.get(), labelX + 20, ry + (ROW_HEIGHT - font.getHeight()) / 2, Colors.WHITE);
            }
            else
            {
                boolean picked = row.channelIndex == this.selectedTrack;
                boolean visible = track.isChannelVisible(row.channelIndex);
                boolean locked = track.isChannelLocked(row.channelIndex);

                RowStyle.row(batcher, labelX, ry, LABEL_WIDTH, ROW_HEIGHT, row.color, false, hover, picked);

                int iconColor = visible ? (picked ? Colors.WHITE : (row.color | Colors.A100)) : Colors.GRAY;

                batcher.icon(row.icon, iconColor, labelX + 6, ry + (ROW_HEIGHT - 16) / 2);

                int visX = this.getVisibilityIconX();
                int lockX = this.getLockIconX();
                int textRight = visX - 4;
                int textColor = !visible ? Colors.GRAY : (picked ? Colors.WHITE : Colors.LIGHTER_GRAY);

                batcher.clip(labelX + 24, ry, Math.max(10, textRight - (labelX + 24)), ROW_HEIGHT, context);
                batcher.textShadow(row.title.get(), labelX + 24, ry + (ROW_HEIGHT - font.getHeight()) / 2, textColor);
                batcher.unclip(context);
                batcher.clipBox(labelX, rulerBottom, labelX + LABEL_WIDTH, this.area.ey(), context);

                /* Visibility & Lock toggle icons */
                boolean hoverVis = hover && mouseX >= visX && mouseX < visX + ICON_SLOT_SIZE;
                boolean hoverLock = hover && mouseX >= lockX && mouseX < lockX + ICON_SLOT_SIZE;

                Icon visIcon = visible ? Icons.VISIBLE : Icons.INVISIBLE;
                int visColor = visible ? (hoverVis ? Colors.WHITE : Colors.LIGHTER_GRAY) : (hoverVis ? Colors.WHITE : Colors.A50);

                Icon lockIcon = locked ? Icons.LOCKED : Icons.UNLOCKED;
                int lockColor = locked ? (Colors.ACTIVE | Colors.A100) : (hoverLock ? Colors.WHITE : Colors.A50);

                if (hover || !visible)
                {
                    batcher.icon(visIcon, visColor, visX, ry + (ROW_HEIGHT - 16) / 2);
                }

                if (hover || locked)
                {
                    batcher.icon(lockIcon, lockColor, lockX, ry + (ROW_HEIGHT - 16) / 2);
                }
            }
        }

        batcher.unclip(context);
    }

    private void renderFoldArrow(UIContext context, float cx, float cy, boolean unfolded)
    {
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(cx, cy, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(unfolded ? 90F : 0F), 0F, 0F, 0F);
        context.batcher.icon(Icons.ARROW_SMALL, Colors.WHITE, 0, 0, 0.5F, 0.5F);
        matrices.pop();
    }

    private static final class RowEntry
    {
        public final boolean isSection;
        public final String sectionKey;
        public final int channelIndex;
        public final IKey title;
        public final Icon icon;
        public final int color;
        public final int yOffset;

        private RowEntry(boolean isSection, String sectionKey, int channelIndex, IKey title, Icon icon, int color, int yOffset)
        {
            this.isSection = isSection;
            this.sectionKey = sectionKey;
            this.channelIndex = channelIndex;
            this.title = title;
            this.icon = icon;
            this.color = color;
            this.yOffset = yOffset;
        }

        public static RowEntry section(String sectionKey, IKey title, int color, int yOffset)
        {
            return new RowEntry(true, sectionKey, -1, title, null, color, yOffset);
        }

        public static RowEntry track(int channelIndex, IKey title, Icon icon, int color, int yOffset)
        {
            return new RowEntry(false, "", channelIndex, title, icon, color, yOffset);
        }
    }
}
