package mchorse.bbs_mod.forms.renderers.utils;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Vector4f;

/**
 * Cuts what is drawn through it by a plane, on the CPU: for the forms whose programs don't know the
 * 3D slice's plane (it lives in the BBS model shader), text above all - the glyphs go through vanilla's
 * text programs and the solid body and its effects through the flat colour one.
 *
 * <p>It gathers a primitive (a quad of the text layers, or a triangle of a triangle buffer), keeps it
 * whole when it lies on the kept side, drops it when it lies on the other, and otherwise clips it
 * against the plane (every value of a vertex - colour, texture, light, normal - goes along the cut)
 * and hands the polygon that is left on in the primitive the buffer takes: a fan of triangles, or of
 * quads with the last corner doubled.</p>
 *
 * <p>The plane is in the space the vertices arrive in (after the matrix a writer passes along); a
 * point is kept where {@code dot(plane.xyz, p) + plane.w >= 0}.</p>
 */
public class SliceClipVertexConsumer implements VertexConsumer
{
    /* Numbers per vertex: position 3, colour 4, texture 2, overlay 2, light 2, normal 3 */
    private static final int STRIDE = 16;
    private static final int MAX_POINTS = 8;

    private final VertexConsumer target;
    private final Vector4f plane;
    private final int corners;

    private final float[] current = new float[STRIDE];
    private final float[] primitive = new float[4 * STRIDE];
    private final float[] polygon = new float[MAX_POINTS * STRIDE];
    private final float[] clipped = new float[MAX_POINTS * STRIDE];
    private int count;

    /**
     * @param corners 4 for a buffer of quads, 3 for one of triangles
     */
    public SliceClipVertexConsumer(VertexConsumer target, Vector4f plane, int corners)
    {
        this.target = target;
        this.plane = plane;
        this.corners = corners;

        this.current[6] = 255F;
        this.current[15] = 1F;
    }

    //? if >=1.21 {
    /*@Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        this.flush();
        this.pending = true;
        this.current[0] = x;
        this.current[1] = y;
        this.current[2] = z;

        return this;
    }

    private boolean pending;

    private void flush()
    {
        if (this.pending)
        {
            this.pending = false;
            this.finish();
        }
    }
    *///?} else {
    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        this.current[0] = (float) x;
        this.current[1] = (float) y;
        this.current[2] = (float) z;

        return this;
    }

    @Override
    public void next()
    {
        this.finish();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha)
    {
        this.target.fixedColor(red, green, blue, alpha);
    }

    @Override
    public void unfixColor()
    {
        this.target.unfixColor();
    }
    //?}

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.current[3] = red;
        this.current[4] = green;
        this.current[5] = blue;
        this.current[6] = alpha;

        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.current[7] = u;
        this.current[8] = v;

        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        this.current[9] = u;
        this.current[10] = v;

        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.current[11] = u;
        this.current[12] = v;

        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.current[13] = x;
        this.current[14] = y;
        this.current[15] = z;

        return this;
    }

    private void finish()
    {
        System.arraycopy(this.current, 0, this.primitive, this.count * STRIDE, STRIDE);

        this.count += 1;

        if (this.count == this.corners)
        {
            this.count = 0;
            this.emit();
        }
    }

    private float side(float[] points, int i)
    {
        int o = i * STRIDE;

        return this.plane.x * points[o] + this.plane.y * points[o + 1] + this.plane.z * points[o + 2] + this.plane.w;
    }

    private void emit()
    {
        int n = this.corners;
        int kept = 0;

        for (int i = 0; i < n; i++)
        {
            if (this.side(this.primitive, i) >= 0F)
            {
                kept += 1;
            }
        }

        if (kept == 0)
        {
            return;
        }

        if (kept == n)
        {
            for (int i = 0; i < n; i++)
            {
                this.write(this.primitive, i);
            }

            return;
        }

        /* Sutherland-Hodgman against the one plane */
        int out = 0;

        for (int i = 0; i < n; i++)
        {
            int j = (i + 1) % n;
            float a = this.side(this.primitive, i);
            float b = this.side(this.primitive, j);

            if (a >= 0F)
            {
                System.arraycopy(this.primitive, i * STRIDE, this.polygon, out * STRIDE, STRIDE);
                out += 1;
            }

            if ((a >= 0F) != (b >= 0F))
            {
                float t = a / (a - b);
                int o = out * STRIDE;

                for (int k = 0; k < STRIDE; k++)
                {
                    float from = this.primitive[i * STRIDE + k];

                    this.polygon[o + k] = from + (this.primitive[j * STRIDE + k] - from) * t;
                }

                out += 1;
            }
        }

        System.arraycopy(this.polygon, 0, this.clipped, 0, out * STRIDE);

        for (int i = 1; i + 1 < out; i++)
        {
            this.write(this.clipped, 0);
            this.write(this.clipped, i);
            this.write(this.clipped, i + 1);

            if (this.corners == 4)
            {
                this.write(this.clipped, i + 1);
            }
        }
    }

    private void write(float[] points, int i)
    {
        int o = i * STRIDE;

        this.target.vertex(points[o], points[o + 1], points[o + 2]);
        this.target.color(Math.round(points[o + 3]), Math.round(points[o + 4]), Math.round(points[o + 5]), Math.round(points[o + 6]));
        this.target.texture(points[o + 7], points[o + 8]);
        this.target.overlay(Math.round(points[o + 9]), Math.round(points[o + 10]));
        this.target.light(Math.round(points[o + 11]), Math.round(points[o + 12]));
        this.target.normal(points[o + 13], points[o + 14], points[o + 15]);
        //? if <1.21
        this.target.next();
    }
}
