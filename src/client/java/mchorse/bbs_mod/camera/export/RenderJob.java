package mchorse.bbs_mod.camera.export;

import java.io.File;
import java.util.UUID;

/**
 * Represents a single video render job in the Render Queue.
 */
public class RenderJob
{
    public enum Status
    {
        PENDING,
        RENDERING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String id;
    private final String filmId;
    private String title;
    private VideoExportProfile profile;
    private File outputFolder;
    private String filename;
    private int duration;

    private Status status = Status.PENDING;
    private int progressFrames;
    private int totalFrames;
    private long startedAtMs;
    private long completedAtMs;
    private String errorMessage;

    public RenderJob(String filmId, String title, VideoExportProfile profile, File outputFolder, String filename, int duration)
    {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.filmId = filmId;
        this.title = title != null && !title.isEmpty() ? title : filmId;
        this.profile = profile != null ? profile.copy() : VideoExportProfile.getBuiltInPresets().get(0).copy();
        this.outputFolder = outputFolder;
        this.filename = filename;
        this.duration = duration;
        this.totalFrames = duration;
    }

    public String getId()
    {
        return this.id;
    }

    public String getFilmId()
    {
        return this.filmId;
    }

    public String getTitle()
    {
        return this.title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public VideoExportProfile getProfile()
    {
        return this.profile;
    }

    public void setProfile(VideoExportProfile profile)
    {
        this.profile = profile;
    }

    public File getOutputFolder()
    {
        return this.outputFolder;
    }

    public void setOutputFolder(File outputFolder)
    {
        this.outputFolder = outputFolder;
    }

    public String getFilename()
    {
        return this.filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public int getDuration()
    {
        return this.duration;
    }

    public void setDuration(int duration)
    {
        this.duration = duration;
        this.totalFrames = duration;
    }

    public Status getStatus()
    {
        return this.status;
    }

    public void setStatus(Status status)
    {
        this.status = status;

        if (status == Status.RENDERING)
        {
            this.startedAtMs = System.currentTimeMillis();
            this.progressFrames = 0;
        }
        else if (status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED)
        {
            this.completedAtMs = System.currentTimeMillis();
        }

        RenderQueue.notifyListeners();
    }

    public int getProgressFrames()
    {
        return this.progressFrames;
    }

    public void setProgressFrames(int progressFrames)
    {
        this.progressFrames = progressFrames;
    }

    public int getTotalFrames()
    {
        return this.totalFrames;
    }

    public void setTotalFrames(int totalFrames)
    {
        this.totalFrames = totalFrames;
    }

    public float getProgress()
    {
        if (this.totalFrames <= 0)
        {
            return 0F;
        }

        return Math.min(1F, (float) this.progressFrames / (float) this.totalFrames);
    }

    public long getStartedAtMs()
    {
        return this.startedAtMs;
    }

    public long getCompletedAtMs()
    {
        return this.completedAtMs;
    }

    public String getErrorMessage()
    {
        return this.errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
