package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.recap.RecapWorldOverlay;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.File;
import java.util.List;

/**
 * A video of a recap, from the start of its timeline to its end, through the shared video
 * recorder, the same one the film studio delivers with.
 *
 * <p><b>Time.</b> While a video is recorded the game runs a fixed share of a tick a frame, the
 * video's frame rate, whatever the machine can draw; the recap plays on those ticks, so every frame
 * is the timeline at {@code ticks + transition} exactly and two exports of one recap are the same
 * video.</p>
 *
 * <p><b>Whole frames.</b> The recap's blocks are all put in with their light before a frame is
 * drawn, and a frame whose blocks or light are not done is held, the clock not moving on, until
 * they are ({@link RecapWorldOverlay#isSettled}). The warm up only ends once the first moment is
 * settled.</p>
 *
 * <p>Everything the viewport shows is in the video: the clips, the effects, the time of day, the
 * weather and the view settings; the sound clips are rendered into the video's sound track, and
 * the game's own sounds with it when the video settings ask for them.</p>
 */
public class RecapVideoExport extends VideoExportSession
{
    private final UIRecapEditor editor;
    private long readyAt;
    private long warmupFrom;

    public RecapVideoExport(UIRecapEditor editor)
    {
        this.editor = editor;
    }

    private String movieName;

    /**
     * Starts the export as the delivery panel set it up: its profile (format, codec, frame rate,
     * range), the file's name and the size; false when it could not be started.
     */
    public boolean start(mchorse.bbs_mod.camera.export.VideoExportProfile profile, String name, int width, int height)
    {
        this.profile = profile;
        this.movieName = name;

        width = Math.max(2, width);
        height = Math.max(2, height);
        width += width % 2;
        height += height % 2;

        /* At least a short warm up: the frame is resized and the first moment settles in it */
        long delay = Math.max(250L, (long) (Math.max(0F, BBSSettings.videoDelay.get()) * 1000F));

        return this.begin(BBSRendering.getTexture().id, width, height, delay);
    }

    @Override
    protected boolean prepare()
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null || !this.editor.isSceneShown())
        {
            return false;
        }

        int length = Math.max(1, this.editor.getLength());

        this.startTick = 0;
        this.endTick = length;

        /* The delivery panel's own range, kept inside the timeline */
        if (this.profile != null && this.profile.getRangeType() == mchorse.bbs_mod.camera.export.VideoExportProfile.RangeType.CUSTOM)
        {
            this.startTick = Math.max(0, Math.min(length - 1, this.profile.getCustomStartTick()));
            this.endTick = this.profile.getCustomEndTick() > 0 ? Math.min(length, this.profile.getCustomEndTick()) : length;
            this.endTick = Math.max(this.startTick + 1, this.endTick);
        }

        if (BBSSettings.videoExportAudio.get())
        {
            List<AudioClip> audio = record.clips.getClips(AudioClip.class);

            if (!audio.isEmpty())
            {
                File file = new File(BBSRendering.getVideoFolder(), StringUtils.createTimestampFilename() + ".wav");

                try
                {
                    if (AudioRenderer.renderAudio(file, audio, length, 48000, TimeUtils.toSeconds(this.startTick), TimeUtils.toSeconds(this.endTick)))
                    {
                        this.audioFile = file;
                    }
                }
                catch (Exception e)
                {
                    mchorse.bbs_mod.BBSMod.LOGGER.warn("Recap export could not render its sound track", e);
                }
            }
        }

        this.editor.stopPlayback();
        this.editor.setFlight(false);
        this.editor.seekForExport(this.startTick);
        this.readyAt = 0L;
        this.warmupFrom = 0L;

        return true;
    }

    @Override
    protected String getMovieName()
    {
        if (this.movieName != null && !this.movieName.trim().isEmpty())
        {
            return uniqueName(BBSRendering.getVideoFolder(), this.movieName.trim());
        }

        RecapRecord record = this.editor.getRecord();
        String name = record == null ? "recap" : record.name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();

        return (name.isEmpty() ? "recap" : name) + " " + StringUtils.createTimestampFilename();
    }

    /** A name no earlier video of the folder has, so the encoder never stops to ask about one. */
    private static String uniqueName(File folder, String base)
    {
        String candidate = base;

        for (int i = 1; taken(folder, candidate); i++)
        {
            candidate = base + " " + i;
        }

        return candidate;
    }

    private static boolean taken(File folder, String base)
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

            if (name.startsWith(prefix) && !isExportArtifact(name.substring(prefix.length())))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void applyExportTarget()
    {
        BBSRendering.setCustomSize(true, this.width, this.height);
    }

    @Override
    protected void onWarmupStarted()
    {
        this.editor.stopPlayback();
    }

    @Override
    protected boolean shouldAbortWarmup()
    {
        return this.editor.getRecord() == null || !this.editor.isSceneShown();
    }

    /** The warm up ends once the first moment's blocks and light are all in, held a few frames. */
    @Override
    protected boolean isWarmupReady()
    {
        long now = System.currentTimeMillis();

        if (this.warmupFrom == 0L)
        {
            this.warmupFrom = now;
        }

        /* Light that never settles must not keep the video from ever starting */
        if (now - this.warmupFrom > 10_000L)
        {
            return true;
        }

        if (!RecapScene.isActive() || !RecapWorldOverlay.isSettled())
        {
            this.readyAt = 0L;

            return false;
        }

        if (this.readyAt == 0L)
        {
            this.readyAt = System.currentTimeMillis();
        }

        return System.currentTimeMillis() - this.readyAt > 150L;
    }

    @Override
    protected void onRecordingStarted()
    {
        this.editor.seekForExport(this.startTick);
        this.editor.startPlayback();
    }

    @Override
    protected boolean isFinished()
    {
        if (System.currentTimeMillis() - this.recordingStartedAtMs < 300L)
        {
            return false;
        }

        return this.editor.getRecord() == null || !this.editor.isSceneShown()
            || !this.editor.isPlaying() || this.editor.getCursor() >= this.endTick;
    }

    @Override
    protected void teardown(boolean cancelled)
    {
        this.editor.stopPlayback();
        this.editor.restoreSceneSize();
        this.editor.onExportFinished(cancelled);
    }
}
