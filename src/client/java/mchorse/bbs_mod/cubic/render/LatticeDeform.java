package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ILatticeForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueLattice;
import mchorse.bbs_mod.forms.renderers.VehicleGeometry;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A form's deformation, ready to bend vertices: the lattice (a box, its control point offsets) and
 * the bulge sphere, and the bones they reach. Works on the model's rest space (blocks, the model's
 * own orientation), so a bone that is posed carries its deformed shape along with it.
 *
 * <p>The lattice box is the target's geometry in the actor's frame (front +Z, left +X, up +Y: the
 * model's X and Z turned half a turn), the frame the editor places it in, moved by the lattice's
 * shift. A vertex inside it is put in the box as {@code (u, v, w)} between 0 and 1 and moved by the
 * Bernstein-weighted sum of the control points' offsets - the trilinear blend of a 2x2x2 lattice,
 * the quadratic one of a 3x3x3 - which leaves it where it was while every offset is zero. A vertex
 * outside the box is left alone.</p>
 *
 * <p>The bulge then pushes whatever is inside its sphere away from the centre by
 * {@code strength * (1 - distance / radius)^2}.</p>
 */
public class LatticeDeform
{
    /** The box of a target: the model and bone it was worked out for, then its corners and the cut */
    private static final Map<ModelForm, Object[]> BOXES = new WeakHashMap<>();

    private final Vector3f min = new Vector3f();
    private final Vector3f size = new Vector3f();
    private final float[] offsets;
    private final int dimensions;
    private final ModelGroup target;
    private final int split;
    private final boolean points;
    private final float[] sphere;
    private final long key;
    /** A model's frame has X and Z turned half a turn from the actor's; a block's or an item's does not */
    private boolean flip = true;

    private final float[] wu = new float[3];
    private final float[] wv = new float[3];
    private final float[] ww = new float[3];

    private LatticeDeform(float[] offsets, int dimensions, ModelGroup target, int split, boolean points, float[] sphere, long key)
    {
        this.offsets = offsets;
        this.dimensions = dimensions;
        this.target = target;
        this.split = split;
        this.points = points;
        this.sphere = sphere;
        this.key = key;
    }

    /**
     * How many pieces each side of a cube face is cut into so the lattice can curve it: all the way
     * for a small model, less as there are more faces to cut, and not at all for a vehicle or a big
     * model (its corners alone are bent).
     */
    public int split()
    {
        return this.split;
    }

    /**
     * What this deformation is made of: the same key means the same bent geometry, so a baked
     * copy of it stays good for as long as the key does.
     */
    public long key()
    {
        return this.key;
    }

    private static int splitFor(Model model, ModelGroup target)
    {
        int quads = 0;

        for (ModelGroup group : model.getAllGroups())
        {
            boolean inside = target == null;

            for (ModelGroup current = group; !inside && current != null; current = current.parent)
            {
                inside = current == target;
            }

            if (inside)
            {
                quads += group.cubes.size() * 6;
            }
        }

        return quads <= 400 ? 6 : quads <= 1500 ? 4 : quads <= 4000 ? 2 : 1;
    }

    /**
     * A block's or an item's deformation, over the box of its own frame, or null when nothing of it
     * bends anything. Not baked: the key only tells a changed lattice from an unchanged one.
     */
    public static LatticeDeform forPlain(ILatticeForm form)
    {
        if (form == null || !form.getLattice().isDeforming())
        {
            return null;
        }

        ValueLattice lattice = form.getLattice();
        Vector3f[] box = box(form);
        boolean points = lattice.isActive();
        float[] sphere = lattice.bulgeActive() ? lattice.bulge.get().clone() : null;
        float[] offsets = lattice.points.get().clone();
        long key = Arrays.hashCode(offsets) * 31L + lattice.dimensions();
        LatticeDeform deform = new LatticeDeform(offsets, lattice.dimensions(), null, 1, points, sphere, key);

        deform.flip = false;
        deform.min.set(box[0]);
        deform.size.set(box[1]).sub(box[0]);

        return deform;
    }

    /** The box a block or an item fills in its own frame (a block stands on its origin, an item is centred), shifted as the lattice is */
    public static Vector3f[] box(ILatticeForm form)
    {
        if (form instanceof ModelForm model)
        {
            return box(model);
        }

        boolean block = form instanceof BlockForm;
        Vector3f min = block ? new Vector3f(-0.5F, 0F, -0.5F) : new Vector3f(-0.5F, -0.5F, -0.5F);
        Vector3f max = block ? new Vector3f(0.5F, 1F, 0.5F) : new Vector3f(0.5F, 0.5F, 0.5F);
        Vector3f shift = form.getLattice().shift();

        return new Vector3f[] {min.add(shift), max.add(shift)};
    }

    /** The form's deformation, or null when nothing of it bends anything or there is nothing to bend */
    public static LatticeDeform of(ModelForm form)
    {
        if (form == null || !form.lattice.isDeforming())
        {
            return null;
        }

        Model model = VehicleGeometry.getModel(form);

        if (model == null)
        {
            return null;
        }

        String bone = form.lattice.targetBone.get();
        ModelGroup target = bone == null || bone.isEmpty() ? null : model.getGroup(bone);

        if (bone != null && !bone.isEmpty() && target == null)
        {
            return null;
        }

        Object[] cached = BOXES.get(form);
        Vector3f[] box;
        int split;

        if (cached != null && cached[0] == model && ((String) cached[1]).equals(bone))
        {
            box = (Vector3f[]) cached[2];
            split = (Integer) cached[3];
        }
        else
        {
            box = rawBox(model, bone);

            if (box == null)
            {
                return null;
            }

            split = splitFor(model, target);
            BOXES.put(form, new Object[] {model, bone, box, split});
        }

        ValueLattice lattice = form.lattice;
        boolean points = lattice.isActive();
        float[] sphere = lattice.bulgeActive() ? lattice.bulge.get().clone() : null;
        float[] offsets = lattice.points.get().clone();
        Vector3f shift = lattice.shift();
        int cut = form.vehicleIk.get() ? 1 : split;
        long key = System.identityHashCode(model);

        key = key * 31 + (points ? Arrays.hashCode(offsets) : 0);
        key = key * 31 + lattice.dimensions();
        key = key * 31 + (sphere == null ? 0 : Arrays.hashCode(sphere));
        key = key * 31 + shift.hashCode();
        key = key * 31 + (bone == null ? 0 : bone.hashCode());
        key = key * 31 + cut;

        LatticeDeform deform = new LatticeDeform(offsets, lattice.dimensions(), target, cut, points, sphere, key);

        deform.min.set(box[0]).add(shift);
        deform.size.set(box[1]).sub(box[0]);

        return deform;
    }

    /** The box a target's geometry fills, in the actor's frame; null for a target with no geometry */
    private static Vector3f[] rawBox(Model model, String bone)
    {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        boolean found = bone == null || bone.isEmpty() ? VehicleGeometry.modelBounds(model, min, max) : VehicleGeometry.bounds(model, bone, min, max);

        if (!found)
        {
            return null;
        }

        Vector3f a = VehicleGeometry.toActor(min);
        Vector3f b = VehicleGeometry.toActor(max);
        Vector3f lo = new Vector3f(a).min(b);
        Vector3f hi = new Vector3f(a).max(b);

        /* A flat target still has a box to be bent in */
        for (int axis = 0; axis < 3; axis++)
        {
            if (hi.get(axis) - lo.get(axis) < 1.0E-3F)
            {
                lo.setComponent(axis, lo.get(axis) - 0.0005F);
                hi.setComponent(axis, hi.get(axis) + 0.0005F);
            }
        }

        return new Vector3f[] {lo, hi};
    }

    /** The box of a form's lattice, shifted as the lattice is, for the editor's wireframe; null without a model or geometry */
    public static Vector3f[] box(ModelForm form)
    {
        Model model = VehicleGeometry.getModel(form);
        Vector3f[] box = model == null ? null : rawBox(model, form.lattice.targetBone.get());

        if (box != null)
        {
            Vector3f shift = form.lattice.shift();

            box[0].add(shift);
            box[1].add(shift);
        }

        return box;
    }

    /** Whether the deformation reaches this bone: the target, everything below it, or all of them with no target */
    public boolean affects(ModelGroup group)
    {
        for (ModelGroup current = group; current != null; current = current.parent)
        {
            if (this.target == null || current == this.target)
            {
                return true;
            }
        }

        return false;
    }

    /** The matrix that takes a bone's own space to the model's at rest: its pivots and initial turns, parents first */
    public static Matrix4f rest(ModelGroup group)
    {
        if (group == null)
        {
            return new Matrix4f();
        }

        Vector3f pivot = group.initial.translate;

        return rest(group.parent)
            .translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F)
            .rotate(Matrices.toLocalRotationZYXDegrees(group.initial.rotate))
            .translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    /** Bend a point of the model's rest space (blocks) in place */
    public void apply(Vector3f point)
    {
        /* The model's X and Z turned half a turn: the actor's frame, which the box and the sphere are in */
        float x = this.flip ? -point.x : point.x;
        float y = point.y;
        float z = this.flip ? -point.z : point.z;

        if (this.points)
        {
            float u = (x - this.min.x) / this.size.x;
            float v = (y - this.min.y) / this.size.y;
            float w = (z - this.min.z) / this.size.z;
            float epsilon = 1.0E-4F;

            if (u >= -epsilon && u <= 1F + epsilon && v >= -epsilon && v <= 1F + epsilon && w >= -epsilon && w <= 1F + epsilon)
            {
                weights(this.dimensions, Math.min(Math.max(u, 0F), 1F), this.wu);
                weights(this.dimensions, Math.min(Math.max(v, 0F), 1F), this.wv);
                weights(this.dimensions, Math.min(Math.max(w, 0F), 1F), this.ww);

                int step = this.dimensions == 2 ? 2 : 1;
                float dx = 0F;
                float dy = 0F;
                float dz = 0F;

                for (int i = 0; i < this.dimensions; i++)
                {
                    for (int j = 0; j < this.dimensions; j++)
                    {
                        for (int k = 0; k < this.dimensions; k++)
                        {
                            float weight = this.wu[i] * this.wv[j] * this.ww[k];
                            int slot = (((i * step) * ValueLattice.SIDE) + (j * step)) * ValueLattice.SIDE * 3 + (k * step) * 3;

                            dx += weight * this.offsets[slot];
                            dy += weight * this.offsets[slot + 1];
                            dz += weight * this.offsets[slot + 2];
                        }
                    }
                }

                x += dx;
                y += dy;
                z += dz;
            }
        }

        if (this.sphere != null)
        {
            float dx = x - this.sphere[0];
            float dy = y - this.sphere[1];
            float dz = z - this.sphere[2];
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float radius = this.sphere[3];

            if (distance < radius)
            {
                float falloff = 1F - distance / radius;
                float push = this.sphere[4] * falloff * falloff;

                /* Straight out from the centre; at the centre itself there is no out, so up */
                if (distance < 1.0E-5F)
                {
                    y += push;
                }
                else
                {
                    x += dx / distance * push;
                    y += dy / distance * push;
                    z += dz / distance * push;
                }
            }
        }

        point.set(this.flip ? -x : x, y, this.flip ? -z : z);
    }

    /** Bernstein weights of a coordinate: linear for two points, quadratic for three */
    private static void weights(int dimensions, float t, float[] out)
    {
        float s = 1F - t;

        if (dimensions == 2)
        {
            out[0] = s;
            out[1] = t;
        }
        else
        {
            out[0] = s * s;
            out[1] = 2F * s * t;
            out[2] = t * t;
        }
    }

    /** Where a control point of an undeformed lattice sits, in the actor's frame, for the editor to draw it */
    public static Vector3f grid(Vector3f[] box, int dimensions, int i, int j, int k)
    {
        float last = dimensions - 1F;

        return new Vector3f(
            box[0].x + (box[1].x - box[0].x) * (i / last),
            box[0].y + (box[1].y - box[0].y) * (j / last),
            box[0].z + (box[1].z - box[0].z) * (k / last)
        );
    }
}
