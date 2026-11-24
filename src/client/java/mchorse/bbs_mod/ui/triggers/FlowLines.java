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
 *
 * <p>Face culling is off while the batch draws: a strip's winding flips with the direction the line
 * runs, and with culling on every wire drawn right to left (or up) simply vanished.</p>
 */
public class FlowLines
{
    private static final int SEGMENTS = 32;

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

    /** The points of an S-shaped wire from an output to an input, leaving and arriving horizontally. */
    public static float[] curve(float x1, float y1, float x2, float y2)
    {
        float reach = Math.max(30F, Math.abs(x2 - x1) * 0.5F);
        float cx1 = x1 + reach;
        float cx2 = x2 - reach;
        float[] points = new float[(SEGMENTS + 1) * 2];

        for (int i = 0; i <= SEGMENTS; i++)
        {
            float t = i / (float) SEGMENTS;
            float u = 1F - t;

            points[i * 2] = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
            points[i * 2 + 1] = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
        }

        return points;
    }

    public void polyline(float[] points, float thickness, int color)
    {
        for (int i = 2; i < points.length; i += 2)
        {
            this.line(points[i - 2], points[i - 1], points[i], points[i + 1], thickness, color);
        }
    }

    public void wire(float x1, float y1, float x2, float y2, float thickness, int color)
    {
        this.polyline(curve(x1, y1, x2, y2), thickness, color);
    }

    /**
     * A live wire: a soft glow under the core, and bright pulses running from the output to the
     * input. {@code time} is in ticks; the pulses move a fixed share of the wire per tick, so they
     * flow at the same pace however long the wire is.
     */
    public void liveWire(float x1, float y1, float x2, float y2, float thickness, int color, int glow, float time)
    {
        float[] points = curve(x1, y1, x2, y2);

        this.polyline(points, thickness * 4F, (glow & 0x00ffffff) | 0x18000000);
        this.polyline(points, thickness * 2.2F, (glow & 0x00ffffff) | 0x30000000);
        this.polyline(points, thickness, color);

        /* Pulses: short bright dashes, three on a wire, a little behind each other */
        int count = SEGMENTS;
        float phase = (time * 0.035F) % 1F;

        for (int p = 0; p < 3; p++)
        {
            float head = (phase + p / 3F) % 1F;
            int index = (int) (head * count);
            int tail = Math.max(0, index - 3);

            for (int i = tail; i < index; i++)
            {
                float fade = (i - tail + 1) / 3F;
                int alpha = (int) (255 * fade);

                this.line(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3], thickness * 1.6F, (alpha << 24) | 0xffffff);
                this.line(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3], thickness * 4F, ((alpha / 4) << 24) | (glow & 0xffffff));
            }
        }
    }

    public void end()
    {
        if (this.builder != null)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            Bufs.draw(this.builder);
            RenderSystem.enableCull();

            this.builder = null;
        }
    }
}
