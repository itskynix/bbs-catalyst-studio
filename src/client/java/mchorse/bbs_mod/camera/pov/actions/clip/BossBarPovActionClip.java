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
import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarLooks;
import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import java.util.List;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public final class BossBarPovActionClip
extends PovActionClip {
    public final KeyframeChannel<String> state = this.channel("state", KeyframeFactories.STRING);
    public final KeyframeChannel<String> name = this.channel("name", KeyframeFactories.STRING);
    public final KeyframeChannel<Float> percent = this.channel("percent", KeyframeFactories.FLOAT);
    public final KeyframeChannel<String> color = this.channel("color", KeyframeFactories.STRING);
    public final KeyframeChannel<String> style = this.channel("style", KeyframeFactories.STRING);

    public BossBarPovActionClip() {
        this.ensureDefaults();
    }

    @Override
    public PovActionType getActionType() {
        return PovActionType.BOSS_BARS;
    }

    public String resolveType() {
        BossBarTypeEntry entry = BossBarTypeEntry.findById(this.state.isEmpty() ? "" : (String)this.state.get(0).getValue());
        return entry == null ? BossBarTypeEntry.DRAGON.id : entry.id;
    }

    public void applyTypeDefaults(BossBarTypeEntry type) {
        if (type == null) {
            type = BossBarTypeEntry.DRAGON;
        }
        BossBarPovActionClip.setConstant(this.state, type.id);
        BossBarPovActionClip.setConstant(this.name, type.defaultTitle);
        BossBarPovActionClip.setConstant(this.color, type.defaultColor);
        BossBarPovActionClip.setConstant(this.style, type.defaultStyle);
        if (this.percent.isEmpty()) {
            this.percent.insert(0.0f, Float.valueOf(1.0f));
        }
    }

    public void ensureDefaults() {
        if (this.state.isEmpty()) {
            this.applyTypeDefaults(BossBarTypeEntry.DRAGON);
        }
        if (this.name.isEmpty()) {
            this.name.insert(0.0f, BossBarTypeEntry.DRAGON.defaultTitle);
        }
        if (this.percent.isEmpty()) {
            this.percent.insert(0.0f, Float.valueOf(1.0f));
        }
        if (this.color.isEmpty()) {
            this.color.insert(0.0f, BossBarTypeEntry.DRAGON.defaultColor);
        }
        if (this.style.isEmpty()) {
            this.style.insert(0.0f, BossBarTypeEntry.DRAGON.defaultStyle);
        }
    }

    @Override
    public void normalize() {
        super.normalize();
        this.ensureDefaults();
        BossBarPovActionClip.clamp(this.percent, 0.0f, 1.0f);
        for (Keyframe keyframe : this.color.getKeyframes()) {
            keyframe.setValue(BossBarLooks.COLORS[BossBarLooks.colorIndex((String)keyframe.getValue())]);
        }
        for (Keyframe keyframe : this.style.getKeyframes()) {
            keyframe.setValue(BossBarLooks.STYLES[BossBarLooks.styleIndex((String)keyframe.getValue())]);
        }
    }

    public void ensureBakingBounds() {
        float end = ((Integer)this.duration.get()).intValue();
        BossBarPovActionClip.padChannel(this.state, end, true);
        BossBarPovActionClip.padChannel(this.name, end, true);
        BossBarPovActionClip.padChannel(this.percent, end, false);
        BossBarPovActionClip.padChannel(this.color, end, true);
        BossBarPovActionClip.padChannel(this.style, end, true);
        BossBarPovActionClip.linear(this.percent);
        BossBarPovActionClip.constant(this.state);
        BossBarPovActionClip.constant(this.name);
        BossBarPovActionClip.constant(this.color);
        BossBarPovActionClip.constant(this.style);
    }

    private static <T> void setConstant(KeyframeChannel<T> channel, T value) {
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
            return;
        }
        channel.get(0).setValue(value);
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
            BossBarPovActionClip.constant(channel);
        }
    }

    protected Clip create() {
        return new BossBarPovActionClip();
    }
}

