/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.camera.Camera
 *  mchorse.bbs_mod.camera.clips.misc.TrackerFrame
 *  mchorse.bbs_mod.camera.data.Angle
 *  mchorse.bbs_mod.camera.data.Point
 *  mchorse.bbs_mod.camera.data.Position
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.FormUtilsClient
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.renderers.FormRenderer
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.forms.renderers.utils.MatrixCache
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.PreviewHud
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.controller.UIFilmController
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils
 *  mchorse.bbs_mod.ui.framework.UIBaseMenu
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace
 *  mchorse.bbs_mod.ui.utils.Area
 *  mchorse.bbs_mod.ui.utils.Gizmo
 *  mchorse.bbs_mod.ui.utils.GizmoDrag
 *  mchorse.bbs_mod.ui.utils.GizmoInteraction
 *  mchorse.bbs_mod.ui.utils.context.UIChoiceMenu
 *  mchorse.bbs_mod.ui.utils.icons.Icon
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.Pair
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.render.Camera
 *  net.minecraft.client.util.math.MatrixStack
 *  org.joml.Matrix4f
 *  org.joml.Vector3d
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Constant
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyConstant
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandGizmo;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.replay.ReplayPovAccess;
import java.util.List;
import java.util.Map;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.PreviewHud;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIFilmController.class}, remap=false)
public class UIFilmControllerPovMixin {
    @Shadow
    public UIFilmPanel panel;
    @Shadow
    private GizmoInteraction gizmo;

    @ModifyConstant(method={"setPov"}, constant={@Constant(intValue=6)})
    private int bbsPov$includePovInModeCount(int original) {
        return 7;
    }

    @ModifyVariable(method={"handleFirstThirdPerson"}, at=@At(value="HEAD"), argsOnly=true, index=3)
    private int bbsPov$useFirstPersonCamera(int mode) {
        return mode == 6 ? 3 : mode;
    }

    @Inject(method={"handleFirstThirdPerson"}, at={@At(value="TAIL")})
    private void bbsPov$applyHardcoreLookInPovMode(Camera camera, float transition, int mode, CallbackInfo info) {
        IEntity entity;
        UIFilmController controller = (UIFilmController)(Object)this;
        if (controller.getPovMode() == 6 && (entity = controller.getCurrentEntity()) != null) {
            boolean isRunning;
            ReplayPovAccess access;
            Replay replay = this.panel.replayEditor.getReplay();
            boolean hardcore = replay instanceof ReplayPovAccess && (Boolean)(access = (ReplayPovAccess)replay).bbsPov$getHardcoreLook().get() != false;
            boolean bl = isRunning = controller.panel.getRunner() != null && controller.panel.getRunner().isRunning();
            if (hardcore) {
                float t = isRunning ? transition : 0.0f;
                this.bbsPov$applyHardcoreCamera(camera, controller, entity, t);
            } else {
                float t = isRunning ? transition : 0.0f;
                float filmTick = (float)this.panel.getCursor() + t;
                double baseEyeHeight = entity.getEyeHeight();
                double povEyeHeight = PovCameraClips.getSmoothPovEyeHeight(replay, entity, entity.getForm(), filmTick);
                if (Math.abs(povEyeHeight - baseEyeHeight) > 1.0E-5) {
                    camera.position.y += povEyeHeight - baseEyeHeight;
                }
            }
        }
    }

