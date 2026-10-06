/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.base.BaseValueGroup
 *  mchorse.bbs_mod.settings.values.core.ValueLink
 *  mchorse.bbs_mod.settings.values.core.ValueString
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.text.Text
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastPresets;
import mchorse.bbs_mod.camera.pov.actions.toast.ToastTypeEntry;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.text.Text;

public final class ToastPovActionClip
extends PovActionClip {
    public final ValueString presetId = new ValueString("preset_id", "adv_stone_age");
    public final ValueString customTitle = new ValueString("custom_title", "");
    public final ValueString customDescription = new ValueString("custom_description", "");
    public final ValueString customIcon = new ValueString("custom_icon", "");
    public final ValueLink customTexture = new ValueLink("custom_texture", null);
    public final ValueString frameType = new ValueString("frame_type", "task");

    public ToastPovActionClip() {
        this.add((BaseValue)this.presetId);
        this.add((BaseValue)this.customTitle);
        this.add((BaseValue)this.customDescription);
        this.add((BaseValue)this.customIcon);
        this.add((BaseValue)this.customTexture);
        this.add((BaseValue)this.frameType);
    }

    public Clip create() {
        ToastPovActionClip clip = new ToastPovActionClip();
        clip.copy((BaseValueGroup)this);
        return clip;
    }

    @Override
    public PovActionType getActionType() {
        return PovActionType.TOASTS;
    }

    public void trimToRecording(int endTick) {
        int start = (Integer)this.tick.get();
        if (endTick > start && start + (Integer)this.duration.get() > endTick) {
            this.duration.set(Math.max(1, endTick - start));
        }
    }

    public void ensureBakingBounds() {
        if ((Integer)this.duration.get() < 1) {
            this.duration.set(1);
        }
    }

    public String getPresetId() {
        return (String)this.presetId.get();
    }

    public void setPresetId(String presetId) {
        this.presetId.set((presetId == null ? "adv_stone_age" : presetId));
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        if (entry != null) {
            this.frameType.set(entry.frameType);
        }
    }

    public void applyPreset(ToastTypeEntry entry) {
        if (entry != null) {
            this.presetId.set(entry.id);
            this.customTitle.set("");
            this.customDescription.set("");
            this.customIcon.set("");
            this.customTexture.set(null);
            this.frameType.set(entry.frameType);
        }
    }

    public String getEffectiveTitle() {
        String title = (String)this.customTitle.get();
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        return entry != null ? entry.title : "Advancement Made!";
    }

    public Text getEffectiveTitleText() {
        String title = (String)this.customTitle.get();
        if (title != null && !title.trim().isEmpty()) {
            return Text.literal((String)title);
        }
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        return entry != null ? entry.getTitleText() : Text.translatable((String)"advancements.toast.task");
    }

    public String getEffectiveDescription() {
        String desc = (String)this.customDescription.get();
        if (desc != null && !desc.trim().isEmpty()) {
            return desc;
        }
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        return entry != null ? entry.description : "Stone Age";
    }

    public Text getEffectiveDescriptionText() {
        String desc = (String)this.customDescription.get();
        if (desc != null && !desc.trim().isEmpty()) {
            return Text.literal((String)desc);
        }
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        return entry != null ? entry.getDescriptionText() : Text.translatable((String)"advancements.story.mine_stone.title");
    }

    public String getEffectiveIcon() {
        String icon = (String)this.customIcon.get();
        if (icon != null && !icon.trim().isEmpty()) {
            return icon;
        }
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        return entry != null ? entry.iconItemId : "minecraft:wooden_pickaxe";
    }

    public Link getCustomTexture() {
        return (Link)this.customTexture.get();
    }

    public void setCustomTexture(Link customTexture) {
        this.customTexture.set(customTexture);
    }

    public String getEffectiveFrameType() {
        String type = (String)this.frameType.get();
        if (type != null && !type.trim().isEmpty()) {
            return type;
        }
        ToastTypeEntry entry = ToastPresets.getById((String)this.presetId.get());
        return entry != null ? entry.frameType : "task";
    }

    public String getCustomTitle() {
        return (String)this.customTitle.get();
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle.set((customTitle == null ? "" : customTitle));
    }

    public String getCustomDescription() {
        return (String)this.customDescription.get();
    }

    public void setCustomDescription(String customDescription) {
        this.customDescription.set((customDescription == null ? "" : customDescription));
    }

    public String getCustomIcon() {
        return (String)this.customIcon.get();
    }

    public void setCustomIcon(String customIcon) {
        this.customIcon.set((customIcon == null ? "" : customIcon));
    }

    public String getFrameType() {
        return (String)this.frameType.get();
    }

    public void setFrameType(String frameType) {
        this.frameType.set((frameType == null ? "task" : frameType));
    }
}

