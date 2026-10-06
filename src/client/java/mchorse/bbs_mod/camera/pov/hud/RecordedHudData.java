/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.core.ValueGroup
 *  mchorse.bbs_mod.settings.values.ui.ValueStringKeys
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.hud;

import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.hud.recording.HudRecorder;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

public final class RecordedHudData {
    public final KeyframeChannel<Integer> health = RecordedHudData.channel("health", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> previousHealth = RecordedHudData.channel("previous_health", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> healthContainer = RecordedHudData.channel("health_container", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> absorption = RecordedHudData.channel("absorption", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> absorptionContainer = RecordedHudData.channel("absorption_container", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> heartType = RecordedHudData.channel("heart_type", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Boolean> hardcore = RecordedHudData.channel("hardcore", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> regeneration = RecordedHudData.channel("regeneration", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> armor = RecordedHudData.channel("armor", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> hunger = RecordedHudData.channel("hunger", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Boolean> hungerEffect = RecordedHudData.channel("hunger_effect", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> mountHealth = RecordedHudData.channel("mount_health", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> mountHealthContainer = RecordedHudData.channel("mount_health_container", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> air = RecordedHudData.channel("air", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Double> experience = RecordedHudData.channel("experience", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Integer> experienceLevel = RecordedHudData.channel("experience_level", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Boolean> heartFlash = RecordedHudData.channel("heart_flash", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> attackCooldown = RecordedHudData.channel("attack_cooldown", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> inventoryAnchor = RecordedHudData.channel("inventory_slots", KeyframeFactories.BOOLEAN);
    public final List<KeyframeChannel<ItemStack>> inventory = new ArrayList<KeyframeChannel<ItemStack>>();
    public final KeyframeChannel<Boolean> absorptionFlash = RecordedHudData.channel("absorption_flash", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Transform> layout = RecordedHudData.channel("layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Transform> slotsLayout = RecordedHudData.channel("slots_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Transform> heartsLayout = RecordedHudData.channel("hearts_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Transform> foodLayout = RecordedHudData.channel("food_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Transform> expLayout = RecordedHudData.channel("exp_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Boolean> visible = RecordedHudData.channel("visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> statusBarsVisible = RecordedHudData.channel("status_bars_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> crosshair = RecordedHudData.channel("crosshair", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Transform> cursorLayout = RecordedHudData.channel("cursor_layout", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Boolean> cursorVisible = RecordedHudData.channel("cursor_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<ItemStack> cursorItem = RecordedHudData.channel("cursor_item", KeyframeFactories.ITEM_STACK);
    public final ValueStringKeys disabledTracks = new ValueStringKeys("pov_disabled_tracks");
    public final HudRecorder recording = new HudRecorder();

    public RecordedHudData() {
        for (int i = 0; i < 27; ++i) {
            this.inventory.add(RecordedHudData.channel("inv_" + i, KeyframeFactories.ITEM_STACK));
        }
    }

    private static <T> KeyframeChannel<T> channel(String id, IKeyframeFactory<T> factory) {
        return new KeyframeChannel("hotbar_" + id, factory);
    }

    public void initializeReplayPreset(ReplayKeyframes replay) {
        this.insertBakedHudPreset(replay);
    }

    public void ensureNativeSlotDefaults(ReplayKeyframes replay) {
        for (KeyframeChannel keyframeChannel : replay.hotbar) {
            if (!keyframeChannel.isEmpty()) continue;
            keyframeChannel.insert(0.0f, ItemStack.EMPTY);
        }
        for (KeyframeChannel keyframeChannel : this.inventory) {
            if (!keyframeChannel.isEmpty()) continue;
            keyframeChannel.insert(0.0f, ItemStack.EMPTY);
        }
        if (this.inventoryAnchor.isEmpty()) {
            this.inventoryAnchor.insert(0.0f, true);
        }
        if (replay.selectedSlot.isEmpty()) {
            replay.selectedSlot.insert(0.0f, 0);
        }
        if (PovSettings.isBakeActions()) {
            if (this.cursorVisible.isEmpty()) {
                this.cursorVisible.insert(0.0f, false);
            }
            if (this.cursorLayout.isEmpty()) {
                this.cursorLayout.insert(0.0f, new Transform());
            }
            if (this.cursorItem.isEmpty()) {
                this.cursorItem.insert(0.0f, ItemStack.EMPTY);
            }
        }
    }

    public void ensureStartRecordingBounds(float startTick, ReplayKeyframes targetReplay) {
        ReplayKeyframesPovAccess targetAccess;
        RecordedHudData targetHud;
        if (!PovSettings.isBakeActions()) {
            return;
        }
        float tick = Math.max(0.0f, startTick);
        Transform initialTransform = new Transform();
        if (targetReplay instanceof ReplayKeyframesPovAccess && (targetHud = (targetAccess = (ReplayKeyframesPovAccess)targetReplay).bbsPov$getHud()) != null && !targetHud.cursorLayout.isEmpty()) {
            initialTransform = ((Transform)targetHud.cursorLayout.interpolate(tick, new Transform())).copy();
        }
        this.cursorVisible.insert(tick, false);
        PovActionClip.constant(this.cursorVisible);
        this.cursorLayout.insert(tick, initialTransform);
        this.cursorItem.insert(tick, ItemStack.EMPTY);
    }

    public void ensureEndRecordingBounds(float endTick) {
        List kfs;
        if (endTick <= 0.0f || !PovSettings.isBakeActions()) {
            return;
        }
        if (!this.cursorVisible.isEmpty()) {
            kfs = this.cursorVisible.getKeyframes();
            if (((Keyframe)kfs.get(kfs.size() - 1)).getTick() < endTick) {
                this.cursorVisible.insert(endTick, false);
            }
        } else {
            this.cursorVisible.insert(endTick, false);
        }
        PovActionClip.constant(this.cursorVisible);
        if (!this.cursorLayout.isEmpty()) {
            kfs = this.cursorLayout.getKeyframes();
            Keyframe last = (Keyframe)kfs.get(kfs.size() - 1);
            if (last.getTick() < endTick) {
                this.cursorLayout.insert(endTick, ((Transform)last.getValue()).copy());
            }
        } else {
            this.cursorLayout.insert(endTick, new Transform());
        }
        if (!this.cursorItem.isEmpty()) {
            kfs = this.cursorItem.getKeyframes();
            if (((Keyframe)kfs.get(kfs.size() - 1)).getTick() < endTick) {
                this.cursorItem.insert(endTick, ItemStack.EMPTY);
            }
        } else {
            this.cursorItem.insert(endTick, ItemStack.EMPTY);
        }
    }

    public void trimCursorForRecordingRange(float startTick, float endTick) {
        RecordedHudData.trimChannelRange(this.cursorLayout, startTick, endTick);
        RecordedHudData.trimChannelRange(this.cursorVisible, startTick, endTick);
        RecordedHudData.trimChannelRange(this.cursorItem, startTick, endTick);
    }

    public void trimCursorForRecordingAt(float tick) {
        this.trimCursorForRecordingRange(tick, Float.MAX_VALUE);
    }

    private static <T> void trimChannelRange(KeyframeChannel<T> channel, float startTick, float endTick) {
        List keyframes = channel.getKeyframes();
        for (int i = keyframes.size() - 1; i >= 0; --i) {
            float tick = ((Keyframe)keyframes.get(i)).getTick();
            if (!(tick >= startTick - 1.0E-4f) || !(tick <= endTick + 1.0E-4f)) continue;
            channel.remove(i);
        }
    }

    private void insertBakedHudPreset(ReplayKeyframes replay) {
        this.recording.reset();
        this.ensureNativeSlotDefaults(replay);
        for (KeyframeChannel<ItemStack> slot : this.inventory) {
            slot.removeAll();
            slot.insert(0.0f, ItemStack.EMPTY);
        }
        this.inventoryAnchor.removeAll();
        this.inventoryAnchor.insert(0.0f, true);
        RecordedHudData.reset(this.health, 20);
        RecordedHudData.reset(this.previousHealth, 20);
        RecordedHudData.reset(this.healthContainer, 20);
        RecordedHudData.reset(this.absorption, 0);
        RecordedHudData.reset(this.absorptionContainer, 0);
        RecordedHudData.reset(this.heartType, 0);
        RecordedHudData.reset(this.hardcore, false);
        RecordedHudData.reset(this.regeneration, false);
        RecordedHudData.reset(this.armor, 0);
        RecordedHudData.reset(this.hunger, 20);
        RecordedHudData.reset(this.hungerEffect, false);
        RecordedHudData.reset(this.mountHealth, 0);
        RecordedHudData.reset(this.mountHealthContainer, 0);
        RecordedHudData.reset(this.air, 300);
        RecordedHudData.reset(this.experience, 0.0);
        RecordedHudData.reset(this.experienceLevel, 0);
        RecordedHudData.reset(this.heartFlash, false);
        RecordedHudData.reset(this.absorptionFlash, false);
        RecordedHudData.reset(this.statusBarsVisible, true);
    }

    public void addTo(ValueGroup group) {
        group.add((BaseValue)this.disabledTracks);
        group.add(this.health);
        group.add(this.previousHealth);
        group.add(this.healthContainer);
        group.add(this.absorption);
        group.add(this.absorptionContainer);
        group.add(this.heartType);
        group.add(this.hardcore);
        group.add(this.regeneration);
        group.add(this.armor);
        group.add(this.hunger);
        group.add(this.hungerEffect);
        group.add(this.mountHealth);
        group.add(this.mountHealthContainer);
        group.add(this.air);
        group.add(this.experience);
        group.add(this.experienceLevel);
        group.add(this.heartFlash);
        group.add(this.attackCooldown);
        group.add(this.absorptionFlash);
        group.add(this.layout);
        group.add(this.slotsLayout);
        group.add(this.heartsLayout);
        group.add(this.foodLayout);
        group.add(this.expLayout);
        group.add(this.visible);
        group.add(this.statusBarsVisible);
        group.add(this.crosshair);
        group.add(this.cursorLayout);
        group.add(this.cursorVisible);
        group.add(this.cursorItem);
        group.add(this.inventoryAnchor);
        for (KeyframeChannel<ItemStack> slot : this.inventory) {
            group.add(slot);
        }
    }

    public boolean hasRecordedData() {
        return !this.health.isEmpty() || !this.crosshair.isEmpty() || !this.cursorVisible.isEmpty() || !this.cursorLayout.isEmpty() || !this.cursorItem.isEmpty();
    }

    public int getDuration(ReplayKeyframes replay) {
        float last = Math.max(RecordedHudData.lastTick(replay.selectedSlot), RecordedHudData.lastTick(replay.offHand));
        for (KeyframeChannel keyframeChannel : replay.hotbar) {
            last = Math.max(last, RecordedHudData.lastTick(keyframeChannel));
        }
        for (KeyframeChannel keyframeChannel : this.inventory) {
            last = Math.max(last, RecordedHudData.lastTick(keyframeChannel));
        }
        for (KeyframeChannel<?> channel : this.getOwnedChannels()) {
            last = Math.max(last, RecordedHudData.lastTick(channel));
        }
        return Math.max(1, (int)Math.ceil(last) + 1);
    }

    public void addRecordingEndKeyframes(ReplayKeyframes replay, int endTick) {
        int tick = Math.max(0, endTick);
        RecordedHudData.addEndKeyframe(this.health, tick, 20);
        RecordedHudData.addEndKeyframe(this.previousHealth, tick, 20);
        RecordedHudData.addEndKeyframe(this.healthContainer, tick, 20);
        RecordedHudData.addEndKeyframe(this.absorption, tick, 0);
        RecordedHudData.addEndKeyframe(this.absorptionContainer, tick, 0);
        RecordedHudData.addEndKeyframe(this.heartType, tick, 0);
        RecordedHudData.addEndKeyframe(this.hardcore, tick, false);
        RecordedHudData.addEndKeyframe(this.regeneration, tick, false);
        RecordedHudData.addEndKeyframe(this.armor, tick, 0);
        RecordedHudData.addEndKeyframe(this.hunger, tick, 20);
        RecordedHudData.addEndKeyframe(this.hungerEffect, tick, false);
        RecordedHudData.addEndKeyframe(this.mountHealth, tick, 0);
        RecordedHudData.addEndKeyframe(this.mountHealthContainer, tick, 0);
        RecordedHudData.addEndKeyframe(this.air, tick, 300);
        RecordedHudData.addEndKeyframe(this.experience, tick, 0.0);
        RecordedHudData.addEndKeyframe(this.experienceLevel, tick, 0);
        RecordedHudData.addEndKeyframe(this.heartFlash, tick, false);
        RecordedHudData.addEndKeyframe(this.attackCooldown, tick, Float.valueOf(1.0f));
        RecordedHudData.addEndKeyframe(this.absorptionFlash, tick, false);
        RecordedHudData.addEndKeyframe(this.statusBarsVisible, tick, true);
        RecordedHudData.addEndKeyframe(this.inventoryAnchor, tick, true);
        for (KeyframeChannel<ItemStack> slot : this.inventory) {
            RecordedHudData.addEndKeyframe(slot, tick, ItemStack.EMPTY);
        }
    }

    public void addNativeItemBoundaryKeyframes(ReplayKeyframes replay, int startTick, int endTick) {
        int start = Math.max(0, startTick);
        int end = Math.max(start, endTick);
        for (KeyframeChannel keyframeChannel : replay.hotbar) {
            RecordedHudData.addBoundaryKeyframes((KeyframeChannel<ItemStack>)keyframeChannel, start, end);
        }
        for (KeyframeChannel keyframeChannel : this.inventory) {
            RecordedHudData.addBoundaryKeyframes((KeyframeChannel<ItemStack>)keyframeChannel, start, end);
        }
        RecordedHudData.addBoundaryKeyframes(this.inventoryAnchor, start, end, true);
        RecordedHudData.addBoundaryKeyframes((KeyframeChannel<ItemStack>)replay.offHand, start, end);
        RecordedHudData.addBoundaryKeyframes(replay.selectedSlot, start, end, 0);
    }

    public KeyframeChannel<?>[] getOwnedChannels() {
        ArrayList<Object> list = new ArrayList<Object>();
        list.add(this.layout);
        list.add(this.slotsLayout);
        list.add(this.heartsLayout);
        list.add(this.foodLayout);
        list.add(this.expLayout);
        list.add(this.visible);
        list.add(this.statusBarsVisible);
        list.add(this.crosshair);
        list.add(this.health);
        list.add(this.previousHealth);
        list.add(this.healthContainer);
        list.add(this.absorption);
        list.add(this.absorptionContainer);
        list.add(this.heartType);
        list.add(this.hardcore);
        list.add(this.regeneration);
        list.add(this.armor);
        list.add(this.hunger);
        list.add(this.hungerEffect);
        list.add(this.mountHealth);
        list.add(this.mountHealthContainer);
        list.add(this.air);
        list.add(this.experience);
        list.add(this.experienceLevel);
        list.add(this.heartFlash);
        list.add(this.attackCooldown);
        list.add(this.absorptionFlash);
        list.add(this.inventoryAnchor);
        list.addAll(this.inventory);
        return list.toArray(new KeyframeChannel[0]);
    }

    private static ItemStack copyItem(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static <T> void addEndKeyframe(KeyframeChannel<T> channel, int tick, T fallback) {
        T value = channel.interpolate((float)tick, fallback);
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack)value;
            ItemStack copied = stack.copy();
            value = (T)copied;
        }
        channel.insert((float)tick, value);
    }

    private static void addBoundaryKeyframes(KeyframeChannel<ItemStack> channel, int start, int end) {
        ItemStack startValue = RecordedHudData.copyItem((ItemStack)channel.interpolate((float)start, ItemStack.EMPTY));
        channel.insert((float)start, startValue);
        ItemStack endValue = RecordedHudData.copyItem((ItemStack)channel.interpolate((float)end, startValue));
        channel.insert((float)end, endValue);
    }

    private static <T> void addBoundaryKeyframes(KeyframeChannel<T> channel, int start, int end, T fallback) {
        T startValue = channel.interpolate((float)start, fallback);
        channel.insert((float)start, startValue);
        channel.insert((float)end, channel.interpolate((float)end, startValue));
    }

    private static <T> void reset(KeyframeChannel<T> channel, T value) {
        channel.insert(0.0f, value);
    }

    private static float lastTick(KeyframeChannel<?> channel) {
        List keyframes = channel.getKeyframes();
        return keyframes.isEmpty() ? 0.0f : ((Keyframe)keyframes.get(keyframes.size() - 1)).getTick();
    }
}

