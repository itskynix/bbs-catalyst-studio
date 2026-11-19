package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.FakeSpaceForm;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.client.light.IRLightPositionResolver;

/**
 * The artificial space scene. Nothing here is in the world: the sky is a dome, a star field, a planet
 * and a galaxy drawn as plain coloured triangles around the camera, at a distance no block could
 * reach, with the depth write off - so it sits behind everything the world draws, costs no blocks and
 * has no collision.
 *
 * <p>The dome is centred on the camera, not on the form: moving the camera moves nothing on it, which
 * is what makes it read as infinitely far. Turning the form turns the sky. The stars, the nebula and
 * the layers keep their places on it; sizes are angles across the sky, so they hold at any view
 * distance. The streak factor draws each star as a streak of light along the line away from the form's
 * front, the Big Rip stretch.</p>
 */
public class FakeSpaceFormRenderer extends FormRenderer<FakeSpaceForm>
{
    private static final float PI = (float) Math.PI;

    /** The form's front, which the stars streak away from */
    private static final Vector3f FRONT = new Vector3f(0F, 0F, 1F);

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
        Vector3f eye = new Vector3f();
        float radius;

        if (editor)
        {
            radius = 40F;
        }
        else
        {
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

            Vec3d camera = mc.gameRenderer.getCamera().getPos();
            float scale = Math.max(world.getColumn(0, new Vector3f()).length(), 1.0E-4F);

            world.invert().transformPosition(eye.set((float) camera.x, (float) camera.y, (float) camera.z));

            /* Just inside the far plane, in the form's own units */
            radius = Math.max(32F, Math.min(mc.gameRenderer.getViewDistance() * 0.9F, 1500F)) / scale;
        }

        float seconds = mc.world == null ? 0F : (mc.world.getTime() % 72000L + context.getTransition()) / 20F;

