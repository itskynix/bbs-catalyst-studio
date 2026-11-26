package mchorse.bbs_mod.physics.ragdoll;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.physics.forms.IModelPhysicsForm;
import mchorse.bbs_mod.physics.forms.PhysicsKnobValue;

/**
 * Reading a model form's active physics. The numbers are knob values of the form (see
 * {@link ActiveKnob}), read here straight into an array the controller owns, so the per-tick read
 * makes no objects.
 */
public final class ActiveRagdolls
{
    public static final int COUNT = ActiveKnob.values().length;

    private static final ActiveKnob[] KNOBS = ActiveKnob.values();

    private ActiveRagdolls()
    {}

    /** Fills {@code out} (at least {@link #COUNT} long), by {@link ActiveKnob#ordinal()}, with the values this tick: keyframed where a track drives them. */
    public static void read(Form form, float[] out)
    {
        if (!(form instanceof IModelPhysicsForm model))
        {
            for (ActiveKnob knob : KNOBS)
            {
                out[knob.ordinal()] = knob == ActiveKnob.STRENGTH ? 0F : knob.fallback;
            }

            return;
        }

        for (ActiveKnob knob : KNOBS)
        {
            float value = model.bbs_physics$getActiveKnob(knob).get();

            out[knob.ordinal()] = Float.isFinite(value) ? Math.max(knob.min, Math.min(knob.max, value)) : knob.fallback;
        }
    }

    public static float get(Form form, ActiveKnob knob)
    {
        return form instanceof IModelPhysicsForm model ? model.bbs_physics$getActiveKnob(knob).get() : knob.fallback;
    }

    public static void set(Form form, ActiveKnob knob, float value)
    {
        if (form instanceof IModelPhysicsForm model)
        {
            PhysicsKnobValue stored = model.bbs_physics$getActiveKnob(knob);

            if (stored.getOriginalValue() != value)
            {
                stored.set(value);
            }
        }
    }

    /** Whether the active physics' own numbers show as tracks: a ragdoll whose active strength is above 0. */
    public static boolean isShown(Form form)
    {
        return FormRagdolls.isEnabled(form) && form instanceof IModelPhysicsForm model
            && model.bbs_physics$getActiveKnob(ActiveKnob.STRENGTH).getOriginalValue() > 0F;
    }
}
