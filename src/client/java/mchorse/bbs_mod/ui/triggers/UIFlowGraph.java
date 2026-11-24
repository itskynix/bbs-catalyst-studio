package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.triggers.FlowGraph;
import mchorse.bbs_mod.triggers.FlowLink;
import mchorse.bbs_mod.triggers.FlowNode;
import mchorse.bbs_mod.triggers.FlowNodeType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The flow board: a grid that pans and zooms with its origin axes drawn bold, round cornered node
 * cards in the colours of BBS's theme, and live execution wires that glow and pulse from an output
 * to the next node's input.
 *
 * <p>Left click on a card picks it (Shift or Ctrl adds or takes it away) and dragging moves every
 * picked card. Left click on the empty board clears the picks; dragging there draws a selection box
 * and picks every card it touches. Dragging an output pin pulls a wire: dropped on a card it
 * connects, on the empty board it opens the add menu and wires the new node in; a wired input picks
 * its wire up again. The middle button, or Alt with the left, pans; the wheel zooms round the cursor;
 * right click opens the add menu; Delete removes the picked cards.</p>
 */
public class UIFlowGraph extends UIElement
{
    public static final int NODE_W = 150;
    public static final int HEADER = 16;
    public static final int ROW = 12;
    private static final int GRID = 16;

    public FlowGraph graph;
    /** The card Details shows: the last one picked. */
    public FlowNode selected;
    /** Every picked card. */
    public final Set<FlowNode> picked = new LinkedHashSet<>();

    public final UIFlowSearch search = new UIFlowSearch();

    private Consumer<FlowNode> onSelect = (n) -> {};
    private Runnable onChange = () -> {};

    private float panX = 60;
    private float panY = 60;
    private float zoom = 1F;

