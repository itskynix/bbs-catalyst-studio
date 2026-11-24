package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector3f;

/**
 * The live preview of the Viewport tab: the trigger volume as a wireframe over a floor grid, with
 * the block it hangs on, turned by dragging. It reads the box every frame, so every change in the
 * fields shows at once, here and in the world behind.
 */
public class UIBoxPreview extends UIElement
{
    private final TriggerBoxBlockEntity box;
    private float yaw = 35F;
    private float pitch = 28F;
    private boolean dragging;
    private int lastX;
    private int lastY;

    public UIBoxPreview(TriggerBoxBlockEntity box)
    {
        this.box = box;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.dragging = true;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.dragging = false;

        return super.subMouseReleased(context);
    }

    private float[] project(float x, float y, float z, float scale, float cx, float cy)
    {
        Vector3f v = new Vector3f(x, y, z).rotateY((float) Math.toRadians(this.yaw)).rotateX((float) Math.toRadians(this.pitch));

        return new float[] {cx + v.x * scale, cy - v.y * scale};
    }

    private void edge(FlowLines lines, float[] a, float[] b, float t, int color)
    {
        lines.line(a[0], a[1], b[0], b[1], t, color);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging)
        {
            this.yaw += (context.mouseX - this.lastX) * 0.6F;
            this.pitch = Math.max(-80F, Math.min(80F, this.pitch + (context.mouseY - this.lastY) * 0.6F));
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xc0181a1e);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff30353d);
        context.batcher.clip(this.area, context);

        float w = this.box.boxWidth;
        float h = this.box.boxHeight;
        float d = this.box.boxDepth;
        float ox = this.box.offsetX;
        float oy = this.box.offsetY;
        float oz = this.box.offsetZ;
        float extent = Math.max(Math.max(w, h), Math.max(d, 1F)) + Math.max(Math.abs(ox), Math.max(Math.abs(oy), Math.abs(oz))) * 2F;
        float scale = Math.min(this.area.w, this.area.h) * 0.42F / Math.max(1F, extent);
        float cx = this.area.mx();
        float cy = this.area.my();
        float baseY = -h / 2F;

        FlowLines lines = new FlowLines(context);

        /* Floor grid, one line a block */
        int half = (int) Math.ceil(extent / 2F) + 1;

        for (int i = -half; i <= half; i++)
        {
            this.edge(lines, this.project(i, baseY - oy, -half, scale, cx, cy), this.project(i, baseY - oy, half, scale, cx, cy), 1F, 0x30ffffff);
            this.edge(lines, this.project(-half, baseY - oy, i, scale, cx, cy), this.project(half, baseY - oy, i, scale, cx, cy), 1F, 0x30ffffff);
        }

        /* The block the box hangs on */
        this.cube(lines, -0.5F, baseY - oy, -0.5F, 0.5F, baseY - oy + 1F, 0.5F, scale, cx, cy, 1.5F, 0xff7d8794);

        /* The volume */
        int color = this.box.showOutline ? 0xffffb547 : 0x90ffb547;

        this.cube(lines, ox - w / 2F, baseY, oz - d / 2F, ox + w / 2F, baseY + h, oz + d / 2F, scale, cx, cy, 2F, color);
        lines.end();

        String size = String.format("%.2f x %.2f x %.2f", w, h, d);

        context.batcher.textShadow(size, this.area.x + 8, this.area.ey() - 14, Colors.WHITE);
        context.batcher.unclip(context);

        super.render(context);
    }

    private void cube(FlowLines lines, float x1, float y1, float z1, float x2, float y2, float z2, float scale, float cx, float cy, float t, int color)
    {
        float[][] p = new float[8][];

        for (int i = 0; i < 8; i++)
        {
            p[i] = this.project((i & 1) == 0 ? x1 : x2, (i & 2) == 0 ? y1 : y2, (i & 4) == 0 ? z1 : z2, scale, cx, cy);
        }

        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

        for (int[] e : edges)
        {
            this.edge(lines, p[e[0]], p[e[1]], t, color);
        }
    }
}
