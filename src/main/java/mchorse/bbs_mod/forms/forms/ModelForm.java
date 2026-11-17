package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.constraints.BoneConstraintsIO;
import mchorse.bbs_mod.cubic.constraints.JitterControl;
import mchorse.bbs_mod.settings.values.core.ValueJitterControl;
import mchorse.bbs_mod.cubic.ik.BoneIKIO;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.cubic.physics.BonePhysicsIO;
import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.ValueBones;
import mchorse.bbs_mod.forms.forms.utils.ValueMaterials;
import mchorse.bbs_mod.forms.values.ValueActionsConfig;
import mchorse.bbs_mod.forms.values.ValueShapeKeys;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueLinks;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueWindControl;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class ModelForm extends Form implements IPosedForm, ILatticeForm
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.POSE;

    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueLinks materialTextures = new ValueLinks("material_textures");
    public final ValueString model = new ValueString("model", "");
    /** Mesh swap: the model shown instead of the intact one while {@link #damaged} is on (a crashed car, say). */
    public final ValueString damagedModel = new ValueString("damaged_model", "");
    public final ValueBoolean damaged = new ValueBoolean("damaged", false);

    /* Vehicle IK: wheels that roll with how fast the actor moves, front wheels and a steering wheel
     * that turn with how fast it turns, and a body that dips under braking and leans into a corner.
     * Driven entirely by the actor's own motion; the bones are picked by name. */
    public final ValueBoolean vehicleIk = new ValueBoolean("vehicle_ik", false);
    public final ValueFloat wheelRadius = new ValueFloat("wheel_radius", 0.45F, 0.05F, 4F);
    public final ValueFloat suspensionStrength = new ValueFloat("suspension_strength", 1F, 0F, 4F);
    public final ValueFloat steeringMultiplier = new ValueFloat("steering_multiplier", 1F, 0F, 4F);
    public final ValueBoolean invertWheelRoll = new ValueBoolean("invert_wheel_roll", false);
    public final ValueString boneBody = new ValueString("vehicle_bone_body", "");
    public final ValueString boneSteering = new ValueString("vehicle_bone_steering", "");
    public final ValueString boneWheelFL = new ValueString("vehicle_bone_wheel_fl", "");
    public final ValueString boneWheelFR = new ValueString("vehicle_bone_wheel_fr", "");
    public final ValueString boneWheelRL = new ValueString("vehicle_bone_wheel_rl", "");
    public final ValueString boneWheelRR = new ValueString("vehicle_bone_wheel_rr", "");
    /* The parts that open (doors, hood, trunk) and how high the seats are, against what the doors suggest */
    public final ValueVehicleParts vehicleParts = new ValueVehicleParts("vehicle_parts");
    public final ValueFloat seatHeight = new ValueFloat("vehicle_seat_height", 0F, -2F, 2F);
    /** The tallest edge a driven vehicle climbs, in blocks */
    public final ValueFloat stepHeight = new ValueFloat("vehicle_step_height", 0.6F, 0F, 1.25F);
    /** The body takes its nose-up and nose-down from the replay's pitch (what a drive records), not from the slope it works out */
    public final ValueBoolean pitchFromReplay = new ValueBoolean("vehicle_replay_pitch", false);
    /** The vehicle stands on the ground under its wheels: tilted to it, settled onto it, each wheel on its own (off for one keyed to fly or roll over) */
    public final ValueBoolean groundSettle = new ValueBoolean("vehicle_ground", true);
    /* The driver's camera, set by hand: where the eyes are in the vehicle's frame (blocks; front +Z,
     * left +X, up from the ground) and where they look against the heading */
    public final ValueBoolean driverCamera = new ValueBoolean("vehicle_driver_camera", false);
    public final ValueFloat driverCameraX = new ValueFloat("vehicle_driver_camera_x", 0F, -16F, 16F);
    public final ValueFloat driverCameraY = new ValueFloat("vehicle_driver_camera_y", 1.2F, -4F, 16F);
    public final ValueFloat driverCameraZ = new ValueFloat("vehicle_driver_camera_z", 0F, -16F, 16F);
    public final ValueFloat driverCameraYaw = new ValueFloat("vehicle_driver_camera_yaw", 0F, -180F, 180F);
    public final ValueFloat driverCameraPitch = new ValueFloat("vehicle_driver_camera_pitch", 0F, -90F, 90F);
    /* The four seats, set by hand: where each sitting plane is in the vehicle's frame (blocks, as
     * the camera) - front left (the driver's), front right, rear left, rear right - and whether it is
     * there at all. Off, the seats are found from the model's seats and doors */
    public static final String[] SEATS = {"fl", "fr", "rl", "rr"};
    public final ValueBoolean seatsCustom = new ValueBoolean("vehicle_seats_custom", false);
    public final ValueBoolean[] seatEnabled = new ValueBoolean[SEATS.length];
    public final ValueFloat[] seatX = new ValueFloat[SEATS.length];
    public final ValueFloat[] seatY = new ValueFloat[SEATS.length];
    public final ValueFloat[] seatZ = new ValueFloat[SEATS.length];
    /* The dashboard a seated player sees: the speed and the keys in the driver's seat, the keys only in the others */
    public final ValueBoolean driverHud = new ValueBoolean("vehicle_driver_hud", true);
    public final ValueBoolean passengerHud = new ValueBoolean("vehicle_passenger_hud", true);

    {
        for (int i = 0; i < SEATS.length; i++)
        {
            float side = i % 2 == 0 ? 0.4F : -0.4F;
            float row = i < 2 ? 0.2F : -0.7F;

            this.seatEnabled[i] = new ValueBoolean("vehicle_seat_" + SEATS[i], true);
            this.seatX[i] = new ValueFloat("vehicle_seat_" + SEATS[i] + "_x", side, -16F, 16F);
            this.seatY[i] = new ValueFloat("vehicle_seat_" + SEATS[i] + "_y", 0.5F, -4F, 16F);
            this.seatZ[i] = new ValueFloat("vehicle_seat_" + SEATS[i] + "_z", row, -16F, 16F);
        }
    }

    /** Free-form deformation: a lattice of control points bending the model, or one bone of it */
    public final ValueLattice.Points latticePoints = new ValueLattice.Points("lattice_points");
    public final ValueLattice.Bulge latticeBulge = new ValueLattice.Bulge("lattice_bulge");
    public final ValueLattice.Center latticeCenter = new ValueLattice.Center("lattice_center");
    public final ValueLattice lattice = new ValueLattice("lattice", this.latticePoints, this.latticeBulge, this.latticeCenter);

    @Override
    public ValueLattice getLattice()
    {
        return this.lattice;
    }
    public final ValuePose pose = new ValuePose("pose", new Pose());
    public final ValuePose poseOverlay = new ValuePose("pose_overlay", new Pose());
    public final ValueActionsConfig actions = new ValueActionsConfig("actions", new ActionsConfig());
    public final ValueColor color = new ValueColor("color", Color.white());
    public final ValueMaterials materials = new ValueMaterials("materials");
    public final ValueShapeKeys shapeKeys = new ValueShapeKeys("shape_keys", new ShapeKeys());
    public final ValueBoolean boneTracks = new ValueBoolean("bone_tracks", true);
    public final ValueBones bones = new ValueBones("bones");

    /** The global wind of the form's physics — one compound animatable property, not bound to a bone. */
    public final ValueWindControl wind = new ValueWindControl("wind", new WindControl());

    /** Procedural jitter tremor at form level (keyframeable global track). */
    public final ValueJitterControl jitter = new ValueJitterControl("jitter", new JitterControl());

    /**
     * The entity states an OptiFine CEM model asks about and a form cannot know — see
     * {@link mchorse.bbs_mod.cubic.jem.CemStatus}.
     * Every one of them lies over what the entity says, so their defaults change nothing, and they only
     * appear in the editor for a model that carries a CEM program.
     */
    public final ValueBoolean cemSitting = new ValueBoolean("cem_sitting", false);
    public final ValueBoolean cemTamed = new ValueBoolean("cem_tamed", false);
    public final ValueBoolean cemAggressive = new ValueBoolean("cem_aggressive", false);
    public final ValueBoolean cemOnShoulder = new ValueBoolean("cem_on_shoulder", false);
    public final ValueBoolean cemBurning = new ValueBoolean("cem_burning", false);
    public final ValueBoolean cemInLava = new ValueBoolean("cem_in_lava", false);
    public final ValueBoolean cemClimbing = new ValueBoolean("cem_climbing", false);
    public final ValueBoolean cemCrawling = new ValueBoolean("cem_crawling", false);
    public final ValueFloat cemHealth = new ValueFloat("cem_health", 1F);

    public final ValueBoolean hasCape = new ValueBoolean("has_cape", false);
    public final ValueLink capeTexture = new ValueLink("cape_texture", Link.bbs("textures/default_cape.png"));

    /** Dissolve and shatter: the model burning away or breaking apart bone by bone. */
    public final FormFxValues fx = new FormFxValues(this);

    /**
     * Runtime per-material texture overrides driven by the per-material animation tracks
     * (keyed by material name). Set each frame by {@code FormProperties} during playback and
     * read first by the renderer's texture resolver; empty means "no track override, use the
     * material's default / the form's default texture".
     */
    public final transient Map<String, Link> materialTextureOverrides = new HashMap<>();

    /**
     * Runtime per-material appearance overrides driven by the material animation tracks
     * (keyed by material name), same lifecycle as {@link #materialTextureOverrides}: set
     * each frame by {@code FormProperties} during playback, read by the renderer over the
     * static {@link #materials} values.
     */
    public final transient Map<String, Color> materialColorOverrides = new HashMap<>();
    public final transient Map<String, Color> materialOverlayOverrides = new HashMap<>();
    public final transient Map<String, Float> materialLightingOverrides = new HashMap<>();
    public final transient Map<String, Integer> materialCullingOverrides = new HashMap<>();
    public final transient Map<String, Boolean> materialVisibilityOverrides = new HashMap<>();

    /** PBR slider overrides (keyed by material name, then by the slider's property name). */
    public final transient Map<String, Map<String, Float>> materialPbrOverrides = new HashMap<>();

    /** Autonomous gaze offset for the pupils (set per frame by a framebuffer form, never saved); NaN X means off. */
    public transient float gazeX = Float.NaN;
    public transient float gazeY;

    public final transient Map<String, Vector3f> ikTargetOverrides = new HashMap<>();
    public final transient Map<String, Vector3f> poleTargetOverrides = new HashMap<>();
    public final transient Map<String, Float> ikTargetWeights = new HashMap<>();
    public final transient Map<String, Float> poleTargetWeights = new HashMap<>();
    public final transient Map<String, Vector3f> physicsTargetOverrides = new HashMap<>();
    public final transient Map<String, Float> physicsTargetWeights = new HashMap<>();

    public ModelForm()
    {
        super();

        this.add(this.texture);
        this.materialTextures.invisible();
        this.add(this.materialTextures);
        this.add(this.model);
        this.add(this.damagedModel);
        this.add(this.damaged);

        /* Vehicle IK: a setup, not an animation - no tracks of its own; the motion comes from the replay */
        for (BaseValue value : new BaseValue[] {this.vehicleIk, this.wheelRadius, this.suspensionStrength, this.steeringMultiplier, this.invertWheelRoll,
            this.boneBody, this.boneSteering, this.boneWheelFL, this.boneWheelFR, this.boneWheelRL, this.boneWheelRR, this.vehicleParts, this.seatHeight, this.stepHeight, this.pitchFromReplay,
            this.driverCamera, this.driverCameraX, this.driverCameraY, this.driverCameraZ, this.driverCameraYaw, this.driverCameraPitch,
            this.seatsCustom, this.driverHud, this.passengerHud, this.groundSettle})
        {
            value.invisible();
            this.add(value);
        }

        for (int i = 0; i < SEATS.length; i++)
        {
            for (BaseValue value : new BaseValue[] {this.seatEnabled[i], this.seatX[i], this.seatY[i], this.seatZ[i]})
            {
                value.invisible();
                this.add(value);
            }
        }
        this.lattice.invisible();
        this.add(this.lattice);
        /* Visible: the shape of the lattice is a track of its own, keyed in the dope sheet */
        this.add(this.latticePoints);
        this.add(this.latticeBulge);
        this.add(this.latticeCenter);
        this.add(this.pose);
        this.add(this.poseOverlay);

        this.syncOverlayTracks();

        this.add(this.actions);
        this.add(this.color);
        this.materials.invisible();
        this.add(this.materials);
        this.add(this.shapeKeys);
        this.boneTracks.invisible();
        this.add(this.boneTracks);

        this.bones.invisible();
        this.add(this.bones);
        this.wind.invisible();
        this.add(this.wind);
        this.add(this.jitter);

        /* Visible, so each is a track of its own: a cat that sits down mid-take is a keyframe like
         * any other. */
        this.add(this.cemSitting);
        this.add(this.cemTamed);
        this.add(this.cemAggressive);
        this.add(this.cemOnShoulder);
        this.add(this.cemBurning);
        this.add(this.cemInLava);
        this.add(this.cemClimbing);
        this.add(this.cemCrawling);
        this.add(this.cemHealth);

        this.add(this.hasCape);
        this.add(this.capeTexture);
    }

    @Override
    public ValuePose getPose()
    {
        return this.pose;
    }

    @Override
    public ValuePose getPoseOverlay()
    {
        return this.poseOverlay;
    }

    @Override
    public boolean hasBoneTracks()
    {
        return this.boneTracks.get();
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        /* The points of a lattice were saved inside its group before they became a track of their own */
        if (data instanceof MapType saved && !saved.has("lattice_points") && saved.has("lattice", BaseType.TYPE_MAP) && saved.getMap("lattice").has("points"))
        {
            this.latticePoints.fromData(saved.getMap("lattice").get("points"));
        }

        /* Forms saved before the bones group kept the constraints and the IK setup as opaque
         * blobs in the exchange formats; unpack them into the per-bone properties. */
        if (data instanceof MapType map)
        {
            if (map.has("constraints", BaseType.TYPE_MAP))
            {
                BoneConstraintsIO.read(map.getMap("constraints"), this.bones, false);
            }

            if (map.has("ik", BaseType.TYPE_MAP))
            {
                BoneIKIO.read(map.getMap("ik"), this.bones, false);
            }

            if (map.has("physics", BaseType.TYPE_MAP))
            {
                BonePhysicsIO.read(map.getMap("physics"), this.bones, this.wind, false);
            }
        }

        /* The head is pure FK: drop any IK chain a stored form carries on it. */
        FormBone head = this.bones.getBone("head");

        if (head != null && BoneIKIO.isFkOnly(head.getId()) && head.hasChain())
        {
            BoneIKIO.clearChain(head);
        }
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.model.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}
