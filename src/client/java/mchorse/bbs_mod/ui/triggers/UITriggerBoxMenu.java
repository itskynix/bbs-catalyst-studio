package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import mchorse.bbs_mod.client.renderer.TriggerBoxRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.triggers.FlowGraph;
import mchorse.bbs_mod.triggers.FlowJavaGenerator;
import mchorse.bbs_mod.triggers.FlowNode;
import mchorse.bbs_mod.triggers.FlowNodeType;
import mchorse.bbs_mod.triggers.FlowNodeTypes;
import mchorse.bbs_mod.triggers.FlowVariable;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The trigger box editor, laid out in three columns. Left: the components, the variables of the flow
 * with their types and starting values, and the events. Middle: the Viewport tab with the box's size
 * and a live preview, and the Flow tab, which the Node | Code switch in its top right corner shows as
 * the node board or as the same flow written out in Java. Right: Details of the picked node, or of
 * the box when no node is picked.
 *
 * <p>Edits go straight into the block entity on this side, so the box in the world follows every
 * change; closing the editor, or the save button, sends the whole box to the server.</p>
 */
public class UITriggerBoxMenu extends UIBaseMenu
{
    private static final int TOP = 24;
    private static final int LEFT_W = 160;
    private static final int RIGHT_W = 180;

    private static int lastTab = 1;
    private static int lastMode;

    private final TriggerBoxBlockEntity box;
    private final FlowGraph graph;

    private UIFlowSegment tabs;
    private UIFlowSegment mode;
    private UIScrollView left;
    private UIScrollView details;
    private UIElement viewportTab;
    private final mchorse.bbs_mod.camera.OrbitDistanceCamera orbit = new mchorse.bbs_mod.camera.OrbitDistanceCamera();
    private final mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera uiOrbit = new mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera();
    private final mchorse.bbs_mod.camera.controller.OrbitCameraController orbitController = new mchorse.bbs_mod.camera.controller.OrbitCameraController(this.orbit);
    private boolean orbiting;
    private net.minecraft.client.option.Perspective perspective;
    private UIFlowGraph board;
    private UIFlowCode code;
    private UIIcon copy;

