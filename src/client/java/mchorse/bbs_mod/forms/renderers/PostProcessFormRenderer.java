package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.fx.FxOverlay;
import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.camera.clips.fx.PostEffectStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.PostProcessForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.ShadowBakeState;
import qualet.irlite.client.light.IRLightPositionResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The post-process volume. It draws no geometry of its own in the world: every frame the world's
 * own pass draws it, it works out how much the camera is inside its box - fully inside, fading out
 * over the blend radius, or not at all - and queues its effect stack scaled by that. Once the world
 * is finished the queue is drawn over it in priority order through the post-process library
 * ({@code postfx_lib}), so a frame with no volume around the camera costs nothing.
 *
 * <p>Only the world's own pass queues: the UI preview, the Iris shadow pass, the shadow bake, the
 * picking pass and the scene seen through a portal all draw forms too, and a volume queued from any
 * of those would be in the wrong place.</p>
 *
 * <p>What is drawn is a guide: the box as a solid wireframe and, with a blend radius, the outer edge
 * of the fade as a faint dashed one - in the form editor, on a model block, and in the world while a
 * BBS screen is open, unless the HUD is hidden or a video is being recorded.</p>
 */
public class PostProcessFormRenderer extends FormRenderer<PostProcessForm>
{
    /** One volume's contribution to this frame */
    private record Entry(PostProcessFormRenderer renderer, int priority, List<FxOverlay> overlays) {}

    private static final List<Entry> QUEUE = new ArrayList<>();
    private static final List<FxOverlay> FLAT = new ArrayList<>();

    private static final float GUIDE_R = 0.35F;
    private static final float GUIDE_G = 0.85F;
    private static final float GUIDE_B = 1F;

    public PostProcessFormRenderer(PostProcessForm form)
    {
        super(form);
    }

    /** At the start of a frame: what a frame that never got to its end queued is not carried over. */
    public static void clearQueue()
    {
        QUEUE.clear();
    }

