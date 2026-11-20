package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.FakeSpaceForm;
import mchorse.bbs_mod.forms.renderers.utils.FakeSpaceSky;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.client.light.IRLightPositionResolver;

/**
 * The artificial space scene. Nothing here is in the world: no blocks, no collision.
 *
 * <p><b>The sky</b> - deep space, the nebula, the stars - is a dome around the camera, at a distance
 * just inside the far plane, drawn without writing depth so it is behind everything. Moving the camera
 * moves nothing on it. The stars streak away from the form's front by the streak factor.</p>
 *
 * <p><b>The scene</b> - planets, rings, the asteroid belt, the galaxy - is real space round the form,
 * in blocks. The opaque parts (globes, rocks) write depth and test it, so they hide one another and
 * the camera can fly round them; the see-through parts (atmospheres, rings, the galaxy) test depth
 * without writing it and come after, so a ring passes behind its planet and in front of it as it
 * should. Faces are not culled: a closed mesh written to depth needs no winding to come out right,
 * which is also what keeps a globe from showing its far side through its near one in stripes.</p>
 *
 * <p>In the form editor and on the podium the whole scene is shown shrunk, so it fits the preview.</p>
 */
public class FakeSpaceFormRenderer extends FormRenderer<FakeSpaceForm>
{
    private static final float PI = (float) Math.PI;

    /** The form's front, which the stars streak away from */
    private final Vector3f front = new Vector3f(0F, 0F, 1F);

    /** How much the scene is shrunk in an editor preview */
    private static final float PREVIEW_SCALE = 0.03F;

