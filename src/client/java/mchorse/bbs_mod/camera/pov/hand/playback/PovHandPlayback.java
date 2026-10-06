/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.client.renderer.LivePlayerItemUse
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.settings.values.core.ValuePose
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.elements.utils.StencilMap
 *  mchorse.bbs_mod.utils.MatrixStackUtils
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  net.minecraft.client.render.item.HeldItemRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.util.Arm
 *  net.minecraft.util.Hand
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.RotationAxis
 */
package mchorse.bbs_mod.camera.pov.hand.playback;

import mchorse.bbs_mod.camera.pov.PovAddon;
import mchorse.bbs_mod.camera.pov.actions.camera.CameraShakeApplier;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.HandState;
import mchorse.bbs_mod.camera.pov.hand.PovItemPose;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.playback.HandPlaybackSession;
import mchorse.bbs_mod.camera.pov.hand.playback.HandSampler;
import mchorse.bbs_mod.camera.pov.hand.playback.HandStateApplier;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import java.util.Map;
import mchorse.bbs_mod.client.renderer.LivePlayerItemUse;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class PovHandPlayback {
    private static HandPlaybackSession active;
    private static boolean reportedFailure;
    public static boolean suppressFormTransform;

    private PovHandPlayback() {
    }

    public static boolean isSuppressFormTransform() {
        return suppressFormTransform || UIPovHandEditor.isActive() || PovHandPlayback.isActive();
    }

    public static boolean isSuppressFormTransform(Form form) {
        if (form == null || FormUtils.getRoot((Form)form) != form) {
            return false;
        }
        return PovHandPlayback.isSuppressFormTransform();
    }

    public static boolean isHandActive(float renderTransition) {
        Replay replay;
        UIFilmPanel panel;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(renderTransition);
        Object object = panel = playback == null ? PovReplaySettings.getFilmPanel() : null;
        if (playback != null && !client.options.getPerspective().isFirstPerson()) {
            return false;
        }
        if (player == null || playback == null && panel == null) {
            return false;
        }
        if (playback != null) {
            if (!((Boolean)playback.clip().hands.get()).booleanValue()) {
                return false;
            }
            replay = playback.replay();
        } else {
            boolean povEditMode;
            if (panel.getData() == null) {
                return false;
            }
            boolean inHandEditor = UIPovHandEditor.isActive();
            int povMode = panel.getController().getPovMode();
            if (!(inHandEditor || povMode != 1 && povMode != 2)) {
                return false;
            }
            boolean bl = povEditMode = povMode == 6 || inHandEditor;
            if (povEditMode) {
                Replay replay2 = replay = panel.replayEditor != null ? panel.replayEditor.getReplay() : null;
                if (replay == null && inHandEditor && UIPovHandEditor.getActive() != null) {
                    replay = UIPovHandEditor.getActive().getReplay();
                }
            } else {
                boolean isPlaying;
                float transition;
                int cursor;
                Film film = (Film)panel.getData();
                PovCameraClip clip = PovCameraClips.resolve(film, (float)(cursor = panel.getCursor()) + (transition = (isPlaying = !inHandEditor && panel.getRunner() != null && panel.getRunner().isRunning()) ? Math.max(0.0f, Math.min(1.0f, renderTransition)) : 0.0f));
                if (clip == null || !((Boolean)clip.hands.get()).booleanValue()) {
                    return false;
                }
                replay = PovCameraClips.resolveReplay(film, clip);
            }
        }
        return replay != null && replay.keyframes instanceof ReplayKeyframesPovAccess;
    }

    public static HandState resolveActiveState(float renderTransition) {
        float tick;
        Replay replay;
        UIFilmPanel panel;
        if (active != null) {
            return PovHandPlayback.active.state;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(renderTransition);
        Object object = panel = playback == null ? PovReplaySettings.getFilmPanel() : null;
        if (playback != null && !client.options.getPerspective().isFirstPerson()) {
            return null;
        }
        if (playback == null && panel == null) {
            return null;
        }
        if (playback != null) {
            if (!((Boolean)playback.clip().hands.get()).booleanValue()) {
                return null;
            }
            replay = playback.replay();
            tick = playback.replayTick();
        } else {
            float transition;
            if (panel.getData() == null) {
                return null;
            }
            int povMode = panel.getController().getPovMode();
            boolean inHandEditor = UIPovHandEditor.isActive();
            if (!(inHandEditor || povMode != 1 && povMode != 2)) {
                return null;
            }
            boolean povEditMode = povMode == 6 || inHandEditor;
            Film film = (Film)panel.getData();
            int cursor = panel.getCursor();
            boolean isPlaying = !inHandEditor && panel.getRunner() != null && panel.getRunner().isRunning();
            float f = transition = isPlaying ? Math.max(0.0f, Math.min(1.0f, renderTransition)) : 0.0f;
            if (povEditMode) {
                Replay replay2 = replay = panel.replayEditor != null ? panel.replayEditor.getReplay() : null;
                if (replay == null && inHandEditor && UIPovHandEditor.getActive() != null) {
                    replay = UIPovHandEditor.getActive().getReplay();
                }
            } else {
                PovCameraClip clip = PovCameraClips.resolve(film, (float)cursor + transition);
                if (clip == null || !((Boolean)clip.hands.get()).booleanValue()) {
                    return null;
                }
                replay = PovCameraClips.resolveReplay(film, clip);
            }
            if (replay == null) {
                return null;
            }
            tick = (float)replay.getTick(cursor) + transition;
        }
        ReplayKeyframes inHandEditor = replay.keyframes;
        if (!(inHandEditor instanceof ReplayKeyframesPovAccess)) {
            return null;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)inHandEditor;
        RecordedHandData data = access.bbsPov$getHand();
        if (data == null) {
            return null;
        }
        return HandSampler.sample(data, replay.keyframes, tick);
    }

    public static boolean begin(float renderTransition) {
        HandState state;
        float transition;
        boolean isPlaying;
        float tick;
        Replay replay;
        UIFilmPanel panel;
        if (active != null) {
            PovHandPlayback.end();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(renderTransition);
        Object object = panel = playback == null ? PovReplaySettings.getFilmPanel() : null;
        if (playback != null && !client.options.getPerspective().isFirstPerson()) {
            return false;
        }
        if (player == null || playback == null && panel == null) {
            return false;
        }
        if (playback != null) {
            if (!((Boolean)playback.clip().hands.get()).booleanValue()) {
                return false;
            }
            replay = playback.replay();
            tick = playback.replayTick();
            isPlaying = !playback.controller().paused;
            transition = isPlaying ? Math.max(0.0f, Math.min(1.0f, renderTransition)) : 0.0f;
        } else {
            boolean inHandEditor = UIPovHandEditor.isActive();
            int povMode = panel.getController().getPovMode();
            if (!(inHandEditor || povMode != 1 && povMode != 2)) {
                return false;
            }
            boolean povEditMode = povMode == 6 || inHandEditor;
            Film film = (Film)panel.getData();
            int cursor = panel.getCursor();
            isPlaying = !inHandEditor && panel.getRunner() != null && panel.getRunner().isRunning();
            float f = transition = isPlaying ? Math.max(0.0f, Math.min(1.0f, renderTransition)) : 0.0f;
            if (povEditMode) {
                Replay replay2 = replay = panel.replayEditor != null ? panel.replayEditor.getReplay() : null;
                if (replay == null && inHandEditor && UIPovHandEditor.getActive() != null) {
                    replay = UIPovHandEditor.getActive().getReplay();
                }
            } else {
                PovCameraClip clip = PovCameraClips.resolve(film, (float)cursor + transition);
                if (clip == null || !((Boolean)clip.hands.get()).booleanValue()) {
                    return false;
                }
                replay = PovCameraClips.resolveReplay(film, clip);
            }
            if (replay == null) {
                return false;
            }
            tick = (float)replay.getTick(cursor) + transition;
        }
        ReplayKeyframes povMode = replay.keyframes;
        if (!(povMode instanceof ReplayKeyframesPovAccess)) {
            return false;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)povMode;
        RecordedHandData data = access.bbsPov$getHand();
        if (UIPovHandEditor.isActive()) {
            state = HandStateApplier.createDefaultHandEditorState(data, replay);
        } else {
            HandState handState = state = data == null ? null : HandSampler.sample(data, replay.keyframes, tick);
            if (state == null) {
                return false;
            }
            HandStateApplier.inheritReplayModel(data, state, replay, panel);
        }
        if (playback != null) {
            LivePlayerItemUse.endFrame();
        }
        HandPlaybackSession next = new HandPlaybackSession(player, client.gameRenderer.firstPersonRenderer, state, data, tick, isPlaying, transition);
        try {
            next.apply(replay);
            active = next;
            if (!PovHandPicking.isStencilPass()) {
                PovHandMatrices.clear();
                PovHandPicking.clearItemBounds();
            }
            reportedFailure = false;
            return true;
        }
        catch (Throwable throwable) {
            next.restore();
            if (!reportedFailure) {
                PovAddon.LOGGER.error("Couldn't prepare BBS POV hand playback", throwable);
                reportedFailure = true;
            }
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean render(HeldItemRenderer renderer, float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light) {
        HandPlaybackSession current = active;
        if (current == null || current.player != player) {
            return false;
        }
        if (!current.state.visible) {
            consumers.draw();
            PovHandPlayback.end();
            return true;
        }
        matrices.push();
        try {
            CameraShakeApplier.apply(matrices, tickDelta);
            MatrixStackUtils.applyTransform((MatrixStack)matrices, (Transform)current.state.cameraOffset);
            renderer.renderItem(current.getItemRenderTickDelta(), matrices, consumers, player, light);
            if (PovHandPicking.isStencilPass()) {
                current.renderBodyParts(light, PovHandPicking.getStencilMap());
            } else {
                current.renderBodyParts(light);
            }
        }
        finally {
            matrices.pop();
            PovHandPlayback.end();
        }
        return true;
    }

    public static void applyBob(MatrixStack matrices, float influence) {
        HandPlaybackSession current = active;
        if (current != null && current.state != null && influence > 0.0f) {
            PovHandPlayback.applyBob(matrices, current.state, influence);
        }
    }

    public static void applyBob(MatrixStack matrices, HandState state, float influence) {
        if (state == null || influence <= 0.0f) {
            return;
        }
        float phase = -state.bobPhase;
        float strength = state.bobStrength * influence;
        float sin = MathHelper.sin((float)(phase * (float)Math.PI));
        float cos = MathHelper.cos((float)(phase * (float)Math.PI));
        matrices.translate(sin * strength * 0.5f, -Math.abs(cos * strength), 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin * strength * 3.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(MathHelper.cos((float)(phase * (float)Math.PI - 0.2f)) * strength) * 5.0f));
    }

    public static void applyTransforms(MatrixStack matrices) {
        HandPlaybackSession current = active;
        if (current != null) {
            matrices.push();
            CameraShakeApplier.apply(matrices, MinecraftClient.getInstance().getTickDelta());
            MatrixStackUtils.applyTransform((MatrixStack)matrices, (Transform)current.state.cameraOffset);
        }
    }

    public static void popTransforms(MatrixStack matrices) {
        matrices.pop();
    }

    public static float getCurrentItemRenderTickDelta(float fallback) {
        HandPlaybackSession current = active;
        return current == null ? fallback : current.getItemRenderTickDelta();
    }

    public static void end() {
        HandPlaybackSession current = active;
        active = null;
        if (current != null) {
            current.restore();
        }
    }

    public static boolean isActive() {
        return active != null;
    }

    public static HandState getActiveState() {
        return active != null ? PovHandPlayback.active.state : null;
    }

    public static boolean shouldRenderModelHand(Hand hand) {
        HandPlaybackSession current = active;
        return current == null || (hand == Hand.MAIN_HAND ? current.state.rightHandVisible : current.state.leftHandVisible);
    }

    public static boolean shouldRenderArm(Arm arm) {
        HandPlaybackSession current = active;
        return current == null || (arm == Arm.RIGHT ? current.state.rightHandVisible : current.state.leftHandVisible);
    }

    public static Pose getRenderPose() {
        HandPlaybackSession current = active;
        return current == null ? null : current.renderPose;
    }

    public static PoseTransform getItemPose(boolean leftHanded) {
        HandPlaybackSession current = active;
        if (current == null) {
            return new PoseTransform();
        }
        String bone = PovItemPose.bone(leftHanded, current.state.mainArm);
        return PovItemPose.transform(current.state.itemPose, bone);
    }

    public static String getItemBone(boolean leftHanded) {
        HandPlaybackSession current = active;
        return current == null ? null : PovItemPose.bone(leftHanded, current.state.mainArm);
    }

    public static void useCapturedPose(Pose pose) {
        HandPlaybackSession current = active;
        if (current != null && pose != null) {
            current.renderPose = pose.copy();
        }
    }

    public static void renderBodyPartsForPicking(int light, StencilMap stencilMap) {
        HandPlaybackSession current = active;
        if (current != null) {
            current.renderBodyParts(light, stencilMap);
        }
    }

    public static void renderBodyParts(int light) {
        HandPlaybackSession current = active;
        if (current != null) {
            current.renderBodyParts(light);
        }
    }

    public static float getActiveTransition() {
        HandPlaybackSession current = active;
        return current != null ? current.transition : 0.0f;
    }

    public static float getArmFix(Arm arm) {
        HandPlaybackSession current = active;
        if (current == null) {
            return 0.0f;
        }
        float maxFix = 0.0f;
        String bone = arm == Arm.RIGHT ? "right_arm" : "left_arm";
        PoseTransform pose = current.state != null ? (arm == Arm.RIGHT ? current.state.rightPose : current.state.leftPose) : null;
        if (pose != null && pose.fix > 0.0f) {
            maxFix = Math.max(maxFix, pose.fix);
        }
        if (current.state != null && current.state.pose != null) {
            maxFix = Math.max(maxFix, PovHandPlayback.getPoseFixForArm(current.state.pose, bone));
        }
        if (current.povForm != null) {
            if (current.povForm.pose.get() != null) {
                maxFix = Math.max(maxFix, PovHandPlayback.getPoseFixForArm((Pose)current.povForm.pose.get(), bone));
            }
            if (current.povForm.poseOverlay.get() != null) {
                maxFix = Math.max(maxFix, PovHandPlayback.getPoseFixForArm((Pose)current.povForm.poseOverlay.get(), bone));
            }
            for (ValuePose addOverlay : current.povForm.additionalOverlays) {
                if (addOverlay.get() == null) continue;
                maxFix = Math.max(maxFix, PovHandPlayback.getPoseFixForArm((Pose)addOverlay.get(), bone));
            }
        }
        return MathHelper.clamp((float)maxFix, (float)0.0f, (float)1.0f);
    }

    private static float getPoseFixForArm(Pose pose, String armRoot) {
        if (pose == null || pose.transforms.isEmpty()) {
            return 0.0f;
        }
        float max = 0.0f;
        for (Map.Entry entry : pose.transforms.entrySet()) {
            String name = (String)entry.getKey();
            PoseTransform pt = (PoseTransform)entry.getValue();
            if (pt == null || !(pt.fix > 0.0f) || !name.equals(armRoot) && !name.startsWith(armRoot) && !name.contains(armRoot)) continue;
            max = Math.max(max, pt.fix);
        }
        return max;
    }

    public static float getHandFix(Hand hand) {
        HandPlaybackSession current = active;
        if (current == null || current.player == null) {
            return 0.0f;
        }
        Arm arm = hand == Hand.MAIN_HAND ? current.player.getMainArm() : current.player.getMainArm().getOpposite();
        return PovHandPlayback.getArmFix(arm);
    }

    public static void applyHandAnimations(MatrixStack matrices, Hand hand) {
        HandPlaybackSession current = active;
        if (current == null || current.state == null) {
            return;
        }
        float fix = PovHandPlayback.getHandFix(hand);
        float influence = 1.0f - fix;
        if (influence <= 0.0f) {
            return;
        }
        float pitchDiff = current.state.viewPitch - current.state.renderPitch;
        float yawDiff = current.state.viewYaw - current.state.renderYaw;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDiff * 0.1f * influence));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDiff * 0.1f * influence));
        PovHandPlayback.applyBob(matrices, current.state, influence);
    }

    static {
        suppressFormTransform = false;
    }
}

