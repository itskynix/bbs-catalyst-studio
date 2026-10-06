/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.option.SimpleOption
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.RotationAxis
 */
package mchorse.bbs_mod.camera.pov.actions.camera;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.CameraShakePovActionClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class CameraShakeApplier {
    private static Sample cachedSample;
    private static float cachedTickDelta;

    private CameraShakeApplier() {
    }

    public static boolean shouldApply(float tickDelta) {
        return CameraShakeApplier.resolve(tickDelta) != null;
    }

    public static boolean shouldCancelVanilla(float tickDelta) {
        cachedSample = CameraShakeApplier.resolve(tickDelta);
        cachedTickDelta = tickDelta;
        return PovPlaybackContext.getActive(tickDelta) != null || cachedSample != null;
    }

    public static void apply(MatrixStack matrices, float tickDelta) {
        Sample sample = tickDelta == cachedTickDelta ? cachedSample : CameraShakeApplier.resolve(tickDelta);
        cachedSample = null;
        cachedTickDelta = Float.NaN;
        if (sample == null) {
            return;
        }
        CameraShakeApplier.applySample(matrices, sample, CameraShakeApplier.frozenDelta(tickDelta));
    }

    private static float frozenDelta(float tickDelta) {
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return playback.controller().paused ? 0.0f : tickDelta;
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (!(panel == null || panel.getRunner() != null && panel.getRunner().isRunning())) {
            return 0.0f;
        }
        return tickDelta;
    }

    private static Sample resolve(float tickDelta) {
        boolean povEditMode;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback != null) {
            return CameraShakeApplier.sampleFromReplay(playback.replay(), playback.replayTick());
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
            if (!PovReplaySettings.isCameraShakeEnabled(replay)) {
                return null;
            }
            return CameraShakeApplier.sampleFromReplay(replay, CameraShakeApplier.replayTick(replay, cursor, transition));
        }
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null) {
            if (!((Boolean)clip.cameraShake.get()).booleanValue()) {
                return null;
            }
            Replay replay = PovCameraClips.resolveReplay(film, clip);
            return CameraShakeApplier.sampleFromReplay(replay, CameraShakeApplier.replayTick(replay, cursor, transition));
        }
        if (povMode == 0 || povMode == 3) {
            Replay replay = film.getFirstPersonReplay();
            return CameraShakeApplier.sampleFromReplay(replay, CameraShakeApplier.replayTick(replay, cursor, transition));
        }
        return null;
    }

    private static float replayTick(Replay replay, int cursor, float transition) {
        if (replay == null) {
            return (float)cursor + transition;
        }
        return (float)replay.getTick(cursor) + transition;
    }

    private static Sample sampleFromReplay(Replay replay, float replayTick) {
        ReplayKeyframes replayKeyframes;
        if (replay == null || !PovReplaySettings.isCameraShakeEnabled(replay) || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
            return null;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedPovActions actions = access.bbsPov$getActions();
        if (actions == null) {
            return null;
        }
        CameraShakePovActionClip shake = actions.getActiveCameraShake(replayTick);
        if (shake == null) {
            return null;
        }
        float local = shake.getLocalTick(replayTick);
        boolean active = Boolean.TRUE.equals(shake.active.interpolate(local, false));
        if (!active) {
            return null;
        }
        int hurtTime = shake.hurtTime.interpolate(local, 0);
        int maxHurtTime = Math.max(1, shake.maxHurtTime.interpolate(local, 10));
        float yaw = shake.damageTiltYaw.interpolate(local, 0.0f);
        int deathTime = shake.deathTime.interpolate(local, 0);
        return new Sample(hurtTime, maxHurtTime, yaw, deathTime);
    }

    private static void applySample(MatrixStack matrices, Sample sample, float tickDelta) {
        float remaining;
        if (sample.deathTime > 0) {
            float death = Math.min((float)sample.deathTime + tickDelta, 20.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(40.0f - 8000.0f / (death + 200.0f)));
        }
        if ((remaining = (float)sample.hurtTime - tickDelta) < 0.0f) {
            return;
        }
        float t = remaining / (float)sample.maxHurtTime;
        t = MathHelper.sin((float)(t * t * (t * t) * (float)Math.PI));
        float strength = 1.0f;
        SimpleOption option = MinecraftClient.getInstance().options.getDamageTiltStrength();
        if (option != null) {
            strength = ((Double)option.getValue()).floatValue();
        }
        float roll = -t * 14.0f * strength;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-sample.damageTiltYaw));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sample.damageTiltYaw));
    }

    static {
        cachedTickDelta = Float.NaN;
    }

    private record Sample(int hurtTime, int maxHurtTime, float damageTiltYaw, int deathTime) {
    }
}

