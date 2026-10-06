/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.types.MapType
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.registry.Registries
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.statuseffect;

import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class StatusEffectEntry {
    private String effectId = "minecraft:speed";
    private boolean unlimited = false;
    private int durationSeconds = 100;
    private int amplifier = 0;

    public StatusEffectEntry() {
    }

    public StatusEffectEntry(String effectId, boolean unlimited, int durationSeconds, int amplifier) {
        this.effectId = effectId != null ? effectId : "minecraft:speed";
        this.unlimited = unlimited;
        this.durationSeconds = Math.max(1, durationSeconds);
        this.amplifier = Math.max(0, amplifier);
    }

    public String getEffectId() {
        return this.effectId;
    }

    public void setEffectId(String effectId) {
        this.effectId = effectId != null ? effectId : "minecraft:speed";
    }

    public boolean isUnlimited() {
        return this.unlimited;
    }

    public void setUnlimited(boolean unlimited) {
        this.unlimited = unlimited;
    }

    public int getDurationSeconds() {
        return this.durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = Math.max(1, durationSeconds);
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = Math.max(0, amplifier);
    }

    public StatusEffect getStatusEffect() {
        Identifier id = Identifier.tryParse((String)this.effectId);
        if (id != null && Registries.STATUS_EFFECT.containsId(id)) {
            return (StatusEffect)Registries.STATUS_EFFECT.get(id);
        }
        return StatusEffects.SPEED;
    }

    public String getDisplayName() {
        Text name;
        StatusEffect effect = this.getStatusEffect();
        if (effect != null && (name = effect.getName()) != null) {
            return name.getString();
        }
        return this.effectId;
    }

    public int getRemainingSeconds(float elapsedTicks) {
        if (this.unlimited) {
            return -1;
        }
        int elapsedSec = (int)Math.floor(elapsedTicks / 20.0f);
        return Math.max(0, this.durationSeconds - elapsedSec);
    }

    public boolean isExpired(float elapsedTicks) {
        if (this.unlimited) {
            return false;
        }
        return elapsedTicks >= (float)this.durationSeconds * 20.0f;
    }

    public String formatDuration(float elapsedTicks) {
        if (this.unlimited) {
            return "\u221e";
        }
        int remaining = this.getRemainingSeconds(elapsedTicks);
        int mins = remaining / 60;
        int secs = remaining % 60;
        return String.format("%d:%02d", mins, secs);
    }

    public String formatDurationSecondsOnly(int seconds) {
        if (this.unlimited) {
            return "\u221e";
        }
        int mins = seconds / 60;
        int secs = seconds % 60;
        if (mins > 0) {
            return String.format("%dmin %02ds (%d:%02d)", mins, secs, mins, secs);
        }
        return String.format("%ds (0:%02d)", secs, secs);
    }

    public StatusEffectEntry copy() {
        return new StatusEffectEntry(this.effectId, this.unlimited, this.durationSeconds, this.amplifier);
    }

    public void toData(MapType data) {
        data.putString("id", this.effectId);
        data.putBool("unlimited", this.unlimited);
        data.putInt("duration", this.durationSeconds);
        data.putInt("amplifier", this.amplifier);
    }

    public void fromData(MapType data) {
        if (data.has("id")) {
            this.effectId = data.getString("id");
        }
        if (data.has("unlimited")) {
            this.unlimited = data.getBool("unlimited");
        }
        if (data.has("duration")) {
            this.durationSeconds = data.getInt("duration");
        }
        if (data.has("amplifier")) {
            this.amplifier = data.getInt("amplifier");
        }
    }
}

