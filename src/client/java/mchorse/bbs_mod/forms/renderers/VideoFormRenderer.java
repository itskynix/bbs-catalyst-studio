package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.video.VideoPlayer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;

public class VideoFormRenderer extends BillboardFormRenderer<VideoForm>
{
    /**
     * The UI preview (a frozen frame) and the world (the carrier's running clock)
     * ask for DIFFERENT timestamps every frame. They must not share a decoder:
     * two alternating targets never settle, and the async seek would starve both.
     * The preview runs on its own player keyed by this object.
     */
    private final Object uiPlayerKey = new Object();

    /** The long side of the grid the alpha is sampled on for the walls: past it, they
     * would cost more than they show */
    private static final int MASK_SIZE = 256;

    /* Wall runs: which side of the opaque cells they close */
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int TOP = 2;
    private static final int BOTTOM = 3;

    private float seconds;
    private boolean still;

    /** The player the texture of this draw came from, for its alpha mask */
    private VideoPlayer player;

    /* One per player, so the preview and the world don't rebuild each other's walls every frame */
    private final Walls worldWalls = new Walls();
    private final Walls uiWalls = new Walls();

    public VideoFormRenderer(VideoForm form)
    {
        super(form);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        VideoForm form = this.form;
        int age = context.entity == null ? 0 : context.entity.getAge();

        this.seconds = (age + context.getTransition()) / 20F + form.videoOffset.get();
        this.still = false;

        super.render3D(context);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* Previews show a frozen frame - no decoder runs for list icons */
        this.seconds = this.form.videoOffset.get();
        this.still = true;

        super.renderInUI(context, x1, y1, x2, y2);

        if (this.getTexture() == null)
        {
            /* No file picked, or the first frame is still decoding - show SOMETHING */
            int size = 32;

            context.batcher.scaledIcon(Icons.VIDEO_CAMERA, Colors.WHITE, (x1 + x2 - size) / 2, (y1 + y2 - size) / 2, size);
        }
    }

    @Override
    protected Texture getTexture()
    {
        VideoForm form = this.form;
        Link link = form.video.get();

        this.player = null;

        if (link == null)
        {
            return null;
        }

        VideoPlayer player = BBSModClient.getVideos().getPlayer(this.still ? this.uiPlayerKey : this, link);

        this.player = player;

        if (player == null)
        {
            return null;
        }

        float time = this.seconds;

        if (form.loop.get() && player.getDuration() > 0F)
        {
            time = time % player.getDuration();

            if (time < 0F)
            {
                time += player.getDuration();
            }
        }

        Texture texture = player.getFrame(time);

        if (texture != null && this.still)
        {
            /* The frame IS on the texture - the preview's ffmpeg process has nothing
             * left to do. Stopping before the frame arrived would kill the decode
             * mid-flight and the preview would never load. */
            player.stop();
        }

        return texture;
    }

    @Override
    protected float getHalfThickness()
    {
        return this.form.extrude.get() ? Math.max(this.form.depth.get(), 0.001F) / 2F : 0F;
    }

