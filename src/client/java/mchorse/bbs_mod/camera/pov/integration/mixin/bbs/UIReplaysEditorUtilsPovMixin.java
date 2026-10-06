/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.camera.Camera
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils
 *  mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
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
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartGizmoDrag;
import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import java.util.function.Supplier;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIReplaysEditorUtils.class}, remap=false)
public class UIReplaysEditorUtilsPovMixin {
    @Inject(method={"buildFilmGizmoDrag"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$buildHandGizmoDrag(UIFilmPanel panel, Camera camera, Area viewport, UIPropTransform editor, float transition, CallbackInfoReturnable<GizmoDrag> info) {
        Matrix4f evaluated;
        if (!(panel instanceof UIFilmPanelPovAccess)) {
            return;
        }
        UIFilmPanelPovAccess access = (UIFilmPanelPovAccess)panel;
        UIPovEditor pov = access.bbsPov$getEditor();
        if (pov == null || !pov.isPoseGizmoSection() || editor == null || editor.getTransform() == null) {
            return;
        }
        UIKeyframeEditor keyframeEditor = pov.keyframeEditor;
        String bone = keyframeEditor == null ? null : pov.getGizmoBone();
        Matrix4f captured = bone == null ? null : PovHandMatrices.getFull(bone);
        Transform transform = editor.getTransform();
        Transform baseline = transform.copy();
        Matrix4f matrix4f = evaluated = bone == null ? null : PovHandMatrices.evaluateFull(bone, baseline, transform);
        if (evaluated != null) {
            captured = evaluated;
        } else if (bone == null) {
            Matrix4f local = new Matrix4f();
            local.translate(0.0f, 0.0f, -1.0f);
            transform.setupMatrix(local);
            captured = local;
        }
        Matrix4f handProjection = PovHandPicking.getProjection();
        Vector3d dragOrigin = captured == null ? new Vector3d(0.0, 0.0, -1.0) : new Vector3d((Vector3fc)captured.getTranslation(new Vector3f()));
        GizmoDrag drag = new PovBodyPartGizmoDrag().setup(camera, viewport, dragOrigin);
        if (handProjection != null) {
            drag.projection.set((Matrix4fc)handProjection);
        }
        drag.view.identity();
        drag.cameraOrigin.set(0.0, 0.0, 0.0);
        drag.gizmoOrigin.set((Vector3dc)dragOrigin);
        Supplier<Matrix4f> matrix = () -> {
            if (bone != null) {
                Matrix4f value = PovHandMatrices.evaluateFull(bone, baseline, transform);
                return value == null ? new Matrix4f().translation(0.0f, 0.0f, -1.0f) : value;
            }
            Matrix4f local = new Matrix4f();
            local.translate(0.0f, 0.0f, -1.0f);
            transform.setupMatrix(local);
            return local;
        };
        TransformSpace space = keyframeEditor != null ? keyframeEditor.getBoneSpace() : TransformSpace.LOCAL;
        Matrix3f displayedBasis = null;
        Matrix3f globalBasis = null;
        if (bone != null) {
            displayedBasis = PovHandMatrices.getBasisForSpace(bone, space);
            globalBasis = PovHandMatrices.getGlobalBasis(bone);
        } else {
            displayedBasis = new Matrix3f();
            if (space == TransformSpace.LOCAL) {
                matrix.get().get3x3(displayedBasis);
            } else {
                displayedBasis.identity();
            }
            globalBasis = new Matrix3f().identity();
        }
        if (displayedBasis != null) {
            drag.gizmoWorldAxes.set((Matrix3fc)displayedBasis);
        }
        if (globalBasis != null) {
            drag.setGlobalAxes(globalBasis);
        }
        drag.setRotateAxes(GizmoDrag.computeRotateAxes((Transform)transform, matrix));
        drag.setJacobian(GizmoDrag.computeTranslateJacobian((Transform)transform, () -> ((Matrix4f)matrix.get()).getTranslation(new Vector3f())));
        Vector3f additiveBase = bone != null ? PovHandMatrices.getAdditiveRotationBase(bone, baseline) : null;
        drag.setAdditiveRotationBase(additiveBase);
        if (bone != null) {
            drag.setFrameAxes(PovHandMatrices.getForSpace(bone, TransformSpace.LOCAL), PovHandMatrices.getForSpace(bone, TransformSpace.PARENT));
        } else {
            drag.setFrameAxes(matrix.get(), new Matrix4f().identity().translation(0.0f, 0.0f, -1.0f));
        }
        info.setReturnValue(drag);
    }
}

