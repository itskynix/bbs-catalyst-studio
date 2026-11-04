package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueVehicleParts;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where a vehicle's parts are: the box a door fills, the seat behind it, which way it swings open,
 * and the matrices that take the model's pixel space to the world, so a click can be laid against
 * them.
 *
 * <p>Pixel space is the model's own (the space cube origins and bone pivots are written in); the
 * renderer turns it half a turn, so there the front is -Z and left is -X, and in the actor's frame
 * the front is +Z and left +X, as the vehicle IK reads its motion.</p>
 */
public class VehicleGeometry
{
    /** The parts found by name for a form whose list is empty, with the model they were found in */
    private static final Map<ModelForm, Object[]> GUESSED = new WeakHashMap<>();
    /** The wheels worked out for a form, with the model and the bone names they were worked out from */
    private static final Map<ModelForm, Object[]> WHEELS = new WeakHashMap<>();

    /**
     * The four wheels of a vehicle at rest, in the actor's frame (blocks; front +Z, left +X, up from
     * the actor's feet): where each one touches the ground, which way the model faces, and the box
     * the whole model fills. Everything that stands the vehicle on the ground (a drive, a parked
     * vehicle settling onto its wheels) and every seat is placed from this, so they all agree.
     */
    public static final class Wheels
    {
        /** FL, FR, RL, RR: x, the height the wheel touches the ground at, z */
        public final Vector3f[] points = new Vector3f[4];
        /** +1 when the front wheels are ahead of the middle (+Z), -1 for a model built facing back; the same for left (+X) */
        public float front = 1F;
        public float left = 1F;
        /** The middle of the four wheels, at the height they touch the ground on average */
        public final Vector3f middle = new Vector3f();
        /** The model's box */
        public final Vector3f footMin = new Vector3f(-1F, 0F, -2F);
        public final Vector3f footMax = new Vector3f(1F, 1.6F, 2F);
        /** Whether any wheel came from a bone rather than the corners of the box */
        public boolean fromBones;

        public float wheelbase()
        {
            return Math.max(Math.abs((this.points[0].z + this.points[1].z) - (this.points[2].z + this.points[3].z)) / 2F, 0.5F);
        }

        public float track()
        {
            return Math.max(Math.abs((this.points[0].x + this.points[2].x) - (this.points[1].x + this.points[3].x)) / 2F, 0.5F);
        }
    }

    /** A point of pixel space in the actor's frame: a sixteenth, turned half a turn */
    public static Vector3f toActor(Vector3f pixel)
    {
        return new Vector3f(-pixel.x / 16F, pixel.y / 16F, -pixel.z / 16F);
    }

