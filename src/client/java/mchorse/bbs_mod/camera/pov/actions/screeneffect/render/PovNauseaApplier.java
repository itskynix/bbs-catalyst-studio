/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.RotationAxis
 *  org.joml.Vector3f
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

public final class PovNauseaApplier {
    private PovNauseaApplier() {
    }

    public static void apply(MatrixStack matrixStack, float tickDelta, int ticks) {
        Sample sample = PovNauseaApplier.resolve(tickDelta, ticks);
        if (sample == null || sample.intensity <= 0.001f) {
            return;
        }
        float nauseaIntensity = sample.intensity;
        float scale = 5.0f / (nauseaIntensity * nauseaIntensity + 5.0f) - nauseaIntensity * 0.04f;
        scale *= scale;
        float angle = sample.time * (float)sample.speed;
        RotationAxis axis = RotationAxis.of((Vector3f)new Vector3f(0.0f, MathHelper.SQUARE_ROOT_OF_TWO / 2.0f, MathHelper.SQUARE_ROOT_OF_TWO / 2.0f));
        matrixStack.multiply(axis.rotationDegrees(angle));
        matrixStack.scale(1.0f / scale, 1.0f, 1.0f);
        matrixStack.multiply(axis.rotationDegrees(-angle));
    }

    private static Sample resolve(float tickDelta, int ticks) {
        boolean povEditMode;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return PovNauseaApplier.sampleFromReplay(playback.replay(), playback.replayTick(), playback.replayTick());
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
            return PovNauseaApplier.sampleFromReplay(replay, rTick, filmTick);
        }
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null) {
            if (!((Boolean)clip.actions.get()).booleanValue()) {
                return null;
            }
            Replay replay = PovCameraClips.resolveReplay(film, clip);
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovNauseaApplier.sampleFromReplay(replay, rTick, filmTick);
        }
        if (povMode == 0 || povMode == 3) {
            Replay replay = film.getFirstPersonReplay();
            float rTick = replay != null ? (float)replay.getTick(cursor) + transition : filmTick;
            return PovNauseaApplier.sampleFromReplay(replay, rTick, filmTick);
        }
        return null;
    }

    private static Sample sampleFromReplay(Replay replay, float replayTick, float time) {
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
            if (!(clip instanceof ScreenEffectPovActionClip) || !(se = (ScreenEffectPovActionClip)clip).isActive(replayTick)) continue;
            float elapsed = se.getLocalTick(replayTick);
            boolean hasNausea = se.hasEffect("nausea");
            boolean hasPortal = se.hasEffect("portal");
            if (!hasNausea && !hasPortal) continue;
            float intensity = 0.0f;
            if (hasNausea) {
                float distortion = se.nauseaDistortion.isEmpty() ? 1.0f : ((Float)se.nauseaDistortion.interpolate(elapsed)).floatValue();
                float opacity = se.nauseaOpacity.isEmpty() ? 1.0f : ((Float)se.nauseaOpacity.interpolate(elapsed)).floatValue();
                intensity = Math.max(intensity, distortion * opacity);
            }
            if (hasPortal) {
                float portalOp = se.portalOpacity.isEmpty() ? 1.0f : ((Float)se.portalOpacity.interpolate(elapsed)).floatValue();
                intensity = Math.max(intensity, portalOp);
            }
            if (!(intensity > 0.001f)) continue;
            int speed = hasPortal ? 20 : 7;
            return new Sample(intensity, time, speed);
        }
        return null;
    }

    private record Sample(float intensity, float time, int speed) {
    }
}

