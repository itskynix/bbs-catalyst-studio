package mchorse.bbs_mod.film.replays;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * The radial replay picker: hold the key, move the mouse towards an actor's slice, let go. Eight
 * slices to a page, clockwise from the top; more replays than that make pages, turned ONLY by
 * clicking the arrow buttons beside the wheel (no wheel, no keys). The centre is a dead zone — let
 * go there, outside the wheel, or press Escape, and nothing happens.
 *
 * <p>The key is polled from the window every tick, not only through key events: if the release is
 * lost (focus change, another screen eating it) the menu still commits or closes instead of
 * staying stuck.</p>
 */
public class UIRadialReplayScreen extends Screen
{
    private static final int PER_PAGE = 8;
    private static final int DEAD_ZONE = 28;
    private static final int INNER = 38;
    private static final int OUTER = 112;
    private static final int RIM = 4;
    private static final int ARROW_SIZE = 26;
    private static final int ARROW_GAP = 46;
    private static final int ICON = 24;

    /** The pop-in, nanoseconds: the wheel grows from nothing to full size, overshooting a little on the way. */
    private static final long POP_NANOS = 120_000_000L;

    /** Time constants of a slice's hover weight, seconds: quick to light up, a little slower to fade. */
    private static final float HOVER_IN = 0.045F;
    private static final float HOVER_OUT = 0.085F;

    /** How much a lit slice's content grows, and how far its rim swells outward, pixels. */
    private static final float HOVER_GROW = 0.18F;
    private static final float HOVER_SWELL = 8F;

    private final KeyBinding key;
    private final int current;
    private final int pages;
    private final List<Replay> replays;
    private int page;
    private boolean finished;

    /** The dashboard's UI context, borrowed to draw form previews the way the replay list does. */
    private UIContext previewContext;

    /** Per slice, 0..1: how lit it is. Eased towards its target by wall-clock time, so the speed does not depend on the frame rate. */
    private final float[] hoverProgress = new float[PER_PAGE];
    private final long openedAt = System.nanoTime();
    private long lastFrame = this.openedAt;

    public UIRadialReplayScreen(Film film, KeyBinding key, int current)
    {
        super(Text.literal("Replays"));

        this.key = key;
        this.current = current;
        this.replays = film.replays.getList();
        this.pages = Math.max(1, (this.replays.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(this.pages - 1, Math.max(0, current) / PER_PAGE));
    }

    @Override
    public boolean shouldPause()
    {
        return false;
    }

    private boolean isHeld()
    {
        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(this.key);
        long window = MinecraftClient.getInstance().getWindow().getHandle();

        if (bound.getCategory() == InputUtil.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(window, bound.getCode()) == GLFW.GLFW_PRESS;
        }

        return InputUtil.isKeyPressed(window, bound.getCode());
    }

    private int cursorX()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return (int) (mc.mouse.getX() * this.width / mc.getWindow().getWidth());
    }

