/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.interps.Interpolations
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

public abstract class PovActionClip
extends Clip {
    private final List<KeyframeChannel<?>> channels = new ArrayList();

    public PovActionClip() {
        this.layer.set(this.getActionType().seedLayer());
    }

    public abstract PovActionType getActionType();

    public float getLocalTick(float globalTick) {
        return Math.max(0.0f, globalTick - (float)((Integer)this.tick.get()).intValue());
    }

    public boolean isActive(float globalTick) {
        if (!((Boolean)this.enabled.get()).booleanValue()) {
            return false;
        }
        int start = (Integer)this.tick.get();
        int end = start + Math.max(0, (Integer)this.duration.get());
        return globalTick >= (float)start && globalTick <= (float)end;
    }

    protected final <T> KeyframeChannel<T> channel(String id, IKeyframeFactory<T> factory) {
        KeyframeChannel channel = new KeyframeChannel(id, factory);
        this.channels.add(channel);
        this.add((BaseValue)channel);
        return channel;
    }

    public final List<KeyframeChannel<?>> getChannels() {
        return Collections.unmodifiableList(this.channels);
    }

    public static void constant(KeyframeChannel<?> channel) {
        if (channel == null) {
            return;
        }
        for (Keyframe keyframe : channel.getKeyframes()) {
            keyframe.getInterpolation().setInterp(Interpolations.CONST);
        }
    }

    public static void linear(KeyframeChannel<?> channel) {
        if (channel == null) {
            return;
        }
        for (Keyframe keyframe : channel.getKeyframes()) {
            keyframe.getInterpolation().setInterp(Interpolations.LINEAR);
        }
    }

    public static void clamp(KeyframeChannel<Float> channel, float minimum, float maximum) {
        for (Keyframe keyframe : channel.getKeyframes()) {
            keyframe.setValue(Float.valueOf(Math.max(minimum, Math.min(maximum, ((Float)keyframe.getValue()).floatValue()))));
        }
    }

    public static void clamp(KeyframeChannel<Double> channel, double minimum, double maximum) {
        for (Keyframe keyframe : channel.getKeyframes()) {
            keyframe.setValue(Math.max(minimum, Math.min(maximum, (Double)keyframe.getValue())));
        }
    }

    public static void clamp(KeyframeChannel<Integer> channel, int minimum, int maximum) {
        for (Keyframe keyframe : channel.getKeyframes()) {
            keyframe.setValue(Math.max(minimum, Math.min(maximum, (Integer)keyframe.getValue())));
        }
    }

    public void normalize() {
        String currentTitle = (String)this.title.get();
        if (currentTitle.equals(this.getActionType().title) || currentTitle.equals("GUI") || currentTitle.equals("Menu")) {
            this.title.set("");
        }
        if ((Integer)this.duration.get() < 1) {
            this.duration.set(1);
        }
    }
}

