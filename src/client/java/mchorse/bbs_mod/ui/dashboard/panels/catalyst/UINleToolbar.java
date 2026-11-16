package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.framework.elements.windows.UIGlassSlider;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * The strip between the monitor and the timeline: the playhead's timecode, the edit tools (select,
 * trim, blade), snapping, linked selection and markers in the middle, and the timeline's zoom and
 * the master volume on the right.
 */
public class UINleToolbar extends UIElement
{
    public static final int HEIGHT = 22;

    private final UINleTimeline timeline;
    private final IntSupplier frame;
    private final IntSupplier fps;

    /** Set by the page: whether the keyframe and curve editor is open, and the switch that opens and closes it */
    public BooleanSupplier keysOpen = () -> false;
    public Runnable toggleKeys = () -> {};

    private final UIElement tools;
    private final Slider zoom;
    private final Slider volume;
    private final DoubleSupplier volumeValue;

    /** The zoom the timeline opens at, shown as 100 percent */
    private static final double BASE_ZOOM = 3D;

    public UINleToolbar(UINleTimeline timeline, IntSupplier frame, IntSupplier fps, DoubleSupplier volume, DoubleConsumer setVolume, Runnable toggleMute)
    {
        this.timeline = timeline;
        this.volumeValue = volume;
        this.frame = frame;
        this.fps = fps;

        this.tools = new UIElement();
        this.tools.relative(this).x(0.5F).y(2).w(7 * 22).h(18).anchorX(0.5F);

        this.tool(0, Icons.CURSOR, UIKeys.CATALYST_NLE_TOOL_SELECT, () -> timeline.tool == UINleTimeline.Tool.SELECT, () -> timeline.tool = UINleTimeline.Tool.SELECT);
        this.tool(1, null, UIKeys.CATALYST_NLE_TOOL_TRIM, () -> timeline.tool == UINleTimeline.Tool.TRIM, () -> timeline.tool = UINleTimeline.Tool.TRIM);
        this.tool(2, Icons.CUT, UIKeys.CATALYST_NLE_TOOL_BLADE, () -> timeline.tool == UINleTimeline.Tool.BLADE, () -> timeline.tool = UINleTimeline.Tool.BLADE);
        this.tool(3, Icons.MAGNET, UIKeys.CATALYST_NLE_TOOL_SNAP, () -> timeline.snapping, () -> timeline.snapping = !timeline.snapping);
        this.tool(4, Icons.LINK, UIKeys.CATALYST_NLE_TOOL_LINKED, () -> timeline.linkedSelection, () -> timeline.linkedSelection = !timeline.linkedSelection);
        this.tool(5, Icons.BOOKMARK, UIKeys.CATALYST_NLE_TOOL_MARKER, () -> false, timeline::addMarkerAtPlayhead);
        this.tool(6, Icons.CURVES, UIKeys.CATALYST_NLE_TOOL_KEYS, () -> this.keysOpen.getAsBoolean(), () -> this.toggleKeys.run());

        UIIcon zoomOut = new UIIcon(Icons.REMOVE, (b) -> timeline.setZoom(timeline.zoom / 1.4D));
        UIIcon zoomIn = new UIIcon(Icons.ADD, (b) -> timeline.setZoom(timeline.zoom * 1.4D));

        zoomOut.tooltip(UIKeys.CATALYST_NLE_ZOOM_OUT);
        zoomIn.tooltip(UIKeys.CATALYST_NLE_ZOOM_IN);

        this.zoom = new Slider(timeline::getZoomFactor, timeline::setZoomFactor);
        this.zoom.tooltip(UIKeys.CATALYST_NLE_ZOOM);
        this.volume = new Slider(() -> volume.getAsDouble() / 2D, (v) -> setVolume.accept(v * 2D));
        this.volume.tooltip(UIKeys.CATALYST_NLE_MASTER_VOLUME);

        /* The speaker mutes: struck through in red while the volume is off, and back to what it was on the next click */
        UIIcon speaker = new UIIcon(Icons.SOUND, (b) -> toggleMute.run())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                boolean muted = volume.getAsDouble() <= 0.0001D;

                if (this.hover)
                {
                    RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 4, RoundedDraw.ALL, 0x22ffffff);
                }

                context.batcher.icon(Icons.SOUND, muted ? 0xffe0455a : 0xccffffff, this.area.x + 1, this.area.y + 1);

