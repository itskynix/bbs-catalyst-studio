package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * What the physics studio knows about one replay: whether the replay is simulated, what it is made of
 * and the tracks that steer it.
 *
 * <p>The tracks are ordinary keyframe channels, so they sit in the dope sheet like any other. {@link #animation}
 * is the one handle: at 1 the replay walks its keys exactly, at 0 it is on its own, in between it is pulled
 * towards its keys with that much force.</p>
 */
public class ReplayPhysics extends ValueGroup
{
    public static final String ANIMATION = "physics_animation";
    public static final String MUSCLES = "physics_muscles";
    public static final String PUSH_X = "physics_push_x";
    public static final String PUSH_Y = "physics_push_y";
    public static final String PUSH_Z = "physics_push_z";
    public static final String BLAST = "physics_blast";
    public static final String BLAST_X = "physics_blast_x";
    public static final String BLAST_Y = "physics_blast_y";
    public static final String BLAST_Z = "physics_blast_z";

    public final ValueBoolean enabled = new ValueBoolean("enabled", false);

    /** A character that tumbles when it is let go; off, the body keeps upright and only slides and falls */
    public final ValueBoolean ragdoll = new ValueBoolean("ragdoll", true);
    /** One rigid body for the whole form, even when the form is a model that could be a jointed ragdoll */
    public final ValueBoolean rigid = new ValueBoolean("rigid", false);
    /** How hard the body keeps fighting for its keyed pose when it is not fully on its own: 0 is limp, 1 holds it */
    public final ValueFloat muscles = new ValueFloat("muscles", 0.5F);

    public final ValueString material = new ValueString("material", PhysicsMaterial.WOOD.id);
    /** Kilograms; 0 weighs the body by its material and its size */
    public final ValueFloat mass = new ValueFloat("mass", 0F);
    public final ValueFloat friction = new ValueFloat("friction", PhysicsMaterial.WOOD.friction);
    public final ValueFloat restitution = new ValueFloat("restitution", PhysicsMaterial.WOOD.restitution);

    /** A balloon: the body is lighter than air and floats up with this much lift, slowed by the air */
    public final ValueBoolean balloon = new ValueBoolean("balloon", false);
    public final ValueFloat lift = new ValueFloat("lift", 0.3F);

    /** A rope from the body to a replay, or to a point above it when no replay is named */
    public final ValueBoolean rope = new ValueBoolean("rope", false);
    public final ValueString ropeAnchor = new ValueString("rope_anchor", "");
    public final ValueFloat ropeLength = new ValueFloat("rope_length", 2F);

    /** A bone torn off the ragdoll at a tick and thrown free; empty or a negative tick for none */
    public final ValueString tearBone = new ValueString("tear_bone", "");
    public final mchorse.bbs_mod.settings.values.numeric.ValueInt tearTick = new mchorse.bbs_mod.settings.values.numeric.ValueInt("tear_tick", -1);

    /** How far a blast reaches, in blocks */
    public final ValueFloat blastRadius = new ValueFloat("blast_radius", 4F);

    public final KeyframeChannel<Double> animation = new KeyframeChannel<>(ANIMATION, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> musclesTrack = new KeyframeChannel<>(MUSCLES, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> pushX = new KeyframeChannel<>(PUSH_X, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> pushY = new KeyframeChannel<>(PUSH_Y, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> pushZ = new KeyframeChannel<>(PUSH_Z, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blast = new KeyframeChannel<>(BLAST, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blastX = new KeyframeChannel<>(BLAST_X, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blastY = new KeyframeChannel<>(BLAST_Y, KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blastZ = new KeyframeChannel<>(BLAST_Z, KeyframeFactories.DOUBLE);

    public ReplayPhysics(String id)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.ragdoll);
        this.add(this.rigid);
        this.add(this.muscles);
        this.add(this.material);
        this.add(this.mass);
        this.add(this.friction);
        this.add(this.restitution);
        this.add(this.balloon);
        this.add(this.lift);
        this.add(this.rope);
        this.add(this.ropeAnchor);
        this.add(this.ropeLength);
        this.add(this.blastRadius);
        this.add(this.tearBone);
        this.add(this.tearTick);
        this.add(this.animation);
        this.add(this.musclesTrack);
        this.add(this.pushX);
        this.add(this.pushY);
        this.add(this.pushZ);
        this.add(this.blast);
        this.add(this.blastX);
        this.add(this.blastY);
        this.add(this.blastZ);
    }

    /** The animation handle at a tick: 1 while nothing is keyed, so a replay that is switched on and left alone follows its keys */
    public float animationAt(float tick)
    {
        return this.animation.isEmpty() ? 1F : clamp(this.animation.interpolate(tick).floatValue());
    }

    public float musclesAt(float tick)
    {
        return this.musclesTrack.isEmpty() ? clamp(this.muscles.get()) : clamp(this.musclesTrack.interpolate(tick).floatValue());
    }

    private static float clamp(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    /** Everything that changes what the simulation of this replay would be, apart from the keyed positions */
    public String settingsKey()
    {
        return this.enabled.get() + "|" + this.ragdoll.get() + "|" + this.rigid.get() + "|" + this.material.get() + "|" + this.mass.get() + "|" + this.friction.get()
            + "|" + this.restitution.get() + "|" + this.balloon.get() + "|" + this.lift.get() + "|" + this.rope.get() + "|" + this.ropeAnchor.get()
            + "|" + this.ropeLength.get() + "|" + this.blastRadius.get() + "|" + (this.muscles.get()) + "|" + this.tearBone.get() + "|" + this.tearTick.get();
    }
}
