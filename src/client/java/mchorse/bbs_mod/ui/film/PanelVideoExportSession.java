package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import org.joml.Vector2i;

import java.io.File;
import java.util.List;

/**
 * Video export of the film panel's preview texture. Renders the audio track,
 * restarts the film to the (loop-aware) start, pauses/resumes the editor around
 * the warm-up, and drives the {@link UIFilmRecorder} overlay.
 */
public class PanelVideoExportSession extends VideoExportSession
{
    private final UIFilmRecorder ui;
    private final UIFilmPanel editor;

    private int duration;
    private int end;
    private boolean restorePaused;
    private String customMovieName;

    public int getDuration()
    {
        return this.duration;
    }

    public int getStart()
    {
        return this.startTick;
    }

    public int getEnd()
    {
        return this.endTick;
    }

    public PanelVideoExportSession(UIFilmRecorder ui, UIFilmPanel editor)
    {
        this.ui = ui;
        this.editor = editor;
    }

    public boolean start(int duration, int textureId, int width, int height)
    {
        return this.start(duration, textureId, width, height, null, null);
    }

    public boolean start(int duration, int textureId, int width, int height, mchorse.bbs_mod.camera.export.VideoExportProfile profile, String movieName)
    {
        this.profile = profile;
        this.customMovieName = movieName;
        this.duration = duration;

        long delayMs = (long) (Math.max(0F, BBSSettings.videoDelay.get()) * 1000F);

        return this.begin(textureId, width, height, delayMs);
    }

    @Override
    protected boolean prepare()
    {
        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;

        if (this.profile != null && this.profile.getRangeType() == mchorse.bbs_mod.camera.export.VideoExportProfile.RangeType.IN_OUT && min != max)
        {
            this.startTick = Math.min(min, max);
            this.endTick = Math.max(min, max);
        }
        else if (this.profile != null && this.profile.getRangeType() == mchorse.bbs_mod.camera.export.VideoExportProfile.RangeType.CUSTOM)
        {
            this.startTick = Math.max(0, this.profile.getCustomStartTick());
            this.endTick = Math.max(this.startTick + 1, this.profile.getCustomEndTick() > 0 ? this.profile.getCustomEndTick() : this.duration);
        }
        else if (BBSSettings.editorLoop.get() && min != max)
        {
            this.startTick = Math.min(min, max);
            this.endTick = Math.max(min, max);
        }
        else
        {
            this.startTick = 0;
            this.endTick = this.duration;
        }

        try
        {
            if (BBSSettings.videoExportAudio.get())
            {
                Clips camera = this.editor.getData().camera;
                List<AudioClip> audioClips = camera.getClips(AudioClip.class);

                String name = StringUtils.createTimestampFilename() + ".wav";
                File file = new File(BBSRendering.getVideoFolder(), name);

                if (AudioRenderer.renderAudio(file, audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(this.startTick), TimeUtils.toSeconds(this.endTick)))
                {
                    this.audioFile = file;
                }
            }
        }
        catch (Exception e)
        {
            UIOverlay.addOverlay(this.editor.getContext(), new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, IKey.constant(e.getMessage())));

            return false;
        }

        this.restorePaused = this.editor.getController().isPaused();

        this.editor.setCursor(this.startTick);
        this.editor.notifyServer(ActionState.RESTART);

        if (this.ui.resetReplays)
        {
            this.editor.getController().createEntities();
        }

        this.ui.attachOverlay();

        return true;
    }

    @Override
    protected String getMovieName()
    {
        if (this.customMovieName != null && !this.customMovieName.isEmpty())
        {
            return uniqueName(BBSRendering.getVideoFolder(), this.customMovieName);
        }

        Film film = this.editor.getData();
        String base = StringUtils.resolveExportFilename(
            BBSSettings.videoExportFilenameFormat.get(),
            film == null ? "" : film.getId(),
            this.width,
            this.height,
            BBSRendering.getVideoFrameRate(),
            film == null ? 0 : film.camera.calculateDuration()
        );

        return uniqueName(BBSRendering.getVideoFolder(), base);
    }

    /**
     * Avoid overwriting a previous export - and, with it, hanging ffmpeg (whose default args
     * carry no {@code -y}): when a file with this base name already exists, append " (n)".
     */
    private static String uniqueName(File folder, String base)
    {
        String candidate = base;

        for (int i = 1; nameTaken(folder, candidate); i++)
        {
            candidate = base + " (" + i + ")";
        }

        return candidate;
    }

    private static boolean nameTaken(File folder, String base)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return false;
        }

        String prefix = (base + ".").toLowerCase();

        for (File file : files)
        {
            String name = file.getName().toLowerCase();

            /* Only a video of an earlier export takes the name: the audio track this
             * export just rendered sits in the same folder under the same base name and
             * must not bump every audio export to a "(1)" */
            if (name.startsWith(prefix) && !isExportArtifact(name.substring(prefix.length())))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onWarmupStarted()
    {
        System.out.println("[PanelVideoExportSession] onWarmupStarted: pausing editor controller");
        this.editor.getController().setPaused(true);
    }

    @Override
    protected void onRecordingStarted()
    {
        System.out.println("[PanelVideoExportSession] onRecordingStarted: resetting cursor to " + this.startTick + " and starting playback!");
        this.editor.getController().setPaused(false);
        this.editor.setCursor(this.startTick);
        if (!this.editor.isRunning())
        {
            this.editor.togglePlayback();
        }
    }

    @Override
    protected boolean isFinished()
    {
        if (System.currentTimeMillis() - this.recordingStartedAtMs < 300)
        {
            return false;
        }

        boolean finished = !this.editor.isRunning() || this.editor.getCursor() >= this.endTick;
        if (finished)
        {
            System.out.println("[PanelVideoExportSession] isFinished triggered: isRunning=" + this.editor.isRunning() + ", cursor=" + this.editor.getCursor() + ", end=" + this.endTick);
        }
        return finished;
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        this.editor.getController().setPaused(this.restorePaused);
        this.editor.restorePreviewSize();

        if (this.editor.isRunning())
        {
            this.editor.togglePlayback();
        }

        this.ui.detachOverlay();

        mchorse.bbs_mod.camera.export.RenderJob targetJob = this.job != null ? this.job : mchorse.bbs_mod.camera.export.RenderQueue.getActiveJob();
        if (targetJob != null)
        {
            targetJob.setStatus(cancelled ? mchorse.bbs_mod.camera.export.RenderJob.Status.CANCELLED : mchorse.bbs_mod.camera.export.RenderJob.Status.COMPLETED);
            if (mchorse.bbs_mod.camera.export.RenderQueue.getActiveJob() == targetJob)
            {
                mchorse.bbs_mod.camera.export.RenderQueue.setActiveJob(null);
            }
            this.job = null;
        }
    }
}
