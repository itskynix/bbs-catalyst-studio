package mchorse.bbs_mod.utils.keyframes.factories;

import java.util.HashMap;
import java.util.Map;

public class KeyframeFactories
{
    public static final Map<String, IKeyframeFactory> FACTORIES = new HashMap<>();

    public static final ColorKeyframeFactory COLOR = new ColorKeyframeFactory();
    public static final TransformKeyframeFactory TRANSFORM = new TransformKeyframeFactory();
    public static final PoseTransformKeyframeFactory POSE_TRANSFORM = new PoseTransformKeyframeFactory();
    public static final PoseKeyframeFactory POSE = new PoseKeyframeFactory();
    public static final IKKeyframeFactory IK = new IKKeyframeFactory();
    public static final PhysicsKeyframeFactory PHYSICS = new PhysicsKeyframeFactory();
    public static final WindKeyframeFactory WIND = new WindKeyframeFactory();
    public static final BooleanKeyframeFactory BOOLEAN = new BooleanKeyframeFactory();
    public static final StringKeyframeFactory STRING = new StringKeyframeFactory();
    public static final FloatKeyframeFactory FLOAT = new FloatKeyframeFactory();
    public static final DoubleKeyframeFactory DOUBLE = new DoubleKeyframeFactory();
    public static final IntegerKeyframeFactory INTEGER = new IntegerKeyframeFactory();
    public static final LongKeyframeFactory LONG = new LongKeyframeFactory();
    public static final LinkKeyframeFactory LINK = new LinkKeyframeFactory();
    public static final Vector4fKeyframeFactory VECTOR4F = new Vector4fKeyframeFactory();
    public static final AnchorKeyframeFactory ANCHOR = new AnchorKeyframeFactory();
    public static final BlockStateKeyframeFactory BLOCK_STATE = new BlockStateKeyframeFactory();
    public static final ItemStackKeyframeFactory ITEM_STACK = new ItemStackKeyframeFactory();
    /** A group of slots at once, for the POV interface clips and the replay's inventory */
    public static final mchorse.bbs_mod.film.pov.PovSlotSetKeyframeFactory POV_SLOTS = new mchorse.bbs_mod.film.pov.PovSlotSetKeyframeFactory();
    public static final ActionsConfigKeyframeFactory ACTIONS_CONFIG = new ActionsConfigKeyframeFactory();
    public static final ShapeKeysKeyframeFactory SHAPE_KEYS = new ShapeKeysKeyframeFactory();
    public static final ParticleSettingsKeyframeFactory PARTICLE_SETTINGS = new ParticleSettingsKeyframeFactory();
    public static final BoneConstraintKeyframeFactory BONE_CONSTRAINT = new BoneConstraintKeyframeFactory();
    public static final JitterKeyframeFactory JITTER = new JitterKeyframeFactory();
    public static final LatticeKeyframeFactory LATTICE = new LatticeKeyframeFactory(LatticeKeyframeFactory.LENGTH);
    public static final LatticeKeyframeFactory BULGE = new LatticeKeyframeFactory(LatticeKeyframeFactory.BULGE_LENGTH);
    public static final LatticeKeyframeFactory CENTER = new LatticeKeyframeFactory(LatticeKeyframeFactory.CENTER_LENGTH);

    /** Transform-like values, which the graph editor opens as one curve per component. */
    public static boolean isComposite(IKeyframeFactory factory)
    {
        return factory instanceof TransformKeyframeFactory
            || factory instanceof PoseTransformKeyframeFactory
            || factory instanceof PoseKeyframeFactory;
    }

    /** Whether the graph editor can draw the factory's track at all. */
    public static boolean isGraphable(IKeyframeFactory factory)
    {
        return isNumeric(factory) || isComposite(factory);
    }

    public static boolean isNumeric(IKeyframeFactory factory)
    {
        return factory instanceof DoubleKeyframeFactory
            || factory instanceof FloatKeyframeFactory
            || factory instanceof IntegerKeyframeFactory
            || factory instanceof LongKeyframeFactory;
    }

    /**
     * Fills the registry. Called by BBS while it initialises, and followed by the event that
     * lets addons add to it.
     *
     * <p>This used to be a static initialiser, which ran whenever something first touched the
     * class — a moment nobody chose and an addon could not aim at.</p>
     */
    public static void setup()
    {
        FACTORIES.put("color", COLOR);
        FACTORIES.put("transform", TRANSFORM);
        FACTORIES.put("pose_transform", POSE_TRANSFORM);
        FACTORIES.put("pose", POSE);
        FACTORIES.put("ik", IK);
        FACTORIES.put("physics", PHYSICS);
        FACTORIES.put("wind", WIND);
        FACTORIES.put("boolean", BOOLEAN);
        FACTORIES.put("string", STRING);
        FACTORIES.put("float", FLOAT);
        FACTORIES.put("double", DOUBLE);
        FACTORIES.put("integer", INTEGER);
        FACTORIES.put("long", LONG);
        FACTORIES.put("link", LINK);
        FACTORIES.put("vector4f", VECTOR4F);
        FACTORIES.put("anchor", ANCHOR);
        FACTORIES.put("block_state", BLOCK_STATE);
        FACTORIES.put("item_stack", ITEM_STACK);
        FACTORIES.put("pov_slots", POV_SLOTS);
        FACTORIES.put("actions_config", ACTIONS_CONFIG);
        FACTORIES.put("shape_keys", SHAPE_KEYS);
        FACTORIES.put("particle_settings", PARTICLE_SETTINGS);
        FACTORIES.put("bone_constraint", BONE_CONSTRAINT);
        FACTORIES.put("jitter", JITTER);
        FACTORIES.put("lattice", LATTICE);
        FACTORIES.put("lattice_bulge", BULGE);
        FACTORIES.put("lattice_center", CENTER);
    }
}
