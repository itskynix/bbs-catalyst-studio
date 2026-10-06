/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.BBSSettings
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.film.replays.tracks.TrackCatalog
 *  mchorse.bbs_mod.film.replays.tracks.TrackId
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.settings.values.base.BaseValueBasic
 *  mchorse.bbs_mod.settings.values.core.ValueTransform
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysEditor
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory
 *  mchorse.bbs_mod.ui.utils.Area
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.colors.Color
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package mchorse.bbs_mod.camera.pov.editor.section;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.editor.section.PovEditorSection;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneUtils;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.LinkedHashMap;
import java.util.Map;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class HandEditorSection
implements PovEditorSection {
    public static final HandEditorSection INSTANCE = new HandEditorSection();

    private HandEditorSection() {
    }

    @Override
    public void fillSheets(UIPovEditor editor, boolean resetView) {
        ReplayKeyframesPovAccess access;
        RecordedHandData hand;
        Replay replay = editor.getReplay();
        if (replay == null) {
            return;
        }
        ModelForm handForm = editor.getHandEditorForm();
        ReplayKeyframes replayKeyframes = replay.keyframes;
        if (replayKeyframes instanceof ReplayKeyframesPovAccess && (hand = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHand()) != null) {
            RecordedHandData.ensureOverlays((Form)handForm);
            handForm.model.set(this.currentModel(editor, hand));
            handForm.texture.set(this.currentTexture(editor, hand));
            handForm.boneTracks.set(true);
            ModelForm baseForm = this.baseModelForm(editor, hand);
            if (!hand.color.isEmpty()) {
                handForm.color.set(((Color)hand.color.interpolate((float)editor.getReplayTick(), Color.white())).copy());
            } else if (baseForm != null && baseForm.color.get() != null) {
                handForm.color.set(((Color)baseForm.color.get()).copy());
            } else {
                handForm.color.set(Color.white());
            }
            if (!hand.colorOverlay.isEmpty()) {
                handForm.overlayColor.set(((Color)hand.colorOverlay.interpolate((float)editor.getReplayTick(), new Color(1.0f, 1.0f, 1.0f, 0.0f))).copy());
            } else if (baseForm != null && baseForm.overlayColor.get() != null) {
                handForm.overlayColor.set(((Color)baseForm.overlayColor.get()).copy());
            } else {
                handForm.overlayColor.set(new Color(1.0f, 1.0f, 1.0f, 0.0f));
            }
            if (baseForm != null && baseForm.pose.get() != null) {
                handForm.pose.set(((Pose)baseForm.pose.get()).copy());
            } else {
                handForm.pose.set(new Pose());
            }
            UIKeyframeSheet visibleSheet = editor.addSheetWithColor("Visible", hand.visible, Icons.VISIBLE, UIReplaysEditor.getColor((String)"visible"), (BaseValueBasic)handForm.visible, () -> hand.visible.interpolate((float)editor.getReplayTick(), true));
            visibleSheet.form((Form)handForm);
            UIKeyframeSheet modelSheet = editor.addSheetWithColor("Model", hand.model, Icons.MORPH, UIReplaysEditor.getColor((String)"model"), (BaseValueBasic)handForm.model, () -> this.currentModel(editor, hand));
            modelSheet.form((Form)handForm);
            UIKeyframeSheet textureSheet = editor.addSheetWithColor("Texture", hand.texture, Icons.IMAGE, UIReplaysEditor.getColor((String)"texture"), (BaseValueBasic)handForm.texture, () -> this.currentTexture(editor, hand));
            textureSheet.form((Form)handForm);
            UIKeyframeSheet colorSheet = editor.addSheetWithColor("Color", hand.color, Icons.COLOR, UIReplaysEditor.getColor((String)"color"), (BaseValueBasic)handForm.color, () -> this.currentColor(editor, hand));
            colorSheet.form((Form)handForm);
            UIKeyframeSheet colorOverlaySheet = editor.createSheetWithColor("Color Overlay", hand.colorOverlay, Icons.COLOR, UIReplaysEditor.getColor((String)"color_overlay"), (BaseValueBasic)handForm.overlayColor, () -> TrackCatalog.opaqueOverlaySeed((Color)this.currentColorOverlay(editor, hand)));
            colorOverlaySheet.form((Form)handForm);
            UIKeyframeSheet cameraOffsetSheet = editor.addSheetWithColor("Camera Offset", hand.cameraOffset, Icons.LAYOUT, UIReplaysEditor.getColor((String)"transform"), () -> ((Transform)hand.cameraOffset.interpolate((float)editor.getReplayTick(), new Transform())).copy());
            cameraOffsetSheet.form((Form)handForm);
            Pose defaultPose = handForm.pose.get() != null ? (Pose)handForm.pose.get() : new Pose();
            UIKeyframeSheet poseSheet = editor.createSheetWithColor("Pose", hand.pose, Icons.POSE, UIReplaysEditor.getColor((String)"pose"), (BaseValueBasic)handForm.pose, () -> ((Pose)hand.pose.interpolate((float)editor.getReplayTick(), defaultPose)).copy());
            poseSheet.form((Form)handForm);
            if (((Boolean)BBSSettings.recordingOverlays.get()).booleanValue()) {
                String overlayKey = "pose_overlay";
                KeyframeChannel<Pose> overlayChannel = hand.getOrCreatePoseOverlay(handForm, overlayKey);
                if (overlayChannel != null) {
                    UIKeyframeSheet overlaySheet = new UIKeyframeSheet(overlayKey, IKey.constant((String)overlayKey), UIReplaysEditor.getColor((String)overlayKey), overlayChannel, (BaseValueBasic)handForm.poseOverlay);
                    overlaySheet.icon(Icons.POSE).form((Form)handForm);
                    overlaySheet.seed(() -> ((Pose)overlayChannel.interpolate((float)editor.getReplayTick(), new Pose())).copy());
                    overlaySheet.setParent(poseSheet);
                    editor.addPendingSheet(overlaySheet);
                }
                int additional = (Integer)BBSSettings.recordingPoseOverlays.get();
                for (int k = 0; k < additional; ++k) {
                    String addKey = "pose_overlay" + k;
                    KeyframeChannel<Pose> addChannel = hand.getOrCreatePoseOverlay(handForm, addKey);
                    BaseValueBasic baseValueBasic = FormUtils.getProperty((Form)handForm, (String)addKey);
                    if (addChannel == null || baseValueBasic == null) continue;
                    UIKeyframeSheet addSheet = new UIKeyframeSheet(addKey, IKey.constant((String)addKey), UIReplaysEditor.getColor((String)addKey), (KeyframeChannel)addChannel, baseValueBasic);
                    addSheet.icon(Icons.POSE).form((Form)handForm);
                    addSheet.seed(() -> ((Pose)addChannel.interpolate((float)editor.getReplayTick(), new Pose())).copy());
                    addSheet.setParent(poseSheet);
                    editor.addPendingSheet(addSheet);
                }
            }
            int color = 0;
            String currentHandModel = this.currentModel(editor, hand);
            handForm.model.set(currentHandModel);
            ModelInstance model = ModelFormRenderer.getModel((ModelForm)handForm);
            if ((model == null || model.getModel() == null) && baseForm != null) {
                model = ModelFormRenderer.getModel((ModelForm)baseForm);
            }
            if (!(model != null && model.getModel() != null || BBSModClient.getModels() == null || (model = BBSModClient.getModels().getModel(currentHandModel)) != null && model.getModel() != null)) {
                model = BBSModClient.getModels().getModel("player/steve");
            }
            HandBoneUtils.HandBones handBones = HandBoneUtils.collect(model);
            LinkedHashMap<String, UIKeyframeSheet> handBoneSheets = new LinkedHashMap<String, UIKeyframeSheet>();
            for (Map.Entry entry : handBones.depths().entrySet()) {
                String bone = (String)entry.getKey();
                KeyframeChannel<PoseTransform> channel = hand.getOrCreatePoseTrack(bone, handBones);
                String sheetId = TrackId.bone((String)"", (String)bone).toKey();
                UIKeyframeSheet boneSheet = this.createBoneSheet(editor, sheetId, bone, channel, color++);
                if (model != null && model.getModel() != null) {
                    String parentBone = model.getModel().getParentGroupKey(bone);
                    UIKeyframeSheet parentSheet = (UIKeyframeSheet)handBoneSheets.get(parentBone);
                    boneSheet.setParent(parentSheet != null ? parentSheet : poseSheet);
                } else {
                    boneSheet.setParent(poseSheet);
                }
                handBoneSheets.put(bone, boneSheet);
            }
            UIKeyframeSheet itemPoseSheet = editor.createSheetWithColor("Item Pose", hand.itemPose, Icons.BLOCK, UIReplaysEditor.getColor((String)"pose"), (BaseValueBasic)handForm.pose, () -> ((Pose)hand.itemPose.interpolate((float)editor.getReplayTick(), defaultPose)).copy());
            itemPoseSheet.form((Form)handForm);
            editor.addSheetWithColor("World Interaction", hand.worldInteraction, Icons.BLOCK, UIReplaysEditor.getColor((String)"world_interaction"), () -> hand.worldInteraction.interpolate((float)editor.getReplayTick(), false));
            editor.addSheetWithColor("Replay Interaction", hand.replayInteraction, Icons.FILM, UIReplaysEditor.getColor((String)"replay_interaction"), () -> hand.replayInteraction.interpolate((float)editor.getReplayTick(), false));
            editor.addSheetWithColor("Right Hand Visible", hand.rightHandVisible, Icons.VISIBLE, UIReplaysEditor.getColor((String)"visible"), () -> hand.rightHandVisible.interpolate((float)editor.getReplayTick(), true));
            editor.addSheetWithColor("Left Hand Visible", hand.leftHandVisible, Icons.VISIBLE, UIReplaysEditor.getColor((String)"visible"), () -> hand.leftHandVisible.interpolate((float)editor.getReplayTick(), false));
            color = editor.addSheet("Off Hand Item", replay.keyframes.offHand, Icons.BLOCK, color);
            color = editor.addSheet("Right Swing", hand.rightSwingProgress, Icons.MAIN_HANDLE, color);
            color = editor.addSheet("Left Swing", hand.leftSwingProgress, Icons.LEFT_HANDLE, color);
            color = editor.addSheet("Main Equip", hand.mainEquipProgress, Icons.MAIN_HANDLE, color);
            color = editor.addSheet("Offhand Equip", hand.offEquipProgress, Icons.LEFT_HANDLE, color);
            color = editor.addSheet("Active Use Hand", hand.activeHand, Icons.POINTER, color);
            color = editor.addSheet("Active Use Item", hand.activeItem, Icons.BLOCK, color);
            color = editor.addSheet("Show Particles", hand.showUseParticles, Icons.BUBBLE, color);
            color = editor.addSheet("Use Time", hand.useTime, Icons.TIME, color);
            color = editor.addSheet("Bob Phase", hand.bobPhase, Icons.CURVES, color);
            color = editor.addSheet("Bob Strength", hand.bobStrength, Icons.CURVES, color);
            color = editor.addSheet("Render Yaw", hand.renderYaw, Icons.SPHERE, color);
            color = editor.addSheet("Render Pitch", hand.renderPitch, Icons.SPHERE, color);
            editor.addSheet("Left-handed Main Arm", hand.mainArm, Icons.LIMB, color);
        }
    }

    public boolean pick(UIPovEditor editor, UIContext context, Area viewport) {
        KeyframeChannel overlay;
        String bone;
        String itemBone;
        if (!editor.isVisible() || !editor.isHandSection() || editor.getReplay() == null) {
            return false;
        }
        ReplayKeyframes replayKeyframes = editor.getReplay().keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return false;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedHandData hand = access.bbsPov$getHand();
        if (hand == null) {
            return false;
        }
        String string = itemBone = context.mouseButton == 0 && !hand.itemPose.isEmpty() ? PovHandPicking.pickItem(context, viewport) : null;
        if (itemBone != null) {
            editor.selectClosestKeyframe(hand.itemPose);
            UIKeyframeFactory uIKeyframeFactory = editor.keyframeEditor.editor;
            if (uIKeyframeFactory instanceof UIPoseKeyframeFactory) {
                UIPoseKeyframeFactory poseFactory = (UIPoseKeyframeFactory)uIKeyframeFactory;
                poseFactory.poseEditor.selectBone(itemBone);
            }
            return true;
        }
        String string2 = bone = context.mouseButton == 0 ? PovHandPicking.getPickedBone() : null;
        if (bone == null) {
            return false;
        }
        KeyframeChannel activeOverlay = null;
        int additional = (Integer)BBSSettings.recordingPoseOverlays.get();
        for (int k = additional - 1; k >= 0; --k) {
            KeyframeChannel add = hand.poseTracks.get(TrackId.property((String)"", (String)("pose_overlay" + k)));
            if (add == null || add.isEmpty()) continue;
            activeOverlay = add;
            break;
        }
        if (activeOverlay == null && (overlay = hand.poseTracks.get(TrackId.property((String)"", (String)"pose_overlay"))) != null && !overlay.isEmpty()) {
            activeOverlay = overlay;
        }
        KeyframeChannel targetPoseChannel = activeOverlay != null ? activeOverlay : hand.pose;
        editor.selectClosestKeyframe(targetPoseChannel);
        UIKeyframeFactory uIKeyframeFactory = editor.keyframeEditor.editor;
        if (uIKeyframeFactory instanceof UIPoseKeyframeFactory) {
            UIPoseKeyframeFactory poseFactory = (UIPoseKeyframeFactory)uIKeyframeFactory;
            poseFactory.poseEditor.selectBone(bone);
        }
        return true;
    }

    public void reloadModel(UIPovEditor editor) {
        ReplayKeyframesPovAccess access;
        RecordedHandData hand;
        ReplayKeyframes replayKeyframes;
        if (editor.getReplay() != null && (replayKeyframes = editor.getReplay().keyframes) instanceof ReplayKeyframesPovAccess && (hand = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHand()) != null) {
            editor.getHandEditorForm().model.set(this.currentModel(editor, hand));
            editor.getHandEditorForm().texture.set(this.currentTexture(editor, hand));
            ModelForm baseForm = this.baseModelForm(editor, hand);
            if (baseForm != null && baseForm.pose.get() != null) {
                editor.getHandEditorForm().pose.set(((Pose)baseForm.pose.get()).copy());
            }
        }
        if (editor.isHandSection()) {
            editor.refreshSheets(false);
        }
    }

    public String currentModel(UIPovEditor editor, RecordedHandData hand) {
        ModelForm baseForm;
        String model = (String)hand.model.interpolate((float)editor.getReplayTick(), null);
        if ((model == null || model.isBlank()) && (baseForm = this.baseModelForm(editor, hand)) != null) {
            model = (String)baseForm.model.get();
        }
        return model == null || model.isBlank() ? "player/steve" : model;
    }

    public Link currentTexture(UIPovEditor editor, RecordedHandData hand) {
        Link baseTexture;
        Link authored = (Link)hand.texture.interpolate((float)editor.getReplayTick(), null);
        if (authored != null) {
            return authored;
        }
        ModelForm baseForm = this.baseModelForm(editor, hand);
        String selectedModel = this.currentModel(editor, hand);
        if (baseForm != null && selectedModel.equals(baseForm.model.get()) && (baseTexture = (Link)baseForm.texture.get()) != null) {
            return baseTexture;
        }
        editor.getHandEditorForm().model.set(this.currentModel(editor, hand));
        ModelInstance model = ModelFormRenderer.getModel((ModelForm)editor.getHandEditorForm());
        Link texture = model == null ? null : model.getTexture();
        return texture == null ? RecordedHandData.DEFAULT_TEXTURE : texture;
    }

    public Color currentColor(UIPovEditor editor, RecordedHandData hand) {
        Color authored;
        if (hand != null && !hand.color.isEmpty() && (authored = (Color)hand.color.interpolate((float)editor.getReplayTick(), null)) != null) {
            return authored.copy();
        }
        ModelForm baseForm = this.baseModelForm(editor, hand);
        if (baseForm != null && baseForm.color.get() != null) {
            return ((Color)baseForm.color.get()).copy();
        }
        return Color.white();
    }

    public Color currentColorOverlay(UIPovEditor editor, RecordedHandData hand) {
        Color authored;
        if (hand != null && !hand.colorOverlay.isEmpty() && (authored = (Color)hand.colorOverlay.interpolate((float)editor.getReplayTick(), null)) != null) {
            return authored.copy();
        }
        ModelForm baseForm = this.baseModelForm(editor, hand);
        if (baseForm != null && baseForm.overlayColor.get() != null) {
            return ((Color)baseForm.overlayColor.get()).copy();
        }
        return new Color(1.0f, 1.0f, 1.0f, 0.0f);
    }

    public ModelForm baseModelForm(UIPovEditor editor, RecordedHandData hand) {
        Form root;
        if (hand != null && hand.baseForm.get() != null && (root = FormUtils.getRoot((Form)((Form)hand.baseForm.get()))) instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)root;
            if (modelForm.model.get() != null && !((String)modelForm.model.get()).isBlank()) {
                return modelForm;
            }
        }
        return this.replayModelForm(editor);
    }

    private ModelForm replayModelForm(UIPovEditor editor) {
        Form root;
        Form form;
        block7: {
            block6: {
                IEntity entity = editor.getFilmPanel().getController().getCurrentEntity();
                Form form2 = form = entity == null ? null : entity.getForm();
                if (form == null) break block6;
                if (!(form instanceof ModelForm)) break block7;
                ModelForm mf = (ModelForm)form;
                if (mf.model.get() != null && !((String)mf.model.get()).isBlank()) break block7;
            }
            if (editor.getReplay() != null) {
                form = (Form)editor.getReplay().form.get();
            }
        }
        Form form3 = root = form == null ? null : FormUtils.getRoot((Form)form);
        if (root instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)root;
            if (modelForm.model.get() != null && !((String)modelForm.model.get()).isBlank()) {
                return modelForm;
            }
        }
        return null;
    }

    public UIKeyframeSheet createBoneSheet(UIPovEditor editor, String sheetId, String bone, KeyframeChannel<PoseTransform> channel, int colorIndex) {
        ModelForm handForm = editor.getHandEditorForm();
        int color = UIKeyframeEditor.COLORS[colorIndex % UIKeyframeEditor.COLORS.length];
        PoseTransform defaultBoneTransform = handForm.pose.get() != null && ((Pose)handForm.pose.get()).get(bone) != null ? HandEditorSection.copyPose(((Pose)handForm.pose.get()).get(bone)) : new PoseTransform();
        ValueTransform property = new ValueTransform(sheetId, (Transform)HandEditorSection.copyPose(defaultBoneTransform));
        UIKeyframeSheet sheet = new UIKeyframeSheet(sheetId, IKey.constant((String)bone), color, channel, (BaseValueBasic)property, true);
        sheet.icon(Icons.LIMB);
        sheet.form((Form)handForm);
        sheet.seed(() -> HandEditorSection.copyPose((PoseTransform)channel.interpolate((float)editor.getReplayTick(), defaultBoneTransform)));
        editor.addPendingSheet(sheet);
        return sheet;
    }

    public boolean getWorldMatrix(UIPovEditor editor, Matrix4f output) {
        Matrix4f matrix;
        String selected = editor.getGizmoBone();
        Matrix4f matrix4f = matrix = selected == null ? null : PovHandMatrices.getFull(selected);
        if (matrix == null) {
            return false;
        }
        output.set((Matrix4fc)matrix);
        return true;
    }

    private boolean hasPoseKeys(UIPovEditor editor, RecordedHandData hand, String bone) {
        KeyframeChannel<?> track = this.boneTrack(editor, hand, bone);
        if (!hand.pose.isEmpty() || track != null && !track.isEmpty()) {
            return true;
        }
        for (KeyframeChannel channel : hand.poseTracks.tracks.values()) {
            if (!channel.getId().startsWith("pose_overlay") || channel.isEmpty()) continue;
            return true;
        }
        return false;
    }

    private KeyframeChannel<?> boneTrack(UIPovEditor editor, RecordedHandData hand, String bone) {
        HandBoneUtils.HandBones bones = HandBoneUtils.collect(ModelFormRenderer.getModel((ModelForm)editor.getHandEditorForm()));
        if (bone.equals(bones.mainRoot())) {
            return hand.rightPose;
        }
        if (bone.equals(bones.offRoot())) {
            return hand.leftPose;
        }
        return hand.poseTracks.get(TrackId.bone((String)"", (String)bone));
    }

    private static PoseTransform copyPose(PoseTransform pose) {
        PoseTransform copy = new PoseTransform();
        copy.copy((Transform)pose);
        return copy;
    }
}

