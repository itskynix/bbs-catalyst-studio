/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.camera.Camera
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.BodyPart
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.ui.forms.editors.UIBodyPartEditor
 *  mchorse.bbs_mod.ui.forms.editors.UIFormEditor
 *  mchorse.bbs_mod.ui.forms.editors.UIForms
 *  mchorse.bbs_mod.ui.forms.editors.UIForms$FormEntry
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIForm
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm
 *  mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer
 *  mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace
 *  mchorse.bbs_mod.ui.utils.Area
 *  mchorse.bbs_mod.ui.utils.GizmoDrag
 *  mchorse.bbs_mod.utils.pose.Transform
 *  org.joml.Matrix3f
 *  org.joml.Matrix3fc
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartGizmoDrag;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import java.util.function.Supplier;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.forms.editors.UIBodyPartEditor;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIFormEditor.class}, remap=false)
public abstract class UIFormEditorPovMixin {
    @Shadow
    public UIForm editor;
    @Shadow
    public UIPickableFormRenderer renderer;
    @Shadow
    public UIBodyPartEditor bodyPartEditor;
    @Shadow
    public UIForms formsList;

    @Shadow
    public abstract boolean isBodyPartGizmoMode();

    @Shadow
    public abstract TransformSpace getGizmoSpace();

