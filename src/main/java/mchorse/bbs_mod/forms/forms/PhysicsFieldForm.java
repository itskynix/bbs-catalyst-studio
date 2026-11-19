package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.function.Supplier;

/**
 * A gravity and cosmic anomaly field for the film's physics: a sphere or a box in the world (or the
 * whole scene) inside which everything the physics owns - ragdolls, torn off limbs, released bodies,
 * cloth - feels a different gravity, a drag, and, in the Big Rip mode, a pull towards the form (or a
 * push away from it) with some turbulence on top.
 *
 * <p>It draws nothing but a guide. The physics scene reads it itself, once per simulated tick, at
 * the whole tick it is simulating, so what a replay keys here - the gravity scale falling from 1 to 0 in
 * one tick, the pull climbing from nothing to its peak - is what the recording replays every time.
 * The values are top-level properties on purpose: only top-level properties are tracks.</p>
 *
 * <p>The field sits where the form sits: the actor's position plus the form's translation (and its
 * parents'). The region is not turned with the form.</p>
 */
public class PhysicsFieldForm extends Form
{
    public static final Icon ICON = Icons.GLOBE;

    public static final int MODE_ZERO_G = 0;
    public static final int MODE_BIG_RIP = 1;

    public static final int SHAPE_SPHERE = 0;
    public static final int SHAPE_BOX = 1;
    public static final int SHAPE_SCENE = 2;

    /** The name shown for the form; the client puts the translated one in */
    public static Supplier<String> displayNameSupplier = () -> "Physics Field";

    /** Zero-G (gravity and drag only) or Big Rip (the same, plus the pull and the turbulence) - a setup, not a track */
    public final ValueInt mode = new ValueInt("mode", MODE_ZERO_G, MODE_ZERO_G, MODE_BIG_RIP);
    /** A sphere, a box, or everywhere - a setup, not a track */
    public final ValueInt shape = new ValueInt("shape", SHAPE_SPHERE, SHAPE_SPHERE, SHAPE_SCENE);

    /** The reach of a sphere, and what the pull's distance falloff is measured against, in blocks */
    public final ValueFloat radius = new ValueFloat("radius", 8F, 0.5F, 512F);
    public final ValueFloat width = new ValueFloat("width", 10F, 0.5F, 512F);
    public final ValueFloat length = new ValueFloat("length", 10F, 0.5F, 512F);
    public final ValueFloat height = new ValueFloat("height", 10F, 0.5F, 512F);

    /** How much of the scene's gravity is felt inside: 1 is normal, 0 weightless, negative lifts */
    public final ValueFloat gravityScale = new ValueFloat("gravity_scale", 0F, -2F, 2F);
    /** How fast motion dies away inside, as the share of the speed lost per second - keeps a drifting thing from spinning forever */
    public final ValueFloat damping = new ValueFloat("damping", 0.3F, 0F, 5F);

    /** Big Rip: the acceleration towards the centre in blocks per second squared (negative pushes away) */
    public final ValueFloat pullStrength = new ValueFloat("pull_strength", 20F, -200F, 200F);
    /** Big Rip: how sharply the pull fades with distance, 0 being the same all the way to the edge */
    public final ValueFloat falloff = new ValueFloat("falloff", 1F, 0F, 4F);
    /** Big Rip: the random buffeting, in blocks per second squared */
    public final ValueFloat turbulence = new ValueFloat("turbulence", 4F, 0F, 50F);

    public final ValueBoolean showGuides = new ValueBoolean("show_guides", true);

    public PhysicsFieldForm()
    {
        super();

        this.mode.invisible();
        this.shape.invisible();
        this.add(this.mode);
        this.add(this.shape);
        this.add(this.radius);
        this.add(this.width);
        this.add(this.length);
        this.add(this.height);
        this.add(this.gravityScale);
        this.add(this.damping);
        this.add(this.pullStrength);
        this.add(this.falloff);
        this.add(this.turbulence);
        this.add(this.showGuides);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return displayNameSupplier.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }
}