                if (muted)
                {
                    for (int i = 0; i < 12; i++)
                    {
                        context.batcher.box(this.area.x + 3 + i, this.area.y + 3 + i, this.area.x + 5 + i, this.area.y + 4 + i, 0xffe0455a);
                    }
                }
            }
        };

        speaker.tooltip(UIKeys.CATALYST_NLE_MUTE);

        /* Right to left: the volume's percent, its slider and the speaker, a divider, then the zoom's percent, + slider - */
        this.volume.relative(this).x(1F, -98).y(5).w(60).h(12);
        speaker.relative(this).x(1F, -119).y(2).wh(18, 18);
        zoomIn.relative(this).x(1F, -182).y(2).wh(18, 18);
        this.zoom.relative(this).x(1F, -262).y(5).w(78).h(12);
        zoomOut.relative(this).x(1F, -282).y(2).wh(18, 18);

        this.add(this.tools, zoomOut, this.zoom, zoomIn, speaker, this.volume);
    }

    private void tool(int index, Icon icon, IKey tooltip, BooleanSupplier active, Runnable action)
    {
        UIIcon button = new UIIcon(icon, (b) -> action.run())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (active.getAsBoolean())
                {
                    RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 4, RoundedDraw.ALL, Colors.setA(BBSSettings.primaryColor.get(), 0.55F));
                }
                else if (this.hover)
                {
                    RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 4, RoundedDraw.ALL, 0x22ffffff);
                }

                if (icon == null)
                {
                    trimGlyph(context, this.area.mx(), this.area.my(), active.getAsBoolean() || this.hover ? 0xffffffff : 0xb0ffffff);
                }
                else
                {
                    super.renderSkin(context);
                }
            }
        };

        button.tooltip(tooltip);
        button.relative(this.tools).x(index * 22).y(0).wh(20, 18);
        this.tools.add(button);
    }

    /** The trim tool: an edit point (a bar) with arrows out to both sides, the way an edge is pulled */
    private static void trimGlyph(UIContext context, int cx, int cy, int color)
    {
        context.batcher.box(cx - 1, cy - 6, cx + 1, cy + 6, color);

        for (int i = 0; i < 3; i++)
        {
            /* the arrowheads, a pixel column at a time */
            context.batcher.box(cx - 6 + i, cy - i, cx - 5 + i, cy + i + 1, color);
            context.batcher.box(cx + 5 - i, cy - i, cx + 6 - i, cy + i + 1, color);
        }

        context.batcher.box(cx - 6, cy, cx - 2, cy + 1, color);
        context.batcher.box(cx + 2, cy, cx + 6, cy + 1, color);
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff181d27);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 1, 0xff262d3a);
        context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0xff0b0e13);

        /* The playhead's timecode, large */
        String code = UINleTimeline.timecode(this.frame.getAsInt(), this.fps.getAsInt());

        context.batcher.getContext().getMatrices().push();
        context.batcher.getContext().getMatrices().translate(this.area.x + 8, this.area.y + 5, 0);
        context.batcher.getContext().getMatrices().scale(1.25F, 1.25F, 1F);
        context.batcher.text(code, 0, 0, 0xffe8edf5, false);
        context.batcher.getContext().getMatrices().pop();

        /* A clear gap with a divider between the zoom and the volume */
        int divider = this.area.ex() - 126;

        context.batcher.box(divider, this.area.y + 4, divider + 1, this.area.ey() - 4, 0xff3a4252);

        /* The values next to their sliders: the timeline's scale and the master volume, in percent */
        String zoomText = "%" + Math.round(this.timeline.zoom / BASE_ZOOM * 100D);
        String volumeText = "%" + Math.round(this.volumeValue.getAsDouble() * 100D);

        context.batcher.text(zoomText, this.area.ex() - 160, this.area.y + 7, 0xffaab4c4, false);
        context.batcher.text(volumeText, this.area.ex() - 34, this.area.y + 7, this.volumeValue.getAsDouble() <= 0.0001D ? 0xffe0455a : 0xffaab4c4, false);

        super.render(context);
    }

    /** A thin glass slider, 0 to 1, dragged along */
    public static class Slider extends UIElement
    {
        private final DoubleSupplier value;
        private final DoubleConsumer set;
        private boolean dragging;

        public Slider(DoubleSupplier value, DoubleConsumer set)
        {
            this.value = value;
            this.set = set;
        }

        private void apply(UIContext context)
        {
            this.set.accept(Math.max(0D, Math.min(1D, (context.mouseX - this.area.x) / (double) Math.max(1, this.area.w))));
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                this.dragging = true;
                this.apply(context);

                return true;
            }

            return false;
        }

        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            this.dragging = false;

            return false;
        }

        @Override
        public void render(UIContext context)
        {
            if (this.dragging)
            {
                if (Window.isMouseButtonPressed(0))
                {
                    this.apply(context);
                }
                else
                {
                    this.dragging = false;
                }
            }

            int thumb = this.area.x + (int) Math.round(this.value.getAsDouble() * this.area.w);

            UIGlassSlider.paintTrack(context, this.area.x, this.area.ex(), this.area.my(), thumb, this.dragging || this.area.isInside(context), 8);

            super.render(context);
        }
    }
}
