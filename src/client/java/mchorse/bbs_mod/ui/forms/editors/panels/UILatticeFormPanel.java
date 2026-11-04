package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.api.client.editor.FormEditorTool;
import mchorse.bbs_mod.cubic.render.LatticeDeform;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ILatticeForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueLattice;
import mchorse.bbs_mod.forms.renderers.LatticeGuide;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The lattice tab: a free-form deformation grid (2x2x2 or 3x3x3 control points) around the model or
 * one bone of it. A control point is picked by clicking it in the preview or from the list here, and
 * the gizmo moves it; the model bends with it as it is dragged. What bends the model is in
 * {@code CubicCubeRenderer} and {@code LatticeDeform}.
 */
public class UILatticeFormPanel<T extends Form & ILatticeForm> extends UIFormPanel<T> implements FormEditorTool
{
    /** How near a click has to be to a control point, in pixels */
    private static final float PICK_RADIUS = 12F;

    /** What the gizmo moves: the selected control point, as translation in the actor's frame; filled from the form, written back when it differs */
    private final Transform proxy = new Transform();
    private final Vector3f filled = new Vector3f();
    public UIPropTransform transform;

    private int selected;

    /* The preview's view of the lattice, as it last drew it: form space to clip space, and the area */
    private final Matrix4f view = new Matrix4f();
    private final Area viewArea = new Area();
    private boolean viewReady;

    public UIToggle enabled;
    public UICirculate dimensions;
    public UIButton target;
    public UIButton allModel;
    public UIButton reset;
    public UIButton squash;
    public UIButton stretch;
    public UIButton twist;
    public UITrackpad point;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad offsetZ;

    /** What the gizmo moves: a control point, the middle of the whole lattice, or the bulge sphere */
    public static final int GIZMO_POINT = 0;
    public static final int GIZMO_CENTER = 1;
    public static final int GIZMO_SPHERE = 2;

    public UICirculate gizmoTarget;
    public UIButton centerShift;
    public UIToggle bulgeEnabled;
    public UIButton bulgeCenter;
    public final UITrackpad[] bulgePads = new UITrackpad[5];

