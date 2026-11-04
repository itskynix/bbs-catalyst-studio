package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;


/**
 * A free-form deformation lattice around a model, or around one bone of it: a grid of control points
 * (2x2x2 or 3x3x3) that bends everything inside it when the points are moved.
 *
 * <p>Every point holds an offset (blocks, in the vehicle-style actor frame: front +Z, left +X, up +Y)
 * from where the undeformed grid has it. The offsets are always stored on the 3x3x3 grid; a 2x2x2
 * lattice uses its corners only, and changing between the two keeps the shape (the points a 3x3x3
 * lattice gains are worked out from the corners).</p>
 */
public class ValueLattice extends ValueGroup
{
    public static final int SIDE = 3;
    public static final int SLOTS = SIDE * SIDE * SIDE;

    public final ValueBoolean enabled = new ValueBoolean("enabled", false);
    public final ValueInt dimensions = new ValueInt("dimensions", 2, 2, 3);
    /** Empty covers the whole model, otherwise that bone and everything below it */
    public final ValueString targetBone = new ValueString("target_bone", "");
    /** Moves the lattice's box off the target's own, in blocks (the actor's frame): the whole cage, by its middle */
    public final ValueFloat shiftX = new ValueFloat("shift_x", 0F, -64F, 64F);
    public final ValueFloat shiftY = new ValueFloat("shift_y", 0F, -64F, 64F);
    public final ValueFloat shiftZ = new ValueFloat("shift_z", 0F, -64F, 64F);
    /** The bulge sphere is on (it is moved by the track, see {@link Bulge}) */
    public final ValueBoolean bulgeEnabled = new ValueBoolean("bulge_enabled", false);
    /** The control point offsets: a form property of its own (a keyframe track), held here to work them */
    public final Points points;
    public final Bulge bulge;
    /** Where the whole cage stands, keyed: an offset on top of the shift above (a track of its own) */
    public final Center center;

    public ValueLattice(String id, Points points, Bulge bulge)
    {
        this(id, points, bulge, new Center("lattice_center"));
    }

    public ValueLattice(String id, Points points, Bulge bulge, Center center)
    {
        super(id);

        this.points = points;
        this.bulge = bulge;
        this.center = center;

        this.add(this.enabled);
        this.add(this.dimensions);
        this.add(this.targetBone);
        this.add(this.shiftX);
        this.add(this.shiftY);
        this.add(this.shiftZ);
        this.add(this.bulgeEnabled);
    }

    /** How far the cage is moved off its target's box: the set shift and the keyed centre together, in blocks */
    public org.joml.Vector3f shift()
    {
        float[] keyed = this.center.get();
        org.joml.Vector3f shift = new org.joml.Vector3f(this.shiftX.get(), this.shiftY.get(), this.shiftZ.get());

        if (keyed != null && keyed.length >= 3)
        {
            shift.add(keyed[0], keyed[1], keyed[2]);
        }

        return shift;
    }

    /** Whether the bulge sphere pushes anything: on, with a radius and a strength */
    public boolean bulgeActive()
    {
        float[] sphere = this.bulge.get();

        return this.bulgeEnabled.get() && sphere.length >= 5 && sphere[3] > 0F && sphere[4] != 0F;
    }

    /** Whether the lattice bends anything: its points, out of place and on, or the bulge */
    public boolean isDeforming()
    {
        return this.enabled.get() && !this.points.isZero() || this.bulgeActive();
    }

    /** The shapes the panel offers in one click */
    public static final int PRESET_SQUASH = 0;
    public static final int PRESET_STRETCH = 1;
    public static final int PRESET_TWIST = 2;

    /**
     * Set the lattice to one of the quick shapes, over a box (the actor's frame) it spans. Written
     * from the undeformed grid, not on top of the current shape. A point is placed by its place in
     * the box, {@code (fx, fy, fz)} each from 0 to 1.
     *
     * <ul>
     *     <li>Squash: the top comes down by a quarter of the height and the sides bulge out.</li>
     *     <li>Stretch: the top goes up by a quarter and the sides draw in.</li>
     *     <li>Twist and bend: each layer turns about the vertical through the middle, the top by 45 degrees,
     *     and the top leans forward a little.</li>
     * </ul>
     */
    public void applyPreset(int preset, org.joml.Vector3f min, org.joml.Vector3f max)
    {
        float[] all = new float[SLOTS * 3];
        float width = max.x - min.x;
        float height = max.y - min.y;
        float length = max.z - min.z;
        float cx = (min.x + max.x) / 2F;
        float cz = (min.z + max.z) / 2F;
        int d = this.dimensions();

        for (int n = 0; n < this.count(); n++)
        {
            int[] cell = this.cell(n);
            float fx = cell[0] / (d - 1F);
            float fy = cell[1] / (d - 1F);
            float fz = cell[2] / (d - 1F);
            float side = fx * 2F - 1F;
            float depth = fz * 2F - 1F;
            float dx = 0F;
            float dy = 0F;
            float dz = 0F;

            switch (preset)
            {
                case PRESET_SQUASH ->
                {
                    dy = -0.25F * height * fy;
                    dx = 0.15F * width * side * 0.5F;
                    dz = 0.15F * length * depth * 0.5F;
                }
                case PRESET_STRETCH ->
                {
                    dy = 0.25F * height * fy;
                    dx = -0.12F * width * side * 0.5F;
                    dz = -0.12F * length * depth * 0.5F;
                }
                default ->
                {
                    float angle = (float) Math.toRadians(45D) * fy;
                    float px = min.x + width * fx - cx;
                    float pz = min.z + length * fz - cz;
                    float cos = (float) Math.cos(angle);
                    float sin = (float) Math.sin(angle);

                    dx = px * cos - pz * sin - px;
                    dz = px * sin + pz * cos - pz + 0.12F * length * fy * fy;
                }
            }

            int slot = this.slot(n) * 3;

            all[slot] = dx;
            all[slot + 1] = dy;
            all[slot + 2] = dz;
        }

        this.points.set(all);
    }

