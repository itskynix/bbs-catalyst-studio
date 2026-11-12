package mchorse.bbs_mod.dev;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.gui.DrawContext;

/**
 * The developer overlays, drawn after everything else: from the in-game HUD when no screen is up
 * (so F1 does not hide them) and after the screen when one is (the film editor and the hubs).
 * While a video is being captured nothing is drawn at all, so no badge or statistic reaches a file.
 */
public final class DevOverlay
{
    private DevOverlay()
    {}

    /** Whether the render engine, the film exporter or the frame capture is producing video right now */
    public static boolean isCapturing()
    {
        try
        {
            if (BBSModClient.getVideoRecorder().isRecording())
            {
                return true;
            }

            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            return dashboard != null
                && dashboard.getPanels().panel instanceof UIFilmPanel film
                && film.recorder != null
                && (film.recorder.isExporting() || film.recorder.isRecording());
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    public static void render(DrawContext context)
    {
        if (isCapturing())
        {
            return;
        }

        DevStageBadge.render(context);
        DevTelemetryHud.render(context);
    }
}
