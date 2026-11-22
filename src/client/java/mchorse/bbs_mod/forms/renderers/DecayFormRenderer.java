package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.DecayForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.VertexUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.client.light.IRLightPositionResolver;

import java.util.function.Supplier;

/**
 * Draws a form's decay as a skin of small flat cells, one sixteenth of a block each - the size of a
 * Minecraft texel - laid a hair above the surface. Each cell asks {@link DecayPattern} how decayed
 * its spot is and is left out when the answer is nothing, so a half decayed wall costs only the
 * decayed half. The cells go through the entity translucent program with a lightmap, so they are lit
 * by the world and by a shader pack like any other form, and they ride the translucent queue so they
 * sort with the rest of the forms.
 *
 * <p>In surface mode the cells sit on the faces of the world's blocks inside the form's box; the box is
 * the form's own - the world matrix of its place and pose - so it can be turned and moved. In object
 * mode they sit on a shell of that box, in the form's own space, and the pattern is read there too, so
 * it moves with whatever the form is a part of.</p>
 *
 * <p>The cells are kept between frames and rebuilt only when something they depend on changed: a value
 * of the form, the form's place, a lighting change, or, in surface mode, once in a while to follow
 * the world's blocks.</p>
 */
public class DecayFormRenderer extends FormRenderer<DecayForm>
{
    /** The most cells one form builds, so a huge volume cannot cost a frame */
    private static final int MAX_QUADS = 40000;
    /** The most cells along one edge of a face */
    private static final int MAX_CELLS = 64;
    /** The cell's lift above its surface, in blocks */
    private static final double LIFT = 0.004;
    /** Floats one cell takes in the cache: 4 corners, the colour, 4 texture points, the light, the normal */
    private static final int STRIDE = 12 + 4 + 8 + 1 + 3;

    private static final int[] TRIANGLES = {0, 1, 2, 0, 2, 3};

    private final DecayPattern.Params params = new DecayPattern.Params();
    private final float[] out = new float[4];
    private final float[] scratch = new float[2];

    private float[] cache = new float[STRIDE * 1024];
    private int quads;
    private long signature = Long.MIN_VALUE;

    /* The build in progress */
    private Matrix4d invWorld;
    private Matrix3f localNormal;
    private float hx;
    private float hy;
    private float hz;
    private float cy;
    private boolean boxLimited;
    private float colorR;
    private float colorG;
    private float colorB;
    private float colorA;

    public DecayFormRenderer(DecayForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        DecayPattern.Params p = new DecayPattern.Params().read(this.form);
        int size = Math.max(8, Math.min(x2 - x1, y2 - y1) - 12);
        int cells = Math.min(32, Math.max(8, size / 4));
        int cell = Math.max(2, size / cells);
        int left = (x1 + x2) / 2 - cell * cells / 2;
        int top = (y1 + y2) / 2 - cell * cells / 2;
        float[] out = new float[4];
        float[] scratch = new float[2];

        context.batcher.box(left, top, left + cell * cells, top + cell * cells, 0xff3a3a3e);

        for (int i = 0; i < cells; i++)
        {
            for (int j = 0; j < cells; j++)
            {
                float fx = (i + 0.5F) / cells;
                float fy = (j + 0.5F) / cells;
                float radial = Math.min(1F, (float) Math.sqrt((fx - 0.5F) * (fx - 0.5F) + (fy - 0.5F) * (fy - 0.5F)) * 1.4142F);

                DecayPattern.sample(p, fx * 3.0, (1F - fy) * 3.0, 0.5, 1F - fy, radial, out, scratch);

                float a = Math.min(1F, out[3] * p.opacity);

                if (a < 0.02F)
                {
                    continue;
                }

                int color = ((int) (a * 255F) << 24) | (channel(out[0]) << 16) | (channel(out[1]) << 8) | channel(out[2]);

                context.batcher.box(left + i * cell, top + j * cell, left + (i + 1) * cell, top + (j + 1) * cell, color);
            }
        }
    }

    private static int channel(float value)
    {
        return Math.max(0, Math.min(255, (int) (value * 255F)));
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (context.isPicking() || BBSRendering.isIrisShadowPass() || ShadowBakeState.isBaking())
        {
            return;
        }

        this.renderDecay(context);
        this.renderGuides(context);
    }

