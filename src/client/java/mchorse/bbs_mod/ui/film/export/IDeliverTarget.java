package mchorse.bbs_mod.ui.film.export;

import mchorse.bbs_mod.camera.export.RenderJob;
import mchorse.bbs_mod.camera.export.VideoExportProfile;
import mchorse.bbs_mod.film.VideoExportSession;

/**
 * Something besides a film or a composition that the delivery panel can render: it names itself,
 * says how long it is and starts its own export with the profile, file name and size the panel
 * settled on. The film studio and the compositor keep their own paths; this one is taken only when
 * the panel was opened for such a target.
 */
public interface IDeliverTarget
{
    /** The name the file is called by when none is typed. */
    String getDeliverName();

    /** How long it is, in ticks. */
    int getDeliverDuration();

    /**
     * Starts the export; false when it could not. {@code job}, when given, is the queue's job of it,
     * and {@code finished} hears once when it ends, whether it was stopped.
     */
    boolean startDeliver(VideoExportProfile profile, String filename, int width, int height, RenderJob job, VideoExportSession.FinishedListener finished);
}
