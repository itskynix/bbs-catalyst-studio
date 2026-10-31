package mchorse.bbs_mod.forms.renderers.utils;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Breaks everything drawn through it into pieces. It holds the vertices of a quad until all four are
 * in, cuts the quad into small tiles, and hands each tile on moved to where {@link ShatterField} has
 * put the chunk it falls in - so a block, an item or a block entity, whatever vanilla draws them from,
 * comes apart without the draw knowing. Quads are the only thing the entity layers a form draws with
 * are made of.
 */
public class ShatterVertexConsumer implements VertexConsumer
{
    /** A quad is never cut into more than this many tiles along an edge */
    private static final int MAX_TILES = 6;

    private final VertexConsumer target;
    private final ShatterField field;

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

    /* The quad being gathered: 4 vertices of 14 numbers each */
    private final float[] quad = new float[4 * 14];
    private int count;

    private final Vector3f temp = new Vector3f();

    public ShatterVertexConsumer(VertexConsumer target, ShatterField field)
    {
        this.target = target;
        this.field = field;
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
        int i = this.count * 14;

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
        /* The normal is carried by its own three slots below */
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

    private final float[] normals = new float[4 * 3];

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

    private float edge(int from, int to)
    {
        Vector3f a = this.field.toLocal(this.quad[from * 14], this.quad[from * 14 + 1], this.quad[from * 14 + 2]);
        float ax = a.x;
        float ay = a.y;
        float az = a.z;
        Vector3f b = this.field.toLocal(this.quad[to * 14], this.quad[to * 14 + 1], this.quad[to * 14 + 2]);

        return (float) Math.sqrt((b.x - ax) * (b.x - ax) + (b.y - ay) * (b.y - ay) + (b.z - az) * (b.z - az));
    }

    private void emit()
    {
        float cell = this.field.getCell();
        int tilesU = Math.max(1, Math.min(MAX_TILES, (int) Math.ceil(this.edge(0, 1) / cell)));
        int tilesV = Math.max(1, Math.min(MAX_TILES, (int) Math.ceil(this.edge(0, 3) / cell)));

        for (int j = 0; j < tilesV; j++)
        {
            for (int i = 0; i < tilesU; i++)
            {
                this.tile(i / (float) tilesU, (i + 1) / (float) tilesU, j / (float) tilesV, (j + 1) / (float) tilesV);
            }
        }
    }

    /** A value of the quad at (s, t): corners 0 and 1 run along s, 0 and 3 along t. */
    private float at(int slot, float s, float t)
    {
        float top = this.quad[slot] + (this.quad[14 + slot] - this.quad[slot]) * s;
        float bottom = this.quad[3 * 14 + slot] + (this.quad[2 * 14 + slot] - this.quad[3 * 14 + slot]) * s;

        return top + (bottom - top) * t;
    }

    private float normalAt(int slot, float s, float t)
    {
        float top = this.normals[slot] + (this.normals[3 + slot] - this.normals[slot]) * s;
        float bottom = this.normals[9 + slot] + (this.normals[6 + slot] - this.normals[9 + slot]) * s;

        return top + (bottom - top) * t;
    }

    private void tile(float s0, float s1, float t0, float t1)
    {
        float midS = (s0 + s1) / 2F;
        float midT = (t0 + t1) / 2F;
        Matrix4f move = this.field.at(this.at(0, midS, midT), this.at(1, midS, midT), this.at(2, midS, midT));

        if (move == null)
        {
            return;
        }

        this.corner(move, s0, t0);
        this.corner(move, s1, t0);
        this.corner(move, s1, t1);
        this.corner(move, s0, t1);
    }

    private void corner(Matrix4f move, float s, float t)
    {
        Vector3f p = move.transformPosition(this.at(0, s, t), this.at(1, s, t), this.at(2, s, t), this.temp);
        float px = p.x;
        float py = p.y;
        float pz = p.z;
        Vector3f n = move.transformDirection(this.normalAt(0, s, t), this.normalAt(1, s, t), this.normalAt(2, s, t), this.temp);
        float length = (float) Math.sqrt(n.x * n.x + n.y * n.y + n.z * n.z);

        if (length > 0.0001F)
        {
            n.div(length);
        }

        this.target.vertex(px, py, pz);
        this.target.color(Math.round(this.at(3, s, t)), Math.round(this.at(4, s, t)), Math.round(this.at(5, s, t)), Math.round(this.at(6, s, t)));
        this.target.texture(this.at(7, s, t), this.at(8, s, t));
        this.target.overlay(Math.round(this.at(9, s, t)), Math.round(this.at(10, s, t)));
        this.target.light(Math.round(this.at(11, s, t)), Math.round(this.at(12, s, t)));
        this.target.normal(n.x, n.y, n.z);
        //? if <1.21
        this.target.next();
    }
}
