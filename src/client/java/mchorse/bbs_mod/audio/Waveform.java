package mchorse.bbs_mod.audio;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.List;

public class Waveform
{
    private WaveformEnvelope envelope;
    private List<ColorCode> colorCodes;
    private float[] cues;
    private int pixelsPerSecond;
    private int height;
    private float duration;

    public void generate(Wave data, List<ColorCode> colorCodes, int pixelsPerSecond, int height)
    {
        this.populate(data, pixelsPerSecond, height);
        this.render(colorCodes, data.getCues());
    }

    /** Update annotations without rebuilding the audio envelope. */
    public void render(List<ColorCode> colorCodes, float[] cues)
    {
        this.colorCodes = colorCodes;
        this.cues = cues;
    }

    public void populate(Wave data, int pixelsPerSecond, int height)
    {
        this.envelope = new WaveformEnvelope(data);
        this.pixelsPerSecond = Math.max(1, pixelsPerSecond);
        this.height = height;
        this.duration = data.getDuration();
    }

    public void delete()
    {
        this.envelope = null;
        this.colorCodes = null;
        this.cues = null;
    }

    public boolean isCreated()
    {
        return this.envelope != null;
    }

    /** Preview scale only; waveform detail is determined by the visible pixels. */
    public int getPixelsPerSecond()
    {
        return this.pixelsPerSecond;
    }

    public int getWidth()
    {
        return (int) Math.ceil(this.duration * this.pixelsPerSecond);
    }

    public int getHeight()
    {
        return this.height;
    }

    public float getDuration()
    {
        return this.duration;
    }

