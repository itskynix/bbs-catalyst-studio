package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * The studio's own keyframe timeline, bound to the recap editor: the playhead is the editor's
 * cursor, the length is the recap's, and the recap's markers sit on the ruler.
 *
 * <p>Everything the studio timeline does works here: keyframes are added, removed, dragged along
 * and between tracks, values and curves are edited in its properties panel and its graph. The only
 * scrub is the ruler along the top: a drag on the tracks below selects or moves keyframes, it
 * never moves the cursor. Right clicking a keyframe also offers its curve and its value.</p>
 */
public class UIRecapKeyframes extends UIKeyframes
{
    private final UIRecapEditor editor;
    private boolean rulerPress;

    public UIRecapKeyframes(UIRecapEditor editor, Consumer<Keyframe> callback)
    {
        super(callback);

        this.editor = editor;
        this.duration(editor::getLength);
        this.rulerRenderer(this::renderMarkers);

        /* Added after the studio timeline's own entries */
        this.context((menu) ->
        {
            Keyframe keyframe = this.getGraph().getSelected();

            if (keyframe != null)
            {
                menu.action(Icons.GRAPH, UIRecapKeys.key("tracks.interpolation"), () -> this.openInterpolation(keyframe));
                menu.action(Icons.EDIT, UIRecapKeys.key("tracks.edit_value"), () -> this.editor.getTracksPanel().showProperties(keyframe));
            }

            menu.action(Icons.CURVES, UIRecapKeys.key("tracks.graph"), this::toggleGraphEditor);
        });
    }

    /** The curve of the selected keyframes, picked from the list every timeline uses. */
    public void openInterpolation(Keyframe keyframe)
    {
        Interpolation interpolation = keyframe.getInterpolation();
        UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interpolation);

        this.getContext().replaceContextMenu(menu.callback(() ->
        {
            this.getGraph().setInterpolation(interpolation);
            this.editor.commitTracks();
        }));
    }

    /* The playhead is the editor's cursor */

    @Override
    public float getTick()
    {
        return this.editor.getCursor();
    }

    @Override
    public float getPlayheadTick(UIContext context)
    {
        return this.editor.getCursor() + (this.editor.isPlaying() ? context.getTransition() : 0F);
    }

    @Override
    public Float getAutoKeyframeTick()
    {
        return null;
    }

    @Override
    protected boolean hasCursor()
    {
        return true;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        /* Only a press on the ruler may move the cursor, also when it is dragged afterwards */
        this.rulerPress = context.mouseButton == 0 && this.graphArea.isInside(context)
            && context.mouseY < TimelineRulerRenderer.getRulerBottom(this.area);

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.rulerPress = false;

        boolean result = super.subMouseReleased(context);

        this.editor.commitTracks();

        return result;
    }

    @Override
    protected void moveNoKeyframes(UIContext context)
    {
        if (!this.rulerPress)
        {
            return;
        }

        this.editor.stopPlayback();
        this.editor.seek(Math.round(Math.max(0F, this.fromGraphCursor(context.mouseX))));
    }

    @Override
    protected void selectNextKeyframe(int direction)
    {
        super.selectNextKeyframe(direction);

        Keyframe keyframe = this.getGraph().getSelected();

        if (keyframe != null)
        {
            this.editor.seek(Math.round(keyframe.getTick()));
        }
    }

    /* Rendering */

    /** The recap's markers as flags along the ruler. */
    private void renderMarkers(UIContext context)
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null)
        {
            return;
        }

        int bottom = TimelineRulerRenderer.getRulerBottom(this.area);
        int color = RecapStyle.marker();

        context.batcher.clip(this.graphArea, context);

        for (int marker : record.markers)
        {
            int x = this.toGraphX(marker);

            context.batcher.box(x, this.area.y, x + 1, bottom, color);
            context.batcher.box(x + 1, this.area.y, x + 6, this.area.y + 5, color);
        }

        context.batcher.unclip(context);
    }

    @Override
    protected void renderOverlay(UIContext context)
    {
        float cursor = this.getPlayheadTick(context);
        int x = this.toGraphX(cursor);
        String label = RecapTime.formatWithTicks((int) cursor) + "/" + RecapTime.format(this.getDuration());

        context.batcher.clip(this.graphArea, context);
        UITimelineCanvas.renderCursor(context, label, this.area, x - 1);
        context.batcher.unclip(context);

        super.renderOverlay(context);
    }
}
