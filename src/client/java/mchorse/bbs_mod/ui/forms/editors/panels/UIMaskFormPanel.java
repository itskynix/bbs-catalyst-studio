package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.api.client.editor.FormEditorTool;
import mchorse.bbs_mod.forms.FormMasking;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import mchorse.bbs_mod.forms.forms.utils.FormMask;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.forms.editors.utils.MaskEditing;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

/**
 * The mask a form is drawn through, on a tab of its own: the shape, its soft edge and inversion,
 * and the shape's placement in the form's space. While the form is edited, the viewport draws the
 * shape's outline and handles (see {@code FormMasking#renderGuide}); a film that plays or is
 * exported never does.
 */
public class UIMaskFormPanel extends UIFormPanel implements FormEditorTool
{
    /**
     * What the gizmo moves while the form is sliced: the plane's middle as translation and its
     * pitch, yaw and roll as rotation. It is filled from the form before every use and read back
     * after every change, where the translation counts only along the plane's normal - the plane's
     * offset - and is put back on the normal.
     */
    private final Transform sliceProxy = new Transform();

    /* What the proxy was last filled with: a change is what differs from it, and only a change is
     * written back - a callback that changed nothing must not overwrite what the pads or the
     * keyframes set */
    private final Vector3f filledTranslate = new Vector3f();
    private final Vector3f filledRotate = new Vector3f();
    public UIPropTransform sliceTransform;

    public UICirculate type;
    public UIToggle invert;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad offsetZ;
    public UITrackpad width;
    public UITrackpad height;
    public UITrackpad radius;
    public UITrackpad rotationX;
    public UITrackpad rotationY;
    public UITrackpad rotationZ;
    /** The mask's own animation in its plane: slide and stretch, tracks of the timeline */
    public UITrackpad posX;
    public UITrackpad posY;
    public UITrackpad scaleX;
    public UITrackpad scaleY;
    public UIElement animation;
    public UIToggle pen;
    public UIButton deletePoint;
    public UIButton clearPoints;

    public UICirculate mode;
    public UISection shape;
    public UISection transform;
    public UISection slice;

