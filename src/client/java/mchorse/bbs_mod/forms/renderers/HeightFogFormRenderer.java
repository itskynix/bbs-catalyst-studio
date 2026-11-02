package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.HeightFogForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class HeightFogFormRenderer extends FormRenderer<HeightFogForm>
{
    public HeightFogFormRenderer(HeightFogForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Color c = this.form.color.get();
        int w = x2 - x1;
        int h = y2 - y1;

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

        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        float densityVal = this.form.density.get();

        if (densityVal <= 0.001F)
        {
            return;
        }

        Color colorVal = this.form.color.get();
        float r = colorVal.r;
        float g = colorVal.g;
        float b = colorVal.b;

        float baseH = this.form.baseHeight.get();
        float maxH = Math.max(0.5F, this.form.height.get());
        float falloffVal = Math.max(0.1F, this.form.falloff.get());
        float widthVal = Math.max(0.5F, this.form.width.get());
        float lengthVal = Math.max(0.5F, this.form.length.get());
        int sliceCount = MathUtils.clamp(this.form.slices.get(), 4, 64);
        boolean followCam = this.form.followCamera.get();

        MatrixStack stack = context.stack;

        stack.push();

        if (followCam && context.camera != null)
        {
            /* Align with camera horizontally if requested */
            stack.translate(0F, 0F, 0F);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Matrix4f m = stack.peek().getPositionMatrix();

        float halfW = widthVal * 0.5F;
        float halfL = lengthVal * 0.5F;

        /* 1. Horizontal exponential decay slice layers */
        for (int i = 0; i < sliceCount; i++)
        {
            float fraction = (float) i / (float) (sliceCount - 1);
            float y = baseH + fraction * maxH;
            float relH = y - baseH;

            float decay = (float) Math.exp(-Math.max(0F, relH) / falloffVal);
            float sliceAlpha = densityVal * decay * (2.0F / (float) sliceCount) * 3.5F;
            sliceAlpha = MathUtils.clamp(sliceAlpha, 0F, 1F);

            if (sliceAlpha > 0.002F)
            {
                builder.vertex(m, -halfW, y, -halfL).color(r, g, b, sliceAlpha);
                builder.vertex(m, -halfW, y,  halfL).color(r, g, b, sliceAlpha);
                builder.vertex(m,  halfW, y,  halfL).color(r, g, b, sliceAlpha);

                builder.vertex(m, -halfW, y, -halfL).color(r, g, b, sliceAlpha);
                builder.vertex(m,  halfW, y,  halfL).color(r, g, b, sliceAlpha);
                builder.vertex(m,  halfW, y, -halfL).color(r, g, b, sliceAlpha);
            }
        }

        /* 2. Side walls with vertical exponential gradient */
        float botAlpha = MathUtils.clamp(densityVal * 0.35F, 0F, 1F);
        float topAlpha = MathUtils.clamp(densityVal * (float) Math.exp(-maxH / falloffVal) * 0.35F, 0F, 1F);

        float yBot = baseH;
        float yTop = baseH + maxH;

        /* Wall 1: Z = -halfL */
        builder.vertex(m, -halfW, yBot, -halfL).color(r, g, b, botAlpha);
        builder.vertex(m,  halfW, yBot, -halfL).color(r, g, b, botAlpha);
        builder.vertex(m,  halfW, yTop, -halfL).color(r, g, b, topAlpha);

        builder.vertex(m, -halfW, yBot, -halfL).color(r, g, b, botAlpha);
        builder.vertex(m,  halfW, yTop, -halfL).color(r, g, b, topAlpha);
        builder.vertex(m, -halfW, yTop, -halfL).color(r, g, b, topAlpha);

        /* Wall 2: Z = +halfL */
        builder.vertex(m,  halfW, yBot,  halfL).color(r, g, b, botAlpha);
        builder.vertex(m, -halfW, yBot,  halfL).color(r, g, b, botAlpha);
        builder.vertex(m, -halfW, yTop,  halfL).color(r, g, b, topAlpha);

        builder.vertex(m,  halfW, yBot,  halfL).color(r, g, b, botAlpha);
        builder.vertex(m, -halfW, yTop,  halfL).color(r, g, b, topAlpha);
        builder.vertex(m,  halfW, yTop,  halfL).color(r, g, b, topAlpha);

        /* Wall 3: X = -halfW */
        builder.vertex(m, -halfW, yBot,  halfL).color(r, g, b, botAlpha);
        builder.vertex(m, -halfW, yBot, -halfL).color(r, g, b, botAlpha);
        builder.vertex(m, -halfW, yTop, -halfL).color(r, g, b, topAlpha);

        builder.vertex(m, -halfW, yBot,  halfL).color(r, g, b, botAlpha);
        builder.vertex(m, -halfW, yTop, -halfL).color(r, g, b, topAlpha);
        builder.vertex(m, -halfW, yTop,  halfL).color(r, g, b, topAlpha);

        /* Wall 4: X = +halfW */
        builder.vertex(m,  halfW, yBot, -halfL).color(r, g, b, botAlpha);
        builder.vertex(m,  halfW, yBot,  halfL).color(r, g, b, botAlpha);
        builder.vertex(m,  halfW, yTop,  halfL).color(r, g, b, topAlpha);

        builder.vertex(m,  halfW, yBot, -halfL).color(r, g, b, botAlpha);
        builder.vertex(m,  halfW, yTop,  halfL).color(r, g, b, topAlpha);
        builder.vertex(m,  halfW, yTop, -halfL).color(r, g, b, topAlpha);

        Bufs.draw(builder);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        stack.pop();
    }
}

