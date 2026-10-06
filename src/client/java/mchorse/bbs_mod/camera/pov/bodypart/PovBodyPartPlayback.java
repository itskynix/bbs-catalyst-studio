/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.types.BaseType
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.FormUtilsClient
 *  mchorse.bbs_mod.forms.forms.BodyPart
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.FormRenderer
 *  mchorse.bbs_mod.settings.values.core.ValuePose
 *  mchorse.bbs_mod.settings.values.core.ValueTransform
 *  mchorse.bbs_mod.ui.framework.elements.utils.StencilMap
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.Transform
 */
package mchorse.bbs_mod.camera.pov.bodypart;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartRenderer;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;

public final class PovBodyPartPlayback {
    private static final Map<ModelForm, BaseType> LAST_SYNCED_PARTS = new WeakHashMap<ModelForm, BaseType>();
    private static final Map<ModelForm, Map<String, BodyPartFrame>> BODY_PART_FRAMES = new WeakHashMap<ModelForm, Map<String, BodyPartFrame>>();

    private PovBodyPartPlayback() {
    }

    public static void syncHoverFrame(ModelForm root) {
        if (!PovHandPicking.isStencilPass()) {
            HashMap<String, BodyPartFrame> frame = new HashMap<String, BodyPartFrame>();
            PovBodyPartPlayback.captureFrame((Form)root, "", frame);
            BODY_PART_FRAMES.put(root, frame);
            return;
        }
        Map<String, BodyPartFrame> frame = BODY_PART_FRAMES.get(root);
        if (frame == null) {
            return;
        }
        for (Map.Entry<String, BodyPartFrame> entry : frame.entrySet()) {
            Form target = FormUtils.getForm((Form)root, (String)entry.getKey());
            BodyPartFrame saved = entry.getValue();
            if (target == null) continue;
            target.transform.setRuntimeValue(saved.transform.copy());
            target.transformOverlay.setRuntimeValue(saved.transformOverlay.copy());
            for (int j = 0; j < Math.min(target.additionalTransforms.size(), saved.additionalTransforms.size()); ++j) {
                ((ValueTransform)target.additionalTransforms.get(j)).setRuntimeValue(saved.additionalTransforms.get(j).copy());
            }
            if (!(target instanceof ModelForm)) continue;
            ModelForm model = (ModelForm)target;
            if (saved.pose != null) {
                model.pose.setRuntimeValue(saved.pose.copy());
            }
            if (saved.poseOverlay != null) {
                model.poseOverlay.setRuntimeValue(saved.poseOverlay.copy());
            }
            for (int j = 0; j < Math.min(model.additionalOverlays.size(), saved.additionalPoses.size()); ++j) {
                ((ValuePose)model.additionalOverlays.get(j)).setRuntimeValue(saved.additionalPoses.get(j).copy());
            }
        }
    }

    private static void captureFrame(Form root, String prefix, Map<String, BodyPartFrame> frame) {
        int index = 0;
        for (BodyPart part : root.parts.getAllTyped()) {
            Object path;
            Form child = part.getForm();
            Object object = path = prefix.isEmpty() ? Integer.toString(index) : prefix + "/" + index;
            if (child != null) {
                ArrayList<Transform> addT = new ArrayList<Transform>();
                for (ValueTransform t : child.additionalTransforms) {
                    addT.add(((Transform)t.get()).copy());
                }
                Pose p = null;
                Pose pOverlay = null;
                ArrayList<Pose> addP = new ArrayList<Pose>();
                if (child instanceof ModelForm) {
                    ModelForm model = (ModelForm)child;
                    p = ((Pose)model.pose.get()).copy();
                    pOverlay = ((Pose)model.poseOverlay.get()).copy();
                    for (ValuePose o : model.additionalOverlays) {
                        addP.add(((Pose)o.get()).copy());
                    }
                }
                frame.put((String)path, new BodyPartFrame(((Transform)child.transform.get()).copy(), ((Transform)child.transformOverlay.get()).copy(), addT, p, pOverlay, addP));
                PovBodyPartPlayback.captureFrame(child, (String)path, frame);
            }
            ++index;
        }
    }

    public static void sync(ModelForm target, ModelForm source) {
        BaseType sourceData = source.parts.toData();
        BaseType last = LAST_SYNCED_PARTS.get(target);
        if (last == null || !BaseType.equals((BaseType)last, (BaseType)sourceData)) {
            LAST_SYNCED_PARTS.put(target, sourceData);
            target.parts.fromData(sourceData);
        }
    }

    public static void render(ModelForm povForm, int light) {
        if (!PovHandPicking.isStencilPass()) {
            PovBodyPartPlayback.render(povForm, light, null);
        } else {
            PovBodyPartPlayback.render(povForm, light, PovHandPicking.getStencilMap());
        }
    }

    public static void render(ModelForm povForm, int light, StencilMap stencilMap) {
        if (povForm == null || povForm.parts.getAllTyped().isEmpty()) {
            return;
        }
        FormRenderer formRenderer = FormUtilsClient.getRenderer((Form)povForm);
        if (formRenderer instanceof PovBodyPartRenderer) {
            PovBodyPartRenderer renderer = (PovBodyPartRenderer)formRenderer;
            renderer.bbsPov$renderBodyParts(light, stencilMap);
        }
    }

    private record BodyPartFrame(Transform transform, Transform transformOverlay, List<Transform> additionalTransforms, Pose pose, Pose poseOverlay, List<Pose> additionalPoses) {
    }
}

