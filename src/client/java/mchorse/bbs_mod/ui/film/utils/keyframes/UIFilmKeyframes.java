package mchorse.bbs_mod.ui.film.utils.keyframes;

import mchorse.bbs_mod.api.client.events.TimelineEvents;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.markers.UIMarkerOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.markers.UIMarkersController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.DoubleClick;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class UIFilmKeyframes extends UIKeyframes
{
    public IUIClipsDelegate editor;
    public boolean absolute;

    /**
     * Every film keyframe timeline knows both the film and its own clip offset, so wiring the
     * markers here covers the replay dope sheet and every nested clip keyframe editor at once.
     */
    private final UIMarkersController markers = new UIMarkersController(this::getFilmMarkers);
    private final DoubleClick<FilmMarker> doubleClick = new DoubleClick<>(true);

    public UIFilmKeyframes(IUIClipsDelegate delegate, Consumer<Keyframe> callback)
    {
        super(callback);

        this.editor = delegate;

        /* Ruler right-click menu for timeline markers */
        this.context((menu) ->
        {
            UIContext context = this.getContext();
            if (context == null || !this.graphArea.isInside(context) || context.mouseY >= TimelineRulerRenderer.getRulerBottom(this.graphArea))
            {
                return;
            }

            FilmMarkers filmMarkers = this.getFilmMarkers();
            if (filmMarkers == null)
            {
                return;
            }

            FilmMarker marker = this.markers.getMarkerAt(this.graphArea, this.getXAxis(), context.mouseX, context.mouseY, (int) this.getClipOffset());

            if (marker == null)
            {
                int tick = Math.max(0, (int) Math.round(this.fromGraphX(context.mouseX) + this.getClipOffset()));
                menu.action(Icons.ADD, UIKeys.FILM_MARKERS_ADD, () -> this.editMarker(filmMarkers.addMarker(tick)));
            }
            else
            {
                menu.action(Icons.EDIT, UIKeys.FILM_MARKERS_EDIT, () -> this.editMarker(marker));
                menu.action(Icons.REMOVE, UIKeys.FILM_MARKERS_REMOVE, () -> {
                    filmMarkers.remove(marker);
                    if (this.editor != null)
                    {
                        this.editor.markLastUndoNoMerging();
                    }
                });
            }
        });
    }

    private FilmMarkers getFilmMarkers()
    {
        Film film = this.editor == null ? null : this.editor.getFilm();

        return film == null ? null : film.markers;
    }

    public UIFilmKeyframes absolute()
    {
        this.absolute = true;

        return this;
    }

    public long getClipOffset()
    {
        if (this.absolute)
        {
            return 0;
        }

        if (this.editor == null || this.editor.getClip() == null)
        {
            return 0;
        }

        return this.editor.getClip().tick.get();
    }

    public float getOffset()
    {
        if (this.editor == null)
        {
            return 0;
        }

        UIContext context = this.getContext();

        return this.editor.getKeyframeCursor(context == null ? 0F : context.getTransition()) - this.getClipOffset();
    }

    @Override
    public float getTick()
    {
        return this.getOffset();
    }

    @Override
    public float getPlayheadTick(UIContext context)
    {
        return this.editor == null ? 0F : this.editor.getTimelineCursor(context.getTransition()) - this.getClipOffset();
    }

    /**
     * The playhead in this timeline's own tick space &mdash; {@link #getOffset()} rather than the
     * raw cursor, so a keyframe clip keys where its cursor is drawn instead of at the film's tick.
     */
    @Override
    public Float getAutoKeyframeTick()
    {
        if (!BBSSettings.autoKeyframe.get() || this.editor == null)
        {
            return null;
        }

        return this.getOffset();
    }

    @Override
    protected void selectNextKeyframe(int direction)
    {
        super.selectNextKeyframe(direction);

        Keyframe keyframe = this.getGraph().getSelected();

        if (keyframe != null)
        {
            this.editor.setCursor(keyframe.getTick() + this.getClipOffset());
        }
    }

    @Override
    protected boolean hasCursor()
    {
        return this.editor != null;
    }

    @Override
    protected void moveNoKeyframes(UIContext context)
    {
        if (this.editor != null)
        {
            long offset = this.getClipOffset();

            this.editor.stopPlaybackOnScrub();
            this.editor.setCursor(Math.max(0F, this.fromGraphCursor(context.mouseX) + offset));
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        FilmMarkers markers = this.getFilmMarkers();

        if (markers != null && this.graphArea.isInside(context) && context.mouseY < TimelineRulerRenderer.getRulerBottom(this.graphArea))
        {
            FilmMarker marker = this.markers.getMarkerAt(this.graphArea, this.getXAxis(), context.mouseX, context.mouseY, (int) this.getClipOffset());

            if (context.mouseButton == 0 && marker != null)
            {
                if (this.doubleClick.hit(marker))
                {
                    this.editMarker(marker);
                    return true;
                }

                this.markers.beginDrag(marker);
                if (this.editor != null)
                {
                    this.editor.stopPlaybackOnScrub();
                    this.editor.setCursor(marker.tick.get());
                }
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.markers.isDragging())
        {
            FilmMarker marker = this.markers.getDragged();

            if (marker != null)
            {
                int tick = this.markers.getDragTick();
                this.markers.stopDrag();

                if (tick != marker.tick.get())
                {
                    if (this.editor != null)
                    {
                        this.editor.markLastUndoNoMerging();
                    }
                    marker.tick.set(tick);
                }
            }

            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected void handleMouse(UIContext context)
    {
        if (this.markers.isDragging())
        {
            this.markers.dragTo(Math.max(0, (int) Math.round(this.fromGraphX(context.mouseX) + this.getClipOffset())));
            return;
        }

        super.handleMouse(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_M) && !context.isFocused() && !Window.isCtrlPressed() && !Window.isAltPressed())
        {
            this.addOrEditMarkerAtPlayhead(context);
            return true;
        }

        return super.subKeyPressed(context);
    }

    public void addOrEditMarkerAtPlayhead(UIContext context)
    {
        FilmMarkers filmMarkers = this.getFilmMarkers();

        if (filmMarkers == null)
        {
            return;
        }

        int tick = Math.max(0, (int) Math.round(this.getPlayheadTick(context) + this.getClipOffset()));
        FilmMarker existing = filmMarkers.getAt(tick);

        if (existing != null)
        {
            this.editMarker(existing);
        }
        else
        {
            FilmMarker newMarker = filmMarkers.addMarker(tick);
            if (this.editor != null)
            {
                this.editor.markLastUndoNoMerging();
            }
            this.editMarker(newMarker);
        }
    }

    public void editMarker(FilmMarker marker)
    {
        FilmMarkers filmMarkers = this.getFilmMarkers();

        if (filmMarkers != null && marker != null)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMarkerOverlayPanel(filmMarkers, marker), 220, 190);
        }
    }

    @Override
    protected void renderOverlay(UIContext context)
    {
        if (this.editor != null)
        {
            float cursor = this.getPlayheadTick(context);
            int cx = this.toGraphX(cursor);
            String label = TimeUtils.formatCursorTime(cursor) + "/" + TimeUtils.formatTime(this.getDuration());

            this.markers.render(context, this.graphArea, this.getXAxis(), (int) this.getClipOffset());

            context.batcher.clip(this.graphArea, context);
            UITimelineCanvas.renderCursor(context, label, this.area, cx - 1);
            context.batcher.unclip(context);
        }

        super.renderOverlay(context);

        if (this.editor != null)
        {
            TimelineEvents.OVERLAY.invoker().render(this.editor.getFilm(), context, this.graphArea,
                tick -> this.toGraphX((float) (tick - this.getClipOffset())));
        }
    }
}