    private int cursorY()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return (int) (mc.mouse.getY() * this.height / mc.getWindow().getHeight());
    }

    /** The replay index under the cursor, or -1 in the dead zone, outside the wheel or over an empty slice. */
    private int hovered(int mouseX, int mouseY)
    {
        double dx = mouseX - this.width / 2D;
        double dy = mouseY - this.height / 2D;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < DEAD_ZONE || distance > OUTER + RIM + 6)
        {
            return -1;
        }

        double angle = Math.atan2(dx, -dy);

        if (angle < 0)
        {
            angle += Math.PI * 2;
        }

        double size = Math.PI * 2 / PER_PAGE;
        int slice = (int) ((angle + size / 2) / size) % PER_PAGE;
        int index = this.page * PER_PAGE + slice;

        return index < this.replays.size() ? index : -1;
    }

    private int arrowX(boolean right)
    {
        return this.width / 2 + (right ? 1 : -1) * (OUTER + ARROW_GAP) - ARROW_SIZE / 2;
    }

    private int arrowY()
    {
        return this.height / 2 - ARROW_SIZE / 2;
    }

    private boolean overArrow(boolean right, int mouseX, int mouseY)
    {
        int x = this.arrowX(right);
        int y = this.arrowY();

        return this.pages > 1 && mouseX >= x && mouseX < x + ARROW_SIZE && mouseY >= y && mouseY < y + ARROW_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0)
        {
            /* The only way to turn a page: a click on an arrow. */
            if (this.overArrow(false, (int) mouseX, (int) mouseY))
            {
                this.page = (this.page - 1 + this.pages) % this.pages;

                return true;
            }

            if (this.overArrow(true, (int) mouseX, (int) mouseY))
            {
                this.page = (this.page + 1) % this.pages;

                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void finish(boolean commit)
    {
        if (this.finished)
        {
            return;
        }

        this.finished = true;

        int target = commit ? this.hovered(this.cursorX(), this.cursorY()) : -1;
        boolean rewind = hasShiftDown();

        MinecraftClient.getInstance().setScreen(null);

        if (target >= 0 && target != this.current)
        {
            MultiTakeSession.switchTo(target, rewind);
        }
    }

    @Override
    public void tick()
    {
        if (!this.isHeld())
        {
            this.finish(true);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(this.key);

        if (bound.getCategory() == InputUtil.Type.KEYSYM && keyCode == bound.getCode())
        {
            this.finish(true);

            return true;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(this.key);

        if (bound.getCategory() == InputUtil.Type.MOUSE && button == bound.getCode())
        {
            this.finish(true);

            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close()
    {
        /* Escape and every other way out: no action. */
        this.finish(false);
    }

    /** Ease-out-back: 0 at the start, a little past 1 near the end, settling on 1. */
    private static float popScale(float t)
    {
        if (t >= 1F)
        {
            return 1F;
        }

        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        float u = t - 1F;

        return Math.max(0F, 1F + c3 * u * u * u + c1 * u * u);
    }

    private static int lerpColor(int from, int to, float t)
    {
        int out = 0;

        for (int shift = 0; shift <= 24; shift += 8)
        {
            int c0 = from >> shift & 0xFF;
            int c1 = to >> shift & 0xFF;

            out |= (c0 + Math.round((c1 - c0) * t) & 0xFF) << shift;
        }

        return out;
    }

    private static int scaleAlpha(int color, float factor)
    {
        int alpha = Math.round((color >>> 24) * factor);

        return alpha << 24 | (color & 0xFFFFFF);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int hover = this.hovered(mouseX, mouseY);
        double size = Math.PI * 2 / PER_PAGE;

        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - this.lastFrame) / 1.0e9F);

        this.lastFrame = now;

        /* Every slice eases towards lit or dark on its own. */
        for (int s = 0; s < PER_PAGE; s++)
        {
            boolean lit = hover >= 0 && hover == this.page * PER_PAGE + s;
            float target = lit ? 1F : 0F;
            float tau = lit ? HOVER_IN : HOVER_OUT;

            this.hoverProgress[s] += (target - this.hoverProgress[s]) * (1F - (float) Math.exp(-dt / tau));
        }

        float pop = popScale(Math.min(1F, (now - this.openedAt) / (float) POP_NANOS));

        /* The pop-in turns about the centre of the screen. */
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0F);
        context.getMatrices().scale(pop, pop, 1F);
        context.getMatrices().translate(-cx, -cy, 0F);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        /* The wheel is raw geometry: blending on, depth and culling off (a strip's winding must not
         * decide whether it shows), and the position-colour program bound for every piece. */
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        this.fan(matrix, cx, cy, 0, DEAD_ZONE, 0, Math.PI * 2, 24, 0xC0101418);

        for (int s = 0; s < PER_PAGE; s++)
        {
            int index = this.page * PER_PAGE + s;
            boolean exists = index < this.replays.size();
            float hp = exists ? this.hoverProgress[s] : 0F;
            int fill = !exists ? 0x50181818 : lerpColor(index == this.current ? 0xD02F6FB0 : 0xC0242830, 0xF0FF7A3C, hp);
            int rim = !exists ? 0x50303030 : lerpColor(index == this.current ? 0xFF5FA8F0 : 0xFF50586A, 0xFFFFD27A, hp);
            double a0 = s * size - size / 2 + 0.015;
            double a1 = s * size + size / 2 - 0.015;

            /* A lit slice swells outwards (and a hair inwards) - only its own geometry moves. */
            float outer = OUTER + hp * HOVER_SWELL;
            float inner = INNER - hp * 2F;

            this.fan(matrix, cx, cy, inner, outer, a0, a1, 10, fill);
            this.fan(matrix, cx, cy, outer, outer + RIM, a0, a1, 10, rim);

            if (hp > 0.01F)
            {
                /* A soft halo just inside the rim. */
                this.fan(matrix, cx, cy, outer - 10, outer, a0, a1, 10, scaleAlpha(0x80FFE0A0, hp));
            }
        }

        for (int s = 0; s < PER_PAGE; s++)
        {
            double a = s * size - size / 2;

            this.fan(matrix, cx, cy, INNER, OUTER + RIM, a - 0.007, a + 0.007, 1, 0xFF0A0C10);
        }

        this.drawArrow(context, matrix, false, mouseX, mouseY);
        this.drawArrow(context, matrix, true, mouseX, mouseY);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();

        /* Slice contents: the number and the name grow about the slice's own centre; push/pop keeps
         * the growth out of the neighbours. The previews are drawn after, in final screen coordinates. */
        for (int s = 0; s < PER_PAGE; s++)
        {
            int index = this.page * PER_PAGE + s;

            if (index >= this.replays.size())
            {
                continue;
            }

            double mid = s * size;
            int r = (INNER + OUTER) / 2;
            int tx = cx + (int) Math.round(Math.sin(mid) * r);
            int ty = cy - (int) Math.round(Math.cos(mid) * r);
            float hp = this.hoverProgress[s];
            float grow = 1F + hp * HOVER_GROW;
            Replay replay = this.replays.get(index);
            String name = MultiTakeSession.name(replay, index);

            if (name.length() > 9)
            {
                name = name.substring(0, 8) + ".";
            }

            boolean hasForm = replay.form.get() != null;
            int nameColor = lerpColor(0xFFE0E4EE, 0xFFFFFFFF, hp);

            context.getMatrices().push();
            context.getMatrices().translate(tx, ty, 0F);
            context.getMatrices().scale(grow, grow, 1F);
            context.getMatrices().translate(-tx, -ty, 0F);

            if (hasForm)
            {
                context.drawCenteredTextWithShadow(this.textRenderer, (index + 1) + "", tx, ty - ICON / 2 - 13, 0x9AA3B5);
                context.drawCenteredTextWithShadow(this.textRenderer, name, tx, ty + ICON / 2 + 2, nameColor);
            }
            else
            {
                context.drawCenteredTextWithShadow(this.textRenderer, (index + 1) + "", tx, ty - 11, 0x9AA3B5);
                context.drawCenteredTextWithShadow(this.textRenderer, name, tx, ty, nameColor);
            }

            context.getMatrices().pop();
        }

        context.getMatrices().pop();

        /* The form pictures, in final screen coordinates: the pop-in about the screen centre, then
         * the slice's own growth about the slice centre. */
        for (int s = 0; s < PER_PAGE; s++)
        {
            int index = this.page * PER_PAGE + s;

            if (index >= this.replays.size())
            {
                continue;
            }

            Form form = this.replays.get(index).form.get();

            if (form == null)
            {
                continue;
            }

            double mid = s * size;
            int r = (INNER + OUTER) / 2;
            float tx = cx + (float) Math.sin(mid) * r;
            float ty = cy - (float) Math.cos(mid) * r;
            float grow = 1F + this.hoverProgress[s] * HOVER_GROW;

            /* The icon sits 3 px above the slice centre, and that offset grows with the slice. */
            float px = cx + pop * (tx - cx);
            float py = cy + pop * (ty - 3F * grow - cy);

            this.drawPreview(context, form, Math.round(px), Math.round(py), Math.round(ICON * grow * pop), mouseX, mouseY);
        }

        /* Header: the full name under the cursor. */
        if (hover >= 0)
        {
            String title = "Seçili: " + MultiTakeSession.name(this.replays.get(hover), hover);
            int w = this.textRenderer.getWidth(title);

            context.fill(cx - w / 2 - 8, 14, cx + w / 2 + 8, 32, 0xB0101418);
            context.drawCenteredTextWithShadow(this.textRenderer, title, cx, 19, 0xFFE0A0);
        }

        /* Footer: the page, and the hints. */
        context.drawCenteredTextWithShadow(this.textRenderer, "Menu " + (this.page + 1) + "/" + this.pages, cx, cy + OUTER + RIM + 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "[Bırak]: Seç / [Shift + Bırak]: Baştan Sar", cx, this.height - 22, hasShiftDown() ? 0xFFE0A0 : 0xB0B8C8);
    }

    /**
     * One form's preview, {@code box} square around ({@code cx}, {@code cy}) in screen pixels. It is drawn through
     * the same preview path as the replay list's, which brings its own GL state: the matrix stack
     * is pushed and popped around it, the preview is clipped to its box, and the blend and depth
     * state the rest of the wheel relies on is put back afterwards. A form that fails to draw
     * leaves its slice without a picture instead of taking the screen down.
     */
    private void drawPreview(DrawContext context, Form form, int cx, int cy, int box, int mouseX, int mouseY)
    {
        if (this.previewContext == null)
        {
            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard == null)
            {
                return;
            }

            this.previewContext = new UIContext(dashboard);
        }

        UIContext ui = this.previewContext;
        int x = cx - box / 2;
        int y = cy - box / 2;

        if (box < 2)
        {
            return;
        }

        context.getMatrices().push();
        ui.setup(new UIRenderingContext(context));
        ui.setMouse(mouseX, mouseY);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        try
        {
            ui.batcher.clip(x, y, box, box, ui);
            FormUtilsClient.renderPreview(form, ui, x, y, x + box, y + box);
            ui.batcher.unclip(ui);
        }
        catch (RuntimeException e)
        {
            /* No picture for this one. */
        }

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.getMatrices().pop();
    }

    private void drawArrow(DrawContext context, Matrix4f matrix, boolean right, int mouseX, int mouseY)
    {
        if (this.pages <= 1)
        {
            return;
        }

        int x = this.arrowX(right);
        int y = this.arrowY();
        boolean hot = this.overArrow(right, mouseX, mouseY);

        this.quad(matrix, x - 1, y - 1, x + ARROW_SIZE + 1, y + ARROW_SIZE + 1, hot ? 0xFFFFD27A : 0xFF50586A);
        this.quad(matrix, x, y, x + ARROW_SIZE, y + ARROW_SIZE, hot ? 0xF0FF7A3C : 0xD0242830);

        context.drawCenteredTextWithShadow(this.textRenderer, right ? ">" : "<", x + ARROW_SIZE / 2, y + ARROW_SIZE / 2 - 4, 0xFFFFFF);
    }

    private void quad(Matrix4f matrix, float x0, float y0, float x1, float y1, int color)
    {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x0, y0, 0F).color(color).next();
        buffer.vertex(matrix, x0, y1, 0F).color(color).next();
        buffer.vertex(matrix, x1, y0, 0F).color(color).next();
        buffer.vertex(matrix, x1, y1, 0F).color(color).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** A filled ring segment between two radii and two angles (clockwise from the top); radius 0 makes a pie. */
    private void fan(Matrix4f matrix, int cx, int cy, float r0, float r1, double a0, double a1, int steps, int color)
    {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= steps; i++)
        {
            double a = a0 + (a1 - a0) * i / steps;
            float sin = (float) Math.sin(a);
            float cos = (float) Math.cos(a);

            buffer.vertex(matrix, cx + sin * r0, cy - cos * r0, 0F).color(color).next();
            buffer.vertex(matrix, cx + sin * r1, cy - cos * r1, 0F).color(color).next();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