    private void bbsPov$applyHardcoreCamera(Camera camera, UIFilmController controller, IEntity entity, float transition) {
        TrackerFrame frame;
        Form form = entity.getForm();
        if (form == null) {
            return;
        }
        FormRenderer formRenderer = FormUtilsClient.getRenderer((Form)form);
        if (formRenderer instanceof ModelFormRenderer) {
            ModelFormRenderer modelFormRenderer = (ModelFormRenderer)formRenderer;
            modelFormRenderer.ensureAnimator(transition);
        }
        String headBone = "head";
        if (formRenderer != null) {
            MatrixCache map = formRenderer.collectMatrices(entity, transition);
            if (!map.has(headBone)) {
                for (String key : map.keySet()) {
                    if (!key.equalsIgnoreCase("head") && !key.toLowerCase().endsWith("/head") && !key.toLowerCase().endsWith(".head")) continue;
                    headBone = key;
                    break;
                }
            }
            if (!map.has(headBone)) {
                List<String> bones = formRenderer.getBones();
                for (String bone : bones) {
                    if (!bone.equalsIgnoreCase("head") && !bone.toLowerCase().endsWith("head")) continue;
                    headBone = bone;
                    break;
                }
            }
        }
        if ((frame = TrackerFrame.resolve((Map)controller.getEntities(), (IEntity)entity, (String)headBone, (double)camera.position.x, (double)camera.position.y, (double)camera.position.z, (float)transition)) != null) {
            double scaleY = 1.0;
            if (form.transform != null && form.transform.get() != null) {
                scaleY = Math.max(0.001, (double)((Transform)form.transform.get()).scale.y);
            }
            float filmTick = (float)this.panel.getCursor() + transition;
            Replay replay = this.panel.replayEditor.getReplay();
            double eyeHeight = PovCameraClips.getSmoothPovEyeHeight(replay, entity, form, filmTick);
            double eyeDiff = (eyeHeight - 1.5) / scaleY;
            Point eyeOffset = new Point(0.0, eyeDiff, 0.0);
            Vector3d headPos = frame.position(eyeOffset);
            Angle headAngle = frame.angles(new Point(0.0, 0.0, 0.0));
            camera.position.set(headPos.x, headPos.y, headPos.z);
            camera.rotation.set((float)Math.toRadians(headAngle.pitch), (float)Math.toRadians(headAngle.yaw), (float)Math.toRadians(headAngle.roll));
        }
    }

