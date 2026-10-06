/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.camera.controller.RunnerCameraController
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.ui.dashboard.UIDashboard
 *  mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.UIBaseMenu
 *  mchorse.bbs_mod.ui.framework.UIScreen
 */
package mchorse.bbs_mod.camera.pov.replay;

import mchorse.bbs_mod.camera.pov.replay.ReplayPovAccess;
import java.lang.ref.WeakReference;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;

public final class PovReplaySettings {
    private static WeakReference<UIFilmPanel> lastFilmPanel = new WeakReference<UIFilmPanel>(null);

    private PovReplaySettings() {
    }

    public static boolean isOverlayEnabled(Replay replay) {
        ReplayPovAccess access;
        return replay instanceof ReplayPovAccess && (Boolean)(access = (ReplayPovAccess)replay).bbsPov$getOverlayEnabled().get() != false;
    }

    public static boolean isCameraShakeEnabled(Replay replay) {
        ReplayPovAccess access;
        return replay instanceof ReplayPovAccess && (Boolean)(access = (ReplayPovAccess)replay).bbsPov$getCameraShake().get() != false;
    }

    public static Replay getSelectedReplay() {
        UIFilmPanel filmPanel = PovReplaySettings.getFilmPanel();
        return filmPanel == null ? null : filmPanel.replayEditor.getReplay();
    }

    public static UIFilmPanel getFilmPanel() {
        UIFilmPanel filmPanel;
        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
        if (currentMenu instanceof UIDashboard) {
            UIDashboard dashboard = (UIDashboard)currentMenu;
            UIDashboardPanel panel = dashboard.getPanels().panel;
            if (panel instanceof UIFilmPanel) {
                UIFilmPanel filmPanel2 = (UIFilmPanel)panel;
                lastFilmPanel = new WeakReference<UIFilmPanel>(filmPanel2);
                return filmPanel2;
            }
        }
        return (filmPanel = (UIFilmPanel)lastFilmPanel.get()) != null && BBSModClient.getCameraController().getCurrent() instanceof RunnerCameraController ? filmPanel : null;
    }

    public static boolean isPovMode() {
        UIFilmPanel filmPanel = PovReplaySettings.getFilmPanel();
        return filmPanel != null && filmPanel.getController() != null && filmPanel.getController().getPovMode() == 6;
    }
}