    public UIMaskFormPanel(UIForm editor)
    {
        super(editor);

        this.type = new UICirculate((b) ->
        {
            this.form.mask.type.set(b.getValue());
            this.updateVisibility();
        });
        this.type.addLabel(UIKeys.FORMS_EDITORS_GENERAL_MASK_NONE);
        this.type.addLabel(UIKeys.FORMS_EDITORS_GENERAL_MASK_CIRCLE);
        this.type.addLabel(UIKeys.FORMS_EDITORS_GENERAL_MASK_RECTANGLE);
        this.type.addLabel(UIKeys.FORMS_EDITORS_GENERAL_MASK_CUSTOM);

        this.invert = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_MASK_INVERT, () -> this.form.mask.invert);
        this.offsetX = UIValues.trackpad(() -> this.form.mask.offsetX);
        this.offsetX.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_OFFSET_X);
        this.offsetY = UIValues.trackpad(() -> this.form.mask.offsetY);
        this.offsetY.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_OFFSET_Y);
        this.offsetZ = UIValues.trackpad(() -> this.form.mask.offsetZ);
        this.offsetZ.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_OFFSET_Z);
        this.width = UIValues.trackpad(() -> this.form.mask.width);
        this.width.limit(0.001D, 1000D).tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_WIDTH);
        this.height = UIValues.trackpad(() -> this.form.mask.height);
        this.height.limit(0.001D, 1000D).tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_HEIGHT);
        this.radius = UIValues.trackpad(() -> this.form.mask.radius);
        this.radius.limit(0.001D, 1000D).tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_RADIUS);
        this.rotationX = UIValues.trackpad(() -> this.form.maskRotationX);
        this.rotationX.limit(-360D, 360D).tooltip(UIKeys.FORMS_EDITORS_MASK_ROTATION_X);
        this.rotationY = UIValues.trackpad(() -> this.form.maskRotationY);
        this.rotationY.limit(-360D, 360D).tooltip(UIKeys.FORMS_EDITORS_MASK_ROTATION_Y);
        this.rotationZ = UIValues.trackpad(() -> this.form.maskRotationZ);
        this.rotationZ.limit(-360D, 360D).tooltip(UIKeys.FORMS_EDITORS_GENERAL_MASK_ROTATION);
        this.posX = UIValues.trackpad(() -> this.form.maskPosX);
        this.posX.tooltip(UIKeys.FORMS_EDITORS_MASK_POS_X);
        this.posY = UIValues.trackpad(() -> this.form.maskPosY);
        this.posY.tooltip(UIKeys.FORMS_EDITORS_MASK_POS_Y);
        this.scaleX = UIValues.trackpad(() -> this.form.maskScaleX);
        this.scaleX.values(0.1D, 0.01D, 0.5D).tooltip(UIKeys.FORMS_EDITORS_MASK_SCALE_X);
        this.scaleY = UIValues.trackpad(() -> this.form.maskScaleY);
        this.scaleY.values(0.1D, 0.01D, 0.5D).tooltip(UIKeys.FORMS_EDITORS_MASK_SCALE_Y);
        this.animation = UI.column(
            UI.label(UIKeys.FORMS_EDITORS_MASK_ANIMATION).marginTop(6),
            UI.row(this.posX, this.posY),
            UI.row(this.scaleX, this.scaleY)
        );
        this.animation.tooltip(UIKeys.FORMS_EDITORS_MASK_ANIMATION_TOOLTIP);
        this.pen = new UIToggle(UIKeys.FORMS_EDITORS_MASK_PEN, (b) -> MaskEditing.pen = b.getValue());
        this.pen.tooltip(UIKeys.FORMS_EDITORS_MASK_PEN_TOOLTIP);
        this.deletePoint = new UIButton(UIKeys.FORMS_EDITORS_MASK_DELETE_POINT, (b) -> MaskEditing.removeSelected(this.form.mask));
        this.clearPoints = new UIButton(UIKeys.FORMS_EDITORS_MASK_CLEAR_POINTS, (b) ->
        {
            this.form.mask.points.set("");
            MaskEditing.selected = -1;
        });

        this.mode = new UICirculate((b) ->
        {
            this.form.mask.mode.set(b.getValue());
            this.updateVisibility();
        });
        this.mode.addLabel(UIKeys.FORMS_EDITORS_MASK_MODE_SCREEN);
        this.mode.addLabel(UIKeys.FORMS_EDITORS_MASK_MODE_SLICE);
        this.mode.tooltip(UIKeys.FORMS_EDITORS_MASK_MODE_TOOLTIP);

        this.sliceTransform = new UIPropTransform().callbacks(null, this::readSliceProxy).noScale();
        this.sliceTransform.setTransform(this.sliceProxy);

        this.slice = new UISection(UIKeys.FORMS_EDITORS_MASK_SLICE);
        this.slice.fields.add(
            UIValues.toggle(UIKeys.FORMS_EDITORS_MASK_SLICE_INVERT, () -> this.form.sliceInvert),
            this.sliceTransform,
            new UIText(UIKeys.FORMS_EDITORS_MASK_SLICE_NOTE).padding(0, 0).lineHeight(10)
        );

        this.shape = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_MASK);
        UISection shape = this.shape;

        shape.fields.add(
            this.type, this.invert
        );

        this.transform = new UISection(UIKeys.FORMS_EDITORS_MASK_TRANSFORM);
        UISection transform = this.transform;

        transform.fields.add(
            UI.row(this.offsetX, this.offsetY, this.offsetZ),
            UI.row(this.width, this.height),
            this.radius,
            UI.row(this.rotationX, this.rotationY, this.rotationZ),
            this.animation,
            this.pen,
            UI.row(this.deletePoint, this.clearPoints)
        );

        this.options.add(UI.labelRow(UIKeys.FORMS_EDITORS_MASK_MODE, this.mode), shape, transform, this.slice);
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        MaskEditing.reset();
        this.pen.setValue(false);
        this.mode.setValue(form.mask.mode.get());
        this.fillSliceProxy();
        this.sliceTransform.refreshFields();
        this.type.setValue(form.mask.type.get());
        this.updateVisibility();
    }

    private void fillSliceProxy()
    {
        if (this.form == null)
        {
            return;
        }

        this.filledTranslate.set(FormMasking.sliceCenter(this.form));
        this.filledRotate.set(this.formRotation());

        this.sliceProxy.setModeEuler();
        this.sliceProxy.translate.set(this.filledTranslate);
        this.sliceProxy.rotate.set(this.filledRotate);
        this.sliceProxy.scale.set(1F, 1F, 1F);
    }

    /** The slice's pitch, yaw and roll as the proxy holds them: radians, any number of turns. */
    private Vector3f formRotation()
    {
        return new Vector3f(
            (float) Math.toRadians(this.form.slicePitch.get()),
            (float) Math.toRadians(this.form.sliceYaw.get()),
            (float) Math.toRadians(this.form.sliceRoll.get())
        );
    }

    /** Whether the form holds something else than the proxy was filled with: the tracks moved it. */
    private boolean isSliceProxyStale()
    {
        return FormMasking.sliceCenter(this.form).distanceSquared(this.filledTranslate) > 1.0E-10F
            || this.formRotation().distanceSquared(this.filledRotate) > 1.0E-10F;
    }

    /** Whether the proxy holds something else than it was filled with: the pads or the gizmo moved it. */
    private boolean isSliceProxyChanged()
    {
        return this.sliceProxy.translate.distanceSquared(this.filledTranslate) > 1.0E-12F
            || this.sliceProxy.getEulerRotation(new Vector3f()).distanceSquared(this.filledRotate) > 1.0E-12F;
    }

    /** What the proxy was moved to goes to the form: the point as it is, the angles in degrees, unwrapped. */
    private void readSliceProxy()
    {
        if (this.form == null || !this.isSliceProxyChanged())
        {
            return;
        }

        Vector3f translate = this.sliceProxy.translate;
        Vector3f rotate = this.sliceProxy.getEulerRotation(new Vector3f());

        this.form.slicePosX.set(translate.x);
        this.form.slicePosY.set(translate.y);
        this.form.slicePosZ.set(translate.z);
        this.form.slicePitch.set((float) Math.toDegrees(rotate.x));
        this.form.sliceYaw.set((float) Math.toDegrees(rotate.y));
        this.form.sliceRoll.set((float) Math.toDegrees(rotate.z));

        this.filledTranslate.set(translate);
        this.filledRotate.set(rotate);
    }

    @Override
    public UIPropTransform getGizmoTransform()
    {
        if (this.form == null || !this.form.mask.isSlicing())
        {
            return null;
        }

        /* Asked every frame the editor draws its gizmo, which makes this the place to carry changes
         * either way, whatever path they came by: a drag that moved the proxy without a callback
         * reaches the form here, and the tracks moving the form reach the proxy. The proxy wins
         * when both moved - it is what the hand is on. */
        if (this.isSliceProxyChanged())
        {
            this.readSliceProxy();
        }
        else if (!this.sliceTransform.isEditing() && this.isSliceProxyStale())
        {
            this.fillSliceProxy();
            this.sliceTransform.refreshFields();
        }

        return this.sliceTransform;
    }

    @Override
    public Matrix4f getGizmoOrigin(float transition, TransformSpace space)
    {
        if (this.form == null || !this.form.mask.isSlicing())
        {
            return null;
        }

        Matrix4f frame = this.editor.getBodyPartGizmoOrigin(transition, TransformSpace.LOCAL);

        if (frame == null || frame == Matrices.EMPTY_4F)
        {
            return null;
        }

        Matrix4f origin = new Matrix4f(frame).translate(FormMasking.sliceCenter(this.form));

        if (space.placesOnOwnFrame())
        {
            origin.mul(new Matrix4f().set(FormMasking.sliceRotation(this.form)));
        }

        return origin;
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        MaskEditing.reset();
    }

    /** Each shape shows only the fields it reads. */
    private void updateVisibility()
    {
        int type = this.form == null ? FormMask.NONE : this.form.mask.type.get();
        boolean shaped = type != FormMask.NONE;
        boolean slicing = this.form != null && this.form.mask.isSlicing();

        this.shape.setVisible(!slicing);
        this.transform.setVisible(!slicing);
        this.slice.setVisible(slicing);

        this.invert.setVisible(shaped);
        this.offsetX.setVisible(shaped);
        this.offsetY.setVisible(shaped);
        this.offsetZ.setVisible(shaped);
        this.rotationX.setVisible(shaped);
        this.rotationY.setVisible(shaped);
        this.rotationZ.setVisible(shaped);
        this.animation.setVisible(shaped);
        this.radius.setVisible(type == FormMask.CIRCLE);
        this.width.setVisible(type == FormMask.RECTANGLE);
        this.height.setVisible(type == FormMask.RECTANGLE);
        this.pen.setVisible(type == FormMask.CUSTOM);
        this.deletePoint.setVisible(type == FormMask.CUSTOM);
        this.clearPoints.setVisible(type == FormMask.CUSTOM);

        this.options.resize();
    }
}
