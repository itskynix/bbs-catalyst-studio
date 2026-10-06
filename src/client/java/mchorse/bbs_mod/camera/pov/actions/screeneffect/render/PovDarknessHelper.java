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

public final class PovDarknessHelper {
    public static final float DEFAULT_RADIUS = 15.0f;

    private PovDarknessHelper() {
    }

    public static float resolveDarknessFactor(float tickDelta) {
        Sample sample = PovDarknessHelper.resolve(tickDelta);
        return sample != null ? sample.fogFactor : -1.0f;
    }

    public static float resolveDarknessLightFactor(float tickDelta) {
        Sample sample = PovDarknessHelper.resolve(tickDelta);
        return sample != null ? sample.lightFactor : -1.0f;
    }

    public static float resolveDarknessRadius(float tickDelta) {
        Sample sample = PovDarknessHelper.resolve(tickDelta);
        return sample != null ? sample.radius : 15.0f;
    }

    private static Sample resolve(float tickDelta) {
        boolean povEditMode;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return PovDarknessHelper.sampleFromReplay(playback.replay(), playback.replayTick());
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
            return PovDarknessHelper.sampleFromReplay(replay, rTick);
        }
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null) {
            if (!((Boolean)clip.actions.get()).booleanValue()) {
                return null;
            }
            Replay replay = PovCameraClips.resolveReplay(film, clip);
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovDarknessHelper.sampleFromReplay(replay, rTick);
        }
        if (povMode == 0 || povMode == 3) {
            Replay replay = film.getFirstPersonReplay();
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovDarknessHelper.sampleFromReplay(replay, rTick);
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
            if (!(clip instanceof ScreenEffectPovActionClip) || !(se = (ScreenEffectPovActionClip)clip).isActive(replayTick) || !se.hasEffect("darkness")) continue;
            float elapsed = se.getLocalTick(replayTick);
            float opacity = se.darknessOpacity.isEmpty() ? 1.0f : ((Float)se.darknessOpacity.interpolate(elapsed)).floatValue();
            float radius = se.darknessRadius.isEmpty() ? 15.0f : ((Float)se.darknessRadius.interpolate(elapsed)).floatValue();
            float clampedOpacity = MathHelper.clamp((float)opacity, (float)0.0f, (float)1.0f);
            float pulse = 0.2f + 0.2f * (float)Math.sin((double)elapsed * 0.05235987755982988);
            float lightFactor = MathHelper.clamp((float)(clampedOpacity * pulse), (float)0.0f, (float)0.4f);
            float fogFactor = clampedOpacity;
            return new Sample(fogFactor, lightFactor, Math.max(1.0f, radius));
        }
        return null;
    }

    private record Sample(float fogFactor, float lightFactor, float radius) {
    }
}