    @Inject(method={"buildGizmoDrag"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$buildPovHandGizmoDrag(UIPropTransform transformEditor, float transition, CallbackInfoReturnable<GizmoDrag> info) {
        UIForms.FormEntry entry;
        if (!UIPovHandEditor.isActive() || transformEditor == null || transformEditor.getTransform() == null) {
            return;
        }
        UIPovHandEditor handEditor = UIPovHandEditor.getActive();
        if (handEditor == null) {
            return;
        }
        if (this.isBodyPartGizmoMode() && this.formsList != null && (entry = (UIForms.FormEntry)this.formsList.getCurrentFirst()) != null && entry.part != null) {
            Matrix4f parentMat;
            Matrix3f globalBasis;
            int partIndex = UIPovHandEditor.findBodyPartIndex(entry.part.getForm());
            String formPath = FormUtils.getPath((Form)entry.part.getForm());
            String key = formPath != null && !formPath.isEmpty() ? formPath : String.valueOf(partIndex >= 0 ? partIndex : 0);
            String fallbackKey1 = partIndex >= 0 ? String.valueOf(partIndex) : "";
            String fallbackKey2 = entry.part.getId() != null ? entry.part.getId() : "";
            Transform transform = transformEditor.getTransform();
            Transform baseline = transform.copy();
            Matrix4f captured = PovHandMatrices.getFull(key);
            if (captured == null && !fallbackKey1.isEmpty()) {
                captured = PovHandMatrices.getFull(fallbackKey1);
            }
            if (captured == null && !fallbackKey2.isEmpty()) {
                captured = PovHandMatrices.getFull(fallbackKey2);
            }
            Matrix4f parentBase = UIPovHandEditor.getBodyPartParent(entry.part);
            Matrix4f base = UIPovHandEditor.getBodyPartBase(entry.part);
            Matrix4f evaluated = PovHandMatrices.evaluateFull(key, baseline, transform);
            if (evaluated == null && !fallbackKey1.isEmpty()) {
                evaluated = PovHandMatrices.evaluateFull(fallbackKey1, baseline, transform);
            }
            if (evaluated == null && !fallbackKey2.isEmpty()) {
                evaluated = PovHandMatrices.evaluateFull(fallbackKey2, baseline, transform);
            }
            if (evaluated == null) {
                evaluated = base;
            }
            if (evaluated != null) {
                captured = evaluated;
            }
            Camera camera = this.renderer.camera;
            Area viewport = handEditor.getGapArea();
            Vector3d dragOrigin = captured == null ? new Vector3d(0.0, 0.0, -1.0) : new Vector3d((Vector3fc)captured.getTranslation(new Vector3f()));
            GizmoDrag drag = new PovBodyPartGizmoDrag().setup(camera, viewport, dragOrigin);
            UIFormEditorPovMixin.bbsPov$configureBodyPartDrag(drag, dragOrigin);
            Matrix4f fallbackMatrix = captured == null ? new Matrix4f().translation(0.0f, 0.0f, -1.0f) : new Matrix4f((Matrix4fc)captured);
            Supplier<Matrix4f> matrix = () -> {
                Matrix4f value = PovHandMatrices.evaluateFull(key, baseline, transform);
                if (value == null && !fallbackKey1.isEmpty()) {
                    value = PovHandMatrices.evaluateFull(fallbackKey1, baseline, transform);
                }
                if (value == null && !fallbackKey2.isEmpty()) {
                    value = PovHandMatrices.evaluateFull(fallbackKey2, baseline, transform);
                }
                if (value == null) {
                    Matrix4f currentBase = new Matrix4f((Matrix4fc)parentBase);
                    if (transform != null) {
                        Matrix4f local = new Matrix4f();
                        transform.setupMatrix(local);
                        currentBase.mul((Matrix4fc)local);
                    }
                    value = currentBase;
                }
                return value == null ? new Matrix4f((Matrix4fc)fallbackMatrix) : value;
            };
            TransformSpace space = transformEditor.getSpace();
            Matrix3f displayedBasis = PovHandMatrices.getBasisForSpace(key, space);
            if (displayedBasis == null && !fallbackKey1.isEmpty()) {
                displayedBasis = PovHandMatrices.getBasisForSpace(fallbackKey1, space);
            }
            if (displayedBasis == null && !fallbackKey2.isEmpty()) {
                displayedBasis = PovHandMatrices.getBasisForSpace(fallbackKey2, space);
            }
            if ((globalBasis = PovHandMatrices.getGlobalBasis(key)) == null && !fallbackKey1.isEmpty()) {
                globalBasis = PovHandMatrices.getGlobalBasis(fallbackKey1);
            }
            if (globalBasis == null && !fallbackKey2.isEmpty()) {
                globalBasis = PovHandMatrices.getGlobalBasis(fallbackKey2);
            }
            if (displayedBasis != null) {
                drag.gizmoWorldAxes.set((Matrix3fc)displayedBasis);
            }
            if (globalBasis != null) {
                drag.setGlobalAxes(globalBasis);
            }
            drag.setRotateAxes(GizmoDrag.computeRotateAxes((Transform)transform, matrix));
            drag.setJacobian(GizmoDrag.computeTranslateJacobian((Transform)transform, () -> ((Matrix4f)matrix.get()).getTranslation(new Vector3f())));
            Matrix4f localMat = PovHandMatrices.getForSpace(key, TransformSpace.LOCAL);
            if (localMat == null && !fallbackKey1.isEmpty()) {
                localMat = PovHandMatrices.getForSpace(fallbackKey1, TransformSpace.LOCAL);
            }
            if (localMat == null && !fallbackKey2.isEmpty()) {
                localMat = PovHandMatrices.getForSpace(fallbackKey2, TransformSpace.LOCAL);
            }
            if (localMat == null) {
                localMat = base;
            }
            if ((parentMat = PovHandMatrices.getForSpace(key, TransformSpace.PARENT)) == null && !fallbackKey1.isEmpty()) {
                parentMat = PovHandMatrices.getForSpace(fallbackKey1, TransformSpace.PARENT);
            }
            if (parentMat == null && !fallbackKey2.isEmpty()) {
                parentMat = PovHandMatrices.getForSpace(fallbackKey2, TransformSpace.PARENT);
            }
            if (parentMat == null) {
                parentMat = parentBase;
            }
            drag.setFrameAxes(localMat, parentMat);
            info.setReturnValue(drag);
            return;
        }
        UIForm partIndex = this.editor;
        if (partIndex instanceof UIModelForm) {
            UIModelForm modelForm = (UIModelForm)partIndex;
            if (modelForm.modelPanel != null && modelForm.modelPanel.poseEditor != null) {
                String bone = (String)modelForm.modelPanel.poseEditor.groups.list.getCurrentFirst();
                if (bone == null || bone.isEmpty()) {
                    return;
                }
                if (modelForm.form == handEditor.getRootForm()) {
                    Matrix4f captured = PovHandMatrices.getFull(bone);
                    Transform transform = transformEditor.getTransform();
                    Transform baseline = transform.copy();
                    Matrix4f evaluated = PovHandMatrices.evaluateFull(bone, baseline, transform);
                    if (evaluated != null) {
                        captured = evaluated;
                    }
                    Camera camera = this.renderer.camera;
                    Area viewport = handEditor.getGapArea();
                    Vector3d dragOrigin = captured == null ? new Vector3d(0.0, 0.0, -1.0) : new Vector3d((Vector3fc)captured.getTranslation(new Vector3f()));
                    GizmoDrag drag = new PovBodyPartGizmoDrag().setup(camera, viewport, dragOrigin);
                    UIFormEditorPovMixin.bbsPov$configureBodyPartDrag(drag, dragOrigin);
                    Matrix4f fallbackMatrix = captured == null ? new Matrix4f().translation(0.0f, 0.0f, -1.0f) : new Matrix4f((Matrix4fc)captured);
                    Supplier<Matrix4f> matrix = () -> {
                        Matrix4f value = PovHandMatrices.evaluateFull(bone, baseline, transform);
                        return value == null ? new Matrix4f((Matrix4fc)fallbackMatrix) : value;
                    };
                    TransformSpace space = modelForm.getGizmoSpace();
                    Matrix3f displayedBasis = PovHandMatrices.getBasisForSpace(bone, space);
                    Matrix3f globalBasis = PovHandMatrices.getGlobalBasis(bone);
                    if (displayedBasis != null) {
                        drag.gizmoWorldAxes.set((Matrix3fc)displayedBasis);
                    }
                    if (globalBasis != null) {
                        drag.setGlobalAxes(globalBasis);
                    }
                    drag.setRotateAxes(GizmoDrag.computeRotateAxes((Transform)transform, matrix));
                    drag.setJacobian(GizmoDrag.computeTranslateJacobian((Transform)transform, () -> ((Matrix4f)matrix.get()).getTranslation(new Vector3f())));
                    drag.setFrameAxes(PovHandMatrices.getForSpace(bone, TransformSpace.LOCAL), PovHandMatrices.getForSpace(bone, TransformSpace.PARENT));
                    info.setReturnValue(drag);
                } else if (modelForm.form != null) {
                    Matrix4f parentMat;
                    Matrix3f globalBasis;
                    ModelForm bodyPartModelForm = (ModelForm)modelForm.form;
                    BodyPart part = UIPovHandEditor.findBodyPart(modelForm.form);
                    if (part == null) {
                        return;
                    }
                    int partIndex2 = UIPovHandEditor.findBodyPartIndex(modelForm.form);
                    String formPath = FormUtils.getPath((Form)modelForm.form);
                    String key = (formPath != null && !formPath.isEmpty() ? formPath : String.valueOf(partIndex2 >= 0 ? Integer.valueOf(partIndex2) : (part.getId() != null ? part.getId() : ""))) + "/" + bone;
                    String fallbackKey1 = (partIndex2 >= 0 ? String.valueOf(partIndex2) : "") + "/" + bone;
                    String fallbackKey2 = (part.getId() != null ? part.getId() : "") + "/" + bone;
                    Matrix4f captured = PovHandMatrices.getFull(key);
                    if (captured == null && !fallbackKey1.equals(key)) {
                        captured = PovHandMatrices.getFull(fallbackKey1);
                    }
                    if (captured == null && !fallbackKey2.equals(key)) {
                        captured = PovHandMatrices.getFull(fallbackKey2);
                    }
                    Transform transform = transformEditor.getTransform();
                    Transform baseline = transform.copy();
                    Matrix4f bodyPartBase = UIPovHandEditor.getBodyPartBase(part);
                    Matrix4f evaluated = PovHandMatrices.evaluateFull(key, baseline, transform);
                    if (evaluated == null && !fallbackKey1.equals(key)) {
                        evaluated = PovHandMatrices.evaluateFull(fallbackKey1, baseline, transform);
                    }
                    if (evaluated == null && !fallbackKey2.equals(key)) {
                        evaluated = PovHandMatrices.evaluateFull(fallbackKey2, baseline, transform);
                    }
                    if (evaluated == null) {
                        evaluated = UIPovHandEditor.evaluateBodyPartBoneMatrix(bodyPartModelForm, bone, baseline, transform, bodyPartBase);
                    }
                    if (evaluated != null) {
                        captured = evaluated;
                    }
                    Camera camera = this.renderer.camera;
                    Area viewport = handEditor.getGapArea();
                    Vector3d dragOrigin = captured == null ? new Vector3d(0.0, 0.0, -1.0) : new Vector3d((Vector3fc)captured.getTranslation(new Vector3f()));
                    GizmoDrag drag = new PovBodyPartGizmoDrag().setup(camera, viewport, dragOrigin);
                    UIFormEditorPovMixin.bbsPov$configureBodyPartDrag(drag, dragOrigin);
                    Matrix4f fallbackMatrix = captured == null ? new Matrix4f().translation(0.0f, 0.0f, -1.0f) : new Matrix4f((Matrix4fc)captured);
                    Supplier<Matrix4f> matrix = () -> {
                        Matrix4f value = PovHandMatrices.evaluateFull(key, baseline, transform);
                        if (value == null && !fallbackKey1.equals(key)) {
                            value = PovHandMatrices.evaluateFull(fallbackKey1, baseline, transform);
                        }
                        if (value == null && !fallbackKey2.equals(key)) {
                            value = PovHandMatrices.evaluateFull(fallbackKey2, baseline, transform);
                        }
                        if (value == null) {
                            value = UIPovHandEditor.evaluateBodyPartBoneMatrix(bodyPartModelForm, bone, baseline, transform, bodyPartBase);
                        }
                        return value == null ? new Matrix4f((Matrix4fc)fallbackMatrix) : value;
                    };
                    TransformSpace space = modelForm.getGizmoSpace();
                    Matrix3f displayedBasis = PovHandMatrices.getBasisForSpace(key, space);
                    if (displayedBasis == null && !fallbackKey1.equals(key)) {
                        displayedBasis = PovHandMatrices.getBasisForSpace(fallbackKey1, space);
                    }
                    if (displayedBasis == null && !fallbackKey2.equals(key)) {
                        displayedBasis = PovHandMatrices.getBasisForSpace(fallbackKey2, space);
                    }
                    if ((globalBasis = PovHandMatrices.getGlobalBasis(key)) == null && !fallbackKey1.equals(key)) {
                        globalBasis = PovHandMatrices.getGlobalBasis(fallbackKey1);
                    }
                    if (globalBasis == null && !fallbackKey2.equals(key)) {
                        globalBasis = PovHandMatrices.getGlobalBasis(fallbackKey2);
                    }
                    if (displayedBasis != null) {
                        drag.gizmoWorldAxes.set((Matrix3fc)displayedBasis);
                    }
                    if (globalBasis != null) {
                        drag.setGlobalAxes(globalBasis);
                    }
                    drag.setRotateAxes(GizmoDrag.computeRotateAxes((Transform)transform, matrix));
                    drag.setJacobian(GizmoDrag.computeTranslateJacobian((Transform)transform, () -> ((Matrix4f)matrix.get()).getTranslation(new Vector3f())));
                    Matrix4f localMat = PovHandMatrices.getForSpace(key, TransformSpace.LOCAL);
                    if (localMat == null && !fallbackKey1.equals(key)) {
                        localMat = PovHandMatrices.getForSpace(fallbackKey1, TransformSpace.LOCAL);
                    }
                    if (localMat == null && !fallbackKey2.equals(key)) {
                        localMat = PovHandMatrices.getForSpace(fallbackKey2, TransformSpace.LOCAL);
                    }
                    if (localMat == null) {
                        localMat = UIPovHandEditor.getBodyPartBoneMatrix(bodyPartModelForm, bone, true, bodyPartBase);
                    }
                    if ((parentMat = PovHandMatrices.getForSpace(key, TransformSpace.PARENT)) == null && !fallbackKey1.equals(key)) {
                        parentMat = PovHandMatrices.getForSpace(fallbackKey1, TransformSpace.PARENT);
                    }
                    if (parentMat == null && !fallbackKey2.equals(key)) {
                        parentMat = PovHandMatrices.getForSpace(fallbackKey2, TransformSpace.PARENT);
                    }
                    if (parentMat == null) {
                        parentMat = UIPovHandEditor.getBodyPartBoneMatrix(bodyPartModelForm, bone, false, bodyPartBase);
                    }
                    drag.setFrameAxes(localMat, parentMat);
                    info.setReturnValue(drag);
                }
            }
        }
    }

    @Unique
    private static void bbsPov$configureBodyPartDrag(GizmoDrag drag, Vector3d origin) {
        drag.view.identity();
        drag.cameraOrigin.set(0.0, 0.0, 0.0);
        drag.gizmoOrigin.set((Vector3dc)origin);
    }

    @Inject(method={"getOrigin(F)Lorg/joml/Matrix4f;"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getBodyPartGizmoOrigin(float transition, CallbackInfoReturnable<Matrix4f> info) {
        UIForms.FormEntry entry;
        if (UIPovHandEditor.isActive() && this.isBodyPartGizmoMode() && this.formsList != null && (entry = (UIForms.FormEntry)this.formsList.getCurrentFirst()) != null && entry.part != null) {
            Matrix4f base = UIPovHandEditor.getBodyPartBase(entry.part);
            info.setReturnValue(new Matrix4f((Matrix4fc)base));
        }
    }

    @Inject(method={"getOriginMatrix(F)Lorg/joml/Matrix4f;"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getBodyPartGizmoOriginMatrix(float transition, CallbackInfoReturnable<Matrix4f> info) {
        UIForms.FormEntry entry;
        if (UIPovHandEditor.isActive() && this.isBodyPartGizmoMode() && this.formsList != null && (entry = (UIForms.FormEntry)this.formsList.getCurrentFirst()) != null && entry.part != null) {
            Matrix4f base = UIPovHandEditor.getBodyPartBase(entry.part);
            info.setReturnValue(new Matrix4f((Matrix4fc)base));
        }
    }

    @Inject(method={"getParentOriginMatrix(F)Lorg/joml/Matrix4f;"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getBodyPartGizmoParentOriginMatrix(float transition, CallbackInfoReturnable<Matrix4f> info) {
        UIForms.FormEntry entry;
        if (UIPovHandEditor.isActive() && this.isBodyPartGizmoMode() && this.formsList != null && (entry = (UIForms.FormEntry)this.formsList.getCurrentFirst()) != null && entry.part != null) {
            Matrix4f parent = UIPovHandEditor.getBodyPartParent(entry.part);
            info.setReturnValue(new Matrix4f((Matrix4fc)parent));
        }
    }
}

