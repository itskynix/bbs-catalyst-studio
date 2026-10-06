/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.hud.recording;

import mchorse.bbs_mod.camera.pov.hud.HudMount;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import java.util.Arrays;
import java.util.Objects;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class HudRecorder {
    private static final int HEALTH_STABLE_TICKS = 20;
    private static final int DAMAGE_FLASH_TICKS = 20;
    private static final int HEAL_FLASH_TICKS = 10;
    private int lastRecordedTick = Integer.MIN_VALUE;
    private int lastRecordedHealth;
    private int recordedRenderHealth;
    private int lastHealthChangeTick;
    private int healthFlashEndTick;
    private Integer lastHealth = null;
    private Integer lastPrevHealth = null;
    private Integer lastHealthContainer = null;
    private Integer lastAbsorption = null;
    private Integer lastAbsorptionContainer = null;
    private Integer lastHeartType = null;
    private Boolean lastHardcore = null;
    private Boolean lastRegeneration = null;
    private Integer lastArmor = null;
    private Integer lastHunger = null;
    private Boolean lastHungerEffect = null;
    private Integer lastMountHealthContainer = null;
    private Integer lastMountHealth = null;
    private Integer lastAir = null;
    private Double lastExperience = null;
    private Integer lastExperienceLevel = null;
    private Boolean lastHeartFlash = null;
    private Float lastAttackCooldown = null;
    private Boolean lastStatusBarsVisible = null;
    private final ItemStack[] lastInventory = new ItemStack[27];

    public void reset() {
        this.lastRecordedTick = Integer.MIN_VALUE;
        this.lastRecordedHealth = 20;
        this.recordedRenderHealth = 20;
        this.lastHealthChangeTick = 0;
        this.healthFlashEndTick = 0;
        this.lastHealth = null;
        this.lastPrevHealth = null;
        this.lastHealthContainer = null;
        this.lastAbsorption = null;
        this.lastAbsorptionContainer = null;
        this.lastHeartType = null;
        this.lastHardcore = null;
        this.lastRegeneration = null;
        this.lastArmor = null;
        this.lastHunger = null;
        this.lastHungerEffect = null;
        this.lastMountHealthContainer = null;
        this.lastMountHealth = null;
        this.lastAir = null;
        this.lastExperience = null;
        this.lastExperienceLevel = null;
        this.lastHeartFlash = null;
        this.lastAttackCooldown = null;
        this.lastStatusBarsVisible = null;
        Arrays.fill(this.lastInventory, null);
    }

    public void record(RecordedHudData data, int tick, PlayerEntity player) {
        boolean firstFrame;
        int absorptionValue = Math.max(0, Math.round(player.getAbsorptionAmount()));
        int healthValue = Math.max(0, (int)Math.ceil(player.getHealth()));
        boolean bl = firstFrame = this.lastRecordedTick == Integer.MIN_VALUE || tick <= this.lastRecordedTick;
        if (firstFrame) {
            this.lastRecordedHealth = healthValue;
            this.recordedRenderHealth = healthValue;
            this.lastHealthChangeTick = tick;
            this.healthFlashEndTick = tick;
        } else if (healthValue != this.lastRecordedHealth) {
            this.lastHealthChangeTick = tick;
            this.healthFlashEndTick = tick + (healthValue < this.lastRecordedHealth ? 20 : 10);
        } else if (tick - this.lastHealthChangeTick > 20) {
            this.recordedRenderHealth = healthValue;
        }
        boolean healthFlash = tick < this.healthFlashEndTick;
        int healthContainer = Math.round(player.getMaxHealth());
        int heartType = player.hasStatusEffect(StatusEffects.POISON) ? 1 : (player.hasStatusEffect(StatusEffects.WITHER) ? 2 : (player.isFrozen() ? 4 : 0));
        boolean hardcore = player.getWorld().getLevelProperties().isHardcore();
        boolean regeneration = player.hasStatusEffect(StatusEffects.REGENERATION);
        int armor = player.getArmor();
        int hunger = player.getHungerManager().getFoodLevel();
        boolean hungerEffect = player.hasStatusEffect(StatusEffects.HUNGER);
        LivingEntity mount = HudMount.jumpingMount(player);
        int mountSlots = HudMount.heartSlots(mount);
        int mountHealthContainer = mountSlots * 2;
        int mountHealth = mount == null ? 0 : Math.max(0, (int)Math.ceil(mount.getHealth()));
        int air = Math.max(0, Math.min(300, player.getAir()));
        double experience = player.experienceProgress;
        int experienceLevel = player.experienceLevel;
        MinecraftClient client = MinecraftClient.getInstance();
        float attackCooldown = client != null && client.currentScreen != null ? 1.0f : player.getAttackCooldownProgress(0.0f);
        boolean statusBarsVisible = client.interactionManager == null || client.interactionManager.hasStatusBars();
        HudRecorder.recordValue(data.health, healthValue, tick, this.lastHealth);
        this.lastHealth = healthValue;
        HudRecorder.recordValue(data.previousHealth, this.recordedRenderHealth, tick, this.lastPrevHealth);
        this.lastPrevHealth = this.recordedRenderHealth;
        HudRecorder.recordValue(data.healthContainer, healthContainer, tick, this.lastHealthContainer);
        this.lastHealthContainer = healthContainer;
        HudRecorder.recordValue(data.absorption, absorptionValue, tick, this.lastAbsorption);
        this.lastAbsorption = absorptionValue;
        HudRecorder.recordValue(data.absorptionContainer, absorptionValue, tick, this.lastAbsorptionContainer);
        this.lastAbsorptionContainer = absorptionValue;
        HudRecorder.recordValue(data.heartType, heartType, tick, this.lastHeartType);
        this.lastHeartType = heartType;
        HudRecorder.recordValue(data.hardcore, hardcore, tick, this.lastHardcore);
        this.lastHardcore = hardcore;
        HudRecorder.recordValue(data.regeneration, regeneration, tick, this.lastRegeneration);
        this.lastRegeneration = regeneration;
        HudRecorder.recordValue(data.armor, armor, tick, this.lastArmor);
        this.lastArmor = armor;
        HudRecorder.recordValue(data.hunger, hunger, tick, this.lastHunger);
        this.lastHunger = hunger;
        HudRecorder.recordValue(data.hungerEffect, hungerEffect, tick, this.lastHungerEffect);
        this.lastHungerEffect = hungerEffect;
        HudRecorder.recordValue(data.mountHealthContainer, mountHealthContainer, tick, this.lastMountHealthContainer);
        this.lastMountHealthContainer = mountHealthContainer;
        HudRecorder.recordValue(data.mountHealth, mountHealth, tick, this.lastMountHealth);
        this.lastMountHealth = mountHealth;
        HudRecorder.recordValue(data.air, air, tick, this.lastAir);
        this.lastAir = air;
        if (this.lastExperience == null || Math.abs(experience - this.lastExperience) > 1.0E-4) {
            HudRecorder.recordValue(data.experience, experience, tick, this.lastExperience);
            this.lastExperience = experience;
        }
        HudRecorder.recordValue(data.experienceLevel, experienceLevel, tick, this.lastExperienceLevel);
        this.lastExperienceLevel = experienceLevel;
        HudRecorder.recordValue(data.heartFlash, healthFlash, tick, this.lastHeartFlash);
        this.lastHeartFlash = healthFlash;
        if (this.lastAttackCooldown == null || Math.abs(attackCooldown - this.lastAttackCooldown.floatValue()) > 0.001f) {
            if (this.lastAttackCooldown != null && this.lastAttackCooldown.floatValue() >= 0.999f && attackCooldown < 0.999f && tick > 0) {
                HudRecorder.recordValue(data.attackCooldown, Float.valueOf(1.0f), tick - 1, this.lastAttackCooldown);
            }
            HudRecorder.recordValue(data.attackCooldown, Float.valueOf(attackCooldown), tick, this.lastAttackCooldown);
            this.lastAttackCooldown = Float.valueOf(attackCooldown);
        }
        HudRecorder.recordValue(data.statusBarsVisible, statusBarsVisible, tick, this.lastStatusBarsVisible);
        this.lastStatusBarsVisible = statusBarsVisible;
        boolean inventoryChanged = false;
        for (int i = 0; i < 27; ++i) {
            ItemStack cur;
            ItemStack stack = (ItemStack)player.getInventory().main.get(9 + i);
            ItemStack itemStack = cur = stack == null ? ItemStack.EMPTY : stack;
            if (this.lastInventory[i] != null && ItemStack.areEqual((ItemStack)cur, (ItemStack)this.lastInventory[i])) continue;
            data.inventory.get(i).insert((float)tick, cur.copy());
            this.lastInventory[i] = cur.copy();
            inventoryChanged = true;
        }
        if (firstFrame || inventoryChanged) {
            data.inventoryAnchor.insert((float)tick, true);
        }
        this.lastRecordedTick = tick;
        this.lastRecordedHealth = healthValue;
    }

    private static <T> void recordValue(KeyframeChannel<T> channel, T value, float tick, T lastVal) {
        if (channel == null) {
            return;
        }
        if (channel.isEmpty() || !Objects.equals(value, lastVal)) {
            channel.insert(tick, value);
        }
    }
}

