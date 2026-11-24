package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.triggers.FlowGraph;
import mchorse.bbs_mod.triggers.FlowLink;
import mchorse.bbs_mod.triggers.FlowNode;
import mchorse.bbs_mod.triggers.FlowNodeType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * The flow board: a grey grid that pans and zooms, node cards with a coloured header, and execution
 * wires drawn as S curves from an output pin to the next node's input.
 *
 * <p>Left drag on a card moves it, on an output pin pulls a wire (dropped on a card it connects,
 * dropped on the empty board it opens the add menu and wires the new node in), on a wired input it
 * picks the wire up again. Left or middle drag on the board pans, the wheel zooms round the cursor,
 * right click opens the add menu, Delete removes the picked card.</p>
 */
public class UIFlowGraph extends UIElement
{
    public static final int NODE_W = 150;
    public static final int HEADER = 16;
    public static final int ROW = 12;
    private static final int GRID = 16;

    public FlowGraph graph;
    public FlowNode selected;

    public final UIFlowSearch search = new UIFlowSearch();

    private Consumer<FlowNode> onSelect = (n) -> {};
    private Runnable onChange = () -> {};

    private float panX = 40;
    private float panY = 40;
    private float zoom = 1F;

    /* Gestures */
    private boolean panning;
    private int lastMouseX;
    private int lastMouseY;
    private FlowNode dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private FlowNode wireFrom;
    private String wirePin;
    private float wireX;
    private float wireY;
    private float addX;
    private float addY;

    public UIFlowGraph()
    {
        this.search.relative(this).wh(UIFlowSearch.WIDTH, UIFlowSearch.HEIGHT);
        this.search.setVisible(false);
        this.add(this.search);
    }

    public UIFlowGraph callbacks(Consumer<FlowNode> onSelect, Runnable onChange)
    {
        this.onSelect = onSelect;
        this.onChange = onChange;

        return this;
    }

    public void setGraph(FlowGraph graph)
    {
        this.graph = graph;
        this.select(null);
    }

    public void select(FlowNode node)
    {
        this.selected = node;
        this.onSelect.accept(node);
    }

    /** Brings a card to the middle of the board. */
    public void focus(FlowNode node)
    {
        this.panX = this.area.w / 2F - (node.x + NODE_W / 2F) * this.zoom;
        this.panY = this.area.h / 2F - (node.y + height(node) / 2F) * this.zoom;
        this.select(node);
    }

    /* Layout of a card */

    public static int execRows(FlowNode node)
    {
        FlowNodeType type = node.getType();

        return type == null ? 1 : Math.max(1, type.outputs.size());
    }

    public static int height(FlowNode node)
    {
        FlowNodeType type = node.getType();
        int params = type == null ? 0 : type.params.size();

        return HEADER + 4 + execRows(node) * ROW + params * ROW + (params > 0 ? 4 : 2);
    }

    private static float inputY(FlowNode node)
    {
        return node.y + HEADER + 4 + ROW / 2F;
    }

    private static float outputY(FlowNode node, int index)
    {
        return node.y + HEADER + 4 + ROW / 2F + index * ROW;
    }

    private float toGraphX(int mouseX)
    {
        return (mouseX - this.area.x - this.panX) / this.zoom;
    }

    private float toGraphY(int mouseY)
    {
        return (mouseY - this.area.y - this.panY) / this.zoom;
    }

    private FlowNode nodeAt(float gx, float gy)
    {
        for (int i = this.graph.nodes.size() - 1; i >= 0; i--)
        {
            FlowNode node = this.graph.nodes.get(i);

            if (gx >= node.x && gx <= node.x + NODE_W && gy >= node.y && gy <= node.y + height(node))
            {
                return node;
            }
        }

        return null;
    }

    /** The output pin under the point, as its index, or -1. */
    private int outputAt(FlowNode node, float gx, float gy)
    {
        FlowNodeType type = node.getType();

        if (type == null)
        {
            return -1;
        }

        for (int i = 0; i < type.outputs.size(); i++)
        {
            if (Math.abs(gx - (node.x + NODE_W)) <= 7 && Math.abs(gy - outputY(node, i)) <= 6)
            {
                return i;
            }
        }

        return -1;
    }

