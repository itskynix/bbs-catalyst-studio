package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

/**
 * Separate-dimensions Bezier for composite keyframe values. A keyframe whose value is a
 * {@link Transform} (alone, or one bone of a pose) can carry an independent handle set for each of
 * its nine components - position, rotation and scale, X/Y/Z - stored on the {@link Keyframe} under
 * the component's key (see {@link #KEYS}; a pose bone's keys are prefixed {@code "bone/"}).
 *
 * <p>Applying is an overlay on the ordinary result: the segment is interpolated exactly as before
 * (a composite {@code BEZIER} has always meant Hermite), and only the components that have handles
 * on either end are then re-solved with {@link CubicBezierSolver} on their own curve. A keyframe
 * with no component handles therefore changes nothing - old scenes open and play exactly as they
 * did. A component with handles on one end only takes the other end's default: the key's own
 * time handle length and no value offset.</p>
 *
 * <p>Handle values are in the transform's stored units (the same numbers the value itself holds).
 * Rotation components are only solved in euler mode; a quaternion-mode rotation keeps its
 * per-component quaternion curve.</p>
 */
public final class ComponentBezier
{
    /** Component keys, in order: translate X/Y/Z, rotate X/Y/Z, scale X/Y/Z. */
    public static final String[] KEYS = {"p.x", "p.y", "p.z", "r.x", "r.y", "r.z", "s.x", "s.y", "s.z"};

    private ComponentBezier()
    {}

    /** Whether either end of the segment carries any component handle at all. */
    public static boolean any(Keyframe<?> a, Keyframe<?> b)
    {
        return a.hasComponentHandles() || b.hasComponentHandles();
    }

    /**
     * Re-solves {@code out}'s components that have handles. {@code prefix} is the component key
     * prefix ("" for a lone transform, "bone/" inside a pose).
     */
    public static void apply(Transform out, String prefix, Transform av, Transform bv, Keyframe<?> a, Keyframe<?> b, float x)
    {
        for (int i = 0; i < KEYS.length; i++)
        {
            float[] ha = a.getComponentHandle(prefix + KEYS[i]);
            float[] hb = b.getComponentHandle(prefix + KEYS[i]);

            if (ha == null && hb == null)
            {
                continue;
            }

            boolean rotation = i >= 3 && i < 6;

            if (rotation && out.rotationMode != Transform.RotationMode.EULER)
            {
                continue;
            }

            double aRx = ha != null ? ha[2] : a.rx;
            double aRy = ha != null ? ha[3] : 0D;
            double bLx = hb != null ? hb[0] : b.lx;
            double bLy = hb != null ? hb[1] : 0D;
            double value = CubicBezierSolver.value(get(av, i), get(bv, i), a.getTick(), b.getTick(), aRx, aRy, bLx, bLy, x);

            set(out, i, (float) value);
        }
    }

    /** Index into {@link #KEYS} of a component key, with or without a "bone/" prefix; -1 when unknown. */
    public static int index(String key)
    {
        String name = key.substring(key.lastIndexOf('/') + 1);

        for (int i = 0; i < KEYS.length; i++)
        {
            if (KEYS[i].equals(name))
            {
                return i;
            }
        }

        return -1;
    }

    /** The bone part of a pose component key ("arm/p.x" gives "arm"), empty for a lone transform's. */
    public static String bone(String key)
    {
        int slash = key.lastIndexOf('/');

        return slash < 0 ? "" : key.substring(0, slash);
    }

    /** Displayed units per stored unit: rotation is stored in radians and shown in degrees. */
    public static double unit(int component)
    {
        return component >= 3 && component < 6 ? 180D / Math.PI : 1D;
    }

    private static Vector3f vector(Transform transform, int component)
    {
        return component < 3 ? transform.translate : component < 6 ? transform.rotate : transform.scale;
    }

    public static float get(Transform transform, int component)
    {
        Vector3f vector = vector(transform, component);

        return switch (component % 3)
        {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    public static void set(Transform transform, int component, float value)
    {
        Vector3f vector = vector(transform, component);

        switch (component % 3)
        {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            default -> vector.z = value;
        }
    }
}
