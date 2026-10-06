/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.graphics.window.Window
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory$Variant
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture$Host
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.utils.Area
 *  mchorse.bbs_mod.ui.utils.GizmoDrag
 *  mchorse.bbs_mod.utils.Axis
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  org.joml.Quaternionf
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 *  org.lwjgl.glfw.GLFW
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hud.editor.IUIPropTransform2DLayout;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.IGizmoDragFirstPerson;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformGesture;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TransformGesture.class}, remap=false)
public abstract class TransformGesturePovMixin {
    @Shadow
    @Final
    private TransformGesture.Host host;
    @Shadow
    private boolean editing;
    @Shadow
    private Axis axis;
    @Shadow
    private Axis axis2;
    @Shadow
    private boolean hotkeyMode;
    @Shadow
    @Final
    private Transform cache;
    @Unique
    private UIElement bbsPov$axisGuide;
    @Unique
    private int bbsPov$startMouseX;
    @Unique
    private int bbsPov$startMouseY;
    @Unique
    private int bbsPov$lastMouseX;
    @Unique
    private int bbsPov$lastMouseY;
    @Unique
    private double bbsPov$accumulatedRotationDeg;
    @Unique
    private double bbsPov$startTranslateX;
    @Unique
    private double bbsPov$startTranslateY;
    @Unique
    private double bbsPov$startRotateDeg;
    @Unique
    private Vector3f bbsPov$rotationPivot;
    @Unique
    private Vector3f bbsPov$startTranslate;
    @Unique
    private Quaternionf bbsPov$startRotation;

    @Shadow
    public abstract void accept();

    @Shadow
    public abstract void reject();

    @Shadow
    public abstract void enableMode(TransformOp var1, Axis var2, Axis var3);

    @Shadow
    public abstract TransformOp getOp();

    @Shadow
    public abstract Axis getAxis();

    @Shadow
    public abstract Axis getAxis2();

    @Shadow
    public abstract boolean isEditing();

    @Unique
    private boolean bbsPov$isLayout() {
        IUIPropTransform2DLayout layout;
        TransformGesture.Host host = this.host;
        return host instanceof IUIPropTransform2DLayout && (layout = (IUIPropTransform2DLayout)host).bbsPov$is2DLayout();
    }

