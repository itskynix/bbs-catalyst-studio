/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 *  mchorse.bbs_mod.utils.pose.Transform
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import java.util.List;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;

public final class MenuPovActionClip
extends PovActionClip {
    public final KeyframeChannel<String> state = this.channel("state", KeyframeFactories.STRING);
    public final KeyframeChannel<Transform> cursorLayout = this.channel("cursor_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Boolean> cursorVisible = this.channel("cursor_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<String> deathMessage = this.channel("death_message", KeyframeFactories.STRING);
    public final KeyframeChannel<String> score = this.channel("score", KeyframeFactories.STRING);
    public final KeyframeChannel<Float> bgOpacity = this.channel("bg_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> opacity = this.channel("opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> buttonsActive = this.channel("buttons_active", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> leaveBed = this.channel("leave_bed", KeyframeFactories.BOOLEAN);

    @Override
    public PovActionType getActionType() {
        return PovActionType.MENU;
    }

    public String resolveType() {
        return this.state.isEmpty() ? "game_menu" : (String)this.state.get(0).getValue();
    }

    @Override
    public void normalize() {
        super.normalize();
        MenuPovActionClip.clamp(this.bgOpacity, 0.0f, 1.0f);
        MenuPovActionClip.clamp(this.opacity, 0.0f, 1.0f);
    }

    public void ensureBakingBounds() {
        float end = ((Integer)this.duration.get()).intValue();
        MenuPovActionClip.padChannel(this.state, end);
        MenuPovActionClip.padChannel(this.cursorLayout, end);
        MenuPovActionClip.padChannel(this.cursorVisible, end);
        MenuPovActionClip.padChannel(this.deathMessage, end);
        MenuPovActionClip.padChannel(this.score, end);
        MenuPovActionClip.padChannel(this.bgOpacity, end, true);
        MenuPovActionClip.padChannel(this.opacity, end, false);
        MenuPovActionClip.padChannel(this.buttonsActive, end, true);
        MenuPovActionClip.padChannel(this.leaveBed, end, true);
        MenuPovActionClip.linear(this.opacity);
    }

    private static <T> void padChannel(KeyframeChannel<T> channel, float end) {
        MenuPovActionClip.padChannel(channel, end, true);
    }

    private static <T> void padChannel(KeyframeChannel<T> channel, float end, boolean hold) {
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
        if (hold) {
            MenuPovActionClip.constant(channel);
        }
    }

    protected Clip create() {
        return new MenuPovActionClip();
    }
}

