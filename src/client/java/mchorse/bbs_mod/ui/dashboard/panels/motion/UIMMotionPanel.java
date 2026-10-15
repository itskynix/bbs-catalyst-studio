package mchorse.bbs_mod.ui.dashboard.panels.motion;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelTopBar;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import java.util.UUID;
import java.util.Objects;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformNumericInput;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.forms.structure.StructureRenderData;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPicker;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.InterpolationUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import org.lwjgl.glfw.GLFW;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.KeyframeType;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import com.mojang.blaze3d.platform.GlStateManager;
import mchorse.bbs_mod.ui.utils.renderers.EmptyStateRenderer;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * mMotion Studio — Motion Graphics Editor Dashboard Panel
 *
 * Dedicated motion graphics panel for Minecraft structures, blocks, items, and entities
 * with isolated 3D studio viewport, scene layer hierarchy, transform properties,
 * native keyframe timeline (UIKeyframes/DopeSheet), and independent creative hub.
 */
public class UIMMotionPanel extends UIEditorDashboardPanel
{
    /* ── Scene Data Model ── */
    public static class MotionScene
    {
        public String id;
        public String name;
        public int duration = 600;
        public int fps = 60;
        public int width = 1920;
        public int height = 1080;
        public long lastModified;
        public final List<MotionLayer> layers = new ArrayList<>();

        public MotionScene(String id, String name, int duration)
        {
            this.id = id;
            this.name = name;
            this.duration = duration;
            this.lastModified = System.currentTimeMillis();
        }

        public MotionScene copy()
        {
            MotionScene c = new MotionScene("scene_" + System.currentTimeMillis(), this.name + " (Kopya)", this.duration);
            c.fps = this.fps;
            c.width = this.width;
            c.height = this.height;
            for (MotionLayer l : this.layers)
            {
                c.layers.add(l.copy());
            }
            return c;
        }
    }

    /* ── Layer Data Model ── */
    public static class MotionLayer
    {
        public String id = UUID.randomUUID().toString().substring(0, 8);
        public String parentId = null;
        public boolean collapsed = false;

        public String name;
        public String type;
        public Icon icon;
        public boolean visible = true;
        public boolean locked = false;
        public int color;
        public String defaultName;
        public int defaultColor;

        /* Live transform properties */
        public double posX = 0.0, posY = 0.0, posZ = 0.0;
        public double rotPitch = 0.0, rotYaw = 0.0, rotRoll = 0.0;
        public double scaleX = 1.0, scaleY = 1.0, scaleZ = 1.0;
        public double opacity = 1.0;

        /* Color overlay (multiplied onto the actor's own colours), driven by the Coloring keyframes */
        public float tintR = 1F;
        public float tintG = 1F;
        public float tintB = 1F;
        public boolean shading = true;

        /* Actor Specific Properties */
        public int blockLight = 15;
        public int itemCount = 1;
        public boolean itemGlint = false;
        public ModelTransformationMode itemTransformMode = ModelTransformationMode.FIXED;
        public String entityModel = "";
        public double entityPose = 0.0;
        public String structureName = "";
        public double structureOffsetX = 0.0, structureOffsetY = 0.0, structureOffsetZ = 0.0;
        public int lightType = 0; // 0: Key Light, 1: Fill Light, 2: Rim Light, 3: Ambient Light
        public double lightIntensity = 1.0;
        public int lightColor = 0xFFFBBF24;

        /* Scene lights (PointLight / SpotLight / RectLight): range in blocks, cone in degrees, softness 0..1, rect size in blocks. Forward is local -Y */
        public double lightRange = 8.0;
        public double lightCone = 45.0;
        public double lightPenumbra = 0.3;
        public double lightWidth = 2.0;
        public double lightHeight = 2.0;
        public double lightGlow = 0.5; // Glow / Beam Opacity (Point glow aura, Spot beam), 0 = off

        /* Camera Actor Properties (static camera: position / rotation live in the transform channel) */
        public double cameraFov = 70.0;
        public boolean renderCamera = false;

        /* Real Render Assets */
        public BlockState blockState;
        public ItemStack itemStack;
        public Form form;

        /* Native Keyframe Channels */
        public final KeyframeChannel<Transform> channelTransform = new KeyframeChannel<>("transform", KeyframeFactories.TRANSFORM);

        /* Secondary transform stacked on top of the main one (shake, nod, extra spin); empty = no overlay */
        public final KeyframeChannel<Transform> channelTransformOverlay = new KeyframeChannel<>("transform_overlay", KeyframeFactories.TRANSFORM);

        /* Overlay sampled at the current frame (null while the overlay channel is empty) */
        public Transform overlay;
        public final KeyframeChannel<Double> channelPose = new KeyframeChannel<>("pose", KeyframeFactories.DOUBLE);

        /* Scene light animation (PointLight / SpotLight / RectLight); the colour uses the "Coloring" channel */
        public final KeyframeChannel<Double> channelIntensity = new KeyframeChannel<>("light_intensity", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Double> channelRange = new KeyframeChannel<>("light_range", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Double> channelCone = new KeyframeChannel<>("light_cone", KeyframeFactories.DOUBLE);
        public final KeyframeChannel<Color> channelColor = new KeyframeChannel<>("color", KeyframeFactories.COLOR);
        public final KeyframeChannel<Double> channelVisible = new KeyframeChannel<>("visible", KeyframeFactories.DOUBLE);

        public MotionLayer(String name, String type, Icon icon, int color)
        {
            this.name = name;
            this.type = type;
            this.icon = icon;
            this.color = color;

            if ("Block".equalsIgnoreCase(type))
            {
                this.blockState = Blocks.GRASS_BLOCK.getDefaultState();
                BlockForm bf = new BlockForm();
                bf.blockState.set(this.blockState);
                this.form = bf;
            }
            else if ("Item".equalsIgnoreCase(type))
            {
                this.itemStack = new ItemStack(Items.DIAMOND_SWORD);
                ItemForm iform = new ItemForm();
                iform.stack.set(this.itemStack);
                this.form = iform;
            }
            else if ("Entity".equalsIgnoreCase(type))
            {
                this.form = new ModelForm();
            }
            else if ("Structure".equalsIgnoreCase(type))
            {
                this.blockState = Blocks.STRUCTURE_BLOCK.getDefaultState();
                StructureForm sf = new StructureForm();
                this.form = sf;
            }
            else if (UIMMotionPanel.isLightType(type))
            {
                this.color = 0xFFFBBF24;
                this.lightColor = 0xFFFBBF24;
            }
            else if ("Camera".equalsIgnoreCase(type))
            {
                this.color = 0xFF60A5FA;
                this.cameraFov = 70.0;
            }

            /* Initial clean default transform */
            this.posX = 0.0;
            this.posY = 0.0;
            this.posZ = 0.0;
            this.rotPitch = 0.0;
            this.rotYaw = 0.0;
            this.rotRoll = 0.0;
            this.scaleX = 1.0;
            this.scaleY = 1.0;
            this.scaleZ = 1.0;
            this.opacity = 1.0;
            this.tintR = 1F;
            this.tintG = 1F;
            this.tintB = 1F;
            this.shading = true;

            this.channelTransform.getAllTyped().clear();
            this.channelTransformOverlay.getAllTyped().clear();
            this.channelPose.getAllTyped().clear();
            this.channelIntensity.getAllTyped().clear();
            this.channelRange.getAllTyped().clear();
            this.channelCone.getAllTyped().clear();
            this.channelColor.getAllTyped().clear();
            this.channelVisible.getAllTyped().clear();

            /* The transform channel starts EMPTY: static pos / rot / scale live in the layer fields until the user adds a keyframe */
            if ("Camera".equalsIgnoreCase(type))
            {
                /* New cameras start at (0, 2, -5) looking towards +Z (the scene origin) */
                this.posX = 0.0;
                this.posY = 2.0;
                this.posZ = -5.0;
            }
            else if (UIMMotionPanel.isLightType(type))
            {
                this.posX = 0.0;
                this.posY = 3.0;
                this.posZ = 0.0;
            }
            else
            {
                this.posX = 0.0;
                this.posY = 0.0;
                this.posZ = 0.0;
            }

            this.rotPitch = 0.0;
            this.rotYaw = 0.0;
            this.rotRoll = 0.0;
            this.scaleX = 1.0;
            this.scaleY = 1.0;
            this.scaleZ = 1.0;

            this.channelPose.insert(0, 0.0);

            if (UIMMotionPanel.isSceneLightType(type))
            {
                this.channelIntensity.insert(0, this.lightIntensity);
                this.channelRange.insert(0, this.lightRange);
                this.channelCone.insert(0, this.lightCone);
            }
            this.channelColor.insert(0, new Color(1F, 1F, 1F, 1F));
            this.channelVisible.insert(0, 1.0);
            this.enforceVisibleHold();

            this.defaultName = name;
            this.defaultColor = this.color;
        }

        /* Visibility keyframes are always Hold (constant): a visibility toggle never fades, whatever interpolation was forced on it */
        public void enforceVisibleHold()
        {
            for (Keyframe<Double> keyframe : this.channelVisible.getKeyframes())
            {
                if (keyframe.getInterpolation().getInterp() != Interpolations.CONST)
                {
                    keyframe.getInterpolation().setInterp(Interpolations.CONST);
                }
            }
        }

        /* Hold sampling: the value of the latest visibility keyframe at or before the tick */
        public boolean isVisibleAt(float tick)
        {
            Keyframe<Double> chosen = null;
            Keyframe<Double> earliest = null;

            for (Keyframe<Double> keyframe : this.channelVisible.getKeyframes())
            {
                if (earliest == null || keyframe.getTick() < earliest.getTick())
                {
                    earliest = keyframe;
                }

                if (keyframe.getTick() <= tick + 0.0001F && (chosen == null || keyframe.getTick() >= chosen.getTick()))
                {
                    chosen = keyframe;
                }
            }

            if (chosen == null)
            {
                chosen = earliest;
            }

            return chosen == null ? this.visible : chosen.getValue() > 0.5;
        }

        public MotionLayer copy()
        {
            MotionLayer copy = new MotionLayer(this.name, this.type, this.icon, this.color);
            copy.id = UUID.randomUUID().toString().substring(0, 8);
            copy.parentId = this.parentId;
            copy.collapsed = this.collapsed;
            copy.defaultName = this.defaultName;
            copy.defaultColor = this.defaultColor;
            copy.visible = this.visible;
            copy.locked = this.locked;
            copy.posX = this.posX;
            copy.posY = this.posY;
            copy.posZ = this.posZ;
            copy.rotPitch = this.rotPitch;
            copy.rotYaw = this.rotYaw;
            copy.rotRoll = this.rotRoll;
            copy.scaleX = this.scaleX;
            copy.scaleY = this.scaleY;
            copy.scaleZ = this.scaleZ;
            copy.opacity = this.opacity;
            copy.tintR = this.tintR;
            copy.tintG = this.tintG;
            copy.tintB = this.tintB;
            copy.shading = this.shading;

            copy.blockLight = this.blockLight;
            copy.itemCount = this.itemCount;
            copy.itemGlint = this.itemGlint;
            copy.itemTransformMode = this.itemTransformMode;
            copy.entityModel = this.entityModel;
            copy.entityPose = this.entityPose;
            copy.structureName = this.structureName;
            copy.structureOffsetX = this.structureOffsetX;
            copy.structureOffsetY = this.structureOffsetY;
            copy.structureOffsetZ = this.structureOffsetZ;
            copy.lightType = this.lightType;
            copy.lightIntensity = this.lightIntensity;
            copy.lightColor = this.lightColor;
        copy.lightRange = this.lightRange;
        copy.lightCone = this.lightCone;
        copy.lightPenumbra = this.lightPenumbra;
        copy.lightWidth = this.lightWidth;
        copy.lightHeight = this.lightHeight;
        copy.lightGlow = this.lightGlow;
            copy.cameraFov = this.cameraFov;
            copy.renderCamera = false;

            copy.blockState = this.blockState;
            copy.itemStack = this.itemStack != null ? this.itemStack.copy() : null;
            copy.form = this.form != null ? FormUtils.copy(this.form) : null;

            copy.channelTransform.copyKeyframes(this.channelTransform);
            copy.channelTransformOverlay.copyKeyframes(this.channelTransformOverlay);
            copy.overlay = this.overlay != null ? this.overlay.copy() : null;
            copy.channelPose.copyKeyframes(this.channelPose);
        copy.channelIntensity.copyKeyframes(this.channelIntensity);
        copy.channelRange.copyKeyframes(this.channelRange);
        copy.channelCone.copyKeyframes(this.channelCone);
            copy.channelColor.copyKeyframes(this.channelColor);
            copy.channelVisible.copyKeyframes(this.channelVisible);

            return copy;
        }
    }

    /* ── Real 3D Studio Viewport (Inherited from UIModelRenderer) ── */
    public class UIMMotionViewport extends UIModelRenderer implements GizmoViewport
    {
        public static final int MODAL_NONE = 0;
        public static final int MODAL_TRANSLATE = 1;
        public static final int MODAL_ROTATE = 2;
        public static final int MODAL_SCALE = 3;

        private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
        private final StencilMap stencilMap = new StencilMap();

        /* Actor id pass (hover / select outline, click picking) */
        private boolean idPass;
        private int idValue;
        private MotionLayer hoveredLayer;
        private final GizmoInteraction gizmo = new GizmoInteraction(this);

        @Override
        public StencilFormFramebuffer getGizmoStencil()
        {
            return this.stencil;
        }

        @Override
        public Matrix4f getGizmoProjection()
        {
            return this.camera.projection;
        }

        @Override
        public Area getGizmoArea()
        {
            return this.area;
        }

        @Override
        public boolean startGizmo(UIContext context, int stencilIndex)
        {
            MotionLayer layer = UIMMotionPanel.this.getSelected();
            if (layer == null)
            {
                return false;
            }

            UIMMotionPanel.this.syncLiveTransformFromLayer(layer);
            UIMMotionPanel.this.transformEditor.setTransform(UIMMotionPanel.this.liveTransform);

            return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, UIMMotionPanel.this.transformEditor, this.buildGizmoDrag());
        }

        @Override
        public void pickGizmoForm(UIContext context, mchorse.bbs_mod.forms.forms.Form form, String bone)
        {
        }

        public GizmoDrag buildGizmoDrag()
        {
            MotionLayer layer = UIMMotionPanel.this.getSelected();
            if (layer == null)
            {
                return null;
            }

            GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.camera, this.getGizmoArea());
            Transform transform = UIMMotionPanel.this.liveTransform;

            if (drag != null && transform != null)
            {
                MotionLayer parent = layer.parentId != null ? UIMMotionPanel.this.findLayerById(layer.parentId) : null;
                Matrix4f parentWorld = parent != null ? UIMMotionPanel.this.getWorldMatrix(parent) : null;

                drag.setJacobian(GizmoDrag.computeTranslateJacobian(
                    transform,
                    () -> {
                        Vector3f localPos = new Vector3f(transform.translate);
                        if (parentWorld != null)
                        {
                            return parentWorld.transformPosition(localPos, new Vector3f());
                        }
                        return localPos;
                    }
                ));
                drag.setRotateAxes(GizmoDrag.computeRotateAxes(
                    transform,
                    () -> {
                        Matrix4f localMat = transform.createMatrix();
                        if (parentWorld != null)
                        {
                            return MatrixStackUtils.stripScale(new Matrix4f(parentWorld).mul(localMat));
                        }
                        return MatrixStackUtils.stripScale(localMat);
                    }
                ));

                Matrix4f localRot = new Matrix4f().set(transform.createRotationMatrix());
                Matrix4f parentRot = parentWorld != null ? new Matrix4f().set(parentWorld.get3x3(new Matrix3f())) : new Matrix4f();
                drag.setFrameAxes(localRot, parentRot);
            }

            return drag;
        }

        public boolean isModalActive()
        {
            return UIMMotionPanel.this.transformEditor != null && UIMMotionPanel.this.transformEditor.isEditing();
        }

        public boolean isGizmoDragging()
        {
            return this.isModalActive();
        }

        public boolean isEditing()
        {
            return this.isModalActive();
        }

        public void resetGizmoState()
        {
            this.gizmo.stop();
            if (UIMMotionPanel.this.transformEditor != null && UIMMotionPanel.this.transformEditor.isEditing())
            {
                UIMMotionPanel.this.transformEditor.getGesture().reject();
            }
        }

        public void startModalTransform(int op, UIContext context)
        {
            if (UIMMotionPanel.this.transformEditor == null) return;
            MotionLayer layer = UIMMotionPanel.this.getSelected();
            if (layer == null) return;
            UIMMotionPanel.this.syncLiveTransformFromLayer(layer);
            UIMMotionPanel.this.transformEditor.setTransform(UIMMotionPanel.this.liveTransform);

            if (op == MODAL_TRANSLATE)
            {
                UIMMotionPanel.this.transformEditor.getGesture().enableMode(mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp.TRANSLATE);
            }
            else if (op == MODAL_ROTATE)
            {
                UIMMotionPanel.this.transformEditor.getGesture().enableMode(mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp.ROTATE);
            }
            else if (op == MODAL_SCALE)
            {
                UIMMotionPanel.this.transformEditor.getGesture().enableMode(mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp.SCALE);
            }
        }

        public boolean handleModalKey(UIContext context)
        {
            return UIMMotionPanel.this.transformEditor != null && UIMMotionPanel.this.transformEditor.keyPressed(context) != null;
        }

        @Override
        public void resize()
        {
            super.resize();
            this.stencil.setup(Link.bbs("stencil_motion"));
            this.stencil.resizeGUI(this.area.w, this.area.h);
        }

        public UIMMotionViewport()
        {
            super();
            this.grid = true;
            this.reset();
            this.setPosition(0, 0.5F, 0);
            this.setDistance(4.5F);
            this.setRotation(45, 30);
        }

        @Override
        public void render(UIContext context)
        {
            super.render(context);

            this.renderActorOutlines(context);
            this.renderModeLabel(context);
            this.gizmo.renderSphereHighlight(context);
            this.gizmo.renderReadout(context);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context))
            {
                if (this.gizmo.mouseClicked(context))
                {
                    return true;
                }

                if (context.mouseButton == 0 && this.hoveredLayer != null)
                {
                    UIMMotionPanel.this.selectLayer(this.hoveredLayer);

                    return true;
                }

                if (context.mouseButton == 0)
                {
                    for (int i = UIMMotionPanel.this.layers.size() - 1; i >= 0; i--)
                    {
                        MotionLayer l = UIMMotionPanel.this.layers.get(i);
                        Vector3d wp = UIMMotionPanel.this.getWorldPosition(l);
                        Vector2f sp = this.projectToScreen(wp.x, wp.y + 0.5, wp.z);
                        if (sp != null && Math.hypot(context.mouseX - sp.x, context.mouseY - sp.y) < 22)
                        {
                            UIMMotionPanel.this.selectLayer(l);
                            return true;
                        }
                    }
                }
            }

            return super.subMouseClicked(context);
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            if (this.gizmo.mouseReleased(context))
            {
                return true;
            }