    @Inject(method={"enableMode(Lmchorse/bbs_mod/ui/framework/elements/input/drag/TransformOp;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$enableTwoDimensionalMode(TransformOp mode, CallbackInfo info) {
        if (!this.bbsPov$isLayout()) {
            return;
        }
        if (mode == TransformOp.TRANSLATE) {
            this.enableMode(TransformOp.TRANSLATE, Axis.X, Axis.Y);
            this.bbsPov$ensureGuide();
            info.cancel();
        } else if (mode == TransformOp.ROTATE) {
            this.enableMode(TransformOp.ROTATE, Axis.Z, null);
            this.bbsPov$ensureGuide();
            info.cancel();
        }
    }

    @Inject(method={"startEdit"}, at={@At(value="TAIL")})
    private void bbsPov$captureStartDrag(TransformOp op, Axis axis, Axis axis2, DragStrategyFactory.Variant variant, GizmoDrag gizmoDrag, boolean hotkey, CallbackInfo info) {
        IGizmoDragFirstPerson fpDrag;
        if (gizmoDrag instanceof IGizmoDragFirstPerson && (fpDrag = (IGizmoDragFirstPerson)gizmoDrag).bbsPov$getRotationPivot() != null) {
            this.bbsPov$rotationPivot = new Vector3f((Vector3fc)fpDrag.bbsPov$getRotationPivot());
            if (this.cache != null) {
                this.bbsPov$startTranslate = new Vector3f((Vector3fc)this.cache.translate);
                this.bbsPov$startRotation = this.cache.createRotation();
            }
        } else {
            this.bbsPov$rotationPivot = null;
            this.bbsPov$startTranslate = null;
            this.bbsPov$startRotation = null;
        }
        if (this.bbsPov$isLayout()) {
            UIContext context = this.host.getContext();
            if (context != null) {
                this.bbsPov$startMouseX = context.mouseX;
                this.bbsPov$startMouseY = context.mouseY;
                this.bbsPov$lastMouseX = context.mouseX;
                this.bbsPov$lastMouseY = context.mouseY;
            }
            this.bbsPov$accumulatedRotationDeg = 0.0;
            if (this.cache != null) {
                this.bbsPov$startTranslateX = this.cache.translate.x;
                this.bbsPov$startTranslateY = this.cache.translate.y;
                this.bbsPov$startRotateDeg = Math.toDegrees(this.cache.rotate.z);
            }
        }
    }

    @Inject(method={"updateDrag"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$finishReleasedMouseDrag(UIContext context, CallbackInfo info) {
        if (!UIPovHandEditor.isActive() || !this.editing || this.hotkeyMode) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() != null && GLFW.glfwGetMouseButton((long)client.getWindow().getHandle(), (int)0) == 0) {
            this.accept();
            info.cancel();
        }
    }

    @Inject(method={"updateDrag"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$updateTwoDimensionalDrag(UIContext context, CallbackInfo info) {
        if (!this.bbsPov$isLayout()) {
            return;
        }
        if (this.isEditing() && this.getOp() == TransformOp.TRANSLATE) {
            int currentMouseX = context.mouseX;
            int currentMouseY = context.mouseY;
            double dx = currentMouseX - this.bbsPov$startMouseX;
            double dy = currentMouseY - this.bbsPov$startMouseY;
            if (Window.isShiftPressed()) {
                dx *= 0.1;
                dy *= 0.1;
            }
            Area viewport = this.bbsPov$getViewport();
            double filmWidth = this.bbsPov$getFilmGuiWidth();
            double filmHeight = this.bbsPov$getFilmGuiHeight();
            double factorX = viewport != null && viewport.w > 0 ? filmWidth / (double)viewport.w : 1.0;
            double factorY = viewport != null && viewport.h > 0 ? filmHeight / (double)viewport.h : 1.0;
            double layoutDeltaX = dx * factorX / 2.0;
            double layoutDeltaY = dy * factorY / 2.0;
            double newX = this.bbsPov$startTranslateX;
            double newY = this.bbsPov$startTranslateY;
            Axis currentAxis = this.getAxis();
            Axis currentAxis2 = this.getAxis2();
            if (currentAxis2 != null) {
                newX += layoutDeltaX;
                newY -= layoutDeltaY;
            } else if (currentAxis == Axis.X) {
                newX += layoutDeltaX;
            } else if (currentAxis == Axis.Y) {
                newY -= layoutDeltaY;
            }
            if (Window.isCtrlPressed()) {
                newX = Math.round(newX);
                newY = Math.round(newY);
            }
            this.host.setT(null, newX, newY, this.cache != null ? (double)this.cache.translate.z : 0.0);
            this.host.refreshFields();
            info.cancel();
        } else if (this.isEditing() && this.getOp() == TransformOp.ROTATE) {
            double stepDeg;
            int currentMouseX = context.mouseX;
            int currentMouseY = context.mouseY;
            Area viewport = this.bbsPov$getViewport();
            Transform transform = this.host.getTransform();
            if (viewport != null && transform != null) {
                int pivotX = this.bbsPov$getPivotX(viewport, transform);
                int pivotY = this.bbsPov$getPivotY(viewport, transform);
                double dx0 = this.bbsPov$lastMouseX - pivotX;
                double dy0 = this.bbsPov$lastMouseY - pivotY;
                double dx1 = currentMouseX - pivotX;
                double dy1 = currentMouseY - pivotY;
                double r0Sq = dx0 * dx0 + dy0 * dy0;
                double r1Sq = dx1 * dx1 + dy1 * dy1;
                if (r0Sq > 9.0 && r1Sq > 9.0) {
                    double a0 = Math.atan2(dy0, dx0);
                    double a1 = Math.atan2(dy1, dx1);
                    double deltaRad = Math.atan2(Math.sin(a1 - a0), Math.cos(a1 - a0));
                    stepDeg = Math.toDegrees(deltaRad);
                } else {
                    double dx = currentMouseX - this.bbsPov$lastMouseX;
                    stepDeg = dx * 1.0;
                }
            } else {
                double dx = currentMouseX - this.bbsPov$lastMouseX;
                stepDeg = dx * 1.0;
            }
            this.bbsPov$lastMouseX = currentMouseX;
            this.bbsPov$lastMouseY = currentMouseY;
            if (Window.isShiftPressed()) {
                stepDeg *= 0.1;
            }
            this.bbsPov$accumulatedRotationDeg += stepDeg;
            double currentDeg = this.bbsPov$startRotateDeg + this.bbsPov$accumulatedRotationDeg;
            if (Window.isCtrlPressed()) {
                currentDeg = (double)Math.round(currentDeg / 5.0) * 5.0;
            }
            this.host.setR(null, this.cache != null ? Math.toDegrees(this.cache.rotate.x) : 0.0, this.cache != null ? Math.toDegrees(this.cache.rotate.y) : 0.0, currentDeg);
            this.host.refreshFields();
            info.cancel();
        }
    }

    @Inject(method={"setAxis"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$rejectLayoutZ(Axis axis, CallbackInfo info) {
        if (this.bbsPov$isLayout() && axis == Axis.Z && this.getOp() != TransformOp.ROTATE) {
            info.cancel();
        }
    }

    @Inject(method={"setAxis"}, at={@At(value="TAIL")})
    private void bbsPov$showAxisGuide(Axis axis, CallbackInfo info) {
        if (!this.bbsPov$isLayout() || axis == Axis.Z && this.getOp() != TransformOp.ROTATE) {
            return;
        }
        this.bbsPov$ensureGuide();
    }

    @Unique
    private void bbsPov$ensureGuide() {
        UIContext context = this.host.getContext();
        if (context == null) {
            return;
        }
        if (this.bbsPov$axisGuide == null) {
            this.bbsPov$axisGuide = new UIElement(){

                public void render(UIContext guideContext) {
                    if (TransformGesturePovMixin.this.isEditing() && TransformGesturePovMixin.this.getOp() == TransformOp.TRANSLATE) {
                        Area viewport = TransformGesturePovMixin.this.bbsPov$getViewport();
                        Transform transform = TransformGesturePovMixin.this.host.getTransform();
                        if (viewport != null && transform != null) {
                            int pivotX = TransformGesturePovMixin.this.bbsPov$getPivotX(viewport, transform);
                            int pivotY = TransformGesturePovMixin.this.bbsPov$getPivotY(viewport, transform);
                            guideContext.batcher.box((float)(pivotX - 4), (float)pivotY, (float)(pivotX + 5), (float)(pivotY + 1), -285212673);
                            guideContext.batcher.box((float)pivotX, (float)(pivotY - 4), (float)(pivotX + 1), (float)(pivotY + 5), -285212673);
                            if (TransformGesturePovMixin.this.getAxis2() == null && TransformGesturePovMixin.this.getAxis() == Axis.X) {
                                guideContext.batcher.box((float)viewport.x, (float)pivotY, (float)viewport.ex(), (float)(pivotY + 1), -855687104);
                            } else if (TransformGesturePovMixin.this.getAxis2() == null && TransformGesturePovMixin.this.getAxis() == Axis.Y) {
                                guideContext.batcher.box((float)pivotX, (float)viewport.y, (float)(pivotX + 1), (float)viewport.ey(), -868155584);
                            }
                        }
                    }
                    super.render(guideContext);
                }
            };
        }
        if (!this.bbsPov$axisGuide.hasParent()) {
            context.menu.overlay.add((IUIElement)this.bbsPov$axisGuide);
        }
    }

    @Unique
    private Area bbsPov$getViewport() {
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        return panel == null || panel.preview == null ? null : panel.preview.getViewport();
    }

    @Unique
    private int bbsPov$getPivotX(Area viewport, Transform transform) {
        double filmWidth = this.bbsPov$getFilmGuiWidth();
        double filmX = filmWidth / 2.0 + (double)(transform.translate.x * 2.0f);
        return viewport.x + (int)Math.round(filmX * (double)viewport.w / filmWidth);
    }

    @Unique
    private int bbsPov$getPivotY(Area viewport, Transform transform) {
        UIKeyframeSheet sheet;
        double filmHeight = this.bbsPov$getFilmGuiHeight();
        double filmY = filmHeight / 2.0 - (double)(transform.translate.y * 2.0f);
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel != null && panel.replayEditor != null && panel.replayEditor.keyframeEditor != null && panel.replayEditor.keyframeEditor.view != null && panel.replayEditor.keyframeEditor.view.getGraph() != null && (sheet = panel.replayEditor.keyframeEditor.view.getGraph().getLastSheet()) != null && (sheet.id.contains("layout") || sheet.id.contains("hotbar"))) {
            filmY = filmHeight - (double)(transform.translate.y * 2.0f);
        }
        return viewport.y + (int)Math.round(filmY * (double)viewport.h / filmHeight);
    }

    @Unique
    private double bbsPov$getFilmGuiWidth() {
        return PovViewportMetrics.getFilmScaledWidth();
    }

    @Unique
    private double bbsPov$getFilmGuiHeight() {
        return PovViewportMetrics.getFilmScaledHeight();
    }

    @Inject(method={"updateDrag"}, at={@At(value="INVOKE", target="Lmchorse/bbs_mod/ui/framework/elements/input/drag/TransformGesture$Host;refreshFields()V", shift=At.Shift.BEFORE)})
    private void bbsPov$compensateFirstPersonRotationPivot(UIContext context, CallbackInfo info) {
        Transform curTransform;
        if (this.bbsPov$rotationPivot != null && this.getOp() == TransformOp.ROTATE && this.host != null && this.bbsPov$startTranslate != null && this.bbsPov$startRotation != null && (curTransform = this.host.getTransform()) != null) {
            Quaternionf curRotation = curTransform.createRotation();
            Vector3f r0 = this.bbsPov$startRotation.transform(new Vector3f((Vector3fc)this.bbsPov$rotationPivot));
            Vector3f r1 = curRotation.transform(new Vector3f((Vector3fc)this.bbsPov$rotationPivot));
            Vector3f delta = new Vector3f((Vector3fc)r0).sub((Vector3fc)r1);
            this.host.setT(null, (double)(this.bbsPov$startTranslate.x + delta.x), (double)(this.bbsPov$startTranslate.y + delta.y), (double)(this.bbsPov$startTranslate.z + delta.z));
        }
    }

    @Inject(method={"sphereWorldRadius"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$fallbackSphereWorldRadius(CallbackInfoReturnable<Float> info) {
        if (((Float)info.getReturnValue()).floatValue() <= 0.0f) {
            info.setReturnValue(Float.valueOf(0.5f));
        }
    }

    @Inject(method={"accept", "reject"}, at={@At(value="TAIL")})
    private void bbsPov$cleanupDragState(CallbackInfo info) {
        this.bbsPov$rotationPivot = null;
        this.bbsPov$startTranslate = null;
        this.bbsPov$startRotation = null;
        if (this.bbsPov$axisGuide != null && this.bbsPov$axisGuide.hasParent()) {
            this.bbsPov$axisGuide.removeFromParent();
        }
    }
}

