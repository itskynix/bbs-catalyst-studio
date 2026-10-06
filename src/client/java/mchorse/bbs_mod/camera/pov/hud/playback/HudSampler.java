/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.hud.playback;

import mchorse.bbs_mod.camera.pov.hud.HudState;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import java.util.Iterator;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

public final class HudSampler {
    private static final float MAX_HEALTH_CONTAINER = 1200.0f;
    private static final int ABSORPTION_FLASH_WINDOW_TICKS = 10;
    private static final int HEALTH_STABLE_TICKS = 20;

    private HudSampler() {
    }

    public static HudState sample(RecordedHudData data, ReplayKeyframes replay, float tick) {
        if (data == null) {
            return null;
        }
        HudState state = new HudState();
        state.visible = (Boolean)data.visible.interpolate(tick, true);
        state.statusBarsVisible = (Boolean)data.statusBarsVisible.interpolate(tick, true);
        state.crosshair = (Boolean)data.crosshair.interpolate(tick, true);
        state.selectedSlot = HudSampler.clamp(replay.getSelectedSlot(tick), 0, 8);
        for (int i = 0; i < 9; ++i) {
            state.items[i] = HudSampler.copyItem((ItemStack)((KeyframeChannel)replay.hotbar.get(i)).interpolate(tick, ItemStack.EMPTY));
        }
        state.offhandItem = HudSampler.copyItem((ItemStack)replay.offHand.interpolate(tick, ItemStack.EMPTY));
        state.healthContainer = HudSampler.clamp((float)((Integer)data.healthContainer.interpolate(tick, 20)).intValue(), 0.0f, 1200.0f);
        state.health = HudSampler.clamp((float)((Integer)data.health.interpolate(tick, 20)).intValue(), 0.0f, state.healthContainer);
        state.lastHealth = HudSampler.sampleHealth(data, tick - 1.0f, state.health);
        float[] recentHealth = HudSampler.recentRange(data.health, data.healthContainer, tick, state.health, 20);
        state.recentHealthLow = recentHealth[0];
        state.recentHealthHigh = recentHealth[1];
        state.previousHealth = data.previousHealth.isEmpty() ? HudSampler.clamp(state.recentHealthHigh, 0.0f, state.healthContainer) : HudSampler.clamp((float)((Integer)data.previousHealth.interpolate(tick, Math.round(state.recentHealthHigh))).intValue(), 0.0f, state.healthContainer);
        state.absorptionContainer = HudSampler.clamp((float)((Integer)data.absorptionContainer.interpolate(tick, 0)).intValue(), 0.0f, 1200.0f);
        state.absorption = HudSampler.clamp((float)((Integer)data.absorption.interpolate(tick, 0)).intValue(), 0.0f, state.absorptionContainer);
        float[] recentAbsorption = HudSampler.recentRange(data.absorption, data.absorptionContainer, tick, state.absorption, 10);
        state.recentAbsorptionLow = recentAbsorption[0];
        state.recentAbsorptionHigh = recentAbsorption[1];
        state.heartType = HudSampler.clamp((Integer)data.heartType.interpolate(tick, 0), 0, 4);
        state.hardcore = (Boolean)data.hardcore.interpolate(tick, false);
        state.heartRegeneration = (Boolean)data.regeneration.interpolate(tick, false);
        state.armor = HudSampler.clamp((float)((Integer)data.armor.interpolate(tick, 0)).intValue(), 0.0f, 20.0f);
        state.hunger = HudSampler.clamp((float)((Integer)data.hunger.interpolate(tick, 20)).intValue(), 0.0f, 20.0f);
        state.hungerEffect = (Boolean)data.hungerEffect.interpolate(tick, false);
        state.mountHealthContainer = HudSampler.clamp((float)((Integer)data.mountHealthContainer.interpolate(tick, 0)).intValue(), 0.0f, 60.0f);
        state.mountHealth = state.mountHealthContainer <= 0.0f ? 0.0f : HudSampler.clamp((float)((Integer)data.mountHealth.interpolate(tick, 0)).intValue(), 0.0f, state.mountHealthContainer);
        state.air = HudSampler.clamp((float)((Integer)data.air.interpolate(tick, 300)).intValue(), 0.0f, 300.0f);
        state.experience = HudSampler.clamp(((Double)data.experience.interpolate(tick, 0.0)).floatValue(), 0.0f, 1.0f);
        state.experienceLevel = HudSampler.clamp((Integer)data.experienceLevel.interpolate(tick, 0), 0, 9999);
        state.heartFlash = (Boolean)data.heartFlash.interpolate(tick, false);
        state.absorptionFlash = (Boolean)data.absorptionFlash.interpolate(tick, false);
        state.healthFlashAge = state.heartFlash ? HudSampler.getTrueRunAge(data.heartFlash, tick) : 0.0f;
        Transform transform = (Transform)data.layout.interpolate(tick, new Transform());
        state.layout.copy(transform);
        state.slotsLayout.copy((Transform)data.slotsLayout.interpolate(tick, new Transform()));
        state.heartsLayout.copy((Transform)data.heartsLayout.interpolate(tick, new Transform()));
        state.foodLayout.copy((Transform)data.foodLayout.interpolate(tick, new Transform()));
        state.expLayout.copy((Transform)data.expLayout.interpolate(tick, new Transform()));
        state.cursorVisible = (Boolean)data.cursorVisible.interpolate(tick, false);
        state.cursorLayout.copy((Transform)data.cursorLayout.interpolate(tick, new Transform()));
        state.cursorItem = HudSampler.copyItem((ItemStack)data.cursorItem.interpolate(tick, ItemStack.EMPTY));
        state.alpha = 1.0f;
        return state;
    }

    private static float sampleHealth(RecordedHudData data, float tick, float fallback) {
        float container = HudSampler.clamp((float)((Integer)data.healthContainer.interpolate(tick, 20)).intValue(), 0.0f, 1200.0f);
        return HudSampler.clamp((float)((Integer)data.health.interpolate(tick, Math.round(fallback))).intValue(), 0.0f, container);
    }

    private static float[] recentRange(KeyframeChannel<Integer> value, KeyframeChannel<Integer> container, float tick, float current, int windowTicks) {
        float low = current;
        float high = current;
        for (int i = 1; i <= windowTicks; ++i) {
            float maximum = HudSampler.clamp((float)((Integer)container.interpolate(tick - (float)i, 0)).intValue(), 0.0f, 1200.0f);
            float sampled = HudSampler.clamp((float)((Integer)value.interpolate(tick - (float)i, 0)).intValue(), 0.0f, maximum);
            low = Math.min(low, sampled);
            high = Math.max(high, sampled);
        }
        return new float[]{low, high};
    }

    private static float getTrueRunAge(KeyframeChannel<Boolean> channel, float tick) {
        Keyframe keyframe;
        boolean previous = false;
        float start = tick;
        Iterator iterator = channel.getKeyframes().iterator();
        while (iterator.hasNext() && !((keyframe = (Keyframe)iterator.next()).getTick() > tick)) {
            boolean value = Boolean.TRUE.equals(keyframe.getValue());
            if (value && !previous) {
                start = keyframe.getTick();
            }
            previous = value;
        }
        return previous ? Math.max(0.0f, tick - start) : 0.0f;
    }

    private static ItemStack copyItem(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

