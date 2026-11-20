package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A slim trackbar: a 4 pixel rounded rail, the part up to the value filled with the accent, a round
 * thumb with a dark frame that swells under the mouse and while dragged, and the number on the right.
 */
public class UIGlassSlider extends UIElement
{
    private static final int VALUE_ROOM = 34;

    private final Consumer<Double> callback;

    private double min;
    private double max = 100D;
    private double value;
    private boolean whole = true;
    private boolean dragging;

    public UIGlassSlider(Consumer<Double> callback)
    {
        super();

        this.callback = callback;
        this.h(20);
    }

    public UIGlassSlider limit(double min, double max, boolean whole)
    {
        this.min = min;
        this.max = max;
        this.whole = whole;

        return this;
    }

    public UIGlassSlider setValue(double value)
    {
        this.value = MathUtils.clamp(value, this.min, this.max);

        return this;
    }

    public double getValue()
    {
        return this.value;
    }

    /** The 4 pixel rail, filled up to the thumb, and the thumb: dark frame, light disc, accent core. */
    public static void paintTrack(UIContext context, int start, int end, int cy, int thumbX, boolean lit, int base)
    {
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        if (FrutigerAero.on())
        {
            boolean opened = RoundedDraw.begin(context.batcher);

            FrutigerAero.slider(context.batcher, start, end, cy, thumbX, lit, base);
            RoundedDraw.end(context.batcher, opened);

            return;
        }

        RoundedDraw.fill(context.batcher, start, cy - 2, end, cy + 2, 2, RoundedDraw.ALL, 0xff3a4558);

        if (thumbX > start)
        {
            RoundedDraw.fill(context.batcher, start, cy - 2, thumbX, cy + 2, 2, RoundedDraw.ALL, accent);
        }

        int size = lit ? base + 2 : base;
        int inner = size - 4;
        int core = lit ? 6 : 4;

        RoundedDraw.fill(context.batcher, thumbX - size / 2, cy - size / 2, thumbX - size / 2 + size, cy - size / 2 + size, size / 2, RoundedDraw.ALL, 0xff0d1118);
        RoundedDraw.fill(context.batcher, thumbX - inner / 2, cy - inner / 2, thumbX - inner / 2 + inner, cy - inner / 2 + inner, inner / 2, RoundedDraw.ALL, 0xfff2f5fa);
        RoundedDraw.fill(context.batcher, thumbX - core / 2, cy - core / 2, thumbX - core / 2 + core, cy - core / 2 + core, core / 2, RoundedDraw.ALL, accent);
    }

    private int railStart()
    {
        return this.area.x + 8;
    }

    private int railEnd()
    {
        return this.area.ex() - VALUE_ROOM;
    }

    private void drag(UIContext context)
    {
        double t = MathUtils.clamp((context.mouseX - this.railStart()) / (double) Math.max(1, this.railEnd() - this.railStart()), 0D, 1D);
        double next = this.min + t * (this.max - this.min);

        if (this.whole)
        {
            next = Math.round(next);
        }

        if (next != this.value)
        {
            this.value = next;

            if (this.callback != null)
            {
                this.callback.accept(next);
            }
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            this.dragging = true;
            this.drag(context);

            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.dragging = false;

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging)
        {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();

            if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
            {
                this.dragging = false;
            }
            else
            {
                this.drag(context);
            }
        }

        Area a = this.area;
        int start = this.railStart();
        int end = this.railEnd();
        double t = this.max > this.min ? (this.value - this.min) / (this.max - this.min) : 0D;
        int thumbX = start + (int) Math.round((end - start) * t);
        boolean lit = this.dragging || (this.area.isInside(context) && context.mouseX < end + 8);

        paintTrack(context, start, end, a.my(), thumbX, lit, 14);

        FontRenderer font = context.batcher.getFont();
        String text = this.whole ? String.valueOf((int) Math.round(this.value)) : String.format("%.2f", this.value);

        context.batcher.text(text, a.ex() - font.getWidth(text), a.my(font.getHeight()), Colors.setA(Colors.WHITE, 0.85F), false);

        super.render(context);
    }
}
