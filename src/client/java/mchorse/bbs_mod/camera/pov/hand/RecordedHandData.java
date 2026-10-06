/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSSettings
 *  mchorse.bbs_mod.film.replays.FormProperties
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.film.replays.tracks.TrackId
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.BodyPart
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.core.ValueForm
 *  mchorse.bbs_mod.settings.values.core.ValueGroup
 *  mchorse.bbs_mod.settings.values.core.ValuePose
 *  mchorse.bbs_mod.settings.values.core.ValueTransform
 *  mchorse.bbs_mod.settings.values.ui.ValueStringKeys
 *  mchorse.bbs_mod.utils.colors.Color
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.hand;

import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneUtils;
import mchorse.bbs_mod.camera.pov.hand.recording.HandRecorder;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

public final class RecordedHandData {
    public static final String DEFAULT_MODEL = "player/steve";
    public static final Link DEFAULT_TEXTURE = Link.assets((String)"models/player/steve/steve.png");
    public final ValueForm baseForm = new ValueForm("pov_base_form");
    public final KeyframeChannel<Boolean> visible = RecordedHandData.channel("visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<String> model = RecordedHandData.channel("model", KeyframeFactories.STRING);
    public final KeyframeChannel<Link> texture = RecordedHandData.channel("texture", KeyframeFactories.LINK);
    public final KeyframeChannel<Color> color = RecordedHandData.channel("color", KeyframeFactories.COLOR);
    public final KeyframeChannel<Color> colorOverlay = RecordedHandData.channel("color_overlay", KeyframeFactories.COLOR);
    public final KeyframeChannel<Transform> cameraOffset = RecordedHandData.channel("camera_offset", KeyframeFactories.TRANSFORM);
    public final KeyframeChannel<Pose> pose = RecordedHandData.channel("pose", KeyframeFactories.POSE);
    public final KeyframeChannel<Pose> itemPose = RecordedHandData.channel("item_pose", KeyframeFactories.POSE);
    public final KeyframeChannel<Boolean> rightHandVisible = RecordedHandData.channel("right_hand_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> leftHandVisible = RecordedHandData.channel("left_hand_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<PoseTransform> rightPose = RecordedHandData.channel("right_pose", KeyframeFactories.POSE_TRANSFORM);
    public final KeyframeChannel<PoseTransform> leftPose = RecordedHandData.channel("left_pose", KeyframeFactories.POSE_TRANSFORM);
    public final FormProperties poseTracks = new FormProperties("pov_hand_pose_tracks");
    public final FormProperties bodyPartTracks = new FormProperties("pov_bodypart_tracks");
    public final KeyframeChannel<Float> rightSwingProgress = RecordedHandData.channel("right_swing_progress", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> leftSwingProgress = RecordedHandData.channel("left_swing_progress", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> mainEquipProgress = RecordedHandData.channel("main_equip", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> offEquipProgress = RecordedHandData.channel("off_equip", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Integer> activeHand = RecordedHandData.channel("active_hand", KeyframeFactories.INTEGER);
    public final KeyframeChannel<ItemStack> activeItem = RecordedHandData.channel("active_item", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<Boolean> showUseParticles = RecordedHandData.channel("show_use_particles", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> useTime = RecordedHandData.channel("use_time", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Float> bobPhase = RecordedHandData.channel("bob_phase", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> bobStrength = RecordedHandData.channel("bob_strength", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> renderYaw = RecordedHandData.channel("render_yaw", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> renderPitch = RecordedHandData.channel("render_pitch", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Boolean> mainArm = RecordedHandData.channel("main_arm", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> worldInteraction = RecordedHandData.channel("world_interaction", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> replayInteraction = RecordedHandData.channel("replay_interaction", KeyframeFactories.BOOLEAN);
    public final ValueStringKeys disabledTracks = new ValueStringKeys("pov_disabled_tracks");
    public final HandRecorder recording = new HandRecorder();

    private static <T> KeyframeChannel<T> channel(String id, IKeyframeFactory<T> factory) {
        return new KeyframeChannel("pov_hand_" + id, factory);
    }

    public void addTo(ValueGroup group) {
        group.add((BaseValue)this.baseForm);
        group.add((BaseValue)this.disabledTracks);
        for (KeyframeChannel<?> channel : this.getOwnedChannels()) {
            group.add(channel);
        }
        group.add((BaseValue)this.poseTracks);
        group.add((BaseValue)this.bodyPartTracks);
    }

    public void initializeReplayPreset() {
        this.insertPreset();
    }

    private void insertPreset() {
        this.recording.reset();
        RecordedHandData.reset(this.rightSwingProgress, Float.valueOf(0.0f));
        RecordedHandData.reset(this.leftSwingProgress, Float.valueOf(0.0f));
        RecordedHandData.reset(this.mainEquipProgress, Float.valueOf(0.0f));
        RecordedHandData.reset(this.offEquipProgress, Float.valueOf(0.0f));
        RecordedHandData.reset(this.activeHand, 0);
        RecordedHandData.reset(this.activeItem, ItemStack.EMPTY);
        RecordedHandData.reset(this.showUseParticles, true);
        RecordedHandData.reset(this.useTime, 0);
        RecordedHandData.reset(this.bobPhase, Float.valueOf(0.0f));
        RecordedHandData.reset(this.bobStrength, Float.valueOf(0.0f));
    }

    public boolean hasRecordedData() {
        return this.recording.hasRecorded();
    }

    public void addRecordingEndKeyframes(ReplayKeyframes replay, int endTick) {
        int tick = Math.max(0, endTick);
        for (KeyframeChannel<?> channel : this.getRecordedChannels()) {
            RecordedHandData.addEndKeyframeUntyped(channel, tick);
        }
    }

    public KeyframeChannel<PoseTransform> getOrCreatePoseTrack(String bone, HandBoneUtils.HandBones bones) {
        if (bone.equals(bones.mainRoot())) {
            return this.rightPose;
        }
        if (bone.equals(bones.offRoot())) {
            return this.leftPose;
        }
        return this.poseTracks.register(TrackId.bone((String)"", (String)bone), (IKeyframeFactory)KeyframeFactories.POSE_TRANSFORM);
    }

    public KeyframeChannel<Pose> getOrCreatePoseOverlay(ModelForm handForm, String key) {
        if (handForm == null || key == null || key.isBlank()) {
            return null;
        }
        return this.poseTracks.getOrCreate((Form)handForm, key);
    }

    public void applyPoseTracks(ModelForm form, float tick) {
        form.pose.setRuntimeValue(null);
        form.poseOverlay.setRuntimeValue(null);
        for (ValuePose overlay : form.additionalOverlays) {
            overlay.setRuntimeValue(null);
        }
        this.poseTracks.applyProperties((Form)form, tick);
    }

    public KeyframeChannel<?> getOrCreateBodyPartTrack(ModelForm root, String partId, String property) {
        if (root == null || partId == null || partId.isBlank() || property == null || property.isBlank()) {
            return null;
        }
        return this.bodyPartTracks.getOrCreate((Form)root, TrackId.property((String)partId, (String)property));
    }

    public KeyframeChannel<?> getOrCreateBodyPartTrack(ModelForm root, int partIndex, String property) {
        if (root == null || partIndex < 0 || root.parts.getAllTyped().size() <= partIndex) {
            return null;
        }
        BodyPart part = (BodyPart)root.parts.getAllTyped().get(partIndex);
        return this.getOrCreateBodyPartTrack(root, part.getId(), property);
    }

    public void applyBodyPartTracks(ModelForm form, float tick) {
        this.bodyPartTracks.applyProperties((Form)form, tick);
    }

    public KeyframeChannel<?>[] getOwnedChannels() {
        return new KeyframeChannel[]{this.visible, this.model, this.texture, this.color, this.colorOverlay, this.cameraOffset, this.pose, this.itemPose, this.rightHandVisible, this.leftHandVisible, this.rightPose, this.leftPose, this.rightSwingProgress, this.leftSwingProgress, this.mainEquipProgress, this.offEquipProgress, this.activeHand, this.activeItem, this.showUseParticles, this.useTime, this.bobPhase, this.bobStrength, this.renderYaw, this.renderPitch, this.mainArm, this.worldInteraction, this.replayInteraction};
    }

    private KeyframeChannel<?>[] getRecordedChannels() {
        return new KeyframeChannel[]{this.rightSwingProgress, this.leftSwingProgress, this.mainEquipProgress, this.offEquipProgress, this.activeHand, this.activeItem, this.showUseParticles, this.useTime, this.bobPhase, this.bobStrength, this.renderYaw, this.renderPitch};
    }

    private static void addEndKeyframeUntyped(KeyframeChannel<?> channel, int tick) {
        KeyframeChannel<?> raw = channel;
        Object value = raw.interpolate((float)tick, null);
        if (value == null) {
            return;
        }
        if (value instanceof Pose) {
            Pose pose = (Pose)value;
            value = pose.copy();
        } else if (value instanceof Transform) {
            Transform transform = (Transform)value;
            value = transform.copy();
        } else if (value instanceof Color) {
            Color color = (Color)value;
            value = color.copy();
        }
        ((KeyframeChannel)channel).insert((float)tick, value);
    }

    private static <T> void reset(KeyframeChannel<T> channel, T value) {
        channel.insert(0.0f, value);
    }

    public Form ensureBaseForm(Form replayForm) {
        Form value = (Form)this.baseForm.get();
        if (value != null) {
            RecordedHandData.ensureOverlays(value);
        }
        return value != null ? value : replayForm;
    }

    public static void ensureOverlays(Form form) {
        if (form == null) {
            return;
        }
        int additionalTransforms = (Integer)BBSSettings.recordingTransformOverlays.get();
        while (form.additionalTransforms.size() < additionalTransforms) {
            int idx = form.additionalTransforms.size();
            ValueTransform vt = new ValueTransform("transform_overlay" + idx, new Transform());
            form.additionalTransforms.add(vt);
            form.add((BaseValue)vt);
        }
        if (form instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)form;
            modelForm.boneTracks.set(true);
            int additionalPoses = (Integer)BBSSettings.recordingPoseOverlays.get();
            while (modelForm.additionalOverlays.size() < additionalPoses) {
                int idx = modelForm.additionalOverlays.size();
                ValuePose vp = new ValuePose("pose_overlay" + idx, new Pose());
                modelForm.additionalOverlays.add(vp);
                modelForm.add((BaseValue)vp);
            }
        }
        for (BodyPart part : form.parts.getAllTyped()) {
            if (part.getForm() == null) continue;
            RecordedHandData.ensureOverlays(part.getForm());
        }
    }

    public Pose getBasePose() {
        Form root;
        Form value = (Form)this.baseForm.get();
        if (value != null && (root = FormUtils.getRoot((Form)value)) instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)root;
            if (modelForm.pose.get() != null) {
                return (Pose)modelForm.pose.get();
            }
        }
        return null;
    }
}