        this.draw(context.stack, eye, radius, seconds);
    }

    /* The sky itself */

    private void draw(MatrixStack stack, Vector3f eye, float radius, float seconds)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        FakeSpaceForm form = this.form;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        try
        {
            if (form.skyDome.get())
            {
                this.dome(matrix, eye, radius);
            }

            this.stars(matrix, eye, radius);

            if (form.galaxyEnabled.get())
            {
                this.galaxy(matrix, eye, radius, seconds);
            }

            if (form.planetEnabled.get())
            {
                this.planet(matrix, eye, radius, seconds);
            }
        }
        finally
        {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
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

        for (int i = 0; i < rings; i++)
        {
            for (int j = 0; j < sectors; j++)
            {
                int k00 = i * (sectors + 1) + j;
                int k01 = k00 + 1;
                int k10 = k00 + sectors + 1;
                int k11 = k10 + 1;

                vertex(builder, m, points[k00], colors[k00], 1F);
                vertex(builder, m, points[k10], colors[k10], 1F);
                vertex(builder, m, points[k11], colors[k11], 1F);
                vertex(builder, m, points[k00], colors[k00], 1F);
                vertex(builder, m, points[k11], colors[k11], 1F);
                vertex(builder, m, points[k01], colors[k01], 1F);
            }
        }

        Bufs.draw(builder);
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

    /** One star at the direction in {@link #a}: a dot, or a streak with its tail fading away */
    private void star(BufferBuilder builder, Matrix4f m, Vector3f eye, float radius, float width, float streak, float variety, float level, float r, float g, float bl)
    {
        float along = this.a.dot(FRONT);
        float sine = (float) Math.sqrt(Math.max(0F, 1F - along * along));
        float length = streak * (0.03F + 0.55F * sine) * (0.4F + 0.6F * variety);

        this.b.set(this.a).fma(-along, FRONT);

        if (length < 0.002F || this.b.lengthSquared() < 1.0E-8F)
        {
            /* A dot: a small square across the sky at the star's place */
            this.tangents(this.a, this.b, this.c);

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

        /* A streak: the head where the star is, the tail turned back towards the front by the length */
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

    /** The spiral galaxy: a faint disc, a glowing core and two arms of points, turning slowly */
    private void galaxy(Matrix4f m, Vector3f eye, float radius, float seconds)
    {
        FakeSpaceForm form = this.form;
        Color core = form.galaxyCoreColor.get();
        Color arm = form.galaxyArmColor.get();
        float brightness = clamp(form.galaxyBrightness.get(), 0F, 2F);
        float rho = (float) Math.toRadians(Math.max(form.galaxySize.get(), 1F) / 2F);
        float tilt = (float) Math.toRadians(clamp(form.galaxyTilt.get(), -90F, 90F));
        float squash = Math.max(0.08F, (float) Math.abs(Math.cos(tilt)));
        float rotation = (float) Math.toRadians(form.galaxyRotationSpeed.get() * seconds);
        Vector3f center = direction(form.galaxyYaw.get(), form.galaxyPitch.get(), new Vector3f());
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f();

        this.tangents(center, right, up);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* The dust of the disc and the glow of the core, as soft fans */
        this.fan(builder, m, eye, radius, center, right, up, rho, rho * squash, arm, 0.12F * brightness, 0F);
        this.fan(builder, m, eye, radius, center, right, up, rho * 0.28F, rho * 0.28F * Math.max(squash, 0.5F), core, 0.55F * brightness, 0F);

        int points = 1500;

        for (int i = 0; i < points; i++)
        {
            float t = hash(i, 5, 0, 23);
            float n = hash(i, 5, 1, 23);
            float s = hash(i, 5, 2, 23);
            boolean inCore = i % 5 == 0;
            float rr = inCore ? rho * 0.3F * t * t : rho * (float) Math.pow(t, 0.75F);
            float theta = inCore ? n * 2F * PI : (rr / rho) * 3.6F + (i % 2) * PI + (n - 0.5F) * (0.5F + 0.5F * (rr / rho)) + rotation;
            float px = rr * (float) Math.cos(theta);
            float py = rr * (float) Math.sin(theta) * squash;
            float k = clamp(rr / rho, 0F, 1F);
            float level = Math.min(1F, (0.18F + 0.55F * (1F - k)) * brightness);
            float half = rho * (0.008F + 0.012F * s);
            float r = core.r + (arm.r - core.r) * k;
            float g = core.g + (arm.g - core.g) * k;
            float bl = core.b + (arm.b - core.b) * k;

            this.skyPoint(center, right, up, px, py, eye, radius, this.a);
            this.skyPoint(center, right, up, px + half, py, eye, radius, this.b);
            this.skyPoint(center, right, up, px, py + half, eye, radius, this.c);
            this.skyPoint(center, right, up, px - half, py - half, eye, radius, this.d);

            vertex(builder, m, this.b, r, g, bl, level);
            vertex(builder, m, this.c, r, g, bl, level);
            vertex(builder, m, this.d, r, g, bl, level);
        }

        Bufs.draw(builder);
    }

    /** A point on the sky at an offset (in radians) from a direction along its two tangents, put on the dome */
    private void skyPoint(Vector3f center, Vector3f right, Vector3f up, float x, float y, Vector3f eye, float radius, Vector3f out)
    {
        out.set(center).fma(x, right).fma(y, up).normalize().mul(radius).add(eye);
    }

    /** A soft disc across the sky: full at the middle, nothing at the edge; the sizes are offsets in radians */
    private void fan(BufferBuilder builder, Matrix4f m, Vector3f eye, float radius, Vector3f center, Vector3f right, Vector3f up, float rx, float ry, Color color, float centerAlpha, float edgeAlpha)
    {
        int segments = 28;
        Vector3f mid = new Vector3f();
        Vector3f p = new Vector3f();
        Vector3f q = new Vector3f();

        this.skyPoint(center, right, up, 0F, 0F, eye, radius, mid);

        for (int i = 0; i < segments; i++)
        {
            float a0 = 2F * PI * i / segments;
            float a1 = 2F * PI * (i + 1) / segments;

            this.skyPoint(center, right, up, rx * (float) Math.cos(a0), ry * (float) Math.sin(a0), eye, radius, p);
            this.skyPoint(center, right, up, rx * (float) Math.cos(a1), ry * (float) Math.sin(a1), eye, radius, q);

            vertex(builder, m, mid, color.r, color.g, color.b, centerAlpha);
            vertex(builder, m, p, color.r, color.g, color.b, edgeAlpha);
            vertex(builder, m, q, color.r, color.g, color.b, edgeAlpha);
        }
    }

    /** The planet: its halo, the back of the ring, the lit and turning globe, then the front of the ring */
    private void planet(Matrix4f m, Vector3f eye, float radius, float seconds)
    {
        FakeSpaceForm form = this.form;
        Color ca = form.planetColorA.get();
        Color cb = form.planetColorB.get();
        Color atmosphere = form.planetAtmosphereColor.get();
        float halo = clamp(form.planetAtmosphere.get(), 0F, 1F);
        float rho = (float) Math.toRadians(clamp(form.planetSize.get(), 1F, 90F) / 2F);
        float spin = (float) Math.toRadians(form.planetRotationSpeed.get() * seconds);
        Vector3f direction = direction(form.planetYaw.get(), form.planetPitch.get(), new Vector3f());
        Vector3f light = direction(form.planetLightYaw.get(), 12F, new Vector3f());
        float body = radius * (float) Math.sin(rho);
        Vector3f center = new Vector3f(direction).mul(radius * (float) Math.cos(rho)).add(eye);
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f();

        this.tangents(direction, right, up);

        float centerDepth = new Vector3f(center).sub(eye).dot(direction);

        /* The halo, behind the globe */
        if (halo > 0.001F)
        {
            BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            int segments = 48;

            for (int i = 0; i < segments; i++)
            {
                float a0 = 2F * PI * i / segments;
                float a1 = 2F * PI * (i + 1) / segments;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                float inner = body * 0.97F;
                float outer = body * (1.1F + 0.35F * halo);

                float level = 0.55F * halo;

                quad(builder, m,
                    center.x + (right.x * c0 + up.x * s0) * inner, center.y + (right.y * c0 + up.y * s0) * inner, center.z + (right.z * c0 + up.z * s0) * inner,
                    center.x + (right.x * c1 + up.x * s1) * inner, center.y + (right.y * c1 + up.y * s1) * inner, center.z + (right.z * c1 + up.z * s1) * inner,
                    center.x + (right.x * c1 + up.x * s1) * outer, center.y + (right.y * c1 + up.y * s1) * outer, center.z + (right.z * c1 + up.z * s1) * outer,
                    center.x + (right.x * c0 + up.x * s0) * outer, center.y + (right.y * c0 + up.y * s0) * outer, center.z + (right.z * c0 + up.z * s0) * outer,
                    atmosphere.r, atmosphere.g, atmosphere.b, level, 0F);
            }

            Bufs.draw(builder);
        }

        boolean ring = form.planetRing.get();
        Vector3f axis = new Vector3f();

        if (ring)
        {
            /* The ring's plane is the planet's equator turned about the sky's right by the tilt */
            float tilt = (float) Math.toRadians(clamp(form.planetRingTilt.get(), -90F, 90F));

            axis.set(up).mul((float) Math.cos(tilt)).fma((float) Math.sin(tilt), direction);

            this.ring(m, center, centerDepth, direction, right, axis, body, form.planetRingColor.get(), eye, true);
        }

        this.globe(m, center, body, eye, ca, cb, atmosphere, halo, light, spin);

        if (ring)
        {
            this.ring(m, center, centerDepth, direction, right, axis, body, form.planetRingColor.get(), eye, false);
        }
    }

    /** The globe: bands and weather turning with the spin, lambert light from the light direction, a rim of atmosphere */
    private void globe(Matrix4f m, Vector3f center, float body, Vector3f eye, Color ca, Color cb, Color atmosphere, float halo, Vector3f light, float spin)
    {
        int rings = 22;
        int sectors = 44;
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float[][] colors = new float[(rings + 1) * (sectors + 1)][];
        float[][] points = new float[(rings + 1) * (sectors + 1)][];
        float cosSpin = (float) Math.cos(spin);
        float sinSpin = (float) Math.sin(spin);
        Vector3f toEye = new Vector3f();

        for (int i = 0; i <= rings; i++)
        {
            float lat = PI * i / rings - PI / 2F;

            for (int j = 0; j <= sectors; j++)
            {
                float lon = 2F * PI * j / sectors;
                float nx = (float) (Math.cos(lat) * Math.cos(lon));
                float ny = (float) Math.sin(lat);
                float nz = (float) (Math.cos(lat) * Math.sin(lon));

                /* The surface turns under the light: the pattern is read from the normal spun about the planet's axis */
                float sx = nx * cosSpin - nz * sinSpin;
                float sz = nx * sinSpin + nz * cosSpin;
                float bands = 0.5F + 0.5F * (float) Math.sin(ny * 8F + 3F * fbm(sx * 2.2F + 4F, ny * 2.2F, sz * 2.2F));
                float t = clamp(bands + (fbm(sx * 5F, ny * 5F + 9F, sz * 5F) - 0.5F) * 0.5F, 0F, 1F);
                float lit = smooth(-0.1F, 0.65F, nx * light.x + ny * light.y + nz * light.z);
                float shade = 0.035F + 0.965F * lit;
                int k = i * (sectors + 1) + j;
                float x = center.x + nx * body;
                float y = center.y + ny * body;
                float z = center.z + nz * body;

                toEye.set(eye.x - x, eye.y - y, eye.z - z).normalize();

                float rim = (float) Math.pow(1F - Math.max(0F, nx * toEye.x + ny * toEye.y + nz * toEye.z), 2.6F) * halo * (0.25F + 0.75F * lit);

                colors[k] = new float[] {
                    Math.min(1F, (ca.r + (cb.r - ca.r) * t) * shade + atmosphere.r * rim),
                    Math.min(1F, (ca.g + (cb.g - ca.g) * t) * shade + atmosphere.g * rim),
                    Math.min(1F, (ca.b + (cb.b - ca.b) * t) * shade + atmosphere.b * rim)
                };
                points[k] = new float[] {x, y, z};
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

                vertex(builder, m, points[k00], colors[k00], 1F);
                vertex(builder, m, points[k10], colors[k10], 1F);
                vertex(builder, m, points[k11], colors[k11], 1F);
                vertex(builder, m, points[k00], colors[k00], 1F);
                vertex(builder, m, points[k11], colors[k11], 1F);
                vertex(builder, m, points[k01], colors[k01], 1F);
            }
        }

        Bufs.draw(builder);
    }

    /**
     * Half of the ring: the part behind the globe (farther from the camera than its centre) or the
     * part in front of it, so the globe hides the one and not the other. Bands of different strength
     * across its width, a gap between the inner and outer parts.
     */
    private void ring(Matrix4f m, Vector3f center, float centerDepth, Vector3f direction, Vector3f right, Vector3f axis, float body, Color color, Vector3f eye, boolean back)
    {
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Vector3f u = new Vector3f(right);
        Vector3f v = new Vector3f(axis).cross(u);
        int segments = 72;
        float[] edges = {1.3F, 1.55F, 1.62F, 1.95F, 2.05F, 2.3F};
        float[] levels = {0.55F, 0.8F, 0.0F, 0.65F, 0.0F, 0.4F};
        Vector3f mid = new Vector3f();

        if (v.lengthSquared() < 1.0E-8F)
        {
            return;
        }

        v.normalize();

        for (int i = 0; i < segments; i++)
        {
            float a0 = 2F * PI * i / segments;
            float a1 = 2F * PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float cm = (float) Math.cos((a0 + a1) / 2F);
            float sm = (float) Math.sin((a0 + a1) / 2F);

            mid.set(center).fma(cm * body * 1.8F, u).fma(sm * body * 1.8F, v);

            float depth = new Vector3f(mid).sub(eye).dot(direction);

            if ((depth > centerDepth) != back)
            {
                continue;
            }

            for (int e = 0; e + 1 < edges.length; e += 2)
            {
                float in = body * edges[e];
                float out = body * edges[e + 1];
                float level = levels[e] * 0.85F;

                quad(builder, m,
                    center.x + (u.x * c0 + v.x * s0) * in, center.y + (u.y * c0 + v.y * s0) * in, center.z + (u.z * c0 + v.z * s0) * in,
                    center.x + (u.x * c1 + v.x * s1) * in, center.y + (u.y * c1 + v.y * s1) * in, center.z + (u.z * c1 + v.z * s1) * in,
                    center.x + (u.x * c1 + v.x * s1) * out, center.y + (u.y * c1 + v.y * s1) * out, center.z + (u.z * c1 + v.z * s1) * out,
                    center.x + (u.x * c0 + v.x * s0) * out, center.y + (u.y * c0 + v.y * s0) * out, center.z + (u.z * c0 + v.z * s0) * out,
                    color.r, color.g, color.b, level, levels[e + 1] > 0F ? levels[e + 1] * 0.85F : level * 0.6F);
            }
        }

        Bufs.draw(builder);
    }

    /* Helpers */

    /** A direction from a yaw about the up axis (0 is the form's front, +Z) and a pitch above the horizon */
    private static Vector3f direction(float yaw, float pitch, Vector3f out)
    {
        float y = (float) Math.toRadians(yaw);
        float p = (float) Math.toRadians(clamp(pitch, -89.9F, 89.9F));

        return out.set((float) (Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
    }

    /** Two unit vectors across the sky at a direction, right and up as seen from the camera at the middle */
    private void tangents(Vector3f direction, Vector3f right, Vector3f up)
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

    private static void vertex(BufferBuilder builder, Matrix4f m, Vector3f point, float r, float g, float b, float alpha)
    {
        builder.vertex(m, point.x, point.y, point.z).color(r, g, b, alpha)/*$ nx*/.next()/**/;
    }

    private static void vertex(BufferBuilder builder, Matrix4f m, float x, float y, float z, float r, float g, float b, float alpha)
    {
        builder.vertex(m, x, y, z).color(r, g, b, alpha)/*$ nx*/.next()/**/;
    }

    /** A quad of corners 1 to 4 in order, the first two (the "from" side) at one alpha and the last two at the other */
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

    /** A hash of four integers into 0 to 1, the same everywhere */
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
