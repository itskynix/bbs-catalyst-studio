/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.types.MapType
 *  mchorse.bbs_mod.resources.Link
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresetEntry;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresets;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public class ScreenEffectEntry {
    private String effectId = "fire";
    private float intensity = 1.0f;
    private int fadeIn = 0;
    private int fadeOut = 0;
    private Link customTexture = null;
    private int customColor = -16777216;
    private String blockOrItem = "";

    public ScreenEffectEntry() {
    }

    public ScreenEffectEntry(String effectId) {
        this.effectId = effectId;
        ScreenEffectPresetEntry preset = ScreenEffectPresets.getById(effectId);
        if (preset != null) {
            this.intensity = preset.defaultIntensity;
        }
    }

    public String getEffectId() {
        return this.effectId;
    }

    public void setEffectId(String effectId) {
        this.effectId = effectId == null ? "fire" : effectId;
    }

    public float getIntensity() {
        return this.intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = MathHelper.clamp((float)intensity, (float)0.0f, (float)1.0f);
    }

    public int getFadeIn() {
        return this.fadeIn;
    }

    public void setFadeIn(int fadeIn) {
        this.fadeIn = Math.max(0, fadeIn);
    }

    public int getFadeOut() {
        return this.fadeOut;
    }

    public void setFadeOut(int fadeOut) {
        this.fadeOut = Math.max(0, fadeOut);
    }

    public Link getCustomTexture() {
        return this.customTexture;
    }

    public void setCustomTexture(Link customTexture) {
        this.customTexture = customTexture;
    }

    public int getCustomColor() {
        return this.customColor;
    }

    public void setCustomColor(int customColor) {
        this.customColor = customColor;
    }

    public String getBlockOrItem() {
        return this.blockOrItem;
    }

    public void setBlockOrItem(String blockOrItem) {
        this.blockOrItem = blockOrItem == null ? "" : blockOrItem;
    }

    public String getDisplayName() {
        ScreenEffectPresetEntry preset = ScreenEffectPresets.getById(this.effectId);
        return preset != null ? preset.name : this.effectId;
    }

    public String getDescription() {
        ScreenEffectPresetEntry preset = ScreenEffectPresets.getById(this.effectId);
        return preset != null ? preset.description : "";
    }

    public int getVanillaRenderOrder() {
        ScreenEffectPresetEntry preset = ScreenEffectPresets.getById(this.effectId);
        return preset != null ? preset.vanillaRenderOrder : 10;
    }

    public ItemStack createIconStack() {
        ScreenEffectPresetEntry preset = ScreenEffectPresets.getById(this.effectId);
        return preset != null ? preset.createIconStack() : ItemStack.EMPTY;
    }

    public float getEffectiveIntensity(float elapsed, int duration) {
        if (duration <= 0 || this.intensity <= 0.001f) {
            return 0.0f;
        }
        int in = Math.min(this.fadeIn, duration / 2);
        int out = Math.min(this.fadeOut, duration / 2);
        float factor = 1.0f;
        if (in > 0 && elapsed < (float)in) {
            factor = elapsed / (float)in;
        } else if (out > 0 && elapsed > (float)(duration - out)) {
            factor = ((float)duration - elapsed) / (float)out;
        }
        return MathHelper.clamp((float)(this.intensity * factor), (float)0.0f, (float)1.0f);
    }

    public ScreenEffectEntry copy() {
        ScreenEffectEntry copy = new ScreenEffectEntry(this.effectId);
        copy.intensity = this.intensity;
        copy.fadeIn = this.fadeIn;
        copy.fadeOut = this.fadeOut;
        copy.customTexture = this.customTexture;
        copy.customColor = this.customColor;
        copy.blockOrItem = this.blockOrItem;
        return copy;
    }

    public void toData(MapType data) {
        data.putString("id", this.effectId);
        data.putFloat("intensity", this.intensity);
        data.putInt("fade_in", this.fadeIn);
        data.putInt("fade_out", this.fadeOut);
        if (this.customTexture != null) {
            data.putString("texture", this.customTexture.toString());
        }
        data.putInt("color", this.customColor);
        if (this.blockOrItem != null && !this.blockOrItem.isEmpty()) {
            data.putString("item_block", this.blockOrItem);
        }
    }

    public void fromData(MapType data) {
        if (data.has("id")) {
            this.effectId = data.getString("id");
        }
        if (data.has("intensity")) {
            this.intensity = data.getFloat("intensity");
        }
        if (data.has("fade_in")) {
            this.fadeIn = data.getInt("fade_in");
        }
        if (data.has("fade_out")) {
            this.fadeOut = data.getInt("fade_out");
        }
        this.customTexture = data.has("texture") ? Link.create((String)data.getString("texture")) : null;
        if (data.has("color")) {
            this.customColor = data.getInt("color");
        }
        this.blockOrItem = data.has("item_block") ? data.getString("item_block") : "";
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        ScreenEffectEntry other = (ScreenEffectEntry)obj;
        return this.effectId != null && this.effectId.equalsIgnoreCase(other.effectId);
    }

    public int hashCode() {
        return this.effectId != null ? this.effectId.toLowerCase().hashCode() : 0;
    }
}

