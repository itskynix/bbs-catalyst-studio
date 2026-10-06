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
import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.util.math.MathHelper;

public final class PovSpyglassZoomHelper {
    private PovSpyglassZoomHelper() {
    }

    public static float resolveZoomMultiplier(float tickDelta) {
        boolean povEditMode;
        if (PovPlaybackContext.getActive(tickDelta) == null && !PovEffectSuppression.isBbsActive()) {
            return 1.0f;
        }
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return PovSpyglassZoomHelper.sampleFromReplay(playback.replay(), playback.replayTick());
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel == null || !panel.hasParent() || !panel.isVisible() || panel.getData() == null) {
            return 1.0f;
        }
        int povMode = panel.getController().getPovMode();
        if (povMode == 1 || povMode == 2) {
            return 1.0f;
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
            return PovSpyglassZoomHelper.sampleFromReplay(replay, rTick);
        }
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null) {
            if (!(clip.isFirstPerson() && ((Boolean)clip.actions.get()).booleanValue() && ((Boolean)clip.screenEffects.get()).booleanValue())) {
                return 1.0f;
            }
            Replay replay = PovCameraClips.resolveReplay(film, clip);
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovSpyglassZoomHelper.sampleFromReplay(replay, rTick);
        }
        if (povMode == 0 || povMode == 3) {
            Replay replay = film.getFirstPersonReplay();
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovSpyglassZoomHelper.sampleFromReplay(replay, rTick);
        }
        return 1.0f;
    }

    public static float sampleFromReplay(Replay replay, float replayTick) {
        ReplayKeyframes replayKeyframes;
        if (replay == null || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
            return 1.0f;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedPovActions actions = access.bbsPov$getActions();
        if (actions == null) {
            return 1.0f;
        }
        float totalMultiplier = 1.0f;
        for (Clip clip : actions.get()) {
            float zoom;
            ScreenEffectPovActionClip se;
            if (!(clip instanceof ScreenEffectPovActionClip) || !(se = (ScreenEffectPovActionClip)clip).isActive(replayTick)) continue;
            float elapsed = se.getLocalTick(replayTick);
            boolean spyglassActive = false;
            if (se.hasEffect("spyglass") && !se.spyglassZoom.isEmpty() && (zoom = ((Float)se.spyglassZoom.interpolate(elapsed)).floatValue()) > 1.0E-4f && Math.abs(zoom - 1.0f) > 0.001f) {
                totalMultiplier *= PovSpyglassZoomHelper.convertZoomToMultiplier(zoom);
                spyglassActive = true;
            }
            if (spyglassActive || !se.hasEffect("frost") || se.frostZoom.isEmpty() || !((zoom = ((Float)se.frostZoom.interpolate(elapsed)).floatValue()) > 0.001f)) continue;
            totalMultiplier *= MathHelper.clamp((float)zoom, (float)0.1f, (float)2.0f);
        }
        return totalMultiplier;
    }

    public static float convertZoomToMultiplier(float zoom) {
        if (zoom <= 1.0E-4f) {
            return 1.0f;
        }
        if (zoom <= 1.0f) {
            return MathHelper.clamp((float)zoom, (float)0.005f, (float)1.0f);
        }
        return MathHelper.clamp((float)(1.0f / zoom), (float)0.005f, (float)1.0f);
    }
}

