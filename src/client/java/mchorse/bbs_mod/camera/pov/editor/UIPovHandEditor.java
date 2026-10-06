/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.BBSSettings
 *  mchorse.bbs_mod.cubic.IBoneHierarchy
 *  mchorse.bbs_mod.cubic.IModel
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.BodyPart
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.forms.renderers.utils.MatrixCache
 *  mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry
 *  mchorse.bbs_mod.ui.dashboard.UIDashboard
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.forms.UIFormList
 *  mchorse.bbs_mod.ui.forms.UIFormPalette
 *  mchorse.bbs_mod.ui.forms.editors.UIFormEditor
 *  mchorse.bbs_mod.ui.forms.editors.UIForms$FormEntry
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIForm
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm
 *  mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel
 *  mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor
 *  mchorse.bbs_mod.ui.framework.UIBaseMenu$UIRootElement
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation
 *  mchorse.bbs_mod.ui.utils.Area
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionfc
 */
package mchorse.bbs_mod.camera.pov.editor;

import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneHierarchy;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneUtils;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Objects;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public class UIPovHandEditor
extends UIElement {
    private static UIPovHandEditor active;
    private final UIFilmPanel filmPanel;
    private final UIDashboard dashboard;
    private Replay replay;
    private RecordedHandData hand;
    private UIFormPalette palette;
    private UIFormEditor formEditor;
    private String filteredModel = "";
    private Form lastConfiguredForm;
    private String lastConfiguredModel;
    private boolean configuredCamera;

    public static boolean isActive() {
        return active != null;
    }

    public static UIPovHandEditor getActive() {
        return active;
    }

    public static UIPovHandEditor open(UIFilmPanel filmPanel, Replay replay, RecordedHandData hand) {
        if (filmPanel == null) {
            return null;
        }
        UIDashboard dashboard = filmPanel.dashboard;
        if (active != null) {
            active.closeEditor();
        }
        if (filmPanel.getContext() != null) {
            filmPanel.getContext().closeContextMenu();
        }
        UIElement container = (UIElement)(dashboard != null && dashboard.main != null ? dashboard.main : filmPanel.getRoot());
        if (container == null) {
            return null;
        }
        UIPovHandEditor editor = new UIPovHandEditor(filmPanel, dashboard, replay, hand);
        editor.resetFlex().relative((UIElement)container).full((UIElement)container);
        container.add((IUIElement)editor);
        editor.resize();
        active = editor;
        return editor;
    }

    public UIPovHandEditor(UIFilmPanel filmPanel, UIDashboard dashboard, Replay replay, RecordedHandData hand) {
        active = this;
        this.eventPropagataion(EventPropagation.BLOCK).markContainer();
        this.filmPanel = filmPanel;
        this.dashboard = dashboard;
        this.replay = replay;
        this.hand = hand;
        Form initial = hand == null ? null : hand.ensureBaseForm(replay == null ? null : (Form)replay.form.get());
        this.palette = new UIFormPalette(form -> {
            if (form != null && this.hand != null) {
                this.hand.baseForm.set(FormUtils.copy((Form)form));
            }
        });
        this.palette.noBackground();
        this.palette.cantExit();
        this.palette.setSelected(initial);
        this.palette.edit(true);
        this.formEditor = this.palette.editor;
        this.formEditor.resetFlex().relative((UIElement)this).full((UIElement)this);
        this.configureEditor();
        this.add((IUIElement)this.formEditor);
    }

    private void configureEditor() {
        if (this.formEditor == null) {
            return;
        }
        this.formEditor.renderer.setVisible(true);
        this.formEditor.renderer.grid = false;
        this.formEditor.renderer.updatable();
        Area gap = this.getGapArea();
        this.formEditor.renderer.area.set(gap.x, gap.y, gap.w, gap.h);
        this.formEditor.renderer.resetFlex().relative((UIElement)this).xy(gap.x, gap.y).wh(gap.w, gap.h);
        this.formEditor.renderer.setPosition(0.0f, 0.0f, 0.0f);
        this.formEditor.renderer.setDistance(0.0f);
        this.formEditor.renderer.setRotation(0.0f, 0.0f);
        this.formEditor.icons.setVisible(false);
        this.formEditor.statesEditor.setVisible(false);
        this.formEditor.finish.setVisible(false);
        this.formEditor.openStateEditor.setVisible(false);
        this.formEditor.bodyPartEditor.useTarget.setVisible(false);
        this.formEditor.forms.x(0);
        UIForms.FormEntry currentEntry = this.formEditor.formsList != null ? (UIForms.FormEntry)this.formEditor.formsList.getCurrentFirst() : null;
        boolean isRoot = currentEntry == null || currentEntry.part == null;
        Form currentForm = isRoot ? this.getRootForm() : (currentEntry != null ? currentEntry.getForm() : this.formEditor.form);
        String currentModel = currentForm instanceof ModelForm mf ? (String)mf.model.get() : "";
        UIForm uIForm = this.formEditor.editor;
        if (uIForm instanceof UIModelForm) {
            UIModelForm modelEditor = (UIModelForm)uIForm;
            if (modelEditor.modelPanel != null) {
                ModelInstance instance;
                ModelForm mf;
                if (isRoot) {
                    if (modelEditor.modelPanel.shapeKeysSection != null) {
                        modelEditor.modelPanel.shapeKeysSection.removeFromParent();
                    }
                    if (modelEditor.modelPanel.poseEditor != null && (currentForm != this.lastConfiguredForm || !Objects.equals(currentModel, this.lastConfiguredModel)) && currentForm instanceof ModelForm) {
                        mf = (ModelForm)currentForm;
                        UIPovHandEditor.filterModelPoseEditor(modelEditor.modelPanel.poseEditor, mf);
                    }
                } else if (modelEditor.modelPanel.poseEditor != null && (currentForm != this.lastConfiguredForm || !Objects.equals(currentModel, this.lastConfiguredModel)) && currentForm instanceof ModelForm && (instance = ModelFormRenderer.getModel((ModelForm)(mf = (ModelForm)currentForm))) != null && instance.getModel() != null) {
                    modelEditor.modelPanel.poseEditor.fillGroups((IBoneHierarchy)instance.getModel(), instance.getFlippedParts(), false, null);
                }
            }
        }
        this.lastConfiguredForm = currentForm;
        this.lastConfiguredModel = currentModel;
    }

    public static void filterModelPoseEditor(UIModelPoseEditor poseEditor, ModelForm modelForm) {
        if (poseEditor == null || modelForm == null) {
            return;
        }
        ModelInstance instance = ModelFormRenderer.getModel((ModelForm)modelForm);
        if (instance == null || instance.getModel() == null) {
            return;
        }
        HandBoneUtils.HandBones handBones = HandBoneUtils.collect(instance);
        if (handBones.depths().isEmpty()) {
            return;
        }
        HandBoneHierarchy filteredHierarchy = new HandBoneHierarchy((IBoneHierarchy)instance.getModel(), handBones);
        poseEditor.fillGroups((IBoneHierarchy)filteredHierarchy, instance.getFlippedParts(), false, null);
        String currentBone = poseEditor.getGroup();
        if (currentBone != null && !handBones.contains(currentBone)) {
            String defaultBone = handBones.mainRoot() != null && handBones.contains(handBones.mainRoot()) ? handBones.mainRoot() : handBones.depths().keySet().iterator().next();
            poseEditor.selectBone(defaultBone);
        }
    }

    public UIFilmPanel getFilmPanel() {
        return this.filmPanel;
    }

    public UIFormEditor getFormEditor() {
        return this.formEditor;
    }

    public Form getRootForm() {
        List list;
        if (this.formEditor != null && this.formEditor.formsList != null && (list = this.formEditor.formsList.getList()) != null && !list.isEmpty() && list.get(0) != null && ((UIForms.FormEntry)list.get(0)).getForm() != null) {
            return ((UIForms.FormEntry)list.get(0)).getForm();
        }
        if (this.formEditor != null && this.formEditor.form != null) {
            return FormUtils.getRoot((Form)this.formEditor.form);
        }
        return this.hand != null ? (Form)this.hand.baseForm.get() : null;
    }

    public Form getPreviewForm() {
        return this.getRootForm();
    }

    public static BodyPart findBodyPart(Form form) {
        if (form == null) {
            return null;
        }
        UIPovHandEditor editor = UIPovHandEditor.getActive();
        if (editor == null) {
            return null;
        }
        Form root = editor.getRootForm();
        if (root == null || root.parts == null) {
            return null;
        }
        for (BodyPart part : root.parts.getAllTyped()) {
            if (part.getForm() != form) continue;
            return part;
        }
        return null;
    }

    public static int findBodyPartIndex(Form form) {
        UIPovHandEditor editor = UIPovHandEditor.getActive();
        if (editor == null) {
            return -1;
        }
        Form root = editor.getRootForm();
        if (root == null || root.parts == null) {
            return -1;
        }
        int index = 0;
        for (BodyPart part : root.parts.getAllTyped()) {
            if (part.getForm() == form) {
                return index;
            }
            ++index;
        }
        return -1;
    }

    public static Matrix4f getBodyPartParent(BodyPart part) {
        Matrix4f parent = new Matrix4f();
        if (part == null) {
            return parent;
        }
        String bone = (String)part.bone.get();
        if (bone != null && !bone.isEmpty()) {
            Matrix4f boneMat = PovHandMatrices.getFull(bone);
            if (boneMat != null) {
                parent.set((Matrix4fc)boneMat);
            } else {
                parent.rotateY((float)Math.PI);
            }
        } else {
            parent.translate(0.0f, -0.75f, -1.2f);
            parent.rotateY((float)Math.toRadians(180.0));
        }
        return parent;
    }

    public static Matrix4f getBodyPartBase(BodyPart part) {
        Matrix4f base = UIPovHandEditor.getBodyPartParent(part);
        if (part != null && part.transform != null && part.transform.get() != null) {
            Matrix4f local = new Matrix4f();
            ((Transform)part.transform.get()).setupMatrix(local);
            base.mul((Matrix4fc)local);
        }
        return base;
    }

    public static Matrix4f getBodyPartBoneMatrix(ModelForm bodyPartForm, String bone, boolean local, Matrix4f bodyPartBase) {
        if (bodyPartForm == null || bone == null || bone.isEmpty()) {
            return bodyPartBase;
        }
        ModelInstance instance = ModelFormRenderer.getModel((ModelForm)bodyPartForm);
        if (instance == null || instance.getModel() == null) {
            return bodyPartBase;
        }
        MatrixCache matrices = new MatrixCache();
        instance.captureMatrices(matrices);
        MatrixCacheEntry entry = matrices.get(bone);
        if (entry != null) {
            Matrix4f boneMat;
            Matrix4f matrix4f = boneMat = local ? entry.matrix() : entry.origin();
            if (boneMat != null) {
                return new Matrix4f((Matrix4fc)bodyPartBase).rotateY((float)Math.PI).mul((Matrix4fc)boneMat);
            }
        }
        return bodyPartBase;
    }

    public static Matrix4f evaluateBodyPartBoneMatrix(ModelForm bodyPartForm, String bone, Matrix4f bodyPartBase) {
        return UIPovHandEditor.getBodyPartBoneMatrix(bodyPartForm, bone, true, bodyPartBase);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static Matrix4f evaluateBodyPartBoneMatrix(ModelForm bodyPartForm, String bone, Transform baseline, Transform edited, Matrix4f bodyPartBase) {
        if (bodyPartForm == null || bone == null || bone.isEmpty()) {
            return bodyPartBase;
        }
        ModelInstance instance = ModelFormRenderer.getModel((ModelForm)bodyPartForm);
        if (instance == null || instance.getModel() == null) {
            return bodyPartBase;
        }
        IModel model = instance.getModel();
        Pose pose = (Pose)bodyPartForm.pose.get();
        Pose originalPose = pose == null ? new Pose() : pose.copy();
        Pose working = originalPose.copy();
        if (baseline != null && edited != null) {
            PoseTransform transform = working.transforms.computeIfAbsent(bone, ignored -> new PoseTransform());
            transform.translate.add(edited.translate.x - baseline.translate.x, edited.translate.y - baseline.translate.y, edited.translate.z - baseline.translate.z);
            Transform rotationDelta = new Transform();
            rotationDelta.setModeQuaternion();
            rotationDelta.quat.set((Quaternionfc)baseline.createRotation()).invert().mul((Quaternionfc)edited.createRotation());
            transform.addRotation(rotationDelta);
        }
        try {
            model.resetPose();
            model.applyPose(working);
            MatrixCache matrices = new MatrixCache();
            instance.captureMatrices(matrices);
            MatrixCacheEntry entry = matrices.get(bone);
            if (entry != null && entry.matrix() != null) {
                Matrix4f matrix4f = new Matrix4f((Matrix4fc)bodyPartBase).rotateY((float)Math.PI).mul((Matrix4fc)entry.matrix());
                return matrix4f;
            }
        }
        finally {
            model.resetPose();
            model.applyPose(originalPose);
        }
        return bodyPartBase;
    }

    public void closeEditor() {
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel;
        if (active == this) {
            active = null;
        }
        if (this.filmPanel != null && this.filmPanel.getContext() != null) {
            this.filmPanel.getContext().closeContextMenu();
        }
        this.syncToBaseForm();
        this.removeFromParent();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            RenderSystem.viewport((int)0, (int)0, (int)client.getWindow().getFramebufferWidth(), (int)client.getWindow().getFramebufferHeight());
        }
        if (this.dashboard != null) {
            this.dashboard.resize(this.dashboard.width, this.dashboard.height);
        }
        if ((uIFilmPanel = this.filmPanel) instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$getEditor() != null) {
            access.bbsPov$getEditor().reloadHandModel();
        }
    }

    public void syncToBaseForm() {
        Form root = this.getRootForm();
        if (root != null && this.hand != null) {
            this.hand.baseForm.set(FormUtils.copy((Form)root));
        }
    }

    public Replay getReplay() {
        return this.replay;
    }

    public Area getGapArea() {
        int screenW = this.area.w > 0 ? this.area.w : MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = this.area.h > 0 ? this.area.h : MinecraftClient.getInstance().getWindow().getScaledHeight();
        int left = this.formEditor != null && this.formEditor.forms != null && this.formEditor.forms.isVisible() ? this.formEditor.forms.area.ex() : this.area.x;
        int right = this.area.ex() > 0 ? this.area.ex() : screenW;
        if (this.formEditor != null && this.formEditor.editor != null) {
            UIElement view = this.formEditor.editor.view;
            if (view instanceof UIFormPanel) {
                UIFormPanel panel = (UIFormPanel)view;
                if (panel.options != null && panel.options.isVisible()) {
                    right = panel.options.area.x;
                }
            } else if (this.formEditor.editor instanceof UIModelForm) {
                UIModelForm modelEditor = (UIModelForm)this.formEditor.editor;
                if (modelEditor.modelPanel != null && modelEditor.modelPanel.options != null && modelEditor.modelPanel.options.isVisible()) {
                    right = modelEditor.modelPanel.options.area.x;
                }
            }
        }
        if (left <= 0) {
            left = (int)((float)screenW * 0.15f);
        }
        if (right >= screenW || right <= left) {
            right = (int)((float)screenW * 0.85f);
        }
        int gapX = left;
        int gapY = this.area.y;
        int gapW = Math.max(1, right - left);
        int gapH = Math.max(1, this.area.h > 0 ? this.area.h : screenH);
        return new Area(gapX, gapY, gapW, gapH);
    }

    public Area getFrameArea() {
        Area gap = this.getGapArea();
        int frameW = gap.w;
        float aspect = 1.7777778f;
        int frameH = Math.round((float)frameW / aspect);
        if (frameH > gap.h) {
            frameH = gap.h;
            frameW = Math.round((float)frameH * aspect);
        }
        int frameX = gap.x + (gap.w - frameW) / 2;
        int frameY = gap.y + (gap.h - frameH) / 2;
        return new Area(frameX, frameY, frameW, frameH);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void render(UIContext context) {
        this.configureEditor();
        context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), -15198184);
        Area gap = this.getGapArea();
        Area frame = this.getFrameArea();
        if (this.formEditor != null && this.formEditor.renderer != null) {
            float fovY;
            this.formEditor.renderer.area.set(gap.x, gap.y, gap.w, gap.h);
            if (this.formEditor.renderer.getGizmoStencil() != null) {
                this.formEditor.renderer.getGizmoStencil().resizeGUI(gap.w, gap.h);
            }
            float halfTan = (float)Math.tan(Math.toRadians(35.0));
            this.formEditor.renderer.camera.fov = fovY = 2.0f * (float)Math.atan(halfTan * ((float)gap.h / (float)frame.h));
        }
        boolean prevLight = BBSSettings.lightInputs;
        BBSSettings.lightInputs = true;
        try {
            super.render(context);
        }
        finally {
            BBSSettings.lightInputs = prevLight;
        }
        if (!this.isFormPickerOpen()) {
            if (frame.y > gap.y) {
                context.batcher.box((float)gap.x, (float)gap.y, (float)gap.ex(), (float)frame.y, -2013265920);
            }
            if (frame.ey() < gap.ey()) {
                context.batcher.box((float)gap.x, (float)frame.ey(), (float)gap.ex(), (float)gap.ey(), -2013265920);
            }
            if (frame.x > gap.x) {
                context.batcher.box((float)gap.x, (float)frame.y, (float)frame.x, (float)frame.ey(), -2013265920);
            }
            if (frame.ex() < gap.ex()) {
                context.batcher.box((float)frame.ex(), (float)frame.y, (float)gap.ex(), (float)frame.ey(), -2013265920);
            }
            context.batcher.outline((float)frame.x, (float)frame.y, (float)frame.ex(), (float)frame.ey(), -1996488705);
        }
    }

    public boolean isFormPickerOpen() {
        if (this.formEditor != null) {
            for (UIFormList list : this.formEditor.getChildren(UIFormList.class)) {
                if (!list.isVisible()) continue;
                return true;
            }
        }
        for (UIFormList list : this.getChildren(UIFormList.class)) {
            if (!list.isVisible()) continue;
            return true;
        }
        if (this.palette != null) {
            for (UIFormList list : this.palette.getChildren(UIFormList.class)) {
                if (!list.isVisible()) continue;
                return true;
            }
        }
        return false;
    }

    public boolean subMouseReleased(UIContext context) {
        this.syncToBaseForm();
        return super.subMouseReleased(context);
    }

    public boolean subKeyPressed(UIContext context) {
        if (context.isPressed(256)) {
            this.closeEditor();
            return true;
        }
        return super.subKeyPressed(context);
    }
}

