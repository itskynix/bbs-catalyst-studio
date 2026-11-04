package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.Arrays;

/**
 * Keyframe value holding the control point offsets of a form's lattice: 27 points of 3 floats (the
 * stored 3x3x3 grid, see {@code ValueLattice}). Every number is interpolated on its own through the
 * keyframe's curve, so the lattice squashes, stretches and breathes between its keys; a key shorter
 * than the grid (an older save) reads as zeros for the rest.
 */
public class LatticeKeyframeFactory implements IKeyframeFactory<float[]>
{
    /** The lattice's control points: 27 of 3 floats */
    public static final int LENGTH = 27 * 3;
    /** The bulge sphere: centre x, y, z, radius, strength */
    public static final int BULGE_LENGTH = 5;
    /** The lattice's centre: x, y, z, an offset in blocks */
    public static final int CENTER_LENGTH = 3;

    private final int length;

    public LatticeKeyframeFactory(int length)
    {
        this.length = length;
    }

    @Override
    public float[] fromData(BaseType data)
    {
        float[] points = this.createEmpty();

        if (data instanceof ListType list)
        {
            for (int i = 0; i < this.length && i < list.size(); i++)
            {
                points[i] = list.getFloat(i);
            }
        }

        return points;
    }

    @Override
    public BaseType toData(float[] value)
    {
        ListType list = new ListType();

        for (float number : value)
        {
            list.addFloat(number);
        }

        return list;
    }

    @Override
    public float[] createEmpty()
    {
        float[] empty = new float[this.length];

        /* A bulge sphere starts a half block wide and with no strength */
        if (this.length == BULGE_LENGTH)
        {
            empty[3] = 0.5F;
        }

        return empty;
    }

    @Override
    public float[] copy(float[] value)
    {
        return Arrays.copyOf(value, this.length);
    }

    @Override
    public boolean compare(Object a, Object b)
    {
        return a instanceof float[] x && b instanceof float[] y && Arrays.equals(x, y);
    }

    @Override
    public int contentHash(float[] value)
    {
        return Arrays.hashCode(value);
    }

    @Override
    public float[] interpolate(Keyframe<float[]> preA, Keyframe<float[]> a, Keyframe<float[]> b, Keyframe<float[]> postB, IInterp interpolation, float x)
    {
        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            boolean clamped = interpolation.has(Interpolations.AUTO_CLAMPED);
            float[] result = new float[this.length];

            for (int i = 0; i < this.length; i++)
            {
                result[i] = (float) AutoBezier.get(
                    at(preA.getValue(), i), at(a.getValue(), i), at(b.getValue(), i), at(postB.getValue(), i),
                    preA.getTick(), a.getTick(), b.getTick(), postB.getTick(), clamped, x
                );
            }

            return result;
        }

        return IKeyframeFactory.super.interpolate(preA, a, b, postB, interpolation, x);
    }

    @Override
    public float[] interpolate(float[] preA, float[] a, float[] b, float[] postB, IInterp interpolation, float x)
    {
        float[] result = new float[this.length];

        for (int i = 0; i < this.length; i++)
        {
            result[i] = (float) interpolation.interpolate(IInterp.context.set(at(preA, i), at(a, i), at(b, i), at(postB, i), x));
        }

        return result;
    }

    private static float at(float[] values, int i)
    {
        return values != null && i < values.length ? values[i] : 0F;
    }
}
