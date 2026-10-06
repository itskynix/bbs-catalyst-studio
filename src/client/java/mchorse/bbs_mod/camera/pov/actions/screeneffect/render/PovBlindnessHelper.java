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

public final class PovBlindnessHelper {
    public static final float DEFAULT_RADIUS = 5.0f;

    private PovBlindnessHelper() {
    }

    public static float resolveBlindnessFactor(float tickDelta) {
        Sample sample = PovBlindnessHelper.resolve(tickDelta);
        return sample != null ? sample.factor : -1.0f;
    }

    public static float resolveBlindnessRadius(float tickDelta) {
        Sample sample = PovBlindnessHelper.resolve(tickDelta);
        return sample != null ? sample.radius : 5.0f;
    }

    private static Sample resolve(float tickDelta) {
        boolean povEditMode;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return PovBlindnessHelper.sampleFromReplay(playback.replay(), playback.replayTick());
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel == null || panel.getData() == null) {
            return null;
        }
        int povMode = panel.getController().getPovMode();
        if (povMode == 1 || povMode == 2) {
            return null;
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
            return PovBlindnessHelper.sampleFromReplay(replay, rTick);
        }
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null) {
            if (!((Boolean)clip.actions.get()).booleanValue()) {
                return null;
            }
            Replay replay = PovCameraClips.resolveReplay(film, clip);
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovBlindnessHelper.sampleFromReplay(replay, rTick);
        }
        if (povMode == 0 || povMode == 3) {
            Replay replay = film.getFirstPersonReplay();
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovBlindnessHelper.sampleFromReplay(replay, rTick);
        }
        return null;
    }

    private static Sample sampleFromReplay(Replay replay, float replayTick) {
        ReplayKeyframes replayKeyframes;
        if (replay == null || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
            return null;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedPovActions actions = access.bbsPov$getActions();
        if (actions == null) {
            return null;
        }
        for (Clip clip : actions.get()) {
            ScreenEffectPovActionClip se;
            if (!(clip instanceof ScreenEffectPovActionClip) || !(se = (ScreenEffectPovActionClip)clip).isActive(replayTick) || !se.hasEffect("blindness")) continue;
            float elapsed = se.getLocalTick(replayTick);
            float opacity = se.blindnessOpacity.isEmpty() ? 1.0f : ((Float)se.blindnessOpacity.interpolate(elapsed)).floatValue();
            float radius = se.blindnessRadius.isEmpty() ? 5.0f : ((Float)se.blindnessRadius.interpolate(elapsed)).floatValue();
            return new Sample(MathHelper.clamp((float)opacity, (float)0.0f, (float)1.0f), Math.max(0.5f, radius));
        }
        return null;
    }

    private record Sample(float factor, float radius) {
    }
}

