package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.function.Consumer;

public interface IUIClipsDelegate extends ICursor
{
    public Film getFilm();

    public Camera getCamera();

    public Clip getClip();

    public default String getClipDisplayName(Clip clip)
    {
        return clip != null ? clip.title.get() : "";
    }

    public void pickClip(Clip clip);

    /**
     * Open the given clip's own sub-editor (its keyframe editor), if it has one. The clip has to
     * be the one picked, since the editor lives in the clip's properties panel.
     *
     * @return whether an editor was opened - false for a clip with nothing to open
     */
    public default boolean openClipEditor(Clip clip)
    {
        return false;
    }

    /**
     * Whether the clips here pick a film's actors and use its player recordings. Recap Studio has
     * no actors: its tracking clips follow the recorded player by themselves, so the pickers and
     * the "from a player recording" entry are left out there.
     */
    public default boolean pickActors()
    {
        return true;
    }

    /** The clip timeline this delegate shows, for the clip panels that act on it; null when none. */
    public default UIClips getClipsTimeline()
    {
        return null;
    }

    /** The time under the playhead and the whole length, as the timeline writes them beside it. */
    public default String formatCursorLabel(float cursor, int duration)
    {
        return mchorse.bbs_mod.camera.utils.TimeUtils.formatCursorTime(cursor) + "/" + mchorse.bbs_mod.camera.utils.TimeUtils.formatTime(duration);
    }

    public void setFlight(boolean flight);

    public boolean isFlying();

    public boolean isRunning();

    public void togglePlayback();

    /**
     * Stop the playback when the user scrubs the cursor on a timeline, if the
     * corresponding setting is enabled. Called by timelines when a scrubbing
     * gesture begins or continues.
     */
    public default void stopPlaybackOnScrub()
    {
        if (BBSSettings.editorStopPlaybackOnScrub.get() && this.isRunning())
        {
            this.togglePlayback();
        }
    }

    public boolean canUseKeybinds();

    public void fillData();

    public void embedView(UIElement element);

    /* Undo/redo */

    public void markLastUndoNoMerging();

    public void editMultiple(ValueInt property, int value);

    public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer);
}