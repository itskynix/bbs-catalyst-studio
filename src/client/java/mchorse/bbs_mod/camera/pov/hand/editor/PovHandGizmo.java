/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.controller.UIFilmController
 *  mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace
 *  mchorse.bbs_mod.ui.framework.elements.utils.StencilMap
 *  mchorse.bbs_mod.ui.utils.Gizmo
 *  mchorse.bbs_mod.utils.MatrixStackUtils
 *  net.minecraft.client.util.math.MatrixStack
 *  org.joml.Matrix4f
 */
package mchorse.bbs_mod.camera.pov.hand.editor;

import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public final class PovHandGizmo {
    private PovHandGizmo() {
    }

    public static void captureVisual() {
        Context context = PovHandGizmo.getContext();
        if (context != null) {
            PovHandGizmo.capture(context, null);
        }
    }

    public static void renderStencil(StencilMap stencilMap) {
        Context context = PovHandGizmo.getContext();
        if (context != null && stencilMap != null) {
            PovHandGizmo.capture(context, stencilMap);
        }
    }

    public static UIPropTransform getTransform() {
        Context context = PovHandGizmo.getContext();
        return context == null ? null : context.transform;
    }

    private static Context getContext() {
        UIFilmPanelPovAccess access;
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel == null || panel.getController().getPovMode() != 6 || !(panel instanceof UIFilmPanelPovAccess) || (access = (UIFilmPanelPovAccess)panel).bbsPov$getEditor() == null || !access.bbsPov$getEditor().isPoseGizmoSection()) {
            return null;
        }
        UIPropTransform transform = access.bbsPov$getEditor().getHandGizmoTransform();
        String selected = access.bbsPov$getEditor().getGizmoBone();
        if (transform == null || selected == null || selected.isBlank()) {
            return null;
        }
        TransformSpace space = access.bbsPov$getEditor().keyframeEditor.getBoneSpace();
        Matrix4f bone = PovHandMatrices.getForSpace(selected, space);
        return bone == null ? null : new Context(panel.getController(), transform, bone, space);
    }

    private static void capture(Context context, StencilMap stencilMap) {
        MatrixStack matrices = new MatrixStack();
        MatrixStackUtils.multiply((MatrixStack)matrices, (Matrix4f)context.bone);
        Gizmo.INSTANCE.trackGesture(context.transform != null ? context.transform.getGesture() : null);
        Gizmo.INSTANCE.reorientForSpace(matrices, context.space, null, null);
        if (stencilMap == null) {
            Gizmo.INSTANCE.captureVisual(matrices);
        } else {
            Gizmo.INSTANCE.renderStencil(matrices);
        }
    }

    private record Context(UIFilmController controller, UIPropTransform transform, Matrix4f bone, TransformSpace space) {
    }
}

