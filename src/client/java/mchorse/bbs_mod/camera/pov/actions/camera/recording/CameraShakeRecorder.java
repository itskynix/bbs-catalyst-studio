/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.network.ClientPlayerEntity
 */
package mchorse.bbs_mod.camera.pov.actions.camera.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.CameraShakePovActionClip;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.List;
import java.util.Objects;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.network.ClientPlayerEntity;

public final class CameraShakeRecorder {
    private CameraShakePovActionClip recordingClip;
    private int previousHurtTime;
    private int previousDeathTime;

    public void reset() {
        this.recordingClip = null;
        this.previousHurtTime = 0;
        this.previousDeathTime = 0;
    }

    public void finish(ReplayKeyframesPovAccess access, int tick) {
        this.finalizeClip(tick);
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder, ClientPlayerEntity player) {
        boolean inPulse;
        if (!PovSettings.isBakeCameraShake() || player == null) {
            return;
        }
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        int hurtTime = player.hurtTime;
        int maxHurtTime = Math.max(1, player.maxHurtTime);
        float damageTiltYaw = player.getDamageTiltYaw();
        int deathTime = player.deathTime;
        boolean bl = inPulse = hurtTime > 0 || deathTime > 0;
        if (this.recordingClip == null) {
            boolean deathRising;
            boolean hurtRising = hurtTime > 0 && this.previousHurtTime <= 0;
            boolean bl2 = deathRising = deathTime > 0 && this.previousDeathTime <= 0;
            if (hurtRising || deathRising) {
                this.openClip(access, recorder.tick, hurtTime, maxHurtTime, damageTiltYaw, deathTime);
            }
        } else {
            int localTick = Math.max(0, recorder.tick - (Integer)this.recordingClip.tick.get());
            this.recordingClip.duration.set(Math.max(1, localTick + 1));
            this.recordValue(this.recordingClip.active, inPulse, localTick);
            this.recordValue(this.recordingClip.hurtTime, hurtTime, localTick);
            this.recordValue(this.recordingClip.maxHurtTime, maxHurtTime, localTick);
            this.recordValue(this.recordingClip.damageTiltYaw, Float.valueOf(damageTiltYaw), localTick);
            this.recordValue(this.recordingClip.deathTime, deathTime, localTick);
            if (!inPulse) {
                this.finalizeClip(recorder.tick);
            }
        }
        this.previousHurtTime = hurtTime;
        this.previousDeathTime = deathTime;
    }

    private void openClip(ReplayKeyframesPovAccess access, int tick, int hurtTime, int maxHurtTime, float damageTiltYaw, int deathTime) {
        this.recordingClip = (CameraShakePovActionClip)access.bbsPov$getActions().add(PovActionType.CAMERA_SHAKE, tick, 1);
        this.recordValue(this.recordingClip.active, true, 0.0f);
        this.recordValue(this.recordingClip.hurtTime, hurtTime, 0.0f);
        this.recordValue(this.recordingClip.maxHurtTime, maxHurtTime, 0.0f);
        this.recordValue(this.recordingClip.damageTiltYaw, Float.valueOf(damageTiltYaw), 0.0f);
        this.recordValue(this.recordingClip.deathTime, deathTime, 0.0f);
    }

    private void finalizeClip(int tick) {
        if (this.recordingClip == null) {
            return;
        }
        int localTick = Math.max(0, tick - (Integer)this.recordingClip.tick.get());
        this.recordingClip.duration.set(Math.max(1, localTick + 1));
        this.recordValue(this.recordingClip.active, false, localTick);
        this.recordingClip.ensureBakingBounds();
        this.recordingClip = null;
    }

    private <T> void recordValue(KeyframeChannel<T> channel, T value, float tick) {
        if (channel == null) {
            return;
        }
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
            return;
        }
        List<Keyframe<T>> keyframes = channel.getKeyframes();
        Keyframe<T> previousKey = keyframes.get(keyframes.size() - 1);
        T previous = previousKey.getValue();
        if (!Objects.equals(previous, value)) {
            if (tick - previousKey.getTick() > 1.0f) {
                channel.insert(tick - 1.0f, previous);
            }
            channel.insert(tick, value);
        }
    }
}

