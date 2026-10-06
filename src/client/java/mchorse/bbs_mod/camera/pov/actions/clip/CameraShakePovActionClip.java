/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import java.util.List;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public final class CameraShakePovActionClip
extends PovActionClip {
    public final KeyframeChannel<Boolean> active = this.channel("active", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> hurtTime = this.channel("hurt_time", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> maxHurtTime = this.channel("max_hurt_time", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Float> damageTiltYaw = this.channel("damage_tilt_yaw", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Integer> deathTime = this.channel("death_time", KeyframeFactories.INTEGER);

    @Override
    public PovActionType getActionType() {
        return PovActionType.CAMERA_SHAKE;
    }

    @Override
    public void normalize() {
        super.normalize();
        CameraShakePovActionClip.clamp(this.hurtTime, 0, 200);
        CameraShakePovActionClip.clamp(this.maxHurtTime, 0, 200);
        CameraShakePovActionClip.clamp(this.deathTime, 0, 200);
    }

    public void ensureBakingBounds() {
        float end = ((Integer)this.duration.get()).intValue();
        CameraShakePovActionClip.padChannel(this.active, end);
        CameraShakePovActionClip.padChannel(this.hurtTime, end);
        CameraShakePovActionClip.padChannel(this.maxHurtTime, end);
        CameraShakePovActionClip.padChannel(this.damageTiltYaw, end);
        CameraShakePovActionClip.padChannel(this.deathTime, end);
    }

    private static <T> void padChannel(KeyframeChannel<T> channel, float end) {
        if (channel == null || channel.isEmpty() || end <= 0.0f) {
            return;
        }
        List<Keyframe<T>> keyframes = channel.getKeyframes();
        Keyframe<T> first = keyframes.get(0);
        Keyframe<T> last = keyframes.get(keyframes.size() - 1);
        if (first.getTick() > 0.0f) {
            channel.insert(0.0f, first.getValue());
        }
        if (last.getTick() < end) {
            channel.insert(end, last.getValue());
        }
        CameraShakePovActionClip.constant(channel);
    }

    protected Clip create() {
        return new CameraShakePovActionClip();
    }
}

