/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.player.PlayerEntity
 */
package mchorse.bbs_mod.camera.pov.hud;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class HudMount {
    private HudMount() {
    }

    public static LivingEntity jumpingMount(PlayerEntity player) {
        LivingEntity living;
        if (player == null) {
            return null;
        }
        Entity entity = player.getVehicle();
        return entity instanceof LivingEntity ? (living = (LivingEntity)entity) : null;
    }

    public static int heartSlots(LivingEntity mount) {
        if (mount == null || mount.isRemoved()) {
            return 0;
        }
        int slots = (int)(mount.getMaxHealth() + 0.5f) / 2;
        return Math.max(0, Math.min(30, slots));
    }
}