    private final List<Runnable> boxSync = new ArrayList<>();
    private boolean codeDirty = true;
    private int viewportSync;

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.trigger_box." + id);
    }

    public UITriggerBoxMenu(TriggerBoxBlockEntity box)
    {
        this.box = box;
        this.graph = box.graph;

        if (this.graph.nodes.isEmpty())
        {
            this.graph.add(FlowNodeTypes.ON_ENTER, 40, 60);
        }

        /* Top bar: tabs over the middle column, the Node | Code switch in its right corner, save and close */
        this.tabs = new UIFlowSegment(List.of(key("tab.viewport"), key("tab.flow")), List.of(Icons.BLOCK, Icons.GRAPH), false, (i) -> this.setTab(i));
        this.tabs.relative(this.viewport).x(LEFT_W).y(0).w(220).h(TOP);

        this.mode = new UIFlowSegment(List.of(key("mode.node"), key("mode.code")), List.of(Icons.GRAPH, Icons.CODE), true, (i) -> this.setMode(i));
        this.mode.relative(this.viewport).x(1F, -RIGHT_W - 156).y(3).w(148).h(18);

        UIIcon save = new UIIcon(Icons.SAVED, (b) -> this.saveWithNotice());
        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.closeThisMenu());

        save.tooltip(key("save"));
        close.tooltip(key("close"));
        save.relative(this.viewport).x(1F, -44).y(2).wh(20, 20);
        close.relative(this.viewport).x(1F, -22).y(2).wh(20, 20);

        /* Ctrl + S saves, and says so */
        this.main.keys().register(mchorse.bbs_mod.ui.Keys.SAVE, this::saveWithNotice);

        /* The orbit camera of the Viewport tab; it takes the mouse only where no panel does */
        this.orbit.setFovRoll(false);
        this.uiOrbit.orbit = this.orbit;
        this.uiOrbit.setControl(true);
        this.uiOrbit.setEnabled(false);

        /* Left column */
        this.left = UI.scrollView(4, 6);
        this.left.relative(this.viewport).x(0).y(TOP).w(LEFT_W).h(1F, -TOP);

        /* Right column, before the board: picking a node (setGraph picks none) fills it */
        this.details = UI.scrollView(4, 6);
        this.details.relative(this.viewport).x(1F, -RIGHT_W).y(TOP).w(RIGHT_W).h(1F, -TOP);

        /* Middle column */
        UIElement center = new UIElement();

        center.relative(this.viewport).x(LEFT_W).y(TOP).w(1F, -LEFT_W - RIGHT_W).h(1F, -TOP);

        this.board = new UIFlowGraph().callbacks(this::onSelect, this::onGraphChanged);
        this.board.full(center);

        this.code = new UIFlowCode();
        this.code.full(center);

        this.copy = new UIIcon(Icons.COPY, (b) ->
        {
            Window.setClipboard(this.code.getCode());
            UIUtils.playClick();
        });
        this.copy.tooltip(key("copy_code"));
        this.copy.relative(center).x(1F, -24).y(4).wh(20, 20);

        this.viewportTab = new UIElement();
        this.viewportTab.full(center);
        this.createViewportTab();
        this.viewportSync = this.boxSync.size();

        center.add(this.viewportTab, this.board, this.code, this.copy);

        this.main.add(this.uiOrbit);
        this.main.add(this.left, center, this.details, this.tabs, this.mode, save, close);

        this.tabs.setValue(lastTab);
        this.mode.setValue(lastMode);

        /* Everything the selection callback touches exists by now: the graph goes in last, and
         * its first (empty) pick fills Details with the box */
        this.board.setGraph(this.graph);
        this.applyVisibility();
        this.fillLeft();
        this.fillDetails(this.board.selected);
    }

    public TriggerBoxBlockEntity getBox()
    {
        return this.box;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);

        TriggerBoxRenderer.editing = this.box.getPos();
    }

    @Override
    public void onClose(UIBaseMenu nextMenu)
    {
        super.onClose(nextMenu);

        TriggerBoxRenderer.editing = null;
        this.setOrbiting(false);
        this.send();
    }

    private void send()
    {
        mchorse.bbs_mod.client.TriggerBoxClient.save(this.box);
    }

    /** Ctrl + S and the save button: sent to the server, with a notice on screen. */
    private void saveWithNotice()
    {
        this.send();
        this.context.notifySuccess(key("saved"));
        UIUtils.playClick();
    }

    /** The orbit camera on or off: on, it circles the box and the player is seen from outside; off, the view is the player's again. */
    private void setOrbiting(boolean orbiting)
    {
        if (this.orbiting == orbiting)
        {
            return;
        }

        this.orbiting = orbiting;
        this.uiOrbit.setEnabled(orbiting);

        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();

        if (orbiting)
        {
            this.aimOrbit();
            this.perspective = mc.options.getPerspective();
            mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            mchorse.bbs_mod.BBSModClient.getCameraController().add(this.orbitController);
        }
        else
        {
            mchorse.bbs_mod.BBSModClient.getCameraController().remove(this.orbitController);

            if (this.perspective != null)
            {
                mc.options.setPerspective(this.perspective);
            }
        }
    }

    /* Tabs and the switch */

    private void setTab(int tab)
    {
        lastTab = tab;
        this.applyVisibility();
    }

    private void setMode(int mode)
    {
        lastMode = mode;

        /* The switch is about the flow: using it from the Viewport takes you there */
        if (this.tabs.getValue() != 1)
        {
            this.tabs.setValue(1);
            lastTab = 1;
        }

        this.applyVisibility();
    }

    private void applyVisibility()
    {
        boolean flow = this.tabs.getValue() == 1;
        boolean codeView = this.mode.getValue() == 1;

        this.viewportTab.setVisible(!flow);
        this.setOrbiting(!flow && this.context != null);
        this.board.setVisible(flow && !codeView);
        this.code.setVisible(flow && codeView);
        this.copy.setVisible(flow && codeView);

        if (!flow || codeView)
        {
            this.board.search.close();
        }

        if (codeView)
        {
            this.refreshCode();
        }
    }

    private void refreshCode()
    {
        String name = "TriggerBox_" + this.box.getPos().getX() + "_" + this.box.getPos().getY() + "_" + this.box.getPos().getZ();

        this.code.setCode(FlowJavaGenerator.generate(this.graph, name.replace('-', 'm')));
        this.codeDirty = false;
    }

    private void onGraphChanged()
    {
        this.codeDirty = true;
        this.fillLeft();
    }

    private void onSelect(FlowNode node)
    {
        this.fillDetails(node);
    }

    /* Viewport tab */

    /**
     * The Viewport tab is the world itself: the middle stays open, an orbit camera circles the box
     * (drag to turn, wheel to zoom) and the box is drawn with a dashed outline only while this editor
     * is open. A strip at the bottom holds its size and offset.
     */
    private void createViewportTab()
    {
        UITrackpad x = this.boxTrackpad(() -> this.box.boxWidth, (v) -> this.box.boxWidth = v, 0.1, 64);
        UITrackpad y = this.boxTrackpad(() -> this.box.boxHeight, (v) -> this.box.boxHeight = v, 0.1, 64);
        UITrackpad z = this.boxTrackpad(() -> this.box.boxDepth, (v) -> this.box.boxDepth = v, 0.1, 64);
        UITrackpad ox = this.boxTrackpad(() -> this.box.offsetX, (v) -> this.box.offsetX = v, -64, 64);
        UITrackpad oy = this.boxTrackpad(() -> this.box.offsetY, (v) -> this.box.offsetY = v, -64, 64);
        UITrackpad oz = this.boxTrackpad(() -> this.box.offsetZ, (v) -> this.box.offsetZ = v, -64, 64);

        x.tooltip(key("box_width"));
        y.tooltip(key("box_height"));
        z.tooltip(key("box_depth"));

        UIElement strip = new UIElement()
        {
            @Override
            public void render(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                FlowTheme.rounded(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), FlowTheme.radius(), FlowTheme.panel(), true, true);

                super.render(context);
            }
        };

        UIElement controls = UI.column(4,
            UI.row(4, UI.label(key("viewport.size")).labelAnchor(0F, 0.5F), x, y, z),
            UI.row(4, UI.label(key("offset")).labelAnchor(0F, 0.5F), ox, oy, oz)
        );

        strip.relative(this.viewportTab).x(10).y(1F, -62).w(1F, -20).h(54);
        controls.relative(strip).x(6).y(4).w(1F, -12).h(1F, -8);
        strip.add(controls);

        UILabel hint = UI.label(key("viewport.hint")).color(Colors.WHITE).background(0x80000000);

        hint.relative(this.viewportTab).x(10).y(8).w(1F, -20).h(12);
        this.viewportTab.add(hint, strip);
    }

    /** Points the orbit camera at the middle of the box, from a little above, far enough to see all of it. */
    private void aimOrbit()
    {
        net.minecraft.util.math.Box volume = this.box.getWorldBox();
        double size = Math.max(Math.max(volume.getLengthX(), volume.getLengthY()), volume.getLengthZ());
        double distance = size * 1.6D + 3D;
        mchorse.bbs_mod.camera.Camera camera = new mchorse.bbs_mod.camera.Camera();
        net.minecraft.client.network.ClientPlayerEntity player = net.minecraft.client.MinecraftClient.getInstance().player;

        camera.position.set(volume.getCenter().x, volume.getCenter().y, volume.getCenter().z);
        camera.rotation.set(mchorse.bbs_mod.utils.MathUtils.toRad(25F), mchorse.bbs_mod.utils.MathUtils.toRad(player == null ? 0F : player.getYaw()), 0F);
        this.orbit.setup(camera);
        this.orbit.distance.setX((int) Math.round(Math.sqrt(distance * 50D)));
    }

    private UITrackpad boxTrackpad(java.util.function.Supplier<Float> getter, Consumer<Float> setter, double min, double max)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            setter.accept(v.floatValue());
            this.syncBox();
        });

        trackpad.limit(min, max).increment(0.25).values(0.25, 0.05, 1);
        trackpad.setValue(getter.get());
        this.boxSync.add(() -> trackpad.setValue(getter.get()));

        return trackpad;
    }

    private UIToggle boxToggle(IKey label, java.util.function.Supplier<Boolean> getter, Consumer<Boolean> setter)
    {
        UIToggle toggle = new UIToggle(label, getter.get(), (b) ->
        {
            setter.accept(b.getValue());
            this.syncBox();
        });

        this.boxSync.add(() -> toggle.setValue(getter.get()));

        return toggle;
    }

    private void syncBox()
    {
        for (Runnable runnable : this.boxSync)
        {
            runnable.run();
        }
    }

    /* Left column */

    private UILabel header(IKey label)
    {
        return UI.label(label).background(0x40000000);
    }

    /** A wide element with a 20 wide one at its right. */
    private UIElement pair(UIElement wide, UIElement side)
    {
        UIElement row = new UIElement();

        row.h(20);
        wide.relative(row).w(1F, -22).h(20);
        side.relative(row).x(1F, -20).wh(20, 20);
        row.add(wide, side);

        return row;
    }

    private void fillLeft()
    {
        this.left.removeAll();

        /* Components */
        UIButton volume = new UIButton(key("component.volume"), (b) ->
        {
            this.board.select(null);
            this.fillDetails(null);
        });

        this.left.add(this.header(key("components")), volume);

        /* Variables */
        UIIcon add = new UIIcon(Icons.ADD, (b) ->
        {
            int n = this.graph.variables.size() + 1;
            String name = "var" + n;

            while (this.graph.getVariable(name) != null)
            {
                name = "var" + (++n);
            }

            this.graph.variables.add(new FlowVariable(name, "integer", "0"));
            this.codeDirty = true;
            this.fillLeft();
            this.refreshDetails();
        });

        add.tooltip(key("add_variable"));
        this.left.add(this.pair(this.header(key("variables")), add));

        for (FlowVariable variable : this.graph.variables)
        {
            this.left.add(this.variableRow(variable));
        }

        if (this.graph.variables.isEmpty())
        {
            this.left.add(UI.label(key("no_variables")).color(0xff7d8794));
        }

        /* Events */
        this.left.add(this.header(key("events")));

        boolean any = false;

        for (FlowNode node : this.graph.nodes)
        {
            FlowNodeType type = node.getType();

            if (type == null || type.kind != FlowNodeType.Kind.EVENT)
            {
                continue;
            }

            UIButton button = new UIButton(IKey.constant(UIFlowSearch.name(type)), (b) ->
            {
                this.tabs.setValue(1);
                this.mode.setValue(0);
                lastTab = 1;
                lastMode = 0;
                this.applyVisibility();
                this.board.focus(node);
            });

            button.color(type.kind.color);
            this.left.add(button);
            any = true;
        }

        if (!any)
        {
            this.left.add(UI.label(key("no_events")).color(0xff7d8794));
        }

        this.left.resize();
    }

    private UIElement variableRow(FlowVariable variable)
    {
        UITextbox name = new UITextbox(64, (text) ->
        {
            String old = variable.name;
            String fresh = text.trim();

            if (fresh.isEmpty() || (this.graph.getVariable(fresh) != null && this.graph.getVariable(fresh) != variable))
            {
                return;
            }

            variable.name = fresh;

            /* The nodes that read or write it follow the new name */
            for (FlowNode node : this.graph.nodes)
            {
                if (old.equals(node.params.get("variable")))
                {
                    node.params.put("variable", fresh);
                }
            }

            this.codeDirty = true;
        });

        name.setText(variable.name);

        /* The type, picked from a drop down list; a new type resets the value and swaps its field */
        UIButton type = new UIButton(key("type." + variable.type), (b) -> this.context.replaceContextMenu((menu) ->
        {
            for (String t : FlowVariable.TYPES)
            {
                menu.action(Icons.CONVERT, key("type." + t), typeColor(t), () ->
                {
                    if (!t.equals(variable.type))
                    {
                        variable.type = t;
                        variable.value = FlowVariable.defaultValue(t);
                        this.codeDirty = true;
                        this.fillLeft();
                    }
                });
            }
        }));

        type.color(typeColor(variable.type));
        type.tooltip(key("variable_type"));

        UIIcon remove = new UIIcon(Icons.REMOVE, (b) ->
        {
            this.graph.variables.remove(variable);
            this.codeDirty = true;
            this.fillLeft();
            this.refreshDetails();
        });

        remove.tooltip(key("remove_variable"));

        UIElement column = UI.column(2, UI.row(2, name, type), this.pair(this.valueField(variable), remove));

        column.marginTop(4);

        return column;
    }

    /** The starting value's field, shaped by the type: a switch, a number, three numbers or text. */
    private UIElement valueField(FlowVariable variable)
    {
        Runnable changed = () -> this.codeDirty = true;

        switch (variable.type)
        {
            case "boolean" ->
            {
                boolean on = variable.value.trim().equals("true") || variable.value.trim().equals("1");

                return new UIToggle(key("value_true"), on, (b) ->
                {
                    variable.value = String.valueOf(b.getValue());
                    changed.run();
                });
            }
            case "integer", "float" ->
            {
                boolean integer = variable.type.equals("integer");
                UITrackpad trackpad = new UITrackpad((v) ->
                {
                    variable.value = integer ? String.valueOf(v.longValue()) : String.valueOf(v.floatValue());
                    changed.run();
                });

                if (integer)
                {
                    trackpad.limit(-1_000_000_000D, 1_000_000_000D, true);
                }

                trackpad.setValue(mchorse.bbs_mod.triggers.FlowValues.parse(variable.value, 0));
                trackpad.tooltip(key("variable_value"));

                return trackpad;
            }
            case "vec3" ->
            {
                String[] split = variable.value.trim().split("[ ,]+");
                double[] xyz = new double[3];
                List<UIElement> pads = new ArrayList<>();

                for (int i = 0; i < 3; i++)
                {
                    xyz[i] = i < split.length ? mchorse.bbs_mod.triggers.FlowValues.parse(split[i], 0) : 0;
                }

                for (int i = 0; i < 3; i++)
                {
                    int axis = i;
                    UITrackpad trackpad = new UITrackpad((v) ->
                    {
                        xyz[axis] = v;
                        variable.value = xyz[0] + " " + xyz[1] + " " + xyz[2];
                        changed.run();
                    });

                    trackpad.setValue(xyz[i]);
                    pads.add(trackpad);
                }

                return UI.row(1, pads.toArray(new UIElement[0]));
            }
            default ->
            {
                UITextbox textbox = new UITextbox(256, (text) ->
                {
                    variable.value = text;
                    changed.run();
                });

                textbox.setText(variable.value);
                textbox.placeholder(key(variable.type.equals("entity") ? "entity_placeholder" : "variable_value"));
                textbox.tooltip(key(variable.type.equals("entity") ? "entity_tooltip" : "variable_value"));

                return textbox;
            }
        }
    }

    public static int typeColor(String type)
    {
        return switch (type)
        {
            case "boolean" -> 0xffd2453f;
            case "integer" -> 0xff2fc4a0;
            case "float" -> 0xff8bd84a;
            case "string" -> 0xffe85ad6;
            case "vec3" -> 0xffeac241;
            default -> 0xff3aa1e8;
        };
    }

    /* Right column */

    private void refreshDetails()
    {
        this.fillDetails(this.board.selected);
    }

    private void fillDetails(FlowNode node)
    {
        /* A selection made while the menu is still being built: the constructor fills Details at its end */
        if (this.details == null)
        {
            return;
        }

        this.details.removeAll();
        /* The Details fields of a previous fill are gone: only the Viewport tab's stay in sync */
        this.boxSync.subList(this.viewportSync, this.boxSync.size()).clear();

        if (node == null || node.getType() == null)
        {
            this.fillBoxDetails();
        }
        else
        {
            this.fillNodeDetails(node);
        }

        this.details.resize();
    }

    private void fillBoxDetails()
    {
        UITrackpad w = this.boxTrackpad(() -> this.box.boxWidth, (v) -> this.box.boxWidth = v, 0.1, 64);
        UITrackpad h = this.boxTrackpad(() -> this.box.boxHeight, (v) -> this.box.boxHeight = v, 0.1, 64);
        UITrackpad d = this.boxTrackpad(() -> this.box.boxDepth, (v) -> this.box.boxDepth = v, 0.1, 64);
        UITrackpad ox = this.boxTrackpad(() -> this.box.offsetX, (v) -> this.box.offsetX = v, -64, 64);
        UITrackpad oy = this.boxTrackpad(() -> this.box.offsetY, (v) -> this.box.offsetY = v, -64, 64);
        UITrackpad oz = this.boxTrackpad(() -> this.box.offsetZ, (v) -> this.box.offsetZ = v, -64, 64);

        List<String> targets = List.of(TriggerBoxBlockEntity.TARGET_ALL_LIVING, TriggerBoxBlockEntity.TARGET_PLAYER_ONLY, TriggerBoxBlockEntity.TARGET_ACTOR_ID);
        UITextbox actor = new UITextbox(128, (text) -> this.box.actorId = text);
        UICirculate target = new UICirculate((b) ->
        {
            this.box.target = targets.get(b.getValue());
            actor.setEnabled(b.getValue() == 2);
        });

        for (String t : targets)
        {
            target.addLabel(key("target." + t));
        }

        target.setValue(Math.max(0, targets.indexOf(this.box.target)));
        actor.setText(this.box.actorId);
        actor.placeholder(key("actor_id"));
        actor.tooltip(key("actor_id_tooltip"));
        actor.setEnabled(this.box.target.equals(TriggerBoxBlockEntity.TARGET_ACTOR_ID));

        w.tooltip(key("box_width"));
        h.tooltip(key("box_height"));
        d.tooltip(key("box_depth"));

        String pos = this.box.getPos().getX() + ", " + this.box.getPos().getY() + ", " + this.box.getPos().getZ();

        this.details.add(
            this.header(key("details")),
            UI.label(IKey.constant(key("component.volume").get() + "  " + pos)).color(0xffffb547),
            UI.label(key("box_size")), UI.row(2, w, h, d),
            UI.label(key("offset")), UI.row(2, ox, oy, oz),
            UI.label(key("target")), target, actor
        );
    }

    private void fillNodeDetails(FlowNode node)
    {
        FlowNodeType type = node.getType();

        this.details.add(this.header(key("details")));
        this.details.add(UI.label(IKey.constant(UIFlowSearch.name(type) + "  #" + node.id)).color(type.kind.color));
        this.details.add(UI.label(key("node_desc." + type.id)).color(0xffa6aeb8));

        for (FlowNodeType.Param param : type.params)
        {
            this.details.add(UI.label(key("param." + param.id())));
            this.details.add(this.paramField(node, param));
        }

        if (type.params.stream().anyMatch((p) -> p.id().equals("command") || p.id().equals("text") || p.id().equals("message") || p.id().equals("value")))
        {
            this.details.add(UI.label(key("placeholders")).color(0xff7d8794));
        }

        UIButton remove = new UIButton(key("delete_node"), (b) ->
        {
            this.graph.remove(node);
            this.board.select(null);
            this.onGraphChanged();
        });

        remove.color(Colors.NEGATIVE);
        remove.marginTop(8);
        this.details.add(remove);
    }

    private UIElement paramField(FlowNode node, FlowNodeType.Param param)
    {
        String id = param.id();

        if (id.equals("variable"))
        {
            List<String> names = new ArrayList<>();

            for (FlowVariable variable : this.graph.variables)
            {
                names.add(variable.name);
            }

            if (names.isEmpty())
            {
                return UI.label(key("no_variables")).color(0xffff7070);
            }

            String current = node.param(id);

            if (!current.isEmpty() && !names.contains(current))
            {
                names.add(current);
            }

            UICirculate circulate = new UICirculate((b) ->
            {
                node.params.put(id, names.get(b.getValue()));
                this.codeDirty = true;
            });

            for (String name : names)
            {
                circulate.addLabel(IKey.constant(name));
            }

            if (current.isEmpty())
            {
                node.params.put(id, names.get(0));
                this.codeDirty = true;
            }

            circulate.setValue(Math.max(0, names.indexOf(node.param(id))));

            return circulate;
        }

        if (id.equals("operator"))
        {
            UICirculate circulate = new UICirculate((b) ->
            {
                node.params.put(id, FlowNodeTypes.OPERATORS.get(b.getValue()));
                this.codeDirty = true;
            });

            for (String op : FlowNodeTypes.OPERATORS)
            {
                circulate.addLabel(IKey.constant(op));
            }

            circulate.setValue(Math.max(0, FlowNodeTypes.OPERATORS.indexOf(node.param(id))));

            return circulate;
        }

        if (id.equals("ticks") || id.equals("interval"))
        {
            UITrackpad trackpad = new UITrackpad((v) ->
            {
                node.params.put(id, String.valueOf(v.intValue()));
                this.codeDirty = true;
            });

            trackpad.limit(id.equals("interval") ? 1 : 0, 72000, true);
            trackpad.setValue(mchorse.bbs_mod.triggers.FlowValues.parse(node.param(id), 20));

            return trackpad;
        }

        UITextbox textbox = new UITextbox(1000, (text) ->
        {
            node.params.put(id, text);
            this.codeDirty = true;
        });

        textbox.setText(node.param(id));

        return textbox;
    }

    /* Drawing */

    @Override
    protected void preRenderMenu(UIRenderingContext context)
    {
        super.preRenderMenu(context);

        if (this.codeDirty && this.code.isVisible())
        {
            this.refreshCode();
        }

        int w = this.width;
        int h = this.height;

        /* Top bar, side columns; the middle stays open on the Viewport tab so the world shows through */
        int divider = FlowTheme.border();

        context.batcher.box(0, 0, w, TOP, FlowTheme.chrome());
        context.batcher.box(0, TOP - 1, w, TOP, divider);
        context.batcher.box(0, TOP, LEFT_W, h, FlowTheme.panel());
        context.batcher.box(w - RIGHT_W, TOP, w, h, FlowTheme.panel());
        context.batcher.box(LEFT_W - 1, TOP, LEFT_W, h, divider);
        context.batcher.box(w - RIGHT_W, TOP, w - RIGHT_W + 1, h, divider);

        context.batcher.icon(Icons.BLOCK, FlowTheme.accent(), 4, 4);
        context.batcher.textShadow(key("title").get(), 24, 8, FlowTheme.text());
    }
}
