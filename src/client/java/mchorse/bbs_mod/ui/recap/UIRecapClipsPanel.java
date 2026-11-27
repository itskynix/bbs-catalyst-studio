package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipTargets;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.ProCameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.recap.RecapClips;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.UIProCameraClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.DataPath;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Recap Studio's timeline: the film studio's own clip timeline ({@link UIClips}) over the recap's
 * clips, with the clip properties panel beside it, as in the film studio's camera editor.
 *
 * <p>Clips are added from the right click menu (its cameras, effects and tools), moved, trimmed
 * at their ends, copied and removed as in the film studio; a double click opens a clip's own
 * keyframes or dope sheet in the timeline's place, with the way back on top. The cursor is the
 * recap editor's, the ruler scrubs it and carries the recap's markers. Every change ends up in
 * the recap and in the editor's history ({@link UIRecapEditor#commitTimeline}).</p>
 */
public class UIRecapClipsPanel extends UITimelinePanel implements IUIClipsDelegate, IUIClipTargets
{
    /** The clips that look for a film's actors find none: a recap has no actors, only the world. */
    private static final Film NO_FILM = new Film();

    private final UIRecapEditor editor;
    public final UIClips clips;

    private UIClip panel;

    public UIRecapClipsPanel(UIRecapEditor editor)
    {
        this.editor = editor;

        UIRecapToolClips.setup();

        this.clips = new UIRecapClips(this, RecapClips.factory());
        this.add(this.clips.full(this));
    }

    /** The properties live in the editor's clip panel, not beside the timeline. */
    @Override
    protected UIElement getPropertiesPanel()
    {
        return null;
    }

    @Override
    protected UIElement getTimeline()
    {
        return this.clips;
    }

    /** Shows the recap's clips again: after it is opened, or a step of the history put them back. */
    public void setRecord(RecapRecord record)
    {
        this.pickClip(null);
        this.clips.setClips(record == null ? null : record.clips);
    }

    /** The picked clip takes the camera that was flown, at the cursor, as the film studio\x27s does. */
    public void editClip(Position position)
    {
        if (this.panel != null)
        {
            this.panel.editClip(position);
        }
    }

    /** What a clip is given as it is made: the film studio's defaults leave some of them still here. */
    @Override
    public void clipCreated(Clip clip)
    {
        if (clip instanceof DollyClip dolly)
        {
            /* The film studio's tenth of a block cannot be seen over a recording */
            dolly.distance.set(4F);
        }
        else if (clip instanceof TrackerClip tracker)
        {
            /* Behind and a little above the recorded player, looking the way it looks */
            tracker.selector.set(RecapClips.PLAYER);
            tracker.offset.set(new Point(0D, 1.7D, -4D));
        }
    }

    /* Targets: the recorded players */

    @Override
    public void showTargets(UIContext context, String current, Consumer<String> callback)
    {
        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(""));

