package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.DecayForm;
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
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Draws a form's decay: every open side of a block in the box, or every side of the shell round an
 * object, is one quad, and the pattern is worked out per pixel.
 *
 * <p>Without a shader pack the pattern runs on the GPU, in the {@code decay} core program (see
 * {@link DecayPattern} for the maths it shares). The quads sit in a vertex buffer of their own, in the
 * form's own space, and the buffer is only rebuilt when the sides themselves change; the progress,
 * the colours and everything else animated are uniforms, so a decay that spreads over a whole hall
 * costs one draw call and no CPU work a frame.</p>
 *
 * <p>Under a shader pack a program of ours would bypass the pack's gbuffers, so the decay is drawn
 * through the entity translucent layer instead, with its pattern baked into an atlas: one 16 by 16
 * tile a side, 64 tiles a row. What a point is - its decay time, its distance to the cracks - is kept
 * between frames; only the colour is composed again while the progress is animated, through tables.</p>
 *
 * <p>The sides of a surface volume are found again the very frame a block inside the box changes:
 * {@link #onBlockChanged} is called by the world as it happens, and nothing is scanned on a timer.</p>
 */
public class DecayFormRenderer extends FormRenderer<DecayForm>
{
    /** The cell's lift above its surface, in blocks */
    private static final double LIFT = 0.004;
    /** Floats one side takes: 4 corners in the form's space, 4 block texture points, the light, the normal, 4 corners where the pattern is read */
    private static final int STRIDE = 12 + 8 + 1 + 3 + 12;
    private static final int[] TRIANGLES = {0, 1, 2, 0, 2, 3};

    /** The atlas: 16 by 16 texels a side, 64 sides a row */
    private static final int TILE = 16;
    private static final int TILES_PER_ROW = 64;
    private static final int ATLAS_WIDTH = TILE * TILES_PER_ROW;
    /** The most sides the atlas holds: 1024 by 4096 texels */
    private static final int ATLAS_MAX_SIDES = TILES_PER_ROW * 256;
    private static final char OUTSIDE = 0xFFFF;
    private static final int BINS = 1024;

    /** Surface volumes alive, so a changed block can tell the ones over it */
    private static final Set<DecayFormRenderer> SURFACES = Collections.newSetFromMap(new WeakHashMap<>());

    private final DecayPattern.Params params = new DecayPattern.Params();

    /* The sides */
    private float[] sides = new float[STRIDE * 64];
    private int count;
    private long geometrySignature = Long.MIN_VALUE;
    private int geometryVersion;
    private boolean dirty;
    /** The block box that was scanned, so a block change outside it is ignored; null when not a surface volume */
    private int[] scanned;
    /** When the scan saw a chunk that was not loaded, the world tick to look again at */
    private long retryAt = -1L;
    private final Matrix4f patternMatrix = new Matrix4f();

    /* The GPU path's buffer */
    private VertexBuffer buffer;
    private int bufferVersion = -1;
    private long bufferQueuedAt;

    /* The atlas path */
    private NativeImageBackedTexture atlas;
    private int atlasHeight;
    private char[] field = new char[0];
    private int fieldSides;
    private long fieldSignature = Long.MIN_VALUE;
    private long composeSignature = Long.MIN_VALUE;

    /* The box */
    private float hx;
    private float hy;
    private float hz;
    private float cy;

    public DecayFormRenderer(DecayForm form)
    {
        super(form);
    }

    /**
     * A block changed in the world. Every surface volume whose box takes it in - or whose sides it
     * touches, one block round - is rebuilt in the next frame drawn.
     */
    public static void onBlockChanged(BlockPos pos)
    {
        if (SURFACES.isEmpty())
        {
            return;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        for (DecayFormRenderer renderer : SURFACES)
        {
            int[] b = renderer.scanned;

            if (b != null && x >= b[0] - 1 && x <= b[3] + 1 && y >= b[1] - 1 && y <= b[4] + 1 && z >= b[2] - 1 && z <= b[5] + 1)
            {
                renderer.dirty = true;
            }
        }
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

                float a = Math.min(1F, out[3]);

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

        this.resolveBox(objectMode);
        this.updateGeometry(context, mc, objectMode, virtualWall);

        if (this.count == 0)
        {
            return;
        }

        ShaderProgram program = BBSShaders.getDecayProgram();

        if (program == null || BBSRendering.isIrisWorldShadersEnabled())
        {
            this.drawAtlas(context, p);
        }
        else
        {
            this.drawShader(context, p, program, objectMode || virtualWall);
        }
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

    /* The sides */

    private void updateGeometry(FormRenderingContext context, MinecraftClient mc, boolean objectMode, boolean virtualWall)
    {
        boolean surface = !objectMode && !virtualWall;
        Matrix4f world = surface ? this.worldMatrix(context) : null;
        long h = this.form.mode.get();

        h = h * 31 + Float.floatToIntBits(this.hx);
        h = h * 31 + Float.floatToIntBits(this.hy);
        h = h * 31 + Float.floatToIntBits(this.hz);
        h = h * 31 + Float.floatToIntBits(this.cy);
        h = h * 31 + (virtualWall ? 1 : 0);

        if (surface)
        {
            /* Rounded, so a form that stands still does not rebuild on float noise */
            for (int i = 0; i < 4; i++)
            {
                for (int j = 0; j < 4; j++)
                {
                    h = h * 31 + Math.round(world.get(i, j) * 256F);
                }
            }

            h = h * 31 + System.identityHashCode(mc.world);
        }
        else
        {
            h = h * 31 + context.light;
        }

        boolean retry = surface && this.retryAt >= 0L && mc.world != null && mc.world.getTime() >= this.retryAt;

        if (h == this.geometrySignature && !this.dirty && !retry)
        {
            return;
        }

        this.geometrySignature = h;
        this.dirty = false;
        this.retryAt = -1L;
        this.count = 0;
        this.geometryVersion++;

        if (surface)
        {
            this.buildSurfaces(mc.world, world);
            SURFACES.add(this);
        }
        else
        {
            this.scanned = null;
            SURFACES.remove(this);
            this.patternMatrix.identity();

            if (virtualWall)
            {
                this.addLocalSide(Direction.SOUTH, -this.hx, this.hx, this.cy - this.hy, this.cy + this.hy, 0, 0, context.light);
            }
            else
            {
                for (Direction dir : Direction.values())
                {
                    this.addLocalSide(dir, -this.hx, this.hx, this.cy - this.hy, this.cy + this.hy, -this.hz, this.hz, context.light);
                }
            }
        }
    }

    /** The open sides of the world's blocks inside the box, in world blocks, brought to the form's space */
    private void buildSurfaces(ClientWorld world, Matrix4f worldMatrix)
    {
        Vector3f origin = worldMatrix.getTranslation(new Vector3f());
        Matrix4d inverse = new Matrix4d(worldMatrix).invert();
        Matrix3f localNormal = new Matrix3f(worldMatrix).transpose();

        this.patternMatrix.set(worldMatrix);

        double radius = Math.sqrt(this.hx * this.hx + this.hy * this.hy + this.hz * this.hz) + 1.0;
        int minX = MathHelper.floor(origin.x - radius);
        int maxX = MathHelper.ceil(origin.x + radius);
        int minY = MathHelper.floor(origin.y - radius);
        int maxY = MathHelper.ceil(origin.y + radius);
        int minZ = MathHelper.floor(origin.z - radius);
        int maxZ = MathHelper.ceil(origin.z + radius);

        this.scanned = new int[] {minX, minY, minZ, maxX, maxY, maxZ};

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();
        double[][] corners = new double[4][3];
        double[] local = new double[12];
        Vector3d v = new Vector3d();
        Vector3f normal = new Vector3f();
        double eps = 1e-6;

        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
        {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
            {
                if (!world.getChunkManager().isChunkLoaded(cx, cz))
                {
                    /* Look again in a second: a chunk that arrives later brings no block change of its own */
                    this.retryAt = world.getTime() + 20L;
                }
            }
        }

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

                            boxCorners(dir, x + box.minX, x + box.maxX, y + box.minY, y + box.maxY, z + box.minZ, z + box.maxZ, corners);

                            /* Lifted, then into the form's space, where the box test is made */
                            float lminX = Float.MAX_VALUE, lminY = Float.MAX_VALUE, lminZ = Float.MAX_VALUE;
                            float lmaxX = -Float.MAX_VALUE, lmaxY = -Float.MAX_VALUE, lmaxZ = -Float.MAX_VALUE;

                            for (int k = 0; k < 4; k++)
                            {
                                corners[k][0] += dir.getOffsetX() * LIFT;
                                corners[k][1] += dir.getOffsetY() * LIFT;
                                corners[k][2] += dir.getOffsetZ() * LIFT;

                                inverse.transformPosition(v.set(corners[k][0], corners[k][1], corners[k][2]));

                                local[k * 3] = v.x;
                                local[k * 3 + 1] = v.y;
                                local[k * 3 + 2] = v.z;
                                lminX = Math.min(lminX, (float) v.x);
                                lminY = Math.min(lminY, (float) v.y);
                                lminZ = Math.min(lminZ, (float) v.z);
                                lmaxX = Math.max(lmaxX, (float) v.x);
                                lmaxY = Math.max(lmaxY, (float) v.y);
                                lmaxZ = Math.max(lmaxZ, (float) v.z);
                            }

                            if (lmaxX < -this.hx || lminX > this.hx || lmaxY < -this.hy || lminY > this.hy || lmaxZ < -this.hz || lminZ > this.hz)
                            {
                                continue;
                            }

                            normal.set(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ()).mul(localNormal).normalize();
                            this.addSide(local, corners, dir, light, normal);
                        }
                    }
                }
            }
        }
    }

    /** One side of the shell, or of the wall drawn without a world: in the form's space, where the pattern is read too */
    private void addLocalSide(Direction dir, double minX, double maxX, double minY, double maxY, double minZ, double maxZ, int light)
    {
        double[][] corners = new double[4][3];
        double[] local = new double[12];

        boxCorners(dir, minX, maxX, minY, maxY, minZ, maxZ, corners);

        for (int k = 0; k < 4; k++)
        {
            corners[k][0] += dir.getOffsetX() * LIFT;
            corners[k][1] += dir.getOffsetY() * LIFT;
            corners[k][2] += dir.getOffsetZ() * LIFT;
            local[k * 3] = corners[k][0];
            local[k * 3 + 1] = corners[k][1];
            local[k * 3 + 2] = corners[k][2];
        }

        this.addSide(local, corners, dir, light, new Vector3f(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ()));
    }

    /**
     * Keeps one side: {@code local} the corners in the form's space, {@code pattern} where the pattern
     * is read (world blocks on a wall, the form's space on an object).
     */
    private void addSide(double[] local, double[][] pattern, Direction dir, int light, Vector3f normal)
    {
        int at = this.count * STRIDE;

        if (at + STRIDE > this.sides.length)
        {
            this.sides = Arrays.copyOf(this.sides, this.sides.length * 2);
        }

        float[] d = this.sides;

        for (int k = 0; k < 12; k++)
        {
            d[at + k] = (float) local[k];
        }

        /* The texture follows the block: a corner's place inside the side's block, by the side's two axes */
        double cx = (pattern[0][0] + pattern[2][0]) * 0.5;
        double cy = (pattern[0][1] + pattern[2][1]) * 0.5;
        double cz = (pattern[0][2] + pattern[2][2]) * 0.5;
        double bx = Math.floor(cx);
        double by = Math.floor(cy);
        double bz = Math.floor(cz);

        for (int k = 0; k < 4; k++)
        {
            double px = pattern[k][0];
            double py = pattern[k][1];
            double pz = pattern[k][2];
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

            d[at + 12 + k * 2] = (float) tu;
            d[at + 13 + k * 2] = (float) tv;
            d[at + 24 + k * 3] = (float) px;
            d[at + 25 + k * 3] = (float) py;
            d[at + 26 + k * 3] = (float) pz;
        }

        d[at + 20] = Float.intBitsToFloat(light);
        d[at + 21] = normal.x;
        d[at + 22] = normal.y;
        d[at + 23] = normal.z;

        this.count++;
    }

    /** The four corners of one side of a box, wound so the side faces out: the first, the one down its V, the opposite, the one along its U */
    private static void boxCorners(Direction dir, double minX, double maxX, double minY, double maxY, double minZ, double maxZ, double[][] c)
    {
        switch (dir)
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

    /* The GPU path */

    private void drawShader(FormRenderingContext context, DecayPattern.Params p, ShaderProgram program, boolean patternIsLocal)
    {
        boolean queue = FormTranslucentQueue.isActive();
        VertexBuffer target = this.buffer;
        boolean owned = false;

        if (this.bufferVersion != this.geometryVersion || target == null)
        {
            /* A buffer queued earlier this frame must stay as it was until the flush draws it */
            boolean inFlight = target != null && System.nanoTime() - this.bufferQueuedAt < 100_000_000L;

            if (inFlight)
            {
                target = new VertexBuffer(VertexBuffer.Usage.STATIC);
                owned = true;
            }
            else if (target == null)
            {
                target = this.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }

            this.upload(target);

            if (!owned)
            {
                this.bufferVersion = this.geometryVersion;
            }
        }

        Link link = this.form.preset.get() == DecayForm.PRESET_CUSTOM ? this.form.texture.get() : null;
        Texture texture = BBSModClient.getTextures().getTexture(link == null ? new Link(Link.COLOR, "ffffffff") : link);
        MatrixStack.Entry entry = context.stack.peek();
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(entry.getPositionMatrix());
        Matrix3f normalMat = new Matrix3f(entry.getNormalMatrix());
        Matrix4f toPattern = patternIsLocal ? new Matrix4f() : new Matrix4f(this.patternMatrix);
        Color tint = new Color(1F, 1F, 1F, 1F);

        tint.mul(context.color);

        float[] values = {
            p.progress, p.softness, p.scale, p.depthInset,
            this.hx, this.hy, this.hz, this.cy,
            p.primary[0], p.primary[1], p.primary[2], p.opacity,
            p.edge[0], p.edge[1], p.edge[2], patternIsLocal ? 0F : 1F,
            tint.r, tint.g, tint.b, tint.a
        };
        int preset = p.preset;
        int seed = p.seed;
        Runnable uniforms = () ->
        {
            uniform(program, "DecayToLocal", new Matrix4f());
            uniform(program, "DecayToPattern", toPattern);
            uniform4(program, "DecayParams", values, 0);
            uniform4(program, "DecayBox", values, 4);
            uniform4(program, "DecayPrimary", values, 8);
            uniform4(program, "DecayEdge", values, 12);
            uniform4(program, "DecayTint", values, 16);

            GlUniform u = program.getUniform("DecayPreset");

            if (u != null) u.set(preset);

            u = program.getUniform("DecaySeed");

            if (u != null) u.set(seed);
        };

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (queue)
        {
            Vector3f origin = modelView.transformPosition(new Vector3f());

            this.bufferQueuedAt = owned ? this.bufferQueuedAt : System.nanoTime();

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                target, owned, () -> program, FormTranslucentQueue.PASS_TRANSLUCENT, false, texture, modelView, normalMat, origin, null, true, uniforms, null
            ));
        }
        else
        {
            if (texture != null)
            {
                BBSModClient.getTextures().bindTexture(texture);
            }

            uniforms.run();
            uniform(program, "NormalMat", normalMat);

            target.bind();
            target.draw(modelView, RenderSystem.getProjectionMatrix(), program);
            VertexBuffer.unbind();

            if (owned)
            {
                target.close();
            }
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    private void upload(VertexBuffer target)
    {
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        float[] d = this.sides;

        for (int n = 0; n < this.count; n++)
        {
            int at = n * STRIDE;
            int light = Float.floatToRawIntBits(d[at + 20]);

            for (int k : TRIANGLES)
            {
                builder.vertex(d[at + k * 3], d[at + k * 3 + 1], d[at + k * 3 + 2])
                    .color(1F, 1F, 1F, 1F)
                    .texture(d[at + 12 + k * 2], d[at + 13 + k * 2])
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(d[at + 21], d[at + 22], d[at + 23])
                    .next();
            }
        }

        target.bind();
        Bufs.upload(target, builder);
        VertexBuffer.unbind();
    }

    private static void uniform(ShaderProgram program, String name, Matrix4f value)
    {
        GlUniform u = program.getUniform(name);

        if (u != null) u.set(value);
    }

    private static void uniform(ShaderProgram program, String name, Matrix3f value)
    {
        GlUniform u = program.getUniform(name);

        if (u != null) u.set(value);
    }

    private static void uniform4(ShaderProgram program, String name, float[] values, int at)
    {
        GlUniform u = program.getUniform(name);

        if (u != null) u.set(values[at], values[at + 1], values[at + 2], values[at + 3]);
    }

    /* The atlas path, under a shader pack */

    private void drawAtlas(FormRenderingContext context, DecayPattern.Params p)
    {
        int sides = Math.min(this.count, ATLAS_MAX_SIDES);

        this.updateField(p, sides);
        this.updateAtlas(p, sides);

        if (this.atlas == null)
        {
            return;
        }

        MatrixStack matrices = context.stack;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMat = matrices.peek().getNormalMatrix();
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
        int atlasId = this.atlas.getGlId();
        Color tint = new Color(1F, 1F, 1F, 1F);

        tint.mul(context.color);

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();
        RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentProgram);
        RenderSystem.setShaderTexture(0, atlasId);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        float[] d = this.sides;
        float du = 1F / ATLAS_WIDTH;
        float dv = 1F / this.atlasHeight;
        float[][] corner = {{0F, 0F}, {0F, 1F}, {1F, 1F}, {1F, 0F}};

        for (int n = 0; n < sides; n++)
        {
            int at = n * STRIDE;
            int light = Float.floatToRawIntBits(d[at + 20]);
            float tx = (n % TILES_PER_ROW) * TILE;
            float ty = (n / TILES_PER_ROW) * TILE;

            for (int k : TRIANGLES)
            {
                float u = (tx + corner[k][0] * TILE) * du;
                float v = (ty + corner[k][1] * TILE) * dv;

                VertexUtils.normal(builder.vertex(matrix, d[at + k * 3], d[at + k * 3 + 1], d[at + k * 3 + 2])
                    .color(tint.r, tint.g, tint.b, tint.a)
                    .texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light), normalMat, d[at + 21], d[at + 22], d[at + 23]).next();
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (FormTranslucentQueue.isActive())
        {
            ShaderProgram finalShader = RenderSystem.getShader();
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);

            vb.bind();
            Bufs.upload(vb, builder);
            VertexBuffer.unbind();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                vb, () -> finalShader, null, modelView, null, origin, null, true, () -> RenderSystem.setShaderTexture(0, atlasId), null
            ));
        }
        else
        {
            Bufs.draw(builder);
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    /** What every texel of every side is, kept until the sides, the preset, the seed or the scale change */
    private void updateField(DecayPattern.Params p, int sides)
    {
        long h = ((long) this.geometryVersion << 32) ^ p.fieldSignature();

        h = h * 31 + Float.floatToIntBits(this.hx) + Float.floatToIntBits(this.hy) * 7 + Float.floatToIntBits(this.hz) * 13 + Float.floatToIntBits(this.cy) * 17;

        if (h == this.fieldSignature)
        {
            return;
        }

        this.fieldSignature = h;
        this.composeSignature = Long.MIN_VALUE;
        this.fieldSides = sides;

        int needed = sides * TILE * TILE * 3;

        if (this.field.length < needed)
        {
            this.field = new char[needed];
        }

        boolean clip = this.scanned != null;
        float[] f = new float[4];
        float[] scratch = new float[2];
        float[] d = this.sides;
        int w = 0;

        for (int n = 0; n < sides; n++)
        {
            int at = n * STRIDE;

            for (int j = 0; j < TILE; j++)
            {
                double v = (j + 0.5) / TILE;

                for (int i = 0; i < TILE; i++, w += 3)
                {
                    double u = (i + 0.5) / TILE;

                    /* The texel's centre in the form's space and where the pattern is read: corner 0, plus v down corner 1, plus u along corner 3 */
                    float lx = (float) (d[at] + (d[at + 3] - d[at]) * v + (d[at + 9] - d[at]) * u);
                    float ly = (float) (d[at + 1] + (d[at + 4] - d[at + 1]) * v + (d[at + 10] - d[at + 1]) * u) - this.cy;
                    float lz = (float) (d[at + 2] + (d[at + 5] - d[at + 2]) * v + (d[at + 11] - d[at + 2]) * u);

                    if (clip && (Math.abs(lx) > this.hx || Math.abs(ly) > this.hy || Math.abs(lz) > this.hz))
                    {
                        this.field[w] = OUTSIDE;

                        continue;
                    }

                    double px = d[at + 24] + (d[at + 27] - d[at + 24]) * v + (d[at + 33] - d[at + 24]) * u;
                    double py = d[at + 25] + (d[at + 28] - d[at + 25]) * v + (d[at + 34] - d[at + 25]) * u;
                    double pz = d[at + 26] + (d[at + 29] - d[at + 26]) * v + (d[at + 35] - d[at + 26]) * u;
                    float rx = lx / Math.max(this.hx, 1e-4F);
                    float ry = ly / Math.max(this.hy, 1e-4F);
                    float rz = lz / Math.max(this.hz, 1e-4F);
                    float radial = Math.min(1F, (float) Math.sqrt(rx * rx + ry * ry + rz * rz) / 1.7320508F);
                    float y01 = MathHelper.clamp(ly / (2F * Math.max(this.hy, 1e-4F)) + 0.5F, 0F, 1F);

                    DecayPattern.field(p, px, py, pz, y01, radial, f, scratch);

                    boolean cracks = p.preset == DecayForm.PRESET_CRACKS;

                    this.field[w] = bin(f[0], 1F);
                    this.field[w + 1] = bin(f[1], cracks ? 0.5F : 1F);
                    this.field[w + 2] = bin(f[2], 0.5F);
                }
            }
        }
    }

    private static char bin(float value, float range)
    {
        return (char) MathHelper.clamp((int) (value / range * BINS), 0, BINS - 1);
    }

    /** The colours for the current progress, written into the atlas and uploaded; skipped while nothing they depend on changed */
    private void updateAtlas(DecayPattern.Params p, int sides)
    {
        long h = this.fieldSignature * 31 + p.composeSignature();

        if (h == this.composeSignature && this.atlas != null)
        {
            return;
        }

        this.composeSignature = h;

        int rows = Math.max(1, (sides + TILES_PER_ROW - 1) / TILES_PER_ROW);
        int height = MathHelper.smallestEncompassingPowerOfTwo(rows * TILE);

        if (this.atlas == null || this.atlasHeight != height)
        {
            if (this.atlas != null)
            {
                this.atlas.close();
            }

            this.atlas = new NativeImageBackedTexture(ATLAS_WIDTH, height, true);
            this.atlasHeight = height;
        }

        NativeImage image = this.atlas.getImage();

        if (image == null)
        {
            return;
        }

        Composer composer = new Composer(p);
        int r = 0;

        for (int n = 0; n < this.fieldSides && n < sides; n++)
        {
            int tx = (n % TILES_PER_ROW) * TILE;
            int ty = (n / TILES_PER_ROW) * TILE;

            for (int j = 0; j < TILE; j++)
            {
                for (int i = 0; i < TILE; i++, r += 3)
                {
                    image.setColor(tx + i, ty + j, composer.abgr(this.field[r], this.field[r + 1], this.field[r + 2]));
                }
            }
        }

        this.atlas.upload();
    }

    /**
     * {@link DecayPattern#compose} through tables: everything that depends only on the progress, the
     * softness and the colours is worked out once per bin, so a texel is a handful of multiplications.
     */
    private static class Composer
    {
        private final boolean cracks;
        private final boolean custom;
        private final float[] primary;
        private final float[] edge;
        private final float opacity;
        private final float dark;
        private final float haloDark;

        /* By the decay time */
        private final float[] gate = new float[BINS];
        private final float[] body = new float[BINS];
        private final float[] branchGate = new float[BINS];
        /* By the distance to a break, or a branch */
        private final float[] core = new float[BINS];
        private final float[] halo = new float[BINS];
        private final float[] branch = new float[BINS];

        public Composer(DecayPattern.Params p)
        {
            this.cracks = p.preset == DecayForm.PRESET_CRACKS;
            this.custom = p.preset == DecayForm.PRESET_CUSTOM;
            this.primary = p.primary;
            this.edge = p.edge;
            this.opacity = p.progress <= 0.0005F ? 0F : p.opacity;
            this.dark = Math.max(0F, 1F - p.depthInset * 4F);
            this.haloDark = Math.max(0.2F, 1F - p.depthInset * 2.5F);

            float soft = p.softness;
            float progress = p.progress;
            float width = 0.03F + 0.03F * progress;
            float haloWidth = width * (2.4F + p.depthInset * 18F);
            float haloStrength = 0.32F + p.depthInset * 2F;

            for (int b = 0; b < BINS; b++)
            {
                float t = (b + 0.5F) / BINS;
                float dist = t * 0.5F;

                if (this.cracks)
                {
                    this.gate[b] = smooth((progress * (1F + soft) - t) / soft);
                    this.branchGate[b] = smoothstep(t * 0.5F + 0.3F, t * 0.5F + 0.5F, progress);
                    this.core[b] = 1F - smoothstep(width * 0.5F, width, dist);
                    this.halo[b] = (1F - smoothstep(width, haloWidth, dist)) * haloStrength;
                    this.branch[b] = 1F - smoothstep(width * 0.25F, width * 0.55F, dist);
                }
                else
                {
                    float u = (progress * (1F + 2.2F * soft) - t) / soft;

                    this.gate[b] = smooth(u);
                    this.body[b] = smoothstep(0.3F, 2.2F, u);
                }
            }
        }

        private static float smooth(float t)
        {
            t = t < 0F ? 0F : (t > 1F ? 1F : t);

            return t * t * (3F - 2F * t);
        }

        private static float smoothstep(float a, float b, float x)
        {
            return smooth((x - a) / (b - a));
        }

        public int abgr(char time, char a, char b)
        {
            if (time == OUTSIDE)
            {
                return 0;
            }

            float gate = this.gate[time];

            if (gate <= 0.002F || this.opacity <= 0F)
            {
                return 0;
            }

            float r;
            float g;
            float bl;
            float alpha;

            if (this.cracks)
            {
                float core = Math.max(this.core[a] * gate, this.branch[b] * this.branchGate[time] * gate * 0.85F);
                float halo = this.halo[a] * gate;

                alpha = Math.min(1F, core + halo * (1F - core));

                if (alpha <= 0.002F)
                {
                    return 0;
                }

                float hw = halo * (1F - core);

                r = (this.primary[0] * this.dark * core + this.edge[0] * this.haloDark * hw) / alpha;
                g = (this.primary[1] * this.dark * core + this.edge[1] * this.haloDark * hw) / alpha;
                bl = (this.primary[2] * this.dark * core + this.edge[2] * this.haloDark * hw) / alpha;
            }
            else
            {
                float body = this.body[time];
                float mottle = this.custom ? 1F : 0.8F + (a + 0.5F) / BINS * 0.4F;

                alpha = gate;
                r = (this.edge[0] + (this.primary[0] - this.edge[0]) * body) * mottle;
                g = (this.edge[1] + (this.primary[1] - this.edge[1]) * body) * mottle;
                bl = (this.edge[2] + (this.primary[2] - this.edge[2]) * body) * mottle;
            }

            int ia = MathHelper.clamp((int) (alpha * this.opacity * 255F), 0, 255);

            return ia << 24 | channel(bl) << 16 | channel(g) << 8 | channel(r);
        }
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
