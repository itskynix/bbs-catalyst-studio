package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.cubic.render.LatticeDeform;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bends everything drawn through it by a lattice. It holds the vertices of a quad until all four are
 * in, cuts the quad into a small grid so the lattice can curve it, and hands each cell on with every
 * corner moved - so a block, an item or a block entity, whatever vanilla draws them from, bends
 * without the draw knowing. Quads are the only thing the entity layers a form draws with are made of.
 *
 * <p>The vertices arrive in the space the draw writes them in; the lattice lives in the form's own
 * frame, so each corner goes there, is bent, and comes back.</p>
 */
public class LatticeVertexConsumer implements VertexConsumer
{
    /** Cells along an edge of a quad: enough to see a curve, few enough to be cheap */
    private static final int CELLS = 4;

    private final VertexConsumer target;
    private final LatticeDeform deform;
    private final Matrix4f forward = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();

    /* The vertex being written */
    private double x;
    private double y;
    private double z;
    private int r = 255;
    private int g = 255;
    private int b = 255;
    private int a = 255;
    private float u;
    private float v;
    private int overlayU;
    private int overlayV;
    private int lightU;
    private int lightV;
    private float nx;
    private float ny;
    private float nz = 1F;

    /* The quad being gathered: 4 vertices of 13 numbers each, and their normals */
    private final float[] quad = new float[4 * 13];
    private final float[] normals = new float[4 * 3];
    private int count;

    private final Vector3f temp = new Vector3f();

    /**
     * @param forward takes the form's frame to the space the vertices are written in
     */
    public LatticeVertexConsumer(VertexConsumer target, Matrix4f forward, LatticeDeform deform)
    {
        this.target = target;
        this.deform = deform;
        this.forward.set(forward);
        this.inverse.set(forward).invert();
    }

    //? if >=1.21 {
    /*@Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        /^ Vertices have no explicit end since 1.21, so one is written when the next begins ^/
        this.flush();
        this.pending = true;

        this.x = x;
        this.y = y;
        this.z = z;

        return this;
    }
    *///?} else {
    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;

        return this;
    }
    //?}

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.r = red;
        this.g = green;
        this.b = blue;
        this.a = alpha;

        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.u = u;
        this.v = v;

        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        this.overlayU = u;
        this.overlayV = v;

        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.lightU = u;
        this.lightV = v;

        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.nx = x;
        this.ny = y;
        this.nz = z;

        //? if >=1.21 {
        /*/^ Every writer ends a vertex with its normal ^/
        this.flush();
        *///?}

        return this;
    }

    //? if >=1.21 {
    /*private boolean pending;

    private void flush()
    {
        if (this.pending)
        {
            this.pending = false;

            this.finish();
        }
    }
    *///?}

    private void finish()
    {
        int i = this.count * 13;

        this.quad[i] = (float) this.x;
        this.quad[i + 1] = (float) this.y;
        this.quad[i + 2] = (float) this.z;
        this.quad[i + 3] = this.r;
        this.quad[i + 4] = this.g;
        this.quad[i + 5] = this.b;
        this.quad[i + 6] = this.a;
        this.quad[i + 7] = this.u;
        this.quad[i + 8] = this.v;
        this.quad[i + 9] = this.overlayU;
        this.quad[i + 10] = this.overlayV;
        this.quad[i + 11] = this.lightU;
        this.quad[i + 12] = this.lightV;
        this.normals[this.count * 3] = this.nx;
        this.normals[this.count * 3 + 1] = this.ny;
        this.normals[this.count * 3 + 2] = this.nz;

        this.count += 1;

        if (this.count == 4)
        {
            this.count = 0;

            this.emit();
        }
    }

    //? if <1.21 {
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

    /* Cutting */

    /** A value of the quad at (s, t): corners 0 and 1 run along s, 0 and 3 along t. */
    private float at(int slot, float s, float t)
    {
        float top = this.quad[slot] + (this.quad[13 + slot] - this.quad[slot]) * s;
        float bottom = this.quad[3 * 13 + slot] + (this.quad[2 * 13 + slot] - this.quad[3 * 13 + slot]) * s;

        return top + (bottom - top) * t;
    }

    private float normalAt(int slot, float s, float t)
    {
        float top = this.normals[slot] + (this.normals[3 + slot] - this.normals[slot]) * s;
        float bottom = this.normals[9 + slot] + (this.normals[6 + slot] - this.normals[9 + slot]) * s;

        return top + (bottom - top) * t;
    }

    private void emit()
    {
        for (int j = 0; j < CELLS; j++)
        {
            for (int i = 0; i < CELLS; i++)
            {
                float s0 = i / (float) CELLS;
                float s1 = (i + 1) / (float) CELLS;
                float t0 = j / (float) CELLS;
                float t1 = (j + 1) / (float) CELLS;

                this.corner(s0, t0);
                this.corner(s1, t0);
                this.corner(s1, t1);
                this.corner(s0, t1);
            }
        }
    }

    private void corner(float s, float t)
    {
        /* Into the form's frame, bent, and back out to where the draw writes */
        this.inverse.transformPosition(this.at(0, s, t), this.at(1, s, t), this.at(2, s, t), this.temp);
        this.deform.apply(this.temp);
        this.forward.transformPosition(this.temp);

        this.target.vertex(this.temp.x, this.temp.y, this.temp.z);
        this.target.color(Math.round(this.at(3, s, t)), Math.round(this.at(4, s, t)), Math.round(this.at(5, s, t)), Math.round(this.at(6, s, t)));
        this.target.texture(this.at(7, s, t), this.at(8, s, t));
        this.target.overlay(Math.round(this.at(9, s, t)), Math.round(this.at(10, s, t)));
        this.target.light(Math.round(this.at(11, s, t)), Math.round(this.at(12, s, t)));
        this.target.normal(this.normalAt(0, s, t), this.normalAt(1, s, t), this.normalAt(2, s, t));
        //? if <1.21
        this.target.next();
    }
}