    public void render(Batcher2D batcher, int color, int x, int y, int w, int h, float startTime, float endTime)
    {
        if (this.envelope == null || w <= 0 || h <= 0 || !(endTime > startTime))
        {
            return;
        }

        double secondsPerPixel = (endTime - (double) startTime) / w;
        /* Physical width also covers BBS's custom GUI scale. Limit CPU work
         * when a zoomed timeline extends far beyond the screen. */
        int first = (int) Math.max(0L, -(long) x);
        int last = (int) Math.min(w, (long) MinecraftClient.getInstance().getWindow().getWidth() - x);

        if (first >= last)
        {
            return;
        }

        float center = y + h / 2F;
        WaveformEnvelope.Range range = new WaveformEnvelope.Range();
        boolean wasBatching = batcher.isBatching();

        batcher.clip(x, y, w, h, 0, 0);

        /* 1. Background cues and color codes */
        batcher.beginBatch();

        if (this.colorCodes != null || this.cues != null)
        {
            for (int i = first; i < last; i++)
            {
                double from = startTime + i * secondsPerPixel;
                double to = from + secondsPerPixel;

                if (to <= 0 || from >= this.duration)
                {
                    continue;
                }

                if (this.colorCodes != null)
                {
                    for (ColorCode code : this.colorCodes)
                    {
                        if (code.isInside((float) Math.max(0, from)))
                        {
                            int columnColor = tint(code.color | 0xff000000, color);
                            batcher.gradientVBox(x + i, y, x + i + 1, y + h,
                                Colors.mulA(columnColor, 0.125F), Colors.mulA(columnColor, 0.375F));
                            break;
                        }
                    }
                }

                if (this.cues != null)
                {
                    for (float cue : this.cues)
                    {
                        if (cue >= from && cue < to)
                        {
                            batcher.box(x + i, y, x + i + 1, y + h, tint(Colors.ACTIVE | Colors.A75, color));
                            break;
                        }
                    }
                }
            }
        }

        batcher.endBatch();

        /* 2. Sample envelope columns into contiguous arrays for high-res sub-pixel connected waveform rendering */
        int count = last - first;
        float[] peaks = new float[count];
        float[] avgs = new float[count];
        int[] cols = new int[count];
        boolean[] active = new boolean[count];

        float halfH = Math.max(1.0F, (h - 2) / 2.0F);

        for (int i = 0; i < count; i++)
        {
            int colIdx = first + i;
            double from = startTime + colIdx * secondsPerPixel;
            double to = from + secondsPerPixel;

            if (to <= 0 || from >= this.duration)
            {
                active[i] = false;
                continue;
            }

            this.envelope.sample(from, to, range);
            active[i] = true;

            int col = color;
            if (this.colorCodes != null)
            {
                for (ColorCode code : this.colorCodes)
                {
                    if (code.isInside((float) Math.max(0, from)))
                    {
                        col = tint(code.color | 0xff000000, color);
                        break;
                    }
                }
            }
            cols[i] = col;

            /* Peak amplitude with subtle hairline floor so silent regions retain sleek waveform continuity */
            peaks[i] = Math.max(0.6F, range.maximum * halfH);
            avgs[i] = Math.max(0.3F, range.average * halfH);
        }

        /* 3. Modern DaVinci / Premiere Style Continuous Connected Geometry */
        Matrix4f matrix = batcher.getContext().getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* 3a. Subtle Center Zero-Crossing Reference Line */
        int zeroCrossColor = Colors.setA(color, 0.22F);
        for (int i = 0; i < count - 1; i++)
        {
            if (!active[i] && !active[i + 1])
            {
                continue;
            }
            float x0 = x + first + i;
            float x1 = x0 + 1.0F;

            builder.vertex(matrix, x0, center - 0.5F, 0F).color(zeroCrossColor)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x0, center + 0.5F, 0F).color(zeroCrossColor)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, center + 0.5F, 0F).color(zeroCrossColor)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, center - 0.5F, 0F).color(zeroCrossColor)/*$ nx*/.next()/**/;
        }

        /* 3b. Outer Waveform Body Fill (Connected Trapezoids) with Soft Opacity */
        for (int i = 0; i < count - 1; i++)
        {
            if (!active[i] && !active[i + 1])
            {
                continue;
            }

            float x0 = x + first + i;
            float x1 = x0 + 1.0F;

            float y0Top = center - peaks[i];
            float y0Bot = center + peaks[i];
            float y1Top = center - peaks[i + 1];
            float y1Bot = center + peaks[i + 1];

            int c0 = Colors.setA(cols[i], 0.38F);
            int c1 = Colors.setA(cols[i + 1], 0.38F);

            builder.vertex(matrix, x0, y0Top, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x0, y0Bot, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Bot, 0F).color(c1)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Top, 0F).color(c1)/*$ nx*/.next()/**/;
        }

        /* 3c. Inner RMS / Average Energy Core (Connected Trapezoids) with Higher Density */
        for (int i = 0; i < count - 1; i++)
        {
            if (!active[i] && !active[i + 1])
            {
                continue;
            }

            float x0 = x + first + i;
            float x1 = x0 + 1.0F;

            float y0Top = center - avgs[i];
            float y0Bot = center + avgs[i];
            float y1Top = center - avgs[i + 1];
            float y1Bot = center + avgs[i + 1];

            int c0 = Colors.setA(cols[i], 0.75F);
            int c1 = Colors.setA(cols[i + 1], 0.75F);

            builder.vertex(matrix, x0, y0Top, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x0, y0Bot, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Bot, 0F).color(c1)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Top, 0F).color(c1)/*$ nx*/.next()/**/;
        }

        /* 3d. Modern High-Res 1D Anti-Aliased Crest Line (Continuous Contour Stroke) */
        float strokeHalf = 0.65F;
        for (int i = 0; i < count - 1; i++)
        {
            if (!active[i] && !active[i + 1])
            {
                continue;
            }

            float x0 = x + first + i;
            float x1 = x0 + 1.0F;

            float y0Top = center - peaks[i];
            float y1Top = center - peaks[i + 1];
            float y0Bot = center + peaks[i];
            float y1Bot = center + peaks[i + 1];

            int c0 = Colors.setA(cols[i], 1.0F);
            int c1 = Colors.setA(cols[i + 1], 1.0F);

            /* Top crest line */
            builder.vertex(matrix, x0, y0Top - strokeHalf, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x0, y0Top + strokeHalf, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Top + strokeHalf, 0F).color(c1)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Top - strokeHalf, 0F).color(c1)/*$ nx*/.next()/**/;

            /* Bottom crest line */
            builder.vertex(matrix, x0, y0Bot - strokeHalf, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x0, y0Bot + strokeHalf, 0F).color(c0)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Bot + strokeHalf, 0F).color(c1)/*$ nx*/.next()/**/;
            builder.vertex(matrix, x1, y1Bot - strokeHalf, 0F).color(c1)/*$ nx*/.next()/**/;
        }

        Bufs.draw(builder);

        batcher.unclip(0, 0);

        if (wasBatching)
        {
            batcher.beginBatch();
        }
    }

    private static int tint(int a, int b)
    {
        int result = 0;

        for (int shift = 0; shift <= 24; shift += 8)
        {
            result |= (((a >>> shift) & 255) * ((b >>> shift) & 255) / 255) << shift;
        }

        return result;
    }
}
