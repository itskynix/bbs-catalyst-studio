package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.PhysicsFieldForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.qualet.irl.light.shadow.ShadowBakeState;

/**
 * The physics field draws nothing of its own in a finished frame: the physics scene reads the form.
 * What is drawn is a guide - the sphere as three circles, the box as its edges, and a cross at the
 * centre - in the form editor, on a model block, and in the world while a BBS screen is open, unless
 * the HUD is hidden or a video is being recorded.
 */
public class PhysicsFieldFormRenderer extends FormRenderer<PhysicsFieldForm>
{
    private static final float R = 1F;
    private static final float G = 0.62F;
    private static final float B = 0.2F;

    public PhysicsFieldFormRenderer(PhysicsFieldForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int accent = 0xffff9e33;
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int r = Math.max(4, Math.min(x2 - x1, y2 - y1) / 3);

        context.batcher.gradientVBox(x1 + 4, y1 + 4, x2 - 4, y2 - 4, 0x10ff9e33, 0x50ff9e33);
        context.batcher.outline(cx - r, cy - r, cx + r, cy + r, accent);
        context.batcher.box(cx - 1, cy - r / 2, cx + 1, cy + r / 2, accent);
        context.batcher.box(cx - r / 2, cy - 1, cx + r / 2, cy + 1, accent);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (!this.form.showGuides.get() || context.isPicking() || BBSRendering.isIrisShadowPass() || ShadowBakeState.isBaking() || FormPortals.isRenderingPortal())
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

        this.renderGuide(context.stack);
    }

    private void renderGuide(MatrixStack stack)
    {
        PhysicsFieldForm form = this.form;

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        Draw.fillBox(builder, stack, -0.25F, -0.015F, -0.015F, 0.25F, 0.015F, 0.015F, R, G, B, 1F);
        Draw.fillBox(builder, stack, -0.015F, -0.25F, -0.015F, 0.015F, 0.25F, 0.015F, R, G, B, 1F);
        Draw.fillBox(builder, stack, -0.015F, -0.015F, -0.25F, 0.015F, 0.015F, 0.25F, R, G, B, 1F);

        int shape = form.shape.get();

        if (shape == PhysicsFieldForm.SHAPE_SPHERE)
        {
            float radius = Math.max(0.5F, form.radius.get());

            /* Three closed rings, one in each plane through the centre: a tube round the axis, so nothing sticks out of it */
            float thickness = Math.max(0.04F, radius * 0.006F);

            Draw.arc3D(builder, stack, Axis.X, radius, thickness, R, G, B, 0F, 360F, 96, 6);
            Draw.arc3D(builder, stack, Axis.Y, radius, thickness, R, G, B, 0F, 360F, 96, 6);
            Draw.arc3D(builder, stack, Axis.Z, radius, thickness, R, G, B, 0F, 360F, 96, 6);
        }
        else if (shape == PhysicsFieldForm.SHAPE_BOX)
        {
            float hx = Math.max(0.5F, form.width.get()) / 2F;
            float hy = Math.max(0.5F, form.height.get()) / 2F;
            float hz = Math.max(0.5F, form.length.get()) / 2F;
            float t = 0.03F;

            for (int sy = -1; sy <= 1; sy += 2)
            {
                for (int sz = -1; sz <= 1; sz += 2)
                {
                    Draw.fillBox(builder, stack, -hx, sy * hy - t, sz * hz - t, hx, sy * hy + t, sz * hz + t, R, G, B, 0.8F);
                }
            }

            for (int sx = -1; sx <= 1; sx += 2)
            {
                for (int sz = -1; sz <= 1; sz += 2)
                {
                    Draw.fillBox(builder, stack, sx * hx - t, -hy, sz * hz - t, sx * hx + t, hy, sz * hz + t, R, G, B, 0.8F);
                }
            }

            for (int sx = -1; sx <= 1; sx += 2)
            {
                for (int sy = -1; sy <= 1; sy += 2)
                {
                    Draw.fillBox(builder, stack, sx * hx - t, sy * hy - t, -hz, sx * hx + t, sy * hy + t, hz, R, G, B, 0.8F);
                }
            }
        }

        Bufs.draw(builder);
        RenderSystem.disableBlend();
    }
}