    private boolean inputAt(FlowNode node, float gx, float gy)
    {
        FlowNodeType type = node.getType();

        return type != null && type.hasInput() && Math.abs(gx - node.x) <= 7 && Math.abs(gy - inputY(node)) <= 6;
    }

    /* Add menu */

    public void openSearch(int mouseX, int mouseY)
    {
        this.addX = this.toGraphX(mouseX);
        this.addY = this.toGraphY(mouseY);

        int x = Math.max(0, Math.min(this.area.w - UIFlowSearch.WIDTH, mouseX - this.area.x));
        int y = Math.max(0, Math.min(this.area.h - UIFlowSearch.HEIGHT, mouseY - this.area.y));
        FlowNode from = this.wireFrom;
        String pin = this.wirePin;

        this.search.relative(this).xy(x, y).wh(UIFlowSearch.WIDTH, UIFlowSearch.HEIGHT);
        this.search.resize();
        this.search.open((type) ->
        {
            FlowNode node = this.graph.add(type.id, this.addX, this.addY);

            if (from != null && type.hasInput())
            {
                this.graph.link(from.id, pin, node.id);
            }

            this.select(node);
            this.onChange.run();
        });
    }

    /* Input */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.graph == null || !this.area.isInside(context))
        {
            return false;
        }

        if (this.search.isVisible())
        {
            this.search.close();

            return true;
        }

        float gx = this.toGraphX(context.mouseX);
        float gy = this.toGraphY(context.mouseY);
        FlowNode node = this.nodeAt(gx, gy);

        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;

        if (context.mouseButton == 1)
        {
            if (node != null)
            {
                FlowNode target = node;

                this.select(node);
                context.replaceContextMenu((menu) ->
                {
                    menu.action(Icons.DUPE, L10n.lang("bbs.ui.trigger_box.duplicate"), () -> this.duplicate(target));
                    menu.action(Icons.REMOVE, L10n.lang("bbs.ui.trigger_box.delete_node"), Colors.NEGATIVE, () -> this.delete(target));
                });
            }
            else
            {
                this.openSearch(context.mouseX, context.mouseY);
            }

            return true;
        }

        if (context.mouseButton == 2)
        {
            this.panning = true;

            return true;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        /* Pins first: they stick out past the card's edge */
        for (int i = this.graph.nodes.size() - 1; i >= 0; i--)
        {
            FlowNode candidate = this.graph.nodes.get(i);
            int output = this.outputAt(candidate, gx, gy);

            if (output >= 0)
            {
                this.wireFrom = candidate;
                this.wirePin = candidate.getType().outputs.get(output);
                this.wireX = gx;
                this.wireY = gy;

                return true;
            }

            if (this.inputAt(candidate, gx, gy))
            {
                for (FlowLink link : this.graph.links)
                {
                    if (link.to() == candidate.id)
                    {
                        this.wireFrom = this.graph.get(link.from());
                        this.wirePin = link.pin();
                        this.wireX = gx;
                        this.wireY = gy;
                        this.graph.unlink(link.from(), link.pin());
                        this.onChange.run();

                        return true;
                    }
                }
            }
        }

        if (node != null)
        {
            this.select(node);
            this.dragging = node;
            this.dragOffsetX = gx - node.x;
            this.dragOffsetY = gy - node.y;

            /* The picked card is drawn last, on top */
            this.graph.nodes.remove(node);
            this.graph.nodes.add(node);

            return true;
        }

        this.select(null);
        this.panning = true;

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.wireFrom != null)
        {
            float gx = this.toGraphX(context.mouseX);
            float gy = this.toGraphY(context.mouseY);
            FlowNode target = this.nodeAt(gx, gy);

            if (target != null && target != this.wireFrom)
            {
                this.graph.link(this.wireFrom.id, this.wirePin, target.id);
                this.onChange.run();
            }
            else if (target == null && this.area.isInside(context))
            {
                this.openSearch(context.mouseX, context.mouseY);
            }

            this.wireFrom = null;
            this.wirePin = null;
        }

        if (this.dragging != null)
        {
            this.dragging = null;
            this.onChange.run();
        }

        this.panning = false;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context) || (this.search.isVisible() && this.search.area.isInside(context)))
        {
            return false;
        }

        float gx = this.toGraphX(context.mouseX);
        float gy = this.toGraphY(context.mouseY);

        this.zoom = Math.max(0.35F, Math.min(2.5F, this.zoom * (context.mouseWheel > 0 ? 1.12F : 1F / 1.12F)));
        this.panX = context.mouseX - this.area.x - gx * this.zoom;
        this.panY = context.mouseY - this.area.y - gy * this.zoom;

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.selected != null && !context.isFocused() && this.isVisible() && (context.isPressed(GLFW.GLFW_KEY_DELETE) || context.isPressed(GLFW.GLFW_KEY_BACKSPACE)))
        {
            this.delete(this.selected);

            return true;
        }

        return false;
    }

    private void delete(FlowNode node)
    {
        this.graph.remove(node);
        this.select(null);
        this.onChange.run();
    }

    private void duplicate(FlowNode node)
    {
        FlowNode copy = this.graph.add(node.type, node.x + 20, node.y + 20);

        copy.params.putAll(node.params);
        this.select(copy);
        this.onChange.run();
    }

    /* Drawing */

    @Override
    public void render(UIContext context)
    {
        if (this.graph == null)
        {
            super.render(context);

            return;
        }

        if (this.panning)
        {
            this.panX += context.mouseX - this.lastMouseX;
            this.panY += context.mouseY - this.lastMouseY;
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;
        }

        if (this.dragging != null)
        {
            this.dragging.x = Math.round(this.toGraphX(context.mouseX) - this.dragOffsetX);
            this.dragging.y = Math.round(this.toGraphY(context.mouseY) - this.dragOffsetY);
        }

        if (this.wireFrom != null)
        {
            this.wireX = this.toGraphX(context.mouseX);
            this.wireY = this.toGraphY(context.mouseY);
        }

        context.batcher.clip(this.area, context);
        this.renderGrid(context);

        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(this.area.x + this.panX, this.area.y + this.panY, 0);
        matrices.scale(this.zoom, this.zoom, 1F);

        this.renderWires(context);

        for (FlowNode node : this.graph.nodes)
        {
            this.renderNode(context, node);
        }

        context.batcher.flush();
        matrices.pop();

        this.renderOverlay(context);
        context.batcher.unclip(context);

        super.render(context);
    }

    private void renderGrid(UIContext context)
    {
        int x1 = this.area.x;
        int y1 = this.area.y;
        int x2 = this.area.ex();
        int y2 = this.area.ey();
        float step = GRID * this.zoom;

        context.batcher.box(x1, y1, x2, y2, 0xff26282c);

        if (step < 4)
        {
            return;
        }

        int firstX = (int) Math.floor(-this.panX / step);
        int firstY = (int) Math.floor(-this.panY / step);

        for (int i = firstX; x1 + this.panX + i * step <= x2; i++)
        {
            float x = x1 + this.panX + i * step;

            context.batcher.box(x, y1, x + 1, y2, i % 8 == 0 ? 0xff17181b : 0xff2d3034);
        }

        for (int i = firstY; y1 + this.panY + i * step <= y2; i++)
        {
            float y = y1 + this.panY + i * step;

            context.batcher.box(x1, y, x2, y + 1, i % 8 == 0 ? 0xff17181b : 0xff2d3034);
        }
    }

    private void renderWires(UIContext context)
    {
        FlowLines lines = new FlowLines(context);

        for (FlowLink link : this.graph.links)
        {
            FlowNode a = this.graph.get(link.from());
            FlowNode b = this.graph.get(link.to());

            if (a == null || b == null || a.getType() == null)
            {
                continue;
            }

            int index = Math.max(0, a.getType().outputs.indexOf(link.pin()));
            boolean hot = a == this.selected || b == this.selected;

            lines.wire(a.x + NODE_W + 4, outputY(a, index), b.x - 4, inputY(b), hot ? 2.5F : 2F, hot ? 0xffffd27a : 0xffd8dde4);
        }

        if (this.wireFrom != null && this.wireFrom.getType() != null)
        {
            int index = Math.max(0, this.wireFrom.getType().outputs.indexOf(this.wirePin));

            lines.wire(this.wireFrom.x + NODE_W + 4, outputY(this.wireFrom, index), this.wireX, this.wireY, 2F, 0xffffd27a);
        }

        lines.end();
    }

    private void renderNode(UIContext context, FlowNode node)
    {
        FlowNodeType type = node.getType();
        int color = type == null ? 0xff555555 : type.kind.color;
        int x = (int) node.x;
        int y = (int) node.y;
        int h = height(node);
        int ex = x + NODE_W;
        int ey = y + h;
        boolean picked = node == this.selected;

        /* Card: soft shadow, dark body, a header in the family colour fading to the right */
        context.batcher.box(x + 3, y + 3, ex + 3, ey + 3, 0x66000000);
        context.batcher.box(x, y, ex, ey, 0xf01c1f24);
        context.batcher.gradientHBox(x, y, ex, y + HEADER, color, Colors.mulRGB(color, 0.35F) | 0xff000000);
        context.batcher.box(x, y + HEADER, ex, y + HEADER + 1, 0x60000000);
        context.batcher.box(x, y, x + 2, ey, color);
        context.batcher.outline(x, y, ex, ey, picked ? 0xffffc35a : 0xff0d0f12, picked ? 2 : 1);

        String title = type == null ? node.type : UIFlowSearch.name(type);

        context.batcher.textShadow(title, x + 6, y + 4, Colors.WHITE);

        if (type != null)
        {
            String kind = UIFlowSearch.kindLabel(type.kind).get().toUpperCase();

            context.batcher.text(kind, ex - 4 - context.batcher.getFont().getWidth(kind), y + 4, 0xb0ffffff);

            /* Execution pins: a notched arrow in, one per output out */
            if (type.hasInput())
            {
                this.renderPin(context, x, inputY(node), this.isInputWired(node));
            }

            for (int i = 0; i < type.outputs.size(); i++)
            {
                String pin = type.outputs.get(i);
                float py = outputY(node, i);

                this.renderPin(context, ex, py, this.graph.next(node.id, pin) != null);

                if (type.outputs.size() > 1 || !pin.equals("then"))
                {
                    String label = L10n.lang("bbs.ui.trigger_box.pin." + pin).get();

                    context.batcher.text(label, ex - 9 - context.batcher.getFont().getWidth(label), py - 4, 0xffc8ced6);
                }
            }

            /* The node's fields, read only on the card: they are edited in Details */
            int py = y + HEADER + 4 + execRows(node) * ROW;

            for (FlowNodeType.Param param : type.params)
            {
                String label = L10n.lang("bbs.ui.trigger_box.param." + param.id()).get() + ": ";
                String value = node.param(param.id());
                int labelW = context.batcher.getFont().getWidth(label);
                String shown = context.batcher.getFont().limitToWidth(value.isEmpty() ? "-" : value, NODE_W - 12 - labelW);

                context.batcher.text(label, x + 6, py + 2, 0xff8a94a0);
                context.batcher.text(shown, x + 6 + labelW, py + 2, 0xffe8ecf0);
                py += ROW;
            }
        }
    }

    private boolean isInputWired(FlowNode node)
    {
        for (FlowLink link : this.graph.links)
        {
            if (link.to() == node.id)
            {
                return true;
            }
        }

        return false;
    }

    /** A pin: an arrow head pointing the way the flow goes, filled when wired. */
    private void renderPin(UIContext context, float cx, float cy, boolean wired)
    {
        int x = (int) cx;
        int y = (int) cy;

        context.batcher.box(x - 4, y - 4, x + 2, y + 4, 0xff0d0f12);
        context.batcher.box(x - 3, y - 3, x + 1, y + 3, wired ? 0xffffffff : 0xff1c1f24);
        context.batcher.box(x + 1, y - 2, x + 3, y + 2, wired ? 0xffffffff : 0xffb8c0ca);
        context.batcher.box(x + 3, y - 1, x + 4, y + 1, wired ? 0xffffffff : 0xffb8c0ca);
    }

    private void renderOverlay(UIContext context)
    {
        String zoom = Math.round(this.zoom * 100) + "%";

        context.batcher.text(zoom, this.area.ex() - 8 - context.batcher.getFont().getWidth(zoom), this.area.ey() - 14, 0x80ffffff);
        context.batcher.text(L10n.lang("bbs.ui.trigger_box.flow_hint").get(), this.area.x + 8, this.area.ey() - 14, 0x80ffffff);
    }
}