    /** Whether any queued volume asks for the world's depth, so it is kept before the hand is drawn */
    public static boolean wantsDepth()
    {
        for (Entry entry : QUEUE)
        {
            for (FxOverlay overlay : entry.overlays)
            {
                if (overlay.params[10] > 0.5F)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** Once the world is finished: draws every queued volume's stack, lowest priority first, and empties the queue */
    public static void flush()
    {
        if (QUEUE.isEmpty())
        {
            return;
        }

        QUEUE.sort(Comparator.comparingInt(Entry::priority));
        FLAT.clear();

        for (Entry entry : QUEUE)
        {
            FLAT.addAll(entry.overlays);
        }

        QUEUE.clear();
        mchorse.bbs_mod.ui.film.PostFxRenderer.renderVolumes(FLAT);
        FLAT.clear();
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int accent = 0xff59d9ff;

        context.batcher.gradientVBox(x1 + 4, y1 + 4, x2 - 4, y2 - 4, 0x1059d9ff, 0x6059d9ff);
        context.batcher.outline(x1 + 4, y1 + 4, x2 - 4, y2 - 4, accent);
        context.batcher.outline(x1 + 8, y1 + 8, x2 - 8, y2 - 8, 0x5059d9ff);
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

        if (type == FormRenderType.ITEM_FP || type == FormRenderType.ITEM_TP || type == FormRenderType.ITEM || type == FormRenderType.ITEM_INVENTORY)
        {
            this.renderBox(context.stack, -0.4F, 0F, -0.4F, 0.4F, 0.8F, 0.4F, 0.02F, 1F, false);

            return;
        }

        boolean editor = context.ui || context.modelRenderer || type == FormRenderType.PREVIEW;

        if (editor)
        {
            if (this.form.showGuides.get())
            {
                this.renderGuide(context.stack);
            }

            return;
        }

        if (type != FormRenderType.ENTITY && type != FormRenderType.MODEL_BLOCK)
        {
            return;
        }

        this.queue(context);

        if (this.form.showGuides.get() && this.guidesInWorld(type))
        {
            this.renderGuide(context.stack);
        }
    }

    /** The guide in the world: on a model block or while a BBS screen is open, never in a clean frame */
    private boolean guidesInWorld(FormRenderType type)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.options.hudHidden || BBSModClient.getVideoRecorder().isRecording())
        {
            return false;
        }

        return type == FormRenderType.MODEL_BLOCK || mc.currentScreen instanceof UIScreen;
    }

    /** Works out the camera's weight and queues this volume's effects scaled by it */
    private void queue(FormRenderingContext context)
    {
        float weight = this.weight(context) * this.form.intensity.get();

        if (weight <= 0.001F || this.form.stack.getAllTyped().isEmpty())
        {
            return;
        }

        /* The same volume drawn twice in one frame counts once */
        for (Entry entry : QUEUE)
        {
            if (entry.renderer == this)
            {
                return;
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float seconds = mc.world == null ? 0F : (mc.world.getTime() % 72000L + context.getTransition()) / 20F;
        List<FxOverlay> overlays = new ArrayList<>();

        for (PostEffectStack.Layer layer : this.form.stack.getAllTyped())
        {
            int slot = layer.slot.get();
            PostEffect effect = PostEffect.get(layer.effect.get());

            if (!layer.enabled.get() || effect == null || slot < 0 || slot >= PostProcessForm.SLOTS)
            {
                continue;
            }

            double intensity = Math.max(0D, Math.min(1D, this.form.slotIntensity[slot].get())) * weight;

            if (intensity <= 0.001D)
            {
                continue;
            }

            FxOverlay overlay = new FxOverlay(FxOverlay.LIBRARY);

            overlay.set(0, effect.mode).set(1, intensity);

            for (int p = 0; p < PostEffectStack.PARAMS; p++)
            {
                overlay.set(2 + p, p < effect.params.length ? this.form.slotParams[slot][p].get() : 0D);
            }

            overlay.rgb(6, effect.hasColor() ? layer.color.get() : 0xffffff);
            overlay.set(9, seconds);
            overlay.set(10, effect.depth ? 1D : 0D);
            overlays.add(overlay);
        }

        if (!overlays.isEmpty())
        {
            QUEUE.add(new Entry(this, this.form.priority.get(), overlays));
        }
    }

    /**
     * How much of the volume the camera gets, 0 to 1: everywhere for an unbound one, 1 inside the box,
     * and outside it a smoothstep from 1 at the box to 0 at the blend radius. The box is the form's own,
     * so it turns and scales with the form; the distance is measured in the world, in blocks, from the
     * camera to the nearest point of the box.
     */
    private float weight(FormRenderingContext context)
    {
        if (this.form.unbound.get())
        {
            return 1F;
        }

        Matrix4f world;

        if (context.world != null && context.entity != null)
        {
            world = new Matrix4f(context.world.peek().getPositionMatrix());
        }
        else
        {
            Vector3d origin = IRLightPositionResolver.resolve(context);

            world = new Matrix4f().translation((float) origin.x, (float) origin.y, (float) origin.z);
        }

        Vec3d eye = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Matrix4f inverse = new Matrix4f(world);

        if (Math.abs(inverse.determinant()) < 1.0E-9F)
        {
            return 0F;
        }

        inverse.invert();

        Vector3f local = inverse.transformPosition(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z));
        float hw = Math.max(0.5F, this.form.width.get()) / 2F;
        float hl = Math.max(0.5F, this.form.length.get()) / 2F;
        float top = Math.max(0.5F, this.form.height.get());

        Vector3f nearest = new Vector3f(
            Math.max(-hw, Math.min(hw, local.x)),
            Math.max(0F, Math.min(top, local.y)),
            Math.max(-hl, Math.min(hl, local.z))
        );

        if (nearest.equals(local, 1.0E-5F))
        {
            return 1F;
        }

        float blend = this.form.blendRadius.get();

        if (blend <= 0F)
        {
            return 0F;
        }

        /* Back into the world, so the distance is in blocks whatever the form's scale */
        Vector3f nearestWorld = world.transformPosition(nearest);
        double distance = Math.sqrt(nearestWorld.distanceSquared((float) eye.x, (float) eye.y, (float) eye.z));

        if (distance >= blend)
        {
            return 0F;
        }

        float t = 1F - (float) (distance / blend);

        return t * t * (3F - 2F * t);
    }

    /** The box as a solid wireframe, and the blend radius as a faint dashed one around it */
    private void renderGuide(MatrixStack stack)
    {
        float hw = Math.max(0.5F, this.form.width.get()) / 2F;
        float hl = Math.max(0.5F, this.form.length.get()) / 2F;
        float top = Math.max(0.5F, this.form.height.get());
        float blend = this.form.blendRadius.get();

        this.renderBox(stack, -hw, 0F, -hl, hw, top, hl, -1F, 1F, false);

        if (blend > 0F && !this.form.unbound.get())
        {
            this.renderBox(stack, -hw - blend, -blend, -hl - blend, hw + blend, top + blend, hl + blend, -0.6F, 0.35F, true);
        }
    }

    private void renderBox(MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float alpha, boolean dashed)
    {
        float[][] corners = {{x1, z1}, {x2, z1}, {x2, z2}, {x1, z2}};

        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (alpha < 1F)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 4; i++)
        {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];

            edge(builder, stack, a[0], y1, a[1], b[0], y1, b[1], thickness, alpha, dashed);
            edge(builder, stack, a[0], y2, a[1], b[0], y2, b[1], thickness, alpha, dashed);
            edge(builder, stack, a[0], y1, a[1], a[0], y2, a[1], thickness, alpha, dashed);
        }

        Bufs.draw(builder);

        if (alpha < 1F)
        {
            RenderSystem.disableBlend();
        }
    }

    /**
     * One edge, as a thin axis-aligned cuboid. A negative thickness scales with the distance in view
     * space, so the edge stays about the same on the screen; dashed edges are one block on, one off.
     */
    private static void edge(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float alpha, boolean dashed)
    {
        if (thickness < 0F)
        {
            Vector3f mid = stack.peek().getPositionMatrix().transformPosition(new Vector3f((x1 + x2) / 2F, (y1 + y2) / 2F, (z1 + z2) / 2F));

            thickness = Math.max(0.03F, Math.min(1F, mid.length() * 0.004F)) * -thickness;
        }

        float length = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        int pieces = dashed ? Math.max(1, Math.min(256, (int) Math.ceil(length))) : 1;
        float h = thickness / 2F;

        for (int i = 0; i < pieces; i++)
        {
            float from = i / (float) pieces;
            float to = dashed ? (i + 0.5F) / pieces : 1F;

            float ax = x1 + (x2 - x1) * from, ay = y1 + (y2 - y1) * from, az = z1 + (z2 - z1) * from;
            float bx = x1 + (x2 - x1) * to, by = y1 + (y2 - y1) * to, bz = z1 + (z2 - z1) * to;

            Draw.fillBox(builder, stack,
                Math.min(ax, bx) - h, Math.min(ay, by) - h, Math.min(az, bz) - h,
                Math.max(ax, bx) + h, Math.max(ay, by) + h, Math.max(az, bz) + h,
                GUIDE_R, GUIDE_G, GUIDE_B, alpha);
        }
    }
}
