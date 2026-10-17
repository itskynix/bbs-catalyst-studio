package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.ComponentBezier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The channel list beside a {@link UIComponentGraph}: for a transform its three groups
 * (position, rotation, scale), for a pose the same under a collapsible heading per bone. Each group
 * is a row of three cells, one per axis, coloured red, green and blue. A cell's box shows or hides
 * its curve; its label makes it the channel whose points and handles can be edited.
 */
public class UIComponentChannelTree extends UIElement
{
    private static final int ROW = 15;
    private static final int BOX = 9;
    private static final int LABEL_WIDTH = 56;
    private static final int PAD = 6;

    private static final String[][] AXES = {{"X", "Y", "Z"}, {"Pitch", "Yaw", "Roll"}, {"X", "Y", "Z"}};

    private final UIComponentGraph graph;
    private final Set<String> collapsed = new HashSet<>();

    private int scroll;

    /** One line of the tree: a bone heading (group -1), or a group of three channels. */
    private record Row(String bone, int group)
    {}

    public UIComponentChannelTree(UIComponentGraph graph)
    {
        this.graph = graph;

        this.relative(graph.getKeyframes()).x(0).y(TimelineRulerRenderer.RULER_BLOCK_HEIGHT).w(UIComponentGraph.TREE_WIDTH).h(1F, -TimelineRulerRenderer.RULER_BLOCK_HEIGHT);
    }

    private List<Row> rows()
    {
        List<Row> rows = new ArrayList<>();

        if (!this.graph.isPose())
        {
            for (int group = 0; group < 3; group++)
            {
                rows.add(new Row("", group));
            }

            return rows;
        }

        for (String bone : this.graph.getBones())
        {
            rows.add(new Row(bone, -1));

            if (!this.collapsed.contains(bone))
            {
                for (int group = 0; group < 3; group++)
                {
                    rows.add(new Row(bone, group));
                }
            }
        }

        return rows;
    }

    private static String keyOf(Row row, int axis)
    {
        return (row.bone().isEmpty() ? "" : row.bone() + "/") + ComponentBezier.KEYS[row.group() * 3 + axis];
    }

    private static String groupLabel(int group)
    {
        return (group == 0 ? UIKeys.KEYFRAMES_GRAPH_POSITION : group == 1 ? UIKeys.KEYFRAMES_GRAPH_ROTATION : UIKeys.KEYFRAMES_GRAPH_SCALE).get();
    }

    private int top()
    {
        return this.area.y + ROW + 4;
    }

    private int indent(Row row)
    {
        return this.graph.isPose() && row.group() >= 0 ? 8 : 0;
    }

    private int cellWidth()
    {
        return (this.area.w - LABEL_WIDTH - PAD * 2 - 8) / 3;
    }

    private int maxScroll(int rows)
    {
        return Math.max(0, rows * ROW - (this.area.ey() - this.top()));
    }

    /* Input */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        if (context.mouseButton == 0 && context.mouseY >= this.top())
        {
            List<Row> rows = this.rows();
            int index = (context.mouseY - this.top() + this.scroll) / ROW;

            if (index >= 0 && index < rows.size())
            {
                this.click(context, rows.get(index));
            }
        }

        return true;
    }

    private void click(UIContext context, Row row)
    {
        if (row.group() < 0)
        {
            if (!this.collapsed.remove(row.bone()))
            {
                this.collapsed.add(row.bone());
            }

            return;
        }

        int x = this.area.x + PAD + this.indent(row);
        int cx = x + LABEL_WIDTH;

        if (context.mouseX < cx)
        {
            /* The group's name flips all three of its curves. */
            boolean all = true;

            for (int axis = 0; axis < 3; axis++)
            {
                all &= this.graph.isChannelVisible(keyOf(row, axis));
            }

            for (int axis = 0; axis < 3; axis++)
            {
                this.graph.setChannelVisible(keyOf(row, axis), !all);
            }

            return;
        }

        int axis = Math.min(2, Math.max(0, (context.mouseX - cx) / Math.max(1, this.cellWidth())));
        String key = keyOf(row, axis);

        if (context.mouseX < cx + axis * this.cellWidth() + BOX + 3)
        {
            this.graph.setChannelVisible(key, !this.graph.isChannelVisible(key));
        }
        else
        {
            this.graph.activate(key);
        }
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        int step = context.mouseWheel > 0 ? -ROW * 2 : ROW * 2;

        this.scroll = Math.max(0, Math.min(this.maxScroll(this.rows().size()), this.scroll + step));

        return true;
    }

    /* Rendering */

    /** Drawn by the timeline after the graph (see {@link #renderLate}): the graph paints over its whole area, tree included. */
    @Override
    public void render(UIContext context)
    {}

    public void renderLate(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.baseSurface());
        context.batcher.box(this.area.ex() - 1, this.area.y, this.area.ex(), this.area.ey(), Colors.setA(Colors.WHITE, 0.12F));
        context.batcher.text(UIKeys.KEYFRAMES_GRAPH_CHANNELS.get(), this.area.x + PAD, this.area.y + 4, Colors.setA(Colors.WHITE, 0.6F));

        List<Row> rows = this.rows();
        int top = this.top();

        this.scroll = Math.max(0, Math.min(this.maxScroll(rows.size()), this.scroll));

        context.batcher.clip(this.area.x, top, this.area.w, this.area.ey() - top, context);

        for (int i = 0; i < rows.size(); i++)
        {
            int y = top + i * ROW - this.scroll;

            if (y + ROW >= top && y <= this.area.ey())
            {
                this.renderRow(context, rows.get(i), y);
            }
        }

        context.batcher.unclip(context);
    }

    private void renderRow(UIContext context, Row row, int y)
    {
        int x = this.area.x + PAD + this.indent(row);
        int textY = y + (ROW - context.batcher.getFont().getHeight()) / 2;

        if (row.group() < 0)
        {
            boolean closed = this.collapsed.contains(row.bone());

            context.batcher.box(this.area.x, y, this.area.ex() - 1, y + ROW - 1, Colors.setA(Colors.WHITE, 0.06F));
            context.batcher.text((closed ? "+ " : "- ") + row.bone(), x, textY, Colors.WHITE);

            return;
        }

        context.batcher.text(groupLabel(row.group()), x, textY, Colors.setA(Colors.WHITE, 0.75F));

        int cellWidth = this.cellWidth();

        for (int axis = 0; axis < 3; axis++)
        {
            String key = keyOf(row, axis);
            int color = UIComponentGraph.AXIS_COLORS[axis];
            int cx = x + LABEL_WIDTH + axis * cellWidth;
            boolean shown = this.graph.isChannelVisible(key);
            boolean active = key.equals(this.graph.getActive());
            int by = y + (ROW - BOX) / 2;

            if (active)
            {
                context.batcher.box(cx - 2, y, cx + cellWidth - 2, y + ROW - 1, Colors.setA(color, 0.28F));
            }

            context.batcher.box(cx, by, cx + BOX, by + BOX, 0x80000000);
            context.batcher.outline(cx, by, cx + BOX, by + BOX, Colors.setA(color, shown ? 1F : 0.5F));

            if (shown)
            {
                context.batcher.box(cx + 2, by + 2, cx + BOX - 2, by + BOX - 2, color | Colors.A100);
            }

            context.batcher.text(AXES[row.group()][axis], cx + BOX + 3, textY, Colors.setA(Colors.WHITE, shown ? 1F : 0.5F));
        }
    }
}