    private void renderDecay(FormRenderingContext context)
    {
        DecayPattern.Params p = this.params.read(this.form);

        if (p.progress <= 0.0005F || p.opacity <= 0.003F || context.stack == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean objectMode = this.form.mode.get() == DecayForm.MODE_OBJECT;
        boolean virtualWall = !objectMode && (mc.world == null || context.ui);

        this.invWorld = null;

        Color tint = new Color(1F, 1F, 1F, 1F);

        tint.mul(context.color);

        this.colorR = tint.r;
        this.colorG = tint.g;
        this.colorB = tint.b;
        this.colorA = tint.a;

        this.resolveBox(objectMode);

        long sig = this.signatureOf(context, p, objectMode, virtualWall, mc);

        if (sig != this.signature)
        {
            this.signature = sig;
            this.quads = 0;

            if (virtualWall)
            {
                this.buildWall(context);
            }
            else if (objectMode)
            {
                this.buildShell(context);
            }
            else
            {
                this.buildSurfaces(context, mc);
            }
        }

        if (this.quads == 0)
        {
            return;
        }

        this.draw(context);
    }

    /**
     * The box the decay works in. In surface mode it is the form's own size. In object mode it is the
     * box round whatever the form is a part of: a model's hitbox, standing on its feet, a block's cube, an
     * item's small cube; a parent it knows nothing about gets the form's own size, centred.
     */
    private void resolveBox(boolean objectMode)
    {
        Form parent = this.form.getParentForm();

        this.hx = this.form.width.get() / 2F;
        this.hz = this.form.length.get() / 2F;
        this.hy = this.form.height.get() / 2F;
        this.cy = 0F;

        if (!objectMode)
        {
            return;
        }

        if (parent instanceof ModelForm model)
        {
            this.hx = Math.max(0.1F, model.hitboxWidth.get() / 2F) * 1.04F;
            this.hz = this.hx;
            this.hy = Math.max(0.1F, model.hitboxHeight.get() / 2F) * 1.02F;
            this.cy = this.hy;
        }
        else if (parent instanceof BlockForm)
        {
            this.hx = 0.5F * 1.01F;
            this.hy = this.hx;
            this.hz = this.hx;
        }
        else if (parent instanceof ItemForm)
        {
            this.hx = 0.25F;
            this.hy = 0.25F;
            this.hz = 0.25F;
        }
    }

    private long signatureOf(FormRenderingContext context, DecayPattern.Params p, boolean objectMode, boolean virtualWall, MinecraftClient mc)
    {
        long h = p.signature();

        h = h * 31 + this.form.mode.get();
        h = h * 31 + Float.floatToIntBits(this.hx);
        h = h * 31 + Float.floatToIntBits(this.hy);
        h = h * 31 + Float.floatToIntBits(this.hz);
        h = h * 31 + context.color;
        h = h * 31 + (virtualWall ? 1 : 0);

        if (objectMode || virtualWall)
        {
            h = h * 31 + context.light;
        }

        if (!objectMode && !virtualWall)
        {
            Matrix4f world = this.worldMatrix(context);

            /* Rounded, so a form that stands still does not rebuild on float noise */
            for (int i = 0; i < 4; i++)
            {
                for (int j = 0; j < 4; j++)
                {
                    h = h * 31 + Math.round(world.get(i, j) * 256F);
                }
            }

            /* The world's own blocks change: look again about twice a second */
            h = h * 31 + (mc.world == null ? 0 : mc.world.getTime() / 10L);
        }

        h = h * 31 + (this.form.preset.get() == DecayForm.PRESET_CUSTOM ? String.valueOf(this.form.texture.get()).hashCode() : 0);

        return h;
    }

    /** The form's place and pose in the world: the entity's world matrix, or just where a light would be placed when there is none */
    private Matrix4f worldMatrix(FormRenderingContext context)
    {
        if (context.world != null && context.entity != null)
        {
            return new Matrix4f(context.world.peek().getPositionMatrix());
        }

        Vector3d origin = IRLightPositionResolver.resolve(context);

        return new Matrix4f().translation((float) origin.x, (float) origin.y, (float) origin.z);
    }

    /* Building */

    private void buildSurfaces(FormRenderingContext context, MinecraftClient mc)
    {
        ClientWorld world = mc.world;
        Matrix4f worldMatrix = this.worldMatrix(context);
        Vector3f origin = worldMatrix.getTranslation(new Vector3f());
        Matrix4d inverse = new Matrix4d(worldMatrix).invert();

        this.invWorld = inverse;
        this.localNormal = new Matrix3f(worldMatrix).transpose();
        this.boxLimited = true;

        double radius = Math.sqrt(this.hx * this.hx + this.hy * this.hy + this.hz * this.hz) + 1.0;
        int minX = MathHelper.floor(origin.x - radius);
        int maxX = MathHelper.ceil(origin.x + radius);
        int minY = MathHelper.floor(origin.y - radius);
        int maxY = MathHelper.ceil(origin.y + radius);
        int minZ = MathHelper.floor(origin.z - radius);
        int maxZ = MathHelper.ceil(origin.z + radius);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();
        double[][] corners = new double[4][3];
        double eps = 1e-6;

        for (int x = minX; x <= maxX; x++)
        {
            for (int y = minY; y <= maxY; y++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    pos.set(x, y, z);

                    BlockState state = world.getBlockState(pos);

                    if (state.isAir())
                    {
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(world, pos);

                    if (shape.isEmpty())
                    {
                        continue;
                    }

                    for (Box box : shape.getBoundingBoxes())
                    {
                        for (Direction dir : Direction.values())
                        {
                            boolean boundary = switch (dir)
                            {
                                case DOWN -> box.minY <= eps;
                                case UP -> box.maxY >= 1 - eps;
                                case NORTH -> box.minZ <= eps;
                                case SOUTH -> box.maxZ >= 1 - eps;
                                case WEST -> box.minX <= eps;
                                case EAST -> box.maxX >= 1 - eps;
                            };
                            int light;

                            if (boundary)
                            {
                                neighbor.set(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());

                                if (world.getBlockState(neighbor).isSideSolidFullSquare(world, neighbor, dir.getOpposite()))
                                {
                                    continue;
                                }

                                light = WorldRenderer.getLightmapCoordinates(world, neighbor);
                            }
                            else
                            {
                                light = WorldRenderer.getLightmapCoordinates(world, pos.up());
                            }

                            boxCorners(dir.ordinal(), x + box.minX, x + box.maxX, y + box.minY, y + box.maxY, z + box.minZ, z + box.maxZ, corners);
                            this.face(corners, dir.ordinal(), light, true);

                            if (this.quads >= MAX_QUADS)
                            {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /** The shell of the form's box: its six sides, in the form's own space, so it travels with the form */
    private void buildShell(FormRenderingContext context)
    {
        this.localNormal = null;
        this.boxLimited = false;

        double[][] corners = new double[4][3];
        double grow = 0.0;

        for (int dir = 0; dir < 6; dir++)
        {
            boxCorners(dir, -this.hx - grow, this.hx + grow, this.cy - this.hy - grow, this.cy + this.hy + grow, -this.hz - grow, this.hz + grow, corners);
            this.face(corners, dir, context.light, false);
        }
    }

    /** What the form editor's viewport and the form list show when there is no world: one wall, facing the viewer */
    private void buildWall(FormRenderingContext context)
    {
        this.localNormal = null;
        this.boxLimited = false;

        double[][] corners = new double[4][3];

        boxCorners(Direction.SOUTH.ordinal(), -this.hx, this.hx, this.cy - this.hy, this.cy + this.hy, 0, 0, corners);
        this.face(corners, Direction.SOUTH.ordinal(), context.light, false);
    }

    /** The four corners of one side of a box, wound so the side faces out: the first, the one down its V, the opposite, the one along its U */
    private static void boxCorners(int dir, double minX, double maxX, double minY, double maxY, double minZ, double maxZ, double[][] c)
    {
        switch (Direction.values()[dir])
        {
            case UP -> set(c, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
            case DOWN -> set(c, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ);
            case NORTH -> set(c, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ);
            case SOUTH -> set(c, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ);
            case WEST -> set(c, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ);
            case EAST -> set(c, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ);
        }
    }

    private static void set(double[][] c, double... v)
    {
        for (int i = 0; i < 4; i++)
        {
            c[i][0] = v[i * 3];
            c[i][1] = v[i * 3 + 1];
            c[i][2] = v[i * 3 + 2];
        }
    }

    /**
     * One side cut into sixteenths and every cell that has anything to show added to the cache.
     * {@code c} are the corners (see {@link #boxCorners}) in world blocks when {@code world}, else in the
     * form's own space.
     */
    private void face(double[][] c, int dirIndex, int light, boolean world)
    {
        Direction dir = Direction.values()[dirIndex];
        double e1x = c[1][0] - c[0][0];
        double e1y = c[1][1] - c[0][1];
        double e1z = c[1][2] - c[0][2];
        double e2x = c[3][0] - c[0][0];
        double e2y = c[3][1] - c[0][1];
        double e2z = c[3][2] - c[0][2];
        double len1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        double len2 = Math.sqrt(e2x * e2x + e2y * e2y + e2z * e2z);
        int nu = MathHelper.clamp((int) Math.round(len2 * 16.0), 1, MAX_CELLS);
        int nv = MathHelper.clamp((int) Math.round(len1 * 16.0), 1, MAX_CELLS);
        float nx = dir.getOffsetX();
        float ny = dir.getOffsetY();
        float nz = dir.getOffsetZ();
        Vector3d center = new Vector3d();
        Vector3d local = new Vector3d();
        Vector3d corner = new Vector3d();
        DecayPattern.Params p = this.params;

        /* The normal the shader gets, in the form's space */
        Vector3f normal = new Vector3f(nx, ny, nz);

        if (world && this.localNormal != null)
        {
            normal.mul(this.localNormal).normalize();
        }

        float[] quad = new float[STRIDE];

        for (int i = 0; i < nu; i++)
        {
            double u0 = i / (double) nu;
            double u1 = (i + 1) / (double) nu;

            for (int j = 0; j < nv; j++)
            {
                if (this.quads >= MAX_QUADS)
                {
                    return;
                }

                double v0 = j / (double) nv;
                double v1 = (j + 1) / (double) nv;
                double um = (u0 + u1) * 0.5;
                double vm = (v0 + v1) * 0.5;

                center.set(c[0][0] + e1x * vm + e2x * um, c[0][1] + e1y * vm + e2y * um, c[0][2] + e1z * vm + e2z * um);

                if (world)
                {
                    this.invWorld.transformPosition(center, local);
                }
                else
                {
                    local.set(center);
                }

                float ly = (float) local.y - this.cy;

                if (this.boxLimited && (Math.abs(local.x) > this.hx || Math.abs(ly) > this.hy || Math.abs(local.z) > this.hz))
                {
                    continue;
                }

                float rx = this.hx > 0F ? (float) local.x / this.hx : 0F;
                float ry = this.hy > 0F ? ly / this.hy : 0F;
                float rz = this.hz > 0F ? (float) local.z / this.hz : 0F;
                float radial = Math.min(1F, (float) Math.sqrt(rx * rx + ry * ry + rz * rz) / 1.7320508F);
                float y01 = MathHelper.clamp(ly / (2F * this.hy) + 0.5F, 0F, 1F);

                DecayPattern.sample(p, center.x, center.y, center.z, y01, radial, this.out, this.scratch);

                float alpha = Math.min(1F, this.out[3] * p.opacity) * this.colorA;

                if (alpha < 0.012F)
                {
                    continue;
                }

                /* The four corners, lifted off the surface */
                double[][] q = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};

                for (int k = 0; k < 4; k++)
                {
                    double cu = q[k][0];
                    double cv = q[k][1];

                    corner.set(c[0][0] + e1x * cv + e2x * cu + nx * LIFT, c[0][1] + e1y * cv + e2y * cu + ny * LIFT, c[0][2] + e1z * cv + e2z * cu + nz * LIFT);

                    double px = corner.x;
                    double py = corner.y;
                    double pz = corner.z;

                    if (world)
                    {
                        this.invWorld.transformPosition(corner);
                    }

                    quad[k * 3] = (float) corner.x;
                    quad[k * 3 + 1] = (float) corner.y;
                    quad[k * 3 + 2] = (float) corner.z;

                    /* The texture follows the block: a corner's place inside the cell's block, by the side's two axes */
                    double bx = Math.floor(center.x);
                    double by = Math.floor(center.y);
                    double bz = Math.floor(center.z);
                    double tu;
                    double tv;

                    if (dir.getAxis() == Direction.Axis.Y)
                    {
                        tu = px - bx;
                        tv = pz - bz;
                    }
                    else if (dir.getAxis() == Direction.Axis.Z)
                    {
                        tu = px - bx;
                        tv = 1.0 - (py - by);
                    }
                    else
                    {
                        tu = pz - bz;
                        tv = 1.0 - (py - by);
                    }

                    quad[16 + k * 2] = (float) tu;
                    quad[16 + k * 2 + 1] = (float) tv;
                }

                quad[12] = MathHelper.clamp(this.out[0] * this.colorR, 0F, 1F);
                quad[13] = MathHelper.clamp(this.out[1] * this.colorG, 0F, 1F);
                quad[14] = MathHelper.clamp(this.out[2] * this.colorB, 0F, 1F);
                quad[15] = alpha;
                quad[24] = Float.intBitsToFloat(light);
                quad[25] = normal.x;
                quad[26] = normal.y;
                quad[27] = normal.z;

                this.store(quad);
            }
        }
    }

    private void store(float[] quad)
    {
        int at = this.quads * STRIDE;

        if (at + STRIDE > this.cache.length)
        {
            this.cache = java.util.Arrays.copyOf(this.cache, this.cache.length * 2);
        }

        System.arraycopy(quad, 0, this.cache, at, STRIDE);

        this.quads++;
    }

    /* Drawing */

    private void draw(FormRenderingContext context)
    {
        Link link = this.form.preset.get() == DecayForm.PRESET_CUSTOM ? this.form.texture.get() : null;
        Texture texture = BBSModClient.getTextures().getTexture(link == null ? new Link(Link.COLOR, "ffffffff") : link);

        if (texture == null)
        {
            return;
        }

        MatrixStack matrices = context.stack;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMat = matrices.peek().getNormalMatrix();
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
        Supplier<ShaderProgram> shader = GameRenderer::getRenderTypeEntityTranslucentProgram;
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(shader);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, format);
        float[] d = this.cache;

        for (int n = 0; n < this.quads; n++)
        {
            int at = n * STRIDE;
            int light = Float.floatToRawIntBits(d[at + 24]);
            float r = d[at + 12];
            float g = d[at + 13];
            float b = d[at + 14];
            float a = d[at + 15];
            float nx = d[at + 25];
            float ny = d[at + 26];
            float nz = d[at + 27];

            for (int k : TRIANGLES)
            {
                this.vertex(builder, matrix, normalMat, d[at + k * 3], d[at + k * 3 + 1], d[at + k * 3 + 2], d[at + 16 + k * 2], d[at + 16 + k * 2 + 1], r, g, b, a, light, nx, ny, nz);
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        if (FormTranslucentQueue.isActive())
        {
            this.uploadTranslucent(builder, texture, matrix);
        }
        else
        {
            Bufs.draw(builder);
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    private void vertex(BufferBuilder builder, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v, float r, float g, float b, float a, int light, float nx, float ny, float nz)
    {
        VertexUtils.normal(builder.vertex(matrix, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light), normal, nx, ny, nz).next();
    }

    private void uploadTranslucent(BufferBuilder builder, Texture texture, Matrix4f matrix)
    {
        ShaderProgram finalShader = RenderSystem.getShader();
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        buffer.bind();
        Bufs.upload(buffer, builder);
        VertexBuffer.unbind();

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

        FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
            buffer, () -> finalShader, texture, modelView, null, origin, null, true, null, null
        ));
    }

    /* Guides */

    private void renderGuides(FormRenderingContext context)
    {
        if (!this.form.showGuides.get() || FormPortals.isRenderingPortal())
        {
            return;
        }

        FormRenderType type = context.type;
        boolean editor = context.ui || context.modelRenderer || type == FormRenderType.PREVIEW;

        if (!editor)
        {
            if (type != FormRenderType.ENTITY && type != FormRenderType.MODEL_BLOCK)
            {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.options.hudHidden || BBSModClient.getVideoRecorder().isRecording())
            {
                return;
            }

            if (type != FormRenderType.MODEL_BLOCK && !(mc.currentScreen instanceof UIScreen))
            {
                return;
            }
        }

        MatrixStack stack = context.stack;
        boolean objectMode = this.form.mode.get() == DecayForm.MODE_OBJECT;
        this.resolveBox(objectMode);

        float hx = this.hx;
        float hz = this.hz;
        float hy = this.hy;
        float cy = this.cy;
        float t = 0.02F;
        float r = 0.9F;
        float g = 0.55F;
        float b = 0.2F;

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int sy = -1; sy <= 1; sy += 2)
        {
            for (int sz = -1; sz <= 1; sz += 2)
            {
                Draw.fillBox(builder, stack, -hx, cy + sy * hy - t, sz * hz - t, hx, cy + sy * hy + t, sz * hz + t, r, g, b, 0.8F);
            }
        }

        for (int sx = -1; sx <= 1; sx += 2)
        {
            for (int sz = -1; sz <= 1; sz += 2)
            {
                Draw.fillBox(builder, stack, sx * hx - t, cy - hy, sz * hz - t, sx * hx + t, cy + hy, sz * hz + t, r, g, b, 0.8F);
            }
        }

        for (int sx = -1; sx <= 1; sx += 2)
        {
            for (int sy = -1; sy <= 1; sy += 2)
            {
                Draw.fillBox(builder, stack, sx * hx - t, cy + sy * hy - t, -hz, sx * hx + t, cy + sy * hy + t, hz, r, g, b, 0.8F);
            }
        }

        Bufs.draw(builder);
        RenderSystem.disableBlend();
    }
}
