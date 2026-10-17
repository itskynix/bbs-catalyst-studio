package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.tooltips.styles.TooltipStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.InterpolationUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;

public class InterpolationRenderer
{
    public static final int PREVIEW_WIDTH = 140;
    public static final int PREVIEW_HEIGHT = 130;

    private static Color color = new Color();

    /**
     * Render the interpolation preview card in an already placed
     * {@link #PREVIEW_WIDTH}×{@link #PREVIEW_HEIGHT} area.
     */
    public static void renderInterpolationPreview(IInterp interp, UIContext context, Area area, int duration)
    {
        if (interp == null)
        {
            return;
        }

        int x = area.x;
        int y = area.y;
        int w = area.w;
        int h = area.h;

        TooltipStyle style = TooltipStyle.get();

        style.renderBackground(context, area);

        Color fg = color.set(style.getForegroundColor(), false);
        int fontColor = style.getTextColor();

        fg.a = 0.2F;

        String name = InterpolationUtils.getName(interp).get();
        int fh = context.batcher.getFont().getHeight() + 2;

        context.batcher.textShadow(name, x + 10, y + 10, fontColor);

        renderInterpolationGraph(interp, context, fg, fontColor, x + 10, y + 10 + fh, w - 20, h - 20 - fh, duration, 10);
    }

    public static void renderInterpolationGraph(IInterp interp, UIContext context, Color fg, int fontColor, int x, int y, int w, int h, int duration, int padding)
    {
        if (interp == null)
        {
            return;
        }

        final float iterations = 40;

        LineBuilder grid = new LineBuilder(0.25F);

        /* Border */
        grid.add(x, y);
        grid.add(x, y + h);
        grid.add(x + w, y + h);
        grid.add(x + w, y);
        grid.add(x, y);

        /* Vertical middle */
        grid.push();
        grid.add(x + w / 2, y);
        grid.add(x + w / 2, y + h);

        /* Horizontal middle */
        grid.push();
        grid.add(x, y + h / 2);
        grid.add(x + w, y + h / 2);

        /* Padding */
        grid.push();
        grid.add(x, y + h - padding);
        grid.add(x + w, y + h - padding);

        grid.push();
        grid.add(x, y + padding);
        grid.add(x + w, y + padding);

        grid.render(context.batcher, SolidColorLineRenderer.get(fg));

        fg.a = 1F;

        context.batcher.clip(x - 6, y - 6, w + 18, h + 12, context);

        /* Area under the curve: a column per pixel, fading out towards the baseline */
        int rgb = fg.getRGBColor();
        int base = y + h - padding;

        for (int px = 0; px < w; px++)
        {
            float v = 1 - interp.interpolate(0, 1, px / (float) w);
            int top = (int) (y + padding + v * (h - padding * 2F));
            int from = Math.min(top, base);
            int to = Math.max(top, base);

            if (to > from)
            {
                context.batcher.gradientVBox(x + px, from, x + px + 1, to, Colors.setA(rgb, top < base ? 0.32F : 0F), Colors.setA(rgb, top < base ? 0F : 0.32F));
            }
        }

        /* Render the interpolation graph */
        LineBuilder line = new LineBuilder(1.5F);

        for (int i = 0; i <= iterations * 2; i++)
        {
            float factor = i / (iterations * 2);
            float value = 1 - interp.interpolate(0, 1, factor);

            float x1 = x + factor * w;
            float y1 = y + padding + value * (h - padding * 2F);

            line.add(x1, y1);
        }

        line.render(context.batcher, SolidColorLineRenderer.get(fg));

        /* Keyframe pins on both ends */
        renderPin(context, x, y + h - padding, rgb);
        renderPin(context, x + w, y + padding, rgb);

        float tick = context.getTickTransition() % (duration + 20);
        float factor = MathUtils.clamp(tick / (float) duration, 0, 1);
        int px = x + w + 5;
        int py = y + padding + (int) ((1 - interp.interpolate(0, 1, factor)) * (h - padding * 2F));

        context.batcher.box(px - 2, py - 2, px + 2, py + 2, Colors.A100 + fg.getRGBColor());

        /* The same moment as a dot riding the curve itself: x = t, y = curve(t) */
        int cx = x + (int) (factor * w);

        context.batcher.box(cx - 4, py - 4, cx + 5, py + 5, Colors.setA(Colors.WHITE, 0.25F));
        context.batcher.box(cx - 2, py - 2, cx + 3, py + 3, Colors.A100 | Colors.WHITE);
        context.batcher.unclip(context);
    }

    /** A keyframe diamond with a dark rim. */
    public static void renderPin(UIContext context, int cx, int cy, int rgb)
    {
        for (int dy = -5; dy <= 5; dy++)
        {
            int half = 5 - Math.abs(dy);

            context.batcher.box(cx - half, cy + dy, cx + half + 1, cy + dy + 1, Colors.A100 | 0x101018);
        }

        for (int dy = -3; dy <= 3; dy++)
        {
            int half = 3 - Math.abs(dy);

            context.batcher.box(cx - half, cy + dy, cx + half + 1, cy + dy + 1, Colors.A100 | Colors.WHITE);
        }
    }

    /** A round handle knob; bigger and brighter while hovered or dragged. */
    public static void renderKnob(UIContext context, int cx, int cy, int rgb, boolean active)
    {
        int r = active ? 5 : 4;

        for (int dy = -r; dy <= r; dy++)
        {
            int half = (int) Math.round(Math.sqrt(r * r + 0.5 - dy * dy));

            context.batcher.box(cx - half, cy + dy, cx + half + 1, cy + dy + 1, Colors.A100 | 0x101018);
        }

        r -= 1;

        for (int dy = -r; dy <= r; dy++)
        {
            int half = (int) Math.round(Math.sqrt(r * r + 0.5 - dy * dy));

            context.batcher.box(cx - half, cy + dy, cx + half + 1, cy + dy + 1, active ? Colors.A100 | Colors.WHITE : Colors.A100 | rgb);
        }
    }
}