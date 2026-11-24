package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.triggers.FlowGraph;
import mchorse.bbs_mod.triggers.FlowLink;
import mchorse.bbs_mod.triggers.FlowNode;
import mchorse.bbs_mod.triggers.FlowNodeType;
import mchorse.bbs_mod.triggers.FlowNodeTypes;
import mchorse.bbs_mod.triggers.FlowValues;
import mchorse.bbs_mod.triggers.FlowVariable;
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
 * cards in the colours of BBS's theme, and live wires that glow and pulse from an output to an input.
 *
 * <p>A card lays its pins out in two columns: execution inputs then data inputs on the left,
 * execution outputs then data outputs on the right. A variable read is a small pill with no header,
 * a variable write a card headed SET. Growable cards have a row of + and - buttons, for inputs on the
 * left and for outputs on the right.</p>
 *
 * <p>Left click on a card picks it (Shift or Ctrl adds or takes it away) and dragging moves every
 * picked card. Left click on the empty board clears the picks, dragging there draws a selection box.
 * Dragging an output pulls a wire: dropped on a card it goes into the pin under the cursor or the
 * first fitting one, dropped on the empty board it opens the add menu and wires the new node in; a
 * wired input picks its wire up again. Middle, or Alt with the left, pans; the wheel zooms round the
 * cursor; right click opens the add menu; Delete removes the picked cards. A variable dragged in from
 * the left panel becomes a Get or a Set node, see {@link #dropVariable}.</p>
 */
public class UIFlowGraph extends UIElement
{
    public static final int NODE_W = 160;
    public static final int PILL_W = 124;
    public static final int HEADER = 16;
    public static final int ROW = 12;
    private static final int GRID = 16;

    /** Where a pin sits on a card: its name, whether it carries the run, and its row. */
    public record Spot(String pin, boolean exec, int row) {}

    /** A card's shape. */
    public static class Layout
    {
        public final List<Spot> left = new ArrayList<>();
        public final List<Spot> right = new ArrayList<>();
        public int rows;
        public boolean grow;
        public int settings;
        public float width = NODE_W;
        public boolean pill;
    }

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
    private boolean wireData;
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

    public void focus(FlowNode node)
    {
        Layout layout = layout(node);

        this.panX = this.area.w / 2F - (node.x + layout.width / 2F) * this.zoom;
        this.panY = this.area.h / 2F - (node.y + height(node) / 2F) * this.zoom;
        this.select(node);
    }

    /* Layout */

    public Layout layout(FlowNode node)
    {
        Layout layout = new Layout();
        FlowNodeType type = node.getType();

        if (type == null)
        {
            layout.rows = 1;

            return layout;
        }

        if (node.type.equals(FlowNodeTypes.GET_VARIABLE))
        {
            layout.pill = true;
            layout.width = PILL_W;
            layout.right.add(new Spot("value", false, 0));

            return layout;
        }

        if ((node.type.equals(FlowNodeTypes.SET_VARIABLE) || node.type.equals(FlowNodeTypes.ADD_VARIABLE)) && this.graph != null && this.graph.variableOf(node) == null)
        {
            layout.pill = true;
            layout.width = PILL_W + 24;
            layout.left.add(new Spot(FlowNodeType.EXEC, true, 0));
            layout.right.add(new Spot("then", true, 0));

            return layout;
        }

        int row = 0;

        for (String pin : type.execInputs) layout.left.add(new Spot(pin, true, row++));
        for (String pin : node.dataInputs()) layout.left.add(new Spot(pin, false, row++));

        row = 0;

        for (String pin : node.execOutputs()) layout.right.add(new Spot(pin, true, row++));
        for (FlowNodeType.DataPin pin : type.dataOutputs) layout.right.add(new Spot(pin.id(), false, row++));

        layout.rows = Math.max(1, Math.max(layout.left.size(), layout.right.size()));
        layout.grow = type.growable() || type.growableOutputs();

        for (FlowNodeType.Param param : type.params)
        {
            if (!param.id().equals("variable"))
            {
                layout.settings += 1;
            }
        }

        return layout;
    }

    public int height(FlowNode node)
    {
        Layout layout = layout(node);

        if (layout.pill)
        {
            return 18;
        }

        return HEADER + 4 + (layout.rows + (layout.grow ? 1 : 0) + layout.settings) * ROW + (layout.settings > 0 ? 6 : 4);
    }

    private float rowY(FlowNode node, int row)
    {
        if (layout(node).pill)
        {
            return node.y + 9;
        }

        return node.y + HEADER + 4 + ROW / 2F + row * ROW;
    }

    private static Spot spot(List<Spot> spots, String pin)
    {
        for (Spot spot : spots)
        {
            if (spot.pin().equals(pin))
            {
                return spot;
            }
        }

        return null;
    }

    private float outY(FlowNode node, String pin)
    {
        Spot spot = spot(layout(node).right, pin);

        return rowY(node, spot == null ? 0 : spot.row());
    }

    private float inY(FlowNode node, String pin)
    {
        Spot spot = spot(layout(node).left, pin);

        return rowY(node, spot == null ? 0 : spot.row());
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

            if (gx >= node.x && gx <= node.x + layout(node).width && gy >= node.y && gy <= node.y + height(node))
            {
                return node;
            }
        }

        return null;
    }

    /** The pin under a point on a card's edge, or null. */
    private Spot pinAt(FlowNode node, float gx, float gy, boolean right, float reach)
    {
        Layout layout = layout(node);
        float x = right ? node.x + layout.width : node.x;

        if (Math.abs(gx - x) > reach)
        {
            return null;
        }

        for (Spot spot : right ? layout.right : layout.left)
        {
            if (Math.abs(gy - rowY(node, spot.row())) <= 6)
            {
                return spot;
            }
        }

        return null;
    }

    /** -1 or +1 on an input grow button, -2 or +2 on an output one, 0 elsewhere. */
    private int growButtonAt(FlowNode node, float gx, float gy)
    {
        Layout layout = layout(node);
        FlowNodeType type = node.getType();

        if (!layout.grow || type == null || Math.abs(gy - rowY(node, layout.rows)) > 5)
        {
            return 0;
        }

        if (type.growable())
        {
            if (gx >= node.x + 6 && gx <= node.x + 18) return 1;
            if (gx >= node.x + 20 && gx <= node.x + 32) return -1;
        }

        if (type.growableOutputs())
        {
            float ex = node.x + layout.width;

            if (gx >= ex - 32 && gx <= ex - 20) return 2;
            if (gx >= ex - 18 && gx <= ex - 6) return -2;
        }

        return 0;
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
        boolean data = this.wireData;

        this.search.relative(this).xy(x, y).wh(UIFlowSearch.WIDTH, UIFlowSearch.HEIGHT);
        this.search.resize();
        this.search.open((type) ->
        {
            FlowNode node = this.graph.add(type.id, this.addX, this.addY);

            if (from != null && data && !node.dataInputs().isEmpty())
            {
                this.graph.linkData(from.id, pin, node.id, node.dataInputs().get(0));
            }
            else if (from != null && !data && !type.execInputs.isEmpty())
            {
                this.graph.link(from.id, pin, node.id, type.execInputs.get(0));
            }

            this.select(node);
            this.onChange.run();
        });
    }

    /* Variables dragged in from the left panel */

    /**
     * A variable dropped on the board. On a data input it becomes a Get wired straight into it; with
     * Ctrl a Get, with Alt a Set, right where it was dropped; otherwise a small menu asks which.
     */
    public void dropVariable(FlowVariable variable, int mouseX, int mouseY, boolean ctrl, boolean alt)
    {
        if (this.graph == null || !this.area.isInside(mouseX, mouseY))
        {
            return;
        }

        float gx = this.toGraphX(mouseX);
        float gy = this.toGraphY(mouseY);

        for (int i = this.graph.nodes.size() - 1; i >= 0; i--)
        {
            FlowNode target = this.graph.nodes.get(i);
            Spot spot = this.pinAt(target, gx, gy, false, 10);

            if (spot != null && !spot.exec())
            {
                FlowNode get = this.addVariableNode(variable, FlowNodeTypes.GET_VARIABLE, target.x - PILL_W - 30, rowY(target, spot.row()) - 9);

                this.graph.linkData(get.id, "value", target.id, spot.pin());
                this.onChange.run();

                return;
            }
        }

        if (ctrl || alt)
        {
            this.addVariableNode(variable, ctrl ? FlowNodeTypes.GET_VARIABLE : FlowNodeTypes.SET_VARIABLE, gx, gy);
            this.onChange.run();

            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.DOWNLOAD, L10n.lang("bbs.ui.trigger_box.get_named").format(variable.name), () ->
            {
                this.addVariableNode(variable, FlowNodeTypes.GET_VARIABLE, gx, gy);
                this.onChange.run();
            });
            menu.action(Icons.UPLOAD, L10n.lang("bbs.ui.trigger_box.set_named").format(variable.name), () ->
            {
                this.addVariableNode(variable, FlowNodeTypes.SET_VARIABLE, gx, gy);
                this.onChange.run();
            });
        });
    }

    private FlowNode addVariableNode(FlowVariable variable, String type, float x, float y)
    {
        FlowNode node = this.graph.add(type, Math.round(x), Math.round(y));

        node.params.put("variable", variable.id);

        if (type.equals(FlowNodeTypes.SET_VARIABLE))
        {
            node.params.put("value", variable.value);
        }

        this.select(node);

        return node;
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

        /* Pins and grow buttons first: they sit on and past the card's edges */
        for (int i = this.graph.nodes.size() - 1; i >= 0; i--)
        {
            FlowNode candidate = this.graph.nodes.get(i);
            Spot out = this.pinAt(candidate, gx, gy, true, 7);

            if (out != null)
            {
                this.startWire(candidate, out.pin(), !out.exec(), gx, gy);

                return true;
            }

            Spot in = this.pinAt(candidate, gx, gy, false, 7);

            if (in != null && this.pickUp(candidate, in, gx, gy))
            {
                return true;
            }

            int grow = this.growButtonAt(candidate, gx, gy);

            if (grow != 0)
            {
                if (Math.abs(grow) == 1)
                {
                    this.graph.setInputCount(candidate, candidate.inputCount() + grow);
                }
                else
                {
                    this.graph.setOutputCount(candidate, candidate.outputCount() + grow / 2);
                }

                this.select(candidate);
                this.onChange.run();

                return true;
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

            this.draggingNodes = true;
            this.dragOffsets.clear();

            for (FlowNode p : this.picked)
            {
                this.dragOffsets.put(p, new float[] {gx - p.x, gy - p.y});
            }

            this.graph.nodes.remove(node);
            this.graph.nodes.add(node);

            return true;
        }

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

    private void startWire(FlowNode from, String pin, boolean data, float gx, float gy)
    {
        this.wireFrom = from;
        this.wirePin = pin;
        this.wireData = data;
        this.wireX = gx;
        this.wireY = gy;
    }

    /** A wired input picks its wire up again, to be dropped elsewhere. */
    private boolean pickUp(FlowNode node, Spot in, float gx, float gy)
    {
        for (FlowLink link : this.graph.links)
        {
            if (link.to() == node.id && link.toPin().equals(in.pin()))
            {
                FlowNode from = this.graph.get(link.from());

                this.graph.links.remove(link);
                this.startWire(from, link.pin(), !in.exec(), gx, gy);
                this.onChange.run();

                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.wireFrom != null)
        {
            float gx = this.toGraphX(context.mouseX);
            float gy = this.toGraphY(context.mouseY);
            FlowNode target = this.nodeAt(gx, gy);

            if (target == null)
            {
                /* Just past a card's left edge, on its pins, still counts */
                for (FlowNode node : this.graph.nodes)
                {
                    if (this.pinAt(node, gx, gy, false, 10) != null)
                    {
                        target = node;
                    }
                }
            }

            if (target != null && target != this.wireFrom)
            {
                this.drop(target, gx, gy);
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

    /** A wire dropped on a card: the fitting pin under the cursor, else the first fitting free one. */
    private void drop(FlowNode target, float gx, float gy)
    {
        Spot under = this.pinAt(target, gx, gy, false, layout(target).width);
        List<Spot> fitting = new ArrayList<>();

        for (Spot spot : layout(target).left)
        {
            if (spot.exec() != this.wireData)
            {
                fitting.add(spot);
            }
        }

        if (fitting.isEmpty())
        {
            return;
        }

        Spot chosen = under != null && under.exec() != this.wireData ? under : null;

        if (chosen == null)
        {
            for (Spot spot : fitting)
            {
                if (this.wireData ? this.graph.dataInto(target.id, spot.pin()) == null : spot.pin().equals(FlowNodeType.EXEC))
                {
                    chosen = spot;

                    break;
                }
            }
        }

        if (chosen == null)
        {
            chosen = fitting.get(0);
        }

        if (this.wireData)
        {
            this.graph.linkData(this.wireFrom.id, this.wirePin, target.id, chosen.pin());
        }
        else
        {
            this.graph.link(this.wireFrom.id, this.wirePin, target.id, chosen.pin());
        }
    }

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
            if (node.x <= x2 && node.x + layout(node).width >= x1 && node.y <= y2 && node.y + height(node) >= y1)
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

    /** Copies the picked cards, with the wires that run between two of them, a little down and right; the copies are picked. */
    public void duplicatePicked()
    {
        if (this.picked.isEmpty())
        {
            return;
        }

        Map<Integer, FlowNode> copies = new HashMap<>();

        for (FlowNode node : this.picked)
        {
            FlowNode copy = this.graph.add(node.type, node.x + 30, node.y + 30);

            copy.params.clear();
            copy.params.putAll(node.params);
            copies.put(node.id, copy);
        }

        for (FlowLink link : new ArrayList<>(this.graph.links))
        {
            FlowNode from = copies.get(link.from());
            FlowNode to = copies.get(link.to());

            if (from != null && to != null)
            {
                this.graph.links.add(new FlowLink(from.id, link.pin(), to.id, link.toPin()));
            }
        }

        this.picked.clear();
        this.picked.addAll(copies.values());
        this.setPrimary(copies.values().iterator().next());
        this.onChange.run();
    }

    public void selectAll()
    {
        this.picked.clear();
        this.picked.addAll(this.graph.nodes);
        this.setPrimary(this.graph.nodes.isEmpty() ? null : this.graph.nodes.get(this.graph.nodes.size() - 1));
    }

    public void deletePicked()
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
        if (!this.picked.contains(node))
        {
            this.select(node);
        }

        this.duplicatePicked();
    }

    /* Labels */

    /** What a pin is called on its card. */
    private String pinLabel(FlowNode node, String pin, boolean exec)
    {
        FlowNodeType type = node.getType();

        if (exec && pin.startsWith(FlowNodeType.EXEC + ":"))
        {
            return L10n.lang("bbs.ui.trigger_box.pin." + pin.substring(FlowNodeType.EXEC.length() + 1)).get();
        }

        if (type != null && type.growableOutputs() && pin.startsWith(type.growOutputPrefix) && pin.length() > type.growOutputPrefix.length() && Character.isDigit(pin.charAt(type.growOutputPrefix.length())))
        {
            int index = (int) FlowValues.parse(pin.substring(type.growOutputPrefix.length()), 0);

            if (node.type.equals("switch_int"))
            {
                return "= " + ((int) FlowValues.parse(node.param("start"), 0) + index);
            }

            if (type.outputParam != null && type.outputParam.equals("case"))
            {
                return "= " + node.param("case" + index);
            }

            String label = L10n.lang("bbs.ui.trigger_box.pin." + type.growOutputPrefix).get() + " " + index;

            return type.outputParam != null ? label + " (" + node.param(type.outputParam + index) + ")" : label;
        }

        if (!exec && pin.startsWith("in") && pin.length() > 2 && Character.isDigit(pin.charAt(2)) && type != null && type.dataInput(pin) == null)
        {
            return String.valueOf((char) ('A' + (int) FlowValues.parse(pin.substring(2), 0)));
        }

        return L10n.lang("bbs.ui.trigger_box.pin." + pin).get();
    }

    public String title(FlowNode node)
    {
        FlowNodeType type = node.getType();

        if (type == null)
        {
            return node.type;
        }

        if (node.type.equals(FlowNodeTypes.GET_VARIABLE) || node.type.equals(FlowNodeTypes.SET_VARIABLE) || node.type.equals(FlowNodeTypes.ADD_VARIABLE))
        {
            FlowVariable variable = this.graph.variableOf(node);
            String name = variable == null ? L10n.lang("bbs.ui.trigger_box.invalid_variable").get() : variable.name;

            return switch (node.type)
            {
                case FlowNodeTypes.SET_VARIABLE -> L10n.lang("bbs.ui.trigger_box.set_title").get() + "  " + name;
                case FlowNodeTypes.ADD_VARIABLE -> L10n.lang("bbs.ui.trigger_box.add_title").get() + "  " + name;
                default -> name;
            };
        }

        return UIFlowSearch.name(type);
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

        if (this.wireFrom != null && this.wireFrom.getType() != null)
        {
            FlowLines lines = new FlowLines(context);
            int color = this.wireData ? FlowTheme.typeColor(this.graph.outputType(this.wireFrom, this.wirePin)) : FlowTheme.accent();

            lines.liveWire(this.wireFrom.x + layout(this.wireFrom).width + 4, outY(this.wireFrom, this.wirePin), this.wireX, this.wireY, 2.2F, color, color, time);
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

            boolean hot = this.picked.contains(a) || this.picked.contains(b);
            float x1 = a.x + layout(a).width + 4;
            float y1 = outY(a, link.pin());
            float x2 = b.x - 4;
            float y2 = inY(b, link.toPin());

            if (!link.isExec())
            {
                int color = FlowTheme.typeColor(this.graph.outputType(a, link.pin()));

                lines.liveWire(x1, y1, x2, y2, hot ? 2F : 1.5F, color, color, time);
            }
            else
            {
                lines.liveWire(x1, y1, x2, y2, hot ? 2.4F : 1.8F, hot ? accent : wire, hot ? accent : a.getType().kind.color, time);
            }
        }

        lines.end();
    }

    private void renderNode(UIContext context, FlowNode node)
    {
        FlowNodeType type = node.getType();
        Layout layout = layout(node);

        if (layout.pill)
        {
            this.renderPill(context.batcher, node, layout);

            return;
        }

        Batcher2D batcher = context.batcher;
        int color = type == null ? 0xff555555 : type.kind.color;
        int r = FlowTheme.radius();
        float x = node.x;
        float y = node.y;
        float ex = x + layout.width;
        float ey = y + height(node);
        boolean picked = this.picked.contains(node);
        int border = picked ? 2 : 1;
        boolean invalid = this.invalid(node);

        if (invalid)
        {
            color = 0xffb03030;
        }

        FlowTheme.rounded(batcher, x + 2, y + 3, ex + 2, ey + 3, r, 0x50000000, true, true);
        FlowTheme.rounded(batcher, x - border, y - border, ex + border, ey + border, r + border, picked ? FlowTheme.accent() : FlowTheme.border(), true, true);
        FlowTheme.rounded(batcher, x, y, ex, ey, r, FlowTheme.body(), true, true);
        FlowTheme.roundedHeader(batcher, x, y, ex, y + HEADER, r, color, Colors.mulRGB(color, 0.55F) | 0xff000000);

        if (FlowTheme.gloss())
        {
            FlowTheme.roundedHeader(batcher, x + 1, y + 1, ex - 1, y + HEADER / 2F, Math.max(0, r - 1), 0x38ffffff, 0x10ffffff);
        }

        batcher.box(x, y + HEADER, ex, y + HEADER + 1, 0x40000000);

        if (type == null)
        {
            batcher.textShadow(node.type, x + 6, y + 4, Colors.WHITE);

            return;
        }

        String kind = UIFlowSearch.kindLabel(type.kind).get().toUpperCase();
        int kw = batcher.getFont().getWidth(kind);
        String title = batcher.getFont().limitToWidth(this.title(node), (int) layout.width - kw - 22);

        batcher.textShadow(title, x + 6, y + 4, Colors.WHITE);
        FlowTheme.rounded(batcher, ex - kw - 10, y + 3, ex - 3, y + HEADER - 3, 3, 0x40000000, true, true);
        batcher.text(kind, ex - kw - 6, y + 4, 0xd0ffffff);

        /* Left column: run inputs, then data inputs with what they read while unwired */
        for (Spot spot : layout.left)
        {
            float py = rowY(node, spot.row());

            if (spot.exec())
            {
                this.renderPin(batcher, x, py, this.isWiredInto(node, spot.pin()), color);

                if (!spot.pin().equals(FlowNodeType.EXEC))
                {
                    batcher.text(this.pinLabel(node, spot.pin(), true), x + 8, py - 4, FlowTheme.text());
                }

                continue;
            }

            boolean wired = this.graph.dataInto(node.id, spot.pin()) != null;
            String label = this.pinLabel(node, spot.pin(), false);

            if (!wired)
            {
                label += ": " + node.param(spot.pin());
            }

            this.renderDataPin(batcher, x, py, wired, FlowTheme.typeColor(this.graph.inputType(node, spot.pin())));
            batcher.text(batcher.getFont().limitToWidth(label, (int) layout.width / 2 + 10), x + 8, py - 4, wired ? FlowTheme.text() : FlowTheme.dim());
        }

        /* Right column: run outputs, then data outputs */
        for (Spot spot : layout.right)
        {
            float py = rowY(node, spot.row());
            boolean single = layout.right.size() == 1 && spot.pin().equals("then");

            if (spot.exec())
            {
                this.renderPin(batcher, ex, py, this.graph.execFrom(node.id, spot.pin()) != null, color);
            }
            else
            {
                this.renderDataPin(batcher, ex, py, this.isDataOutWired(node, spot.pin()), FlowTheme.typeColor(this.graph.outputType(node, spot.pin())));
            }

            if (!single)
            {
                String label = batcher.getFont().limitToWidth(this.pinLabel(node, spot.pin(), spot.exec()), (int) layout.width / 2);

                batcher.text(label, ex - 9 - batcher.getFont().getWidth(label), py - 4, FlowTheme.text());
            }
        }

        if (layout.grow)
        {
            float gy = rowY(node, layout.rows);

            if (type.growable())
            {
                this.renderGrowButton(batcher, x + 6, gy, "+", true);
                this.renderGrowButton(batcher, x + 20, gy, "-", node.inputCount() > type.minInputs);
            }

            if (type.growableOutputs())
            {
                this.renderGrowButton(batcher, ex - 32, gy, "+", true);
                this.renderGrowButton(batcher, ex - 18, gy, "-", node.outputCount() > type.minOutputs);
            }
        }

        /* Settings, read only on the card: they are edited in Details */
        float py = y + HEADER + 4 + (layout.rows + (layout.grow ? 1 : 0)) * ROW;

        if (layout.settings > 0)
        {
            FlowTheme.rounded(batcher, x + 4, py, ex - 4, py + layout.settings * ROW + 2, Math.min(3, r), (FlowTheme.field() & 0xffffff) | 0xa0000000, true, true);
        }

        for (FlowNodeType.Param param : type.params)
        {
            if (param.id().equals("variable"))
            {
                continue;
            }

            String label = L10n.lang("bbs.ui.trigger_box.param." + param.id()).get() + ": ";
            String value = node.param(param.id());
            int labelW = batcher.getFont().getWidth(label);

            batcher.text(label, x + 8, py + 2, FlowTheme.dim());
            batcher.text(batcher.getFont().limitToWidth(value.isEmpty() ? "-" : value, (int) layout.width - 16 - labelW), x + 8 + labelW, py + 2, FlowTheme.text());
            py += ROW;
        }
    }

    /** A variable read: a small pill, its variable's colour dot and name, a data pin of its type. */
    private void renderPill(Batcher2D batcher, FlowNode node, Layout layout)
    {
        float x = node.x;
        float y = node.y;
        float ex = x + layout.width;
        float ey = y + 18;
        boolean picked = this.picked.contains(node);
        boolean invalid = this.invalid(node);
        FlowVariable variable = this.graph.variableOf(node);
        int typeColor = invalid || variable == null ? 0xffb03030 : FlowTheme.typeColor(variable.type);

        FlowTheme.rounded(batcher, x + 2, y + 3, ex + 2, ey + 3, 9, 0x50000000, true, true);
        FlowTheme.rounded(batcher, x - 1, y - 1, ex + 1, ey + 1, 10, picked ? FlowTheme.accent() : (typeColor & 0xffffff) | 0xc0000000, true, true);
        FlowTheme.rounded(batcher, x, y, ex, ey, 9, FlowTheme.body(), true, true);

        if (FlowTheme.gloss())
        {
            FlowTheme.rounded(batcher, x + 3, y + 1, ex - 3, y + 8, 6, 0x22ffffff, true, false);
        }

        float textX = layout.left.isEmpty() ? x + 18 : x + 24;

        FlowTheme.rounded(batcher, textX - 12, y + 5, textX - 4, y + 13, 4, typeColor, true, true);
        batcher.text(batcher.getFont().limitToWidth(this.title(node), (int) (ex - textX) - 14), textX, y + 5, invalid ? 0xffff7070 : FlowTheme.text());

        for (Spot spot : layout.left)
        {
            this.renderPin(batcher, x, y + 9, this.isWiredInto(node, spot.pin()), typeColor);
        }

        for (Spot spot : layout.right)
        {
            if (spot.exec())
            {
                this.renderPin(batcher, ex, y + 9, this.graph.execFrom(node.id, spot.pin()) != null, typeColor);
            }
            else
            {
                this.renderDataPin(batcher, ex, y + 9, this.isDataOutWired(node, spot.pin()), typeColor);
            }
        }
    }

    private boolean invalid(FlowNode node)
    {
        FlowNodeType type = node.getType();

        return type != null && type.params.stream().anyMatch((p) -> p.id().equals("variable")) && this.graph.variableOf(node) == null;
    }

    private boolean isWiredInto(FlowNode node, String pin)
    {
        for (FlowLink link : this.graph.links)
        {
            if (link.to() == node.id && link.toPin().equals(pin))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isDataOutWired(FlowNode node, String pin)
    {
        for (FlowLink link : this.graph.links)
        {
            if (!link.isExec() && link.from() == node.id && link.pin().equals(pin))
            {
                return true;
            }
        }

        return false;
    }

    private void renderDataPin(Batcher2D batcher, float cx, float cy, boolean wired, int color)
    {
        FlowTheme.rounded(batcher, cx - 4, cy - 4, cx + 4, cy + 4, 4, color, true, true);

        if (!wired)
        {
            FlowTheme.rounded(batcher, cx - 2.5F, cy - 2.5F, cx + 2.5F, cy + 2.5F, 2, FlowTheme.body(), true, true);
        }
    }

    private void renderGrowButton(Batcher2D batcher, float x, float cy, String sign, boolean enabled)
    {
        FlowTheme.rounded(batcher, x, cy - 5, x + 12, cy + 5, 3, enabled ? (FlowTheme.accent() & 0xffffff) | 0xc0000000 : 0x30808080, true, true);
        batcher.text(sign, x + 6 - batcher.getFont().getWidth(sign) / 2F, cy - 4, enabled ? Colors.WHITE : FlowTheme.dim());
    }

    /** A run pin: a round socket with an arrow head pointing the way the flow goes, lit when wired. */
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
    }
}
