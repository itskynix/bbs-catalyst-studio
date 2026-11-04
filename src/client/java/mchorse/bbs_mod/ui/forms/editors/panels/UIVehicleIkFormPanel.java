package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.api.client.editor.FormEditorTool;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueVehicleParts;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.VehicleBoneDetector;
import mchorse.bbs_mod.forms.renderers.VehicleCameraGuide;
import mchorse.bbs_mod.forms.renderers.VehicleGeometry;
import mchorse.bbs_mod.forms.renderers.VehicleIkState;
import mchorse.bbs_mod.forms.renderers.VehicleSeats;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The vehicle IK tab: switch the simulation on, let it find the body, steering wheel and four wheels
 * by name (or pick each from the model's bones), and tune how the wheels roll, steer and how the
 * body answers braking and corners; then the parts that open, the four seats, the driver's camera
 * and the dashboard. What it drives is in {@code VehicleIkState}, the seats in {@code VehicleSeats}.
 */
public class UIVehicleIkFormPanel extends UIFormPanel<ModelForm> implements FormEditorTool
{
    /** What the gizmo moves: the driver's camera, or one of the four seats (1 to 4) */
    private static final int GIZMO_CAMERA = 0;

    /**
     * What the gizmo moves while the driver camera is placed by hand: the eye as translation, its
     * pitch and turn as rotation (radians, the turn negated - see {@code VehicleCameraGuide#rotation}).
     * Filled from the form, and only what differs from the fill is written back, as the mask does.
     */
    private final Transform cameraProxy = new Transform();
    private final Vector3f filledTranslate = new Vector3f();
    private final Vector3f filledRotate = new Vector3f();
    public UIPropTransform cameraTransform;

    /** The same for a seat set by hand: its sitting plane's middle, moved only (a seat has no turn of its own) */
    private final Transform seatProxy = new Transform();
    private final Vector3f filledSeat = new Vector3f();
    public UIPropTransform seatTransform;

    public UIToggle enabled;
    public UIButton detect;
    public UITrackpad wheelRadius;
    public UITrackpad suspension;
    public UITrackpad steering;
    public UIToggle invert;
    public UIButton detectParts;
    public UIButton addPart;
    public UIElement parts;
    public UITrackpad stepHeight;
    public UIToggle replayPitch;
    public UIToggle ground;

    public UIToggle seatsCustom;
    public UIButton fillSeats;
    public UITrackpad seatHeight;
    public UIElement seats;
    public final UIToggle[] seatToggles = new UIToggle[ModelForm.SEATS.length];
    public final UITrackpad[][] seatPads = new UITrackpad[ModelForm.SEATS.length][3];

    public UIToggle driverCamera;
    public UITrackpad cameraX;
    public UITrackpad cameraY;
    public UITrackpad cameraZ;
    public UITrackpad cameraYaw;
    public UITrackpad cameraPitch;
    public UICirculate gizmo;

    public UIToggle driverHud;
    public UIToggle passengerHud;
    public UIToggle debug;

    public UIVehicleIkFormPanel(UIForm editor)
    {
        super(editor);

        this.enabled = UIValues.toggle(key("enabled"), () -> this.form.vehicleIk);
        this.detect = new UIButton(key("detect"), (b) -> this.detect());
        this.detect.tooltip(key("detect_tooltip"));

        this.wheelRadius = UIValues.trackpad(() -> this.form.wheelRadius).limit(0.05D, 4D).increment(0.05D).values(0.05D, 0.01D, 0.25D);
        this.wheelRadius.tooltip(key("wheel_radius_tooltip"));
        this.suspension = UIValues.trackpad(() -> this.form.suspensionStrength).limit(0D, 4D).increment(0.1D).values(0.1D, 0.01D, 0.5D);
        this.steering = UIValues.trackpad(() -> this.form.steeringMultiplier).limit(0D, 4D).increment(0.1D).values(0.1D, 0.01D, 0.5D);
        this.invert = UIValues.toggle(key("invert"), () -> this.form.invertWheelRoll);

        this.detectParts = new UIButton(key("parts_detect"), (b) -> this.detectParts());
        this.detectParts.tooltip(key("parts_detect_tooltip"));
        this.addPart = new UIButton(key("parts_add"), (b) -> this.pickBone((bone) ->
        {
            Integer axis = VehicleBoneDetector.partAxis(bone);

            this.addPart(bone, axis == null ? ValueVehicleParts.AXIS_YAW : axis);
            this.rebuildParts();
        }));
        this.parts = UI.column();
        this.stepHeight = UIValues.trackpad(() -> this.form.stepHeight).limit(0D, 1.25D).increment(0.05D).values(0.05D, 0.01D, 0.25D);
        this.stepHeight.tooltip(key("step_height_tooltip"));
        this.replayPitch = UIValues.toggle(key("replay_pitch"), () -> this.form.pitchFromReplay);
        this.replayPitch.tooltip(key("replay_pitch_tooltip"));
        this.ground = UIValues.toggle(key("ground"), () -> this.form.groundSettle);
        this.ground.tooltip(key("ground_tooltip"));

        /* The seats: found from the model, or set by hand one by one */
        this.seatsCustom = UIValues.toggle(key("seats_custom"), () -> this.form.seatsCustom);
        this.seatsCustom.callback = (b) -> this.setSeatsCustom(b.getValue());
        this.seatsCustom.tooltip(key("seats_custom_tooltip"));
        this.fillSeats = new UIButton(key("seats_fill"), (b) -> this.fillSeats());
        this.fillSeats.tooltip(key("seats_fill_tooltip"));
        this.seatHeight = UIValues.trackpad(() -> this.form.seatHeight).limit(-2D, 2D).increment(0.05D).values(0.05D, 0.01D, 0.25D);
        this.seatHeight.tooltip(key("seat_height_tooltip"));
        this.seats = UI.column();

        for (int i = 0; i < ModelForm.SEATS.length; i++)
        {
            final int seat = i;

            this.seatToggles[i] = UIValues.toggle(key("seat_" + ModelForm.SEATS[i]), () -> this.form.seatEnabled[seat]);
            this.seatToggles[i].tooltip(key("seat_enabled_tooltip"));
            this.seatPads[i][0] = UIValues.trackpad(() -> this.form.seatX[seat]).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
            this.seatPads[i][1] = UIValues.trackpad(() -> this.form.seatY[seat]).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
            this.seatPads[i][2] = UIValues.trackpad(() -> this.form.seatZ[seat]).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
            this.seatPads[i][0].tooltip(key("seat_x"));
            this.seatPads[i][1].tooltip(key("seat_y"));
            this.seatPads[i][2].tooltip(key("seat_z"));
            this.seats.add(this.seatToggles[i], UI.row(this.seatPads[i][0], this.seatPads[i][1], this.seatPads[i][2]));
        }

        this.driverCamera = UIValues.toggle(key("camera_enabled"), () -> this.form.driverCamera);
        this.driverCamera.tooltip(key("camera_enabled_tooltip"));
        this.cameraX = UIValues.trackpad(() -> this.form.driverCameraX).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
        this.cameraY = UIValues.trackpad(() -> this.form.driverCameraY).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
        this.cameraZ = UIValues.trackpad(() -> this.form.driverCameraZ).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);
        this.cameraYaw = UIValues.trackpad(() -> this.form.driverCameraYaw).increment(5D).values(5D, 1D, 15D);
        this.cameraPitch = UIValues.trackpad(() -> this.form.driverCameraPitch).increment(5D).values(5D, 1D, 15D);
        this.cameraX.tooltip(key("camera_x"));
        this.cameraY.tooltip(key("camera_y"));
        this.cameraZ.tooltip(key("camera_z"));
        this.cameraYaw.tooltip(key("camera_yaw"));
        this.cameraPitch.tooltip(key("camera_pitch"));

        /* Which of them the gizmo in the preview moves; a seat found from the model is set by hand once moved */
        this.gizmo = new UICirculate((c) -> this.pickGizmo(c.getValue()));
        this.gizmo.addLabel(key("gizmo_camera"));

        for (String seat : ModelForm.SEATS)
        {
            this.gizmo.addLabel(key("seat_" + seat));
        }

        this.gizmo.tooltip(key("gizmo_tooltip"));

        this.cameraTransform = new UIPropTransform().callbacks(null, this::readCameraProxy).noScale();
        this.cameraTransform.setTransform(this.cameraProxy);
        /* Only the gizmo works these two, the pads above show the same; they sit in the tab so a drag has a context */
        this.cameraTransform.setVisible(false);
        this.seatTransform = new UIPropTransform().callbacks(null, this::readSeatProxy).noScale();
        this.seatTransform.setTransform(this.seatProxy);
        this.seatTransform.setRotationVisible(false);
        this.seatTransform.setVisible(false);

        this.driverHud = UIValues.toggle(key("driver_hud"), () -> this.form.driverHud);
        this.driverHud.tooltip(key("driver_hud_tooltip"));
        this.passengerHud = UIValues.toggle(key("passenger_hud"), () -> this.form.passengerHud);
        this.passengerHud.tooltip(key("passenger_hud_tooltip"));

        /* A switch for the session, not a property of the form: never saved or keyed */
        this.debug = new UIToggle(key("debug"), (b) -> VehicleIkState.debug = b.getValue());
        this.debug.setValue(VehicleIkState.debug);
        this.debug.tooltip(key("debug_tooltip"));

        this.options.add(
            this.enabled,
            this.detect.marginTop(UIConstants.SECTION_GAP),
            UI.label(key("bones")).marginTop(UIConstants.SECTION_GAP),
            this.bone(key("bone_body"), () -> this.form.boneBody),
            this.bone(key("bone_steering"), () -> this.form.boneSteering),
            this.bone(key("bone_fl"), () -> this.form.boneWheelFL),
            this.bone(key("bone_fr"), () -> this.form.boneWheelFR),
            this.bone(key("bone_rl"), () -> this.form.boneWheelRL),
            this.bone(key("bone_rr"), () -> this.form.boneWheelRR),
            UI.label(key("physics")).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(key("wheel_radius"), this.wheelRadius),
            UI.labelRow(key("suspension"), this.suspension),
            UI.labelRow(key("steering"), this.steering),
            this.invert,
            UI.labelRow(key("step_height"), this.stepHeight),
            this.replayPitch,
            this.ground,
            UI.label(key("parts")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.detectParts, this.addPart),
            this.parts,
            UI.label(key("seats")).marginTop(UIConstants.SECTION_GAP),
            this.seatsCustom,
            this.fillSeats,
            this.seats,
            UI.labelRow(key("seat_height"), this.seatHeight),
            UI.label(key("camera")).marginTop(UIConstants.SECTION_GAP),
            this.driverCamera,
            UI.row(this.cameraX, this.cameraY, this.cameraZ),
            UI.row(this.cameraYaw, this.cameraPitch),
            UI.labelRow(key("gizmo"), this.gizmo),
            this.cameraTransform,
            this.seatTransform,
            UI.label(key("hud")).marginTop(UIConstants.SECTION_GAP),
            this.driverHud,
            this.passengerHud,
            this.debug.marginTop(UIConstants.SECTION_GAP)
        );
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        this.rebuildParts();
        this.fillCameraProxy();
        this.cameraTransform.refreshFields();
        this.fillSeatProxy();
        this.seatTransform.refreshFields();
        this.updateSeats();
    }

    /** The seat the gizmo is on, for the preview to draw brighter; -1 when it is on the camera */
    public int getGizmoSeat()
    {
        return this.gizmo.getValue() - 1;
    }

    /* The seats */

    private void setSeatsCustom(boolean custom)
    {
        if (this.form == null)
        {
            return;
        }

        /* Set by hand for the first time: from where the model has them, not from nowhere */
        if (custom && this.isSeatsUntouched())
        {
            this.writeFoundSeats();
        }

        this.form.seatsCustom.set(custom);
        this.updateSeats();
    }

    private boolean isSeatsUntouched()
    {
        for (int i = 0; i < ModelForm.SEATS.length; i++)
        {
            if (!this.form.seatX[i].isDefault() || !this.form.seatY[i].isDefault() || !this.form.seatZ[i].isDefault() || !this.form.seatEnabled[i].isDefault())
            {
                return false;
            }
        }

        return true;
    }

    private void fillSeats()
    {
        if (this.form == null)
        {
            return;
        }

        this.writeFoundSeats();
        this.form.seatsCustom.set(true);
        this.seatsCustom.setValue(true);
        this.updateSeats();
        this.getContext().notifyInfo(key("seats_fill_done"));
        UIUtils.playClick();
    }

    /** The seats the model gives, into the ones set by hand: a seat it has none for is switched off */
    private void writeFoundSeats()
    {
        VehicleSeats.Layout found = VehicleSeats.found(this.form);

        for (int i = 0; i < ModelForm.SEATS.length; i++)
        {
            Vector3f seat = found.seats[i];

            this.form.seatEnabled[i].set(seat != null);

            if (seat != null)
            {
                this.form.seatX[i].set(seat.x);
                this.form.seatY[i].set(seat.y);
                this.form.seatZ[i].set(seat.z);
            }
        }

        this.fillSeatProxy();
        this.seatTransform.refreshFields();
    }

    /** The seat rows only mean something while the seats are set by hand */
    private void updateSeats()
    {
        boolean custom = this.form != null && this.form.seatsCustom.get();

        this.seats.setVisible(custom);
        this.options.resize();
    }

    private void pickGizmo(int target)
    {
        if (this.form != null && target != GIZMO_CAMERA && !this.form.seatsCustom.get())
        {
            this.setSeatsCustom(true);
            this.seatsCustom.setValue(true);
        }

        this.fillSeatProxy();
        this.seatTransform.refreshFields();
    }

    private Vector3f seatPoint(int seat)
    {
        return new Vector3f(this.form.seatX[seat].get(), this.form.seatY[seat].get(), this.form.seatZ[seat].get());
    }

    private void fillSeatProxy()
    {
        int seat = this.getGizmoSeat();

        if (this.form == null || seat < 0)
        {
            return;
        }

        this.filledSeat.set(this.seatPoint(seat));
        this.seatProxy.setModeEuler();
        this.seatProxy.translate.set(this.filledSeat);
        this.seatProxy.rotate.set(0F, 0F, 0F);
        this.seatProxy.scale.set(1F, 1F, 1F);
    }

    private void readSeatProxy()
    {
        int seat = this.getGizmoSeat();

        if (this.form == null || seat < 0 || this.seatProxy.translate.distanceSquared(this.filledSeat) <= 1.0E-12F)
        {
            return;
        }

        Vector3f translate = this.seatProxy.translate;

        this.form.seatX[seat].set(translate.x);
        this.form.seatY[seat].set(translate.y);
        this.form.seatZ[seat].set(translate.z);
        this.filledSeat.set(translate);
        this.seatPads[seat][0].setValue(translate.x);
        this.seatPads[seat][1].setValue(translate.y);
        this.seatPads[seat][2].setValue(translate.z);
    }

    /* The driver camera through the gizmo */

    private Vector3f cameraEye()
    {
        return new Vector3f(this.form.driverCameraX.get(), this.form.driverCameraY.get(), this.form.driverCameraZ.get());
    }

    private Vector3f cameraAngles()
    {
        return new Vector3f((float) Math.toRadians(this.form.driverCameraPitch.get()), (float) -Math.toRadians(this.form.driverCameraYaw.get()), 0F);
    }

    private void fillCameraProxy()
    {
        if (this.form == null)
        {
            return;
        }

        this.filledTranslate.set(this.cameraEye());
        this.filledRotate.set(this.cameraAngles());

        this.cameraProxy.setModeEuler();
        this.cameraProxy.translate.set(this.filledTranslate);
        this.cameraProxy.rotate.set(this.filledRotate);
        this.cameraProxy.scale.set(1F, 1F, 1F);
    }

    private boolean isCameraProxyChanged()
    {
        return this.cameraProxy.translate.distanceSquared(this.filledTranslate) > 1.0E-12F
            || this.cameraProxy.getEulerRotation(new Vector3f()).distanceSquared(this.filledRotate) > 1.0E-12F;
    }

    private boolean isCameraProxyStale()
    {
        return this.cameraEye().distanceSquared(this.filledTranslate) > 1.0E-10F
            || this.cameraAngles().distanceSquared(this.filledRotate) > 1.0E-10F;
    }

    /** The eye as it is; pitch and turn in degrees. A roll has no meaning for the eyes and is dropped. */
    private void readCameraProxy()
    {
        if (this.form == null || !this.isCameraProxyChanged())
        {
            return;
        }

        Vector3f translate = this.cameraProxy.translate;
        Vector3f rotate = this.cameraProxy.getEulerRotation(new Vector3f());

        this.form.driverCameraX.set(translate.x);
        this.form.driverCameraY.set(translate.y);
        this.form.driverCameraZ.set(translate.z);
        this.form.driverCameraPitch.set((float) Math.toDegrees(rotate.x));
        this.form.driverCameraYaw.set((float) -Math.toDegrees(rotate.y));

        this.filledTranslate.set(translate);
        this.filledRotate.set(this.cameraAngles());
        this.cameraX.setValue(translate.x);
        this.cameraY.setValue(translate.y);
        this.cameraZ.setValue(translate.z);
        this.cameraYaw.setValue(this.form.driverCameraYaw.get());
        this.cameraPitch.setValue(this.form.driverCameraPitch.get());
    }

    @Override
    public UIPropTransform getGizmoTransform()
    {
        if (this.form == null)
        {
            return null;
        }

        int seat = this.getGizmoSeat();

        if (seat >= 0)
        {
            if (!this.form.seatsCustom.get() || !this.form.seatEnabled[seat].get())
            {
                return null;
            }

            /* Asked every frame the gizmo is drawn: carry a drag to the form, or the pads to the proxy */
            if (this.seatProxy.translate.distanceSquared(this.filledSeat) > 1.0E-12F)
            {
                this.readSeatProxy();
            }
            else if (!this.seatTransform.isEditing() && this.seatPoint(seat).distanceSquared(this.filledSeat) > 1.0E-10F)
            {
                this.fillSeatProxy();
                this.seatTransform.refreshFields();
            }

            return this.seatTransform;
        }

        if (!this.form.driverCamera.get())
        {
            return null;
        }

        if (this.isCameraProxyChanged())
        {
            this.readCameraProxy();
        }
        else if (!this.cameraTransform.isEditing() && this.isCameraProxyStale())
        {
            this.fillCameraProxy();
            this.cameraTransform.refreshFields();
        }

        return this.cameraTransform;
    }

    @Override
    public Matrix4f getGizmoOrigin(float transition, TransformSpace space)
    {
        if (this.getGizmoTransform() == null)
        {
            return null;
        }

        Matrix4f frame = this.editor.getBodyPartGizmoOrigin(transition, TransformSpace.LOCAL);

        if (frame == null || frame == Matrices.EMPTY_4F)
        {
            return null;
        }

        int seat = this.getGizmoSeat();

        if (seat >= 0)
        {
            /* On the plane as it is drawn: the seats' common height on top of its own */
            return new Matrix4f(frame).translate(this.seatPoint(seat).add(0F, this.form.seatHeight.get(), 0F));
        }

        Matrix4f origin = new Matrix4f(frame).translate(this.cameraEye());

        if (space.placesOnOwnFrame())
        {
            origin.rotate(VehicleCameraGuide.rotation(this.form));
        }

        return origin;
    }

    /* The parts that open */

    /** One row a part: its bone, the axis it swings about, how far, and a button to drop it */
    private void rebuildParts()
    {
        this.parts.removeAll();

        if (this.form != null)
        {
            for (ValueVehicleParts.Part part : this.form.vehicleParts.getAllTyped())
            {
                UICirculate axis = new UICirculate((c) -> part.axis.set(c.getValue()));

                axis.addLabel(key("axis_yaw"));
                axis.addLabel(key("axis_pitch"));
                axis.setValue(part.axis.get());
                axis.tooltip(key("axis_tooltip"));

                UITrackpad angle = UIValues.trackpad(() -> part.angle).limit(-180D, 180D).increment(5D).values(5D, 1D, 15D);

                angle.tooltip(key("angle_tooltip"));

                UIIcon remove = new UIIcon(Icons.REMOVE, (b) ->
                {
                    this.form.vehicleParts.remove(part);
                    this.rebuildParts();
                });

                remove.tooltip(key("parts_remove"));

                UIButton flip = new UIButton(key("parts_flip"), (b) -> part.angle.set(-part.angle.get()));

                flip.tooltip(key("parts_flip_tooltip"));
                this.parts.add(UI.row(this.boneButton(() -> part.bone), axis.w(50), angle.w(56), flip.w(34), remove.w(20)));
            }

            if (this.form.vehicleParts.getAllTyped().isEmpty())
            {
                this.parts.add(UI.label(key("parts_none")));
            }
        }

        this.options.resize();
    }

    /** Lists a part, opening the way it would open on this model; a bone already listed is left as it is */
    private boolean addPart(String bone, int axis)
    {
        if (bone == null || bone.isEmpty() || this.form.vehicleParts.find(bone) != null)
        {
            return false;
        }

        float sign = VehicleGeometry.openingSign(VehicleGeometry.getModel(this.form), this.form, bone, axis);

        this.form.vehicleParts.add(bone, axis, sign * (axis == ValueVehicleParts.AXIS_PITCH ? 60F : 75F));

        return true;
    }

    private void detectParts()
    {
        if (this.form == null)
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(this.form);
        Map<String, String> parents = new HashMap<>();

        if (model != null && model.model != null)
        {
            for (ModelGroup group : model.model.getAllGroups())
            {
                if (group.parent != null)
                {
                    parents.put(group.id, group.parent.id);
                }
            }
        }

        int added = 0;

        for (Map.Entry<String, Integer> entry : VehicleBoneDetector.detectParts(FormUtilsClient.getBones(this.form), parents::get).entrySet())
        {
            if (this.addPart(entry.getKey(), entry.getValue()))
            {
                added += 1;
            }
        }

        this.rebuildParts();
        this.getContext().notifyInfo(added == 0 ? key("parts_detect_none") : key("parts_detect_done").format(added));
        UIUtils.playClick();
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.vehicle_ik." + id);
    }

    /** A slot: its name, and a button that shows the bone in it and opens the model's bones to pick from */
    private UIButton boneButton(Supplier<ValueString> value)
    {
        UIButton button = new UIButton(IKey.EMPTY, null);

        button.callback = (b) -> this.pickBone((selected) -> value.get().set(selected == null ? "" : selected));
        button.valueBinding(() ->
        {
            String bone = this.form == null ? "" : value.get().get();

            button.label = bone == null || bone.isEmpty() ? key("none") : IKey.constant(bone);
        });

        return button;
    }

    /** The model's bones, sorted, to pick one from */
    private void pickBone(Consumer<String> callback)
    {
        if (this.form == null)
        {
            return;
        }

        List<String> bones = new ArrayList<>(FormUtilsClient.getBones(this.form));

        bones.sort(String.CASE_INSENSITIVE_ORDER);

        UIOverlay.addOverlay(this.getContext(), new UIStringOverlayPanel(key("pick"), true, bones, callback), 240, 0.6F);
    }

    private UIElement bone(IKey label, Supplier<ValueString> value)
    {
        return UI.labelRow(label, this.boneButton(value));
    }

    /** Fill the empty slots from the model's bone names; a slot already set is left as the user set it */
    private void detect()
    {
        if (this.form == null)
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(this.form);
        Map<String, float[]> pivots = new HashMap<>();
        Map<String, String> parents = new HashMap<>();

        if (model != null && model.model != null)
        {
            for (ModelGroup group : model.model.getAllGroups())
            {
                pivots.put(group.id, new float[] {group.initial.translate.x, group.initial.translate.y, group.initial.translate.z});

                if (group.parent != null)
                {
                    parents.put(group.id, group.parent.id);
                }
            }
        }

        Function<String, float[]> pivot = pivots.isEmpty() ? null : pivots::get;
        Map<String, String> found = VehicleBoneDetector.detect(FormUtilsClient.getBones(this.form), pivot, parents::get);

        fill(this.form.boneBody, found.get(VehicleBoneDetector.BODY));
        fill(this.form.boneSteering, found.get(VehicleBoneDetector.STEERING));
        fill(this.form.boneWheelFL, found.get(VehicleBoneDetector.FL));
        fill(this.form.boneWheelFR, found.get(VehicleBoneDetector.FR));
        fill(this.form.boneWheelRL, found.get(VehicleBoneDetector.RL));
        fill(this.form.boneWheelRR, found.get(VehicleBoneDetector.RR));

        this.getContext().notifyInfo(found.isEmpty() ? key("detect_none") : key("detect_done").format(found.size()));
        UIUtils.playClick();
    }

    private static void fill(ValueString value, String bone)
    {
        if (bone != null && (value.get() == null || value.get().isEmpty()))
        {
            value.set(bone);
        }
    }
}