    /**
     * The walls of the extrusion: along every edge between an opaque cell of the alpha
     * mask and a clear one (or the border of the shown part of the frame), from the
     * back face to the front. Each wall wears the colour of the edge pixels it closes,
     * stretched over the depth, the way an extruded item's sides do.
     */
    @Override
    protected void fillSides(VertexFormat format, BufferBuilder builder, Matrix4f matrix, Color color, int overlay, int light, Matrix3f normal, Quad quad, Quad uvQuad, float halfThickness)
    {
        VideoPlayer player = this.player;

        if (player == null)
        {
            return;
        }

        int version = player.updateAlphaMask(MASK_SIZE);

        if (version < 0)
        {
            return;
        }

        Walls walls = this.still ? this.uiWalls : this.worldWalls;

        walls.update(player, version, this.form.alphaThreshold.get(), uvQuad);

        if (walls.count == 0 || !walls.map.set(quad, uvQuad))
        {
            return;
        }

        boolean shade = this.form.sideShading.get();
        float gw = walls.width;
        float gh = walls.height;
        Vector3f a = walls.a;
        Vector3f b = walls.b;
        Vector3f in = walls.in;
        Vector3f out = walls.out;

        for (int k = 0; k < walls.count; k++)
        {
            int type = walls.runs[k * 4];
            int line = walls.runs[k * 4 + 1];
            int start = walls.runs[k * 4 + 2];
            int end = walls.runs[k * 4 + 3];
            boolean vertical = type == LEFT || type == RIGHT;
            /* The cell the wall closes, and the one past it */
            int cell = type == LEFT || type == TOP ? line : line - 1;
            int past = type == LEFT || type == TOP ? line - 1 : line + 1;
            float ua, va, ub, vb;

            if (vertical)
            {
                walls.map.position(line / gw, start / gh, a);
                walls.map.position(line / gw, end / gh, b);
                walls.map.position((cell + 0.5F) / gw, (start + 0.5F) / gh, in);
                walls.map.position((past + 0.5F) / gw, (start + 0.5F) / gh, out);
                ua = ub = (cell + 0.5F) / gw;
                va = start / gh;
                vb = end / gh;
            }
            else
            {
                walls.map.position(start / gw, line / gh, a);
                walls.map.position(end / gw, line / gh, b);
                walls.map.position((start + 0.5F) / gw, (cell + 0.5F) / gh, in);
                walls.map.position((start + 0.5F) / gw, (past + 0.5F) / gh, out);
                ua = start / gw;
                ub = end / gw;
                va = vb = (cell + 0.5F) / gh;
            }

            out.sub(in);

            float ex = b.x - a.x;
            float ey = b.y - a.y;

            /* Wound so the wall faces away from the opaque cell: (b - a) x z points out */
            if (ey * out.x - ex * out.y < 0F)
            {
                float t;

                t = a.x; a.x = b.x; b.x = t;
                t = a.y; a.y = b.y; b.y = t;
                t = ua; ua = ub; ub = t;
                t = va; va = vb; vb = t;
                ex = -ex;
                ey = -ey;
            }

            float length = (float) Math.sqrt(ex * ex + ey * ey);

            if (length < 1.0E-6F)
            {
                continue;
            }

            float nx = ey / length;
            float ny = -ex / length;
            float factor = shade ? (ny >= 0F ? 0.75F + 0.2F * ny : 0.75F + 0.25F * ny) : 1F;
            float r = color.r * factor;
            float g = color.g * factor;
            float bl = color.b * factor;
            float h = halfThickness;

            /* a back, b back, b front, then a back, b front, a front */
            this.vertex(format, builder, matrix, a.x, a.y, -h, r, g, bl, color.a, ua, va, overlay, light, normal, nx, ny, 0F)/*$ nx*/.next()/**/;
            this.vertex(format, builder, matrix, b.x, b.y, -h, r, g, bl, color.a, ub, vb, overlay, light, normal, nx, ny, 0F)/*$ nx*/.next()/**/;
            this.vertex(format, builder, matrix, b.x, b.y, h, r, g, bl, color.a, ub, vb, overlay, light, normal, nx, ny, 0F)/*$ nx*/.next()/**/;

            this.vertex(format, builder, matrix, a.x, a.y, -h, r, g, bl, color.a, ua, va, overlay, light, normal, nx, ny, 0F)/*$ nx*/.next()/**/;
            this.vertex(format, builder, matrix, b.x, b.y, h, r, g, bl, color.a, ub, vb, overlay, light, normal, nx, ny, 0F)/*$ nx*/.next()/**/;
            this.vertex(format, builder, matrix, a.x, a.y, h, r, g, bl, color.a, ua, va, overlay, light, normal, nx, ny, 0F)/*$ nx*/.next()/**/;
        }
    }

    /**
     * Texture coordinates to positions on the faces: both quads are parallelograms
     * (the crop is a rectangle, offset and rotation move it whole), so one affine map
     * through their top left, top right and bottom left corners covers them.
     */
    private static class FaceMap
    {
        private float u1, v1, ax, ay, bx, by, det;
        private final Vector3f p1 = new Vector3f();
        private final Vector3f pa = new Vector3f();
        private final Vector3f pb = new Vector3f();

        /** False when the texture coordinates have collapsed and there is no map. */
        public boolean set(Quad quad, Quad uvQuad)
        {
            this.u1 = uvQuad.p1.x;
            this.v1 = uvQuad.p1.y;
            this.ax = uvQuad.p2.x - this.u1;
            this.ay = uvQuad.p2.y - this.v1;
            this.bx = uvQuad.p3.x - this.u1;
            this.by = uvQuad.p3.y - this.v1;
            this.det = this.ax * this.by - this.ay * this.bx;

            this.p1.set(quad.p1);
            this.pa.set(quad.p2).sub(quad.p1);
            this.pb.set(quad.p3).sub(quad.p1);

            return Math.abs(this.det) > 1.0E-9F;
        }

        /** Where (u, v) is across the faces: s from left to right, t from top to bottom. */
        public float s(float u, float v)
        {
            return ((u - this.u1) * this.by - (v - this.v1) * this.bx) / this.det;
        }

        public float t(float u, float v)
        {
            return (this.ax * (v - this.v1) - this.ay * (u - this.u1)) / this.det;
        }