    public int dimensions()
    {
        return this.dimensions.get() >= SIDE ? SIDE : 2;
    }

    /** Whether the points of the lattice bend anything: on, and with a point out of place */
    public boolean isActive()
    {
        return this.enabled.get() && !this.points.isZero();
    }

    /** How many control points the lattice has */
    public int count()
    {
        int d = this.dimensions();

        return d * d * d;
    }

    /** The slot of the n-th control point (x slowest, z fastest) in the stored 3x3x3 grid */
    public int slot(int n)
    {
        int d = this.dimensions();
        int step = d == 2 ? 2 : 1;
        int i = n / (d * d);
        int j = n / d % d;
        int k = n % d;

        return ((i * step) * SIDE + (j * step)) * SIDE + k * step;
    }

    /** The grid position (0 or 1 for 2x2x2, 0 to 2 for 3x3x3) of the n-th control point */
    public int[] cell(int n)
    {
        int d = this.dimensions();

        return new int[] {n / (d * d), n / d % d, n % d};
    }

    public float[] get(int n)
    {
        float[] all = this.points.get();
        int slot = this.slot(n) * 3;

        return new float[] {all[slot], all[slot + 1], all[slot + 2]};
    }

    public void set(int n, float x, float y, float z)
    {
        float[] all = this.points.get().clone();
        int slot = this.slot(n) * 3;

        all[slot] = x;
        all[slot + 1] = y;
        all[slot + 2] = z;
        this.points.set(all);
    }

    /** Back to the undeformed grid */
    public void clear()
    {
        this.points.set(new float[SLOTS * 3]);
    }

    /** Switch between 2x2x2 and 3x3x3, keeping the shape the corners give */
    public void setDimensions(int dimensions)
    {
        int next = dimensions >= SIDE ? SIDE : 2;

        if (next == this.dimensions())
        {
            return;
        }

        if (next == SIDE)
        {
            /* The points between the corners, from the trilinear blend of the corners' offsets */
            float[] all = this.points.get().clone();

            for (int i = 0; i < SIDE; i++)
            {
                for (int j = 0; j < SIDE; j++)
                {
                    for (int k = 0; k < SIDE; k++)
                    {
                        if (i % 2 == 0 && j % 2 == 0 && k % 2 == 0)
                        {
                            continue;
                        }

                        float u = i / 2F;
                        float v = j / 2F;
                        float w = k / 2F;
                        int at = ((i * SIDE) + j) * SIDE + k;

                        for (int axis = 0; axis < 3; axis++)
                        {
                            float sum = 0F;

                            for (int a = 0; a < 2; a++)
                            {
                                for (int b = 0; b < 2; b++)
                                {
                                    for (int c = 0; c < 2; c++)
                                    {
                                        float weight = (a == 0 ? 1F - u : u) * (b == 0 ? 1F - v : v) * (c == 0 ? 1F - w : w);

                                        sum += weight * all[(((a * 2) * SIDE) + (b * 2)) * SIDE * 3 + (c * 2) * 3 + axis];
                                    }
                                }
                            }

                            all[at * 3 + axis] = sum;
                        }
                    }
                }
            }

            this.points.set(all);
        }

        this.dimensions.set(next);
    }

    /** The control point offsets, 3 floats a slot, on the stored 3x3x3 grid; a keyframe track */
    public static class Points extends BaseKeyframeFactoryValue<float[]>
    {
        public Points(String id)
        {
            super(id, KeyframeFactories.LATTICE, new float[SLOTS * 3]);
        }

        public boolean isZero()
        {
            for (float value : this.get())
            {
                if (value != 0F)
                {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * The bulge sphere, a keyframe track of its own: centre x, y, z (blocks, the actor's frame), radius
     * and strength. Every point of the model inside the sphere is pushed away from its centre by
     * {@code strength * (1 - distance / radius)^2}, so a sphere keyed along a pipe sends a swelling down it.
     */
    public static class Bulge extends BaseKeyframeFactoryValue<float[]>
    {
        public Bulge(String id)
        {
            super(id, KeyframeFactories.BULGE, KeyframeFactories.BULGE.createEmpty());
        }
    }

    /**
     * The centre of the cage, a keyframe track of its own: x, y, z in blocks (the actor's frame), added to
     * the shift. Keyed from one place to another, the whole lattice glides along its curve, bending
     * whatever it passes over.
     */
    public static class Center extends BaseKeyframeFactoryValue<float[]>
    {
        public Center(String id)
        {
            super(id, KeyframeFactories.CENTER, KeyframeFactories.CENTER.createEmpty());
        }
    }
}
