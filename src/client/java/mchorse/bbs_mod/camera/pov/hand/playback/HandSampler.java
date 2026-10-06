/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.utils.colors.Color
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 *  net.minecraft.util.Arm
 *  net.minecraft.util.Hand
 */
package mchorse.bbs_mod.camera.pov.hand.playback;

import mchorse.bbs_mod.camera.pov.hand.HandState;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

public final class HandSampler {
    private HandSampler() {
    }

    public static HandState sample(RecordedHandData data, ReplayKeyframes replay, float tick) {
        HandState state = new HandState();
        Pose basePose = data.getBasePose();
        Pose defaultPose = basePose != null ? basePose.copy() : new Pose();
        state.visible = (Boolean)data.visible.interpolate(tick, true);
        state.model = (String)data.model.interpolate(tick, null);
        state.texture = (Link)data.texture.interpolate(tick, null);
        state.color = data.color.isEmpty() ? null : (Color)data.color.interpolate(tick, null);
        state.colorOverlay = data.colorOverlay.isEmpty() ? null : (Color)data.colorOverlay.interpolate(tick, null);
        state.cameraOffset.copy((Transform)data.cameraOffset.interpolate(tick, new Transform()));
        state.pose.copy((Pose)data.pose.interpolate(tick, defaultPose));
        state.itemPose.copy((Pose)data.itemPose.interpolate(tick, defaultPose));
        state.rightHandVisible = (Boolean)data.rightHandVisible.interpolate(tick, true);
        state.leftHandVisible = (Boolean)data.leftHandVisible.interpolate(tick, false);
        PoseTransform defaultRight = basePose != null && basePose.get("right_arm") != null ? HandSampler.copyPoseTransform((Transform)basePose.get("right_arm")) : new PoseTransform();
        PoseTransform defaultLeft = basePose != null && basePose.get("left_arm") != null ? HandSampler.copyPoseTransform((Transform)basePose.get("left_arm")) : new PoseTransform();
        state.rightPose.copy((Transform)data.rightPose.interpolate(tick, defaultRight));
        state.leftPose.copy((Transform)data.leftPose.interpolate(tick, defaultLeft));
        state.mainHand = HandSampler.copyItem(replay.getMainHandStack(tick));
        state.offHand = HandSampler.copyItem((ItemStack)replay.offHand.interpolate(tick, ItemStack.EMPTY));
        state.previousRightSwingProgress = state.rightSwingProgress = HandSampler.sampleSwingProgress(data.rightSwingProgress, tick);
        state.previousLeftSwingProgress = state.leftSwingProgress = HandSampler.sampleSwingProgress(data.leftSwingProgress, tick);
        state.mainEquipProgress = HandSampler.clamp(((Float)data.mainEquipProgress.interpolate(tick, Float.valueOf(0.0f))).floatValue(), 0.0f, 1.0f);
        state.previousMainEquipProgress = HandSampler.clamp(((Float)data.mainEquipProgress.interpolate(tick - 1.0f, Float.valueOf(state.mainEquipProgress))).floatValue(), 0.0f, 1.0f);
        state.offEquipProgress = HandSampler.clamp(((Float)data.offEquipProgress.interpolate(tick, Float.valueOf(0.0f))).floatValue(), 0.0f, 1.0f);
        state.previousOffEquipProgress = HandSampler.clamp(((Float)data.offEquipProgress.interpolate(tick - 1.0f, Float.valueOf(state.offEquipProgress))).floatValue(), 0.0f, 1.0f);
        int wholeTick = (int)Math.floor(tick);
        int active = (Integer)data.activeHand.interpolate((float)wholeTick, 0);
        state.usingItem = active != 0;
        state.activeHand = active == 2 ? Hand.OFF_HAND : Hand.MAIN_HAND;
        state.activeItem = HandSampler.copyItem((ItemStack)data.activeItem.interpolate((float)wholeTick, ItemStack.EMPTY));
        state.showUseParticles = (Boolean)data.showUseParticles.interpolate(tick, true);
        state.useTime = HandSampler.sampleUseTime(data, tick, active);
        state.bobPhase = ((Float)data.bobPhase.interpolate(tick, Float.valueOf(0.0f))).floatValue();
        state.previousBobPhase = ((Float)data.bobPhase.interpolate(tick - 1.0f, Float.valueOf(state.bobPhase))).floatValue();
        state.bobStrength = Math.max(0.0f, ((Float)data.bobStrength.interpolate(tick, Float.valueOf(0.0f))).floatValue());
        state.previousBobStrength = Math.max(0.0f, ((Float)data.bobStrength.interpolate(tick - 1.0f, Float.valueOf(state.bobStrength))).floatValue());
        state.mainArm = (Boolean)data.mainArm.interpolate(tick, false) != false ? Arm.LEFT : Arm.RIGHT;
        state.viewYaw = ((Double)replay.yaw.interpolate(tick, 0.0)).floatValue();
        state.previousViewYaw = ((Double)replay.yaw.interpolate(tick - 1.0f, (double)state.viewYaw)).floatValue();
        state.viewPitch = ((Double)replay.pitch.interpolate(tick, 0.0)).floatValue();
        state.previousViewPitch = ((Double)replay.pitch.interpolate(tick - 1.0f, (double)state.viewPitch)).floatValue();
        state.renderYaw = ((Float)data.renderYaw.interpolate(tick, Float.valueOf(state.viewYaw))).floatValue();
        state.previousRenderYaw = ((Float)data.renderYaw.interpolate(tick - 1.0f, Float.valueOf(state.renderYaw))).floatValue();
        state.renderPitch = ((Float)data.renderPitch.interpolate(tick, Float.valueOf(state.viewPitch))).floatValue();
        state.previousRenderPitch = ((Float)data.renderPitch.interpolate(tick - 1.0f, Float.valueOf(state.renderPitch))).floatValue();
        state.worldInteraction = (Boolean)data.worldInteraction.interpolate(tick, false);
        state.replayInteraction = (Boolean)data.replayInteraction.interpolate(tick, false);
        return state;
    }