    public static Wheels wheels(ModelForm form)
    {
        Model model = getModel(form);
        String[] bones = {form.boneWheelFL.get(), form.boneWheelFR.get(), form.boneWheelRL.get(), form.boneWheelRR.get()};
        String key = String.join("\n", bones) + "\n" + form.wheelRadius.get();
        Object[] cached = WHEELS.get(form);

        if (cached != null && cached[0] == model && key.equals(cached[1]))
        {
            return (Wheels) cached[2];
        }

        Wheels wheels = new Wheels();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        if (modelBounds(model, min, max))
        {
            /* Half a turn swaps which side of the box is the least */
            wheels.footMin.set(-max.x / 16F, min.y / 16F, -max.z / 16F);
            wheels.footMax.set(-min.x / 16F, max.y / 16F, -min.z / 16F);
        }

        Vector3f middle = new Vector3f(wheels.footMin).add(wheels.footMax).mul(0.5F);
        boolean[] found = new boolean[4];

        for (int i = 0; i < 4; i++)
        {
            ModelGroup group = bones[i].isEmpty() || model == null ? null : model.getGroup(bones[i]);

            if (group == null)
            {
                continue;
            }

            Vector3f pivot = toActor(group.initial.translate);
            /* It touches the ground one wheel radius under its pivot: the radius set in the tab when it was
             * set (the tyre's own number, to the pixel), otherwise the wheel's lowest point; with no geometry, the radius */
            float contact = !form.wheelRadius.isDefault() || !bounds(model, bones[i], min, max) ? pivot.y - form.wheelRadius.get() : min.y / 16F;

            wheels.points[i] = new Vector3f(pivot.x, contact, pivot.z);
            found[i] = true;
            wheels.fromBones = true;
        }

        /* Which way is front and which is left, as the modeller named the wheels: a front wheel ahead
         * of the middle, a left one on the +X side (a right one on the other) */
        for (int i = 0; i < 4; i++)
        {
            if (found[i])
            {
                boolean ahead = wheels.points[i].z >= middle.z;
                boolean onPlus = wheels.points[i].x >= middle.x;

                wheels.front = (i < 2) == ahead ? 1F : -1F;
                wheels.left = (i % 2 == 0) == onPlus ? 1F : -1F;

                break;
            }
        }

        /* A missing wheel mirrors its partner across (FL-FR, RL-RR), or without one stands most of
         * the way to its corner of the box */
        int[] partner = {1, 0, 3, 2};
        float halfWidth = (wheels.footMax.x - wheels.footMin.x) / 2F;
        float halfLength = (wheels.footMax.z - wheels.footMin.z) / 2F;

        for (int i = 0; i < 4; i++)
        {
            if (found[i])
            {
                continue;
            }

            if (found[partner[i]])
            {
                Vector3f other = wheels.points[partner[i]];

                wheels.points[i] = new Vector3f(2F * middle.x - other.x, other.y, other.z);
            }
            else
            {
                float side = (i % 2 == 0 ? 1F : -1F) * wheels.left;
                float row = (i < 2 ? 1F : -1F) * wheels.front;

                wheels.points[i] = new Vector3f(middle.x + side * halfWidth * 0.8F, wheels.footMin.y, middle.z + row * halfLength * 0.65F);
            }
        }

        for (Vector3f point : wheels.points)
        {
            wheels.middle.add(point);
        }

        wheels.middle.mul(0.25F);

        WHEELS.put(form, new Object[] {model, key, wheels});

        return wheels;
    }

    /**
     * The parts that open: the form's own list, or - when nobody filled it - the doors and lids its
     * bones are named as, so a vehicle answers a click before its tab was ever opened. The guess is
     * kept per form and made again when the model changes.
     */
    public static List<ValueVehicleParts.Part> parts(ModelForm form)
    {
        List<ValueVehicleParts.Part> own = form.vehicleParts.getAllTyped();

        if (!own.isEmpty())
        {
            return own;
        }

        ModelInstance instance = ModelFormRenderer.getModel(form);
        Object key = instance == null ? null : instance.model;
        Object[] guessed = GUESSED.get(form);

        if (guessed != null && guessed[0] == key)
        {
            @SuppressWarnings("unchecked")
            List<ValueVehicleParts.Part> list = (List<ValueVehicleParts.Part>) guessed[1];

            return list;
        }

        List<ValueVehicleParts.Part> list = new ArrayList<>();

        if (key != null)
        {
            Model model = getModel(form);
            Map<String, String> parents = new HashMap<>();

            if (model != null)
            {
                for (ModelGroup group : model.getAllGroups())
                {
                    if (group.parent != null)
                    {
                        parents.put(group.id, group.parent.id);
                    }
                }
            }

            for (Map.Entry<String, Integer> entry : VehicleBoneDetector.detectParts(FormUtilsClient.getBones(form), parents::get).entrySet())
            {
                ValueVehicleParts.Part part = new ValueVehicleParts.Part("");
                int axis = entry.getValue();

                part.bone.set(entry.getKey());
                part.axis.set(axis);
                part.angle.set(openingSign(model, form, entry.getKey(), axis) * (axis == ValueVehicleParts.AXIS_PITCH ? 60F : 75F));
                list.add(part);
            }
        }

        list = Collections.unmodifiableList(list);
        GUESSED.put(form, new Object[] {key, list});

        return list;
    }