    public UILatticeFormPanel(UIForm editor)
    {
        super(editor);

        this.enabled = UIValues.toggle(key("enabled"), () -> this.lattice().enabled);
        this.enabled.tooltip(key("enabled_tooltip"));

        this.dimensions = new UICirculate((c) ->
        {
            this.lattice().setDimensions(c.getValue() == 0 ? 2 : 3);
            this.selected = MathUtils.clamp(this.selected, 0, this.lattice().count() - 1);
            this.syncSelection();
        });
        this.dimensions.addLabel(key("dimensions_2"));
        this.dimensions.addLabel(key("dimensions_3"));
        this.dimensions.tooltip(key("dimensions_tooltip"));

        this.target = new UIButton(IKey.EMPTY, (b) -> this.pickBone());
        this.target.tooltip(key("target_tooltip"));
        this.target.valueBinding(() ->
        {
            String bone = this.form == null ? "" : this.lattice().targetBone.get();

            this.target.label = bone == null || bone.isEmpty() ? key("target_all") : IKey.constant(bone);
        });
        this.allModel = new UIButton(key("target_clear"), (b) -> this.lattice().targetBone.set(""));
        this.allModel.tooltip(key("target_clear_tooltip"));
        this.reset = new UIButton(key("reset"), (b) ->
        {
            this.lattice().clear();
            this.syncSelection();
            UIUtils.playClick();
        });
        this.reset.tooltip(key("reset_tooltip"));

        this.squash = new UIButton(key("squash"), (b) -> this.preset(ValueLattice.PRESET_SQUASH));
        this.squash.tooltip(key("squash_tooltip"));
        this.stretch = new UIButton(key("stretch"), (b) -> this.preset(ValueLattice.PRESET_STRETCH));
        this.stretch.tooltip(key("stretch_tooltip"));
        this.twist = new UIButton(key("twist"), (b) -> this.preset(ValueLattice.PRESET_TWIST));
        this.twist.tooltip(key("twist_tooltip"));

        this.point = new UITrackpad((v) ->
        {
            this.selected = MathUtils.clamp((int) Math.round(v) - 1, 0, this.lattice().count() - 1);
            this.syncSelection();
        });
        this.point.integer().limit(1, 27).increment(1D).values(1D, 1D, 1D);
        this.point.tooltip(key("point_tooltip"));

        this.offsetX = new UITrackpad((v) -> this.writeOffset(0, v));
        this.offsetY = new UITrackpad((v) -> this.writeOffset(1, v));
        this.offsetZ = new UITrackpad((v) -> this.writeOffset(2, v));

        for (UITrackpad pad : new UITrackpad[] {this.offsetX, this.offsetY, this.offsetZ})
        {
            pad.increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
        }

        this.offsetX.tooltip(key("offset_x"));
        this.offsetY.tooltip(key("offset_y"));
        this.offsetZ.tooltip(key("offset_z"));

        /* What the pads show follows the value, a track moving it included */
        UITrackpad[] offsets = {this.offsetX, this.offsetY, this.offsetZ};

        for (int axis = 0; axis < 3; axis++)
        {
            final int a = axis;

            offsets[axis].valueBinding(() ->
            {
                if (this.form != null && !offsets[a].isDragging())
                {
                    float[] offset = this.lattice().get(this.selected);

                    if (offsets[a].getValue() != offset[a])
                    {
                        offsets[a].setValue(offset[a]);
                    }
                }
            });
        }

        this.gizmoTarget = new UICirculate((c) ->
        {
            this.syncSelection();
        });
        this.gizmoTarget.addLabel(key("gizmo_point"));
        this.gizmoTarget.addLabel(key("gizmo_center"));
        this.gizmoTarget.addLabel(key("gizmo_sphere"));
        this.gizmoTarget.tooltip(key("gizmo_tooltip"));

        this.centerShift = new UIButton(key("center_reset"), (b) ->
        {
            this.lattice().shiftX.set(0F);
            this.lattice().shiftY.set(0F);
            this.lattice().shiftZ.set(0F);
            this.syncSelection();
        });
        this.centerShift.tooltip(key("center_reset_tooltip"));

        this.bulgeEnabled = UIValues.toggle(key("bulge_enabled"), () -> this.lattice().bulgeEnabled);
        this.bulgeEnabled.tooltip(key("bulge_enabled_tooltip"));
        this.bulgeEnabled.callback = (b) -> this.setBulge(b.getValue());
        this.bulgeCenter = new UIButton(key("bulge_center"), (b) ->
        {
            Vector3f[] box = LatticeDeform.box(this.latticeForm());

            if (box != null)
            {
                float[] sphere = this.lattice().bulge.getOriginalValue().clone();

                sphere[0] = (box[0].x + box[1].x) / 2F;
                sphere[1] = (box[0].y + box[1].y) / 2F;
                sphere[2] = (box[0].z + box[1].z) / 2F;
                this.lattice().bulge.set(sphere);
                this.syncSelection();
            }
        });
        this.bulgeCenter.tooltip(key("bulge_center_tooltip"));

        for (int i = 0; i < 5; i++)
        {
            final int index = i;
            UITrackpad pad = new UITrackpad((v) -> this.writeBulge(index, v.floatValue()));

            pad.increment(i == 3 ? 0.05D : 0.0625D).values(i == 3 ? 0.05D : 0.0625D, 0.01D, 0.25D);
            pad.tooltip(key("bulge_" + new String[] {"x", "y", "z", "radius", "strength"}[i]));
            pad.valueBinding(() ->
            {
                if (this.form != null && !pad.isDragging())
                {
                    float[] sphere = this.lattice().bulge.get();

                    if (index < sphere.length && pad.getValue() != sphere[index])
                    {
                        pad.setValue(sphere[index]);
                    }
                }
            });
            this.bulgePads[i] = pad;
        }

        this.bulgePads[3].limit(0D, 16D);
        this.bulgePads[4].limit(-8D, 8D);

        this.transform = new UIPropTransform().callbacks(null, this::readProxy).noScale();
        this.transform.setRotationVisible(false);
        this.transform.setTransform(this.proxy);
        /* Only the gizmo works it, the pads above show the same; it sits in the tab so a drag has a context */
        this.transform.setVisible(false);

        this.options.add(
            this.enabled,
            UI.labelRow(key("dimensions"), this.dimensions).marginTop(UIConstants.SECTION_GAP),
            UI.label(key("target")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.target, this.allModel),
            this.reset.marginTop(UIConstants.SECTION_GAP),
            UI.label(key("presets")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.squash, this.stretch, this.twist),
            UI.label(key("points")).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(key("gizmo"), this.gizmoTarget),
            UI.labelRow(key("point"), this.point),
            UI.row(this.offsetX, this.offsetY, this.offsetZ),
            this.centerShift,
            UI.label(key("bulge")).marginTop(UIConstants.SECTION_GAP),
            this.bulgeEnabled,
            this.bulgeCenter,
            UI.row(this.bulgePads[0], this.bulgePads[1], this.bulgePads[2]),
            UI.row(this.bulgePads[3], this.bulgePads[4]),
            this.transform
        );
    }

