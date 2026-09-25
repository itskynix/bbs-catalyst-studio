package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import org.lwjgl.glfw.GLFW;

/**
 * Thin UI wrapper around a {@link PanelVideoExportSession}. Owns the overlay
 * presence (warm-up countdown, ESC-to-cancel) and delegates the export
 * lifecycle to the session.
 */
public class UIFilmRecorder extends UIElement
{
    public UIFilmPanel editor;

    public boolean resetReplays = true;

    private final PanelVideoExportSession session;
    private final UIExit exit = new UIExit(this);
    private final mchorse.bbs_mod.ui.film.export.UIRenderMonitorHud monitorHud = new mchorse.bbs_mod.ui.film.export.UIRenderMonitorHud();

    public UIFilmRecorder(UIFilmPanel editor)
    {
        super();

        this.editor = editor;
        this.session = new PanelVideoExportSession(this, editor);

        this.add(this.monitorHud);
        this.noCulling();
    }

    public boolean isRecording()
    {
        return this.session.isRecording();
    }

    public boolean isExporting()
    {
        return this.session.isExporting();
    }

    public void setFinishedListener(VideoExportSession.FinishedListener listener)
    {
        this.session.setFinishedListener(listener);
    }

    public void cancel()
    {
        this.session.cancel();
    }

    public void stop()
    {
        this.session.stop();
    }

    public void openMovies()
    {
        UIUtils.openFolder(BBSRendering.getVideoFolder());
    }

    public void startRecording(int duration, Texture texture)
    {
        this.startRecording(duration, texture.id, texture.width, texture.height);
    }

    public void startRecording(int duration, int id, int w, int h)
    {
        this.startRecording(duration, id, w, h, null, null);
    }

    public void setJob(mchorse.bbs_mod.camera.export.RenderJob job)
    {
        this.session.setJob(job);
    }

    public mchorse.bbs_mod.camera.export.RenderJob getJob()
    {
        return this.session.getJob();
    }

    public void startRecording(int duration, int id, int w, int h, mchorse.bbs_mod.camera.export.VideoExportProfile profile, String movieName)
    {
        if (this.editor.isRunning() || duration <= 0)
        {
            return;
        }

        this.session.start(duration, id, w, h, profile, movieName);
    }

    /**
     * Add the recorder to the overlay and disable the main UI. Called by the
     * session as recording is set up.
     */
    void attachOverlay()
    {
        UIContext context = this.editor.getContext();

        String filmTitle = this.editor.getData() != null ? this.editor.getData().getId() : "Film";
        int start = this.session.getStart();
        int end = this.session.getEnd();
        int totalTicks = Math.max(1, end - start);
        int totalFrames = (int) (totalTicks * (BBSRendering.getVideoFrameRate() / 20.0));
        this.monitorHud.start(filmTitle, this.session.getProfile(), totalTicks, totalFrames, BBSRendering.getVideoFrameRate(), this::cancel);
        this.monitorHud.setVisible(true);

        context.menu.main.setEnabled(false);
        context.menu.main.setVisible(false);

        this.full(context.menu.overlay);
        this.monitorHud.full(this);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);
        context.menu.overlay.resize();
    }

    /**
     * Remove the recorder from the overlay and re-enable the main UI. Called by
     * the session during teardown.
     */
    void detachOverlay()
    {
        UIContext context = this.editor.getContext();

        context.menu.main.setVisible(true);
        context.menu.main.setEnabled(true);
        context.render.postRunnable(this.exit::removeFromParent);
        context.render.postRunnable(this::removeFromParent);
    }

    @Override
    public void resize()
    {
        super.resize();
        this.monitorHud.full(this);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.session.isWarmingUp() && BBSSettings.recordingOverlays.get())
        {
            long remainingMs = this.session.getWarmupRemainingMs();
            int countdown = Math.max(0, (int) Math.ceil(remainingMs / 50D));
            Area previewArea = this.editor.preview.getViewport();

            BBSRendering.renderRecordingTimerOverlay(context.batcher, String.valueOf(TimeUtils.toSeconds(countdown)), previewArea.x + 5, previewArea.y + 5);
        }
        else if (this.session.isRecording() && BBSSettings.recordingOverlays.get())
        {
            int relativeTick = Math.max(0, this.editor.getCursor() - this.session.getStart());
            this.monitorHud.updateProgress(
                mchorse.bbs_mod.BBSModClient.getVideoRecorder().getCounter(),
                relativeTick
            );
        }

        this.monitorHud.setVisible(this.session.isExporting());

        super.render(context);

        this.session.update();
    }

    public static class UIExit extends UIElement
    {
        private UIFilmRecorder recorder;

        public UIExit(UIFilmRecorder recorder)
        {
            this.recorder = recorder;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(GLFW.GLFW_KEY_F1))
            {
                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.recorder.cancel();

                return true;
            }

            return super.subKeyPressed(context);
        }
    }
}