    private static float sampleSwingProgress(KeyframeChannel<Float> channel, float tick) {
        int wholeTick = (int)Math.floor(tick);
        float transition = tick - (float)wholeTick;
        float current = HandSampler.clamp(((Float)channel.interpolate((float)wholeTick, Float.valueOf(0.0f))).floatValue(), 0.0f, 1.0f);
        if (transition <= 0.0f) {
            return current;
        }
        float next = HandSampler.clamp(((Float)channel.interpolate((float)wholeTick + 1.0f, Float.valueOf(current))).floatValue(), 0.0f, 1.0f);
        if (current > 0.0f && next < current) {
            float unwrapped = current + (next + 1.0f - current) * transition;
            return unwrapped >= 1.0f ? unwrapped - 1.0f : unwrapped;
        }
        return HandSampler.clamp(((Float)channel.interpolate(tick, Float.valueOf(current))).floatValue(), 0.0f, 1.0f);
    }

    private static float sampleUseTime(RecordedHandData data, float tick, int active) {
        if (active == 0) {
            return 0.0f;
        }
        int wholeTick = (int)Math.floor(tick);
        float transition = tick - (float)wholeTick;
        int current = Math.max(0, (Integer)data.useTime.interpolate((float)wholeTick, 0));
        if (transition <= 0.0f) {
            return current;
        }
        int nextActive = (Integer)data.activeHand.interpolate((float)wholeTick + 1.0f, 0);
        int next = Math.max(0, (Integer)data.useTime.interpolate((float)wholeTick + 1.0f, current));
        if (nextActive != active) {
            return current;
        }
        if (next < current) {
            return (float)current + (float)(next - current) * transition;
        }
        if (next - current > 2) {
            return current;
        }
        return (float)current + (float)(next - current) * transition;
    }

    private static ItemStack copyItem(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static PoseTransform copyPoseTransform(Transform source) {
        PoseTransform copy = new PoseTransform();
        if (source != null) {
            copy.copy(source);
        }
        return copy;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