            for (Map.Entry<String, String> target : RecapScene.targets().entrySet())
            {
                String id = target.getKey();
                IKey name = IKey.constant(this.targetName(id));

                if (id.equals(current))
                {
                    menu.action(Icons.POSE, name, BBSSettings.primaryColor(0), () -> callback.accept(id));
                }
                else
                {
                    menu.action(Icons.POSE, name, () -> callback.accept(id));
                }
            }
        });
    }

    @Override
    public void showTargetBones(UIContext context, String target, String current, Consumer<String> callback)
    {
        IEntity entity = RecapScene.target(target == null || target.isEmpty() ? RecapClips.PLAYER : target);
        Form form = entity == null ? null : entity.getForm();

        if (form == null)
        {
            return;
        }

        Set<String> bones = FormUtilsClient.getRenderer(form).collectMatrices(entity, 0F).keySet();

        if (bones.isEmpty())
        {
            return;
        }

        UIBonePickerContextMenu picker = new UIBonePickerContextMenu(callback);

        picker.attachments(form, bones).set(current);
        context.replaceContextMenu(picker);
    }

    @Override
    public String targetName(String target)
    {
        String name = RecapScene.targets().get(target);

        if (RecapClips.PLAYER.equals(target))
        {
            return UIRecapKeys.text("target.player");
        }

        return name == null || name.isEmpty() ? target : name;
    }

    @Override
    public Map<String, IEntity> targetEntities()
    {
        return RecapScene.targetEntities();
    }

    /* The delegate */

    @Override
    public Film getFilm()
    {
        return NO_FILM;
    }

    @Override
    public Camera getCamera()
    {
        return BBSModClient.getCameraController().camera;
    }

    @Override
    public Clip getClip()
    {
        return this.panel == null ? null : this.panel.clip;
    }

    @Override
    public String getClipDisplayName(Clip clip)
    {
        return clip != null ? this.clips.getClipDisplayName(clip) : "";
    }

    /**
     * Picks a clip: its properties go into the editor's own clip panel, between the record and
     * the view settings, instead of being squeezed beside the timeline.
     */
    @Override
    public void pickClip(Clip clip)
    {
        UIClip.saveScroll(this.panel);

        if (this.panel != null && this.panel.clip == clip)
        {
            this.panel.fillData();

            return;
        }

        this.panel = null;

        if (clip == null)
        {
            this.clips.clearSelection();
            this.editor.showClipPanel(null);

            return;
        }

        this.clips.embedView(null);
        this.panel = clip instanceof ProCameraClip proCamera ? new UIProCameraClip(proCamera, this) : UIClip.createPanel(clip, this);

        if (this.panel != null)
        {
            this.panel.setUndoId("recap_clip_panel");
            this.editor.showClipPanel(this.panel);
            this.panel.fillData();
            this.panel.restoreScroll();
        }
        else
        {
            this.editor.showClipPanel(null);
        }
    }

    /** The recording's length while there are no clips, else as far as the last clip reaches. */
    @Override
    public int getTimelineDuration(Clips clips)
    {
        return this.editor.getLength();
    }

    @Override
    public boolean pickActors()
    {
        return false;
    }

    @Override
    public UIClips getClipsTimeline()
    {
        return this.clips;
    }

    /** The recap's own time code, as on the viewport: where the cursor is and the whole length. */
    @Override
    public String formatCursorLabel(float cursor, int duration)
    {
        return mchorse.bbs_mod.recap.RecapTime.formatWithTicks((int) cursor) + " / " + mchorse.bbs_mod.recap.RecapTime.formatWithTicks(this.editor.getLength());
    }

    @Override
    public boolean openClipEditor(Clip clip)
    {
        return clip != null && this.panel != null && this.panel.clip == clip && this.panel.openSubEditor();
    }

    @Override
    public void setFlight(boolean flight)
    {
        this.editor.setFlight(flight);
    }

    @Override
    public boolean isFlying()
    {
        return this.editor.isFlying();
    }

    @Override
    public int getCursor()
    {
        return this.editor.getCursor();
    }

    @Override
    public void setCursor(int tick)
    {
        this.editor.seek(tick);
    }

    @Override
    public boolean isRunning()
    {
        return this.editor.isPlaying();
    }

    @Override
    public void togglePlayback()
    {
        this.editor.togglePlayback();
    }

    @Override
    public boolean canUseKeybinds()
    {
        return this.editor.isVisible() && this.editor.getRecord() != null;
    }

    @Override
    public void fillData()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    @Override
    public void embedView(UIElement element)
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.closeContextMenu();
        }

        this.clips.embedView(element);
    }

    @Override
    public void markLastUndoNoMerging()
    {}

    @Override
    public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer)
    {
        DataPath path = property.getRelativePath(this.getClip());

        if (path == null)
        {
            consumer.accept(property);

            return;
        }

        for (Clip clip : this.clips.getClipsFromSelection())
        {
            BaseValue value = clip.findRecursively(path);

            if (value != null && value.getClass() == property.getClass())
            {
                consumer.accept((T) value);
            }
        }
    }

    @Override
    public void editMultiple(ValueInt property, int value)
    {
        int difference = value - property.get();
        List<Clip> selected = this.clips.getClipsFromSelection();

        for (Clip clip : selected)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());
            int next = clipValue.get() + difference;

            if (next < clipValue.getMin() || next > clipValue.getMax())
            {
                return;
            }
        }

        for (Clip clip : selected)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());

            clipValue.set(clipValue.get() + difference);
        }
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        boolean result = super.subMouseReleased(context);

        /* A clip moved, trimmed or a value dragged in its panel is a step once it is let go */
        this.editor.commitTimeline();

        return result;
    }

    /** The film studio's clip timeline, with the recap's markers as flags along its ruler. */
    private class UIRecapClips extends UIClips
    {
        private UIRecapClips(IUIClipsDelegate delegate, mchorse.bbs_mod.utils.factory.IFactory<Clip, mchorse.bbs_mod.camera.clips.ClipFactoryData> factory)
        {
            super(delegate, factory);
        }

        @Override
        public void render(UIContext context)
        {
            super.render(context);

            RecapRecord record = UIRecapClipsPanel.this.editor.getRecord();
            Clips clips = this.getClips();

            if (record == null || clips == null || this.hasEmbeddedView())
            {
                return;
            }

            int bottom = TimelineRulerRenderer.getRulerBottom(this.area);
            int color = RecapStyle.marker();

            context.batcher.clip(this.area, context);

            for (int marker : record.markers)
            {
                int x = this.toGraphX(marker);

                context.batcher.box(x, this.area.y, x + 1, bottom, color);
                context.batcher.box(x + 1, this.area.y, x + 6, this.area.y + 5, color);
            }

            this.renderDimensions(context, record, clips, bottom);

            context.batcher.unclip(context);
        }

        /**
         * The dimensions the recording went through, as a coloured band under the ruler: what the
         * timeline shows at each moment, after the speed and freeze clips, with each stretch's name
         * where it fits and under the pointer. Only for a recap that went through more than one, or
         * that is in another one than the client.
         */
        private void renderDimensions(UIContext context, RecapRecord record, Clips clips, int bottom)
        {
            mchorse.bbs_mod.recap.RecapDimensions dimensions = record.dimensions;

            if (dimensions.isEmpty())
            {
                return;
            }

            String here = mchorse.bbs_mod.recap.RecapWorldOverlay.dimensionOf(net.minecraft.client.MinecraftClient.getInstance().world);

            if (!dimensions.isMixed() && dimensions.getId(0).equals(here))
            {
                return;
            }

            int length = UIRecapClipsPanel.this.editor.getLength();
            int y = bottom;
            int h = 4;
            int startX = Math.max(this.area.x, this.toGraphX(0));
            int endX = Math.min(this.area.ex(), this.toGraphX(length));
            String hovered = null;
            String run = null;
            int runX = startX;

            for (int x = startX; x < endX; x += 2)
            {
                String id = this.dimensionAt(dimensions, clips, x);

                if (id != null && id.equals(run))
                {
                    continue;
                }

                if (run != null)
                {
                    this.renderStretch(context, run, runX, x, y, h);
                }

                run = id;
                runX = x;
            }

            /* The last stretch ends with the recording, whatever the width left over at this zoom */
            if (run != null)
            {
                this.renderStretch(context, run, runX, endX, y, h);
            }

            int mouseX = context.mouseX;
            int mouseY = context.mouseY;

            if (mouseX >= startX && mouseX < endX && mouseY >= y - 2 && mouseY < y + h + 2)
            {
                hovered = this.dimensionAt(dimensions, clips, mouseX);
            }

            if (hovered != null)
            {
                String label = UIRecapKeys.dimensionName(hovered);
                int w = context.batcher.getFont().getWidth(label) + 6;
                int lx = Math.min(mouseX + 6, this.area.ex() - w);

                context.batcher.box(lx, y + h + 2, lx + w, y + h + 14, 0xee000000);
                context.batcher.text(label, lx + 3, y + h + 4, RecapStyle.dimension(hovered), false);
            }
        }

        private String dimensionAt(mchorse.bbs_mod.recap.RecapDimensions dimensions, Clips clips, int x)
        {
            double tick = this.fromGraphX(x);

            if (tick < 0)
            {
                return null;
            }

            float source = RecapClips.sourceTime(clips, (float) tick);

            return dimensions.at((int) Math.floor(source));
        }

        private void renderStretch(UIContext context, String id, int x1, int x2, int y, int h)
        {
            if (x2 <= x1)
            {
                return;
            }

            int color = RecapStyle.dimension(id);

            context.batcher.box(x1, y, x2, y + h, color);
            /* The start of a stretch, a dimension change, as a tick of its own */
            context.batcher.box(x1, y - 3, x1 + 1, y + h, color);

            String label = UIRecapKeys.dimensionName(id);
            int width = context.batcher.getFont().getWidth(label);

            if (x2 - x1 > width + 8)
            {
                context.batcher.text(label, x1 + 3, y + h + 1, mchorse.bbs_mod.utils.colors.Colors.setA(color, 0.85F), true);
            }
        }
    }
}