    @Redirect(method={"renderFrame"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/film/Recorder;renderCameraPreview"))
    private void bbsPov$hideRecordingCameraPreview(Position position, net.minecraft.client.render.Camera camera, MatrixStack matrices) {
        UIFilmController controller = (UIFilmController)(Object)this;
        if (controller.getPovMode() != 6) {
            Recorder.renderCameraPreview((Position)position, (net.minecraft.client.render.Camera)camera, (MatrixStack)matrices);
        }
    }

    @Inject(method={"getOrbitModeIcon(I)Lmchorse/bbs_mod/ui/utils/icons/Icon;"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getPovIcon(int mode, CallbackInfoReturnable<Icon> info) {
        if (mode == 6) {
            info.setReturnValue(Icons.VISIBLE);
        }
    }

    @Inject(method={"getOrbitModeLabel"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$getPovLabel(int mode, CallbackInfoReturnable<IKey> info) {
        if (mode == 6) {
            info.setReturnValue(L10n.lang("bbs.pov.camera_mode", "Point of View"));
        }
    }

    @Inject(method={"getGizmoProjection"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$useHandGizmoProjection(CallbackInfoReturnable<Matrix4f> info) {
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel;
        UIFilmController controller = (UIFilmController)(Object)this;
        Matrix4f projection = PovHandPicking.getProjection();
        if (controller.getPovMode() == 6 && (uIFilmPanel = this.panel) instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$getEditor() != null && access.bbsPov$getEditor().isPoseGizmoSection() && projection != null) {
            info.setReturnValue(projection);
        }
    }

    @Inject(method={"getBone"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$usePovEditorBone(CallbackInfoReturnable<Pair<String, TransformSpace>> info) {
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel;
        UIFilmController controller = (UIFilmController)(Object)this;
        if (controller.getPovMode() == 6 && (uIFilmPanel = this.panel) instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$getEditor() != null && access.bbsPov$getEditor().isPoseGizmoSection()) {
            info.setReturnValue(access.bbsPov$getEditor().keyframeEditor.getBone());
        }
    }

    @Inject(method={"renderHUD"}, at={@At(value="HEAD")})
    private void bbsPov$refreshVisibleGizmo(UIContext context, PreviewHud hud, Area area, CallbackInfo info) {
        if (((UIFilmController)(Object)this).getPovMode() == 6) {
            PovHandGizmo.captureVisual();
        }
    }

    @Inject(method={"canShowGizmo"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$isolateGizmoInPovCamera(CallbackInfoReturnable<Boolean> info) {
        UIFilmController controller = (UIFilmController)(Object)this;
        if (controller.getPovMode() == 6) {
            info.setReturnValue(UIBaseMenu.shouldRenderAxes() && !controller.isRecording() && this.bbsPov$getHandGizmoTransform() != null);
        }
    }

    @Inject(method={"setPov"}, at={@At(value="HEAD")})
    private void bbsPov$stopOldGizmoWhenCameraChanges(int mode, CallbackInfo info) {
        UIFilmController controller = (UIFilmController)(Object)this;
        if (mode == 6 || controller.getPovMode() == 6) {
            controller.stopGizmoInteraction();
            Gizmo.INSTANCE.stop();
        }
    }

    @Redirect(method={"startGizmo"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/ui/film/replays/UIReplaysEditorUtils;startFilmGizmo(Lmchorse/bbs_mod/ui/film/UIFilmPanel;Lmchorse/bbs_mod/ui/framework/UIContext;IF)Z"))
    private boolean bbsPov$startHandGizmo(UIFilmPanel panel, UIContext context, int index, float transition) {
        UIFilmController controller = (UIFilmController)(Object)this;
        UIPropTransform transform = this.bbsPov$getHandGizmoTransform();
        if (controller.getPovMode() != 6 || transform == null) {
            return UIReplaysEditorUtils.startFilmGizmo((UIFilmPanel)panel, (UIContext)context, (int)index, (float)transition);
        }
        GizmoDrag drag = UIReplaysEditorUtils.buildFilmGizmoDrag((UIFilmPanel)panel, (Camera)panel.getCamera(), (Area)panel.preview.getViewport(), (UIPropTransform)transform, (float)transition);
        return Gizmo.INSTANCE.start(index, context.mouseX, context.mouseY, transform, drag);
    }

    @Redirect(method={"toggleOrbitMode"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/ui/utils/context/UIChoiceMenu;of(Ljava/lang/Iterable;)Lmchorse/bbs_mod/ui/utils/context/UIChoiceMenu;"))
    private UIChoiceMenu<Integer> bbsPov$addPovCameraMode(Iterable<Integer> modes) {
        List<Integer> list = List.of(Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(6));
        return UIChoiceMenu.of(list);
    }

    @ModifyVariable(method={"setPov"}, at=@At(value="HEAD"), argsOnly=true)
    private int bbsPov$sanitizePovMode(int pov) {
        if (pov == 3 || pov == 4 || pov == 5) {
            return 6;
        }
        return pov;
    }

    @Inject(method={"subMouseClicked"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$pickHandBeforeReplayController(UIContext context, CallbackInfoReturnable<Boolean> info) {
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel;
        int index;
        UIFilmController controller = (UIFilmController)(Object)this;
        if (controller.getPovMode() == 6 && this.bbsPov$getHandGizmoTransform() != null && context.mouseButton == 0 && controller.picker.getStencil().hasPicked() && (index = controller.picker.getStencil().getIndex()) >= 1 && index <= 19 && controller.startGizmo(context, index)) {
            info.setReturnValue(true);
            return;
        }
        if (controller.getPovMode() == 6 && this.bbsPov$getHandGizmoTransform() != null && this.gizmo.mouseClickedSphere(context)) {
            info.setReturnValue(true);
            return;
        }
        if (controller.getPovMode() == 6 && this.panel.preview.getViewport().isInside(context) && (uIFilmPanel = this.panel) instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$getEditor() != null && access.bbsPov$getEditor().pickViewport(context, this.panel.preview.getViewport())) {
            info.setReturnValue(true);
        }
    }

    private UIPropTransform bbsPov$getHandGizmoTransform() {
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel = this.panel;
        if (!(uIFilmPanel instanceof UIFilmPanelPovAccess) || (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$getEditor() == null || !access.bbsPov$getEditor().isPoseGizmoSection()) {
            return null;
        }
        return PovHandGizmo.getTransform();
    }
}