    /**
     * Where a bone's pivot is in the world, relative to ({@code cx}, {@code cy}, {@code cz}), read
     * straight from the bone matrices: works for any model with bones, whatever its format, and
     * whether or not the bone has any geometry. Null when there is no such bone.
     */
    public static Vector3f boneOrigin(IEntity entity, ModelForm form, String bone, float transition, double cx, double cy, double cz)
    {
        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        MatrixCache cache = FormFrameCache.collect(null, form, entity, transition);
        Matrix4f matrix = cache.has(bone) ? cache.get(bone).matrix() : null;

        if (matrix == null)
        {
            return null;
        }

        return FilmMatrices.getMatrixForRenderWithRotation(entity, cx, cy, cz, transition).mul(matrix).getTranslation(new Vector3f());
    }

    public static Model getModel(ModelForm form)
    {
        ModelInstance instance = ModelFormRenderer.getModel(form);

        return instance != null && instance.model instanceof Model model ? model : null;
    }

    /** The box a bone and everything inside it fill, in pixel space */
    public static boolean bounds(Model model, String bone, Vector3f min, Vector3f max)
    {
        ModelGroup group = model == null || bone == null ? null : model.getGroup(bone);

        if (group == null)
        {
            return false;
        }

        min.set(Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY);

        return union(group, min, max);
    }

    private static boolean union(ModelGroup group, Vector3f min, Vector3f max)
    {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        boolean any = false;

        if (group.getGeometryBounds(a, b))
        {
            min.min(a);
            max.max(b);
            any = true;
        }

        for (ModelGroup child : group.children)
        {
            any |= union(child, min, max);
        }

        return any;
    }

    /** The box the whole model fills, in pixel space */
    public static boolean modelBounds(Model model, Vector3f min, Vector3f max)
    {
        min.set(Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY);

        boolean any = false;

        if (model == null)
        {
            return false;
        }

        for (ModelGroup group : model.getAllGroups())
        {
            if (group.parent == null)
            {
                any |= union(group, min, max);
            }
        }

        return any;
    }

    /** The middle of the whole model (or of its body, when one is picked), in pixel space */
    public static Vector3f center(Model model, ModelForm form)
    {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        boolean any = !form.boneBody.get().isEmpty() && bounds(model, form.boneBody.get(), min, max);

        if (!any && model != null)
        {
            for (ModelGroup group : model.getAllGroups())
            {
                if (group.parent == null)
                {
                    any |= union(group, min, max);
                }
            }
        }

        return any ? min.add(max).mul(0.5F) : new Vector3f();
    }

    /**
     * Which way a part opens: a door swings its free end away from the middle, a lid lifts.
     * Tried both ways with the rotation the renderer gives it; the one that opens wins.
     */
    public static float openingSign(Model model, ModelForm form, String bone, int axis)
    {
        ModelGroup group = model == null ? null : model.getGroup(bone);
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        if (group == null || !bounds(model, bone, min, max))
        {
            return 1F;
        }

        Vector3f pivot = group.initial.translate;
        Vector3f middle = center(model, form);
        Vector3f arm = new Vector3f(min).add(max).mul(0.5F).sub(pivot);
        float best = Float.NEGATIVE_INFINITY;
        float sign = 1F;

        for (float s : new float[] {1F, -1F})
        {
            float angle = MathUtils.toRad(40F * s);
            /* A bone turned about its yaw swings the opposite way to the same turn of its pixel space (the
             * model's X is mirrored); about its pitch the two agree */
            Quaternionf rotation = axis == ValueVehicleParts.AXIS_PITCH ? new Quaternionf().rotationX(angle) : new Quaternionf().rotationY(-angle);
            Vector3f moved = rotation.transform(new Vector3f(arm)).add(pivot);
            float score = axis == ValueVehicleParts.AXIS_PITCH ? moved.y : Math.abs(moved.x - middle.x);

            if (score > best)
            {
                best = score;
                sign = s;
            }
        }

        return sign;
    }

