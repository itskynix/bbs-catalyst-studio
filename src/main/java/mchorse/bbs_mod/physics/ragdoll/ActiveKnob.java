package mchorse.bbs_mod.physics.ragdoll;

/**
 * The numbers of a ragdoll's active physics: the live controller that keeps a character on its
 * feet, reacts to what hits it and braces when it falls, instead of only hanging from its joints.
 * Each one is a {@code PhysicsKnobValue} on the model form, so a keyframe reaches it on the tick it
 * lands, exactly like the ragdoll's own numbers ({@link RagdollKnob}).
 *
 * <p>Strength 0 is the controller off: the ragdoll is the passive one it always was, and nothing
 * below is read. Between 0 and 1 it is the blend between that passive ragdoll and the full active
 * response, which is what lets a take fade a character from fighting for balance to limp.</p>
 */
public enum ActiveKnob
{
    /** The blend between the passive ragdoll (0) and the full active response (1). */
    STRENGTH("bbs_physics:active_strength", 0F, 0F, 1F),
    /** How hard the trunk is turned back upright, against the animated pose's own lean. */
    BALANCE("bbs_physics:active_balance", 0.6F, 0F, 1F),
    /** The lean, in degrees off the animated pose, past which balance gives up and the body goes down. */
    FALL_ANGLE("bbs_physics:active_fall_angle", 50F, 5F, 120F),
    /** How fast a stagger wears off and a fallen body gets back to its pose, per second. */
    RECOVERY("bbs_physics:active_recovery", 0.5F, 0F, 2F),
    /** How much a hit or a hard contact staggers: the share of the muscles and the animation's hold it takes away. */
    IMPACT("bbs_physics:active_impact", 0.6F, 0F, 2F),
    /** How strongly the limbs brace before the ground, and how much of the landing is soaked up. */
    REFLEX("bbs_physics:active_reflex", 0.5F, 0F, 1F),
    /** How much of the landing is given back as a bounce. */
    BOUNCE("bbs_physics:active_bounce", 0.1F, 0F, 1F),
    /** The muscle tone the controller holds the joints at while it is in charge. */
    STIFFNESS("bbs_physics:active_stiffness", 0.7F, 0F, 1F),
    /** How quickly the muscles and the balance settle instead of overshooting. */
    DAMPING("bbs_physics:active_damping", 0.6F, 0F, 1F),
    /** The speed, in blocks per second, under which a body counts as resting and the controller sleeps. */
    SLEEP_SPEED("bbs_physics:active_sleep_speed", 0.08F, 0F, 2F),
    /** The most solver iterations the ragdoll's parts get per step; 0 leaves the scene's own. */
    ITERATIONS("bbs_physics:active_iterations", 0F, 0F, 16F);

    public final String id;
    public final float fallback;
    public final float min;
    public final float max;

    ActiveKnob(String id, float fallback, float min, float max)
    {
        this.id = id;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
    }
}
