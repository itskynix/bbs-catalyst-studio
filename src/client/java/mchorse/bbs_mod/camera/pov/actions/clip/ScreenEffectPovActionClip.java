/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.base.BaseValueGroup
 *  mchorse.bbs_mod.settings.values.core.ValueColor
 *  mchorse.bbs_mod.settings.values.core.ValueString
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.colors.Color
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectEntry;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ScreenEffectPovActionClip
extends PovActionClip {
    public final ValueString activeEffects = new ValueString("active_effects", "");
    public final KeyframeChannel<Boolean> vignetteVisible = this.channel("vignette_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> vignetteOpacity = this.channel("vignette_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Color> vignetteColor = this.channel("vignette_color", KeyframeFactories.COLOR);
    public final KeyframeChannel<Boolean> fireVisible = this.channel("fire_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> frostVisible = this.channel("frost_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> frostProgress = this.channel("frost_progress", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> frostZoom = this.channel("frost_zoom", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> portalVisible = this.channel("portal_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> portalOpacity = this.channel("portal_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> pumpkinVisible = this.channel("pumpkin_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> pumpkinOpacity = this.channel("pumpkin_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> spyglassVisible = this.channel("spyglass_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> spyglassScale = this.channel("spyglass_scale", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> spyglassZoom = this.channel("spyglass_zoom", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> suffocationVisible = this.channel("suffocation_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<String> suffocationBlock = this.channel("suffocation_block", KeyframeFactories.STRING);
    public final KeyframeChannel<Float> suffocationOpacity = this.channel("suffocation_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> nightVisionVisible = this.channel("night_vision_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> nightVisionOpacity = this.channel("night_vision_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> nightVisionFlash = this.channel("night_vision_flash", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> blindnessVisible = this.channel("blindness_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> blindnessOpacity = this.channel("blindness_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> blindnessRadius = this.channel("blindness_radius", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> underwaterVisible = this.channel("underwater_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> underwaterOpacity = this.channel("underwater_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> totemVisible = this.channel("totem_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> totemProgress = this.channel("totem_progress", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> totemFlipped = this.channel("totem_flipped", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> totemParticles = this.channel("totem_particles", KeyframeFactories.BOOLEAN);
    public final ValueString totemItem = new ValueString("totem_item", "minecraft:totem_of_undying");
    public transient int lastTotemParticleTick = Integer.MIN_VALUE;
    public final KeyframeChannel<Boolean> nauseaVisible = this.channel("nausea_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> nauseaDistortion = this.channel("nausea_distortion", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> nauseaOpacity = this.channel("nausea_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> darknessVisible = this.channel("darkness_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Float> darknessOpacity = this.channel("darkness_opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> darknessRadius = this.channel("darkness_radius", KeyframeFactories.FLOAT);
    public final ValueColor fireColor = new ValueColor("fire_color", new Color(1.0f, 1.0f, 1.0f, 1.0f));
    public final ValueColor portalColor = new ValueColor("portal_color", new Color(1.0f, 1.0f, 1.0f, 1.0f));
    public final ValueColor frostColor = new ValueColor("frost_color", new Color(1.0f, 1.0f, 1.0f, 1.0f));
    public final ValueColor underwaterColor = new ValueColor("underwater_color", new Color(1.0f, 1.0f, 1.0f, 1.0f));

    public ScreenEffectPovActionClip() {
        this.add((BaseValue)this.activeEffects);
        this.add((BaseValue)this.totemItem);
        this.add((BaseValue)this.fireColor);
        this.add((BaseValue)this.portalColor);
        this.add((BaseValue)this.frostColor);
        this.add((BaseValue)this.underwaterColor);
    }

    public Clip create() {
        ScreenEffectPovActionClip clip = new ScreenEffectPovActionClip();
        clip.copy((BaseValueGroup)this);
        return clip;
    }

    @Override
    public PovActionType getActionType() {
        return PovActionType.SCREEN_EFFECT;
    }

    @Override
    public void normalize() {
        super.normalize();
        ScreenEffectPovActionClip.constant(this.vignetteVisible);
        ScreenEffectPovActionClip.clamp(this.vignetteOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.fireVisible);
        ScreenEffectPovActionClip.constant(this.frostVisible);
        ScreenEffectPovActionClip.clamp(this.frostProgress, 0.0f, 1.0f);
        ScreenEffectPovActionClip.clamp(this.frostZoom, 0.1f, 2.0f);
        ScreenEffectPovActionClip.constant(this.portalVisible);
        ScreenEffectPovActionClip.clamp(this.portalOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.pumpkinVisible);
        ScreenEffectPovActionClip.clamp(this.pumpkinOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.spyglassVisible);
        ScreenEffectPovActionClip.clamp(this.spyglassScale, 0.1f, 10.0f);
        ScreenEffectPovActionClip.clamp(this.spyglassZoom, 0.001f, 1000.0f);
        ScreenEffectPovActionClip.constant(this.suffocationVisible);
        ScreenEffectPovActionClip.constant(this.suffocationBlock);
        ScreenEffectPovActionClip.clamp(this.suffocationOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.nightVisionVisible);
        ScreenEffectPovActionClip.clamp(this.nightVisionOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.clamp(this.nightVisionFlash, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.blindnessVisible);
        ScreenEffectPovActionClip.clamp(this.blindnessOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.clamp(this.blindnessRadius, 0.5f, 128.0f);
        ScreenEffectPovActionClip.constant(this.underwaterVisible);
        ScreenEffectPovActionClip.clamp(this.underwaterOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.totemVisible);
        ScreenEffectPovActionClip.constant(this.totemFlipped);
        ScreenEffectPovActionClip.constant(this.totemParticles);
        ScreenEffectPovActionClip.clamp(this.totemProgress, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.nauseaVisible);
        ScreenEffectPovActionClip.clamp(this.nauseaDistortion, 0.0f, 1.0f);
        ScreenEffectPovActionClip.clamp(this.nauseaOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.constant(this.darknessVisible);
        ScreenEffectPovActionClip.clamp(this.darknessOpacity, 0.0f, 1.0f);
        ScreenEffectPovActionClip.clamp(this.darknessRadius, 1.0f, 128.0f);
    }

    public List<String> getActiveEffectList() {
        String str = (String)this.activeEffects.get();
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        ArrayList<String> list = new ArrayList<String>();
        for (String part : str.split(",")) {
            String trimmed = part.trim().toLowerCase();
            if (trimmed.isEmpty() || list.contains(trimmed)) continue;
            list.add(trimmed);
        }
        list.sort(Comparator.comparingInt(id -> ScreenEffectPresets.getById((String)id).vanillaRenderOrder));
        return list;
    }

    public boolean hasEffect(String id) {
        return this.getActiveEffectList().contains(id.toLowerCase());
    }

    public void addEffect(String id) {
        this.addEffect(id, true);
    }

    public void addEffect(String id, boolean seedDefaults) {
        if (id == null) {
            return;
        }
        String lower = id.toLowerCase();
        List<String> list = this.getActiveEffectList();
        if (!list.contains(lower)) {
            list.add(lower);
            list.sort(Comparator.comparingInt(eid -> ScreenEffectPresets.getById((String)eid).vanillaRenderOrder));
            this.activeEffects.set(String.join((CharSequence)",", list));
            if (seedDefaults) {
                this.seedDefaultKeyframes(lower);
            }
        }
    }

    public List<ScreenEffectEntry> getEffects() {
        ArrayList<ScreenEffectEntry> list = new ArrayList<ScreenEffectEntry>();
        for (String id : this.getActiveEffectList()) {
            list.add(new ScreenEffectEntry(id));
        }
        return list;
    }

    public void addEffect(ScreenEffectEntry entry) {
        if (entry != null) {
            this.addEffect(entry.getEffectId());
        }
    }

    public void removeEffect(ScreenEffectEntry entry) {
        if (entry != null) {
            this.removeEffect(entry.getEffectId());
        }
    }

    public void removeEffect(String id) {
        if (id == null) {
            return;
        }
        String lower = id.toLowerCase();
        List<String> list = this.getActiveEffectList();
        if (list.remove(lower)) {
            this.activeEffects.set(String.join((CharSequence)",", list));
            this.clearEffectKeyframes(lower);
        }
    }

    private static <T> void clearChannel(KeyframeChannel<T> channel) {
        while (!channel.isEmpty()) {
            channel.remove(0);
        }
    }

    public void clearEffectKeyframes(String id) {
        switch (id) {
            case "vignette": {
                ScreenEffectPovActionClip.clearChannel(this.vignetteVisible);
                ScreenEffectPovActionClip.clearChannel(this.vignetteOpacity);
                ScreenEffectPovActionClip.clearChannel(this.vignetteColor);
                break;
            }
            case "fire": {
                ScreenEffectPovActionClip.clearChannel(this.fireVisible);
                break;
            }
            case "frost": {
                ScreenEffectPovActionClip.clearChannel(this.frostVisible);
                ScreenEffectPovActionClip.clearChannel(this.frostProgress);
                ScreenEffectPovActionClip.clearChannel(this.frostZoom);
                break;
            }
            case "portal": {
                ScreenEffectPovActionClip.clearChannel(this.portalVisible);
                ScreenEffectPovActionClip.clearChannel(this.portalOpacity);
                break;
            }
            case "pumpkin": {
                ScreenEffectPovActionClip.clearChannel(this.pumpkinVisible);
                ScreenEffectPovActionClip.clearChannel(this.pumpkinOpacity);
                break;
            }
            case "spyglass": {
                ScreenEffectPovActionClip.clearChannel(this.spyglassVisible);
                ScreenEffectPovActionClip.clearChannel(this.spyglassScale);
                ScreenEffectPovActionClip.clearChannel(this.spyglassZoom);
                break;
            }
            case "suffocation": {
                ScreenEffectPovActionClip.clearChannel(this.suffocationVisible);
                ScreenEffectPovActionClip.clearChannel(this.suffocationBlock);
                ScreenEffectPovActionClip.clearChannel(this.suffocationOpacity);
                break;
            }
            case "night_vision": {
                ScreenEffectPovActionClip.clearChannel(this.nightVisionVisible);
                ScreenEffectPovActionClip.clearChannel(this.nightVisionOpacity);
                ScreenEffectPovActionClip.clearChannel(this.nightVisionFlash);
                break;
            }
            case "blindness": {
                ScreenEffectPovActionClip.clearChannel(this.blindnessVisible);
                ScreenEffectPovActionClip.clearChannel(this.blindnessOpacity);
                ScreenEffectPovActionClip.clearChannel(this.blindnessRadius);
                break;
            }
            case "totem": {
                ScreenEffectPovActionClip.clearChannel(this.totemVisible);
                ScreenEffectPovActionClip.clearChannel(this.totemProgress);
                ScreenEffectPovActionClip.clearChannel(this.totemFlipped);
                ScreenEffectPovActionClip.clearChannel(this.totemParticles);
                break;
            }
            case "nausea": {
                ScreenEffectPovActionClip.clearChannel(this.nauseaVisible);
                ScreenEffectPovActionClip.clearChannel(this.nauseaDistortion);
                ScreenEffectPovActionClip.clearChannel(this.nauseaOpacity);
                break;
            }
            case "darkness": {
                ScreenEffectPovActionClip.clearChannel(this.darknessVisible);
                ScreenEffectPovActionClip.clearChannel(this.darknessOpacity);
                ScreenEffectPovActionClip.clearChannel(this.darknessRadius);
                break;
            }
            case "underwater": {
                ScreenEffectPovActionClip.clearChannel(this.underwaterVisible);
                ScreenEffectPovActionClip.clearChannel(this.underwaterOpacity);
            }
        }
    }

    public void seedDefaultKeyframes(String id) {
        switch (id) {
            case "vignette": {
                if (this.vignetteVisible.isEmpty()) {
                    this.vignetteVisible.insert(0.0f, true);
                }
                if (this.vignetteOpacity.isEmpty()) {
                    this.vignetteOpacity.insert(0.0f, Float.valueOf(0.5f));
                }
                if (!this.vignetteColor.isEmpty()) break;
                this.vignetteColor.insert(0.0f, new Color(0.0f, 0.0f, 0.0f, 1.0f));
                break;
            }
            case "fire": {
                if (!this.fireVisible.isEmpty()) break;
                this.fireVisible.insert(0.0f, true);
                break;
            }
            case "frost": {
                if (this.frostVisible.isEmpty()) {
                    this.frostVisible.insert(0.0f, true);
                }
                if (this.frostProgress.isEmpty()) {
                    this.frostProgress.insert(0.0f, Float.valueOf(1.0f));
                }
                if (!this.frostZoom.isEmpty()) break;
                this.frostZoom.insert(0.0f, Float.valueOf(1.0f));
                break;
            }
            case "portal": {
                if (this.portalVisible.isEmpty()) {
                    this.portalVisible.insert(0.0f, true);
                }
                if (!this.portalOpacity.isEmpty()) break;
                this.portalOpacity.insert(0.0f, Float.valueOf(1.0f));
                break;
            }
            case "pumpkin": {
                if (this.pumpkinVisible.isEmpty()) {
                    this.pumpkinVisible.insert(0.0f, true);
                }
                if (!this.pumpkinOpacity.isEmpty()) break;
                this.pumpkinOpacity.insert(0.0f, Float.valueOf(1.0f));
                break;
            }
            case "spyglass": {
                if (this.spyglassVisible.isEmpty()) {
                    this.spyglassVisible.insert(0.0f, true);
                }
                if (this.spyglassScale.isEmpty()) {
                    this.spyglassScale.insert(0.0f, Float.valueOf(1.12f));
                }
                if (!this.spyglassZoom.isEmpty()) break;
                this.spyglassZoom.insert(0.0f, Float.valueOf(0.102f));
                break;
            }
            case "suffocation": {
                if (this.suffocationVisible.isEmpty()) {
                    this.suffocationVisible.insert(0.0f, true);
                }
                if (!this.suffocationBlock.isEmpty()) break;
                this.suffocationBlock.insert(0.0f, "minecraft:stone");
                break;
            }
            case "night_vision": {
                if (this.nightVisionVisible.isEmpty()) {
                    this.nightVisionVisible.insert(0.0f, true);
                }
                if (this.nightVisionOpacity.isEmpty()) {
                    this.nightVisionOpacity.insert(0.0f, Float.valueOf(1.0f));
                }
                if (!this.nightVisionFlash.isEmpty()) break;
                this.nightVisionFlash.insert(0.0f, Float.valueOf(0.0f));
                break;
            }
            case "blindness": {
                if (this.blindnessVisible.isEmpty()) {
                    this.blindnessVisible.insert(0.0f, true);
                }
                if (this.blindnessOpacity.isEmpty()) {
                    this.blindnessOpacity.insert(0.0f, Float.valueOf(1.0f));
                }
                if (!this.blindnessRadius.isEmpty()) break;
                this.blindnessRadius.insert(0.0f, Float.valueOf(5.0f));
                break;
            }
            case "totem": {
                if (this.totemVisible.isEmpty()) {
                    this.totemVisible.insert(0.0f, true);
                }
                if (this.totemFlipped.isEmpty()) {
                    this.totemFlipped.insert(0.0f, false);
                }
                if (this.totemParticles.isEmpty()) {
                    this.totemParticles.insert(0.0f, true);
                }
                if (!this.totemProgress.isEmpty()) break;
                this.totemProgress.insert(0.0f, Float.valueOf(0.0f));
                this.totemProgress.insert((float)Math.min(40, (Integer)this.duration.get()), Float.valueOf(1.0f));
                break;
            }
            case "nausea": {
                if (this.nauseaVisible.isEmpty()) {
                    this.nauseaVisible.insert(0.0f, true);
                }
                if (this.nauseaDistortion.isEmpty()) {
                    this.nauseaDistortion.insert(0.0f, Float.valueOf(1.0f));
                }
                if (!this.nauseaOpacity.isEmpty()) break;
                this.nauseaOpacity.insert(0.0f, Float.valueOf(1.0f));
                break;
            }
            case "darkness": {
                if (this.darknessVisible.isEmpty()) {
                    this.darknessVisible.insert(0.0f, true);
                }
                if (this.darknessOpacity.isEmpty()) {
                    this.darknessOpacity.insert(0.0f, Float.valueOf(1.0f));
                }
                if (!this.darknessRadius.isEmpty()) break;
                this.darknessRadius.insert(0.0f, Float.valueOf(15.0f));
                break;
            }
            case "underwater": {
                if (this.underwaterVisible.isEmpty()) {
                    this.underwaterVisible.insert(0.0f, true);
                }
                if (!this.underwaterOpacity.isEmpty()) break;
                this.underwaterOpacity.insert(0.0f, Float.valueOf(0.1f));
            }
        }
    }

    public void trimToRecording(int endTick) {
        int start = (Integer)this.tick.get();
        if (endTick > start && start + (Integer)this.duration.get() > endTick) {
            this.duration.set(Math.max(1, endTick - start));
        }
    }

    public void ensureBakingBounds() {
        this.padChannels(((Integer)this.duration.get()).intValue());
    }

    public void padChannelsToEnd(float end) {
        this.padChannels(end);
    }

    public void padChannels(float end) {
        ScreenEffectPovActionClip.padChannel(this.vignetteVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.vignetteOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.vignetteColor, end, new Color(0.0f, 0.0f, 0.0f, 0.0f));
        ScreenEffectPovActionClip.padChannel(this.fireVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.frostVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.frostProgress, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.frostZoom, end, Float.valueOf(1.0f));
        ScreenEffectPovActionClip.padChannel(this.portalVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.portalOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.nauseaVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.nauseaDistortion, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.nauseaOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.pumpkinVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.pumpkinOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.spyglassVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.spyglassScale, end, Float.valueOf(1.12f));
        ScreenEffectPovActionClip.padChannel(this.spyglassZoom, end, Float.valueOf(1.0f));
        ScreenEffectPovActionClip.padChannel(this.suffocationVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.suffocationBlock, end, "minecraft:stone");
        ScreenEffectPovActionClip.padChannel(this.suffocationOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.nightVisionVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.nightVisionOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.nightVisionFlash, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.blindnessVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.blindnessOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.blindnessRadius, end, Float.valueOf(5.0f));
        ScreenEffectPovActionClip.padChannel(this.totemVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.totemFlipped, end, false);
        ScreenEffectPovActionClip.padChannel(this.totemParticles, end, false);
        ScreenEffectPovActionClip.padChannel(this.totemProgress, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.nauseaVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.nauseaDistortion, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.nauseaOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.darknessVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.darknessOpacity, end, Float.valueOf(0.0f));
        ScreenEffectPovActionClip.padChannel(this.darknessRadius, end, Float.valueOf(15.0f));
        ScreenEffectPovActionClip.padChannel(this.underwaterVisible, end, false);
        ScreenEffectPovActionClip.padChannel(this.underwaterOpacity, end, Float.valueOf(0.0f));
    }

    private static <T> void padChannel(KeyframeChannel<T> channel, float end, T defaultStartValue) {
        if (channel == null || channel.isEmpty() || end <= 0.0f) {
            return;
        }
        List<Keyframe<T>> keyframes = channel.getKeyframes();
        Keyframe<T> first = keyframes.get(0);
        Keyframe<T> last = keyframes.get(keyframes.size() - 1);
        if (first.getTick() > 0.0f) {
            channel.insert(0.0f, defaultStartValue);
            if (first.getTick() > 1.0f) {
                channel.insert(first.getTick() - 1.0f, defaultStartValue);
            }
        }
        if (last.getTick() < end) {
            channel.insert(end, last.getValue());
        }
    }
}

