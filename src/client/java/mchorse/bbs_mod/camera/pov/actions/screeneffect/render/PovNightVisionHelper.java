/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.util.math.MathHelper;

public final class PovNightVisionHelper {
    private PovNightVisionHelper() {
    }

    public static float resolveNightVisionStrength(float tickDelta) {
        boolean povEditMode;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return PovNightVisionHelper.sampleFromReplay(playback.replay(), playback.replayTick());
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel == null || panel.getData() == null) {
            return -1.0f;
        }
        int povMode = panel.getController().getPovMode();
        if (povMode == 1 || povMode == 2) {
            return -1.0f;
        }
        Film film = (Film)panel.getData();
        int cursor = panel.getCursor();
        boolean playing = panel.getRunner() != null && panel.getRunner().isRunning();
        float transition = playing ? Math.max(0.0f, Math.min(1.0f, tickDelta)) : 0.0f;
        float filmTick = (float)cursor + transition;
        boolean bl = povEditMode = povMode == 6;
        if (povEditMode) {
            Replay replay;
            Replay replay2 = replay = panel.replayEditor != null ? panel.replayEditor.getReplay() : null;
            if (replay == null) {
                replay = film.getFirstPersonReplay();
            }
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovNightVisionHelper.sampleFromReplay(replay, rTick);
        }
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null) {
            if (!((Boolean)clip.actions.get()).booleanValue()) {
                return -1.0f;
            }
            Replay replay = PovCameraClips.resolveReplay(film, clip);
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovNightVisionHelper.sampleFromReplay(replay, rTick);
        }
        if (povMode == 0 || povMode == 3) {
            Replay replay = film.getFirstPersonReplay();
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovNightVisionHelper.sampleFromReplay(replay, rTick);
        }
        return -1.0f;
    }

    private static float sampleFromReplay(Replay replay, float replayTick) {
        ReplayKeyframes replayKeyframes;
        if (replay == null || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
            return -1.0f;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedPovActions actions = access.bbsPov$getActions();
        if (actions == null) {
            return -1.0f;
        }
        for (Clip clip : actions.get()) {
            boolean isVisible;
            ScreenEffectPovActionClip se;
            if (!(clip instanceof ScreenEffectPovActionClip) || !(se = (ScreenEffectPovActionClip)clip).isActive(replayTick) || !se.hasEffect("night_vision")) continue;
            float elapsed = se.getLocalTick(replayTick);
            boolean bl = isVisible = se.nightVisionVisible.isEmpty() ? true : (Boolean)se.nightVisionVisible.interpolate(elapsed);
            if (!isVisible) {
                return 0.0f;
            }
            float opacity = se.nightVisionOpacity.isEmpty() ? 1.0f : ((Float)se.nightVisionOpacity.interpolate(elapsed)).floatValue();
            return MathHelper.clamp((float)opacity, (float)0.0f, (float)1.0f);
        }
        return -1.0f;
    }
}