    @Override
    public void startEdit(T form)
    {
        super.startEdit(form);

        ValueLattice lattice = ((ILatticeForm) form).getLattice();
        boolean model = form instanceof ModelForm;

        /* Only a model has bones to aim the lattice at; a block and an item are one piece */
        this.target.setEnabled(model);
        this.allModel.setEnabled(model);

        this.selected = MathUtils.clamp(this.selected, 0, lattice.count() - 1);
        this.dimensions.setValue(lattice.dimensions() == 2 ? 0 : 1);
        this.point.limit(1, lattice.count());
        this.syncSelection();
    }

    private ILatticeForm latticeForm()
    {
        return (ILatticeForm) this.form;
    }

    private ValueLattice lattice()
    {
        return ((ILatticeForm) this.form).getLattice();
    }

    /** One of the quick shapes, written over the whole lattice (and the lattice switched on, so it shows) */
    private void preset(int preset)
    {
        Vector3f[] box = this.form == null ? null : LatticeDeform.box(this.latticeForm());

        if (box == null)
        {
            return;
        }

        this.lattice().enabled.set(true);
        this.enabled.setValue(true);
        this.lattice().applyPreset(preset, box[0], box[1]);
        this.syncSelection();
        UIUtils.playClick();
    }

    /** The selected control point's index, for the preview to draw it larger */
    public int getSelected()
    {
        return this.selected;
    }

    /** Show the selected control point in the pads and hand it to the gizmo */
    private void syncSelection()
    {
        if (this.form == null)
        {
            return;
        }

        this.point.limit(1, this.lattice().count());
        this.point.setValue(this.selected + 1);
        this.fillProxy();
        this.transform.refreshFields();
    }

    private void writeOffset(int axis, double value)
    {
        if (this.form == null)
        {
            return;
        }

        float[] offset = this.lattice().get(this.selected);

        offset[axis] = (float) value;
        this.lattice().set(this.selected, offset[0], offset[1], offset[2]);
        this.fillProxy();
        this.transform.refreshFields();
    }

    /* The gizmo */

    /** Which of the three the gizmo is on */
    public int getGizmoMode()
    {
        return this.gizmoTarget.getValue();
    }

    private Vector3f position()
    {
        int mode = this.getGizmoMode();

        if (mode == GIZMO_SPHERE)
        {
            float[] sphere = this.lattice().bulge.get();

            return new Vector3f(sphere[0], sphere[1], sphere[2]);
        }

        if (mode == GIZMO_CENTER)
        {
            Vector3f[] box = LatticeDeform.box(this.latticeForm());

            return box == null ? null : new Vector3f(box[0]).add(box[1]).mul(0.5F);
        }

        Vector3f rest = LatticeGuide.rest(this.latticeForm(), this.selected);
        float[] offset = this.lattice().get(this.selected);

        return rest == null ? null : rest.add(offset[0], offset[1], offset[2]);
    }

    private void fillProxy()
    {
        Vector3f position = this.form == null ? null : this.position();

        if (position == null)
        {
            return;
        }

        this.filled.set(position);
        this.proxy.setModeEuler();
        this.proxy.translate.set(position);
        this.proxy.rotate.set(0F, 0F, 0F);
        this.proxy.scale.set(1F, 1F, 1F);
    }

    /** The bulge sphere: one of its numbers, and the sphere switched on the first time (put in the middle of the lattice) */
    private void writeBulge(int index, float value)
    {
        if (this.form == null)
        {
            return;
        }

        float[] sphere = this.lattice().bulge.getOriginalValue().clone();

        sphere[index] = value;
        this.lattice().bulge.set(sphere);
        this.fillProxy();
        this.transform.refreshFields();
    }

    private void setBulge(boolean on)
    {
        if (this.form == null)
        {
            return;
        }

        this.lattice().bulgeEnabled.set(on);

        float[] sphere = this.lattice().bulge.getOriginalValue();

        /* Switched on with no strength: a push to start from, so there is something to see */
        if (on && sphere[4] == 0F)
        {
            this.bulgeCenter.callback.accept(this.bulgeCenter);
            this.writeBulge(4, 0.25F);
        }
    }