        public void position(float u, float v, Vector3f out)
        {
            float s = this.s(u, v);
            float t = this.t(u, v);

            out.set(this.p1).add(this.pa.x * s + this.pb.x * t, this.pa.y * s + this.pb.y * t, 0F);
        }
    }

    /**
     * The edges of the opaque part of a frame, as runs of cell edges along the grid
     * lines: rebuilt only when a new frame, another threshold or another shown part
     * of the frame comes, so a still or held frame costs nothing to keep walled.
     * Each run is four ints: side, grid line, first and past-the-last cell along it.
     */
    private static class Walls
    {
        public final FaceMap map = new FaceMap();
        /* Scratch points for drawing the walls */
        public final Vector3f a = new Vector3f();
        public final Vector3f b = new Vector3f();
        public final Vector3f in = new Vector3f();
        public final Vector3f out = new Vector3f();
        public int[] runs = new int[256];
        public int count;
        public int width;
        public int height;

        private VideoPlayer player;
        private int version = -1;
        private float threshold = -1F;
        private final float[] uvs = new float[6];
        private boolean[] solid = new boolean[0];

        public void update(VideoPlayer player, int version, float threshold, Quad uvQuad)
        {
            boolean sameUvs = this.uvs[0] == uvQuad.p1.x && this.uvs[1] == uvQuad.p1.y
                && this.uvs[2] == uvQuad.p2.x && this.uvs[3] == uvQuad.p2.y
                && this.uvs[4] == uvQuad.p3.x && this.uvs[5] == uvQuad.p3.y;

            if (this.player == player && this.version == version && this.threshold == threshold && sameUvs)
            {
                return;
            }

            this.player = player;
            this.version = version;
            this.threshold = threshold;
            this.uvs[0] = uvQuad.p1.x;
            this.uvs[1] = uvQuad.p1.y;
            this.uvs[2] = uvQuad.p2.x;
            this.uvs[3] = uvQuad.p2.y;
            this.uvs[4] = uvQuad.p3.x;
            this.uvs[5] = uvQuad.p3.y;

            this.build(player.getAlphaMask(), player.getAlphaMaskWidth(), player.getAlphaMaskHeight(), uvQuad);
        }

        private void build(byte[] mask, int w, int h, Quad uvQuad)
        {
            this.count = 0;
            this.width = w;
            this.height = h;

            if (mask == null || w * h > mask.length || !this.map.set(uvQuad, uvQuad))
            {
                return;
            }

            if (this.solid.length < w * h)
            {
                this.solid = new boolean[w * h];
            }

            /* Opaque enough, and inside the part of the frame the faces show */
            int cutoff = Math.round(MathUtils.clamp(this.threshold, 0F, 1F) * 255F);

            for (int j = 0; j < h; j++)
            {
                float v = (j + 0.5F) / h;

                for (int i = 0; i < w; i++)
                {
                    float u = (i + 0.5F) / w;
                    float s = this.map.s(u, v);
                    float t = this.map.t(u, v);

                    this.solid[j * w + i] = (mask[j * w + i] & 0xFF) > cutoff && s >= 0F && s <= 1F && t >= 0F && t <= 1F;
                }
            }

            for (int i = 0; i < w; i++)
            {
                this.scan(LEFT, i, h, true, i, -1);
                this.scan(RIGHT, i + 1, h, true, i, 1);
            }

            for (int j = 0; j < h; j++)
            {
                this.scan(TOP, j, w, false, j, -1);
                this.scan(BOTTOM, j + 1, w, false, j, 1);
            }
        }

        /** The runs along one grid line where the cells on one side are opaque and the ones past it are not. */
        private void scan(int type, int line, int length, boolean vertical, int cell, int step)
        {
            int start = -1;

            for (int k = 0; k <= length; k++)
            {
                boolean edge = k < length && (vertical
                    ? this.isSolid(cell, k) && !this.isSolid(cell + step, k)
                    : this.isSolid(k, cell) && !this.isSolid(k, cell + step));

                if (edge && start < 0)
                {
                    start = k;
                }
                else if (!edge && start >= 0)
                {
                    this.add(type, line, start, k);
                    start = -1;
                }
            }
        }

        private boolean isSolid(int i, int j)
        {
            return i >= 0 && j >= 0 && i < this.width && j < this.height && this.solid[j * this.width + i];
        }

        private void add(int type, int line, int start, int end)
        {
            if (this.count * 4 + 4 > this.runs.length)
            {
                this.runs = Arrays.copyOf(this.runs, this.runs.length * 2);
            }

            this.runs[this.count * 4] = type;
            this.runs[this.count * 4 + 1] = line;
            this.runs[this.count * 4 + 2] = start;
            this.runs[this.count * 4 + 3] = end;
            this.count++;
        }
    }
}
