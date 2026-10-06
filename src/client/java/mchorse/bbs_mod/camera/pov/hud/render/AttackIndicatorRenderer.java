/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.option.AttackIndicator
 *  net.minecraft.entity.EquipmentSlot
 *  net.minecraft.entity.attribute.EntityAttributeModifier
 *  net.minecraft.entity.attribute.EntityAttributes
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.hud.render;

import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import java.util.List;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public final class AttackIndicatorRenderer {
    private AttackIndicatorRenderer() {
    }

    public static boolean shouldDraw() {
        AttackIndicator mode = (AttackIndicator)MinecraftClient.getInstance().options.getAttackIndicator().getValue();
        return mode == AttackIndicator.CROSSHAIR;
    }

    public static float progress(ReplayKeyframes replay, RecordedHudData hud, RecordedHandData hand, float tick) {
        if (hud != null && !hud.attackCooldown.isEmpty()) {
            return AttackIndicatorRenderer.sampleCooldown(hud.attackCooldown, tick);
        }
        return AttackIndicatorRenderer.reconstruct(replay, hand, tick);
    }

    public static float sampleCooldown(KeyframeChannel<Float> channel, float tick) {
        if (channel == null || channel.isEmpty()) {
            return 1.0f;
        }
        List keyframes = channel.getKeyframes();
        int size = keyframes.size();
        if (tick < ((Keyframe)keyframes.get(0)).getTick()) {
            return 1.0f;
        }
        if (tick >= ((Keyframe)keyframes.get(size - 1)).getTick()) {
            return MathHelper.clamp((float)((Float)((Keyframe)keyframes.get(size - 1)).getValue()).floatValue(), (float)0.0f, (float)1.0f);
        }
        int prevIdx = 0;
        int i = 0;
        while (i < size && ((Keyframe)keyframes.get(i)).getTick() <= tick) {
            prevIdx = i++;
        }
        Keyframe prev = (Keyframe)keyframes.get(prevIdx);
        Keyframe next = (Keyframe)keyframes.get(prevIdx + 1);
        float v0 = ((Float)prev.getValue()).floatValue();
        float v1 = ((Float)next.getValue()).floatValue();
        if (v0 >= 0.999f && v1 < v0) {
            return 1.0f;
        }
        if (v1 < v0) {
            return 1.0f;
        }
        float t0 = prev.getTick();
        float t1 = next.getTick();
        if (t1 <= t0) {
            return MathHelper.clamp((float)v1, (float)0.0f, (float)1.0f);
        }
        float factor = (tick - t0) / (t1 - t0);
        float value = v0 + (v1 - v0) * factor;
        return MathHelper.clamp((float)value, (float)0.0f, (float)1.0f);
    }

    public static float liveProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 1.0f;
        }
        return MathHelper.clamp((float)client.player.getAttackCooldownProgress(0.0f), (float)0.0f, (float)1.0f);
    }

    private static float reconstruct(ReplayKeyframes replay, RecordedHandData hand, float tick) {
        if (replay == null || tick < 0.0f) {
            return 1.0f;
        }
        int slot = MathHelper.clamp((int)replay.getSelectedSlot(tick), (int)0, (int)8);
        ItemStack main = (ItemStack)((KeyframeChannel)replay.hotbar.get(slot)).interpolate(tick, ItemStack.EMPTY);
        float period = AttackIndicatorRenderer.cooldownPeriod(main);
        int lookback = Math.max(2, (int)Math.ceil(period) + 1);
        for (int i = 0; i <= lookback; ++i) {
            float thenTick = tick - (float)i;
            if (thenTick < 0.0f) {
                return 1.0f;
            }
            if (!AttackIndicatorRenderer.resetAt(replay, hand, thenTick)) continue;
            return MathHelper.clamp((float)((float)i / period), (float)0.0f, (float)1.0f);
        }
        return 1.0f;
    }

    private static boolean resetAt(ReplayKeyframes replay, RecordedHandData hand, float tick) {
        ItemStack previousOff;
        ItemStack previousMain;
        int previousSlot;
        float previousTick = tick - 1.0f;
        if (previousTick < 0.0f) {
            return false;
        }
        int slot = MathHelper.clamp((int)replay.getSelectedSlot(tick), (int)0, (int)8);
        if (slot != (previousSlot = MathHelper.clamp((int)replay.getSelectedSlot(previousTick), (int)0, (int)8))) {
            return true;
        }
        ItemStack main = (ItemStack)((KeyframeChannel)replay.hotbar.get(slot)).interpolate(tick, ItemStack.EMPTY);
        if (!ItemStack.areItemsEqual((ItemStack)main, (ItemStack)(previousMain = (ItemStack)((KeyframeChannel)replay.hotbar.get(previousSlot)).interpolate(previousTick, ItemStack.EMPTY)))) {
            return true;
        }
        ItemStack off = (ItemStack)replay.offHand.interpolate(tick, ItemStack.EMPTY);
        return !ItemStack.areItemsEqual((ItemStack)off, (ItemStack)(previousOff = (ItemStack)replay.offHand.interpolate(previousTick, ItemStack.EMPTY)));
    }

    private static float cooldownPeriod(ItemStack stack) {
        double base = 4.0;
        double addition = 0.0;
        double multiplyBase = 1.0;
        double multiplyTotal = 1.0;
        if (stack != null && !stack.isEmpty()) {
            for (EntityAttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
                switch (modifier.getOperation()) {
                    case ADDITION: {
                        addition += modifier.getValue();
                        break;
                    }
                    case MULTIPLY_BASE: {
                        multiplyBase += modifier.getValue();
                        break;
                    }
                    case MULTIPLY_TOTAL: {
                        multiplyTotal *= 1.0 + modifier.getValue();
                    }
                }
            }
        }
        double speed = Math.max(1.0E-4, (base + addition) * multiplyBase * multiplyTotal);
        return (float)(1.0 / speed * 20.0);
    }
}

