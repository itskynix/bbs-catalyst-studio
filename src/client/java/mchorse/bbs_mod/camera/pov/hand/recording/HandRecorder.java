/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.forms.forms.Form
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.util.Arm
 *  net.minecraft.util.Hand
 *  net.minecraft.util.UseAction
 */
package mchorse.bbs_mod.camera.pov.hand.recording;

import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HeldItemRendererPovAccess;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;

public final class HandRecorder {
    private int lastRecordedTick = Integer.MIN_VALUE;
    private boolean suppressRejectedConsumeSwing;
    private int spyglassCooldown = 0;

    public void reset() {
        this.lastRecordedTick = Integer.MIN_VALUE;
        this.suppressRejectedConsumeSwing = false;
        this.spyglassCooldown = 0;
    }

    public boolean hasRecorded() {
        return this.lastRecordedTick != Integer.MIN_VALUE;
    }

    public void record(RecordedHandData data, int tick, ClientPlayerEntity player, Form recordingForm) {
        boolean usingSpyglass;
        boolean rejectedConsume;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean using = player.isUsingItem();
        boolean bl = rejectedConsume = !using && client.options.useKey.isPressed() && (HandRecorder.hasConsumeAction(player.getMainHandStack()) || HandRecorder.hasConsumeAction(player.getOffHandStack()));
        if (using) {
            this.suppressRejectedConsumeSwing = false;
        } else if (rejectedConsume) {
            this.suppressRejectedConsumeSwing = true;
        } else if (!player.handSwinging) {
            this.suppressRejectedConsumeSwing = false;
        }
        boolean bl2 = usingSpyglass = using && (player.getActiveItem().isOf(Items.SPYGLASS) || player.isUsingSpyglass());
        if (usingSpyglass) {
            this.spyglassCooldown = 4;
        } else if (this.spyglassCooldown > 0) {
            --this.spyglassCooldown;
        }
        float rawSwing = this.suppressRejectedConsumeSwing ? 0.0f : HandRecorder.clamp(player.getHandSwingProgress(1.0f), 0.0f, 1.0f);
        boolean isOffHand = player.preferredHand == Hand.OFF_HAND;
        boolean isLeftArm = player.getMainArm() == Arm.LEFT && !isOffHand || player.getMainArm() == Arm.RIGHT && isOffHand;
        float rightSwing = player.handSwinging && !isLeftArm ? rawSwing : 0.0f;
        float leftSwing = player.handSwinging && isLeftArm ? rawSwing : 0.0f;
        data.rightSwingProgress.insert((float)tick, Float.valueOf(rightSwing));
        data.leftSwingProgress.insert((float)tick, Float.valueOf(leftSwing));
        HeldItemRendererPovAccess held = (HeldItemRendererPovAccess)client.gameRenderer.firstPersonRenderer;
        float mainEquip = HandRecorder.clamp(1.0f - held.bbsPov$getEquipProgressMainHand(), 0.0f, 1.0f);
        float offEquip = HandRecorder.clamp(1.0f - held.bbsPov$getEquipProgressOffHand(), 0.0f, 1.0f);
        if (usingSpyglass || this.spyglassCooldown > 0 && player.getMainHandStack().isOf(Items.SPYGLASS)) {
            mainEquip = 0.0f;
        }
        if (usingSpyglass || this.spyglassCooldown > 0 && player.getOffHandStack().isOf(Items.SPYGLASS)) {
            offEquip = 0.0f;
        }
        data.mainEquipProgress.insert((float)tick, Float.valueOf(mainEquip));
        data.offEquipProgress.insert((float)tick, Float.valueOf(offEquip));
        data.activeHand.insert((float)tick, (using ? (player.getActiveHand() == Hand.OFF_HAND ? 2 : 1) : 0));
        data.activeItem.insert((float)tick, (using ? player.getActiveItem().copy() : ItemStack.EMPTY));
        data.showUseParticles.insert((float)tick, using);
        data.useTime.insert((float)tick, Math.max(0, player.getItemUseTime()));
        data.bobPhase.insert((float)tick, Float.valueOf(player.horizontalSpeed));
        data.bobStrength.insert((float)tick, Float.valueOf(Math.max(0.0f, player.strideDistance)));
        data.renderYaw.insert((float)tick, Float.valueOf(player.renderYaw));
        data.renderPitch.insert((float)tick, Float.valueOf(player.renderPitch));
        this.lastRecordedTick = tick;
    }

    private static boolean hasConsumeAction(ItemStack stack) {
        UseAction action = stack.getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