    /* Gestures */
    private boolean panning;
    private int lastMouseX;
    private int lastMouseY;
    private boolean draggingNodes;
    private final Map<FlowNode, float[]> dragOffsets = new HashMap<>();
    private FlowNode wireFrom;
    private String wirePin;
    private float wireX;
    private float wireY;
    private boolean boxing;
    private boolean boxAdd;
    private float boxX;
    private float boxY;
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
        this.picked.clear();
        this.select(null);
    }

    /** Picks just this card, or nothing. */
    public void select(FlowNode node)
    {
        this.picked.clear();

        if (node != null)
        {
            this.picked.add(node);
        }

        this.setPrimary(node);
    }

    private void setPrimary(FlowNode node)
    {
        this.selected = node;
        this.onSelect.accept(node);
    }

    /** Brings a card to the middle of the board and picks it. */
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

        return HEADER + 4 + execRows(node) * ROW + params * ROW + (params > 0 ? 6 : 4);
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
        boolean additive = Window.isShiftPressed() || Window.isCtrlPressed();

        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;

        if (context.mouseButton == 1)
        {
            if (node != null)
            {
                FlowNode target = node;

                if (!this.picked.contains(node))
                {
                    this.select(node);
                }

                context.replaceContextMenu((menu) ->
                {
                    menu.action(Icons.DUPE, L10n.lang("bbs.ui.trigger_box.duplicate"), () -> this.duplicate(target));
                    menu.action(Icons.REMOVE, L10n.lang("bbs.ui.trigger_box.delete_node"), Colors.NEGATIVE, this::deletePicked);
                });
            }
            else
            {
                this.openSearch(context.mouseX, context.mouseY);
            }

            return true;
        }

        if (context.mouseButton == 2 || (context.mouseButton == 0 && Window.isAltPressed()))
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
            if (additive)
            {
                if (!this.picked.remove(node))
                {
                    this.picked.add(node);
                    this.setPrimary(node);
                }
                else
                {
                    this.setPrimary(this.picked.isEmpty() ? null : this.picked.iterator().next());

                    return true;
                }
            }
            else if (!this.picked.contains(node))
            {
                this.select(node);
            }
            else
            {
                this.setPrimary(node);
            }

            /* Every picked card moves with the one under the cursor */
            this.draggingNodes = true;
            this.dragOffsets.clear();

            for (FlowNode p : this.picked)
            {
                this.dragOffsets.put(p, new float[] {gx - p.x, gy - p.y});
            }

            /* The picked card is drawn last, on top */
            this.graph.nodes.remove(node);
            this.graph.nodes.add(node);

            return true;
        }

        /* The empty board: clear, then a selection box from here */
        if (!additive)
        {
            this.select(null);
        }

        this.boxing = true;
        this.boxAdd = additive;
        this.boxX = gx;
        this.boxY = gy;

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

        if (this.draggingNodes)
        {
            this.draggingNodes = false;
            this.dragOffsets.clear();
            this.onChange.run();
        }

        if (this.boxing)
        {
            this.boxing = false;
            this.pickInBox(this.toGraphX(context.mouseX), this.toGraphY(context.mouseY));
        }

        this.panning = false;

        return super.subMouseReleased(context);
    }

    /** Picks every card the box touches, on top of the earlier picks when it was drawn with Shift or Ctrl. */
    private void pickInBox(float gx, float gy)
    {
        float x1 = Math.min(this.boxX, gx);
        float y1 = Math.min(this.boxY, gy);
        float x2 = Math.max(this.boxX, gx);
        float y2 = Math.max(this.boxY, gy);

        if (x2 - x1 < 2 && y2 - y1 < 2)
        {
            return;
        }

        if (!this.boxAdd)
        {
            this.picked.clear();
        }

        FlowNode last = this.selected;

        for (FlowNode node : this.graph.nodes)
        {
            if (node.x <= x2 && node.x + NODE_W >= x1 && node.y <= y2 && node.y + height(node) >= y1)
            {
                this.picked.add(node);
                last = node;
            }
        }

        this.setPrimary(this.picked.isEmpty() ? null : last);
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
        if (!this.picked.isEmpty() && !context.isFocused() && this.isVisible() && (context.isPressed(GLFW.GLFW_KEY_DELETE) || context.isPressed(GLFW.GLFW_KEY_BACKSPACE)))
        {
            this.deletePicked();

            return true;
        }

        return false;
    }

    private void deletePicked()
    {
        for (FlowNode node : new ArrayList<>(this.picked))
        {
            this.graph.remove(node);
        }

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

    /** The wires' clock: the world's game time plus the frame's share of the next tick. */
    private static float time()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.world == null ? 0F : (mc.world.getTime() % 1000000L) + mc.getTickDelta();
    }

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

        float mx = this.toGraphX(context.mouseX);
        float my = this.toGraphY(context.mouseY);

        if (this.draggingNodes)
        {
            for (Map.Entry<FlowNode, float[]> entry : this.dragOffsets.entrySet())
            {
                entry.getKey().x = Math.round(mx - entry.getValue()[0]);
                entry.getKey().y = Math.round(my - entry.getValue()[1]);
            }
        }

        if (this.wireFrom != null)
        {
            this.wireX = mx;
            this.wireY = my;
        }

        context.batcher.clip(this.area, context);
        this.renderGrid(context);

        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(this.area.x + this.panX, this.area.y + this.panY, 0);
        matrices.scale(this.zoom, this.zoom, 1F);

        float time = time();

        this.renderWires(context, time);

        context.batcher.beginBatch();

        for (FlowNode node : this.graph.nodes)
        {
            this.renderNode(context, node);
        }

        context.batcher.endBatch();

        /* The wire being pulled, and the selection box, over the cards */
        if (this.wireFrom != null && this.wireFrom.getType() != null)
        {
            FlowLines lines = new FlowLines(context);
            int index = Math.max(0, this.wireFrom.getType().outputs.indexOf(this.wirePin));
            int accent = FlowTheme.accent();

            lines.liveWire(this.wireFrom.x + NODE_W + 4, outputY(this.wireFrom, index), this.wireX, this.wireY, 2.2F, accent, accent, time);
            lines.end();
        }

        if (this.boxing)
        {
            float x1 = Math.min(this.boxX, mx);
            float y1 = Math.min(this.boxY, my);
            float x2 = Math.max(this.boxX, mx);
            float y2 = Math.max(this.boxY, my);
            int accent = FlowTheme.accent();

            context.batcher.box(x1, y1, x2, y2, (accent & 0xffffff) | 0x30000000);
            context.batcher.outline(x1, y1, x2, y2, (accent & 0xffffff) | 0xc0000000);
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

        context.batcher.box(x1, y1, x2, y2, FlowTheme.board());

        if (step >= 4)
        {
            int minor = FlowTheme.gridMinor();
            int major = FlowTheme.gridMajor();
            int firstX = (int) Math.floor(-this.panX / step);
            int firstY = (int) Math.floor(-this.panY / step);

            context.batcher.beginBatch();

            for (int i = firstX; x1 + this.panX + i * step <= x2; i++)
            {
                float x = x1 + this.panX + i * step;

                context.batcher.box(x, y1, x + 1, y2, i % 8 == 0 ? major : minor);
            }

            for (int i = firstY; y1 + this.panY + i * step <= y2; i++)
            {
                float y = y1 + this.panY + i * step;

                context.batcher.box(x1, y, x2, y + 1, i % 8 == 0 ? major : minor);
            }

            context.batcher.endBatch();
        }

        /* The origin: X = 0 and Y = 0, bold, so (0, 0) is never lost */
        int accent = (FlowTheme.accent() & 0xffffff) | 0xb0000000;
        float ox = x1 + this.panX;
        float oy = y1 + this.panY;
        float t = Math.max(2F, 2F * this.zoom);

        if (ox >= x1 - t && ox <= x2 + t)
        {
            context.batcher.box(ox - t / 2F, y1, ox + t / 2F, y2, accent);
        }

        if (oy >= y1 - t && oy <= y2 + t)
        {
            context.batcher.box(x1, oy - t / 2F, x2, oy + t / 2F, accent);
        }
    }

    private void renderWires(UIContext context, float time)
    {
        FlowLines lines = new FlowLines(context);
        int wire = FlowTheme.wire();
        int accent = FlowTheme.accent();

        for (FlowLink link : this.graph.links)
        {
            FlowNode a = this.graph.get(link.from());
            FlowNode b = this.graph.get(link.to());

            if (a == null || b == null || a.getType() == null)
            {
                continue;
            }

            int index = Math.max(0, a.getType().outputs.indexOf(link.pin()));
            boolean hot = this.picked.contains(a) || this.picked.contains(b);

            lines.liveWire(a.x + NODE_W + 4, outputY(a, index), b.x - 4, inputY(b), hot ? 2.4F : 1.8F, hot ? accent : wire, hot ? accent : a.getType().kind.color, time);
        }

        lines.end();
    }

    private void renderNode(UIContext context, FlowNode node)
    {
        Batcher2D batcher = context.batcher;
        FlowNodeType type = node.getType();
        int color = type == null ? 0xff555555 : type.kind.color;
        int r = FlowTheme.radius();
        float x = node.x;
        float y = node.y;
        int h = height(node);
        float ex = x + NODE_W;
        float ey = y + h;
        boolean picked = this.picked.contains(node);
        int border = picked ? 2 : 1;

        /* Card: a soft shadow, a rim (the accent when picked), the body, a header in the family colour */
        FlowTheme.rounded(batcher, x + 2, y + 3, ex + 2, ey + 3, r, 0x50000000, true, true);
        FlowTheme.rounded(batcher, x - border, y - border, ex + border, ey + border, r + border, picked ? FlowTheme.accent() : FlowTheme.border(), true, true);
        FlowTheme.rounded(batcher, x, y, ex, ey, r, FlowTheme.body(), true, true);
        FlowTheme.roundedHeader(batcher, x, y, ex, y + HEADER, r, color, Colors.mulRGB(color, 0.55F) | 0xff000000);

        if (FlowTheme.gloss())
        {
            FlowTheme.roundedHeader(batcher, x + 1, y + 1, ex - 1, y + HEADER / 2F, Math.max(0, r - 1), 0x38ffffff, 0x10ffffff);
        }

        batcher.box(x, y + HEADER, ex, y + HEADER + 1, 0x40000000);

        String title = type == null ? node.type : UIFlowSearch.name(type);

        batcher.textShadow(title, x + 6, y + 4, Colors.WHITE);

        if (type == null)
        {
            return;
        }

        /* The family as a small pill on the right of the header */
        String kind = UIFlowSearch.kindLabel(type.kind).get().toUpperCase();
        int kw = batcher.getFont().getWidth(kind);

        FlowTheme.rounded(batcher, ex - kw - 10, y + 3, ex - 3, y + HEADER - 3, 3, 0x40000000, true, true);
        batcher.text(kind, ex - kw - 6, y + 4, 0xd0ffffff);

        /* Execution pins */
        if (type.hasInput())
        {
            this.renderPin(batcher, x, inputY(node), this.isInputWired(node), color);
        }

        for (int i = 0; i < type.outputs.size(); i++)
        {
            String pin = type.outputs.get(i);
            float py = outputY(node, i);

            this.renderPin(batcher, ex, py, this.graph.next(node.id, pin) != null, color);

            if (type.outputs.size() > 1 || !pin.equals("then"))
            {
                String label = L10n.lang("bbs.ui.trigger_box.pin." + pin).get();

                batcher.text(label, ex - 9 - batcher.getFont().getWidth(label), py - 4, FlowTheme.text());
            }
        }

        /* The node's fields, read only on the card: they are edited in Details */
        float py = y + HEADER + 4 + execRows(node) * ROW;

        if (!type.params.isEmpty())
        {
            FlowTheme.rounded(batcher, x + 4, py, ex - 4, py + type.params.size() * ROW + 2, Math.min(3, r), (FlowTheme.field() & 0xffffff) | 0xa0000000, true, true);
        }

        for (FlowNodeType.Param param : type.params)
        {
            String label = L10n.lang("bbs.ui.trigger_box.param." + param.id()).get() + ": ";
            String value = node.param(param.id());
            int labelW = batcher.getFont().getWidth(label);
            String shown = batcher.getFont().limitToWidth(value.isEmpty() ? "-" : value, NODE_W - 16 - labelW);

            batcher.text(label, x + 8, py + 2, FlowTheme.dim());
            batcher.text(shown, x + 8 + labelW, py + 2, FlowTheme.text());
            py += ROW;
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

    /** A pin: a round socket with an arrow head pointing the way the flow goes, lit when wired. */
    private void renderPin(Batcher2D batcher, float cx, float cy, boolean wired, int color)
    {
        int rim = FlowTheme.border();
        int fill = wired ? color : FlowTheme.body();

        FlowTheme.rounded(batcher, cx - 5, cy - 5, cx + 5, cy + 5, 5, rim, true, true);
        FlowTheme.rounded(batcher, cx - 4, cy - 4, cx + 4, cy + 4, 4, fill, true, true);
        batcher.box(cx - 1, cy - 2, cx + 1, cy + 2, wired ? Colors.WHITE : FlowTheme.dim());
        batcher.box(cx + 1, cy - 1, cx + 2, cy + 1, wired ? Colors.WHITE : FlowTheme.dim());
    }

    private void renderOverlay(UIContext context)
    {
        String zoom = Math.round(this.zoom * 100) + "%";
        String picked = this.picked.size() > 1 ? L10n.lang("bbs.ui.trigger_box.picked").format(this.picked.size()).get() + "   " : "";
        int dim = (FlowTheme.text() & 0xffffff) | 0x90000000;

        context.batcher.text(picked + zoom, this.area.ex() - 8 - context.batcher.getFont().getWidth(picked + zoom), this.area.ey() - 14, dim);
        context.batcher.text(L10n.lang("bbs.ui.trigger_box.flow_hint").get(), this.area.x + 8, this.area.ey() - 14, dim);
    }
}
