package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * The crowd glance of a replay: an open world bystander turning its head to a passing character
 * and back to what it was doing, the way crowds react in GTA, RDR2 or The Witcher.
 *
 * <p>Only the settings live here. The glance is worked out while the film plays, on top of the head
 * the keyframes (and the look-at) give the actor, and is never written into the keyframes: switching
 * it off leaves the animation exactly as it was. See {@code ReplayCrowdGlanceDriver} on the client.</p>
 */
public class ReplayCrowdGlance extends ValueGroup
{
    public final ValueBoolean enabled = new ValueBoolean("enabled", false);

    /** Id of the replay the crowd reacts to */
    public final ValueString target = new ValueString("target", "");

    /** How close the target has to come, in blocks */
    public final ValueFloat radius = new ValueFloat("radius", 6F, 0F, 1000F);

    /** How long one glance lasts, in ticks */
    public final ValueInt duration = new ValueInt("duration", 60, 1, 72000);

    /** How far off the body's facing the head turns, in degrees; past it the actor looks away */
    public final ValueFloat maxNeckAngle = new ValueFloat("max_neck_angle", 75F, 0F, 180F);

    /** How many ticks turning to the target and back take */
    public final ValueInt blendTicks = new ValueInt("blend_ticks", 8, 1, 200);

    /** How long the actor keeps its eyes off the target after a glance, unless the target walks away first */
    public final ValueInt cooldownTicks = new ValueInt("cooldown_ticks", 30, 0, 72000);

    public ReplayCrowdGlance(String id)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.target);
        this.add(this.radius);
        this.add(this.duration);
        this.add(this.maxNeckAngle);
        this.add(this.blendTicks);
        this.add(this.cooldownTicks);
    }
}