    /* A low icosahedron, the base of every rock */
    private static final float[][] ICO_VERTICES;
    private static final int[][] ICO_FACES = {
        {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
        {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
    };

    static
    {
        float t = (1F + (float) Math.sqrt(5D)) / 2F;
        float[][] raw = {
            {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t},
            {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}
        };

        ICO_VERTICES = new float[raw.length][];

        for (int i = 0; i < raw.length; i++)
        {
            float l = (float) Math.sqrt(raw[i][0] * raw[i][0] + raw[i][1] * raw[i][1] + raw[i][2] * raw[i][2]);

            ICO_VERTICES[i] = new float[] {raw[i][0] / l, raw[i][1] / l, raw[i][2] / l};
        }
    }

    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f c = new Vector3f();
    private final Vector3f d = new Vector3f();

    public FakeSpaceFormRenderer(FakeSpaceForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Color background = this.form.backgroundColor.get();
        int base = 0xff000000 | (int) (background.r * 255F) << 16 | (int) (background.g * 255F) << 8 | (int) (background.b * 255F);

        context.batcher.gradientVBox(x1 + 4, y1 + 4, x2 - 4, y2 - 4, base, 0xff1c1440);

        for (int i = 0; i < 24; i++)
        {
            float x = x1 + 6 + hash(i, 1, 0, 7) * (x2 - x1 - 12);
            float y = y1 + 6 + hash(i, 2, 0, 7) * (y2 - y1 - 12);
            int level = 120 + (int) (hash(i, 3, 0, 7) * 135F);

            context.batcher.box((int) x, (int) y, (int) x + 1, (int) y + 1, 0xff000000 | level << 16 | level << 8 | level);
        }

        int cx = (x1 + x2) / 2 + 6;
        int cy = (y1 + y2) / 2 + 2;
        int r = Math.max(3, Math.min(x2 - x1, y2 - y1) / 5);

        context.batcher.box(cx - r, cy - r / 2, cx + r, cy + r / 2, 0xff3d7ac2);
        context.batcher.box(cx - r / 2, cy - r, cx + r / 2, cy + r, 0xff3d7ac2);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (context.isPicking() || BBSRendering.isIrisShadowPass() || ShadowBakeState.isBaking() || FormPortals.isRenderingPortal())
        {
            return;
        }

        FormRenderType type = context.type;
        boolean editor = context.ui || context.modelRenderer || type == FormRenderType.PREVIEW;

        if (!editor && type != FormRenderType.ENTITY && type != FormRenderType.MODEL_BLOCK)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float seconds = mc.world == null ? 0F : (mc.world.getTime() % 72000L + context.getTransition()) / 20F;
        MatrixStack stack = context.stack;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        try
        {
            if (editor)
            {
                stack.push();
                stack.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);

                /* No camera to measure against in a preview: the sky is a small dome round the form, the scene in front of it */
                this.sky(stack.peek().getPositionMatrix(), new Vector3f(), 600F);
                this.scene(stack, null, seconds);

                stack.pop();

                return;
            }

            Matrix4f world;

            if (context.world != null && context.entity != null)
            {
                world = new Matrix4f(context.world.peek().getPositionMatrix());
            }
            else
            {
                org.joml.Vector3d origin = IRLightPositionResolver.resolve(context);

                world = new Matrix4f().translation((float) origin.x, (float) origin.y, (float) origin.z);
            }

            if (Math.abs(world.determinant()) < 1.0E-9F)
            {
                return;
            }

            /* Not drawn here: an actor or a block whose own place is off screen is not drawn at all, and its sky
             * went with it - a hole of empty space wherever the camera looked away from the form. The scene is
             * kept and drawn after the world's entities, wherever the form is (see renderWorld). */
            SCENES.put(this, new Scene(world, System.nanoTime()));
        }
        finally
        {
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    /* The scenes in the world, drawn after its entities whether or not their form was drawn this frame */

    private record Scene(Matrix4f world, long seen) {}

    private static final java.util.Map<FakeSpaceFormRenderer, Scene> SCENES = new java.util.WeakHashMap<>();

    /** How long a scene whose form stopped being drawn (culled, out of view) is still kept, in nanoseconds */
    private static final long KEEP_NANOS = 60_000_000_000L;

    /** The far plane the space scenes are drawn with: a planet fifty thousand blocks off is still well inside it */
    private static final float SPACE_FAR = 200000F;
    private static final float SPACE_NEAR = 0.5F;
    private static final float SKY_RADIUS = 150000F;

    /**
     * Draws the space scenes of the world, before its terrain. With a projection of their own whose far
     * plane is two hundred thousand blocks out: first the sky of the newest scene around the camera, a
     * whole sphere with nothing culled and no depth written, then every scene's planets, belt and galaxy
     * where its form is, hiding one another by depth. Then the depth is cleared and the game's own
     * projection put back, so the world is drawn over space as if space were infinitely far: nothing is
     * cut by the game's far plane, nothing sinks into the dome, and flying round a planet has its true
     * parallax.
     */
    public static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        if (SCENES.isEmpty())
        {
            return;
        }

        long now = System.nanoTime();

        SCENES.values().removeIf((scene) -> now - scene.seen() > KEEP_NANOS);

        if (SCENES.isEmpty() || BBSRendering.isIrisShadowPass() || FormPortals.isRenderingPortal())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack stack = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        float delta = context./*$ wtd*/tickDelta()/**/;
        float seconds = mc.world == null ? 0F : (mc.world.getTime() % 72000L + delta) / 20F;
        FakeSpaceFormRenderer newest = null;
        long newestSeen = Long.MIN_VALUE;

        for (java.util.Map.Entry<FakeSpaceFormRenderer, Scene> entry : SCENES.entrySet())
        {
            if (entry.getValue().seen() > newestSeen)
            {
                newest = entry.getKey();
                newestSeen = entry.getValue().seen();
            }
        }

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.systems.VertexSorter sorting = RenderSystem.getVertexSorting();
        Matrix4f far = new Matrix4f(projection);

        /* Only the depth range of a perspective changes: the field of view and the aspect stay the game's */
        if (Math.abs(far.m23() + 1F) < 1.0E-3F)
        {
            far.m22((SPACE_FAR + SPACE_NEAR) / (SPACE_NEAR - SPACE_FAR));
            far.m32(2F * SPACE_FAR * SPACE_NEAR / (SPACE_NEAR - SPACE_FAR));
        }

        RenderSystem.setProjectionMatrix(far, sorting);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        try
        {
            /* The sky: round the camera, in the world's axes, never moved or cut by the form */
            if (newest != null)
            {
                Scene scene = SCENES.get(newest);
                Color background = newest.form.backgroundColor.get();
                float radius = SKY_RADIUS;

                newest.front.set(0F, 0F, 1F);
                scene.world().transformDirection(newest.front);

                if (newest.front.lengthSquared() < 1.0E-8F)
                {
                    newest.front.set(0F, 0F, 1F);
                }

                newest.front.normalize();
                FakeSpaceSky.drawn(background.r, background.g, background.b);
                newest.sky(stack.peek().getPositionMatrix(), new Vector3f(), radius);
            }

            /* The scenes where their forms are */
            for (java.util.Map.Entry<FakeSpaceFormRenderer, Scene> entry : SCENES.entrySet())
            {
                Matrix4f world = entry.getValue().world();
                Vector3f eye = new Matrix4f(world).invert().transformPosition(new Vector3f((float) camera.x, (float) camera.y, (float) camera.z));

                stack.push();
                stack.translate(-camera.x, -camera.y, -camera.z);
                stack.multiplyPositionMatrix(world);
                entry.getKey().scene(stack, eye, seconds);
                stack.pop();
            }
        }
        finally
        {
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

            /* Space is behind everything: the world draws over it from a clean depth, with its own projection */
            RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    /* The sky: around the camera, behind everything */

    private void sky(Matrix4f matrix, Vector3f eye, float radius)
    {
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (this.form.skyDome.get())
        {
            this.dome(matrix, eye, radius);
        }

        this.stars(matrix, eye, radius);
    }

    /** The deep space colour with the nebula across it, as a sphere whose vertices carry the colour */
    private void dome(Matrix4f m, Vector3f eye, float radius)
    {
        Color bg = this.form.backgroundColor.get();
        Color na = this.form.nebulaColorA.get();
        Color nb = this.form.nebulaColorB.get();
        float amount = clamp(this.form.nebulaAmount.get(), 0F, 1F);
        float scale = Math.max(this.form.nebulaScale.get(), 0.1F);
        int rings = 20;
        int sectors = 40;
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float[][] colors = new float[(rings + 1) * (sectors + 1)][];
        float[][] points = new float[(rings + 1) * (sectors + 1)][];

        for (int i = 0; i <= rings; i++)
        {
            float lat = PI * i / rings - PI / 2F;

            for (int j = 0; j <= sectors; j++)
            {
                float lon = 2F * PI * j / sectors;
                float x = (float) (Math.cos(lat) * Math.cos(lon));
                float y = (float) Math.sin(lat);
                float z = (float) (Math.cos(lat) * Math.sin(lon));
                float n1 = fbm(x * scale * 2F + 3.1F, y * scale * 2F, z * scale * 2F - 1.7F);
                float n2 = fbm(x * scale * 1.3F - 5.2F, y * scale * 1.3F + 2.4F, z * scale * 1.3F);
                float density = smooth(0.38F, 0.85F, n1) * amount;
                float mix = smooth(0.3F, 0.7F, n2);
                int k = i * (sectors + 1) + j;

                colors[k] = new float[] {
                    bg.r + (na.r + (nb.r - na.r) * mix) * density * 0.7F,
                    bg.g + (na.g + (nb.g - na.g) * mix) * density * 0.7F,
                    bg.b + (na.b + (nb.b - na.b) * mix) * density * 0.7F
                };
                points[k] = new float[] {eye.x + x * radius, eye.y + y * radius, eye.z + z * radius};
            }
        }

        grid(builder, m, points, colors, rings, sectors, 1F);
        Bufs.draw(builder);
    }

    /** Two triangles for each cell of a rings x sectors grid of points, every index once - no gaps, no seams */
    private static void grid(BufferBuilder builder, Matrix4f m, float[][] points, float[][] colors, int rings, int sectors, float alpha)
    {
        for (int i = 0; i < rings; i++)
        {
            for (int j = 0; j < sectors; j++)
            {
                int k00 = i * (sectors + 1) + j;
                int k01 = k00 + 1;
                int k10 = k00 + sectors + 1;
                int k11 = k10 + 1;

                vertex(builder, m, points[k00], colors[k00], alpha);
                vertex(builder, m, points[k10], colors[k10], alpha);
                vertex(builder, m, points[k11], colors[k11], alpha);
                vertex(builder, m, points[k00], colors[k00], alpha);
                vertex(builder, m, points[k11], colors[k11], alpha);
                vertex(builder, m, points[k01], colors[k01], alpha);
            }
        }
    }

    /** The stars: dots, or with a streak factor streaks of light away from the form's front */
    private void stars(Matrix4f m, Vector3f eye, float radius)
    {
        FakeSpaceForm form = this.form;
        float density = clamp(form.starDensity.get(), 0F, 1F);
        int count = (int) (150F + density * 3600F);
        float brightness = clamp(form.starBrightness.get(), 0F, 2F);
        float size = Math.max(form.starSize.get(), 0.1F);
        float streak = clamp(form.streakFactor.get(), 0F, 1F);
        int seed = form.starSeed.get();
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < count; i++)
        {
            float u = hash(i, seed, 0, 11);
            float v = hash(i, seed, 1, 11);
            float h = hash(i, seed, 2, 11);
            float tint = hash(i, seed, 3, 11);
            float z = 1F - 2F * u;
            float ring = (float) Math.sqrt(Math.max(0F, 1F - z * z));
            float phi = 2F * PI * v;

            this.a.set(ring * (float) Math.cos(phi), z, ring * (float) Math.sin(phi));

            float level = Math.min(1F, (0.22F + 0.78F * h * h * h * h) * brightness);
            float width = radius * (0.0011F + 0.0026F * h * h * h) * size;
            float r = 0.75F + 0.25F * tint + (tint > 0.8F ? 0.15F : 0F);
            float g = 0.82F + 0.1F * tint;
            float bl = 1F - 0.25F * tint;

            this.star(builder, m, eye, radius, width, streak, hash(i, seed, 4, 11), level, r, g, bl);
        }

        Bufs.draw(builder);
    }

    private void star(BufferBuilder builder, Matrix4f m, Vector3f eye, float radius, float width, float streak, float variety, float level, float r, float g, float bl)
    {
        float along = this.a.dot(this.front);
        float sine = (float) Math.sqrt(Math.max(0F, 1F - along * along));
        float length = streak * (0.03F + 0.55F * sine) * (0.4F + 0.6F * variety);

        this.b.set(this.a).fma(-along, this.front);

        if (length < 0.002F || this.b.lengthSquared() < 1.0E-8F)
        {
            tangents(this.a, this.b, this.c);

            float x = eye.x + this.a.x * radius;
            float y = eye.y + this.a.y * radius;
            float zz = eye.z + this.a.z * radius;

            quad(builder, m,
                x - this.b.x * width - this.c.x * width, y - this.b.y * width - this.c.y * width, zz - this.b.z * width - this.c.z * width,
                x + this.b.x * width - this.c.x * width, y + this.b.y * width - this.c.y * width, zz + this.b.z * width - this.c.z * width,
                x + this.b.x * width + this.c.x * width, y + this.b.y * width + this.c.y * width, zz + this.b.z * width + this.c.z * width,
                x - this.b.x * width + this.c.x * width, y - this.b.y * width + this.c.y * width, zz - this.b.z * width + this.c.z * width,
                r, g, bl, level, level);

            return;
        }

        this.b.normalize();
        length = Math.min(length, 1.2F);

        float cos = (float) Math.cos(length);
        float sin = (float) Math.sin(length);

        this.d.set(this.a).mul(cos).fma(-sin, this.b).normalize();
        this.c.set(this.a).cross(this.b).normalize();

        float w = width * (1F - 0.45F * streak);
        float hx = eye.x + this.a.x * radius;
        float hy = eye.y + this.a.y * radius;
        float hz = eye.z + this.a.z * radius;
        float tx = eye.x + this.d.x * radius;
        float ty = eye.y + this.d.y * radius;
        float tz = eye.z + this.d.z * radius;

        quad(builder, m,
            tx - this.c.x * w * 0.25F, ty - this.c.y * w * 0.25F, tz - this.c.z * w * 0.25F,
            hx - this.c.x * w, hy - this.c.y * w, hz - this.c.z * w,
            hx + this.c.x * w, hy + this.c.y * w, hz + this.c.z * w,
            tx + this.c.x * w * 0.25F, ty + this.c.y * w * 0.25F, tz + this.c.z * w * 0.25F,
            r, g, bl, 0F, Math.min(1F, level * (1F + streak * 0.4F)));
    }

    /* The scene: real space round the form */

    /** @param eye the camera in the form's frame, or null in a preview, where there is none */
    private void scene(MatrixStack stack, Vector3f eye, float seconds)
    {
        FakeSpaceForm form = this.form;
        Vector3f light = direction(form.lightYaw.get(), form.lightPitch.get(), new Vector3f());

        /* Opaque first, writing depth: the globes and the rocks */
        RenderSystem.depthMask(true);

        for (FakeSpaceForm.Planet planet : form.planets.getAllTyped())
        {
            drawGlobe(stack, planet, planet.x.get(), planet.y.get(), planet.z.get(), planet.radius.get(), light, seconds, eye);
        }

        this.asteroids(stack.peek().getPositionMatrix(), light, seconds);

        /* Then what can be seen through, testing depth but not writing it */
        RenderSystem.depthMask(false);

        for (FakeSpaceForm.Planet planet : form.planets.getAllTyped())
        {
            drawHaloAndRing(stack, planet, planet.x.get(), planet.y.get(), planet.z.get(), planet.radius.get(), light);
        }

        if (form.galaxyEnabled.get())
        {
            this.galaxy(stack.peek().getPositionMatrix(), eye, seconds);
        }
    }

    /**
     * A planet's globe, closed and lit: bands and weather in its two colours or its texture with the
     * UV offset and scale, turning about its leaning axis. Shared with the planet editor's preview.
     */
    public static void drawGlobe(MatrixStack stack, FakeSpaceForm.Planet planet, float cx, float cy, float cz, float radius, Vector3f light, float seconds, Vector3f eye)
    {
        Matrix4f m = stack.peek().getPositionMatrix();
        Link texture = planet.texture.get();
        boolean textured = texture != null;
        Color ca = planet.colorA.get();
        Color cb = planet.colorB.get();
        Color atmosphere = planet.atmosphereColor.get();
        float halo = clamp(planet.atmosphere.get(), 0F, 1F);
        Matrix3f turn = orientation(planet, seconds, true);
        int rings = 28;
        int sectors = 56;
        float su = planet.uvScaleX.get();
        float sv = planet.uvScaleY.get();
        float ou = planet.uvOffsetX.get();
        float ov = planet.uvOffsetY.get();
        Vector3f n = new Vector3f();
        Vector3f toEye = new Vector3f();
        BufferBuilder builder;

        if (textured)
        {
            BBSModClient.getTextures().bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        }
        else
        {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        }

        int size = (rings + 1) * (sectors + 1);
        float[][] points = new float[size][];
        float[][] colors = new float[size][];
        float[][] uvs = new float[size][];

        for (int i = 0; i <= rings; i++)
        {
            float lat = PI / 2F - PI * i / rings;

            for (int j = 0; j <= sectors; j++)
            {
                float lon = 2F * PI * j / sectors;
                float lx = (float) (Math.cos(lat) * Math.cos(lon));
                float ly = (float) Math.sin(lat);
                float lz = (float) (Math.cos(lat) * Math.sin(lon));
                int k = i * (sectors + 1) + j;

                /* The surface is in the planet's own frame; the world sees it turned and leaning */
                turn.transform(n.set(lx, ly, lz));

                float lit = smooth(-0.12F, 0.6F, n.dot(light));
                float shade = 0.04F + 0.96F * lit;
                float rim = 0F;

                points[k] = new float[] {cx + n.x * radius, cy + n.y * radius, cz + n.z * radius};

                if (eye != null)
                {
                    toEye.set(eye.x - points[k][0], eye.y - points[k][1], eye.z - points[k][2]).normalize();
                    rim = (float) Math.pow(1F - Math.max(0F, n.dot(toEye)), 2.6F) * halo * (0.25F + 0.75F * lit);
                }

                if (textured)
                {
                    colors[k] = new float[] {Math.min(1F, shade + atmosphere.r * rim), Math.min(1F, shade + atmosphere.g * rim), Math.min(1F, shade + atmosphere.b * rim)};
                    uvs[k] = new float[] {j / (float) sectors * su + ou, i / (float) rings * sv + ov};
                }
                else
                {
                    float bands = 0.5F + 0.5F * (float) Math.sin(ly * 8F + 3F * fbm(lx * 2.2F + 4F, ly * 2.2F, lz * 2.2F));
                    float t = clamp(bands + (fbm(lx * 5F, ly * 5F + 9F, lz * 5F) - 0.5F) * 0.5F, 0F, 1F);

                    colors[k] = new float[] {
                        Math.min(1F, (ca.r + (cb.r - ca.r) * t) * shade + atmosphere.r * rim),
                        Math.min(1F, (ca.g + (cb.g - ca.g) * t) * shade + atmosphere.g * rim),
                        Math.min(1F, (ca.b + (cb.b - ca.b) * t) * shade + atmosphere.b * rim)
                    };
                }
            }
        }

        for (int i = 0; i < rings; i++)
        {
            for (int j = 0; j < sectors; j++)
            {
                int k00 = i * (sectors + 1) + j;
                int k01 = k00 + 1;
                int k10 = k00 + sectors + 1;
                int k11 = k10 + 1;
                int[] order = {k00, k10, k11, k00, k11, k01};

                for (int k : order)
                {
                    if (textured)
                    {
                        builder.vertex(m, points[k][0], points[k][1], points[k][2]).texture(uvs[k][0], uvs[k][1]).color(colors[k][0], colors[k][1], colors[k][2], 1F)/*$ nx*/.next()/**/;
                    }
                    else
                    {
                        vertex(builder, m, points[k], colors[k], 1F);
                    }
                }
            }
        }

        Bufs.draw(builder);
    }

    /** A planet's atmosphere, a thin glowing shell, and its ring in the plane of its equator. Shared with the preview */
    public static void drawHaloAndRing(MatrixStack stack, FakeSpaceForm.Planet planet, float cx, float cy, float cz, float radius, Vector3f light)
    {
        Matrix4f m = stack.peek().getPositionMatrix();
        float halo = clamp(planet.atmosphere.get(), 0F, 1F);
        Matrix3f lean = orientation(planet, 0F, false);
        Vector3f n = new Vector3f();

        if (halo > 0.001F)
        {
            Color atmosphere = planet.atmosphereColor.get();
            int rings = 20;
            int sectors = 40;
            float[][] points = new float[(rings + 1) * (sectors + 1)][];
            float[][] colors = new float[(rings + 1) * (sectors + 1)][];
            float shell = radius * (1.04F + 0.08F * halo);

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            for (int i = 0; i <= rings; i++)
            {
                float lat = PI / 2F - PI * i / rings;

                for (int j = 0; j <= sectors; j++)
                {
                    float lon = 2F * PI * j / sectors;

                    n.set((float) (Math.cos(lat) * Math.cos(lon)), (float) Math.sin(lat), (float) (Math.cos(lat) * Math.sin(lon)));

                    float lit = 0.35F + 0.65F * smooth(-0.3F, 0.6F, n.dot(light));
                    int k = i * (sectors + 1) + j;

                    points[k] = new float[] {cx + n.x * shell, cy + n.y * shell, cz + n.z * shell};
                    colors[k] = new float[] {atmosphere.r * lit, atmosphere.g * lit, atmosphere.b * lit};
                }
            }

            grid(builder, m, points, colors, rings, sectors, 0.28F * halo);
            Bufs.draw(builder);
        }

        if (!planet.ring.get())
        {
            return;
        }

        Color color = planet.ringColor.get();
        Link texture = planet.ringTexture.get();
        float inner = radius * Math.max(1.01F, planet.ringInner.get());
        float outer = radius * Math.max(planet.ringInner.get() + 0.02F, planet.ringOuter.get());
        int segments = 128;
        int bands = 6;
        Vector3f u = lean.transform(new Vector3f(1F, 0F, 0F));
        Vector3f v = lean.transform(new Vector3f(0F, 0F, 1F));
        Vector3f up = lean.transform(new Vector3f(0F, 1F, 0F));
        /* The lit side of the ring is the one facing the sun */
        float lit = 0.45F + 0.55F * Math.abs(up.dot(light));
        BufferBuilder builder;

        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        }
        else
        {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        }

        for (int i = 0; i < segments; i++)
        {
            float a0 = 2F * PI * i / segments;
            float a1 = 2F * PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);

            for (int band = 0; band < bands; band++)
            {
                float t0 = band / (float) bands;
                float t1 = (band + 1) / (float) bands;
                float r0 = inner + (outer - inner) * t0;
                float r1 = inner + (outer - inner) * t1;
                /* Bands of different density, a gap two thirds of the way out */
                float alpha0 = texture != null ? 0.95F : ringDensity(t0);
                float alpha1 = texture != null ? 0.95F : ringDensity(t1);
                float[][] corners = {
                    {cx + (u.x * c0 + v.x * s0) * r0, cy + (u.y * c0 + v.y * s0) * r0, cz + (u.z * c0 + v.z * s0) * r0, t0, alpha0},
                    {cx + (u.x * c1 + v.x * s1) * r0, cy + (u.y * c1 + v.y * s1) * r0, cz + (u.z * c1 + v.z * s1) * r0, t0, alpha0},
                    {cx + (u.x * c1 + v.x * s1) * r1, cy + (u.y * c1 + v.y * s1) * r1, cz + (u.z * c1 + v.z * s1) * r1, t1, alpha1},
                    {cx + (u.x * c0 + v.x * s0) * r1, cy + (u.y * c0 + v.y * s0) * r1, cz + (u.z * c0 + v.z * s0) * r1, t1, alpha1}
                };
                int[] order = {0, 1, 2, 0, 2, 3};

                for (int k : order)
                {
                    float[] p = corners[k];

                    if (texture != null)
                    {
                        builder.vertex(m, p[0], p[1], p[2]).texture(p[3], 0.5F).color(color.r * lit, color.g * lit, color.b * lit, p[4])/*$ nx*/.next()/**/;
                    }
                    else
                    {
                        builder.vertex(m, p[0], p[1], p[2]).color(color.r * lit, color.g * lit, color.b * lit, p[4])/*$ nx*/.next()/**/;
                    }
                }
            }
        }

        Bufs.draw(builder);
    }

    private static float ringDensity(float t)
    {
        float density = 0.45F + 0.35F * (float) Math.sin(t * 19F) * (float) Math.sin(t * 7F + 1F);

        if (t > 0.62F && t < 0.7F)
        {
            density *= 0.15F;
        }

        return clamp(density, 0.05F, 0.9F);
    }

    /** How the planet's own frame sits in the scene: its spin about its axis (when asked) and the lean of that axis */
    private static Matrix3f orientation(FakeSpaceForm.Planet planet, float seconds, boolean spin)
    {
        Matrix3f matrix = new Matrix3f().rotateZ((float) Math.toRadians(planet.tilt.get()));

        if (spin)
        {
            matrix.rotateY((float) Math.toRadians(planet.rotationSpeed.get() * seconds));
        }

        return matrix;
    }

    /** The belt: low tumbling rocks in a flat ring round the form, each with its own shape, size and pace */
    private void asteroids(Matrix4f m, Vector3f light, float seconds)
    {
        FakeSpaceForm form = this.form;
        int count = Math.max(0, Math.min(300, form.asteroidCount.get()));

        if (count == 0)
        {
            return;
        }

        float belt = form.asteroidRadius.get();
        float speed = form.asteroidSpeed.get();
        float size = form.asteroidSize.get();
        Color base = form.asteroidColor.get();
        float[][] shape = new float[ICO_VERTICES.length][3];
        Matrix3f tumble = new Matrix3f();
        Vector3f p = new Vector3f();
        Vector3f e1 = new Vector3f();
        Vector3f e2 = new Vector3f();
        Vector3f normal = new Vector3f();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < count; i++)
        {
            float h0 = hash(i, 41, 0, 5);
            float h1 = hash(i, 41, 1, 5);
            float h2 = hash(i, 41, 2, 5);
            float h3 = hash(i, 41, 3, 5);
            float h4 = hash(i, 41, 4, 5);
            float orbit = belt * (0.75F + 0.5F * h1);
            float angle = h0 * 2F * PI + (float) Math.toRadians(speed * (0.4F + 0.6F * h3) * seconds) * (30F / Math.max(orbit, 1F));
            float cx = (float) Math.cos(angle) * orbit;
            float cz = (float) Math.sin(angle) * orbit;
            float cy = (h2 - 0.5F) * belt * 0.12F;
            float scale = size * (0.4F + 1.2F * h4 * h4);
            float spin = seconds * (0.3F + h3 * 1.5F) * (h0 > 0.5F ? 1F : -1F);
            float shade = 0.8F + 0.4F * h1;

            tumble.identity().rotateXYZ(h1 * 6F + spin, h2 * 6F + spin * 0.7F, h3 * 6F);

            for (int v = 0; v < ICO_VERTICES.length; v++)
            {
                float bump = 0.7F + 0.5F * hash(i, v, 7, 13);

                tumble.transform(p.set(ICO_VERTICES[v][0] * bump, ICO_VERTICES[v][1] * bump * (0.7F + 0.3F * h2), ICO_VERTICES[v][2] * bump));
                shape[v][0] = cx + p.x * scale;
                shape[v][1] = cy + p.y * scale;
                shape[v][2] = cz + p.z * scale;
            }

            for (int[] face : ICO_FACES)
            {
                float[] v0 = shape[face[0]];
                float[] v1 = shape[face[1]];
                float[] v2 = shape[face[2]];

                e1.set(v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2]);
                e2.set(v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]);
                e1.cross(e2, normal);

                if (normal.lengthSquared() < 1.0E-12F)
                {
                    continue;
                }

                normal.normalize();

                /* Faces are flat shaded: a rock reads as a rock by its facets */
                float l = 0.18F + 0.82F * Math.max(0F, Math.abs(normal.dot(light)) * (normal.dot(light) >= 0F ? 1F : 0.25F));
                float r = Math.min(1F, base.r * shade * l);
                float g = Math.min(1F, base.g * shade * l);
                float bl = Math.min(1F, base.b * shade * l);

                vertex(builder, m, v0[0], v0[1], v0[2], r, g, bl, 1F);
                vertex(builder, m, v1[0], v1[1], v1[2], r, g, bl, 1F);
                vertex(builder, m, v2[0], v2[1], v2[2], r, g, bl, 1F);
            }
        }

        Bufs.draw(builder);
    }

    /** The spiral galaxy: a disc of glowing points in space, turning slowly, added onto what is behind it */
    private void galaxy(Matrix4f m, Vector3f eye, float seconds)
    {
        FakeSpaceForm form = this.form;
        Color core = form.galaxyCoreColor.get();
        Color arm = form.galaxyArmColor.get();
        float brightness = clamp(form.galaxyBrightness.get(), 0F, 2F);
        float size = Math.max(form.galaxySize.get(), 1F);
        float rotation = (float) Math.toRadians(form.galaxyRotationSpeed.get() * seconds);
        Vector3f center = new Vector3f(form.galaxyX.get(), form.galaxyY.get(), form.galaxyZ.get());
        Matrix3f disc = new Matrix3f().rotateY((float) Math.toRadians(form.galaxyYaw.get())).rotateX((float) Math.toRadians(form.galaxyTilt.get()));
        Vector3f du = disc.transform(new Vector3f(1F, 0F, 0F));
        Vector3f dv = disc.transform(new Vector3f(0F, 0F, 1F));
        Vector3f p = new Vector3f();
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f();
        Vector3f view = new Vector3f();

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* The faint dust of the disc and the glow of the core */
        discFan(builder, m, center, du, dv, size, size, arm, 0.08F * brightness);
        discFan(builder, m, center, du, dv, size * 0.25F, size * 0.25F, core, 0.45F * brightness);

        int points = 1600;

        for (int i = 0; i < points; i++)
        {
            float t = hash(i, 5, 0, 23);
            float n = hash(i, 5, 1, 23);
            float s = hash(i, 5, 2, 23);
            float lift = hash(i, 5, 3, 23) - 0.5F;
            boolean inCore = i % 5 == 0;
            float rr = inCore ? size * 0.3F * t * t : size * (float) Math.pow(t, 0.75F);
            float theta = inCore ? n * 2F * PI : (rr / size) * 3.6F + (i % 2) * PI + (n - 0.5F) * (0.5F + 0.5F * (rr / size)) + rotation;
            float k = clamp(rr / size, 0F, 1F);
            float level = Math.min(1F, (0.18F + 0.55F * (1F - k)) * brightness);
            float half = size * (0.008F + 0.012F * s);
            float r = core.r + (arm.r - core.r) * k;
            float g = core.g + (arm.g - core.g) * k;
            float bl = core.b + (arm.b - core.b) * k;

            p.set(center).fma(rr * (float) Math.cos(theta), du).fma(rr * (float) Math.sin(theta), dv);
            /* A little thickness, more at the core */
            p.fma(lift * size * 0.04F * (1.5F - k), disc.transform(view.set(0F, 1F, 0F)));

            if (eye != null)
            {
                view.set(eye).sub(p);

                if (view.lengthSquared() < 1.0E-6F)
                {
                    continue;
                }

                view.normalize();
                tangents(view, right, up);
            }
            else
            {
                right.set(du);
                up.set(dv);
            }

            quad(builder, m,
                p.x - right.x * half - up.x * half, p.y - right.y * half - up.y * half, p.z - right.z * half - up.z * half,
                p.x + right.x * half - up.x * half, p.y + right.y * half - up.y * half, p.z + right.z * half - up.z * half,
                p.x + right.x * half + up.x * half, p.y + right.y * half + up.y * half, p.z + right.z * half + up.z * half,
                p.x - right.x * half + up.x * half, p.y - right.y * half + up.y * half, p.z - right.z * half + up.z * half,
                r, g, bl, level, level);
        }

        Bufs.draw(builder);
        RenderSystem.defaultBlendFunc();
    }

    private static void discFan(BufferBuilder builder, Matrix4f m, Vector3f center, Vector3f u, Vector3f v, float ru, float rv, Color color, float alpha)
    {
        int segments = 40;

        for (int i = 0; i < segments; i++)
        {
            float a0 = 2F * PI * i / segments;
            float a1 = 2F * PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0) * ru;
            float s0 = (float) Math.sin(a0) * rv;
            float c1 = (float) Math.cos(a1) * ru;
            float s1 = (float) Math.sin(a1) * rv;

            vertex(builder, m, center.x, center.y, center.z, color.r, color.g, color.b, alpha);
            vertex(builder, m, center.x + u.x * c0 + v.x * s0, center.y + u.y * c0 + v.y * s0, center.z + u.z * c0 + v.z * s0, color.r, color.g, color.b, 0F);
            vertex(builder, m, center.x + u.x * c1 + v.x * s1, center.y + u.y * c1 + v.y * s1, center.z + u.z * c1 + v.z * s1, color.r, color.g, color.b, 0F);
        }
    }

    /* Helpers */

    /** A direction from a yaw about the up axis (0 is the form's front, +Z) and a pitch above the horizon */
    public static Vector3f direction(float yaw, float pitch, Vector3f out)
    {
        float y = (float) Math.toRadians(yaw);
        float p = (float) Math.toRadians(clamp(pitch, -89.9F, 89.9F));

        return out.set((float) (Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
    }

    /** Two unit vectors across a direction */
    private static void tangents(Vector3f direction, Vector3f right, Vector3f up)
    {
        if (Math.abs(direction.y) > 0.99F)
        {
            right.set(1F, 0F, 0F);
        }
        else
        {
            right.set(0F, 1F, 0F).cross(direction).normalize();
        }

        up.set(direction).cross(right).normalize();
    }

    private static void vertex(BufferBuilder builder, Matrix4f m, float[] point, float[] color, float alpha)
    {
        builder.vertex(m, point[0], point[1], point[2]).color(color[0], color[1], color[2], alpha)/*$ nx*/.next()/**/;
    }

    private static void vertex(BufferBuilder builder, Matrix4f m, float x, float y, float z, float r, float g, float b, float alpha)
    {
        builder.vertex(m, x, y, z).color(r, g, b, alpha)/*$ nx*/.next()/**/;
    }

    private static void quad(BufferBuilder builder, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float fromAlpha, float toAlpha)
    {
        vertex(builder, m, x1, y1, z1, r, g, b, fromAlpha);
        vertex(builder, m, x2, y2, z2, r, g, b, fromAlpha);
        vertex(builder, m, x3, y3, z3, r, g, b, toAlpha);
        vertex(builder, m, x1, y1, z1, r, g, b, fromAlpha);
        vertex(builder, m, x3, y3, z3, r, g, b, toAlpha);
        vertex(builder, m, x4, y4, z4, r, g, b, toAlpha);
    }

    private static float clamp(float value, float min, float max)
    {
        return value < min ? min : (value > max ? max : value);
    }

    private static float smooth(float from, float to, float value)
    {
        float t = clamp((value - from) / (to - from), 0F, 1F);

        return t * t * (3F - 2F * t);
    }

    private static float hash(int x, int y, int z, int w)
    {
        int h = x * 374761393 + y * 668265263 + z * 1274126177 + w * 2147483647;

        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h & 0xffffff) / (float) 0x1000000;
    }

    private static float vnoise(float x, float y, float z)
    {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        float fx = x - ix;
        float fy = y - iy;
        float fz = z - iz;

        fx = fx * fx * (3F - 2F * fx);
        fy = fy * fy * (3F - 2F * fy);
        fz = fz * fz * (3F - 2F * fz);

        float x00 = lerp(hash(ix, iy, iz, 3), hash(ix + 1, iy, iz, 3), fx);
        float x10 = lerp(hash(ix, iy + 1, iz, 3), hash(ix + 1, iy + 1, iz, 3), fx);
        float x01 = lerp(hash(ix, iy, iz + 1, 3), hash(ix + 1, iy, iz + 1, 3), fx);
        float x11 = lerp(hash(ix, iy + 1, iz + 1, 3), hash(ix + 1, iy + 1, iz + 1, 3), fx);

        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static float fbm(float x, float y, float z)
    {
        return vnoise(x, y, z) * 0.55F + vnoise(x * 2.1F + 17F, y * 2.1F, z * 2.1F) * 0.3F + vnoise(x * 4.3F, y * 4.3F + 3F, z * 4.3F) * 0.15F;
    }

    private static float lerp(float a, float b, float t)
    {
        return a + (b - a) * t;
    }
}
