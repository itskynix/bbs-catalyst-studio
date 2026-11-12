package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.HeightFogForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.colors.Color;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.qualet.irl.light.VlGlobalsBuffer;
import org.qualet.irl.light.iris.IrisShadersState;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.client.forms.SpotGuideDrag;
import qualet.irlite.client.light.IRLightPositionResolver;

/**
 * Height fog. The fog itself is no geometry of ours: under a shader pack patched by IRLights, the form
 * hands its numbers to the volumetric globals buffer every frame it renders in the world, and the
 * pack's volumetric pass marches it through the scene - it dims what lies behind it and lights up with
 * its own colour, with or without any light around. Without shaders it draws nothing in the world.
 *
 * <p>Only the world's own pass hands the fog over. Everything else that draws forms with an entity's
 * render type - the shadow bake (with the light's matrices), framebuffer and anchor forms drawing
 * into buffers of their own, the scene through a portal, the picking pass - would put the fog
 * somewhere it is not, often right around the camera, and the last one to register before the upload
 * is the fog the whole frame gets.</p>
 *
 * <p>What is drawn is a guide: one wireframe box, the fog's volume, in the form editor and, in the
 * world, while the replay is selected in the film editor. As an item it is a small fog cube.</p>
 */
public class HeightFogFormRenderer extends FormRenderer<HeightFogForm>
{
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");

    public HeightFogFormRenderer(HeightFogForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Color c = this.form.color.get();

        context.batcher.gradientVBox(
            x1 + 4,
            y1 + 4,
            x2 - 4,
            y2 - 4,
            c.copy().set(c.r, c.g, c.b, 0.05F).getARGBColor(),
            c.copy().set(c.r, c.g, c.b, Math.min(1.0F, this.form.density.get())).getARGBColor()
        );

        context.batcher.outline(x1 + 4, y1 + 4, x2 - 4, y2 - 4, c.copy().set(c.r, c.g, c.b, 0.4F).getARGBColor());
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

        /* In a hand, on the ground or in a slot: a small fog cube, never the box (or the fog) around the holder */
        if (type == FormRenderType.ITEM_FP || type == FormRenderType.ITEM_TP || type == FormRenderType.ITEM || type == FormRenderType.ITEM_INVENTORY)
        {
            this.renderItemCube(context.stack);

            return;
        }

        boolean editor = context.ui || context.modelRenderer || type == FormRenderType.PREVIEW;

        if (editor)
        {
            this.renderGuide(context.stack);

            return;
        }

        if (type != FormRenderType.ENTITY && type != FormRenderType.MODEL_BLOCK)
        {
            return;
        }

        this.registerFog(context);

        if (SpotGuideDrag.isFilmSelected(this.form))
        {
            this.renderGuide(context.stack);
        }
    }

    /** Hands this frame's fog to the shader pack, in world coordinates. Only from the world's own pass. */
    private void registerFog(FormRenderingContext context)
    {
        /* isIrisWorldShadersEnabled: the world is being drawn and the pack is shading this very draw,
         * which it is not inside a framebuffer form's or anchor's own buffer */
        if (!IRIS_LOADED || !IrisShadersState.isIrisLoaded() || IrisShadersState.shadersDisabled() || !BBSRendering.isIrisWorldShadersEnabled())
        {
            return;
        }

        float density = this.form.density.get();

        if (density <= 0.001F)
        {
            return;
        }

        Color color = this.form.color.get();
        /* Where the form stands in the world, from the world matrix the context keeps alongside the
         * render one (entity position, then every transform and body part on the way here). Unlike
         * taking the render matrix back through the view, it does not depend on which camera or
         * view the frame is drawn with */
        Vector3d origin;

        if (context.world != null && context.entity != null)
        {
            org.joml.Vector3f t = context.world.peek().getPositionMatrix().getTranslation(new org.joml.Vector3f());

            origin = new Vector3d(t.x, t.y, t.z);
        }
        else
        {
            origin = IRLightPositionResolver.resolve(context);
        }
        double bottom = origin.y;
        double top = bottom + Math.max(0.5F, this.form.height.get());
        double centreX = origin.x;
        double centreZ = origin.z;

        if (this.form.followCamera.get())
        {
            /* The box keeps its size but slides with the camera across the ground, never up or down */
            net.minecraft.util.math.Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

            centreX = camera.x;
            centreZ = camera.z;
        }

        /* The wind runs on the world's clock: it moves with the game (and with a film's export, which
         * ticks the world) and stops when the game pauses. Not the actor's age - a model block's actor
         * is not aged by every path, and the fog stood still on one */
        MinecraftClient mc = MinecraftClient.getInstance();
        float time = mc.world == null ? 0F : (mc.world.getTime() % 72000L + context.getTransition()) / 20F;

        logSource(context.type, origin, bottom, top, centreX, centreZ);

        VlGlobalsBuffer.setHeightFog(
            color.r, color.g, color.b, density,
            bottom + this.form.baseHeight.get(), this.form.falloff.get(), bottom, top,
            centreX, centreZ,
            Math.max(0.5F, this.form.width.get()) / 2F, Math.max(0.5F, this.form.length.get()) / 2F,
            this.form.noiseIntensity.get(), this.form.noiseScale.get(), this.form.windSpeed.get(), time
        );
    }

    /* The last fog source logged: which pass, roughly where, and whether the camera was inside it */
    private static String lastLogged = "";

