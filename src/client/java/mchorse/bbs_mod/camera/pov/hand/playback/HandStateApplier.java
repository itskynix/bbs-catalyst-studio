/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.cubic.model.ArmorSlot
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.FormUtilsClient
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.FormRenderer
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.utils.colors.Color
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 */
package mchorse.bbs_mod.camera.pov.hand.playback;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartPlayback;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.HandState;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

public final class HandStateApplier {
    private HandStateApplier() {
    }

    public static void inheritReplayModel(RecordedHandData data, HandState state, Replay replay, UIFilmPanel panel) {
        Form root;
        ModelForm baseModelForm = null;
        if (data != null && data.baseForm.get() != null && (root = FormUtils.getRoot((Form)((Form)data.baseForm.get()))) instanceof ModelForm) {
            ModelForm mf = (ModelForm)root;
            if (mf.model.get() != null && !((String)mf.model.get()).isBlank()) {
                baseModelForm = mf;
            }
        }
        if (baseModelForm == null) {
            Form root2;
            Film film;
            int selector;
            IEntity entity = null;
            if (panel != null && (selector = PovCameraClips.indexOfReplay(film = (Film)panel.getData(), replay)) >= 0) {
                entity = (IEntity)panel.getController().getEntities().get(selector);
            }
            Form source = entity == null ? (replay != null ? (Form)replay.form.get() : null) : entity.getForm();
            Form form = root2 = source == null ? null : FormUtils.getRoot((Form)source);
            if (root2 instanceof ModelForm) {
                ModelForm mf = (ModelForm)root2;
                if (mf.model.get() != null && !((String)mf.model.get()).isBlank()) {
                    baseModelForm = mf;
                }
            }
        }
        if (baseModelForm == null) {
            if (state.model == null || state.model.isBlank()) {
                state.model = "player/steve";
            }
            return;
        }
        if (state.model == null || state.model.isBlank()) {
            String model = (String)baseModelForm.model.get();
            state.model = model != null && !model.isBlank() ? model : "player/steve";
        }
        if (state.texture == null) {
            Link link = state.texture = state.model.equals(baseModelForm.model.get()) ? (Link)baseModelForm.texture.get() : null;
        }
        if (state.color == null && baseModelForm.color.get() != null) {
            state.color = ((Color)baseModelForm.color.get()).copy();
        }
        if (state.colorOverlay == null && baseModelForm.overlayColor.get() != null) {
            state.colorOverlay = ((Color)baseModelForm.overlayColor.get()).copy();
        }
        if (baseModelForm.pose.get() != null && state.pose.transforms.isEmpty()) {
            state.pose.copy((Pose)baseModelForm.pose.get());
        }
    }

    public static void applyForm(ModelForm form, HandState state, RecordedHandData data, float tick) {
        ModelFormRenderer modelRenderer;
        ModelInstance model;
        RecordedHandData.ensureOverlays((Form)form);
        form.visible.set(true);
        form.model.set((state.model == null || state.model.isBlank() ? "player/steve" : state.model));
        form.texture.set(state.texture);
        form.color.set((state.color != null ? state.color.copy() : Color.white()));
        form.overlayColor.set((state.colorOverlay != null ? state.colorOverlay.copy() : new Color(1.0f, 1.0f, 1.0f, 0.0f)));
        String rightBone = "right_arm";
        String leftBone = "left_arm";
        FormRenderer renderer = FormUtilsClient.getRenderer((Form)form);
        if (renderer instanceof ModelFormRenderer && (model = (modelRenderer = (ModelFormRenderer)renderer).getModel()) != null) {
            rightBone = HandStateApplier.getBone(model.getFpMain(), rightBone);
            leftBone = HandStateApplier.getBone(model.getFpOffhand(), leftBone);
        }
        Pose pose = state.pose.copy();
        if (data != null && !data.rightPose.isEmpty()) {
            pose.transforms.put(rightBone, HandStateApplier.copyPose(state.rightPose));
        }
        if (data != null && !data.leftPose.isEmpty()) {
            pose.transforms.put(leftBone, HandStateApplier.copyPose(state.leftPose));
        }
        form.pose.set(pose);
        if (!UIPovHandEditor.isActive()) {
            Form root;
            data.applyPoseTracks(form, tick);
            if (data != null && data.baseForm.get() != null && (root = FormUtils.getRoot((Form)((Form)data.baseForm.get()))) instanceof ModelForm) {
                ModelForm mf = (ModelForm)root;
                PovBodyPartPlayback.sync(form, mf);
            }
            data.applyBodyPartTracks(form, tick);
            PovBodyPartPlayback.syncHoverFrame(form);
        } else {
            Form root;
            Form preview;
            Form form2 = preview = UIPovHandEditor.getActive() != null ? UIPovHandEditor.getActive().getPreviewForm() : null;
            if (preview != null && (root = FormUtils.getRoot((Form)preview)) instanceof ModelForm) {
                ModelForm mf = (ModelForm)root;
                PovBodyPartPlayback.sync(form, mf);
            }
        }
    }

    private static String getBone(ArmorSlot slot, String fallback) {
        return slot == null || slot.group == null || slot.group.isBlank() ? fallback : slot.group;
    }

    public static HandState createDefaultHandEditorState(RecordedHandData data, Replay replay) {
        ModelForm mf;
        Form root;
        Form activeForm;
        HandState state = new HandState();
        state.visible = true;
        state.rightHandVisible = true;
        state.leftHandVisible = true;
        String model = null;
        Link texture = null;
        Form form = activeForm = UIPovHandEditor.getActive() != null ? UIPovHandEditor.getActive().getPreviewForm() : null;
        if (activeForm == null && data != null) {
            activeForm = (Form)data.baseForm.get();
        }
        if (activeForm != null && (root = FormUtils.getRoot((Form)activeForm)) instanceof ModelForm) {
            mf = (ModelForm)root;
            if (mf.model.get() != null && !((String)mf.model.get()).isBlank()) {
                model = (String)mf.model.get();
                texture = (Link)mf.texture.get();
                if (mf.color.get() != null) {
                    state.color = ((Color)mf.color.get()).copy();
                }
                if (mf.overlayColor.get() != null) {
                    state.colorOverlay = ((Color)mf.overlayColor.get()).copy();
                }
                if (mf.pose.get() != null) {
                    state.pose.copy((Pose)mf.pose.get());
                }
            }
        }
        if ((model == null || model.isBlank()) && replay != null && replay.form.get() != null && (root = FormUtils.getRoot((Form)((Form)replay.form.get()))) instanceof ModelForm) {
            mf = (ModelForm)root;
            if (mf.model.get() != null && !((String)mf.model.get()).isBlank()) {
                model = (String)mf.model.get();
                texture = (Link)mf.texture.get();
                if (state.color == null && mf.color.get() != null) {
                    state.color = ((Color)mf.color.get()).copy();
                }
                if (state.colorOverlay == null && mf.overlayColor.get() != null) {
                    state.colorOverlay = ((Color)mf.overlayColor.get()).copy();
                }
            }
        }
        if (model == null || model.isBlank()) {
            model = "player/steve";
            texture = RecordedHandData.DEFAULT_TEXTURE;
        }
        state.model = model;
        state.texture = texture;
        return state;
    }

    private static PoseTransform copyPose(PoseTransform source) {
        PoseTransform copy = new PoseTransform();
        copy.copy((Transform)source);
        return copy;
    }
}