            return super.subMouseReleased(context);
        }

        private int actorIndex(MotionLayer layer)
        {
            java.util.List<MotionLayer> all = UIMMotionPanel.this.layers;

            for (int i = 0; i < all.size(); i++)
            {
                if (all.get(i) == layer)
                {
                    return i;
                }
            }

            return -1;
        }

        private void updateHoveredLayer(UIContext context)
        {
            this.hoveredLayer = null;

            if (!this.area.isInside(context) || this.isModalActive())
            {
                return;
            }

            int picked = this.stencil.getIndex();

            if (picked > Gizmo.STENCIL_MAX)
            {
                int i = picked - Gizmo.STENCIL_MAX - 1;
                java.util.List<MotionLayer> all = UIMMotionPanel.this.layers;

                if (i >= 0 && i < all.size())
                {
                    this.hoveredLayer = all.get(i);
                }
            }
        }

        private boolean exportMode;

        /* Export pass: glow / beam volumes are drawn after every actor so they composite over (and not behind) the geometry */
        private final List<Object[]> pendingVolumes = new ArrayList<>();

        private void flushLightVolumes()
        {
            if (this.pendingVolumes.isEmpty()) return;

            for (Object[] entry : this.pendingVolumes)
            {
                MotionLayer l = (MotionLayer) entry[0];
                MatrixStack s = new MatrixStack();

                s.peek().getPositionMatrix().set((Matrix4f) entry[1]);
                this.renderLightVolume(s, l, (float) Math.max(0.1, l.lightRange));
            }

            this.pendingVolumes.clear();
        }

        /* Re-creates the picking buffer after the panel released it (setup is a no-op while a buffer exists) */
        private void ensureStencil()
        {
            if (this.stencil.getFramebuffer() == null)
            {
                this.stencil.setup(Link.bbs("stencil_motion"));
                this.stencil.resizeGUI(this.area.w, this.area.h);
            }
        }

        /* Panel closed / hidden: ends gizmo and gestures, restores GL state and frees the picking framebuffer */
        public void releaseResources()
        {
            this.resetGizmoState();
            this.pendingVolumes.clear();
            this.hoveredLayer = null;
            this.idPass = false;

            try
            {
                org.lwjgl.opengl.GL11.glPolygonMode(org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK, org.lwjgl.opengl.GL11.GL_FILL);
                RenderSystem.lineWidth(1F);
                this.resetRenderState();
            }
            finally
            {
                mchorse.bbs_mod.graphics.Framebuffer buffer = this.stencil.getFramebuffer();

                if (buffer != null)
                {
                    mchorse.bbs_mod.BBSModClient.getFramebuffers().framebuffers.remove(Link.bbs("stencil_motion"));
                    buffer.delete();
                    this.stencil = new StencilFormFramebuffer();
                }
            }
        }

        /** The visible (colour) pass of every root layer, wrapped in the active viewport mode. */        private void drawVisibleLayers(UIContext context, MatrixStack stack, VertexConsumerProvider.Immediate consumers, int light, int overlay)
        {
            this.resetRenderState();

            try
            {
                this.beginViewportMode();

                for (MotionLayer root : UIMMotionPanel.this.getRootLayers())
                {
                    this.renderLayerRecursive(context, stack, consumers, light, overlay, root);
                }

                /* Flush while the mode is still active: wireframe is a GL state, not a per-draw flag */
                consumers.draw();

                this.flushLightVolumes();
            }
            finally
            {
                try
                {
                    this.endViewportMode();
                }
                finally
                {
                    /* Nothing of the scene (colour, blend, depth, lightmap) may leak into the vanilla GUI, items and text */
                    try
                    {
                        consumers.draw();
                    }
                    finally
                    {
                        this.resetRenderState();
                    }
                }
            }
        }

        /* Default GUI / world state: white shader colour, standard blending, depth test and culling on, full colour mask */
        private void resetRenderState()
        {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
        }

        /**
         * Lit: the studio lights the viewport was set up with. Unlit: two opposed lights on the
         * cube diagonal, so every axis-aligned face gets the same brightness regardless of its
         * direction. Wireframe: unlit plus line polygon mode.
         */
        private void beginViewportMode()
        {
            int mode = UIMMotionPanel.this.viewportMode;

            if (mode == 1 || mode == 2)
            {
                Vector3f diagonal = new Vector3f(1F, 1F, 1F).normalize();

                RenderSystem.setupLevelDiffuseLighting(diagonal, new Vector3f(diagonal).negate(), this.camera.view);
            }

            if (mode == 2)
            {
                org.lwjgl.opengl.GL11.glPolygonMode(org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK, org.lwjgl.opengl.GL11.GL_LINE);
                RenderSystem.lineWidth(1.5F);
            }
        }

        private void endViewportMode()
        {
            int mode = UIMMotionPanel.this.viewportMode;

            /* Always back to filled polygons, whatever mode was active when the pass started */
            org.lwjgl.opengl.GL11.glPolygonMode(org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK, org.lwjgl.opengl.GL11.GL_FILL);
            RenderSystem.lineWidth(1F);

            if (mode == 1 || mode == 2)
            {
                RenderSystem.setupLevelDiffuseLighting(
                    new Vector3f(0, 0.85F, -1).normalize(),
                    new Vector3f(0, 0.85F, 1).normalize(),
                    this.camera.view
                );
            }
        }

        /**
         * Draws the scene (no grid, gizmo or helpers) into {@code fb} from the editor camera at the
         * framebuffer's own resolution and returns the picture. The caller owns the image.
         */
        public net.minecraft.client.texture.NativeImage renderExportFrame(UIContext context, net.minecraft.client.gl.SimpleFramebuffer fb, boolean transparent)
        {
            this.drawExportFrame(context, fb, transparent);

            net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage(fb.textureWidth, fb.textureHeight, false);

            RenderSystem.bindTexture(fb.getColorAttachment());
            image.loadFromTextureImage(0, !transparent);
            image.mirrorVertically();

            this.endExportFrame(fb);

            return image;
        }

        /**
         * Same picture as {@link #renderExportFrame}, but copied by the driver in one go into
         * {@code out} (a direct buffer of {@code w * h * 4} bytes) as raw RGBA, rows bottom-up as GL
         * stores them. No image object, no per-pixel work on the CPU.
         */
        public void renderExportFrameRgba(UIContext context, net.minecraft.client.gl.SimpleFramebuffer fb, boolean transparent, java.nio.ByteBuffer out)
        {
            this.drawExportFrame(context, fb, transparent);

            try
            {
                out.clear();
                RenderSystem.bindTexture(fb.getColorAttachment());
                GlStateManager._pixelStore(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 4);
                org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, out);
            }
            finally
            {
                this.endExportFrame(fb);
            }
        }

        private void endExportFrame(net.minecraft.client.gl.SimpleFramebuffer fb)
        {
            fb.endWrite();
            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
            GlStateManager._enableScissorTest();
        }

        /** Draws the export picture into {@code fb} and leaves it bound for the read back. */
        private void drawExportFrame(UIContext context, net.minecraft.client.gl.SimpleFramebuffer fb, boolean transparent)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = fb.textureWidth;
            int h = fb.textureHeight;

            context.batcher.flush();
            GlStateManager._disableScissorTest();

            if (transparent)
            {
                fb.setClearColor(0F, 0F, 0F, 0F);
            }
            else
            {
                fb.setClearColor(0.07F, 0.08F, 0.10F, 1F);
            }
            fb.clear(MinecraftClient.IS_SYSTEM_MAC);
            fb.beginWrite(true);

            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);

            this.setupPosition();
            this.camera.updatePerspectiveProjection(w, h);
            this.camera.updateView();

            /* The final render looks through the render camera actor when the scene has one */
            MotionLayer renderCam = UIMMotionPanel.this.getRenderCamera();

            if (renderCam != null)
            {
                this.applyCameraActor(renderCam, w, h);
            }

            MatrixStack stack = context.render.batcher.getContext().getMatrices();
            VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

            MatrixStackUtils.cacheMatrices();
            RenderSystem.setProjectionMatrix(this.camera.projection, com.mojang.blaze3d.systems.VertexSorter.BY_Z);
            RenderSystem.setInverseViewRotationMatrix(new org.joml.Matrix3f(this.camera.view).invert());

            stack.push();
            MatrixStackUtils.multiply(stack, this.camera.view);
            stack.translate(-this.camera.position.x, -this.camera.position.y, -this.camera.position.z);

            RenderSystem.setupLevelDiffuseLighting(
                new Vector3f(0, 0.85F, -1).normalize(),
                new Vector3f(0, 0.85F, 1).normalize(),
                this.camera.view
            );

            this.exportMode = true;

            try
            {
                this.drawVisibleLayers(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            }
            finally
            {
                this.exportMode = false;
                stack.pop();
                MatrixStackUtils.restoreMatrices();
            }
        }

        private boolean fovOverridden;

        /** Puts the viewport camera exactly where a camera actor is: its position, its rotation (local +Z is the looking direction) and its FOV. */
        private void applyCameraActor(MotionLayer cam, int w, int h)
        {
            Matrix4f world = UIMMotionPanel.this.getWorldMatrix(cam);
            Vector3f p = world.getTranslation(new Vector3f());
            Matrix3f rot = MatrixStackUtils.stripScale(new Matrix4f(world)).get3x3(new Matrix3f());

            this.camera.position.set(p.x, p.y, p.z);
            this.camera.setFov((float) MathUtils.clamp(cam.cameraFov, 10.0, 150.0));
            this.fovOverridden = true;
            this.camera.updatePerspectiveProjection(Math.max(1, w), Math.max(1, h));
            this.camera.view.identity().rotateY((float) Math.PI).mul(new Matrix4f().set(rot).invert());
        }

        @Override
        protected void setupViewport(UIContext context)
        {
            MotionLayer cam = UIMMotionPanel.this.getViewCamera();

            if (cam == null && this.fovOverridden)
            {
                this.camera.setFov(70F);
                this.fovOverridden = false;
            }

            super.setupViewport(context);

            if (cam != null)
            {
                int[] viewport = UIUtils.viewportArea(this.area);

                this.applyCameraActor(cam, viewport[2], viewport[3]);
            }
        }

        private static void cameraLine(BufferBuilder bld, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a)
        {
            bld.vertex(m, x1, y1, z1).color(r, g, b, a).next();
            bld.vertex(m, x2, y2, z2).color(r, g, b, a).next();
        }

        /** Camera actor marker: a small camera body plus the wire frame pyramid of its frustum (local +Z is forward). */
        private void renderCameraBody(MatrixStack stack, MotionLayer layer, boolean selected)
        {
            MotionScene scene = UIMMotionPanel.this.currentScene;
            float aspect = scene != null && scene.height > 0 ? scene.width / (float) scene.height : 16F / 9F;
            float depth = 1.4F;
            float halfH = (float) Math.tan(Math.toRadians(MathUtils.clamp(layer.cameraFov, 10.0, 150.0)) / 2.0) * depth;
            float halfW = halfH * aspect;
            int base = layer.color & 0xFFFFFF;
            float r = selected ? 1F : Colors.getR(base);
            float g = selected ? 0.85F : Colors.getG(base);
            float b = selected ? 0.2F : Colors.getB(base);
            float a = selected ? 1F : 0.9F;

            BufferBuilder bld = Tessellator.getInstance().getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            /* Body, lens and viewfinder */
            bld.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.fillBox(bld, stack, -0.12F, -0.09F, -0.3F, 0.12F, 0.09F, 0F, 0xFF000000 | base);
            Draw.fillBox(bld, stack, -0.05F, -0.05F, 0F, 0.05F, 0.05F, 0.1F, 0xFF1F2937);
            Draw.fillBox(bld, stack, -0.04F, 0.09F, -0.22F, 0.04F, 0.14F, -0.1F, 0xFF000000 | base);
            BufferRenderer.drawWithGlobalProgram(bld.end());

            /* Selection highlight / body outline */
            Draw.renderBox(stack, -0.12F, -0.09F, -0.3F, 0.24F, 0.18F, 0.3F, r, g, b, a);

            /* Frustum pyramid: apex to the four corners of the far plane, the far rectangle and an "up" marker */
            RenderSystem.lineWidth(selected ? 2.5F : 1.5F);

            Matrix4f m = stack.peek().getPositionMatrix();
            float[][] c = {{-halfW, -halfH}, {halfW, -halfH}, {halfW, halfH}, {-halfW, halfH}};

            bld.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            for (int i = 0; i < 4; i++)
            {
                float[] p = c[i];
                float[] q = c[(i + 1) % 4];

                cameraLine(bld, m, 0F, 0F, 0F, p[0], p[1], depth, r, g, b, a * 0.8F);
                cameraLine(bld, m, p[0], p[1], depth, q[0], q[1], depth, r, g, b, a);
            }

            cameraLine(bld, m, -halfW * 0.25F, halfH, depth, 0F, halfH * 1.3F, depth, r, g, b, a);
            cameraLine(bld, m, 0F, halfH * 1.3F, depth, halfW * 0.25F, halfH, depth, r, g, b, a);

            BufferRenderer.drawWithGlobalProgram(bld.end());
            RenderSystem.lineWidth(1F);
        }

        private void beamCone(BufferBuilder bld, Matrix4f m, float length, float radius, float r, float g, float b, float alpha)
        {
            int seg = 32;

            for (int i = 0; i < seg; i++)
            {
                double a0 = Math.PI * 2.0 * i / seg;
                double a1 = Math.PI * 2.0 * (i + 1) / seg;

                bld.vertex(m, 0F, 0F, 0F).color(r, g, b, alpha).next();
                bld.vertex(m, (float) Math.cos(a0) * radius, -length, (float) Math.sin(a0) * radius).color(r, g, b, 0F).next();
                bld.vertex(m, (float) Math.cos(a1) * radius, -length, (float) Math.sin(a1) * radius).color(r, g, b, 0F).next();
            }
        }

        /*
         * Volumetric helpers, Lit mode only: a soft additive beam for spot lights (bright at the source, fading towards the
         * far end and the cone edges) and a camera facing glow aura for point lights. Opacity follows Glow / Beam Opacity
         * and the light intensity.
         */
        private void renderLightVolume(MatrixStack stack, MotionLayer layer, float range)
        {
            String type = layer.type;
            boolean spot = type.equalsIgnoreCase("SpotLight");
            boolean point = type.equalsIgnoreCase("PointLight");

            if ((!spot && !point) || (!this.exportMode && UIMMotionPanel.this.viewportMode != 0) || layer.lightGlow <= 0.001)
            {
                return;
            }

            float cr = Colors.getR(layer.lightColor);
            float cg = Colors.getG(layer.lightColor);
            float cb = Colors.getB(layer.lightColor);
            double energy = Math.min(1.0, 0.15 + Math.log10(1.0 + Math.max(0.0, layer.lightIntensity)) * 0.55);
            float k = (float) (MathUtils.clamp(layer.lightGlow, 0.0, 1.0) * energy);
            Matrix4f m = stack.peek().getPositionMatrix();
            BufferBuilder bld = Tessellator.getInstance().getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.colorMask(true, true, true, true);

            if (this.exportMode)
            {
                /* Colour is additive, alpha accumulates ("over"): the frame alpha follows the glow intensity instead of staying 0 */
                RenderSystem.blendFuncSeparate(
                    com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.SrcFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
                );
            }
            else
            {
                RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
            }
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            bld.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            if (spot)
            {
                double outer = Math.toRadians(MathUtils.clamp(layer.lightCone, 5.0, 120.0) / 2.0);
                double inner = outer * (1.0 - MathUtils.clamp(layer.lightPenumbra, 0.0, 1.0));

                this.beamCone(bld, m, range, (float) Math.tan(outer) * range, cr, cg, cb, k * 0.3F);
                this.beamCone(bld, m, range, (float) Math.tan(inner) * range, cr, cg, cb, k * 0.3F);
            }
            else
            {
                Matrix4f inv = new Matrix4f(m).invert();
                Vector3f right = inv.transformDirection(new Vector3f(1F, 0F, 0F)).normalize();
                Vector3f up = inv.transformDirection(new Vector3f(0F, 1F, 0F)).normalize();
                float rad = 0.35F + 0.5F * (float) Math.min(1.0, Math.log10(1.0 + Math.max(0.0, layer.lightIntensity)) / 1.7);
                int seg = 24;

                for (int i = 0; i < seg; i++)
                {
                    double a0 = Math.PI * 2.0 * i / seg;
                    double a1 = Math.PI * 2.0 * (i + 1) / seg;
                    float c0 = (float) Math.cos(a0) * rad;
                    float s0 = (float) Math.sin(a0) * rad;
                    float c1 = (float) Math.cos(a1) * rad;
                    float s1 = (float) Math.sin(a1) * rad;

                    bld.vertex(m, 0F, 0F, 0F).color(cr, cg, cb, k * 0.7F).next();
                    bld.vertex(m, right.x * c0 + up.x * s0, right.y * c0 + up.y * s0, right.z * c0 + up.z * s0).color(cr, cg, cb, 0F).next();
                    bld.vertex(m, right.x * c1 + up.x * s1, right.y * c1 + up.y * s1, right.z * c1 + up.z * s1).color(cr, cg, cb, 0F).next();
                }
            }

            try
            {
                BufferRenderer.drawWithGlobalProgram(bld.end());
            }
            finally
            {
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        private void lightCircle(BufferBuilder bld, Matrix4f m, int axis, float off, float radius, float r, float g, float b, float a)
        {
            int seg = 32;

            for (int i = 0; i < seg; i++)
            {
                double a0 = Math.PI * 2.0 * i / seg;
                double a1 = Math.PI * 2.0 * (i + 1) / seg;
                float c0 = (float) Math.cos(a0) * radius;
                float s0 = (float) Math.sin(a0) * radius;
                float c1 = (float) Math.cos(a1) * radius;
                float s1 = (float) Math.sin(a1) * radius;

                if (axis == 0) cameraLine(bld, m, off, c0, s0, off, c1, s1, r, g, b, a);
                else if (axis == 1) cameraLine(bld, m, c0, off, s0, c1, off, s1, r, g, b, a);
                else cameraLine(bld, m, c0, s0, off, c1, s1, off, r, g, b, a);
            }
        }

        /* Wireframe guides of the scene lights: bulb + range sphere (point), cone (spot), plane + normal (rect). Forward is local -Y */
        private void renderLightBody(MatrixStack stack, MotionLayer layer, boolean selected)
        {
            int base = layer.lightColor & 0xFFFFFF;
            float r = selected ? 1F : Colors.getR(base);
            float g = selected ? 0.9F : Colors.getG(base);
            float b = selected ? 0.3F : Colors.getB(base);
            float a = selected ? 1F : 0.85F;
            float range = (float) Math.max(0.1, layer.lightRange);
            String type = layer.type;

            BufferBuilder bld = Tessellator.getInstance().getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            /* Bulb */
            float h = 0.09F;

            bld.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.fillBox(bld, stack, -h, -h, -h, h, h, h, 0xFF000000 | base);
            BufferRenderer.drawWithGlobalProgram(bld.end());
            Draw.renderBox(stack, -h, -h, -h, h * 2F, h * 2F, h * 2F, r, g, b, a);

            this.renderLightVolume(stack, layer, range);

            RenderSystem.lineWidth(selected ? 2.5F : 1.5F);

            Matrix4f m = stack.peek().getPositionMatrix();

            bld.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            if (type.equalsIgnoreCase("PointLight"))
            {
                for (int axis = 0; axis < 3; axis++)
                {
                    this.lightCircle(bld, m, axis, 0F, range, r, g, b, a * 0.55F);
                }
            }
            else if (type.equalsIgnoreCase("SpotLight"))
            {
                double outer = Math.toRadians(MathUtils.clamp(layer.lightCone, 5.0, 120.0) / 2.0);
                double inner = outer * (1.0 - MathUtils.clamp(layer.lightPenumbra, 0.0, 1.0));
                float outerR = (float) Math.tan(outer) * range;
                float innerR = (float) Math.tan(inner) * range;

                this.lightCircle(bld, m, 1, -range, outerR, r, g, b, a);
                this.lightCircle(bld, m, 1, -range, innerR, r, g, b, a * 0.45F);

                for (int i = 0; i < 4; i++)
                {
                    double ang = Math.PI * 0.5 * i;

                    cameraLine(bld, m, 0F, 0F, 0F, (float) Math.cos(ang) * outerR, -range, (float) Math.sin(ang) * outerR, r, g, b, a * 0.8F);
                }

                cameraLine(bld, m, 0F, 0F, 0F, 0F, -range, 0F, r, g, b, a * 0.35F);
            }
            else if (type.equalsIgnoreCase("RectLight"))
            {
                float hw = (float) Math.max(0.05, layer.lightWidth) / 2F;
                float hh = (float) Math.max(0.05, layer.lightHeight) / 2F;
                float[][] c = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};

                for (int i = 0; i < 4; i++)
                {
                    float[] p = c[i];
                    float[] q = c[(i + 1) % 4];

                    cameraLine(bld, m, p[0], 0F, p[1], q[0], 0F, q[1], r, g, b, a);
                }

                cameraLine(bld, m, -hw, 0F, -hh, hw, 0F, hh, r, g, b, a * 0.3F);
                cameraLine(bld, m, hw, 0F, -hh, -hw, 0F, hh, r, g, b, a * 0.3F);

                float nl = Math.min(range, Math.max(0.5F, Math.min(hw, hh)));

                cameraLine(bld, m, 0F, 0F, 0F, 0F, -nl, 0F, r, g, b, a);
                cameraLine(bld, m, 0F, -nl, 0F, 0.08F, -nl + 0.15F, 0F, r, g, b, a);
                cameraLine(bld, m, 0F, -nl, 0F, -0.08F, -nl + 0.15F, 0F, r, g, b, a);
                cameraLine(bld, m, 0F, -nl, 0F, 0F, -nl + 0.15F, 0.08F, r, g, b, a);
                cameraLine(bld, m, 0F, -nl, 0F, 0F, -nl + 0.15F, -0.08F, r, g, b, a);
            }

            BufferRenderer.drawWithGlobalProgram(bld.end());
            RenderSystem.lineWidth(1F);
        }

        private void renderModeLabel(UIContext context)
        {
            int mode = UIMMotionPanel.this.viewportMode;
            String text = mode == 0 ? "Lit" : (mode == 1 ? "Unlit" : "Wireframe");
            MotionLayer viewCam = UIMMotionPanel.this.getViewCamera();

            if (viewCam != null)
            {
                text = text + "  |  " + viewCam.name + " (FOV " + (int) viewCam.cameraFov + ")";
            }

            int x = this.area.x + 10;
            int y = this.area.y + 8;
            int w = context.batcher.getFont().getWidth(text) + 10;

            context.batcher.box(x - 5, y - 3, x - 5 + w, y + 11, 0x88000000);
            context.batcher.text(text, x, y, 0xFFE0E0E0);
        }

        private void renderActorOutlines(UIContext context)
        {
            MotionLayer selected = UIMMotionPanel.this.getSelected();

            if (selected != null && selected.visible)
            {
                int id = Gizmo.STENCIL_MAX + 1 + this.actorIndex(selected);

                this.stencil.renderOutline(context, this.area, id, 0xFF000000 | (selected.color & 0xFFFFFF));
            }

            if (this.hoveredLayer != null && this.hoveredLayer != selected)
            {
                int id = Gizmo.STENCIL_MAX + 1 + this.actorIndex(this.hoveredLayer);

                this.stencil.renderOutline(context, this.area, id, 0xAA000000 | (this.hoveredLayer.color & 0xFFFFFF));
            }
        }

        public Vector2f projectToScreen(double worldX, double worldY, double worldZ)
        {
            Vector4f clip = new Vector4f(
                (float) (worldX - this.camera.position.x),
                (float) (worldY - this.camera.position.y),
                (float) (worldZ - this.camera.position.z),
                1.0F
            );

            new Matrix4f(this.camera.projection).mul(this.camera.view).transform(clip);

            if (clip.w <= 0.0001F) return null;

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;

            float screenX = this.area.x + (ndcX * 0.5F + 0.5F) * this.area.w;
            float screenY = this.area.y + (1.0F - (ndcY * 0.5F + 0.5F)) * this.area.h;

            return new Vector2f(screenX, screenY);
        }

        @Override
        protected void renderUserModel(UIContext context)
        {
            MatrixStack stack = context.render.batcher.getContext().getMatrices();

            /* 3D Coordinate Axes at Origin only when no actor is selected to prevent double pivot clutter */
            if (UIMMotionPanel.this.showGrid && UIMMotionPanel.this.getSelected() == null)
            {
                Draw.coolerAxes(stack, 1.2F, 0.04F);
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
            int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            int overlay = OverlayTexture.DEFAULT_UV;

            /* Render Root Layers recursively with hierarchical MatrixStack transforms */
            this.drawVisibleLayers(context, stack, consumers, light, overlay);

            /* 3D Transform Gizmo (Pivot) via BBS native Gizmo engine */
            MotionLayer selected = UIMMotionPanel.this.getSelected();
            Gizmo.INSTANCE.setViewportHeight(this.area.h);

            if (selected != null)
            {
                Vector3d wpos = UIMMotionPanel.this.getBaseWorldPosition(selected);
                stack.push();
                stack.translate((float) wpos.x, (float) wpos.y, (float) wpos.z);
                if (UIMMotionPanel.this.transformEditor.getSpace().placesOnOwnFrame())
                {
                    MatrixStackUtils.multiply(stack, new Matrix4f().set(UIMMotionPanel.this.liveTransform.createRotationMatrix()));
                }
                TransformSpace space = UIMMotionPanel.this.transformEditor.getSpace();
                Gizmo.INSTANCE.reorientForSpace(stack, space == TransformSpace.PARENT ? TransformSpace.GLOBAL : space, this.camera.view, null);

                if (UIBaseMenu.shouldRenderAxes())
                {
                    RenderSystem.disableDepthTest();
                    Gizmo.INSTANCE.render(stack);
                    RenderSystem.enableDepthTest();
                }
                stack.pop();
            }

            if (selected != null || this.area.isInside(context))
            {
                GlStateManager._disableScissorTest();

                this.ensureStencil();
                this.stencilMap.setup();
                this.stencil.apply();

                /* Actor ids come right after the gizmo's handle ids: hover / select outline and click picking */
                this.idPass = true;

                try
                {
                    for (MotionLayer root : UIMMotionPanel.this.getRootLayers())
                    {
                        this.renderLayerRecursive(context, stack, consumers, light, overlay, root);
                    }

                    consumers.draw();
                }
                finally
                {
                    this.idPass = false;
                }

                if (selected != null)
                {
                    /* Handles always win over the actor behind them, like the drawn gizmo */
                    RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, false);

                    Vector3d wpos = UIMMotionPanel.this.getBaseWorldPosition(selected);
                    stack.push();
                    stack.translate((float) wpos.x, (float) wpos.y, (float) wpos.z);
                    if (UIMMotionPanel.this.transformEditor.getSpace().placesOnOwnFrame())
                    {
                        MatrixStackUtils.multiply(stack, new Matrix4f().set(UIMMotionPanel.this.liveTransform.createRotationMatrix()));
                    }
                    TransformSpace space = UIMMotionPanel.this.transformEditor.getSpace();
                    Gizmo.INSTANCE.reorientForSpace(stack, space == TransformSpace.PARENT ? TransformSpace.GLOBAL : space, this.camera.view, null);

                    if (!UIBaseMenu.isHideGizmoHeld())
                    {
                        Gizmo.INSTANCE.renderStencil(stack);
                    }

                    stack.pop();
                }

                if (this.area.isInside(context))
                {
                    this.stencil.pickGUI(context, this.area, BBSSettings.gizmoHoverTolerance.get(), Gizmo.STENCIL_MAX);
                }
                else
                {
                    this.stencil.clearIndex();
                }

                this.stencil.unbind(this.stencilMap);

                MinecraftClient.getInstance().getFramebuffer().beginWrite(true);

                GlStateManager._enableScissorTest();
            }
            else
            {
                this.stencil.clearPicking();
            }

            this.updateHoveredLayer(context);

            this.gizmo.update(context);
        }

        private void renderLayerRecursive(UIContext context, MatrixStack stack, VertexConsumerProvider.Immediate consumers, int light, int overlay, MotionLayer layer)
        {
            stack.push();
            stack.translate((float) layer.posX, (float) layer.posY, (float) layer.posZ);
            stack.multiply(Matrices.toQuaternionZYXDegrees((float) layer.rotPitch, (float) layer.rotYaw, (float) layer.rotRoll));
            stack.scale((float) layer.scaleX, (float) layer.scaleY, (float) layer.scaleZ);

            /* Transform overlay goes on top of the main transform; children inherit both */
            if (layer.overlay != null)
            {
                MatrixStackUtils.multiply(stack, layer.overlay.createMatrix());
            }

            if (layer.visible)
            {
                if (!this.idPass)
                {
                    this.renderSingleLayer(context, stack, consumers, light, overlay, layer);
                }
                else if (!UIMMotionPanel.isLightType(layer.type) && !"Camera".equalsIgnoreCase(layer.type))
                {
                    this.idValue = Gizmo.STENCIL_MAX + 1 + this.actorIndex(layer);
                    this.stencilMap.objectIndex = this.idValue;
                    this.stencilMap.setIncrement(false);
                    this.renderSingleLayer(context, stack, consumers, light, overlay, layer);
                }
            }

            /* Recursively render child actors in this local transform space */
            for (MotionLayer child : UIMMotionPanel.this.getChildLayers(layer))
            {
                this.renderLayerRecursive(context, stack, consumers, light, overlay, child);
            }

            stack.pop();
        }

        /* Writes a flat actor id into a POSITION_COLOR buffer, whatever the source vertex format was */
        private static final class FlatIdConsumer implements net.minecraft.client.render.VertexConsumer
        {
            private final net.minecraft.client.render.VertexConsumer delegate;
            private final int r;
            private final int g;
            private final int b;

            private FlatIdConsumer(net.minecraft.client.render.VertexConsumer delegate, int id)
            {
                this.delegate = delegate;
                this.r = id & 0xFF;
                this.g = (id >> 8) & 0xFF;
                this.b = (id >> 16) & 0xFF;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer vertex(double x, double y, double z)
            {
                if (this.delegate != null) this.delegate.vertex(x, y, z);

                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer color(int red, int green, int blue, int alpha)
            {
                if (this.delegate != null) this.delegate.color(this.r, this.g, this.b, 255);

                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer texture(float u, float v)
            {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer overlay(int u, int v)
            {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer light(int u, int v)
            {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer normal(float x, float y, float z)
            {
                return this;
            }

            @Override
            public void next()
            {
                if (this.delegate != null) this.delegate.next();
            }

            @Override
            public void fixedColor(int red, int green, int blue, int alpha)
            {
            }

            @Override
            public void unfixColor()
            {
            }
        }

        private static final class IdProvider implements VertexConsumerProvider
        {
            private final VertexConsumerProvider delegate;
            private final int id;

            private IdProvider(VertexConsumerProvider delegate, int id)
            {
                this.delegate = delegate;
                this.id = id;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer getBuffer(net.minecraft.client.render.RenderLayer layer)
            {
                /* Only quads can be flattened safely; anything else (lines, glint, ...) is dropped from the id pass */
                if (layer.getDrawMode() == net.minecraft.client.render.VertexFormat.DrawMode.QUADS)
                {
                    return new FlatIdConsumer(this.delegate.getBuffer(net.minecraft.client.render.RenderLayer.getDebugQuads()), this.id);
                }

                return new FlatIdConsumer(null, this.id);
            }
        }

        private static final java.util.Map<net.minecraft.client.render.RenderLayer, net.minecraft.client.render.RenderLayer> TRANSLUCENT_CACHE = new java.util.HashMap<>();
        private static final java.util.regex.Pattern LAYER_TEXTURE = java.util.regex.Pattern.compile("texture\\[optional\\[([a-z0-9_.\\-/:]+)\\]");

        /* Solid / cutout entity layers ignore alpha: give them their translucent twin (same texture) */
        private static net.minecraft.client.render.RenderLayer translucentFor(net.minecraft.client.render.RenderLayer layer)
        {
            net.minecraft.client.render.RenderLayer cached = TRANSLUCENT_CACHE.get(layer);

            if (cached != null)
            {
                return cached;
            }

            net.minecraft.client.render.RenderLayer result = layer;
            String name = layer.toString().toLowerCase();
            int colon = name.indexOf(':');
            String head = colon > 0 ? name.substring(0, colon) : name;

            /* The tail of every layer name carries "affects_outline", so only the head may be inspected */
            if (head.contains("entity_cutout") || head.contains("entity_solid"))
            {
                java.util.regex.Matcher matcher = LAYER_TEXTURE.matcher(name);

                if (matcher.find())
                {
                    String texture = matcher.group(1);

                    if (texture.endsWith("atlas/blocks.png"))
                    {
                        result = net.minecraft.client.render.TexturedRenderLayers.getEntityTranslucentCull();
                    }
                    else
                    {
                        result = net.minecraft.client.render.RenderLayer.getEntityTranslucent(new net.minecraft.util.Identifier(texture));
                    }
                }
            }

            TRANSLUCENT_CACHE.put(layer, result);

            return result;
        }

        private static final class TintProvider implements VertexConsumerProvider
        {
            private final VertexConsumerProvider delegate;
            private final Color color;
            private final boolean remap;

            private TintProvider(VertexConsumerProvider delegate, Color color, boolean remap)
            {
                this.delegate = delegate;
                this.color = color;
                this.remap = remap;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer getBuffer(net.minecraft.client.render.RenderLayer layer)
            {
                net.minecraft.client.render.RenderLayer target = layer;

                if (this.remap && this.color.a < 0.999F)
                {
                    target = translucentFor(layer);
                }

                return mchorse.bbs_mod.client.BBSRendering.getColorConsumer(this.color).apply(this.delegate.getBuffer(target));
            }
        }

        /* Forms: tint + alpha through the context colour; mobs ignore that colour, so they get the substitute consumer and translucent layers */
        private void renderFormTinted(mchorse.bbs_mod.forms.forms.Form form, FormRenderingContext fc, Color tintColor, float a, boolean tinted)
        {
            if (this.idPass)
            {
                /* Id pass: the form renderers draw their own picking ids from the stencil map */
                fc.stencilMap(this.stencilMap);
                FormUtilsClient.render(form, fc);

                return;
            }

            mchorse.bbs_mod.forms.CustomVertexConsumerProvider provider = FormUtilsClient.getProvider();
            boolean apply = tinted || a < 0.999F;

            if (apply && form instanceof mchorse.bbs_mod.forms.forms.MobForm)
            {
                provider.setSubstitute(mchorse.bbs_mod.client.BBSRendering.getColorConsumer(tintColor));

                if (a < 0.999F)
                {
                    mchorse.bbs_mod.forms.CustomVertexConsumerProvider.layerRemap = (l) -> translucentFor(l);
                }
            }

            try
            {
                FormUtilsClient.render(form, fc);
            }
            finally
            {
                if (apply && form instanceof mchorse.bbs_mod.forms.forms.MobForm)
                {
                    provider.setSubstitute(null);
                    mchorse.bbs_mod.forms.CustomVertexConsumerProvider.layerRemap = null;
                }
            }
        }

        private void renderSingleLayer(UIContext context, MatrixStack stack, VertexConsumerProvider.Immediate consumers, int light, int overlay, MotionLayer layer)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean isSelected = UIMMotionPanel.this.getSelected() == layer;

            float a = (float) MathUtils.clamp(layer.opacity, 0.0, 1.0);
            float[] lightMul = (this.idPass || (!this.exportMode && UIMMotionPanel.this.viewportMode != 0)) ? null : UIMMotionPanel.this.computeLightMultiplier(layer);
            boolean tinted = layer.tintR < 0.999F || layer.tintG < 0.999F || layer.tintB < 0.999F || lightMul != null;
            if (this.idPass)
            {
                a = 1F;
                tinted = false;
            }

            Color tintColor = lightMul != null
                ? new Color(layer.tintR * lightMul[0], layer.tintG * lightMul[1], layer.tintB * lightMul[2], a)
                : new Color(layer.tintR, layer.tintG, layer.tintB, a);
            int tintArgb = tintColor.getARGBColor();
            VertexConsumerProvider blockVc = this.idPass ? new IdProvider(consumers, this.idValue) : ((a < 0.999F || tinted) ? new TintProvider(consumers, tintColor, true) : consumers);
            VertexConsumerProvider entityVc = this.idPass ? new IdProvider(consumers, this.idValue) : ((a < 0.999F || tinted) ? new TintProvider(consumers, tintColor, false) : consumers);
            if (a < 0.999F || tinted)
            {
                RenderSystem.enableBlend();
            }

            String type = layer.type != null ? layer.type : "";

            /* Lights are editor helpers (markers); they never appear in a render */
            if (this.exportMode && UIMMotionPanel.isSceneLightType(type))
            {
                this.pendingVolumes.add(new Object[] {layer, new Matrix4f(stack.peek().getPositionMatrix())});
            }

            if (this.exportMode && (UIMMotionPanel.isLightType(type) || "Camera".equalsIgnoreCase(type)))
            {
                return;
            }

            /* The camera being looked through sits at the eye: its own body is never drawn */
            if ("Camera".equalsIgnoreCase(type) && layer.id.equals(UIMMotionPanel.this.viewCameraId))
            {
                return;
            }

            if (type.equalsIgnoreCase("Block"))
            {
                BlockState state = layer.blockState != null ? layer.blockState : Blocks.GRASS_BLOCK.getDefaultState();
                int blockLight = LightmapTextureManager.pack(MathUtils.clamp(layer.blockLight, 0, 15), MathUtils.clamp(layer.blockLight, 0, 15));
                stack.push();
                stack.translate(-0.5F, -0.5F, -0.5F);
                mc.getBlockRenderManager().renderBlockAsEntity(state, stack, blockVc, blockLight, overlay);
                consumers.draw();
                stack.pop();
            }
            else if (type.equalsIgnoreCase("Item"))
            {
                ItemStack itemStack = (layer.itemStack != null ? layer.itemStack : new ItemStack(Items.DIAMOND_SWORD)).copy();
                itemStack.setCount(Math.max(1, layer.itemCount));
                if (layer.itemGlint)
                {
                    itemStack.getOrCreateNbt().put("Enchantments", new NbtList());
                    itemStack.getOrCreateNbt().getList("Enchantments", 10).add(new NbtCompound());
                }
                ModelTransformationMode mode = layer.itemTransformMode != null ? layer.itemTransformMode : ModelTransformationMode.FIXED;
                stack.push();
                stack.translate(0.0F, 0.0F, 0.0F);

                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.defaultBlendFunc();
                if (a >= 0.999F && !tinted)
                {
                    RenderSystem.disableBlend();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
                DiffuseLighting.enableGuiDepthLighting();
                mc.getItemRenderer().renderItem(itemStack, mode, light, overlay, stack, blockVc, mc.world, 0);
                consumers.draw();
                DiffuseLighting.disableGuiDepthLighting();
                RenderSystem.enableDepthTest();

                stack.pop();
            }
            else if (type.equalsIgnoreCase("Entity"))
            {
                if (layer.form instanceof ModelForm mf && layer.entityModel != null && !layer.entityModel.isEmpty())
                {
                    mf.model.set(layer.entityModel);
                }
                boolean hasCustomModel = layer.form != null && (!(layer.form instanceof ModelForm mf) || (mf.model.get() != null && !mf.model.get().isEmpty()));
                if (hasCustomModel)
                {
                    FormRenderingContext fc = new FormRenderingContext()
                        .set(FormRenderType.PREVIEW, UIMMotionViewport.this.entity, stack, light, overlay, context.getTransition())
                        .camera(UIMMotionViewport.this.camera)
                        .modelRenderer(context.getTick())
                        .color(tintArgb);
                    this.renderFormTinted(layer.form, fc, tintColor, a, tinted);
                }
                else if (mc.player != null)
                {
                    Morph playerMorph = Morph.getMorph(mc.player);
                    if (playerMorph != null && playerMorph.getForm() != null)
                    {
                        FormRenderingContext fc = new FormRenderingContext()
                            .set(FormRenderType.PREVIEW, playerMorph.entity, stack, light, overlay, context.getTransition())
                            .camera(UIMMotionViewport.this.camera)
                            .modelRenderer(context.getTick())
                        .color(tintArgb);
                        this.renderFormTinted(playerMorph.getForm(), fc, tintColor, a, tinted);
                    }
                    else
                    {
                        mc.getEntityRenderDispatcher().render(mc.player, 0.0, 0.0, 0.0, 0.0F, context.getTransition(), stack, entityVc, light);
                        consumers.draw();
                    }
                }
            }
            else if (type.equalsIgnoreCase("Structure"))
            {
                stack.push();
                stack.translate((float) layer.structureOffsetX, (float) layer.structureOffsetY, (float) layer.structureOffsetZ);
                if (layer.form != null)
                {
                    FormRenderingContext fc = new FormRenderingContext()
                        .set(FormRenderType.PREVIEW, UIMMotionViewport.this.entity, stack, light, overlay, context.getTransition())
                        .camera(UIMMotionViewport.this.camera)
                        .modelRenderer(context.getTick())
                        .color(tintArgb);
                    this.renderFormTinted(layer.form, fc, tintColor, a, tinted);
                }
                else
                {
                    BlockState structState = layer.blockState != null ? layer.blockState : Blocks.STRUCTURE_BLOCK.getDefaultState();
                    stack.push();
                    stack.translate(-0.5F, -0.5F, -0.5F);
                    mc.getBlockRenderManager().renderBlockAsEntity(structState, stack, blockVc, light, overlay);
                    consumers.draw();
                    stack.pop();
                }
                stack.pop();
            }
            else if (type.equalsIgnoreCase("Camera"))
            {
                this.renderCameraBody(stack, layer, isSelected);
            }
            else if (UIMMotionPanel.isSceneLightType(type))
            {
                this.renderLightBody(stack, layer, isSelected);
            }
            else if (type.equalsIgnoreCase("Light"))
            {
                /* Studio Light Actor: Visual glowing bulb, outer cage and directional indicator rays */
                float lr = Colors.getR(layer.lightColor);
                float lg = Colors.getG(layer.lightColor);
                float lb = Colors.getB(layer.lightColor);
                float li = (float) MathUtils.clamp(layer.lightIntensity, 0.2, 5.0);
                float half = 0.12F * li;
                float size = 0.24F * li;

                /* 1. Solid glowing bulb cube */
                BufferBuilder bld = Tessellator.getInstance().getBuffer();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                bld.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                Draw.fillBox(bld, stack, -half, -half, -half, half, half, half, layer.lightColor | Colors.A100);
                BufferRenderer.drawWithGlobalProgram(bld.end());

                /* 2. Outer cage frame */
                Draw.renderBox(stack, -half, -half, -half, size, size, size, 1.0F, 1.0F, 1.0F, 0.9F);

                /* 3. Directional Light indicator rays */
                bld.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                float rayLen = 0.6F * li;
                bld.vertex(stack.peek().getPositionMatrix(), 0F, 0F, 0F).color(lr, lg, lb, 0.9F).next();
                bld.vertex(stack.peek().getPositionMatrix(), 0F, -rayLen, 0F).color(lr, lg, lb, 0.1F).next();
                bld.vertex(stack.peek().getPositionMatrix(), -half, -half, -half).color(lr, lg, lb, 0.7F).next();
                bld.vertex(stack.peek().getPositionMatrix(), -half * 1.8F, -rayLen, -half * 1.8F).color(lr, lg, lb, 0.1F).next();
                bld.vertex(stack.peek().getPositionMatrix(), half, -half, half).color(lr, lg, lb, 0.7F).next();
                bld.vertex(stack.peek().getPositionMatrix(), half * 1.8F, -rayLen, half * 1.8F).color(lr, lg, lb, 0.1F).next();
                BufferRenderer.drawWithGlobalProgram(bld.end());
            }
            else
            {
                /* Generic / Form fallback */
                if (layer.form != null)
                {
                    FormRenderingContext fc = new FormRenderingContext()
                        .set(FormRenderType.PREVIEW, UIMMotionViewport.this.entity, stack, light, overlay, context.getTransition())
                        .camera(UIMMotionViewport.this.camera)
                        .modelRenderer(context.getTick())
                        .color(tintArgb);
                    this.renderFormTinted(layer.form, fc, tintColor, a, tinted);
                }
            }

            if (a < 0.999F || tinted)
            {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /* ── Native Keyframes Extension ── */
    public class UIMMotionKeyframes extends UIKeyframes
    {
        public UIMMotionKeyframes(Consumer<Keyframe> callback)
        {
            super(callback);
        }

        @Override
        public float getTick()
        {
            return UIMMotionPanel.this.currentFrame;
        }

        @Override
        public float getPlayheadTick(UIContext context)
        {
            return UIMMotionPanel.this.currentFrame;
        }

        @Override
        protected boolean hasCursor()
        {
            return true;
        }

        @Override
        public void render(UIContext context)
        {
            /* Timeline yonu: 0. tick solda, son tick sagda olmali. Ters (aynali) ise duzelt. */
            if (UIMMotionPanel.this.maxFrames > 0 && this.toGraphX(0) > this.toGraphX(UIMMotionPanel.this.maxFrames))
            {
                this.xAxis.inverse = false;
                if (this.xAxis.getZoom() < 0)
                {
                    this.xAxis.setZoom(-this.xAxis.getZoom());
                }
                this.resetViewX();
            }

            super.render(context);
        }

        @Override
        public void panTime(double wheel)
        {
            float sens = BBSSettings.scrollingSensitivityHorizontal != null ? BBSSettings.scrollingSensitivityHorizontal.get() : 1.0F;
            this.xAxis.setShift(this.xAxis.getShift() - (25F * sens * (float) wheel) / this.xAxis.getZoom());
        }

        @Override
        public void dragTimeBy(int dx)
        {
            /* Standart pan yönü: sağa çekince geçmişe/küçük ticklere, sola çekince geleceğe/büyük ticklere */
            this.xAxis.setShift(this.xAxis.getShift() - dx / this.xAxis.getZoom());
        }

        @Override
        protected void moveNoKeyframes(UIContext context)
        {
            super.moveNoKeyframes(context);
            float target = this.fromGraphCursor(context.mouseX);
            UIMMotionPanel.this.currentFrame = MathUtils.clamp((int) Math.round(target), 0, UIMMotionPanel.this.maxFrames);
            UIMMotionPanel.this.updateLayersFromKeyframes();
            UIMMotionPanel.this.syncPropertiesFromSelected();
        }

        private boolean isCustomScrubbing = false;

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            /* 0. Sol etiket sütunu: başlık satırı katlar, kanal satırı oynatma kafasına keyframe ekler */
            if (this.area.isInside(context) && context.mouseX <= this.area.x + this.getLabelWidth())
            {
                if (context.mouseButton == 0)
                {
                    /* Aktör başlığı (grup): aktörü seç, katlama / açmayı dope sheet'in kendisi yapar */
                    UIKeyframeSheet.Section group = this.getDopeSheet().getSection(context.mouseY);

                    if (group != null)
                    {
                        UIMMotionPanel.this.selectLayerByTimelineKey(group.id());

                        return super.subMouseClicked(context);
                    }

                    /* Kanal satırı: o tick'te keyframe yoksa canlı değerle ekler */
                    UIKeyframeSheet sheet = this.getDopeSheet().getSheet(context.mouseY);

                    /* Alt satırı olan kanalın (Transform -> Transform Overlay) yalnızca oku katlar / açar */
                    if (sheet != null && this.getDopeSheet().isFoldToggleHit(context, sheet))
                    {
                        UIMMotionPanel.this.selectLayerByTimelineKey(sheet.id);

                        return super.subMouseClicked(context);
                    }

                    if (sheet != null)
                    {
                        MotionLayer layer = UIMMotionPanel.this.selectLayerByTimelineKey(sheet.id);

                        if (layer != null)
                        {
                            UIMMotionPanel.this.insertLiveKeyframe(layer, sheet);
                        }

                        UIUtils.playClick();
                        return true;
                    }
                }
                return false;
            }

            if (this.graphArea.isInside(context))
            {
                /* 1. Cetvel (Ruler) alanına tıklandıysa: Kesintisiz Scrubbing */
                if (context.mouseButton == 0 && context.mouseY < TimelineRulerRenderer.getRulerBottom(this.area))
                {
                    this.isCustomScrubbing = true;
                    this.moveNoKeyframes(context);
                    return true;
                }

                /* 1b. Motion Shift tutamacı: keyframe değil, bu yüzden aşağıdaki marquee onu yutmadan önce yakalanır */
                if (context.mouseButton == 0 && this.getMotionShift().mouseClicked(context))
                {
                    return true;
                }

                /* 2. Alt + Sol Tık: Keyframe Çoğaltma (Duplication) */
                if (context.mouseButton == 0 && Window.isAltPressed())
                {
                    Pair<Keyframe, KeyframeType> pair = this.getGraph().findKeyframe(context.mouseX, context.mouseY);
                    if (pair != null && pair.a != null)
                    {
                        Keyframe original = pair.a;
                        UIKeyframeSheet sheet = this.getGraph().getSheet(original);
                        if (sheet != null && sheet.channel != null)
                        {
                            float targetTick = this.fromGraphCursor(context.mouseX);
                            Object copiedVal = original.getFactory().copy(original.getValue());
                            int insertedIndex = sheet.channel.insert(targetTick, copiedVal);
                            Keyframe clone = sheet.channel.get(insertedIndex);
                            if (clone != null)
                            {
                                clone.copy(original);
                                clone.setTick(targetTick, true);
                                this.getGraph().clearSelection();
                                sheet.selection.add(clone);
                                this.pickKeyframe(clone);
                                UIMMotionPanel.this.selectedKeyframe = clone;
                                this.triggerChange();
                                UIMMotionPanel.this.syncPropertiesFromSelected();
                                UIUtils.playClick();
                                return true;
                            }
                        }
                    }
                }

                /* 3. Sağ Tık: Keyframe Seçimi & Enterpolasyon Menüsü */
                if (context.mouseButton == 1)
                {
                    Pair<Keyframe, KeyframeType> pair = this.getGraph().findKeyframe(context.mouseX, context.mouseY);
                    if (pair != null && pair.a != null)
                    {
                        Keyframe kf = pair.a;
                        this.getGraph().clearSelection();
                        UIKeyframeSheet sheet = this.getGraph().getSheet(kf);
                        if (sheet != null) sheet.selection.add(kf);
                        this.pickKeyframe(kf);
                        UIMMotionPanel.this.selectedKeyframe = kf;
                        UIMMotionPanel.this.syncPropertiesFromSelected();

                        Interpolation interp = kf.getInterpolation();
                        UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interp);
                        this.getContext().replaceContextMenu(menu.callback(() ->
                        {
                            kf.getInterpolation().copy(interp);
                            this.triggerChange();
                            UIMMotionPanel.this.syncPropertiesFromSelected();
                            UIUtils.playClick();
                        }));
                        return true;
                    }
                }

                /* 4. Sol Tık boş alana: Marquee (Box Selection) Başlat */
                if (context.mouseButton == 0 && !Window.isCtrlPressed() && !Window.isAltPressed())
                {
                    Pair<Keyframe, KeyframeType> pair = this.getGraph().findKeyframe(context.mouseX, context.mouseY);
                    if (pair == null)
                    {
                        this.getGraph().clearSelection();
                        UIMMotionPanel.this.selectedKeyframe = null;
                        UIMMotionPanel.this.syncPropertiesFromSelected();
                        this.marquee.press(context.mouseX, context.mouseY);
                        return true;
                    }
                }
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected void handleMouse(UIContext context)
        {
            if (this.isCustomScrubbing && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
            {
                this.isCustomScrubbing = false;
            }

            if (this.isCustomScrubbing)
            {
                this.moveNoKeyframes(context);
                return;
            }
            super.handleMouse(context);
        }

        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            this.isCustomScrubbing = false;

            boolean shifting = this.getMotionShift().isDragging();
            boolean handled = super.subMouseReleased(context);

            /* A finished Motion Shift drag is one undo step right away, and it is saved */
            if (shifting && !this.getMotionShift().isDragging())
            {
                UIMMotionPanel.this.saveScene(true);
            }

            return handled;
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseWheelHorizontal != 0)
            {
                this.panTime(context.mouseWheelHorizontal);
                return true;
            }

            if (this.area.isInside(context) && context.mouseWheel != 0D)
            {
                if (Window.isAltPressed())
                {
                    /* Alt + Tekerlek: Satir (row) yuksekligi daralt / genislet */
                    this.getDopeSheet().setTrackHeight(this.getDopeSheet().getTrackHeight() - context.mouseWheel * 2D);
                    return true;
                }
                else if (Window.isShiftPressed() && !Window.isCtrlPressed())
                {
                    /* Shift + Tekerlek: Dikey kanal kaydirma */
                    this.getDopeSheet().getYAxis().mouseScroll(context);
                    return true;
                }
                else if (Window.isCtrlPressed())
                {
                    /* Ctrl + Tekerlek: zaman ekseninde ileri / geri kaydır (yatay pan, mevcut zoom ölçeğinde) */
                    this.panTime(context.mouseWheel);
                    return true;
                }
                else
                {
                    /* Tekerlek: Zaman ekseninde Zoom In / Zoom Out */
                    this.zoomTimeAt(context, context.mouseWheel);
                    return true;
                }
            }

            return super.subMouseScrolled(context);
        }

        public void deleteSelectedKeyframes()
        {
            List<Keyframe> toDelete = new ArrayList<>();
            if (UIMMotionPanel.this.selectedKeyframe != null)
            {
                toDelete.add(UIMMotionPanel.this.selectedKeyframe);
            }
            if (this.getGraph() != null)
            {
                for (UIKeyframeSheet sheet : this.getGraph().getSheets())
                {
                    if (sheet.selection != null)
                    {
                        for (Keyframe kf : sheet.selection.getSelected())
                        {
                            if (!toDelete.contains(kf))
                            {
                                toDelete.add(kf);
                            }
                        }
                    }
                }
            }

            for (Keyframe kf : toDelete)
            {
                UIMMotionPanel.this.removeKeyframe(kf);
            }

            if (this.getGraph() != null)
            {
                this.getGraph().clearSelection();
            }
            UIMMotionPanel.this.selectedKeyframe = null;
        }

        @Override
        public boolean subKeyPressed(UIContext context)
        {
            if (context.isFocused() || (UIMMotionPanel.this.actorNameInput != null && UIMMotionPanel.this.actorNameInput.isFocused()))
            {
                return super.subKeyPressed(context);
            }
            /* Escape: Aktör veya Keyframe seçimini temizle (Deselect) */
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                if (UIMMotionPanel.this.selectedKeyframe != null)
                {
                    UIMMotionPanel.this.selectedKeyframe = null;
                    if (this.getGraph() != null) this.getGraph().clearSelection();
                    UIMMotionPanel.this.syncPropertiesFromSelected();
                    return true;
                }
                if (UIMMotionPanel.this.getSelected() != null)
                {
                    UIMMotionPanel.this.selectedLayerIndex = -1;
                    UIMMotionPanel.this.syncPropertiesFromSelected();
                    return true;
                }
            }
            if (context.isPressed(GLFW.GLFW_KEY_SPACE))
            {
                UIMMotionPanel.this.togglePlayback();
                return true;
            }
            if (context.isPressed(GLFW.GLFW_KEY_HOME))
            {
                UIMMotionPanel.this.currentFrame = 0;
                UIMMotionPanel.this.updateLayersFromKeyframes();
                UIMMotionPanel.this.syncPropertiesFromSelected();
                return true;
            }
            if (context.isPressed(GLFW.GLFW_KEY_END))
            {
                UIMMotionPanel.this.currentFrame = UIMMotionPanel.this.maxFrames;
                UIMMotionPanel.this.updateLayersFromKeyframes();
                UIMMotionPanel.this.syncPropertiesFromSelected();
                return true;
            }
            if (!Window.isCtrlPressed() && !Window.isAltPressed() && UIMMotionPanel.this.handlePlayheadKeys(context))
            {
                return true;
            }
            if (context.isPressed(GLFW.GLFW_KEY_DELETE) || context.isPressed(GLFW.GLFW_KEY_BACKSPACE))
            {
                this.deleteSelectedKeyframes();
                return true;
            }
            return super.subKeyPressed(context);
        }

        @Override
        protected void renderOverlay(UIContext context)
        {
            super.renderOverlay(context);

            /* Render playhead cursor: Kesintisiz dikey kırmızı çizgi ve cetvel scrubber rozeti */
            float cursor = this.getPlayheadTick(context);
            int cx = this.toGraphX(cursor);
            int playheadColor = 0xFFEF4444; // Bright Red Scrubber & Playhead

            if (cx >= this.graphArea.x && cx <= this.graphArea.ex())
            {
                /* Vertical Playhead Line from top ruler to bottom of timeline */
                context.batcher.box(cx - 1, this.area.y, cx + 1, this.area.ey(), playheadColor);

                /* Top Scrubber Head (Badge on ruler) */
                int rw = 32;
                int rh = 13;
                int rx = MathUtils.clamp(cx - rw / 2, this.graphArea.x, this.graphArea.ex() - rw);
                int ry = this.area.y + 1;
                context.batcher.box(rx, ry, rx + rw, ry + rh, playheadColor);

                /* Downwards pointer triangle */
                int tx = cx;
                int ty = ry + rh;
                context.batcher.box(tx - 3, ty, tx + 3, ty + 1, playheadColor);
                context.batcher.box(tx - 2, ty + 1, tx + 2, ty + 2, playheadColor);
                context.batcher.box(tx - 1, ty + 2, tx + 1, ty + 3, playheadColor);

                String label = String.format("%03d", (int) cursor);
                context.batcher.textCard(label, rx + 4, ry + 2, Colors.WHITE, playheadColor, 0);
            }
        }
    }

    /** Removes one keyframe from its own channel; every channel is its own row, so nothing cascades to the others. */
    public void removeKeyframe(Keyframe keyframe)
    {
        if (keyframe == null) return;

        MotionLayer targetLayer = null;
        for (MotionLayer l : this.layers)
        {
            if (kfIn(l.channelTransform, keyframe) || kfIn(l.channelTransformOverlay, keyframe)
                || kfIn(l.channelPose, keyframe) || kfIn(l.channelIntensity, keyframe) || kfIn(l.channelRange, keyframe) || kfIn(l.channelCone, keyframe)
                || kfIn(l.channelColor, keyframe)
                || kfIn(l.channelVisible, keyframe))
            {
                targetLayer = l;
                break;
            }
        }
        if (targetLayer == null)
        {
            targetLayer = this.getSelected();
        }

        if (targetLayer != null)
        {
            int tIdx = kfIndex(targetLayer.channelTransform, keyframe);
            if (tIdx >= 0) targetLayer.channelTransform.remove(tIdx);

            int toIdx = kfIndex(targetLayer.channelTransformOverlay, keyframe);
            if (toIdx >= 0) targetLayer.channelTransformOverlay.remove(toIdx);

            int pIdx = kfIndex(targetLayer.channelPose, keyframe);
            if (pIdx >= 0) targetLayer.channelPose.remove(pIdx);

            int cIdx = kfIndex(targetLayer.channelColor, keyframe);
            if (cIdx >= 0) targetLayer.channelColor.remove(cIdx);

            int vIdx = kfIndex(targetLayer.channelVisible, keyframe);
            if (vIdx >= 0) targetLayer.channelVisible.remove(vIdx);

            int iIdx = kfIndex(targetLayer.channelIntensity, keyframe);
            if (iIdx >= 0) targetLayer.channelIntensity.remove(iIdx);

            int rIdx = kfIndex(targetLayer.channelRange, keyframe);
            if (rIdx >= 0) targetLayer.channelRange.remove(rIdx);

            int cnIdx = kfIndex(targetLayer.channelCone, keyframe);
            if (cnIdx >= 0) targetLayer.channelCone.remove(cnIdx);
        }
        if (this.keyframesView != null)
        {
            this.keyframesView.triggerChange();
        }
        this.rebuildKeyframeSheets();
        this.updateLayersFromKeyframes();
        this.syncPropertiesFromSelected();
    }

    /* Keyframe.equals compares tick/value/interpolation, so two actors with identical keyframes looked "equal":
       every lookup must be by identity or the first actor always wins */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean kfIn(KeyframeChannel channel, Object keyframe)
    {
        return kfIndex(channel, keyframe) >= 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int kfIndex(KeyframeChannel channel, Object keyframe)
    {
        java.util.List list = channel.getKeyframes();

        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i) == keyframe)
            {
                return i;
            }
        }

        return -1;
    }

    /** The actor a timeline row belongs to: the group id is the actor key, channel ids are "<key>_<channel>". Selects it. */
    public MotionLayer selectLayerByTimelineKey(String id)
    {
        if (id == null) return null;

        for (int i = 0; i < this.layers.size(); i++)
        {
            MotionLayer layer = this.layers.get(i);
            String layerKey = (layer.id != null && !layer.id.isEmpty()) ? layer.id : ("layer_" + i);

            if (id.equals(layerKey) || id.startsWith(layerKey + "_"))
            {
                this.selectLayer(layer);

                return layer;
            }
        }

        return null;
    }

    /**
     * Click on a channel row: puts a keyframe at the playhead holding the actor's live value, unless the
     * channel already has one at that tick (then that one is picked). Lands in the undo history at once.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void insertLiveKeyframe(MotionLayer layer, UIKeyframeSheet sheet)
    {
        KeyframeChannel channel = sheet.channel;
        float tick = this.currentFrame;
        Keyframe keyframe = this.findKeyframeAt(channel, tick);

        if (keyframe == null)
        {
            Object value = this.liveValueFor(layer, channel);

            if (value == null)
            {
                return;
            }

            keyframe = channel.get(channel.insert(tick, value));

            if (channel == layer.channelVisible)
            {
                layer.enforceVisibleHold();
            }

            if (this.keyframesView != null)
            {
                this.keyframesView.triggerChange();
            }

            this.updateLayersFromKeyframes();
            this.flushHistory();
        }

        if (this.keyframesView != null && keyframe != null)
        {
            this.keyframesView.getGraph().clearSelection();
            sheet.selection.add(keyframe);
        }

        this.selectedKeyframe = keyframe;
        this.syncPropertiesFromSelected();
    }

    /** What the actor shows right now on a given channel, as a fresh keyframe value. */
    private Object liveValueFor(MotionLayer layer, KeyframeChannel<?> channel)
    {
        if (channel == layer.channelTransformOverlay)
        {
            /* A new overlay keyframe holds what the overlay adds right now: nothing (identity) while it is empty */
            return layer.overlay != null ? layer.overlay.copy() : new Transform();
        }

        if (channel == layer.channelTransform)
        {
            Transform t = new Transform();

            t.translate.set((float) layer.posX, (float) layer.posY, (float) layer.posZ);
            t.rotate.set(MathUtils.toRad((float) layer.rotPitch), MathUtils.toRad((float) layer.rotYaw), MathUtils.toRad((float) layer.rotRoll));
            t.scale.set((float) layer.scaleX, (float) layer.scaleY, (float) layer.scaleZ);
            t.rotationMode = Transform.RotationMode.EULER;

            return t;
        }

        if (channel == layer.channelColor)
        {
            float a = (float) MathUtils.clamp(layer.opacity, 0.0, 1.0);

            /* On a scene light this channel animates the light colour */
            if (isSceneLightType(layer.type))
            {
                return new Color(Colors.getR(layer.lightColor), Colors.getG(layer.lightColor), Colors.getB(layer.lightColor), a);
            }

            return new Color(layer.tintR, layer.tintG, layer.tintB, a);
        }

        if (channel == layer.channelVisible) return layer.visible ? 1.0 : 0.0;
        if (channel == layer.channelIntensity) return layer.lightIntensity;
        if (channel == layer.channelRange) return layer.lightRange;
        if (channel == layer.channelCone) return layer.lightCone;
        if (channel == layer.channelPose) return layer.entityPose;

        return null;
    }

    /* ── Hub Screen & Scene Management ── */
    public UIMMotionHubScreen hubScreen;
    public MotionScene currentScene;

    public final List<MotionLayer> layers = new ArrayList<>();
    public int selectedLayerIndex = 0;
    public Keyframe selectedKeyframe = null;

    /* ── Playback & Timeline State ── */
    public boolean isPlaying = false;
    public int currentFrame = 0;
    public int maxFrames = 600;
    public boolean loop = true;
    private long lastTickTimeMs = 0L;
    public final FoldState<String> timelineFolds = new FoldState<>(true);

    /* Transform rows whose fold state was already given its starting value (folded unless the overlay has keys) */
    private final java.util.Set<String> seenTransformFolds = new java.util.HashSet<>();

    /* ── Viewport Camera State ── */
    public float studioYaw = 45F;
    public float studioPitch = -25F;
    public float studioZoom = 1.0F;
    public boolean showGrid = true;
    public boolean studioLights = true;
    public int viewportMode = 0; // 0: Lit, 1: Unlit, 2: Wireframe
    public int cameraPreset = 0; // 0: Perspective, 1: Front, 2: Top, 3: Isometric
    private boolean draggingViewport = false;
    private int lastMouseX, lastMouseY;

    /* ── UI Layout Containers & Resizers ── */
    public int leftW = 210;
    public int rightW = 230;
    public int timelineH = 150;

    public UIElement editorSubBar;
    public UIElement sceneTreeArea;
    public UIElement viewportArea;
    public UIMMotionViewport viewportRenderer;
    public UIPropTransform transformEditor;
    public final Transform liveTransform = new Transform();

    public void syncLiveTransformFromLayer(MotionLayer layer)
    {
        if (layer == null) return;
        this.liveTransform.translate.set((float) layer.posX, (float) layer.posY, (float) layer.posZ);
        this.liveTransform.rotate.set(MathUtils.toRad((float) layer.rotPitch), MathUtils.toRad((float) layer.rotYaw), MathUtils.toRad((float) layer.rotRoll));
        this.liveTransform.scale.set((float) layer.scaleX, (float) layer.scaleY, (float) layer.scaleZ);
        this.liveTransform.rotationMode = Transform.RotationMode.EULER;
    }

    public void syncLayerFromLiveTransform(MotionLayer layer)
    {
        if (layer == null) return;
        /* The live transform mirrors the selected actor only: never leak it into another actor */
        if (layer != this.getSelected()) return;

        layer.posX = this.liveTransform.translate.x;
        layer.posY = this.liveTransform.translate.y;
        layer.posZ = this.liveTransform.translate.z;
        Vector3f euler = new Vector3f();
        this.liveTransform.getEulerRotation(euler);
        layer.rotPitch = MathUtils.toDeg(euler.x);
        layer.rotYaw = MathUtils.toDeg(euler.y);
        layer.rotRoll = MathUtils.toDeg(euler.z);
        layer.scaleX = this.liveTransform.scale.x;
        layer.scaleY = this.liveTransform.scale.y;
        layer.scaleZ = this.liveTransform.scale.z;
    }
    public UIElement propertiesArea;
    public UIElement timelineArea;

    public UIDraggable leftResizer;
    public UIDraggable rightResizer;
    public UIDraggable bottomResizer;

    public UIScrollView layersScroll;
    public UIIcon addActorBtn;
    public UIScrollView propertiesScroll;
    public UIElement actorCard;
    public UITextbox actorNameInput;
    public UIMMotionKeyframes keyframesView;

    /* Property inputs */
    public UITrackpad posXInput, posYInput, posZInput;
    public UITrackpad rotPitchInput, rotYawInput, rotRollInput;
    public UITrackpad scaleXInput, scaleYInput, scaleZInput;
    public boolean lockScale = false;
    public UIIcon lockScaleBtn;
    public UIToggle visibilityToggle;
    public UILabel actorColorLabel;
    public UIColor actorColorPicker;
    public UILabel opacityLabel;
    public UITrackpad opacityInput;
    public UIToggle shadingToggle;
    public UILabel emptySelectionPlaceholder;

    /* Keyframe Details elements */
    public UIElement keyframeSection;
    public UILabel keyframeTitleLabel;
    public UILabel keyframeChannelLabel;
    public UITrackpad keyframeTickInput;
    public UIIcon keyframeInterpBtn;

    /* Actor Specific Panels & Inputs */
    public UIElement blockSection;
    public UIBlockStateEditor blockStateEditor;
    public UITrackpad blockLightInput;

    public UIElement itemSection;
    public UIItemStack itemStackEditor;
    public UITrackpad itemCountInput;
    public UIToggle itemGlintToggle;
    public UIButton itemTransformModeBtn;

    public UIElement entitySection;
    public UIButton chooseModelBtn;

    public UIElement structureSection;
    public UITextbox structureNameInput;
    public UIButton pickStructureBtn;
    public UIButton openStructureFolderBtn;
    public UIButton centerStructureBtn;

    /* Keyframe-driven Properties panel (BBS keyframe editor embedded here) */
    private UIKeyframeFactory keyframePanel;
    private Keyframe keyframePanelFor;
    private final List<Runnable> keyframeRefreshers = new ArrayList<>();

    public UIElement lightSection;
    public UILabel lightTypeLabel;
    public UIElement cameraSection;
    public UITrackpad cameraFovInput;
    public UIButton cameraViewBtn;
    public UIToggle cameraRenderToggle;
    public UIIcon camViewBtn;

    /** Id of the camera actor the viewport currently looks through (null = the free studio camera). */
    public String viewCameraId = null;
    public UIColor lightColorPicker;
    public UITrackpad lightIntensityInput;
    public UITrackpad lightRangeInput;
    public UITrackpad lightConeInput;
    public UITrackpad lightPenumbraInput;
    public UITrackpad lightWidthInput;
    public UITrackpad lightHeightInput;
    public UIElement lightRangeGroup;
    public UIElement lightSpotGroup;
    public UIElement lightRectGroup;
    public UITrackpad lightGlowInput;
    public UIElement lightGlowGroup;

    /* Top Bar Elements */
    public UIIcon backToHubBtn;
    public UIElement centerCapsule;
    public UIButton litBtn;
    public UIButton unlitBtn;
    public UIButton wireBtn;
    public UIIcon undoBtn;
    public UIIcon redoBtn;
    public UIIcon saveBtn;
    public UIIcon settingsBtn;
    public UIIcon renderBtn;
    public UIIcon resetCameraBtn;

    /* Timeline controls */
    public UIIcon playPauseBtn;
    public UIIcon stopBtn;
    public UIIcon loopBtn;

    public UIMMotionPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.onClose(this::releasePanelResources);
        this.onDisappear(this::releasePanelResources);

        int topBarH = this.topBar != null && this.topBar.area.h > 0 ? this.topBar.area.h : UIPanelTopBar.HEIGHT;
        this.editor.relative(this).x(0).y(topBarH).w(1F).h(1F, -topBarH);
        this.editor.markContainer().noCulling();

        this.setupEditorSubBar();
        this.setupSceneTree();
        this.setupViewport();
        this.setupProperties();
        this.setupTimeline();
        this.setupResizers();

        this.editor.add(
            this.editorSubBar,
            this.sceneTreeArea,
            this.viewportArea,
            this.propertiesArea,
            this.timelineArea,
            this.leftResizer,
            this.rightResizer,
            this.bottomResizer
        );

        /* Action buttons in the dashboard top bar on the right */
        UIIcon openFolderAction = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(UIMMotionHubScreen.getScenesFolder()));
        openFolderAction.tooltip(UIKeys.MMOTION_HUB_OPEN_FOLDER);
        this.actions().common(openFolderAction);

        /* Mount Hub screen as the landing screen */
        this.mountLanding();

        /* Ensure editor starts hidden and landing starts visible */
        this.editor.setVisible(false);
    }

    @Override
    protected void mountLanding()
    {
        this.hubScreen = new UIMMotionHubScreen(this);
        this.landing = this.hubScreen;

        this.add(this.layoutUnderTopBar(this.landing));

        this.syncLanding();
    }

    @Override
    public void syncLanding()
    {
        super.syncLanding();

        boolean inEditor = this.tabs.getCurrentId() != null;

        if (this.editor != null)
        {
            this.editor.setVisible(inEditor);
            if (inEditor && !this.getChildren().contains(this.editor))
            {
                this.add(this.layoutUnderTopBar(this.editor));
            }
        }

        if (this.hubScreen != null)
        {
            this.hubScreen.setVisible(!inEditor);
            if (!inEditor)
            {
                if (!this.getChildren().contains(this.hubScreen))
                {
                    this.add(this.layoutUnderTopBar(this.hubScreen));
                }
                this.hubScreen.refresh();
            }
        }

        if (inEditor)
        {
            this.forceEditorLayout();
            if (this.editor != null)
            {
                this.editor.resize();
            }
            this.resize();
        }
        else
        {
            this.resize();
        }
    }

    /* ── Hub & Scene Switching Methods ── */
    public void openHub()
    {
        int empty = this.tabs.findEmptyTab();

        if (empty >= 0)
        {
            this.tabs.switchTab(empty);
        }
        else
        {
            this.tabs.addTab();
        }

        this.syncLanding();
    }

    public void openScene(MotionScene scene)
    {
        if (scene == null)
        {
            return;
        }

        /* Tabs carry the unique scene id: two scenes with the same name never share a tab */
        for (int i = 0; i < this.tabs.size(); i++)
        {
            if (scene.id.equals(this.tabs.get(i).dataId))
            {
                this.tabs.switchTab(i);
                this.loadScene(scene);
                return;
            }
        }

        if (this.tabs.getCurrentId() == null)
        {
            this.tabs.pick(scene.id);
        }
        else
        {
            this.tabs.addTab();
            this.tabs.pick(scene.id);
        }

        this.loadScene(scene);
    }

    @Override
    protected void showTab(String id)
    {
        /* Never lose work when switching tabs / going back to the hub */
        if (this.currentScene != null)
        {
            this.saveScene(true);
        }

        if (id == null)
        {
            this.currentScene = null;

            if (this.editor != null)
            {
                this.editor.setVisible(false);
            }

            if (this.hubScreen != null)
            {
                if (!this.getChildren().contains(this.hubScreen))
                {
                    this.add(this.layoutUnderTopBar(this.hubScreen));
                }
                this.hubScreen.setVisible(true);
                this.hubScreen.refresh();
            }

            this.resize();
        }
        else
        {
            MotionScene scene = this.hubScreen != null ? this.hubScreen.getScene(id) : null;
            if (scene == null)
            {
                scene = new MotionScene("scene_" + System.currentTimeMillis(), id, 600);
                /* Temiz Başlangıç: Yeni açılan sahne boş başlar */
                if (this.hubScreen != null)
                {
                    this.hubScreen.getScenes().add(0, scene);
                    this.hubScreen.refresh();
                }
            }

            this.loadScene(scene);

            /* A tab that asked for an unknown id now holds the fresh scene: record its real id */
            if (!scene.id.equals(id))
            {
                this.tabs.setOpenId(scene.id);
            }

            if (this.editor != null)
            {
                this.editor.resize();
            }
            this.resize();
        }
    }

    public void loadScene(MotionScene scene)
    {
        this.currentScene = scene;
        this.layers.clear();
        if (scene != null)
        {
            this.layers.addAll(scene.layers);
            this.maxFrames = scene.duration;
        }
        this.currentFrame = 0;
        this.isPlaying = false;
        this.selectedLayerIndex = this.layers.isEmpty() ? -1 : 0;
        this.selectedKeyframe = null;
        this.updateLayersFromKeyframes();

        this.rebuildLayersList();
        this.rebuildKeyframeSheets();
        this.syncPropertiesFromSelected();
        this.resetHistory();

        if (this.hubScreen != null)
        {
            this.hubScreen.setVisible(false);
        }

        if (!this.getChildren().contains(this.editor))
        {
            this.add(this.layoutUnderTopBar(this.editor));
        }

        if (this.editor != null)
        {
            this.editor.setVisible(true);
            int topBarH = this.topBar != null && this.topBar.area.h > 0 ? this.topBar.area.h : UIPanelTopBar.HEIGHT;
            this.editor.relative(this).x(0).y(topBarH).w(1F).h(1F, -topBarH);
        }

        this.forceEditorLayout();
        if (this.editor != null)
        {
            this.editor.resize();
        }
        this.resize();
    }

    @Override
    public String getOpenId()
    {
        return this.currentScene == null ? null : this.currentScene.id;
    }

    /** Display name of the scene behind a tab id (the id itself when the scene is gone). */
    private String sceneName(String id)
    {
        MotionScene scene = this.hubScreen != null ? this.hubScreen.getScene(id) : null;

        if (scene == null && this.currentScene != null && id.equals(this.currentScene.id))
        {
            scene = this.currentScene;
        }

        return scene != null ? scene.name : id;
    }

    @Override
    public IKey getTabLabel(String id)
    {
        return IKey.raw(this.sceneName(id));
    }

    @Override
    public void saveOpen()
    {
        if (this.currentScene != null)
        {
            this.saveScene(true);
        }
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.CURVES;
    }

    @Override
    public IKey getNewTabLabel()
    {
        return UIKeys.NEW_TAB;
    }

    @Override
    public IKey getTabTooltip(String id)
    {
        if (id == null)
        {
            return UIKeys.NEW_TAB;
        }
        return IKey.raw(this.sceneName(id));
    }

    /* ILandingHost implementation */

    @Override
    public IKey getTitle()
    {
        return UIKeys.MMOTION_TITLE;
    }

    @Override
    public String getRecentType()
    {
        return "mmotion";
    }

    @Override
    public void openDataManager()
    {
        this.openHub();
    }

    @Override
    public void requestNames()
    {
        if (this.hubScreen != null)
        {
            List<String> names = new ArrayList<>();
            for (MotionScene s : this.hubScreen.getScenes())
            {
                names.add(s.id);
            }
            this.fillNames(names);
        }
    }

    @Override
    public IKey getCreateLabel()
    {
        return UIKeys.MMOTION_NEW_SCENE;
    }

    @Override
    public void addNewData(UIContext context)
    {
        if (this.hubScreen != null)
        {
            this.hubScreen.openNewSceneDialog();
        }
    }

    @Override
    public File getDataFolder()
    {
        return UIMMotionHubScreen.getScenesFolder();
    }

    /* ── Editor Sub-Bar Setup ── */
    private void setupEditorSubBar()
    {
        this.editorSubBar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                int bg = BBSSettings.lightSurfaces() ? 0xDDEDEDED : 0xDD181818;
                int border = BBSSettings.lightSurfaces() ? 0x33000000 : 0x22FFFFFF;

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
                context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), border);

                FontRenderer font = context.batcher.getFont();
                int cy = a.y + (a.h - font.getHeight()) / 2;

                /* Icon & Title */
                int titleX = a.x + 30;
                context.batcher.icon(Icons.CURVES, Colors.WHITE, titleX, a.y + (a.h - 16) / 2);
                context.batcher.text("mMotion Studio", titleX + 20, cy, Colors.WHITE);

                /* Save state, right-aligned in front of the action buttons */
                String stateText = UIMMotionPanel.this.getSaveStateText();
                int stateColor = UIMMotionPanel.this.isUnsaved() ? 0xFFFBBF24 : 0xFF94A3B8;
                int stateX = a.ex() - 6 - 5 * 22 - 8 - font.getWidth(stateText);
                context.batcher.text(stateText, stateX, cy, stateColor);

                super.render(context);
            }
        };

        /* Back to Hub Button on the far left of the content bar */
        this.backToHubBtn = new UIIcon(Icons.LEFTLOAD, (b) -> this.openHub());
        this.backToHubBtn.tooltip(UIKeys.MMOTION_BACK_TO_HUB);
        this.backToHubBtn.relative(this.editorSubBar).x(6).y(2).wh(20, 20);


        /* Center Capsule (Sadeleştirilmiş: Viewport Modları ve Kamera Sıfırlama) */
        this.centerCapsule = new UIElement();
        this.centerCapsule.relative(this.editorSubBar).x(0.5F).y(2).wh(202, 20).anchor(0.5F, 0F);

        this.centerCapsule.add(new UIRenderable((context) ->
        {
            Area a = this.centerCapsule.area;
            context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 6, Colors.A50, 0);
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xd010141e);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), 0x38ffffff);
        }));

        this.litBtn = new UIButton(IKey.raw("Lit"), (b) -> this.setViewportMode(0));
        this.litBtn.tooltip(UIKeys.MMOTION_VIEW_LIT_TOOLTIP);
        this.litBtn.relative(this.centerCapsule).x(3).y(1).wh(40, 18);

        this.unlitBtn = new UIButton(IKey.raw("Unlit"), (b) -> this.setViewportMode(1));
        this.unlitBtn.tooltip(UIKeys.MMOTION_VIEW_UNLIT_TOOLTIP);
        this.unlitBtn.relative(this.centerCapsule).x(45).y(1).wh(44, 18);

        this.wireBtn = new UIButton(IKey.raw("Wireframe"), (b) -> this.setViewportMode(2));
        this.wireBtn.tooltip(UIKeys.MMOTION_VIEW_WIREFRAME_TOOLTIP);
        this.wireBtn.relative(this.centerCapsule).x(91).y(1).wh(58, 18);

        this.resetCameraBtn = new UIIcon(Icons.REFRESH, (b) ->
        {
            if (this.viewportRenderer != null)
            {
                this.viewportRenderer.reset();
                this.viewportRenderer.setPosition(0, 0.5F, 0);
                this.viewportRenderer.setDistance(4.5F);
                this.viewportRenderer.setRotation(45, 30);
            }
            this.cameraPreset = 0;
            this.viewCameraId = null;
            UIUtils.playClick();
        });
        this.resetCameraBtn.tooltip(UIKeys.MMOTION_RESET_CAMERA);
        this.resetCameraBtn.relative(this.centerCapsule).x(152).y(1).wh(22, 18);

        this.camViewBtn = new UIIcon(Icons.CAMERA, (b) -> this.toggleCameraViewQuick());
        this.camViewBtn.tooltip(UIKeys.MMOTION_CAMERA_VIEW);
        this.camViewBtn.relative(this.centerCapsule).x(176).y(1).wh(22, 18);

        this.centerCapsule.add(this.litBtn, this.unlitBtn, this.wireBtn, this.resetCameraBtn, this.camViewBtn);
        this.refreshViewportModeButtons();

        /* Right side actions: undo, redo, save, project settings, render */
        this.undoBtn = new UIIcon(Icons.UNDO, (b) -> this.undo());
        this.undoBtn.tooltip(UIKeys.MMOTION_UNDO);
        this.undoBtn.relative(this.editorSubBar).x(1F, -(6 + 5 * 22)).y(2).wh(20, 20);

        this.redoBtn = new UIIcon(Icons.REDO, (b) -> this.redo());
        this.redoBtn.tooltip(UIKeys.MMOTION_REDO);
        this.redoBtn.relative(this.editorSubBar).x(1F, -(6 + 4 * 22)).y(2).wh(20, 20);

        this.saveBtn = new UIIcon(Icons.SAVED, (b) -> this.saveScene(false));
        this.saveBtn.tooltip(UIKeys.MMOTION_SAVE);
        this.saveBtn.relative(this.editorSubBar).x(1F, -(6 + 3 * 22)).y(2).wh(20, 20);

        this.settingsBtn = new UIIcon(Icons.GEAR, (b) -> this.openProjectSettings());
        this.settingsBtn.tooltip(UIKeys.MMOTION_PROJECT_SETTINGS_TOOLTIP);
        this.settingsBtn.relative(this.editorSubBar).x(1F, -(6 + 2 * 22)).y(2).wh(20, 20);

        this.renderBtn = new UIIcon(Icons.FILM, (b) -> this.openRenderDialog());
        this.renderBtn.tooltip(UIKeys.MMOTION_RENDER_TOOLTIP);
        this.renderBtn.relative(this.editorSubBar).x(1F, -(6 + 22)).y(2).wh(20, 20);

        this.editorSubBar.markContainer().noCulling();
        this.backToHubBtn.noCulling();
        this.centerCapsule.markContainer().noCulling();
        this.litBtn.noCulling();
        this.unlitBtn.noCulling();
        this.wireBtn.noCulling();
        this.resetCameraBtn.noCulling();
        this.camViewBtn.noCulling();
        this.undoBtn.noCulling();
        this.redoBtn.noCulling();
        this.saveBtn.noCulling();
        this.settingsBtn.noCulling();
        this.renderBtn.noCulling();
        this.editorSubBar.add(this.backToHubBtn, this.centerCapsule, this.undoBtn, this.redoBtn, this.saveBtn, this.settingsBtn, this.renderBtn);
    }

    /* ── Resizers Setup ── */
    private void setupResizers()
    {
        /* Left Splitter: between Scene Tree and Viewport */
        this.leftResizer = new UIDraggable((context) ->
        {
            this.leftW = MathUtils.clamp(context.mouseX - this.editor.area.x, 140, Math.min(380, this.editor.area.w - this.rightW - 120));
            this.resize();
        });
        this.leftResizer.hoverOnly().cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        this.leftResizer.rendering((context) ->
        {
            if (this.leftResizer.area.isInside(context) || this.leftResizer.isDragging())
            {
                Area a = this.leftResizer.area;
                context.batcher.box(a.x + a.w / 2, a.y, a.x + a.w / 2 + 1, a.ey(), 0xFF3B82F6);
            }
        });

        /* Right Splitter: between Viewport and Properties */
        this.rightResizer = new UIDraggable((context) ->
        {
            this.rightW = MathUtils.clamp(this.editor.area.ex() - context.mouseX, 180, Math.min(400, this.editor.area.w - this.leftW - 120));
            this.resize();
        });
        this.rightResizer.hoverOnly().cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        this.rightResizer.rendering((context) ->
        {
            if (this.rightResizer.area.isInside(context) || this.rightResizer.isDragging())
            {
                Area a = this.rightResizer.area;
                context.batcher.box(a.x + a.w / 2, a.y, a.x + a.w / 2 + 1, a.ey(), 0xFF3B82F6);
            }
        });

        /* Bottom Splitter: between Viewport and Timeline */
        this.bottomResizer = new UIDraggable((context) ->
        {
            this.timelineH = MathUtils.clamp(this.editor.area.ey() - context.mouseY, 100, Math.min(450, this.editor.area.h - 120));
            this.resize();
        });
        this.bottomResizer.hoverOnly().cursors(GLFW.GLFW_VRESIZE_CURSOR, GLFW.GLFW_VRESIZE_CURSOR);
        this.bottomResizer.rendering((context) ->
        {
            if (this.bottomResizer.area.isInside(context) || this.bottomResizer.isDragging())
            {
                Area a = this.bottomResizer.area;
                context.batcher.box(a.x, a.y + a.h / 2, a.ex(), a.y + a.h / 2 + 1, 0xFF3B82F6);
            }
        });

        this.leftResizer.noCulling();
        this.rightResizer.noCulling();
        this.bottomResizer.noCulling();
    }

    /* ── Sol Alan: Sahne Ağacı (Scene Hierarchy / Outliner) ── */
    private void setupSceneTree()
    {
        this.sceneTreeArea = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                int bg = BBSSettings.lightSurfaces() ? 0xDDEDEDED : 0xDD181818;
                int border = BBSSettings.lightSurfaces() ? 0x33000000 : 0x22FFFFFF;

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
                context.batcher.box(a.ex() - 1, a.y, a.ex(), a.ey(), border);

                /* Header */
                context.batcher.box(a.x, a.y, a.ex(), a.y + 24, BBSSettings.lightSurfaces() ? 0xFFE2E2E2 : 0xFF202020);
                context.batcher.box(a.x, a.y + 23, a.ex(), a.y + 24, border);

                FontRenderer font = context.batcher.getFont();
                context.batcher.icon(Icons.LAYOUT, Colors.WHITE, a.x + 6, a.y + 4);
                context.batcher.text(UIKeys.MMOTION_SCENE_TREE.get(), a.x + 24, a.y + (24 - font.getHeight()) / 2, Colors.WHITE);

                super.render(context);
            }
        };
        this.sceneTreeArea.markContainer().noCulling();

        /* Add Actor (+) Button on top right of Outliner Header */
        this.addActorBtn = new UIIcon(Icons.ADD, (b) -> this.openAddActorMenu());
        this.addActorBtn.tooltip(UIKeys.MMOTION_ADD_ACTOR);
        this.addActorBtn.relative(this.sceneTreeArea).x(1F, -22).y(3).wh(18, 18);
        this.addActorBtn.noCulling();

        this.layersScroll = new UIScrollView()
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                boolean handled = super.subMouseClicked(context);
                if (!handled && this.area.isInside(context) && context.mouseButton == 0)
                {
                    UIMMotionPanel.this.selectedLayerIndex = -1;
                    UIMMotionPanel.this.selectedKeyframe = null;
                    UIMMotionPanel.this.syncPropertiesFromSelected();
                    UIMMotionPanel.this.rebuildLayersList();
                    return true;
                }
                return handled;
            }
        };
        this.layersScroll.markContainer().noCulling();
        this.rebuildLayersList();

        this.sceneTreeArea.add(this.addActorBtn, this.layersScroll);
    }

    public void openAddActorMenu()
    {
        ContextMenuManager manager = new ContextMenuManager();
        manager.action(Icons.BLOCK, UIKeys.MMOTION_ADD_BLOCK, () -> this.addLayer("New Block", "Block", Icons.BLOCK, 0xFF10B981));
        manager.action(Icons.ARMOR_CHESTPLATE, UIKeys.MMOTION_ADD_ITEM, () -> this.addLayer("New Item", "Item", Icons.ARMOR_CHESTPLATE, 0xFFF59E0B));
        manager.action(Icons.POSE, UIKeys.MMOTION_ADD_MORPH, () -> this.openAddMorphDialog(null));
        manager.action(Icons.STRUCTURE, UIKeys.MMOTION_ADD_STRUCTURE, () -> this.openAddStructureDialog(null));
        manager.action(Icons.SPARKLES, UIKeys.MMOTION_ADD_POINT_LIGHT, () -> this.addLayer("Point Light", "PointLight", Icons.SPARKLES, 0xFFFBBF24));
        manager.action(Icons.SUN, UIKeys.MMOTION_ADD_SPOT_LIGHT, () -> this.addLayer("Spot Light", "SpotLight", Icons.SUN, 0xFFFB923C));
        manager.action(Icons.BLOCK, UIKeys.MMOTION_ADD_RECT_LIGHT, () -> this.addLayer("Rect Light", "RectLight", Icons.BLOCK, 0xFFA78BFA));
        manager.action(Icons.CAMERA, UIKeys.MMOTION_ADD_CAMERA, () -> this.addLayer("Camera", "Camera", Icons.CAMERA, 0xFF60A5FA));

        this.getContext().setContextMenu(manager.create());
    }

    public void openAddActorMenuForParent(MotionLayer parent)
    {
        ContextMenuManager manager = new ContextMenuManager();
        manager.action(Icons.BLOCK, UIKeys.MMOTION_ADD_BLOCK, () -> this.addChildLayer(parent, "New Block", "Block", Icons.BLOCK, 0xFF10B981));
        manager.action(Icons.ARMOR_CHESTPLATE, UIKeys.MMOTION_ADD_ITEM, () -> this.addChildLayer(parent, "New Item", "Item", Icons.ARMOR_CHESTPLATE, 0xFFF59E0B));
        manager.action(Icons.POSE, UIKeys.MMOTION_ADD_MORPH, () -> this.openAddMorphDialog(parent));
        manager.action(Icons.STRUCTURE, UIKeys.MMOTION_ADD_STRUCTURE, () -> this.openAddStructureDialog(parent));
        manager.action(Icons.SPARKLES, UIKeys.MMOTION_ADD_POINT_LIGHT, () -> this.addChildLayer(parent, "Point Light", "PointLight", Icons.SPARKLES, 0xFFFBBF24));
        manager.action(Icons.SUN, UIKeys.MMOTION_ADD_SPOT_LIGHT, () -> this.addChildLayer(parent, "Spot Light", "SpotLight", Icons.SUN, 0xFFFB923C));
        manager.action(Icons.BLOCK, UIKeys.MMOTION_ADD_RECT_LIGHT, () -> this.addChildLayer(parent, "Rect Light", "RectLight", Icons.BLOCK, 0xFFA78BFA));
        manager.action(Icons.CAMERA, UIKeys.MMOTION_ADD_CAMERA, () -> this.addChildLayer(parent, "Camera", "Camera", Icons.CAMERA, 0xFF60A5FA));

        this.getContext().setContextMenu(manager.create());
    }

    public void openAddMorphDialog(MotionLayer parent)
    {
        UIFormPalette.open(this, false, null, (f) ->
        {
            if (f != null)
            {
                String name = f.getDisplayName();
                if (name == null || name.isEmpty()) name = "Morph";
                MotionLayer layer = new MotionLayer(name + " " + (this.layers.size() + 1), "Entity", Icons.POSE, 0xFFEC4899);
                layer.form = f;
                if (f instanceof ModelForm mf && mf.model.get() != null)
                {
                    layer.entityModel = mf.model.get();
                }
                if (parent != null)
                {
                    layer.parentId = parent.id;
                    parent.collapsed = false;
                }
                if (this.viewportRenderer != null)
                {
                    this.viewportRenderer.resetGizmoState();
                }
                this.layers.add(layer);
                this.selectLayer(layer);
                this.rebuildLayersList();
                this.rebuildKeyframeSheets();
                this.syncPropertiesFromSelected();
                UIUtils.playClick();
            }
        });
    }

    public void openAddStructureDialog(MotionLayer parent)
    {
        StructureManager.invalidate();
        UIStringOverlayPanel panel = new UIStringOverlayPanel(
            UIKeys.MMOTION_PICK_STRUCTURE,
            StructureManager.getStructureIds(),
            (id) ->
            {
                if (id != null && !id.isEmpty())
                {
                    String shortName = id.contains(":") ? id.substring(id.lastIndexOf(':') + 1) : id;
                    MotionLayer layer = new MotionLayer(shortName + " " + (this.layers.size() + 1), "Structure", Icons.STRUCTURE, 0xFF3B82F6);
                    layer.structureName = id;
                    StructureForm sf = new StructureForm();
                    sf.structure.set(id);
                    layer.form = sf;
                    this.centerStructureLayer(layer);
                    if (parent != null)
                    {
                        layer.parentId = parent.id;
                        parent.collapsed = false;
                    }
                    if (this.viewportRenderer != null)
                    {
                        this.viewportRenderer.resetGizmoState();
                    }
                    this.layers.add(layer);
                    this.selectLayer(layer);
                    this.rebuildLayersList();
                    this.rebuildKeyframeSheets();
                    this.syncPropertiesFromSelected();
                    UIUtils.playClick();
                }
            }
        );
        UIOverlay.addOverlay(this.getContext(), panel, 260, 300);
    }

    public void openMorphPicker(MotionLayer layer)
    {
        if (layer == null) return;
        UIFormPalette.open(this, false, layer.form, (f) ->
        {
            if (f != null)
            {
                layer.form = f;
                if (f instanceof ModelForm mf && mf.model.get() != null)
                {
                    layer.entityModel = mf.model.get();
                }
                String name = f.getDisplayName();
                if (name != null && !name.isEmpty())
                {
                    layer.name = name;
                }
                this.rebuildLayersList();
                this.syncPropertiesFromSelected();
                UIUtils.playClick();
            }
        });
    }

    public void openStructurePicker()
    {
        MotionLayer layer = this.getSelected();
        if (layer == null) return;
        StructureManager.invalidate();
        UIStringOverlayPanel panel = new UIStringOverlayPanel(
            UIKeys.MMOTION_PICK_STRUCTURE,
            StructureManager.getStructureIds(),
            (id) ->
            {
                if (id != null && !id.isEmpty())
                {
                    layer.structureName = id;
                    if (layer.form instanceof StructureForm sf)
                    {
                        sf.structure.set(id);
                    }
                    else
                    {
                        StructureForm sf = new StructureForm();
                        sf.structure.set(id);
                        layer.form = sf;
                    }
                    this.centerStructureLayer(layer);
                    this.syncPropertiesFromSelected();
                    UIUtils.playClick();
                }
            }
        );
        panel.set(layer.structureName != null ? layer.structureName : "");
        UIOverlay.addOverlay(this.getContext(), panel, 260, 300);
    }

    public void centerStructureLayer(MotionLayer layer)
    {
        if (layer == null) return;
        if (layer.structureName != null && !layer.structureName.isEmpty())
        {
            StructureRenderData data = StructureManager.get(layer.structureName);
            if (data != null && data.size != null)
            {
                layer.structureOffsetX = -data.size.getX() / 2.0;
                layer.structureOffsetY = 0.0;
                layer.structureOffsetZ = -data.size.getZ() / 2.0;
                return;
            }
        }
        layer.structureOffsetX = 0.0;
        layer.structureOffsetY = 0.0;
        layer.structureOffsetZ = 0.0;
    }

    public void addChildLayer(MotionLayer parent, String name, String type, Icon icon, int color)
    {
        MotionLayer child = new MotionLayer(name + " " + (this.layers.size() + 1), type, icon, color);
        if (parent != null)
        {
            child.parentId = parent.id;
            parent.collapsed = false;
        }
        if (this.viewportRenderer != null)
        {
            this.viewportRenderer.resetGizmoState();
        }
        this.layers.add(child);
        this.selectLayer(child);
        this.rebuildLayersList();
        this.rebuildKeyframeSheets();
        this.syncPropertiesFromSelected();
        UIUtils.playClick();
    }

    public List<MotionLayer> getRootLayers()
    {
        List<MotionLayer> roots = new ArrayList<>();
        for (MotionLayer layer : this.layers)
        {
            if (layer.parentId == null || this.findLayerById(layer.parentId) == null)
            {
                roots.add(layer);
            }
        }
        return roots;
    }

    public List<MotionLayer> getChildLayers(MotionLayer parent)
    {
        List<MotionLayer> children = new ArrayList<>();
        if (parent == null || parent.id == null) return children;
        for (MotionLayer layer : this.layers)
        {
            if (parent.id.equals(layer.parentId))
            {
                children.add(layer);
            }
        }
        return children;
    }

    public MotionLayer findLayerById(String id)
    {
        if (id == null) return null;
        for (MotionLayer layer : this.layers)
        {
            if (id.equals(layer.id)) return layer;
        }
        return null;
    }

    /* Light actor types: the legacy marker "Light" plus the three scene lights */
    public static boolean isLightType(String type)
    {
        return "Light".equalsIgnoreCase(type) || isSceneLightType(type);
    }

    public static boolean isSceneLightType(String type)
    {
        return "PointLight".equalsIgnoreCase(type) || "SpotLight".equalsIgnoreCase(type) || "RectLight".equalsIgnoreCase(type);
    }

    /**
     * Diffuse contribution of the visible scene lights on an actor (Block / Item / Entity / Structure).
     * Returns an RGB multiplier (0..1) for the actor's tint, or null when the scene has no lights.
     * Everything is evaluated in the light's local space (forward = -Y), so parenting and rotation are honoured.
     */
    public static final float AMBIENT_WITH_LIGHTS = 0.12F;
    public static final float LIGHT_RESPONSE_GAIN = 25F;
    public static final float LIGHT_RESPONSE_MAX = 14F;

    public float[] computeLightMultiplier(MotionLayer target)
    {
        if (target == null || target.type == null) return null;

        String t = target.type;

        if (!(t.equalsIgnoreCase("Block") || t.equalsIgnoreCase("Item") || t.equalsIgnoreCase("Entity") || t.equalsIgnoreCase("Structure")))
        {
            return null;
        }

        Vector3d wp = null;
        /* With scene lights the studio goes dark: only lit surfaces rise above this base level */
        float ar = 0F;
        float ag = 0F;
        float ab = 0F;
        boolean any = false;

        for (MotionLayer l : this.layers)
        {
            if (!l.visible || !isSceneLightType(l.type)) continue;

            any = true;

            if (wp == null) wp = this.getWorldPosition(target);

            Matrix4f inv = new Matrix4f(this.getWorldMatrix(l)).invert();
            Vector3f lp = inv.transformPosition(new Vector3f((float) wp.x, (float) wp.y, (float) wp.z));
            float range = (float) Math.max(0.1, l.lightRange);
            float d;
            float dirCos;

            if (l.type.equalsIgnoreCase("RectLight"))
            {
                float nx = (float) MathUtils.clamp(lp.x, -l.lightWidth / 2.0, l.lightWidth / 2.0);
                float nz = (float) MathUtils.clamp(lp.z, -l.lightHeight / 2.0, l.lightHeight / 2.0);
                float dx = lp.x - nx;
                float dz = lp.z - nz;

                /* Distance to the closest point of the oriented quad (local x / z are the panel axes, local -Y is its front normal) */
                float soft = 0.25F * (float) Math.sqrt(Math.max(0.01, l.lightWidth * l.lightHeight));

                d = (float) Math.sqrt(dx * dx + lp.y * lp.y + dz * dz + soft * soft);

                if (-lp.y <= 0F) continue;

                dirCos = d > 1.0E-4F ? (-lp.y) / d : 1F;
            }
            else
            {
                d = lp.length();
                dirCos = d > 1.0E-4F ? (-lp.y) / d : 1F;
            }

            float shape = 1F;

            if (l.type.equalsIgnoreCase("SpotLight"))
            {
                double half = Math.toRadians(MathUtils.clamp(l.lightCone, 5.0, 120.0) / 2.0);
                float outer = (float) Math.cos(half);
                float inner = (float) Math.cos(half * (1.0 - MathUtils.clamp(l.lightPenumbra, 0.0, 1.0)));
                float s = MathUtils.clamp((dirCos - outer) / Math.max(1.0E-4F, inner - outer), 0F, 1F);

                shape = s * s * (3F - 2F * s);
            }
            else if (l.type.equalsIgnoreCase("RectLight"))
            {
                shape = MathUtils.clamp(dirCos, 0F, 1F);
            }

            float att = MathUtils.clamp(1F - d / range, 0F, 1F);
            float k = (float) l.lightIntensity * att * att * shape * 0.7F;

            ar += Colors.getR(l.lightColor) * k;
            ag += Colors.getG(l.lightColor) * k;
            ab += Colors.getB(l.lightColor) * k;
        }

        if (!any) return null;

        /*
         * HDR accumulation: the energy is never clamped per channel (a per-channel clamp turns every strong light white).
         * Instead the whole colour is normalised by its brightest channel, which keeps the light's hue and saturation
         * (a red light stays a saturated red at intensity 8, 15 or 30) while a higher intensity keeps widening the lit area.
         */
        float peak = Math.max(ar, Math.max(ag, ab));

        if (peak > 1.0E-6F)
        {
            /*
             * Logarithmic response (not a saturating x / (x + 1)): intensity 1 is a balanced studio light, 5 - 10 is clearly
             * stronger and only the extreme range (about 40 - 50 at close distance) reaches full strength. The curve is applied
             * to the brightest channel and the other channels follow by the same ratio, so the light keeps its hue.
             */
            float response = (float) (Math.log(1.0 + LIGHT_RESPONSE_GAIN * peak) / Math.log(1.0 + LIGHT_RESPONSE_GAIN * LIGHT_RESPONSE_MAX));
            float scale = Math.min(1F, response) / peak;


            ar *= scale;
            ag *= scale;
            ab *= scale;
        }

        float amb = AMBIENT_WITH_LIGHTS;

        return new float[] {amb + (1F - amb) * ar, amb + (1F - amb) * ag, amb + (1F - amb) * ab};
    }

    /** Where the actor really is: parents (with their overlays), the main transform, then its own transform overlay. */
    public Matrix4f getWorldMatrix(MotionLayer layer)
    {
        return this.getWorldMatrix(layer, true);
    }

    private Matrix4f getWorldMatrix(MotionLayer layer, boolean ownOverlay)
    {
        if (layer == null) return new Matrix4f();
        Matrix4f local = new Matrix4f();
        local.translate((float) layer.posX, (float) layer.posY, (float) layer.posZ);
        local.rotate(Matrices.toQuaternionZYXDegrees((float) layer.rotPitch, (float) layer.rotYaw, (float) layer.rotRoll));
        local.scale((float) layer.scaleX, (float) layer.scaleY, (float) layer.scaleZ);

        if (ownOverlay && layer.overlay != null)
        {
            layer.overlay.setupMatrix(local);
        }

        if (layer.parentId != null)
        {
            MotionLayer parent = this.findLayerById(layer.parentId);
            if (parent != null)
            {
                return new Matrix4f(this.getWorldMatrix(parent, true)).mul(local);
            }
        }
        return local;
    }

    /** The gizmo edits the main transform, so it sits where the main transform puts the actor (its own overlay left out). */
    public Vector3d getBaseWorldPosition(MotionLayer layer)
    {
        if (layer == null) return new Vector3d();
        Vector3f pos = this.getWorldMatrix(layer, false).getTranslation(new Vector3f());
        return new Vector3d(pos.x, pos.y, pos.z);
    }

    /* ── Camera Actors ── */

    /** The camera actor the viewport looks through right now, or null for the free studio camera. */
    public MotionLayer getViewCamera()
    {
        if (this.viewCameraId == null)
        {
            return null;
        }

        MotionLayer layer = this.findLayerById(this.viewCameraId);

        if (layer == null || !"Camera".equalsIgnoreCase(layer.type))
        {
            this.viewCameraId = null;

            return null;
        }

        return layer;
    }

    /** The camera the render uses: the one being looked through, else the flagged one, else the first visible camera. */
    public MotionLayer getRenderCamera()
    {
        MotionLayer viewed = this.getViewCamera();

        if (viewed != null)
        {
            return viewed;
        }

        MotionLayer first = null;

        for (MotionLayer layer : this.layers)
        {
            if (!"Camera".equalsIgnoreCase(layer.type) || !layer.visible)
            {
                continue;
            }

            if (layer.renderCamera)
            {
                return layer;
            }

            if (first == null)
            {
                first = layer;
            }
        }

        return first;
    }

    public void toggleCameraView(MotionLayer layer)
    {
        if (layer == null || !"Camera".equalsIgnoreCase(layer.type))
        {
            return;
        }

        this.viewCameraId = layer.id.equals(this.viewCameraId) ? null : layer.id;
        UIUtils.playClick();
        this.syncPropertiesFromSelected();
    }

    /** Viewport capsule button: leave the camera view, or enter the selected (else the render) camera. */
    public void toggleCameraViewQuick()
    {
        if (this.viewCameraId != null)
        {
            this.viewCameraId = null;
            UIUtils.playClick();
            this.syncPropertiesFromSelected();

            return;
        }

        MotionLayer target = this.getSelected();

        if (target == null || !"Camera".equalsIgnoreCase(target.type))
        {
            target = this.getRenderCamera();
        }

        if (target == null)
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_NO_CAMERA.get();

            return;
        }

        this.toggleCameraView(target);
    }

    public void setRenderCamera(MotionLayer layer, boolean value)
    {
        for (MotionLayer other : this.layers)
        {
            if ("Camera".equalsIgnoreCase(other.type))
            {
                other.renderCamera = false;
            }
        }

        layer.renderCamera = value;
    }

    public Vector3d getWorldPosition(MotionLayer layer)
    {
        if (layer == null) return new Vector3d();
        Matrix4f mat = this.getWorldMatrix(layer);
        Vector3f pos = mat.getTranslation(new Vector3f());
        return new Vector3d(pos.x, pos.y, pos.z);
    }

    public void rebuildLayersList()
    {
        if (this.layersScroll == null) return;
        double savedScroll = this.layersScroll.scroll.getScroll();
        this.layersScroll.removeAll();

        List<MotionLayer> roots = this.getRootLayers();
        for (MotionLayer root : roots)
        {
            this.buildTreeBranch(root, 0);
        }

        this.layersScroll.column(2).vertical().stretch().scroll();
        this.layersScroll.scroll.setScroll((int) savedScroll);
        this.layersScroll.scroll.clamp();
    }

    private void buildTreeBranch(MotionLayer layer, int indent)
    {
        UIElement row = this.createActorRow(layer, indent);
        this.layersScroll.add(row);

        List<MotionLayer> children = this.getChildLayers(layer);
        if (!children.isEmpty() && !layer.collapsed)
        {
            for (MotionLayer child : children)
            {
                this.buildTreeBranch(child, indent + 1);
            }
        }
    }

    private UIElement createActorRow(MotionLayer layer, int indent)
    {
        List<MotionLayer> children = this.getChildLayers(layer);
        boolean hasChildren = !children.isEmpty();
        int indentOffset = indent * 14;

        UIElement row = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                boolean isSelected = UIMMotionPanel.this.getSelected() == layer;
                boolean isHovered = a.isInside(context);

                int rowBg = isSelected ? 0xFF2563EB : (isHovered ? 0xFF22242C : 0xFF1B1C22);
                context.batcher.box(a.x, a.y, a.ex(), a.ey(), rowBg);

                if (isSelected)
                {
                    context.batcher.outline(a.x, a.y, a.ex(), a.ey(), 0xFF60A5FA, 1);
                }

                /* UE5 Hierarchy Connector Lines */
                if (indent > 0)
                {
                    int lineX = a.x + indentOffset - 7;
                    int midY = a.y + a.h / 2;
                    context.batcher.box(lineX, a.y, lineX + 1, midY, 0x5594A3B8);
                    context.batcher.box(lineX, midY, lineX + 6, midY + 1, 0x5594A3B8);
                }

                /* Color accent bar on the left */
                context.batcher.box(a.x, a.y, a.x + 3, a.ey(), layer.color);

                /* Layer Type Icon */
                int iconX = a.x + 6 + indentOffset + (hasChildren ? 14 : 0);
                context.batcher.icon(layer.icon, layer.visible ? Colors.WHITE : Colors.GRAY, iconX, a.y + (a.h - 16) / 2);

                /* Layer Name */
                FontRenderer font = context.batcher.getFont();
                int textColor = layer.visible ? Colors.WHITE : Colors.GRAY;
                int textX = iconX + 18;
                context.batcher.text(layer.name, textX, a.y + (a.h - font.getHeight()) / 2, textColor);

                super.render(context);
            }

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context))
                {
                    UIMMotionPanel.this.selectLayer(layer);
                    if (context.mouseButton == 1)
                    {
                        UIMMotionPanel.this.openActorContextMenu(layer);
                    }
                    UIUtils.playClick();
                    return true;
                }
                return super.subMouseClicked(context);
            }
        };
        row.markContainer().noCulling();
        row.w(1F).h(24);

        /* Chevron for collapsible parent */
        if (hasChildren)
        {
            UIIcon chevronBtn = new UIIcon(() -> layer.collapsed ? Icons.ARROW_RIGHT : Icons.ARROW_DOWN, (b) ->
            {
                layer.collapsed = !layer.collapsed;
                UIMMotionPanel.this.rebuildLayersList();
                UIUtils.playClick();
            });
            chevronBtn.noCulling();
            chevronBtn.relative(row).x(4 + indentOffset).y(4).wh(16, 16);
            row.add(chevronBtn);
        }

        /* Context Menu (...) Button */
        UIIcon menuBtn = new UIIcon(Icons.MORE, (b) ->
        {
            UIMMotionPanel.this.selectLayer(layer);
            UIMMotionPanel.this.openActorContextMenu(layer);
            UIUtils.playClick();
        });
        menuBtn.tooltip(UIKeys.MMOTION_ACTOR_OPTIONS);
        menuBtn.noCulling();
        menuBtn.relative(row).x(1F, -54).y(4).wh(16, 16);

        /* Visibility Toggle */
        UIIcon visBtn = new UIIcon(() -> layer.visible ? Icons.VISIBLE : Icons.INVISIBLE, (b) ->
        {
            layer.visible = !layer.visible;
            UIUtils.playClick();
        });
        visBtn.noCulling();
        visBtn.tooltip(UIKeys.MMOTION_TOGGLE_VISIBILITY);
        visBtn.relative(row).x(1F, -36).y(4).wh(16, 16);

        /* Lock Toggle */
        UIIcon lockBtn = new UIIcon(() -> layer.locked ? Icons.LOCKED : Icons.UNLOCKED, (b) ->
        {
            layer.locked = !layer.locked;
            UIUtils.playClick();
        });
        lockBtn.noCulling();
        lockBtn.tooltip(UIKeys.MMOTION_TOGGLE_LOCK);
        lockBtn.relative(row).x(1F, -18).y(4).wh(16, 16);

        row.add(menuBtn, visBtn, lockBtn);
        return row;
    }

    public void openActorContextMenu(MotionLayer layer)
    {
        ContextMenuManager manager = new ContextMenuManager();

        /* 1. Alt Aktör Ekle */
        manager.action(Icons.ADD, UIKeys.MMOTION_ADD_CHILD, () -> this.openAddActorMenuForParent(layer));

        /* 2. Başka Aktöre İliştir (Attach To) */
        manager.action(Icons.LINK, UIKeys.MMOTION_ATTACH, () -> this.openAttachContextMenu(layer));

        /* 3. Ebeveynden Ayır (Detach / Unparent) */
        if (layer.parentId != null)
        {
            manager.action(Icons.CLOSE, UIKeys.MMOTION_DETACH, () -> this.detachLayer(layer));
        }

        /* 4. Çoğalt (Duplicate) */
        manager.action(Icons.COPY, UIKeys.MMOTION_DUPLICATE, () -> this.duplicateLayer(layer));

        /* 5. Sil (Delete) */
        manager.action(Icons.REMOVE, UIKeys.MMOTION_DELETE_ACTOR, () -> this.removeLayer(layer));

        this.getContext().setContextMenu(manager.create());
    }

    public void openAttachContextMenu(MotionLayer layer)
    {
        ContextMenuManager manager = new ContextMenuManager();
        boolean hasTargets = false;

        for (MotionLayer other : this.layers)
        {
            if (other != layer && !this.isDescendant(layer, other))
            {
                hasTargets = true;
                manager.action(other.icon, IKey.raw("↳ " + other.name), () -> this.attachLayer(layer, other));
            }
        }

        if (!hasTargets)
        {
            manager.action(Icons.CLOSE, UIKeys.MMOTION_NO_ATTACH_TARGETS, () -> {});
        }

        this.getContext().setContextMenu(manager.create());
    }

    public boolean isDescendant(MotionLayer potentialAncestor, MotionLayer target)
    {
        if (potentialAncestor == null || target == null || target.parentId == null) return false;
        if (potentialAncestor.id.equals(target.parentId)) return true;
        MotionLayer parent = this.findLayerById(target.parentId);
        return this.isDescendant(potentialAncestor, parent);
    }

    public void attachLayer(MotionLayer child, MotionLayer newParent)
    {
        if (child == null || newParent == null || child == newParent) return;
        child.parentId = newParent.id;
        newParent.collapsed = false;
        this.rebuildLayersList();
        UIUtils.playClick();
    }

    public void detachLayer(MotionLayer layer)
    {
        if (layer == null) return;
        layer.parentId = null;
        this.rebuildLayersList();
        UIUtils.playClick();
    }

    public void duplicateLayer(MotionLayer layer)
    {
        if (layer == null) return;
        MotionLayer copy = layer.copy();
        copy.name = layer.name + " (Kopya)";
        this.layers.add(copy);
        this.selectLayer(copy);
        this.rebuildLayersList();
        this.rebuildKeyframeSheets();
        this.syncPropertiesFromSelected();
        UIUtils.playClick();
    }

    public void removeLayer(MotionLayer layer)
    {
        if (layer == null) return;
        /* Re-parent children to current layer's parent */
        for (MotionLayer child : this.getChildLayers(layer))
        {
            child.parentId = layer.parentId;
        }
        this.layers.remove(layer);
        if (this.selectedLayerIndex >= this.layers.size())
        {
            this.selectedLayerIndex = Math.max(0, this.layers.size() - 1);
        }
        this.selectedKeyframe = null;
        this.rebuildLayersList();
        this.rebuildKeyframeSheets();
        this.syncPropertiesFromSelected();
        UIUtils.playClick();
    }

    private void addLayer(String name, String type, Icon icon, int color)
    {
        MotionLayer layer = new MotionLayer(name + " " + (this.layers.size() + 1), type, icon, color);
        if (this.viewportRenderer != null)
        {
            this.viewportRenderer.resetGizmoState();
        }
        this.layers.add(layer);
        this.selectLayer(layer);
        this.rebuildLayersList();
        this.rebuildKeyframeSheets();
        this.syncPropertiesFromSelected();
        UIUtils.playClick();
    }

    public void updatePropertiesPanel()
    {
        this.syncPropertiesFromSelected();
    }

    /* Cancels a running transform gesture on the outgoing actor so the shared live transform never leaks into the next selection */
    private void releaseTransformEditing()
    {
        if (this.viewportRenderer != null)
        {
            this.viewportRenderer.resetGizmoState();
        }
        else if (this.transformEditor != null && this.transformEditor.isEditing())
        {
            this.transformEditor.getGesture().reject();
        }
    }

    /* Panel closed or hidden: stops playback, aborts a running render and frees viewport GPU resources */
    private void releasePanelResources()
    {
        this.isPlaying = false;

        RenderJob job = this.renderJob;

        if (job != null)
        {
            job.cancelled = true;
            this.finishRender(job);
        }

        if (this.viewportRenderer != null)
        {
            this.viewportRenderer.releaseResources();
        }
    }

    private void selectLayer(int index)
    {
        if (index != this.selectedLayerIndex)
        {
            this.releaseTransformEditing();
        }

        this.selectedLayerIndex = MathUtils.clamp(index, 0, Math.max(0, this.layers.size() - 1));
        this.selectedKeyframe = null;
        this.rebuildLayersList();
        this.updatePropertiesPanel();
    }

    public void selectLayer(MotionLayer layer)
    {
        int idx = this.layers.indexOf(layer);
        if (idx >= 0)
        {
            if (idx != this.selectedLayerIndex)
            {
                this.releaseTransformEditing();
            }

            this.selectedLayerIndex = idx;
            this.selectedKeyframe = null;
            this.rebuildLayersList();
            this.updatePropertiesPanel();
        }
    }

    /* ── Merkez Alan: Gerçek 3D Stüdyo Viewport (UIModelRenderer Mirası) ── */
    private void setupViewport()
    {
        this.viewportRenderer = new UIMMotionViewport();
        this.viewportArea = this.viewportRenderer;
        this.viewportArea.markContainer().noCulling();
    }

    /* ── Sağ Alan: Ayrıntılar (Transform & Properties) ── */
    /* ── Sağ Alan: Dinamik & Evrensel Özellikler (Properties Paneli) ── */
    private void setupProperties()
    {
        this.propertiesArea = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                int bg = BBSSettings.lightSurfaces() ? 0xDDEDEDED : 0xDD181818;
                int border = BBSSettings.lightSurfaces() ? 0x33000000 : 0x22FFFFFF;

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
                context.batcher.box(a.x, a.y, a.x + 1, a.ey(), border);

                /* Header */
                context.batcher.box(a.x, a.y, a.ex(), a.y + 24, BBSSettings.lightSurfaces() ? 0xFFE2E2E2 : 0xFF202020);
                context.batcher.box(a.x, a.y + 23, a.ex(), a.y + 24, border);

                FontRenderer font = context.batcher.getFont();
                context.batcher.icon(Icons.PROPERTIES, Colors.WHITE, a.x + 6, a.y + 4);
                context.batcher.text(UIKeys.MMOTION_PROPERTIES.get(), a.x + 24, a.y + (24 - font.getHeight()) / 2, Colors.WHITE);

                if (UIMMotionPanel.this.getSelected() == null && UIMMotionPanel.this.selectedKeyframe == null)
                {
                    Area contentArea = new Area(a.x + 10, a.y + 28, a.w - 20, a.h - 32);
                    EmptyStateRenderer.renderHint(context, contentArea, UIKeys.MMOTION_EMPTY_HINT);
                }

                super.render(context);
            }
        };
        this.propertiesArea.markContainer().noCulling();

        this.propertiesScroll = new UIScrollView();
        this.propertiesScroll.markContainer().noCulling();

        /* ── Durum 2: Keyframe Details Card ── */
        this.keyframeSection = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xFF1C2230);
                context.batcher.outline(a.x, a.y, a.ex(), a.ey(), 0xFF3B82F6, 1);
                super.render(context);
            }
        };
        this.keyframeSection.markContainer().noCulling();
        this.keyframeSection.h(26);

        this.keyframeTitleLabel = UI.label(UIKeys.MMOTION_KEYFRAME_TITLE).color(0xFF93C5FD);
        this.keyframeTitleLabel.relative(this.keyframeSection).xy(6, 5).w(75).h(16);

        this.keyframeTickInput = new UITrackpad((v) ->
        {
            if (this.selectedKeyframe != null)
            {
                this.selectedKeyframe.setTick((float) (double) v, true);
                if (this.keyframesView != null) this.keyframesView.triggerChange();
            }
        });
        this.keyframeTickInput.limit(0, 10000, true).tooltip(UIKeys.MMOTION_KEYFRAME_TICK);
        this.keyframeTickInput.relative(this.keyframeSection).xy(85, 4).w(55).h(18);

        this.keyframeInterpBtn = new UIIcon(Icons.GRAPH, (b) ->
        {
            if (this.selectedKeyframe != null)
            {
                Interpolation interp = this.selectedKeyframe.getInterpolation();
                UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interp);
                this.getContext().replaceContextMenu(menu.callback(() ->
                {
                    this.selectedKeyframe.getInterpolation().copy(interp);
                    if (this.keyframesView != null) this.keyframesView.triggerChange();
                    this.syncPropertiesFromSelected();
                    UIUtils.playClick();
                }));
            }
        });
        this.keyframeInterpBtn.tooltip(UIKeys.MMOTION_INTERP_MENU);
        this.keyframeInterpBtn.relative(this.keyframeSection).x(1F, -44).y(4).wh(18, 18);

        UIIcon removeKfBtn = new UIIcon(Icons.REMOVE, (b) ->
        {
            if (this.selectedKeyframe != null)
            {
                this.removeKeyframe(this.selectedKeyframe);
                this.selectedKeyframe = null;
                UIUtils.playClick();
            }
            else if (this.keyframesView != null)
            {
                this.keyframesView.getGraph().removeSelected();
                this.syncPropertiesFromSelected();
                UIUtils.playClick();
            }
        });
        removeKfBtn.tooltip(UIKeys.MMOTION_REMOVE_KEYFRAME);
        removeKfBtn.relative(this.keyframeSection).x(1F, -22).y(4).wh(18, 18);

        this.keyframeChannelLabel = UI.label(UIKeys.MMOTION_KEYFRAME_CHANNEL.format(UIKeys.MMOTION_CHANNEL_TRANSFORM)).color(0xFF94A3B8);
        this.keyframeChannelLabel.relative(this.keyframeSection).xy(145, 5).w(90).h(16);

        this.keyframeSection.add(this.keyframeTitleLabel, this.keyframeTickInput, this.keyframeInterpBtn, removeKfBtn, this.keyframeChannelLabel);

        /* ── Durum 1: Actor Overview Card (Düzenlenebilir İsim ve Tür Rozeti) ── */
        this.actorNameInput = new UITextbox(120, (text) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null && !text.trim().isEmpty())
            {
                layer.name = text.trim();
                this.rebuildLayersList();
            }
        });
        this.actorNameInput.placeholder(UIKeys.MMOTION_ACTOR_NAME);

        this.actorCard = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                MotionLayer layer = UIMMotionPanel.this.getSelected();
                if (layer == null) return;

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xFF1C2230);
                context.batcher.outline(a.x, a.y, a.ex(), a.ey(), 0xFF3B82F6, 1);
                context.batcher.box(a.x, a.y, a.x + 3, a.ey(), layer.color);

                /* Layer icon */
                context.batcher.icon(layer.icon, Colors.WHITE, a.x + 8, a.y + 6);

                /* Type Badge Pill */
                String typeStr = layer.type != null ? layer.type.toUpperCase() : "ACTOR";
                FontRenderer font = context.batcher.getFont();
                int badgeW = font.getWidth(typeStr) + 10;
                int badgeX = a.ex() - badgeW - 6;
                int badgeY = a.y + 6;
                context.batcher.box(badgeX, badgeY, badgeX + badgeW, badgeY + 16, 0xFF2563EB);
                context.batcher.outline(badgeX, badgeY, badgeX + badgeW, badgeY + 16, 0xFF60A5FA, 1);
                context.batcher.text(typeStr, badgeX + 5, badgeY + (16 - font.getHeight()) / 2, Colors.WHITE);

                /* Hierarchy sub-label */
                MotionLayer parent = UIMMotionPanel.this.findLayerById(layer.parentId);
                String parentStr = parent != null ? UIKeys.MMOTION_PARENT.format(parent.name).get() : UIKeys.MMOTION_HIERARCHY_ROOT.get();
                context.batcher.text(parentStr, a.x + 28, a.y + 28, 0xFF94A3B8);

                super.render(context);
            }
        };
        this.actorCard.h(44);
        this.actorCard.markContainer().noCulling();
        this.actorNameInput.relative(this.actorCard).x(28).y(5).w(1F, -85).h(18);
        this.actorCard.add(this.actorNameInput);

        /* ── Transform Section ── */
        this.posXInput = new UITrackpad((v) -> this.applyTrackpadValue("posX", v));
        this.posXInput.tooltip(UIKeys.MMOTION_POS_X);
        this.posYInput = new UITrackpad((v) -> this.applyTrackpadValue("posY", v));
        this.posYInput.tooltip(UIKeys.MMOTION_POS_Y);
        this.posZInput = new UITrackpad((v) -> this.applyTrackpadValue("posZ", v));
        this.posZInput.tooltip(UIKeys.MMOTION_POS_Z);

        this.rotPitchInput = new UITrackpad((v) -> this.applyTrackpadValue("rotPitch", v));
        this.rotPitchInput.tooltip(UIKeys.MMOTION_ROT_PITCH);
        this.rotYawInput = new UITrackpad((v) -> this.applyTrackpadValue("rotYaw", v));
        this.rotYawInput.tooltip(UIKeys.MMOTION_ROT_YAW);
        this.rotRollInput = new UITrackpad((v) -> this.applyTrackpadValue("rotRoll", v));
        this.rotRollInput.tooltip(UIKeys.MMOTION_ROT_ROLL);

        this.scaleXInput = new UITrackpad((v) -> this.applyTrackpadValue("scaleX", v));
        this.scaleXInput.tooltip(UIKeys.MMOTION_SCALE_X);
        this.scaleYInput = new UITrackpad((v) -> this.applyTrackpadValue("scaleY", v));
        this.scaleYInput.tooltip(UIKeys.MMOTION_SCALE_Y);
        this.scaleZInput = new UITrackpad((v) -> this.applyTrackpadValue("scaleZ", v));
        this.scaleZInput.tooltip(UIKeys.MMOTION_SCALE_Z);

        this.lockScaleBtn = new UIIcon(() -> this.lockScale ? Icons.LOCKED : Icons.UNLOCKED, (b) ->
        {
            this.lockScale = !this.lockScale;
            this.lockScaleBtn.tooltip(this.lockScale ? UIKeys.MMOTION_SCALE_LOCKED : UIKeys.MMOTION_SCALE_UNLOCKED);
            UIUtils.playClick();
        });
        this.lockScaleBtn.tooltip(UIKeys.MMOTION_SCALE_LOCK);
        this.lockScaleBtn.wh(16, 16);

        UIElement scaleHeaderRow = UI.row(4, UI.label(UIKeys.MMOTION_SCALE_HEADER), this.lockScaleBtn);

        this.visibilityToggle = new UIToggle(UIKeys.MMOTION_VISIBILITY, true, (b) ->
        {
            this.applyTrackpadValue("visible", b.getValue() ? 1.0 : 0.0);
        });

        this.actorColorPicker = new UIColor((c) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                /* Actor colour / accent only: never touches the Coloring keyframes */
                layer.color = c;
            }
        });

        this.opacityInput = new UITrackpad((v) -> this.applyTrackpadValue("opacity", MathUtils.clamp(v, 0.0, 1.0)));
        this.opacityInput.limit(0.0, 1.0);
        this.opacityInput.values(0.05, 0.01, 0.1);

        this.shadingToggle = new UIToggle(UIKeys.MMOTION_STUDIO_SHADING, true, (b) ->
        {
            if (this.getSelected() != null) this.getSelected().shading = b.getValue();
        });

        /* ── Actor Specific Sections ── */

        /* 1. Block Section */
        this.blockSection = UI.column(4);
        this.blockStateEditor = new UIBlockStateEditor((state) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                layer.blockState = state;
                if (layer.form instanceof BlockForm bf) bf.blockState.set(state);
                UIUtils.playClick();
            }
        });
        this.blockLightInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.blockLight = (int) Math.round(v);
        });
        this.blockLightInput.limit(0, 15, true).tooltip(UIKeys.MMOTION_BLOCK_LIGHT_TOOLTIP);
        this.blockSection.add(
            UI.label(UIKeys.MMOTION_BLOCK_PICKER),
            this.blockStateEditor,
            UI.label(UIKeys.MMOTION_BLOCK_LIGHT),
            this.blockLightInput
        );

        /* 2. Item Section */
        this.itemSection = UI.column(4);
        this.itemStackEditor = new UIItemStack((stack) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                layer.itemStack = stack.copy();
                if (layer.form instanceof ItemForm iform) iform.stack.set(stack.copy());
                UIUtils.playClick();
            }
        });
        this.itemCountInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.itemCount = (int) Math.round(v);
        });
        this.itemCountInput.limit(1, 64, true).tooltip(UIKeys.MMOTION_ITEM_COUNT_TOOLTIP);

        this.itemGlintToggle = new UIToggle(UIKeys.MMOTION_ITEM_GLINT, false, (b) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.itemGlint = b.getValue();
        });

        this.itemTransformModeBtn = new UIButton(UIKeys.MMOTION_ITEM_MODE.format("fixed"), (b) ->
        {
            this.openItemModeContextMenu(this.getSelected());
        });

        this.itemSection.add(
            UI.label(UIKeys.MMOTION_ITEM_PICKER),
            this.itemStackEditor,
            UI.row(2, UI.label(UIKeys.MMOTION_ITEM_COUNT), this.itemCountInput),
            this.itemGlintToggle,
            UI.row(2, UI.label(UIKeys.MMOTION_ITEM_DISPLAY), this.itemTransformModeBtn)
        );

        /* 3. Entity Section */
        this.entitySection = UI.column(4);
        this.chooseModelBtn = new UIButton(UIKeys.MMOTION_PICK_MODEL, (b) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                this.openMorphPicker(layer);
            }
        });
        this.entitySection.add(
            UI.label(UIKeys.MMOTION_ENTITY_MODEL),
            this.chooseModelBtn
        );

        /* 4. Structure Section */
        this.structureSection = UI.column(4);
        this.structureNameInput = new UITextbox(120, (text) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                layer.structureName = text;
                if (layer.form instanceof StructureForm sf) sf.structure.set(text);
            }
        });
        this.structureNameInput.placeholder(UIKeys.MMOTION_STRUCTURE_NAME_PLACEHOLDER);

        this.pickStructureBtn = new UIButton(UIKeys.MMOTION_PICK_STRUCTURE_BUTTON, (b) ->
        {
            this.openStructurePicker();
        });

        this.openStructureFolderBtn = new UIButton(UIKeys.MMOTION_OPEN_FOLDER, (b) ->
        {
            File folder = StructureManager.getAssetsFolder();
            if (folder != null) UIUtils.openFolder(folder);
        });

        this.centerStructureBtn = new UIButton(UIKeys.MMOTION_CENTER_BOUNDS, (b) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                this.centerStructureLayer(layer);
                this.syncPropertiesFromSelected();
                UIUtils.playClick();
            }
        });
        this.structureSection.add(
            UI.label(UIKeys.MMOTION_STRUCTURE_NAME),
            this.structureNameInput,
            UI.row(2, this.pickStructureBtn, this.openStructureFolderBtn),
            this.centerStructureBtn
        );

        /* 5. Light Section */
        this.lightSection = UI.column(4);
        this.lightTypeLabel = this.sectionTitle(IKey.EMPTY);
        this.lightColorPicker = new UIColor((c) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null)
            {
                layer.lightColor = c;
                layer.color = c;
                this.writeLightColorKeyframe(layer);
            }
        }).withAlpha();
        this.lightIntensityInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) { layer.lightIntensity = v; this.writeLightChannel(layer.channelIntensity, v); }
        });
        this.lightIntensityInput.limit(0.0, 50.0).tooltip(UIKeys.MMOTION_LIGHT_INTENSITY);
        this.lightSection.add(
            this.lightTypeLabel,
            UI.label(UIKeys.MMOTION_LIGHT_COLOR),
            this.lightColorPicker,
            UI.label(UIKeys.MMOTION_LIGHT_INTENSITY_LABEL),
            this.lightIntensityInput
        );

        /* 5b. Scene light extras (Point / Spot / Rect) */
        this.lightRangeInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) { layer.lightRange = v; this.writeLightChannel(layer.channelRange, v); }
        });
        this.lightRangeInput.limit(0.1, 128.0).tooltip(UIKeys.MMOTION_LIGHT_RANGE_TOOLTIP);
        this.lightConeInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) { layer.lightCone = v; this.writeLightChannel(layer.channelCone, v); }
        });
        this.lightConeInput.limit(5.0, 120.0).tooltip(UIKeys.MMOTION_LIGHT_CONE);
        this.lightPenumbraInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.lightPenumbra = v;
        });
        this.lightPenumbraInput.limit(0.0, 1.0).tooltip(UIKeys.MMOTION_LIGHT_PENUMBRA_TOOLTIP);
        this.lightWidthInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.lightWidth = v;
        });
        this.lightWidthInput.limit(0.1, 32.0).tooltip(UIKeys.MMOTION_LIGHT_WIDTH);
        this.lightHeightInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.lightHeight = v;
        });
        this.lightHeightInput.limit(0.1, 32.0).tooltip(UIKeys.MMOTION_LIGHT_HEIGHT);

        this.lightRangeGroup = UI.column(4);
        this.lightRangeGroup.add(UI.label(UIKeys.MMOTION_LIGHT_RANGE_LABEL), this.lightRangeInput);
        this.lightSpotGroup = UI.column(4);
        this.lightSpotGroup.add(UI.label(UIKeys.MMOTION_LIGHT_CONE_LABEL), this.lightConeInput, UI.label(UIKeys.MMOTION_LIGHT_PENUMBRA_LABEL), this.lightPenumbraInput);
        this.lightRectGroup = UI.column(4);
        this.lightRectGroup.add(UI.label(UIKeys.MMOTION_LIGHT_WIDTH_LABEL), this.lightWidthInput, UI.label(UIKeys.MMOTION_LIGHT_HEIGHT_LABEL), this.lightHeightInput);
        this.lightGlowInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.lightGlow = v;
        });
        this.lightGlowInput.limit(0.0, 1.0).tooltip(UIKeys.MMOTION_LIGHT_GLOW_TOOLTIP);
        this.lightGlowGroup = UI.column(4);
        this.lightGlowGroup.add(UI.label(UIKeys.MMOTION_LIGHT_GLOW_LABEL), this.lightGlowInput);
        this.lightSection.add(this.lightRangeGroup, this.lightSpotGroup, this.lightRectGroup, this.lightGlowGroup);

        /* 6. Camera Section */
        this.cameraSection = UI.column(4);
        this.cameraFovInput = new UITrackpad((v) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) layer.cameraFov = MathUtils.clamp(v, 10.0, 150.0);
        });
        this.cameraFovInput.limit(10.0, 150.0).tooltip(UIKeys.MMOTION_CAMERA_FOV_TOOLTIP);
        this.cameraViewBtn = new UIButton(UIKeys.MMOTION_CAMERA_ENTER, (b) -> this.toggleCameraView(this.getSelected()));
        this.cameraRenderToggle = new UIToggle(UIKeys.MMOTION_RENDER_CAMERA, false, (b) ->
        {
            MotionLayer layer = this.getSelected();
            if (layer != null) this.setRenderCamera(layer, b.getValue());
        });
        this.cameraSection.add(
            this.sectionTitle(UIKeys.MMOTION_CAMERA_SETTINGS),
            UI.label(UIKeys.MMOTION_CAMERA_FOV),
            this.cameraFovInput,
            this.cameraViewBtn,
            this.cameraRenderToggle
        );

        this.transformEditor = new UIPropTransform();
        this.transformEditor.enableHotkeys();
        this.transformEditor.hotkeyDrag(() -> this.viewportRenderer.buildGizmoDrag());
        this.transformEditor.callbacks(
            () -> {
                MotionLayer l = this.getSelected();
                if (l != null)
                {
                    this.syncLayerFromLiveTransform(l);
                    this.writeTransformToKeyframe(l);
                }
            },
            () -> {
                MotionLayer l = this.getSelected();
                if (l != null)
                {
                    this.syncLayerFromLiveTransform(l);
                    this.writeTransformToKeyframe(l);
                }
            },
            () -> {
                MotionLayer l = this.getSelected();
                if (l != null)
                {
                    this.syncLayerFromLiveTransform(l);
                    this.writeTransformToKeyframe(l);
                    this.syncPropertiesFromSelected();
                }
            }
        );

        this.emptySelectionPlaceholder = new UILabel(UIKeys.MMOTION_EMPTY_HINT)
            .color(0x88FFFFFF)
            .labelAnchor(0.5F, 0.5F);
        this.emptySelectionPlaceholder.h(60);

        this.actorColorLabel = UI.label(UIKeys.MMOTION_ACTOR_COLOR);
        this.opacityLabel = UI.label(UIKeys.MMOTION_OPACITY_TOOLTIP);

        /* Assemble all sections in Properties scroll view */
        this.propertiesScroll.add(
            this.emptySelectionPlaceholder,
            this.actorCard,
            this.keyframeSection,
            this.visibilityToggle,
            this.transformEditor,
            this.blockSection,
            this.itemSection,
            this.entitySection,
            this.structureSection,
            this.lightSection,
            this.cameraSection,
            this.actorColorLabel,
            this.actorColorPicker,
            this.opacityLabel,
            this.opacityInput,
            this.shadingToggle
        );

        this.attachActorResets();

        this.propertiesScroll.column(4).vertical().stretch().scroll();
        this.propertiesScroll.relative(this.propertiesArea).x(4).y(28).w(1F, -8).h(1F, -32);
        this.propertiesArea.add(this.propertiesScroll);

        this.syncPropertiesFromSelected();
    }

    private void openItemModeContextMenu(MotionLayer layer)
    {
        if (layer == null) return;
        ContextMenuManager manager = new ContextMenuManager();
        for (ModelTransformationMode mode : ModelTransformationMode.values())
        {
            manager.action(Icons.MATERIAL, IKey.raw(mode.asString()), () ->
            {
                layer.itemTransformMode = mode;
                this.syncPropertiesFromSelected();
                UIUtils.playClick();
            });
        }
        this.getContext().setContextMenu(manager.create());
    }

    private void applyTrackpadValue(String property, double v)
    {
        MotionLayer layer = this.getSelected();
        if (layer == null) return;

        double oldX = layer.scaleX;
        double oldY = layer.scaleY;
        double oldZ = layer.scaleZ;

        switch (property)
        {
            case "posX" -> layer.posX = v;
            case "posY" -> layer.posY = v;
            case "posZ" -> layer.posZ = v;
            case "rotPitch" -> layer.rotPitch = v;
            case "rotYaw" -> layer.rotYaw = v;
            case "rotRoll" -> layer.rotRoll = v;
            case "scaleX" -> {
                layer.scaleX = v;
                if (this.lockScale && oldX != 0)
                {
                    double ratio = v / oldX;
                    layer.scaleY = oldY * ratio;
                    layer.scaleZ = oldZ * ratio;
                    if (this.scaleYInput != null) this.scaleYInput.setValue(layer.scaleY);
                    if (this.scaleZInput != null) this.scaleZInput.setValue(layer.scaleZ);
                }
            }
            case "scaleY" -> {
                layer.scaleY = v;
                if (this.lockScale && oldY != 0)
                {
                    double ratio = v / oldY;
                    layer.scaleX = oldX * ratio;
                    layer.scaleZ = oldZ * ratio;
                    if (this.scaleXInput != null) this.scaleXInput.setValue(layer.scaleX);
                    if (this.scaleZInput != null) this.scaleZInput.setValue(layer.scaleZ);
                }
            }
            case "scaleZ" -> {
                layer.scaleZ = v;
                if (this.lockScale && oldZ != 0)
                {
                    double ratio = v / oldZ;
                    layer.scaleX = oldX * ratio;
                    layer.scaleY = oldY * ratio;
                    if (this.scaleXInput != null) this.scaleXInput.setValue(layer.scaleX);
                    if (this.scaleYInput != null) this.scaleYInput.setValue(layer.scaleY);
                }
            }
            case "opacity" -> layer.opacity = v;
            case "visible" -> layer.visible = v > 0.5;
            case "pose" -> layer.entityPose = v;
        }

        /* Two-way data-binding: update keyframe (only a keyframe owned by the edited actor) */
        if (this.selectedKeyframe != null && this.keyframeBelongsTo(layer, this.selectedKeyframe))
        {
            /* Only a main transform keyframe takes the live transform: an overlay keyframe is edited in its own section */
            if (kfIn(layer.channelTransform, this.selectedKeyframe))
            {
                this.writeTransformToKeyframe(layer);
            }
            else if (this.selectedKeyframe.getValue() instanceof Color selectedColor)
            {
                if ("opacity".equals(property))
                {
                    Color next = selectedColor.copy();

                    next.a = (float) MathUtils.clamp(v, 0.0, 1.0);
                    this.selectedKeyframe.setValue(next, true);

                    if (this.keyframesView != null)
                    {
                        this.keyframesView.triggerChange();
                    }
                }
            }
            else if (this.selectedKeyframe.getValue() instanceof Double)
            {
                this.selectedKeyframe.setValue(v, true);
                if (this.keyframesView != null)
                {
                    this.keyframesView.triggerChange();
                }
            }
        }
        else
        {
            /* If no keyframe explicitly selected, update existing keyframe at currentFrame if present */
            if ("visible".equals(property))
            {
                for (Keyframe<Double> kf : layer.channelVisible.getKeyframes())
                {
                    if ((int) Math.round(kf.getTick()) == this.currentFrame)
                    {
                        kf.setValue(v, true);
                        if (this.keyframesView != null) this.keyframesView.triggerChange();
                        break;
                    }
                }
            }
            else if ("opacity".equals(property))
            {
                for (Keyframe<Color> kf : layer.channelColor.getKeyframes())
                {
                    if ((int) Math.round(kf.getTick()) == this.currentFrame)
                    {
                        Color tinted = kf.getValue().copy();
                        tinted.a = (float) v;
                        kf.setValue(tinted, true);
                        if (this.keyframesView != null) this.keyframesView.triggerChange();
                        break;
                    }
                }
            }
            else if ("pose".equals(property))
            {
                for (Keyframe<Double> kf : layer.channelPose.getKeyframes())
                {
                    if ((int) Math.round(kf.getTick()) == this.currentFrame)
                    {
                        kf.setValue(v, true);
                        if (this.keyframesView != null) this.keyframesView.triggerChange();
                        break;
                    }
                }
            }
            else
            {
                this.writeTransformToKeyframe(layer);
            }
        }
    }

    private static boolean containsRef(List<?> list, Object value)
    {
        for (Object o : list)
        {
            if (o == value) return true;
        }

        return false;
    }

    /* True when the keyframe lives in one of the actor's own channels (identity check) */
    private boolean keyframeBelongsTo(MotionLayer layer, Keyframe<?> keyframe)
    {
        if (layer == null || keyframe == null) return false;

        return containsRef(layer.channelTransform.getKeyframes(), keyframe)
            || containsRef(layer.channelTransformOverlay.getKeyframes(), keyframe)
            || containsRef(layer.channelColor.getKeyframes(), keyframe)
            || containsRef(layer.channelVisible.getKeyframes(), keyframe)
            || containsRef(layer.channelPose.getKeyframes(), keyframe)
            || containsRef(layer.channelIntensity.getKeyframes(), keyframe)
            || containsRef(layer.channelRange.getKeyframes(), keyframe)
            || containsRef(layer.channelCone.getKeyframes(), keyframe);
    }

    public void writeTransformToKeyframe(MotionLayer layer)
    {
        if (layer == null) return;
        Transform t = null;
        if (this.selectedKeyframe != null && kfIn(layer.channelTransform, this.selectedKeyframe) && this.selectedKeyframe.getValue() instanceof Transform st)
        {
            t = st;
        }
        else
        {
            for (Keyframe<Transform> kf : layer.channelTransform.getKeyframes())
            {
                if ((int) Math.round(kf.getTick()) == this.currentFrame)
                {
                    t = kf.getValue();
                    break;
                }
            }
            if (t == null)
            {
                t = new Transform();
                layer.channelTransform.insert(this.currentFrame, t);
            }
        }

        if (t != null)
        {
            if (this.transformEditor != null && this.transformEditor.isEditing())
            {
                t.copy(this.liveTransform);
            }
            else
            {
                this.syncLiveTransformFromLayer(layer);
                t.copy(this.liveTransform);
            }

            if (this.keyframesView != null)
            {
                this.keyframesView.triggerChange();
            }
        }
    }

    private MotionLayer getSelected()
    {
        if (this.selectedLayerIndex >= 0 && this.selectedLayerIndex < this.layers.size())
        {
            return this.layers.get(this.selectedLayerIndex);
        }
        return null;
    }

    /* â”€â”€ Properties: keyframe-driven panel (BBS keyframe editor embedded) & per-input Reset â”€â”€ */

    private void destroyKeyframePanel()
    {
        if (this.keyframePanel != null)
        {
            this.keyframePanel.removeFromParent();
            this.keyframePanel = null;
        }

        this.keyframePanelFor = null;
        this.keyframeRefreshers.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void syncKeyframePanel(MotionLayer layer)
    {
        Keyframe keyframe = this.selectedKeyframe;

        if (keyframe == null || layer == null || this.keyframesView == null || this.propertiesArea == null)
        {
            this.destroyKeyframePanel();

            return;
        }

        if (this.keyframePanel == null || this.keyframePanelFor != keyframe)
        {
            this.destroyKeyframePanel();

            UIKeyframeFactory panel = UIKeyframeFactory.createPanel(keyframe, this.keyframesView);

            if (panel == null)
            {
                return;
            }

            this.addKeyframeSections(panel, layer, keyframe);
            panel.relative(this.propertiesArea).x(4).y(28).w(1F, -8).h(1F, -32);
            this.propertiesArea.add(panel);
            panel.resize();

            this.keyframePanel = panel;
            this.keyframePanelFor = keyframe;
        }
        else
        {
            this.keyframePanel.update();

            for (Runnable refresher : new ArrayList<>(this.keyframeRefreshers))
            {
                refresher.run();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addKeyframeSections(UIKeyframeFactory panel, MotionLayer layer, Keyframe keyframe)
    {
        this.keyframeRefreshers.clear();

        /* Keep only the BBS header row (Graph, Tick, Forced Duration, Motion Shift): the value editors are rebuilt per channel below */
        List<Object> kids = new ArrayList<>(panel.scroll.getChildren());

        for (int i = 1; i < kids.size(); i++)
        {
            if (kids.get(i) instanceof UIElement element)
            {
                panel.scroll.remove(element);
            }
        }

        if (!kids.isEmpty() && kids.get(0) instanceof UIElement header)
        {
            header.addAfter(panel.tick, this.createResetIcon(() -> panel.tick.setValueAndNotify(0D)));
            header.addAfter(panel.duration, this.createResetIcon(() -> panel.duration.setValueAndNotify(0D)));
            header.addAfter(panel.motionShift, this.createResetIcon(() -> panel.motionShift.setValueAndNotify(0D)));
        }

        float tick = keyframe.getTick();
        boolean isTransform = kfIn(layer.channelTransform, keyframe);
        boolean isOverlay = kfIn(layer.channelTransformOverlay, keyframe);
        boolean isColor = kfIn(layer.channelColor, keyframe);
        boolean isVisible = kfIn(layer.channelVisible, keyframe);
        boolean isLightValue = kfIn(layer.channelIntensity, keyframe) || kfIn(layer.channelRange, keyframe) || kfIn(layer.channelCone, keyframe);

        if (isLightValue)
        {
            this.addLightValueSection(panel, layer, keyframe);
        }

        if (isVisible)
        {
            /* Visibility keyframes are always Hold */
            panel.interp.setEnabled(false);
            panel.interp.tooltip(UIKeys.MMOTION_VISIBILITY_HOLD);
        }

        if (isTransform)
        {
            this.addTransformSection(panel, layer.channelTransform, UIKeys.MMOTION_CHANNEL_TRANSFORM, UIKeys.MMOTION_NO_TRANSFORM_KEYFRAME, tick);
        }

        /* Overlay keyframe: only its own translate / rotate / scale, on top of the main transform */
        if (isOverlay)
        {
            this.addTransformSection(panel, layer.channelTransformOverlay, UIKeys.MMOTION_CHANNEL_TRANSFORM_OVERLAY, UIKeys.MMOTION_NO_OVERLAY_KEYFRAME, tick);
        }

        if (isTransform || isColor)
        {
            this.addColorSection(panel, layer, tick);
        }

        if (isTransform || isVisible)
        {
            this.addVisibilitySection(panel, layer, tick);
        }

        UIButton remove = new UIButton(UIKeys.MMOTION_REMOVE_KEYFRAME, (b) ->
        {
            Keyframe target = this.selectedKeyframe;

            if (target != null)
            {
                this.selectedKeyframe = null;
                this.removeKeyframe(target);
                UIUtils.playClick();
            }
        });

        panel.scroll.add(remove);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addLightValueSection(UIKeyframeFactory panel, MotionLayer layer, Keyframe keyframe)
    {
        IKey title;
        double min;
        double max;
        double reset;

        if (kfIn(layer.channelIntensity, keyframe))
        {
            title = UIKeys.MMOTION_LIGHT_INTENSITY;
            min = 0.0;
            max = 50.0;
            reset = 1.0;
        }
        else if (kfIn(layer.channelRange, keyframe))
        {
            title = UIKeys.MMOTION_CHANNEL_RANGE;
            min = 0.1;
            max = 128.0;
            reset = 8.0;
        }
        else
        {
            title = UIKeys.MMOTION_LIGHT_CONE;
            min = 5.0;
            max = 120.0;
            reset = 45.0;
        }

        panel.scroll.add(this.sectionTitle(title));

        UITrackpad input = new UITrackpad((v) -> this.writeDoubleKeyframe(keyframe, v));

        input.limit(min, max);
        input.setValue(this.readDoubleKeyframe(keyframe));
        input.tooltip(title);
        panel.scroll.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, input, this.createResetIcon(() -> input.setValueAndNotify(reset))));

        this.keyframeRefreshers.add(() ->
        {
            if (!input.isDragging() && !input.textbox.isFocused())
            {
                input.setValue(this.readDoubleKeyframe(keyframe));
            }
        });
    }

    private UILabel sectionTitle(IKey title)
    {
        return UI.label(title).color(0xFF93C5FD);
    }

    private <T> Keyframe<T> findKeyframeAt(KeyframeChannel<T> channel, float tick)
    {
        for (Keyframe<T> candidate : channel.getKeyframes())
        {
            if (Math.abs(candidate.getTick() - tick) < 0.01F)
            {
                return candidate;
            }
        }

        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeDoubleKeyframe(Keyframe keyframe, double value)
    {
        keyframe.preNotify();
        keyframe.setValue(value);
        keyframe.postNotify();

        if (this.keyframesView != null)
        {
            this.keyframesView.triggerChange();
        }
    }

    private double readDoubleKeyframe(Keyframe keyframe)
    {
        return keyframe.getValue() instanceof Number number ? number.doubleValue() : 0D;
    }

    /** Transform editor whose Translate / Scale / Rotate rows each carry their own Reset icon. */
    private class UIMMotionTransform extends UIPropTransform
    {
        private final UIIcon resetT = UIMMotionPanel.this.createResetIcon(() -> this.fillSetT(0, 0, 0));
        private final UIIcon resetS = UIMMotionPanel.this.createResetIcon(() -> this.fillSetS(1, 1, 1));
        private final UIIcon resetR = UIMMotionPanel.this.createResetIcon(() -> this.fillSetR(0, 0, 0));

        @Override
        public void render(UIContext context)
        {
            this.ensureReset(this.tx.getParent(), this.resetT);
            this.ensureReset(this.scaleRow, this.resetS);
            this.ensureReset(this.rotateRow, this.resetR);

            super.render(context);
        }

        /* The scale / rotate rows are rebuilt by the uniform-scale and rotation-storage toggles, so the icon is put back whenever it was dropped */
        private void ensureReset(UIElement row, UIIcon icon)
        {
            if (row != null && icon.getParent() != row)
            {
                row.add(icon);

                UIElement container = this.getParentContainer();

                if (container != null)
                {
                    container.resize();
                }
                else
                {
                    this.resize();
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /** Translate / rotate / scale editor bound to the keyframe of a transform channel (main or overlay) at the tick. */
    private void addTransformSection(UIKeyframeFactory panel, KeyframeChannel<Transform> channel, IKey title, IKey missing, float tick)
    {
        panel.scroll.add(this.sectionTitle(title));

        Keyframe keyframe = this.findKeyframeAt(channel, tick);

        if (keyframe == null || !(keyframe.getValue() instanceof Transform))
        {
            panel.scroll.add(UI.label(missing).color(0xFF94A3B8));

            return;
        }

        UIMMotionTransform editor = new UIMMotionTransform();

        editor.callbacks(
            () -> keyframe.preNotify(),
            () ->
            {
                keyframe.postNotify();

                if (this.keyframesView != null)
                {
                    this.keyframesView.triggerChange();
                }
            }
        );
        editor.setTransform((Transform) keyframe.getValue());
        panel.scroll.add(editor);

        this.keyframeRefreshers.add(() ->
        {
            if (!editor.isUserEditing() && keyframe.getValue() instanceof Transform transform)
            {
                editor.setTransform(transform);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeColorKeyframe(Keyframe keyframe, Color value)
    {
        keyframe.preNotify();
        keyframe.setValue(value);
        keyframe.postNotify();

        if (this.keyframesView != null)
        {
            this.keyframesView.triggerChange();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addColorSection(UIKeyframeFactory panel, MotionLayer layer, float tick)
    {
        panel.scroll.add(this.sectionTitle(UIKeys.MMOTION_CHANNEL_COLORING));

        Keyframe keyframe = this.findKeyframeAt(layer.channelColor, tick);

        if (keyframe == null || !(keyframe.getValue() instanceof Color))
        {
            panel.scroll.add(UI.label(UIKeys.MMOTION_NO_COLORING_KEYFRAME).color(0xFF94A3B8));

            return;
        }

        /* Color overlay: tints the actor's own colours (block, item, entity ...); independent from the actor accent colour */
        UIColor overlay = new UIColor((rgb) ->
        {
            Color next = ((Color) keyframe.getValue()).copy();

            next.set(rgb, false);
            this.writeColorKeyframe(keyframe, next);
        });

        overlay.setColor(Colors.A100 | ((Color) keyframe.getValue()).getRGBColor());
        overlay.tooltip(UIKeys.MMOTION_COLOR_OVERLAY);
        panel.scroll.add(UI.label(UIKeys.MMOTION_COLOR_OVERLAY));
        panel.scroll.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, overlay, this.createResetIcon(() ->
        {
            Color next = ((Color) keyframe.getValue()).copy();

            next.set(1F, 1F, 1F);
            this.writeColorKeyframe(keyframe, next);
            overlay.setColor(0xFFFFFFFF);
        })));

        UITrackpad opacity = new UITrackpad((v) ->
        {
            Color next = ((Color) keyframe.getValue()).copy();

            next.a = (float) MathUtils.clamp(v, 0.0, 1.0);
            this.writeColorKeyframe(keyframe, next);
        });

        opacity.limit(0.0, 1.0);
        opacity.values(0.05, 0.01, 0.1);
        opacity.setValue(((Color) keyframe.getValue()).a);
        opacity.tooltip(UIKeys.MMOTION_OPACITY_TOOLTIP);
        panel.scroll.add(UI.label(UIKeys.MMOTION_OPACITY));
        panel.scroll.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, opacity, this.createResetIcon(() -> opacity.setValueAndNotify(1D))));

        this.keyframeRefreshers.add(() ->
        {
            if (!opacity.isDragging() && !opacity.textbox.isFocused() && keyframe.getValue() instanceof Color current)
            {
                opacity.setValue(current.a);
            }
        });
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addVisibilitySection(UIKeyframeFactory panel, MotionLayer layer, float tick)
    {
        panel.scroll.add(this.sectionTitle(UIKeys.MMOTION_CHANNEL_VISIBILITY));

        Keyframe keyframe = this.findKeyframeAt(layer.channelVisible, tick);

        if (keyframe == null)
        {
            panel.scroll.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, UI.label(UIKeys.MMOTION_NO_VISIBILITY_KEYFRAME).color(0xFF94A3B8)));

            return;
        }

        UIToggle toggle = new UIToggle(UIKeys.MMOTION_VISIBLE, this.readDoubleKeyframe(keyframe) > 0.5, (b) -> this.writeDoubleKeyframe(keyframe, b.getValue() ? 1D : 0D));

        panel.scroll.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, toggle, this.createResetIcon(() ->
        {
            toggle.setValue(true);
            this.writeDoubleKeyframe(keyframe, 1D);
        })));

        this.keyframeRefreshers.add(() -> toggle.setValue(this.readDoubleKeyframe(keyframe) > 0.5));
    }
    private UIButton resetButton(IKey label, Runnable action)
    {
        UIButton button = new UIButton(label, (b) ->
        {
            action.run();
            UIUtils.playClick();
        });

        button.tooltip(UIKeys.MMOTION_RESET_TO_DEFAULT.format(label));

        return button;
    }

    private UIIcon createResetIcon(Runnable action)
    {
        UIIcon icon = new UIIcon(Icons.REFRESH, (b) ->
        {
            action.run();
            UIUtils.playClick();
        });

        icon.tooltip(UIKeys.MMOTION_RESET_DEFAULT);
        icon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);

        return icon;
    }

    /**
     * Gives an input its Reset button: in the same row when the input is a compact widget, or
     * as a full-width button right under it when the input is a tall picker.
     */
    private void attachReset(UIElement input, boolean inRow, Runnable action)
    {
        if (input == null || input.getParent() == null || action == null)
        {
            return;
        }

        UIElement parent = input.getParent();

        if (inRow)
        {
            parent.add(this.createResetIcon(action));

            return;
        }

        List<UIElement> kids = new ArrayList<>();

        for (Object child : parent.getChildren())
        {
            if (child instanceof UIElement element)
            {
                kids.add(element);
            }
        }

        parent.removeAll();

        for (UIElement kid : kids)
        {
            if (kid != input)
            {
                parent.add(kid);
            }
            else if (input instanceof UIBlockStateEditor || input instanceof UIItemStack)
            {
                parent.add(input, this.resetButton(UIKeys.MMOTION_RESET, action));
            }
            else
            {
                parent.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, input, this.createResetIcon(action)));
            }
        }
    }

    private Runnable layerReset(Consumer<MotionLayer> action)
    {
        return () ->
        {
            MotionLayer layer = this.getSelected();

            if (layer == null)
            {
                return;
            }

            action.accept(layer);
            this.syncPropertiesFromSelected();
            this.rebuildLayersList();
        };
    }

    private void attachActorResets()
    {
        /* Display name (reset sits on the actor card) */
        if (this.actorCard != null)
        {
            UIIcon nameReset = this.createResetIcon(this.layerReset((l) -> l.name = l.defaultName != null ? l.defaultName : l.name));

            nameReset.relative(this.actorCard).x(1F, -22).y(24).wh(16, 16);
            this.actorCard.add(nameReset);
        }

        /* Common actor properties */

        this.attachReset(this.actorColorPicker, false, this.layerReset((l) ->
        {
            l.color = l.defaultColor;

            if (UIMMotionPanel.isLightType(l.type))
            {
                l.lightColor = l.defaultColor;
            }
        }));

        this.attachReset(this.shadingToggle, false, this.layerReset((l) -> l.shading = true));

        /* Block */
        this.attachReset(this.blockStateEditor, false, this.layerReset((l) ->
        {
            l.blockState = Blocks.GRASS_BLOCK.getDefaultState();

            if (l.form instanceof BlockForm bf)
            {
                bf.blockState.set(l.blockState);
            }
        }));
        this.attachReset(this.blockLightInput, false, this.layerReset((l) -> l.blockLight = 15));

        /* Item */
        this.attachReset(this.itemStackEditor, false, this.layerReset((l) ->
        {
            l.itemStack = new ItemStack(Items.DIAMOND_SWORD);

            if (l.form instanceof ItemForm iform)
            {
                iform.stack.set(l.itemStack.copy());
            }
        }));
        this.attachReset(this.itemCountInput, true, this.layerReset((l) -> l.itemCount = 1));
        this.attachReset(this.itemGlintToggle, false, this.layerReset((l) -> l.itemGlint = false));
        this.attachReset(this.itemTransformModeBtn, true, this.layerReset((l) -> l.itemTransformMode = ModelTransformationMode.FIXED));

        /* Entity */
        this.attachReset(this.chooseModelBtn, false, this.layerReset((l) ->
        {
            l.entityModel = "";
            l.form = new ModelForm();
        }));

        /* Structure */
        this.attachReset(this.structureNameInput, false, this.layerReset((l) ->
        {
            l.structureName = "";

            if (l.form instanceof StructureForm sf)
            {
                sf.structure.set("");
            }

            this.centerStructureLayer(l);
        }));

        /* Light */
        this.attachReset(this.lightColorPicker, false, this.layerReset((l) ->
        {
            l.lightColor = 0xFFFBBF24;
            l.color = 0xFFFBBF24;
        }));
        this.attachReset(this.lightIntensityInput, false, this.layerReset((l) -> l.lightIntensity = 1.0));
        this.attachReset(this.lightRangeInput, false, this.layerReset((l) -> l.lightRange = 8.0));
        this.attachReset(this.lightConeInput, false, this.layerReset((l) -> l.lightCone = 45.0));
        this.attachReset(this.lightPenumbraInput, false, this.layerReset((l) -> l.lightPenumbra = 0.3));
        this.attachReset(this.lightWidthInput, false, this.layerReset((l) -> l.lightWidth = 2.0));
        this.attachReset(this.lightHeightInput, false, this.layerReset((l) -> l.lightHeight = 2.0));
        this.attachReset(this.lightGlowInput, false, this.layerReset((l) -> l.lightGlow = 0.5));

        /* Camera */
        this.attachReset(this.cameraFovInput, false, this.layerReset((l) -> l.cameraFov = 70.0));
    }

    private void syncPropertiesFromSelected()
    {
        MotionLayer layer = this.getSelected();
        this.syncKeyframePanel(layer);
        if (this.actorCard != null) this.actorCard.setVisible(layer != null);

        if (layer == null)
        {
            if (this.actorNameInput != null) this.actorNameInput.setText("");
            if (this.keyframeSection != null) this.keyframeSection.setVisible(false);
            if (this.blockSection != null) this.blockSection.setVisible(false);
            if (this.itemSection != null) this.itemSection.setVisible(false);
            if (this.entitySection != null) this.entitySection.setVisible(false);
            if (this.structureSection != null) this.structureSection.setVisible(false);
            if (this.lightSection != null) this.lightSection.setVisible(false);
            if (this.cameraSection != null) this.cameraSection.setVisible(false);
            if (this.posXInput != null) this.posXInput.setValue(0);
            if (this.posYInput != null) this.posYInput.setValue(0);
            if (this.posZInput != null) this.posZInput.setValue(0);
            if (this.rotPitchInput != null) this.rotPitchInput.setValue(0);
            if (this.rotYawInput != null) this.rotYawInput.setValue(0);
            if (this.rotRollInput != null) this.rotRollInput.setValue(0);
            if (this.scaleXInput != null) this.scaleXInput.setValue(1);
            if (this.scaleYInput != null) this.scaleYInput.setValue(1);
            if (this.scaleZInput != null) this.scaleZInput.setValue(1);
            if (this.transformEditor != null) this.transformEditor.setVisible(false);
            if (this.visibilityToggle != null) this.visibilityToggle.setVisible(false);
            if (this.actorColorLabel != null) this.actorColorLabel.setVisible(false);
            if (this.actorColorPicker != null) this.actorColorPicker.setVisible(false);
            if (this.opacityLabel != null) this.opacityLabel.setVisible(false);
            if (this.opacityInput != null) this.opacityInput.setVisible(false);
            if (this.shadingToggle != null) this.shadingToggle.setVisible(false);
            if (this.emptySelectionPlaceholder != null) this.emptySelectionPlaceholder.setVisible(true);
            if (this.propertiesScroll != null)
            {
                this.propertiesScroll.setVisible(false);
                this.propertiesScroll.resize();
            }
            return;
        }

        if (this.propertiesScroll != null) this.propertiesScroll.setVisible(true);
        if (this.emptySelectionPlaceholder != null) this.emptySelectionPlaceholder.setVisible(false);
        if (this.visibilityToggle != null) this.visibilityToggle.setVisible(true);
        if (this.actorColorLabel != null) this.actorColorLabel.setVisible(true);
        if (this.actorColorPicker != null) this.actorColorPicker.setVisible(true);
        if (this.opacityLabel != null) this.opacityLabel.setVisible(true);
        if (this.opacityInput != null) this.opacityInput.setVisible(true);
        if (this.shadingToggle != null) this.shadingToggle.setVisible(true);

        boolean editingTransform = this.transformEditor != null && this.transformEditor.isEditing();

        if (!editingTransform)
        {
            this.syncLiveTransformFromLayer(layer);
        }

        if (this.transformEditor != null)
        {
            if (!editingTransform)
            {
                this.transformEditor.setTransform(this.liveTransform);
            }

            this.transformEditor.setVisible(true);
        }

        if (this.actorNameInput != null && !this.actorNameInput.isFocused())
        {
            this.actorNameInput.setText(layer.name);
        }

        /* If a keyframe is selected, prioritize keyframe values */
        if (this.selectedKeyframe != null && kfIn(layer.channelTransform, this.selectedKeyframe) && this.selectedKeyframe.getValue() instanceof Transform t)
        {
            if (this.posXInput != null) this.posXInput.setValue(t.translate.x);
            if (this.posYInput != null) this.posYInput.setValue(t.translate.y);
            if (this.posZInput != null) this.posZInput.setValue(t.translate.z);

            if (this.rotPitchInput != null) this.rotPitchInput.setValue(t.rotate.x);
            if (this.rotYawInput != null) this.rotYawInput.setValue(t.rotate.y);
            if (this.rotRollInput != null) this.rotRollInput.setValue(t.rotate.z);

            if (this.scaleXInput != null) this.scaleXInput.setValue(t.scale.x);
            if (this.scaleYInput != null) this.scaleYInput.setValue(t.scale.y);
            if (this.scaleZInput != null) this.scaleZInput.setValue(t.scale.z);
        }
        else
        {
            if (this.posXInput != null) this.posXInput.setValue(layer.posX);
            if (this.posYInput != null) this.posYInput.setValue(layer.posY);
            if (this.posZInput != null) this.posZInput.setValue(layer.posZ);

            if (this.rotPitchInput != null) this.rotPitchInput.setValue(layer.rotPitch);
            if (this.rotYawInput != null) this.rotYawInput.setValue(layer.rotYaw);
            if (this.rotRollInput != null) this.rotRollInput.setValue(layer.rotRoll);

            if (this.scaleXInput != null) this.scaleXInput.setValue(layer.scaleX);
            if (this.scaleYInput != null) this.scaleYInput.setValue(layer.scaleY);
            if (this.scaleZInput != null) this.scaleZInput.setValue(layer.scaleZ);
        }

        if (this.visibilityToggle != null) this.visibilityToggle.setValue(layer.visible);
        if (this.actorColorPicker != null) this.actorColorPicker.setColor(layer.color);
        if (this.opacityInput != null) this.opacityInput.setValue(layer.opacity);
        if (this.shadingToggle != null) this.shadingToggle.setValue(layer.shading);

        /* Keyframe Card header & channel updates */
        if (this.keyframeSection != null)
        {
            if (this.selectedKeyframe != null)
            {
                this.keyframeSection.setVisible(true);
                this.keyframeTitleLabel.label = UIKeys.MMOTION_KEYFRAME_AT.format((int) this.selectedKeyframe.getTick());
                if (this.keyframeTickInput != null) this.keyframeTickInput.setValue(this.selectedKeyframe.getTick());

                /* Determine channel type */
                IKey channelDesc = kfIn(layer.channelTransformOverlay, this.selectedKeyframe) ? UIKeys.MMOTION_CHANNEL_TRANSFORM_OVERLAY : UIKeys.MMOTION_CHANNEL_TRANSFORM;
                if (this.selectedKeyframe.getValue() instanceof Color selectedColor)
                {
                    channelDesc = UIKeys.MMOTION_CHANNEL_COLORING;
                    if (this.opacityInput != null) this.opacityInput.setValue(selectedColor.a);
                }

                boolean isDouble = this.selectedKeyframe.getValue() instanceof Double;
                if (isDouble)
                {
                    double dVal = (double) this.selectedKeyframe.getValue();
                    if (kfIn(layer.channelColor, this.selectedKeyframe))
                    {
                        channelDesc = UIKeys.MMOTION_CHANNEL_COLORING;
                        if (this.opacityInput != null) this.opacityInput.setValue(dVal);
                    }
                    else if (kfIn(layer.channelVisible, this.selectedKeyframe))
                    {
                        channelDesc = UIKeys.MMOTION_CHANNEL_VISIBILITY;
                    }
                }

                if (this.keyframeChannelLabel != null) this.keyframeChannelLabel.label = UIKeys.MMOTION_KEYFRAME_CHANNEL.format(channelDesc);

                if (this.keyframeInterpBtn != null)
                {
                    Icon interpIcon = UIInterpolationContextMenu.INTERP_ICON_MAP.getOrDefault(
                        this.selectedKeyframe.getInterpolation().getInterp(),
                        Icons.GRAPH
                    );
                    this.keyframeInterpBtn.both(interpIcon);
                    this.keyframeInterpBtn.tooltip(UIKeys.MMOTION_INTERPOLATION.format(InterpolationUtils.getName(this.selectedKeyframe.getInterpolation().getInterp()).get()));
                }
            }
            else
            {
                this.keyframeSection.setVisible(false);
            }
        }

        /* Actor Specific Section Visibility & Sync */
        String type = layer.type != null ? layer.type : "";

        if (this.blockSection != null)
        {
            boolean isBlock = type.equalsIgnoreCase("Block");
            this.blockSection.setVisible(isBlock);
            if (isBlock)
            {
                if (this.blockStateEditor != null) this.blockStateEditor.setBlockState(layer.blockState != null ? layer.blockState : Blocks.GRASS_BLOCK.getDefaultState());
                if (this.blockLightInput != null) this.blockLightInput.setValue(layer.blockLight);
            }
        }

        if (this.itemSection != null)
        {
            boolean isItem = type.equalsIgnoreCase("Item");
            this.itemSection.setVisible(isItem);
            if (isItem)
            {
                if (this.itemStackEditor != null) this.itemStackEditor.setStack(layer.itemStack != null ? layer.itemStack : new ItemStack(Items.DIAMOND_SWORD));
                if (this.itemCountInput != null) this.itemCountInput.setValue(layer.itemCount);
                if (this.itemGlintToggle != null) this.itemGlintToggle.setValue(layer.itemGlint);
                if (this.itemTransformModeBtn != null)
                {
                    String modeStr = layer.itemTransformMode != null ? layer.itemTransformMode.asString() : "fixed";
                    this.itemTransformModeBtn.label = UIKeys.MMOTION_ITEM_MODE.format(modeStr);
                }
            }
        }

        if (this.entitySection != null)
        {
            boolean isEntity = type.equalsIgnoreCase("Entity");
            this.entitySection.setVisible(isEntity);
            if (isEntity)
            {
                if (this.chooseModelBtn != null)
                {
                    String modelName = layer.entityModel != null && !layer.entityModel.isEmpty() ? layer.entityModel : UIKeys.MMOTION_DEFAULT_MODEL.get();
                    this.chooseModelBtn.label = UIKeys.MMOTION_MODEL.format(modelName);
                }
            }
        }

        if (this.structureSection != null)
        {
            boolean isStruct = type.equalsIgnoreCase("Structure");
            this.structureSection.setVisible(isStruct);
            if (isStruct && this.structureNameInput != null)
            {
                this.structureNameInput.setText(layer.structureName != null ? layer.structureName : "");
            }
        }

        if (this.lightSection != null)
        {
            boolean isLight = isLightType(type);
            this.lightSection.setVisible(isLight);
            if (isLight)
            {
                if (this.lightColorPicker != null) this.lightColorPicker.setColor(layer.lightColor);

                boolean sceneLight = isSceneLightType(type);

                if (this.lightTypeLabel != null)
                {
                    String typeName = type.equalsIgnoreCase("PointLight") ? "Point Light" : (type.equalsIgnoreCase("SpotLight") ? "Spot Light" : (type.equalsIgnoreCase("RectLight") ? "Rect Light" : "Studio Light"));

                    this.lightTypeLabel.label = UIKeys.MMOTION_LIGHT_TYPE.format(typeName);
                }

                if (this.lightRangeInput != null) this.lightRangeInput.setValue(layer.lightRange);
                if (this.lightConeInput != null) this.lightConeInput.setValue(layer.lightCone);
                if (this.lightPenumbraInput != null) this.lightPenumbraInput.setValue(layer.lightPenumbra);
                if (this.lightWidthInput != null) this.lightWidthInput.setValue(layer.lightWidth);
                if (this.lightHeightInput != null) this.lightHeightInput.setValue(layer.lightHeight);
                if (this.lightRangeGroup != null) this.lightRangeGroup.setVisible(sceneLight);
                if (this.lightSpotGroup != null) this.lightSpotGroup.setVisible(type.equalsIgnoreCase("SpotLight"));
                if (this.lightRectGroup != null) this.lightRectGroup.setVisible(type.equalsIgnoreCase("RectLight"));
                if (this.lightGlowInput != null) this.lightGlowInput.setValue(layer.lightGlow);
                if (this.lightGlowGroup != null) this.lightGlowGroup.setVisible(type.equalsIgnoreCase("PointLight") || type.equalsIgnoreCase("SpotLight"));
                if (this.lightIntensityInput != null) this.lightIntensityInput.setValue(layer.lightIntensity);
            }
        }

        boolean isCamera = type.equalsIgnoreCase("Camera");

        if (this.cameraSection != null)
        {
            this.cameraSection.setVisible(isCamera);
            if (isCamera)
            {
                if (this.cameraFovInput != null) this.cameraFovInput.setValue(layer.cameraFov);
                if (this.cameraRenderToggle != null) this.cameraRenderToggle.setValue(layer.renderCamera);
                if (this.cameraViewBtn != null)
                {
                    this.cameraViewBtn.label = layer.id.equals(this.viewCameraId) ? UIKeys.MMOTION_CAMERA_EXIT : UIKeys.MMOTION_CAMERA_ENTER;
                }
            }
        }

        boolean keyframeMode = this.keyframePanel != null;

        if (this.visibilityToggle != null) this.visibilityToggle.setVisible(false);
        if (this.opacityLabel != null) this.opacityLabel.setVisible(false);
        if (this.opacityInput != null) this.opacityInput.setVisible(false);

        if (this.keyframeSection != null) this.keyframeSection.setVisible(false);
        if (this.transformEditor != null) this.transformEditor.setVisible(false);

        /* The camera has no keyframe-driven transform section: its transform block (native UIPropTransform) is shown directly */
        if (this.transformEditor != null && isCamera && !keyframeMode)
        {
            if (!this.transformEditor.isEditing())
            {
                this.syncLiveTransformFromLayer(layer);
                this.transformEditor.setTransform(this.liveTransform);
            }

            this.transformEditor.setVisible(true);
        }

        if (this.propertiesScroll != null) this.propertiesScroll.setVisible(!keyframeMode);

        if (this.propertiesScroll != null)
        {
            this.propertiesScroll.resize();
        }
    }

    /* ── Alt Alan: Native Keyframe Timeline (UIKeyframes) ── */
    private void setupTimeline()
    {
        this.timelineArea = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                int bg = BBSSettings.lightSurfaces() ? 0xDDEDEDED : 0xDD15161C;
                int border = BBSSettings.lightSurfaces() ? 0x33000000 : 0x22FFFFFF;

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
                context.batcher.box(a.x, a.y, a.ex(), a.y + 1, border);

                /* Top strip toolbar background */
                int toolbarH = 24;
                context.batcher.box(a.x, a.y, a.ex(), a.y + toolbarH, BBSSettings.lightSurfaces() ? 0xFFE2E2E2 : 0xFF1C1D24);
                context.batcher.box(a.x, a.y + toolbarH - 1, a.ex(), a.y + toolbarH, border);

                FontRenderer font = context.batcher.getFont();
                int toolbarCy = a.y + (toolbarH - font.getHeight()) / 2;

                /* Frame Counter Text */
                String frameStr = String.format("%03d / %03d ticks (00:%02d:%02d)",
                    UIMMotionPanel.this.currentFrame,
                    UIMMotionPanel.this.maxFrames,
                    UIMMotionPanel.this.currentFrame / UIMMotionPanel.this.getFps(),
                    UIMMotionPanel.this.currentFrame % UIMMotionPanel.this.getFps());

                context.batcher.text(frameStr, a.x + 98, toolbarCy, Colors.WHITE);

                super.render(context);
            }
        };
        this.timelineArea.markContainer().noCulling();

        /* Playback Buttons */
        this.playPauseBtn = new UIIcon(() -> this.isPlaying ? Icons.PAUSE : Icons.PLAY, (b) ->
        {
            this.togglePlayback();
        });
        this.playPauseBtn.noCulling();
        this.playPauseBtn.tooltip(UIKeys.MMOTION_PLAY_PAUSE);
        this.playPauseBtn.relative(this.timelineArea).x(6).y(2).wh(20, 20);

        this.stopBtn = new UIIcon(Icons.STOP, (b) ->
        {
            this.isPlaying = false;
            this.currentFrame = 0;
            this.updateLayersFromKeyframes();
            this.syncPropertiesFromSelected();
            UIUtils.playClick();
        });
        this.stopBtn.noCulling();
        this.stopBtn.tooltip(UIKeys.MMOTION_STOP);
        this.stopBtn.relative(this.timelineArea).x(28).y(2).wh(20, 20);

        this.loopBtn = new UIIcon(Icons.REVERSE, (b) ->
        {
            this.loop = !this.loop;
            UIUtils.playClick();
        });
        this.loopBtn.noCulling();
        this.loopBtn.highlight(() -> this.loop, mchorse.bbs_mod.utils.Direction.BOTTOM);
        this.loopBtn.tooltip(UIKeys.MMOTION_LOOP);
        this.loopBtn.relative(this.timelineArea).x(50).y(2).wh(20, 20);

        /* Native Keyframe View */
        this.keyframesView = new UIMMotionKeyframes(this::onKeyframeSelected);
        this.keyframesView.noCulling();
        this.keyframesView.duration(() -> this.maxFrames);
        this.keyframesView.relative(this.timelineArea).y(24).w(1F).h(1F, -24);
        this.keyframesView.changed(this::onKeyframesChanged);
        this.keyframesView.getDopeSheet().setExpanded(this.timelineFolds);
        this.keyframesView.getDopeSheet().sectionSummaries = false;

        this.timelineArea.add(this.playPauseBtn, this.stopBtn, this.loopBtn, this.keyframesView);
        this.rebuildKeyframeSheets();
    }

    public void rebuildKeyframeSheets()
    {
        if (this.keyframesView == null) return;

        double savedScroll = this.keyframesView.getDopeSheet().getYAxis().getScroll();

        this.keyframesView.removeAllSheets();

        for (int i = 0; i < this.layers.size(); i++)
        {
            MotionLayer layer = this.layers.get(i);
            String layerKey = (layer.id != null && !layer.id.isEmpty()) ? layer.id : ("layer_" + i);

            layer.enforceVisibleHold();

            /*
             * The actor row is a pure group title (a dope sheet section): it holds no channel, draws no
             * keyframes and is the only row that folds. Every keyframe lives on one of the channel rows below.
             * The pose channel stays in the data for old saves but has no row: it drives nothing on screen.
             */
            UIKeyframeSheet.Section group = new UIKeyframeSheet.Section(layerKey, IKey.raw(layer.name), layer.icon, layer.color);

            UIKeyframeSheet transformSheet = this.addChannelSheet(group, layerKey + "_transform", UIKeys.MMOTION_CHANNEL_TRANSFORM, 0xFF10B981, Icons.POSE, layer.channelTransform);

            /* Transform Overlay folds under the Transform row (its arrow); a fresh actor starts folded unless it already has overlay keys */
            if (this.seenTransformFolds.add(transformSheet.id))
            {
                this.timelineFolds.set(transformSheet.id, !layer.channelTransformOverlay.isEmpty());
            }

            UIKeyframeSheet overlaySheet = new UIKeyframeSheet(layerKey + "_transform_overlay", UIKeys.MMOTION_CHANNEL_TRANSFORM_OVERLAY, 0xFF34D399, layer.channelTransformOverlay, null);

            overlaySheet.icon(Icons.POSE);
            overlaySheet.section = group;
            overlaySheet.setParent(transformSheet);
            this.keyframesView.addSheet(overlaySheet);

            this.addChannelSheet(group, layerKey + "_color", UIKeys.MMOTION_CHANNEL_COLORING, 0xFFA855F7, Icons.COLOR, layer.channelColor);

            if (isSceneLightType(layer.type))
            {
                this.addLightSheet(group, layerKey + "_intensity", UIKeys.MMOTION_CHANNEL_INTENSITY, layer.channelIntensity);
                this.addLightSheet(group, layerKey + "_range", UIKeys.MMOTION_CHANNEL_RANGE, layer.channelRange);

                if (layer.type.equalsIgnoreCase("SpotLight"))
                {
                    this.addLightSheet(group, layerKey + "_cone", UIKeys.MMOTION_CHANNEL_CONE, layer.channelCone);
                }
            }

            this.addChannelSheet(group, layerKey + "_visible", UIKeys.MMOTION_CHANNEL_VISIBILITY, 0xFF06B6D4, Icons.VISIBLE, layer.channelVisible);
        }

        /* Preserve persistent fold state */
        this.keyframesView.getDopeSheet().setExpanded(this.timelineFolds);

        /* Restore vertical track scroll position so list does not jump to top */
        this.keyframesView.getDopeSheet().getYAxis().setScroll(savedScroll);
        this.keyframesView.getDopeSheet().getYAxis().clamp();
    }

    private void onKeyframeSelected(Keyframe keyframe)
    {
        this.selectedKeyframe = keyframe;
        if (keyframe != null)
        {
            for (int i = 0; i < this.layers.size(); i++)
            {
                MotionLayer layer = this.layers.get(i);
                if (kfIn(layer.channelTransform, keyframe) || kfIn(layer.channelTransformOverlay, keyframe)
                    || kfIn(layer.channelPose, keyframe) || kfIn(layer.channelIntensity, keyframe) || kfIn(layer.channelRange, keyframe) || kfIn(layer.channelCone, keyframe)
                    || kfIn(layer.channelColor, keyframe)
                    || kfIn(layer.channelVisible, keyframe))
                {
                    this.selectedLayerIndex = i;
                    break;
                }
            }
        }
        this.rebuildLayersList();
        this.syncPropertiesFromSelected();
    }

    private void onKeyframesChanged()
    {
        this.updateLayersFromKeyframes();
        this.syncPropertiesFromSelected();
    }

    private void addLightSheet(UIKeyframeSheet.Section group, String id, IKey title, KeyframeChannel<Double> channel)
    {
        this.addChannelSheet(group, id, title, 0xFFFBBF24, Icons.SUN, channel);
    }

    /** One channel row under an actor's group title. */
    private UIKeyframeSheet addChannelSheet(UIKeyframeSheet.Section group, String id, IKey title, int color, Icon icon, KeyframeChannel<?> channel)
    {
        UIKeyframeSheet sheet = new UIKeyframeSheet(id, title, color, channel, null);

        sheet.icon(icon);
        sheet.section = group;
        this.keyframesView.addSheet(sheet);

        return sheet;
    }

    /* A light channel with a single keyframe is static: the live value wins, unless that very keyframe is being edited */
    private double driveLightValue(KeyframeChannel<Double> channel, double current)
    {
        List<Keyframe<Double>> keyframes = channel.getKeyframes();

        if (keyframes.isEmpty()) return current;

        if (keyframes.size() == 1 && keyframes.get(0) != this.selectedKeyframe)
        {
            Double only = keyframes.get(0).getValue();

            if (only == null || Math.abs(only - current) > 1.0E-9)
            {
                keyframes.get(0).setValue(current);
            }

            return current;
        }

        Double value = channel.interpolate(this.currentFrame);

        return value != null ? value : current;
    }

    private static int packRgb(float r, float g, float b)
    {
        int ri = (int) Math.round(MathUtils.clamp(r, 0F, 1F) * 255F);
        int gi = (int) Math.round(MathUtils.clamp(g, 0F, 1F) * 255F);
        int bi = (int) Math.round(MathUtils.clamp(b, 0F, 1F) * 255F);

        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    /* Intensity, range, cone angle and colour of a scene light follow their keyframes (linear between keyframes) */
    private void applyLightChannels(MotionLayer layer)
    {
        layer.lightIntensity = this.driveLightValue(layer.channelIntensity, layer.lightIntensity);
        layer.lightRange = this.driveLightValue(layer.channelRange, layer.lightRange);
        layer.lightCone = this.driveLightValue(layer.channelCone, layer.lightCone);

        List<Keyframe<Color>> colors = layer.channelColor.getKeyframes();

        if (colors.isEmpty()) return;

        if (colors.size() == 1 && colors.get(0) != this.selectedKeyframe)
        {
            Color only = colors.get(0).getValue();

            if (only != null)
            {
                only.r = Colors.getR(layer.lightColor);
                only.g = Colors.getG(layer.lightColor);
                only.b = Colors.getB(layer.lightColor);
            }

            return;
        }

        Color value = layer.channelColor.interpolate(this.currentFrame);

        if (value != null)
        {
            layer.lightColor = packRgb(value.r, value.g, value.b);
            layer.color = layer.lightColor;
        }
    }

    /* Editing a light value while its channel is animated writes (or inserts) a keyframe instead of being reverted */
    private void writeLightChannel(KeyframeChannel<Double> channel, double value)
    {
        Keyframe<Double> target = null;

        if (this.selectedKeyframe != null && containsRef(channel.getKeyframes(), this.selectedKeyframe))
        {
            target = this.selectedKeyframe;
        }
        else if (channel.getKeyframes().size() > 1)
        {
            target = this.findKeyframeAt(channel, this.currentFrame);

            if (target == null)
            {
                channel.insert(this.currentFrame, value);

                if (this.keyframesView != null) this.keyframesView.triggerChange();

                return;
            }
        }

        if (target != null)
        {
            this.writeDoubleKeyframe(target, value);
        }
    }

    private void writeLightColorKeyframe(MotionLayer layer)
    {
        KeyframeChannel<Color> channel = layer.channelColor;
        Keyframe<Color> target = null;

        if (this.selectedKeyframe != null && containsRef(channel.getKeyframes(), this.selectedKeyframe))
        {
            target = this.selectedKeyframe;
        }
        else if (channel.getKeyframes().size() > 1)
        {
            target = this.findKeyframeAt(channel, this.currentFrame);

            if (target == null)
            {
                channel.insert(this.currentFrame, new Color(Colors.getR(layer.lightColor), Colors.getG(layer.lightColor), Colors.getB(layer.lightColor), 1F));

                if (this.keyframesView != null) this.keyframesView.triggerChange();

                return;
            }
        }

        if (target != null && target.getValue() != null)
        {
            Color next = target.getValue().copy();

            next.r = Colors.getR(layer.lightColor);
            next.g = Colors.getG(layer.lightColor);
            next.b = Colors.getB(layer.lightColor);

            target.preNotify();
            target.setValue(next);
            target.postNotify();

            if (this.keyframesView != null) this.keyframesView.triggerChange();
        }
    }

    public void updateLayersFromKeyframes()
    {
        MotionLayer selected = this.getSelected();
        boolean editingSelected = selected != null && ((this.transformEditor != null && this.transformEditor.isEditing()) || (this.viewportRenderer != null && this.viewportRenderer.isEditing()));

        for (MotionLayer layer : this.layers)
        {
            /* The overlay is sampled for every actor, also the one being dragged: the gizmo only edits the main transform */
            if (layer.channelTransformOverlay.isEmpty())
            {
                layer.overlay = null;
            }
            else
            {
                Transform overlay = layer.channelTransformOverlay.interpolate(this.currentFrame);

                layer.overlay = overlay != null ? overlay.copy() : null;
            }

            if (layer == selected && editingSelected)
            {
                continue;
            }

            /* Actors with an empty transform channel keep their static pos / rot / scale untouched; only real keyframes drive them */
            if (!layer.channelTransform.isEmpty())
            {
                Transform t = layer.channelTransform.interpolate(this.currentFrame);
                if (t != null)
                {
                    layer.posX = t.translate.x;
                    layer.posY = t.translate.y;
                    layer.posZ = t.translate.z;
                    Vector3f euler = new Vector3f();
                    t.getEulerRotation(euler);
                    layer.rotPitch = MathUtils.toDeg(euler.x);
                    layer.rotYaw = MathUtils.toDeg(euler.y);
                    layer.rotRoll = MathUtils.toDeg(euler.z);
                    layer.scaleX = t.scale.x;
                    layer.scaleY = t.scale.y;
                    layer.scaleZ = t.scale.z;
                }
            }

            if (!layer.channelColor.isEmpty())
            {
                Color overlay = layer.channelColor.interpolate(this.currentFrame);
                if (overlay != null)
                {
                    layer.tintR = overlay.r;
                    layer.tintG = overlay.g;
                    layer.tintB = overlay.b;
                    layer.opacity = MathUtils.clamp(overlay.a, 0.0, 1.0);
                }
            }

            if (!layer.channelVisible.isEmpty())
            {
                layer.enforceVisibleHold();
                layer.visible = layer.isVisibleAt(this.currentFrame);
            }

            if (isSceneLightType(layer.type))
            {
                this.applyLightChannels(layer);
            }

            if (!layer.channelPose.isEmpty())
            {
                Double pose = layer.channelPose.interpolate(this.currentFrame);
                if (pose != null)
                {
                    layer.entityPose = pose;
                }
            }
        }
    }

    public void forceEditorLayout()
    {
        if (this.editor == null || !this.editor.isVisible())
        {
            return;
        }

        int topBarH = this.topBar != null && this.topBar.area.h > 0 ? this.topBar.area.h : UIPanelTopBar.HEIGHT;
        this.editor.relative(this).x(0).y(topBarH).w(1F).h(1F, -topBarH);
        Area ea = this.editor.area;
        if (ea.w <= 0 || ea.h <= 0)
        {
            ea.set(this.area.x, this.area.y + topBarH, this.area.w, Math.max(0, this.area.h - topBarH));
        }

        int subBarH = 24;
        int totalH = Math.max(80, ea.h - subBarH);
        int mainH = Math.max(60, totalH - this.timelineH);

        this.editorSubBar.area.set(ea.x, ea.y, ea.w, subBarH);
        this.sceneTreeArea.area.set(ea.x, ea.y + subBarH, this.leftW, mainH);
        this.propertiesArea.area.set(ea.ex() - this.rightW, ea.y + subBarH, this.rightW, mainH);
        this.viewportArea.area.set(this.sceneTreeArea.area.ex(), ea.y + subBarH, Math.max(40, this.propertiesArea.area.x - this.sceneTreeArea.area.ex()), mainH);
        this.timelineArea.area.set(ea.x, ea.ey() - this.timelineH, ea.w, this.timelineH);

        this.leftResizer.area.set(this.sceneTreeArea.area.ex() - 3, ea.y + subBarH, 6, mainH);
        this.rightResizer.area.set(this.propertiesArea.area.x - 3, ea.y + subBarH, 6, mainH);
        this.bottomResizer.area.set(ea.x, ea.ey() - this.timelineH - 3, ea.w, 6);

        this.editorSubBar.relative(this.editor).x(0).y(0).w(1F).h(subBarH);
        this.sceneTreeArea.relative(this.editor).x(0).y(subBarH).w(this.leftW).h(mainH);
        this.propertiesArea.relative(this.editor).x(1F, -this.rightW).y(subBarH).w(this.rightW).h(mainH);
        this.viewportArea.relative(this.editor).x(this.leftW).y(subBarH).w(1F, -(this.leftW + this.rightW)).h(mainH);
        this.timelineArea.relative(this.editor).x(0).y(1F, -this.timelineH).w(1F).h(this.timelineH);

        this.leftResizer.relative(this.editor).x(this.leftW - 3).y(subBarH).w(6).h(mainH);
        this.rightResizer.relative(this.editor).x(1F, -this.rightW - 3).y(subBarH).w(6).h(mainH);
        this.bottomResizer.relative(this.editor).x(0).y(1F, -this.timelineH - 3).w(1F).h(6);

        this.editorSubBar.resize();
        this.sceneTreeArea.resize();
        this.viewportArea.resize();
        this.propertiesArea.resize();
        this.timelineArea.resize();
        this.leftResizer.resize();
        this.rightResizer.resize();
        this.bottomResizer.resize();

        if (this.addActorBtn != null)
        {
            this.addActorBtn.relative(this.sceneTreeArea).x(1F, -22).y(3).wh(18, 18);
            this.addActorBtn.area.set(this.sceneTreeArea.area.ex() - 22, this.sceneTreeArea.area.y + 3, 18, 18);
            this.addActorBtn.resize();
        }
        if (this.layersScroll != null)
        {
            this.layersScroll.relative(this.sceneTreeArea).x(0).y(24).w(1F).h(1F, -24);
            this.layersScroll.area.set(this.sceneTreeArea.area.x, this.sceneTreeArea.area.y + 24, this.sceneTreeArea.area.w, Math.max(0, this.sceneTreeArea.area.h - 24));
            this.layersScroll.resize();
        }
        if (this.propertiesScroll != null)
        {
            this.propertiesScroll.relative(this.propertiesArea).x(4).y(28).w(1F, -8).h(1F, -32);
            this.propertiesScroll.area.set(this.propertiesArea.area.x + 4, this.propertiesArea.area.y + 28, Math.max(0, this.propertiesArea.area.w - 8), Math.max(0, this.propertiesArea.area.h - 32));
            this.propertiesScroll.resize();
        }
        if (this.keyframesView != null)
        {
            this.keyframesView.relative(this.timelineArea).x(0).y(24).w(1F).h(1F, -24);
            this.keyframesView.area.set(this.timelineArea.area.x, this.timelineArea.area.y + 24, this.timelineArea.area.w, Math.max(0, this.timelineArea.area.h - 24));
            this.keyframesView.resize();
        }
    }

    @Override
    public void resize()
    {
        int topBarH = this.topBar != null && this.topBar.area.h > 0 ? this.topBar.area.h : UIPanelTopBar.HEIGHT;
        if (this.editor != null)
        {
            this.editor.relative(this).x(0).y(topBarH).w(1F).h(1F, -topBarH);
        }

        super.resize();

        if (this.hubScreen != null && this.hubScreen.isVisible())
        {
            this.hubScreen.resize();
        }

        if (this.editor != null && this.editor.isVisible())
        {
            this.forceEditorLayout();
        }
    }

    /* ══════════════ History (undo / redo), saving, project settings & rendering ══════════════ */

    private static final int HISTORY_LIMIT = 100;
    private static final long HISTORY_POLL_MS = 200L;
    private static final long HISTORY_SETTLE_MS = 350L;
    private static final long AUTOSAVE_DELAY_MS = 2000L;

    private final java.util.ArrayDeque<String> undoStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> redoStack = new java.util.ArrayDeque<>();
    private String committedSnapshot;
    private String pendingSnapshot;
    private long pendingSince;
    private long lastPoll;
    private boolean dirty;
    private long dirtySince;
    private boolean applyingHistory;
    private String saveStatus = "";

    public RenderJob renderJob;

    /** One running PNG export; one frame is drawn per UI frame so the game keeps responding. */
    public static final class RenderJob
    {
        public File folder;
        public int start;
        public int end;
        public int next;
        public int written;
        public int w;
        public int h;
        public int savedFrame;
        public boolean transparent;
        public boolean cancelled;
        public String error;
        public net.minecraft.client.gl.SimpleFramebuffer fb;

        /** 0 = PNG sequence, 1 = MOV ProRes 4444 (alpha), 2 = MOV QuickTime Animation (alpha). */
        public int format;
        public File movFile;
        public Process process;
        public java.io.OutputStream pipe;

        /* MOV only: the GL read back lands in the direct buffer, the upright frame for the pipe in the array; both are reused every frame */
        public java.nio.ByteBuffer pixels;
        public byte[] frame;

        public boolean isMov()
        {
            return this.format == 1 || this.format == 2;
        }
    }

    public int getFps()
    {
        return this.currentScene != null ? Math.max(1, this.currentScene.fps) : 60;
    }

    public boolean isUnsaved()
    {
        return this.dirty || this.pendingSnapshot != null;
    }

    public String getSaveStateText()
    {
        if (this.renderJob != null)
        {
            return UIKeys.MMOTION_STATUS_RENDERING.get();
        }

        if (this.isUnsaved())
        {
            return "\u25CF " + UIKeys.MMOTION_STATUS_UNSAVED.get();
        }

        return this.saveStatus.isEmpty() ? UIKeys.MMOTION_STATUS_SAVED.get() : this.saveStatus;
    }

    private static String clock()
    {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
    }

    public void setViewportMode(int mode)
    {
        this.viewportMode = mode;
        this.refreshViewportModeButtons();
        UIUtils.playClick();
    }

    private void refreshViewportModeButtons()
    {
        int on = 0xFF3B82F6;
        int onHover = 0xFF60A5FA;
        int off = 0xFF232733;
        int offHover = 0xFF2F3544;

        if (this.litBtn != null) this.litBtn.color(this.viewportMode == 0 ? on : off, this.viewportMode == 0 ? onHover : offHover);
        if (this.unlitBtn != null) this.unlitBtn.color(this.viewportMode == 1 ? on : off, this.viewportMode == 1 ? onHover : offHover);
        if (this.wireBtn != null) this.wireBtn.color(this.viewportMode == 2 ? on : off, this.viewportMode == 2 ? onHover : offHover);
    }

    /** Mirror the live editor state into the scene object, so the hub, the save and the history all see the same thing. */
    public void syncSceneModel()
    {
        if (this.currentScene == null)
        {
            return;
        }

        this.currentScene.layers.clear();
        this.currentScene.layers.addAll(this.layers);
        this.currentScene.duration = this.maxFrames;
    }

    private String takeSnapshot()
    {
        this.syncSceneModel();

        return MotionSceneIO.snapshot(this.currentScene, this.layers);
    }

    private void resetHistory()
    {
        this.undoStack.clear();
        this.redoStack.clear();
        this.pendingSnapshot = null;
        this.dirty = false;
        this.committedSnapshot = this.currentScene == null ? null : this.takeSnapshot();
    }

    private static boolean isPointerDown()
    {
        long window = MinecraftClient.getInstance().getWindow().getHandle();

        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void markDirty()
    {
        this.dirty = true;
        this.dirtySince = System.currentTimeMillis();
    }

    /**
     * Edits are noticed by comparing the serialized scene to the last committed one. A change
     * becomes ONE history step once it has stopped moving (no drag, no typing) for a moment, so a
     * whole gizmo drag or a typed word undoes in one go.
     */
    private void pollHistory()
    {
        if (this.currentScene == null || this.committedSnapshot == null || this.isPlaying || this.applyingHistory || this.renderJob != null)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - this.lastPoll >= HISTORY_POLL_MS)
        {
            this.lastPoll = now;

            String current = this.takeSnapshot();

            if (current.equals(this.committedSnapshot))
            {
                this.pendingSnapshot = null;
            }
            else if (!current.equals(this.pendingSnapshot))
            {
                this.pendingSnapshot = current;
                this.pendingSince = now;
            }
        }

        boolean pointer = isPointerDown();

        if (this.pendingSnapshot != null && !pointer && now - this.pendingSince >= HISTORY_SETTLE_MS)
        {
            this.commitPending();
        }

        if (this.dirty && !pointer && this.pendingSnapshot == null && now - this.dirtySince >= AUTOSAVE_DELAY_MS)
        {
            this.saveScene(true);
        }
    }

    private void commitPending()
    {
        if (this.pendingSnapshot == null)
        {
            return;
        }

        this.undoStack.push(this.committedSnapshot);

        while (this.undoStack.size() > HISTORY_LIMIT)
        {
            this.undoStack.removeLast();
        }

        this.redoStack.clear();
        this.committedSnapshot = this.pendingSnapshot;
        this.pendingSnapshot = null;
        this.markDirty();
    }

    /** Turns whatever is in flight right now into a history step (used before undo and before saving). */
    private void flushHistory()
    {
        if (this.currentScene == null || this.committedSnapshot == null || this.applyingHistory)
        {
            return;
        }

        String current = this.takeSnapshot();

        if (!current.equals(this.committedSnapshot))
        {
            this.pendingSnapshot = current;
            this.commitPending();
        }
    }

    public void undo()
    {
        if (this.currentScene == null || this.renderJob != null)
        {
            return;
        }

        this.flushHistory();

        if (this.undoStack.isEmpty())
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_NOTHING_TO_UNDO.get();

            return;
        }

        this.redoStack.push(this.committedSnapshot);
        this.restoreSnapshot(this.undoStack.pop());
        this.saveStatus = UIKeys.MMOTION_STATUS_UNDONE.format(this.undoStack.size()).get();
        UIUtils.playClick();
    }

    public void redo()
    {
        if (this.currentScene == null || this.renderJob != null)
        {
            return;
        }

        this.flushHistory();

        if (this.redoStack.isEmpty())
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_NOTHING_TO_REDO.get();

            return;
        }

        this.undoStack.push(this.committedSnapshot);
        this.restoreSnapshot(this.redoStack.pop());
        this.saveStatus = UIKeys.MMOTION_STATUS_REDONE.get();
        UIUtils.playClick();
    }

    private void restoreSnapshot(String snapshot)
    {
        this.applyingHistory = true;

        try
        {
            mchorse.bbs_mod.data.types.MapType map = mchorse.bbs_mod.data.DataToString.mapFromString(snapshot);
            MotionLayer previouslySelected = this.getSelected();
            String selectedId = previouslySelected != null ? previouslySelected.id : null;

            List<MotionLayer> restored = MotionSceneIO.readLayers(map);

            this.layers.clear();
            this.layers.addAll(restored);
            MotionSceneIO.applySettings(this.currentScene, map);
            this.maxFrames = this.currentScene.duration;
            this.currentFrame = MathUtils.clamp(this.currentFrame, 0, this.maxFrames);

            this.selectedLayerIndex = this.layers.isEmpty() ? -1 : 0;

            for (int i = 0; i < this.layers.size(); i++)
            {
                if (this.layers.get(i).id.equals(selectedId))
                {
                    this.selectedLayerIndex = i;

                    break;
                }
            }

            this.selectedKeyframe = null;

            if (this.viewportRenderer != null)
            {
                this.viewportRenderer.resetGizmoState();
            }

            this.updateLayersFromKeyframes();
            this.rebuildLayersList();
            this.rebuildKeyframeSheets();
            this.syncPropertiesFromSelected();

            this.committedSnapshot = snapshot;
            this.pendingSnapshot = null;
            this.syncSceneModel();
            this.markDirty();

            if (this.hubScreen != null)
            {
                this.hubScreen.refresh();
            }
        }
        finally
        {
            this.applyingHistory = false;
        }
    }

    public boolean saveScene(boolean auto)
    {
        if (this.currentScene == null)
        {
            return false;
        }

        if (!this.applyingHistory && this.renderJob == null)
        {
            this.flushHistory();
        }

        this.syncSceneModel();

        boolean ok = MotionSceneIO.save(this.currentScene, this.layers);

        if (ok)
        {
            this.dirty = false;
            this.saveStatus = (auto ? UIKeys.MMOTION_STATUS_AUTOSAVED : UIKeys.MMOTION_STATUS_SAVED_AT).format(clock()).get();

            if (!auto)
            {
                UIUtils.playClick();
            }
        }
        else
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_SAVE_FAILED.get();
        }

        return ok;
    }

    public void openProjectSettings()
    {
        if (this.currentScene == null)
        {
            return;
        }

        mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(this.getContext(), new UIMMotionProjectSettingsDialog(this), 360, 270);
    }

    public void openRenderDialog()
    {
        if (this.currentScene == null || this.renderJob != null)
        {
            return;
        }

        mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(this.getContext(), new UIMMotionRenderDialog(this), 360, 320);
    }

    /** Edits the project settings of the open scene. Becomes its own undo step and is saved right away. */
    public void applyProjectSettings(int fps, int frames, int width, int height)
    {
        if (this.currentScene == null)
        {
            return;
        }

        this.flushHistory();

        this.currentScene.fps = MathUtils.clamp(fps, 1, 240);
        this.currentScene.width = MathUtils.clamp(width, 16, 7680);
        this.currentScene.height = MathUtils.clamp(height, 16, 4320);
        this.currentScene.duration = Math.max(1, frames);
        this.maxFrames = this.currentScene.duration;
        this.currentFrame = MathUtils.clamp(this.currentFrame, 0, this.maxFrames);

        this.updateLayersFromKeyframes();
        this.rebuildKeyframeSheets();
        this.syncPropertiesFromSelected();

        this.flushHistory();
        this.saveScene(false);

        if (this.hubScreen != null)
        {
            this.hubScreen.refresh();
        }
    }

    /* ── Rendering (PNG frame / sequence) ── */

    public void startRender(int start, int end, boolean transparent)
    {
        this.startRender(start, end, transparent, 0);
    }

    public void startRender(int start, int end, boolean transparent, int format)
    {
        if (this.currentScene == null || this.viewportRenderer == null || this.renderJob != null)
        {
            return;
        }

        this.saveScene(true);
        this.isPlaying = false;

        RenderJob job = new RenderJob();

        job.w = MathUtils.clamp(this.currentScene.width, 16, 7680);
        job.h = MathUtils.clamp(this.currentScene.height, 16, 4320);
        job.start = MathUtils.clamp(start, 0, this.maxFrames);
        job.end = MathUtils.clamp(end, job.start, this.maxFrames);
        job.next = job.start;
        job.transparent = transparent;
        job.savedFrame = this.currentFrame;
        job.format = MathUtils.clamp(format, 0, 2);

        String safe = this.currentScene.name.replaceAll("[^A-Za-z0-9_\\-]", "_");
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());

        if (job.isMov())
        {
            /* motion_scenes/renders/<scene>_<timestamp>.mov */
            job.folder = new File(UIMMotionHubScreen.getScenesFolder(), "renders");
            job.movFile = new File(job.folder, safe + "_" + stamp + ".mov");
        }
        else
        {
            job.folder = new File(UIMMotionHubScreen.getScenesFolder(), "renders/" + safe + "_" + stamp);
        }

        if (!job.folder.mkdirs() && !job.folder.exists())
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_RENDER_FOLDER_FAILED.get();

            return;
        }

        this.renderJob = job;
    }

    public void cancelRender()
    {
        if (this.renderJob != null)
        {
            this.renderJob.cancelled = true;
        }
    }

    private void processRenderJob(UIContext context)
    {
        RenderJob job = this.renderJob;

        if (job == null)
        {
            return;
        }

        if (job.cancelled || job.next > job.end)
        {
            this.finishRender(job);

            return;
        }

        net.minecraft.client.texture.NativeImage image = null;

        try
        {
            if (job.fb == null)
            {
                job.fb = new net.minecraft.client.gl.SimpleFramebuffer(job.w, job.h, true, MinecraftClient.IS_SYSTEM_MAC);
            }

            this.currentFrame = job.next;
            this.updateLayersFromKeyframes();

            if (job.isMov())
            {
                this.writeMovFrame(context, job);
            }
            else
            {
                image = this.viewportRenderer.renderExportFrame(context, job.fb, job.transparent);
                image.writeTo(new File(job.folder, String.format("frame_%05d.png", job.next)));
            }

            job.written++;
        }
        catch (Exception e)
        {
            job.error = e.toString();
            job.cancelled = true;
        }
        finally
        {
            if (image != null)
            {
                image.close();
            }
        }

        job.next++;
    }

    /**
     * Renders one frame and pipes it as raw RGBA (R, G, B, A byte order) to FFmpeg. The pixels come
     * from the GPU in one bulk read and the vertical flip is a row-by-row bulk copy, so the CPU never
     * touches single pixels; with a transparent background the alpha byte is never overwritten.
     */
    private void writeMovFrame(UIContext context, RenderJob job) throws java.io.IOException
    {
        if (job.process == null)
        {
            java.util.List<String> args = mchorse.bbs_mod.camera.export.FFmpegCommandBuilder.buildTransparentMovArgs(job.w, job.h, this.getFps(), job.movFile, job.format == 1);
            ProcessBuilder builder = new ProcessBuilder(args);

            builder.directory(job.folder);
            builder.redirectErrorStream(true);
            builder.redirectOutput(mchorse.bbs_mod.BBSMod.getSettingsPath("converter.log"));

            job.process = builder.start();
            /* The frame is already one large array: buffering it again would only add a copy */
            job.pipe = job.process.getOutputStream();
            job.pixels = org.lwjgl.BufferUtils.createByteBuffer(job.w * job.h * 4);
            job.frame = new byte[job.w * job.h * 4];
        }

        this.viewportRenderer.renderExportFrameRgba(context, job.fb, job.transparent, job.pixels);

        int stride = job.w * 4;

        /* GL rows run bottom-up, FFmpeg wants top-down: one bulk copy per row */
        for (int y = 0; y < job.h; y++)
        {
            job.pixels.get((job.h - 1 - y) * stride, job.frame, y * stride, stride);
        }

        /* Opaque background: same as the PNG path, whatever translucent geometry left in the alpha channel */
        if (!job.transparent)
        {
            for (int i = 3; i < job.frame.length; i += 4)
            {
                job.frame[i] = (byte) 0xFF;
            }
        }

        job.pipe.write(job.frame);
    }

    private void closeMovPipe(RenderJob job)
    {
        if (job.process == null)
        {
            return;
        }

        try
        {
            if (job.pipe != null)
            {
                job.pipe.close();
            }

            if (job.cancelled)
            {
                job.process.destroy();
            }
            else if (!job.process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS))
            {
                job.process.destroyForcibly();
                job.error = UIKeys.MMOTION_STATUS_FFMPEG_TIMEOUT.get();
            }
            else if (job.process.exitValue() != 0 && job.error == null)
            {
                job.error = UIKeys.MMOTION_STATUS_FFMPEG_EXIT.format(job.process.exitValue()).get();
            }
        }
        catch (Exception e)
        {
            if (job.error == null)
            {
                job.error = e.toString();
            }

            job.process.destroyForcibly();
        }
        finally
        {
            job.process = null;
            job.pipe = null;
            job.pixels = null;
            job.frame = null;
        }
    }

    private void finishRender(RenderJob job)
    {
        try
        {
            this.closeMovPipe(job);
        }
        finally
        {
            if (job.fb != null)
            {
                job.fb.delete();
                job.fb = null;
            }
        }

        this.currentFrame = job.savedFrame;
        this.updateLayersFromKeyframes();
        this.syncPropertiesFromSelected();
        this.renderJob = null;

        /* A new render may come with a new picture for the scene card: look at the disk again */
        if (this.currentScene != null)
        {
            UIMMotionHubScreen.invalidateThumbnail(this.currentScene.id);
        }

        if (job.error != null)
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_RENDER_ERROR.format(job.error).get();
        }
        else if (job.cancelled)
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_RENDER_CANCELLED.format(job.written).get();
        }
        else
        {
            this.saveStatus = UIKeys.MMOTION_STATUS_RENDER_DONE.format(job.written).get() + (job.movFile != null ? " → " + job.movFile.getName() : "");
            UIUtils.openFolder(job.folder);
        }
    }

    private void drawRenderOverlay(UIContext context)
    {
        RenderJob job = this.renderJob;

        if (job == null)
        {
            return;
        }

        int total = Math.max(1, job.end - job.start + 1);
        int done = MathUtils.clamp(job.next - job.start, 0, total);
        int w = 300;
        int h = 62;
        int x = this.area.mx() - w / 2;
        int y = this.area.my() - h / 2;
        FontRenderer font = context.batcher.getFont();

        context.batcher.box(x, y, x + w, y + h, 0xEE10141E);
        context.batcher.outline(x, y, x + w, y + h, 0x66FFFFFF);
        context.batcher.text(UIKeys.MMOTION_RENDER_PROGRESS.format(Math.min(job.next, job.end), job.end, job.w, job.h).get(), x + 12, y + 10, Colors.WHITE);
        context.batcher.box(x + 12, y + 28, x + w - 12, y + 36, 0xFF2A2F3C);
        context.batcher.box(x + 12, y + 28, x + 12 + (int) ((w - 24) * (done / (float) total)), y + 36, 0xFF3B82F6);
        context.batcher.text(UIKeys.MMOTION_RENDER_CANCEL_HINT.get(), x + 12, y + 44, 0xFF94A3B8);
    }

    /** Arrow keys: moves the playhead by a number of ticks (pausing playback), kept within 0 .. duration. */
    public void stepPlayhead(int ticks)
    {
        if (this.currentScene == null)
        {
            return;
        }

        this.isPlaying = false;
        this.currentFrame = MathUtils.clamp(this.currentFrame + ticks, 0, this.maxFrames);
        this.updateLayersFromKeyframes();
        this.syncPropertiesFromSelected();
    }

    /** Left / right arrow (held keys repeat), with shift for 10 ticks at a time; true when it was one of them. */
    private boolean handlePlayheadKeys(UIContext context)
    {
        int step = Window.isShiftPressed() ? 10 : 1;

        if (context.isHeld(GLFW.GLFW_KEY_LEFT))
        {
            this.stepPlayhead(-step);

            return true;
        }

        if (context.isHeld(GLFW.GLFW_KEY_RIGHT))
        {
            this.stepPlayhead(step);

            return true;
        }

        return false;
    }

    public void togglePlayback()
    {
        this.isPlaying = !this.isPlaying;
        if (this.isPlaying && this.currentFrame >= this.maxFrames)
        {
            this.currentFrame = 0;
        }
        this.lastTickTimeMs = System.currentTimeMillis();
        UIUtils.playClick();
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyAction() == KeyAction.RELEASED)
        {
            return false;
        }

        /* A running render owns the keyboard: Esc cancels, everything else is ignored */
        if (this.renderJob != null)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.cancelRender();
            }

            return true;
        }

        if (this.currentScene != null && Window.isCtrlPressed())
        {
            if (context.isPressed(GLFW.GLFW_KEY_S))
            {
                this.saveScene(false);

                return true;
            }

            boolean typing = context.isFocused() || (this.actorNameInput != null && this.actorNameInput.isFocused());

            if (!typing && context.isPressed(GLFW.GLFW_KEY_Z))
            {
                if (Window.isShiftPressed())
                {
                    this.redo();
                }
                else
                {
                    this.undo();
                }

                return true;
            }

            if (!typing && context.isPressed(GLFW.GLFW_KEY_Y))
            {
                this.redo();

                return true;
            }
        }

        if (this.currentScene != null && !context.isFocused() && (this.actorNameInput == null || !this.actorNameInput.isFocused()))
        {
            if (this.transformEditor != null && this.transformEditor.keyPressed(context) != null)
            {
                return true;
            }

            /* Escape: Aktör veya Keyframe seçimini temizle (Deselect) */
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                if (this.selectedKeyframe != null)
                {
                    this.selectedKeyframe = null;
                    if (this.keyframesView != null && this.keyframesView.getGraph() != null)
                    {
                        this.keyframesView.getGraph().clearSelection();
                    }
                    this.syncPropertiesFromSelected();
                    return true;
                }
                if (this.getSelected() != null)
                {
                    this.selectedLayerIndex = -1;
                    this.syncPropertiesFromSelected();
                    return true;
                }
            }

            /* Boşluk (Space): Oynat / Durdur (Play / Pause) */
            if (context.isPressed(GLFW.GLFW_KEY_SPACE))
            {
                this.togglePlayback();
                return true;
            }

            /* Home: Başa sar (0. tick) */
            if (context.isPressed(GLFW.GLFW_KEY_HOME))
            {
                this.currentFrame = 0;
                this.updateLayersFromKeyframes();
                this.syncPropertiesFromSelected();
                return true;
            }

            /* End: Son tick'e sar */
            if (context.isPressed(GLFW.GLFW_KEY_END))
            {
                this.currentFrame = this.maxFrames;
                this.updateLayersFromKeyframes();
                this.syncPropertiesFromSelected();
                return true;
            }

            /* Sol / Sağ ok: 1 tick geri / ileri, Shift ile 10 tick */
            if (!Window.isCtrlPressed() && !Window.isAltPressed() && this.handlePlayheadKeys(context))
            {
                return true;
            }

            /* Delete / Backspace: Seçili keyframe'i sil */
            if (context.isPressed(GLFW.GLFW_KEY_DELETE) || context.isPressed(GLFW.GLFW_KEY_BACKSPACE))
            {
                if (this.selectedKeyframe != null || (this.keyframesView != null && this.keyframesView.getGraph().getSelected() != null))
                {
                    if (this.keyframesView != null)
                    {
                        this.keyframesView.deleteSelectedKeyframes();
                    }
                    else if (this.selectedKeyframe != null)
                    {
                        this.removeKeyframe(this.selectedKeyframe);
                        this.selectedKeyframe = null;
                    }
                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.transformEditor != null)
        {
            this.transformEditor.getGesture().pump(context);
        }
        int topBarH = this.topBar != null && this.topBar.area.h > 0 ? this.topBar.area.h : UIPanelTopBar.HEIGHT;

        /* Render dark opaque backdrop when in editor view to prevent any transparent bleed-through */
        if (this.editor != null && this.editor.isVisible() && this.area.w > 0 && this.area.h > 0)
        {
            context.batcher.box(this.area.x, this.area.y + topBarH, this.area.ex(), this.area.ey(), 0xFF12141A);
        }

        /* Advance playback frame at ~60 FPS */
        if (this.isPlaying && this.currentScene != null)
        {
            long now = System.currentTimeMillis();
            if (this.lastTickTimeMs == 0L)
            {
                this.lastTickTimeMs = now;
            }
            long frameMs = Math.max(1L, 1000L / this.getFps());
            long delta = now - this.lastTickTimeMs;
            if (delta >= frameMs)
            {
                int step = Math.max(1, (int) (delta / frameMs));
                this.currentFrame += step;
                this.lastTickTimeMs = now;

                if (this.currentFrame >= this.maxFrames)
                {
                    if (this.loop)
                    {
                        this.currentFrame = 0;
                    }
                    else
                    {
                        this.currentFrame = this.maxFrames;
                        this.isPlaying = false;
                    }
                }

                this.updateLayersFromKeyframes();
                this.syncPropertiesFromSelected();
            }
        }
        else
        {
            this.lastTickTimeMs = 0L;
        }

        if (this.renderJob != null)
        {
            this.processRenderJob(context);
        }
        else
        {
            this.pollHistory();
        }

        super.render(context);

        this.drawRenderOverlay(context);
    }
}