    /** A drag of the gizmo, into the thing it moves */
    private void readProxy()
    {
        if (this.form == null || this.proxy.translate.distanceSquared(this.filled) <= 1.0E-12F)
        {
            return;
        }

        Vector3f moved = new Vector3f(this.proxy.translate);
        int mode = this.getGizmoMode();

        if (mode == GIZMO_SPHERE)
        {
            float[] sphere = this.lattice().bulge.getOriginalValue().clone();

            sphere[0] = moved.x;
            sphere[1] = moved.y;
            sphere[2] = moved.z;
            this.lattice().bulge.set(sphere);
        }
        else if (mode == GIZMO_CENTER)
        {
            /* The whole cage goes with it: its box moves by how far the middle was dragged */
            Vector3f by = new Vector3f(moved).sub(this.filled);

            this.lattice().shiftX.set(this.lattice().shiftX.get() + by.x);
            this.lattice().shiftY.set(this.lattice().shiftY.get() + by.y);
            this.lattice().shiftZ.set(this.lattice().shiftZ.get() + by.z);
        }
        else
        {
            Vector3f rest = LatticeGuide.rest(this.latticeForm(), this.selected);

            if (rest == null)
            {
                return;
            }

            this.lattice().set(this.selected, moved.x - rest.x, moved.y - rest.y, moved.z - rest.z);
        }

        this.filled.set(moved);
    }

    @Override
    public UIPropTransform getGizmoTransform()
    {
        if (this.form == null)
        {
            return null;
        }

        int mode = this.getGizmoMode();

        if (mode == GIZMO_SPHERE ? !this.lattice().bulgeEnabled.get() : !this.lattice().enabled.get())
        {
            return null;
        }

        Vector3f position = this.position();

        if (position == null)
        {
            return null;
        }

        /* Asked every frame the gizmo is drawn: carry a drag to the form, or the form to the proxy */
        if (this.proxy.translate.distanceSquared(this.filled) > 1.0E-12F)
        {
            this.readProxy();
        }
        else if (!this.transform.isEditing() && position.distanceSquared(this.filled) > 1.0E-10F)
        {
            this.fillProxy();
            this.transform.refreshFields();
        }

        return this.transform;
    }

    @Override
    public Matrix4f getGizmoOrigin(float transition, TransformSpace space)
    {
        if (this.getGizmoTransform() == null)
        {
            return null;
        }

        Matrix4f frame = this.editor.getBodyPartGizmoOrigin(transition, TransformSpace.LOCAL);
        Vector3f position = this.position();

        if (frame == null || frame == Matrices.EMPTY_4F || position == null)
        {
            return null;
        }

        return new Matrix4f(frame).translate(position);
    }

    /* Picking a control point in the preview */

    /** The preview hands over its view as it draws: form space to clip space, and the area it is drawn in */
    public void setView(Matrix4f matrix, Area area)
    {
        this.view.set(matrix);
        this.viewArea.copy(area);
        this.viewReady = true;
    }

    /** A click in the preview: the control point under it becomes the selected one. False when none is, or it is the selected one (the gizmo's) */
    public boolean pickPoint(UIContext context)
    {
        if (context.mouseButton != 0 || this.getGizmoMode() != GIZMO_POINT || !this.viewReady || this.form == null || !this.lattice().enabled.get() || !this.viewArea.isInside(context))
        {
            return false;
        }

        Vector3f[] points = LatticeGuide.points(this.latticeForm());

        if (points == null)
        {
            return false;
        }

        int best = -1;
        float distance = PICK_RADIUS * PICK_RADIUS;

        for (int n = 0; n < points.length; n++)
        {
            Vector4f clip = this.view.transform(new Vector4f(points[n].x, points[n].y, points[n].z, 1F));

            if (clip.w <= 0F)
            {
                continue;
            }

            float x = this.viewArea.x + (clip.x / clip.w * 0.5F + 0.5F) * this.viewArea.w;
            float y = this.viewArea.y + (1F - (clip.y / clip.w * 0.5F + 0.5F)) * this.viewArea.h;
            float d = (x - context.mouseX) * (x - context.mouseX) + (y - context.mouseY) * (y - context.mouseY);

            if (d <= distance)
            {
                distance = d;
                best = n;
            }
        }

        if (best < 0 || best == this.selected)
        {
            return false;
        }

        this.selected = best;
        this.syncSelection();

        return true;
    }

    /* The target bone */

    private void pickBone()
    {
        if (this.form == null)
        {
            return;
        }

        if (!(this.form instanceof ModelForm))
        {
            return;
        }

        List<String> bones = new ArrayList<>(FormUtilsClient.getBones(this.form));

        bones.sort(String.CASE_INSENSITIVE_ORDER);

        UIOverlay.addOverlay(this.getContext(), new UIStringOverlayPanel(key("target_pick"), true, bones, (bone) -> this.lattice().targetBone.set(bone == null ? "" : bone)), 240, 0.6F);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.lattice." + id);
    }
}