    /**
     * One line in the log whenever where the fog comes from or whether the camera stands inside it
     * changes: a fog that seems to cover the whole world is either the camera inside the box (which is
     * what a fog volume looks like from within) or a registration from somewhere it should not be.
     */
    private void logSource(FormRenderType type, Vector3d origin, double bottom, double top, double centreX, double centreZ)
    {
        net.minecraft.util.math.Vec3d eye = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        float hw = Math.max(0.5F, this.form.width.get()) / 2F;
        float hl = Math.max(0.5F, this.form.length.get()) / 2F;
        boolean inside = Math.abs(eye.x - centreX) <= hw && Math.abs(eye.z - centreZ) <= hl && eye.y >= bottom && eye.y <= top;
        String line = String.format("%s at %d %d %d, box %.0fx%.0fx%.0f, camera at %d %d %d %s, screen %s",
            type, Math.round(origin.x), Math.round(origin.y), Math.round(origin.z),
            hw * 2F, hl * 2F, top - bottom,
            Math.round(eye.x), Math.round(eye.y), Math.round(eye.z), inside ? "INSIDE the fog" : "outside the fog",
            MinecraftClient.getInstance().currentScreen == null ? "none" : MinecraftClient.getInstance().currentScreen.getClass().getSimpleName());

        if (!line.equals(lastLogged))
        {
            lastLogged = line;

            System.out.println("[BBS] Height fog: " + line);
        }
    }

    /** The fog's volume, one box: from [-width/2, 0, -length/2] to [width/2, height, length/2] */
    private void renderGuide(MatrixStack stack)
    {
        Color c = this.form.color.get();
        float hw = Math.max(0.5F, this.form.width.get()) / 2F;
        float hl = Math.max(0.5F, this.form.length.get()) / 2F;
        float top = Math.max(0.5F, this.form.height.get());

        this.renderBox(stack, -hw, 0F, -hl, hw, top, hl, -1F, c, 0F);
    }

    /** A block-sized fog cube for item views, translucent so a held one hides nothing */
    private void renderItemCube(MatrixStack stack)
    {
        this.renderBox(stack, -0.4F, 0F, -0.4F, 0.4F, 0.8F, 0.4F, 0.02F, this.form.color.get(), 0.35F);
    }

    private void renderBox(MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, Color c, float fillAlpha)
    {
        float[][] corners = {{x1, z1}, {x2, z1}, {x2, z2}, {x1, z2}};

        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (fillAlpha > 0F)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            BufferBuilder fill = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Matrix4f m = stack.peek().getPositionMatrix();

            /* The four sides, then the top and the bottom */
            for (int i = 0; i < 4; i++)
            {
                float[] a = corners[i];
                float[] b = corners[(i + 1) % 4];

                quad(fill, m, a[0], y1, a[1], b[0], y1, b[1], b[0], y2, b[1], a[0], y2, a[1], c, fillAlpha);
            }

            quad(fill, m, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, c, fillAlpha);
            quad(fill, m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c, fillAlpha);

            Bufs.draw(fill);

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 4; i++)
        {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];

            edge(builder, stack, a[0], y1, a[1], b[0], y1, b[1], thickness, c);
            edge(builder, stack, a[0], y2, a[1], b[0], y2, b[1], thickness, c);
            edge(builder, stack, a[0], y1, a[1], a[0], y2, a[1], thickness, c);
        }

        Bufs.draw(builder);

        if (fillAlpha > 0F)
        {
            RenderSystem.disableBlend();
        }
    }

    /**
     * One edge of the box. A negative thickness means "about the same on the screen at any distance":
     * a fixed 0.04 block edge of a 24 block box seen from 30 blocks off is a fraction of a pixel, and
     * draws as broken dashes - which is what made the one box look like two half boxes. The distance is
     * the edge's middle in view space (the stack holds the view, in the world and in the editor alike).
     */
    private static void edge(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, Color c)
    {
        if (thickness < 0F)
        {
            org.joml.Vector3f mid = stack.peek().getPositionMatrix().transformPosition(new org.joml.Vector3f((x1 + x2) / 2F, (y1 + y2) / 2F, (z1 + z2) / 2F));

            thickness = Math.max(0.03F, Math.min(1F, mid.length() * 0.004F));
        }

        /* Every edge of the box runs along an axis, so it is drawn as a thin axis-aligned cuboid,
         * a half thickness past both ends so the corners close. Not Draw.fillBoxTo: that turns its bar
         * by an angle worked out from the direction, and for the edges pointing back along an axis
         * the bar went off the other way - only two corners' worth of the box showed */
        float h = thickness / 2F;

        Draw.fillBox(builder, stack,
            Math.min(x1, x2) - h, Math.min(y1, y2) - h, Math.min(z1, z2) - h,
            Math.max(x1, x2) + h, Math.max(y1, y2) + h, Math.max(z1, z2) + h,
            c.r, c.g, c.b, 1F);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, Color c, float a)
    {
        b.vertex(m, x1, y1, z1).color(c.r, c.g, c.b, a)/*$ nx*/.next()/**/;
        b.vertex(m, x2, y2, z2).color(c.r, c.g, c.b, a)/*$ nx*/.next()/**/;
        b.vertex(m, x3, y3, z3).color(c.r, c.g, c.b, a)/*$ nx*/.next()/**/;
        b.vertex(m, x1, y1, z1).color(c.r, c.g, c.b, a)/*$ nx*/.next()/**/;
        b.vertex(m, x3, y3, z3).color(c.r, c.g, c.b, a)/*$ nx*/.next()/**/;
        b.vertex(m, x4, y4, z4).color(c.r, c.g, c.b, a)/*$ nx*/.next()/**/;
    }
}
