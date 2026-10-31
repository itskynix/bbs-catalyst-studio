package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the pieces of a shattering form are. The form is cut into chunks - cells of a grid laid over
 * its own frame, or whole bones - and each chunk takes its own direction, drifts, falls, spins and
 * shrinks away to nothing as the progress reaches 1. At 0 nothing has moved, so the chunks tile the
 * form exactly.
 *
 * <p>Whatever draws the form hands each piece's position here and gets back the matrix that moves
 * it, or null when the piece is already gone. Nothing here touches the graphics state.</p>
 */
public class ShatterField
{
    /** The cell a block-sized form is cut into, in the form's own units. */
    public static final float CELL = 0.2F;

    private static final Matrix4f GONE = new Matrix4f();

    private final float progress;
    private final float spread;
    private final int seed;
    private final float cell;
    private final boolean flat;

    /** Takes the form's own frame to the space the vertices are written in, and back. */
    private final Matrix4f forward = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Map<Long, Matrix4f> chunks = new HashMap<>();
    private final Matrix4f temp = new Matrix4f();
    private final Vector3f point = new Vector3f();

    /* Bone mode: the frame the model stands in, to work out which way is down for a bone */
    private Matrix4f rootInverse;

    /**
     * @param forward takes the form's own frame to the space its vertices are written in
     * @param cell the size of a chunk, in the form's own units
     * @param flat cuts along X and Y only, so the front, back and sides of a slab stay together
     */
    public ShatterField(FormFxValues fx, Matrix4f forward, float cell, boolean flat)
    {
        this.progress = fx.shatterProgress.get();
        this.spread = fx.shatterSpread.get();
        this.seed = 0x5A17;
        this.cell = Math.max(0.01F, cell);
        this.flat = flat;

        this.forward.set(forward);
        this.inverse.set(forward).invert();
    }

    /** A field that moves whole bones rather than cells; the matrix is the model's frame as it was drawn. */
    public static ShatterField forBones(FormFxValues fx, Matrix4f root)
    {
        ShatterField field = new ShatterField(fx, new Matrix4f(), CELL, false);

        field.rootInverse = new Matrix4f(root).invert();

        return field;
    }

    public float getCell()
    {
        return this.cell;
    }

    /** Where a point in vertex space lies in the form's own frame. */
    public Vector3f toLocal(float x, float y, float z)
    {
        return this.inverse.transformPosition(x, y, z, this.point);
    }

    /* Random */

    private static int hash(int a, int b, int c)
    {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9e3779b1;

        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;

        return h;
    }

    private float random(int x, int y, int z, int slot)
    {
        return (hash(this.seed + slot * 7919, x * 73856093 ^ z * 83492791, y) & 0xFFFFFF) / (float) 0x1000000;
    }

    /** How far along this chunk is, 0 to 1: they do not all go at once. */
    private float local(int x, int y, int z)
    {
        float delay = this.random(x, y, z, 4) * 0.35F;

        return Math.max(0F, Math.min(1F, (this.progress - delay) / 0.65F));
    }

    /* Cells */

    /**
     * The move of the chunk a point belongs to, in vertex space: it takes a vertex of the chunk to
     * where the vertex is now. Null when the chunk has shrunk away. Chunks are cached, so asking again
     * for the same cell costs nothing.
     *
     * @param x a point of the piece, in vertex space
     */
    public Matrix4f at(float x, float y, float z)
    {
        Vector3f local = this.toLocal(x, y, z);
        int cx = (int) Math.floor(local.x / this.cell);
        int cy = (int) Math.floor(local.y / this.cell);
        int cz = this.flat ? 0 : (int) Math.floor(local.z / this.cell);
        long key = ((long) cx & 0x1FFFFF) | ((long) cy & 0x1FFFFF) << 21 | ((long) cz & 0x1FFFFF) << 42;
        Matrix4f cached = this.chunks.get(key);

        if (cached == null)
        {
            cached = this.build(cx, cy, cz);

            this.chunks.put(key, cached);
        }

        return cached == GONE ? null : cached;
    }

    private Matrix4f build(int cx, int cy, int cz)
    {
        float local = this.local(cx, cy, cz);
        float scale = 1F - local * local;

        if (scale <= 0.001F)
        {
            return GONE;
        }

        float fx = this.random(cx, cy, cz, 0) * 2F - 1F;
        float fy = this.random(cx, cy, cz, 1) * 2F - 1F + 0.3F;
        float fz = this.random(cx, cy, cz, 2) * 2F - 1F;
        float centerX = (cx + 0.5F) * this.cell;
        float centerY = (cy + 0.5F) * this.cell;
        float centerZ = this.flat ? 0F : (cz + 0.5F) * this.cell;

        /* In the form's own frame; the matrix that comes out is then walked into vertex space */
        this.temp.identity().translate(
            centerX + fx * this.spread * local,
            centerY + fy * this.spread * local - this.spread * 1.2F * local * local,
            centerZ + fz * this.spread * local
        );
        this.temp.rotateZ((this.random(cx, cy, cz, 3) * 2F - 1F) * 3.5F * local);
        this.temp.rotateX((this.random(cx, cy, cz, 5) * 2F - 1F) * 3.5F * local);
        this.temp.scale(scale);
        this.temp.translate(-centerX, -centerY, -centerZ);

        return new Matrix4f(this.forward).mul(this.temp).mul(this.inverse);
    }

    /* Bones */

    /**
     * Moves the bone the stack stands in: it drifts off in a direction of its own, falls, turns about
     * its pivot and shrinks, its children going along with it. Called with the stack in the bone's
     * frame, after the bone's own pose.
     */
    public void applyBone(MatrixStack stack, ModelGroup group)
    {
        int id = group.id == null ? 0 : group.id.hashCode();
        float local = this.local(id, 0, 0);

        if (local <= 0F)
        {
            return;
        }

        float scale = Math.max(0.0001F, 1F - local * local);
        Vector3f move = new Vector3f(
            this.random(id, 0, 0, 0) * 2F - 1F,
            this.random(id, 0, 0, 1) * 2F - 1F + 0.3F,
            this.random(id, 0, 0, 2) * 2F - 1F
        ).mul(this.spread * local);

        move.y -= this.spread * 1.2F * local * local;

        /* The drift is worked out in the model's frame, so down is down for every bone; this brings
         * it into the bone's own frame, scale and all */
        Matrix4f chain = new Matrix4f(this.rootInverse).mul(stack.peek().getPositionMatrix());

        chain.invert().transformDirection(move);
        stack.translate(move.x, move.y, move.z);

        Vector3f pivot = group.initial.translate;

        stack.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
        stack.multiply(new org.joml.Quaternionf().rotationZYX(
            (this.random(id, 0, 0, 3) * 2F - 1F) * 3.5F * local,
            (this.random(id, 0, 0, 6) * 2F - 1F) * 3.5F * local,
            (this.random(id, 0, 0, 5) * 2F - 1F) * 3.5F * local
        ));
        stack.scale(scale, scale, scale);
        stack.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }
}