    /**
     * The seat behind a door, in pixel space: in from the door toward the middle, at the height of
     * the door's lower third (where a cushion sits against a door card).
     */
    public static Vector3f seat(Model model, ModelForm form, String door)
    {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        if (!bounds(model, door, min, max))
        {
            return null;
        }

        Vector3f middle = center(model, form);
        Vector3f c = new Vector3f(min).add(max).mul(0.5F);

        return new Vector3f(middle.x + (c.x - middle.x) * 0.45F, min.y + (max.y - min.y) * 0.3F, c.z);
    }

    /**
     * The matrix that takes a bone's pixel space to the world, relative to ({@code cx}, {@code cy},
     * {@code cz}); an empty bone is the model's root. Null when the model has no such bone.
     */
    public static Matrix4f toWorld(IEntity entity, ModelForm form, String bone, float transition, double cx, double cy, double cz)
    {
        Model model = getModel(form);
        ModelGroup group = model == null || bone == null || bone.isEmpty() ? null : model.getGroup(bone);
        MatrixCache cache = FormFrameCache.collect(null, form, entity, transition);
        Matrix4f matrix = cache.get(group == null ? "" : bone).matrix();

        if (matrix == null)
        {
            return null;
        }

        Matrix4f world = FilmMatrices.getMatrixForRenderWithRotation(entity, cx, cy, cz, transition).mul(matrix);

        /* The cache's bone frame sits on the pivot, turned half a turn; the root's is the form's
         * frame, before the model's own half turn */
        world.rotateY(MathUtils.PI);

        if (group != null)
        {
            Vector3f pivot = group.initial.translate;

            world.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
        }

        return world.scale(1F / 16F);
    }

    /** A point of a bone's pixel space in the world (relative to the same origin as the matrix) */
    public static Vector3f transform(Matrix4f matrix, Vector3f pixel)
    {
        Vector4f point = matrix.transform(new Vector4f(pixel, 1F));

        return new Vector3f(point.x, point.y, point.z);
    }

    /**
     * How far along a ray (from the matrix's origin, in {@code direction}) it enters the pixel space
     * box; negative when it misses.
     */
    public static float rayBox(Matrix4f matrix, Vector3f direction, Vector3f min, Vector3f max)
    {
        Matrix4f inverse = new Matrix4f(matrix).invert();
        Vector4f o = inverse.transform(new Vector4f(0F, 0F, 0F, 1F));
        Vector4f d = inverse.transform(new Vector4f(direction, 0F));
        float near = Float.NEGATIVE_INFINITY;
        float far = Float.POSITIVE_INFINITY;
        float[] origin = {o.x, o.y, o.z};
        float[] dir = {d.x, d.y, d.z};
        float[] lo = {min.x, min.y, min.z};
        float[] hi = {max.x, max.y, max.z};

        for (int i = 0; i < 3; i++)
        {
            if (Math.abs(dir[i]) < 1.0E-6F)
            {
                if (origin[i] < lo[i] || origin[i] > hi[i])
                {
                    return -1F;
                }

                continue;
            }

            float t1 = (lo[i] - origin[i]) / dir[i];
            float t2 = (hi[i] - origin[i]) / dir[i];

            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }

        if (far < Math.max(near, 0F))
        {
            return -1F;
        }

        return Math.max(near, 0F);
    }

    /** How far along a ray it passes through a sphere around {@code point}; negative when it misses */
    public static float raySphere(Vector3f point, Vector3f direction, float radius)
    {
        float t = point.dot(direction);

        if (t < 0F)
        {
            return -1F;
        }

        float distance = new Vector3f(direction).mul(t).distance(point);

        return distance <= radius ? t : -1F;
    }
}
