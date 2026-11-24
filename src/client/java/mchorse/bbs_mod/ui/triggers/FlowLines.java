package mchorse.bbs_mod.ui.triggers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Thick lines and curves for the flow editor, drawn as quads in one batch under the current matrix,
 * so they pan and zoom with the board like everything else on it.
 */
public class FlowLines
{
    private final UIContext context;
    private BufferBuilder builder;
    private Matrix4f matrix;

    public FlowLines(UIContext context)
    {
        this.context = context;
    }

    public void line(float x1, float y1, float x2, float y2, float thickness, int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 1E-4F)
        {
            return;
        }

        if (this.builder == null)
        {
            this.context.batcher.flush();
            this.matrix = this.context.batcher.getContext().getMatrices().peek().getPositionMatrix();
            this.builder = Bufs.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        }

        float nx = -dy / length * thickness / 2F;
        float ny = dx / length * thickness / 2F;

        this.builder.vertex(this.matrix, x1 + nx, y1 + ny, 0).color(color)/*$ nx*/.next()/**/;
        this.builder.vertex(this.matrix, x1 - nx, y1 - ny, 0).color(color)/*$ nx*/.next()/**/;
        this.builder.vertex(this.matrix, x2 - nx, y2 - ny, 0).color(color)/*$ nx*/.next()/**/;
        this.builder.vertex(this.matrix, x2 + nx, y2 + ny, 0).color(color)/*$ nx*/.next()/**/;
    }

    /** An S-shaped wire from an output to an input, leaving and arriving horizontally. */
    public void wire(float x1, float y1, float x2, float y2, float thickness, int color)
    {
        float reach = Math.max(30F, Math.abs(x2 - x1) * 0.5F);
        float cx1 = x1 + reach;
        float cx2 = x2 - reach;
        int segments = 28;
        float px = x1;
        float py = y1;

        for (int i = 1; i <= segments; i++)
        {
            float t = i / (float) segments;
            float u = 1F - t;
            float x = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
            float y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;

            this.line(px, py, x, y, thickness, color);
            px = x;
            py = y;
        }
    }

    public void end()
    {
        if (this.builder != null)
        {
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            Bufs.draw(this.builder);

            this.builder = null;
        }
    }
}
