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
import mchorse.bbs_mod.triggers.FlowValues;
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
    /** The menu bar, above the tab bar. */
    private static final int BAR = 18;
    private static final int HEAD = BAR + TOP;

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

    /** The variable being dragged out of the left panel, or null. */
    private FlowVariable dragging;
    /** The variable open in Details, or null; a picked node takes Details over. */
    private FlowVariable selectedVariable;
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
        this.tabs.relative(this.viewport).x(LEFT_W).y(BAR).w(220).h(TOP);

        this.mode = new UIFlowSegment(List.of(key("mode.node"), key("mode.code")), List.of(Icons.GRAPH, Icons.CODE), true, (i) -> this.setMode(i));
        this.mode.relative(this.viewport).x(1F, -RIGHT_W - 156).y(BAR + 3).w(148).h(18);

        UIIcon save = new UIIcon(Icons.SAVED, (b) -> this.saveWithNotice());
        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.closeThisMenu());

        save.tooltip(key("save"));
        close.tooltip(key("close"));
        save.relative(this.viewport).x(1F, -44).y(BAR + 2).wh(20, 20);
        close.relative(this.viewport).x(1F, -22).y(BAR + 2).wh(20, 20);

        /* Shortcuts: Ctrl + S saves and says so; Ctrl + Z and Ctrl + Y walk the history; Ctrl + D copies the picked cards */
        this.main.keys().register(mchorse.bbs_mod.ui.Keys.SAVE, this::saveWithNotice);
        this.main.keys().register(mchorse.bbs_mod.ui.Keys.UNDO, this::undo);
        this.main.keys().register(mchorse.bbs_mod.ui.Keys.REDO, this::redo);
        this.main.keys().register(DUPLICATE, () -> this.board.duplicatePicked()).active(() -> this.board.isVisible());

        /* The orbit camera of the Viewport tab; it takes the mouse only where no panel does */
        this.orbit.setFovRoll(false);
        this.uiOrbit.orbit = this.orbit;
        this.uiOrbit.setControl(true);
        this.uiOrbit.setEnabled(false);

        /* Left column */
        this.left = UI.scrollView(4, 6);
        this.left.relative(this.viewport).x(0).y(HEAD).w(LEFT_W).h(1F, -HEAD);

        /* Right column, before the board: picking a node (setGraph picks none) fills it */
        this.details = UI.scrollView(4, 6);
        this.details.relative(this.viewport).x(1F, -RIGHT_W).y(HEAD).w(RIGHT_W).h(1F, -HEAD);

        /* Middle column */
        UIElement center = new UIElement();

        center.relative(this.viewport).x(LEFT_W).y(HEAD).w(1F, -LEFT_W - RIGHT_W).h(1F, -HEAD);

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
        this.main.add(this.left, center, this.details, this.tabs, this.mode, save, close, this.createMenuBar());

        /* Over everything: the variable being dragged, as a pill under the cursor */
        UIElement ghost = new UIElement()
        {
            @Override
            public void render(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                FlowVariable variable = UITriggerBoxMenu.this.dragging;

                if (variable != null)
                {
                    String label = variable.name + "   " + key("drag_hint").get();
                    int w = context.batcher.getFont().getWidth(label) + 22;
                    int x = context.mouseX + 10;
                    int y = context.mouseY + 6;

                    FlowTheme.rounded(context.batcher, x, y, x + w, y + 16, 8, FlowTheme.body(), true, true);
                    FlowTheme.rounded(context.batcher, x + 5, y + 4, x + 13, y + 12, 4, typeColor(variable.type), true, true);
                    context.batcher.text(label, x + 17, y + 4, FlowTheme.text());
                }

                super.render(context);
            }
        };

        ghost.full(this.viewport);
        this.main.add(ghost);

        this.tabs.setValue(lastTab);
        this.mode.setValue(lastMode);

        /* Everything the selection callback touches exists by now: the graph goes in last, and
         * its first (empty) pick fills Details with the box */
        this.board.setGraph(this.graph);
        this.applyVisibility();
        this.fillLeft();
        this.fillDetails(this.board.selected);
        this.snapshot();
    }

    /* Menu bar */

    private static final mchorse.bbs_mod.ui.utils.keys.KeyCombo DUPLICATE = new mchorse.bbs_mod.ui.utils.keys.KeyCombo("trigger_box_duplicate", key("duplicate"), org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);

    /** File, Edit and View, in the look of the dashboard's own menu bar. */
    private UIElement createMenuBar()
    {
        UIElement bar = new UIElement()
        {
            @Override
            public void render(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), FlowTheme.chrome());
                context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), FlowTheme.border());

                super.render(context);
            }
        };

        bar.relative(this.viewport).x(0).y(0).w(1F).h(BAR);

        int x = 4;

        for (Object[] entry : new Object[][] {{mchorse.bbs_mod.ui.UIKeys.MENU_FILE, (java.util.function.Consumer<mchorse.bbs_mod.ui.utils.context.ContextMenuManager>) this::fillFileMenu}, {mchorse.bbs_mod.ui.UIKeys.MENU_EDIT, (java.util.function.Consumer<mchorse.bbs_mod.ui.utils.context.ContextMenuManager>) this::fillEditMenu}, {key("menu.view"), (java.util.function.Consumer<mchorse.bbs_mod.ui.utils.context.ContextMenuManager>) this::fillViewMenu}})
        {
            IKey label = (IKey) entry[0];
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<mchorse.bbs_mod.ui.utils.context.ContextMenuManager> filler = (java.util.function.Consumer<mchorse.bbs_mod.ui.utils.context.ContextMenuManager>) entry[1];
            mchorse.bbs_mod.ui.dashboard.menu.UIMenuBarItem item = new mchorse.bbs_mod.ui.dashboard.menu.UIMenuBarItem(label, (b) -> this.context.replaceContextMenu(filler, b.area.x, b.area.ey() + 1));
            int width = mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D.getDefaultTextRenderer().getWidth(label.get()) + 16;

            item.relative(bar).x(x).y(0).wh(width, BAR);
            bar.add(item);
            x += width;
        }

        return bar;
    }

    private void fillFileMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        menu.action(Icons.SAVED, key("menu.save"), this::saveWithNotice);
        menu.action(Icons.COPY, key("menu.copy_flow"), () ->
        {
            Window.setClipboard(mchorse.bbs_mod.data.DataToString.toString(this.graph.toData(), true));
            this.context.notifySuccess(key("menu.copied"));
        });
        menu.action(Icons.PASTE, key("menu.paste_flow"), this::pasteFlow);
        menu.action(Icons.CODE, key("menu.copy_java"), () ->
        {
            this.refreshCode();
            Window.setClipboard(this.code.getCode());
            this.context.notifySuccess(key("menu.copied"));
        });
        menu.action(Icons.REFRESH, key("menu.reset_flow"), Colors.NEGATIVE, () ->
        {
            this.restore(FlowGraph.createDefault().toData());
            this.snapshot();
        });
        menu.action(Icons.CLOSE, key("close"), this::closeThisMenu);
    }

    private void fillEditMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        menu.action(Icons.UNDO, key("menu.undo"), this::undo);
        menu.action(Icons.REDO, key("menu.redo"), this::redo);
        menu.action(Icons.DUPE, key("menu.duplicate"), () -> this.board.duplicatePicked());
        menu.action(Icons.ALL_DIRECTIONS, key("menu.select_all"), () -> this.board.selectAll());
        menu.action(Icons.REMOVE, key("menu.delete_picked"), Colors.NEGATIVE, () -> this.board.deletePicked());
    }

    private void fillViewMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        menu.action(Icons.BLOCK, key("tab.viewport"), () ->
        {
            this.tabs.setValue(0);
            this.setTab(0);
        });
        menu.action(Icons.GRAPH, key("tab.flow"), () ->
        {
            this.tabs.setValue(1);
            this.setTab(1);
        });
        menu.action(Icons.GRAPH, key("mode.node"), () ->
        {
            this.mode.setValue(0);
            this.setMode(0);
        });
        menu.action(Icons.CODE, key("mode.code"), () ->
        {
            this.mode.setValue(1);
            this.setMode(1);
        });
    }

    /** A flow from the clipboard, as copied by File > Copy flow, replaces this one; a broken clipboard says so. */
    private void pasteFlow()
    {
        mchorse.bbs_mod.data.types.MapType data = mchorse.bbs_mod.data.DataToString.mapFromString(Window.getClipboard());

        if (data == null || !data.has("nodes"))
        {
            this.context.notifyError(key("menu.paste_failed"));

            return;
        }

        this.restore(data);
        this.snapshot();
    }

    /* History: whole flow snapshots, so any edit, wire or move can be walked back */

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private boolean historyDirty;
    private int quietTicks;

    /** An edit was made: the code view is stale, and the history takes a snapshot once the typing settles. */
    private void changed()
    {
        this.codeDirty = true;
        this.historyDirty = true;
        this.quietTicks = 0;
    }

    /** Puts the flow as it is now on the history, dropping whatever was undone past it. */
    private void snapshot()
    {
        String now = mchorse.bbs_mod.data.DataToString.toString(this.graph.toData());

        this.historyDirty = false;

        if (this.historyIndex >= 0 && this.history.get(this.historyIndex).equals(now))
        {
            return;
        }

        while (this.history.size() > this.historyIndex + 1)
        {
            this.history.remove(this.history.size() - 1);
        }

        this.history.add(now);

        if (this.history.size() > 100)
        {
            this.history.remove(0);
        }

        this.historyIndex = this.history.size() - 1;
    }

    private void undo()
    {
        if (this.historyDirty)
        {
            this.snapshot();
        }

        if (this.historyIndex > 0)
        {
            this.historyIndex -= 1;
            this.restore(mchorse.bbs_mod.data.DataToString.mapFromString(this.history.get(this.historyIndex)));
            this.context.notifyInfo(key("menu.undone"));
        }
    }

    private void redo()
    {
        if (this.historyIndex < this.history.size() - 1)
        {
            this.historyIndex += 1;
            this.restore(mchorse.bbs_mod.data.DataToString.mapFromString(this.history.get(this.historyIndex)));
            this.context.notifyInfo(key("menu.redone"));
        }
    }

    /** Loads a flow into this box's graph and rebuilds everything that shows it. */
    private void restore(mchorse.bbs_mod.data.types.MapType data)
    {
        if (data == null)
        {
            return;
        }

        String open = this.selectedVariable == null ? null : this.selectedVariable.id;

        this.graph.fromData(data);
        this.board.setGraph(this.graph);
        this.selectedVariable = open == null ? null : this.graph.getVariableById(open);
        this.codeDirty = true;
        this.historyDirty = false;
        this.fillLeft();
        this.fillDetails(null);
    }

    @Override
    public void update()
    {
        super.update();

        /* Typing settles after half a second without a change: then it goes on the history as one step */
        if (this.historyDirty && ++this.quietTicks >= 10)
        {
            this.snapshot();
        }
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        FlowVariable variable = this.dragging;

        if (variable != null)
        {
            this.dragging = null;

            /* Dropped on the Flow board: Ctrl a Get, Alt a Set, on a data input a Get wired in, else a small menu */
            if (this.board.isVisible() && this.board.area.isInside(mouseX, mouseY))
            {
                this.context.setMouse(mouseX, mouseY);
                this.board.dropVariable(variable, mouseX, mouseY, Window.isCtrlPressed(), Window.isAltPressed());
            }

            return true;
        }

        return super.mouseReleased(mouseX, mouseY, mouseButton);
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
        this.snapshot();
        this.fillLeft();
        this.refreshDetails();
    }

    private void onSelect(FlowNode node)
    {
        if (node != null)
        {
            this.selectedVariable = null;
        }

        this.fillDetails(node);
    }

    /* Viewport tab */

    /**
     * The Viewport tab is the world itself: the middle stays open, an orbit camera circles the box
     * (drag to turn, wheel to zoom) and the box is drawn with a dashed outline only while this editor
     * is open. Its size and offset live in Details on the right, the one place they are edited.
     */
    private void createViewportTab()
    {
        UILabel hint = UI.label(key("viewport.hint")).color(Colors.WHITE).background(0x80000000);

        hint.relative(this.viewportTab).x(10).y(8).w(1F, -20).h(12);
        this.viewportTab.add(hint);
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
            this.selectedVariable = null;
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

            FlowVariable fresh = new FlowVariable(name, "integer", "0");

            this.graph.variables.add(fresh);
            this.changed();
            this.fillLeft();
            this.selectVariable(fresh);
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

    /**
     * A variable in the left panel, one row: the grip to drag it onto the board, its name with its
     * type and a [] for lists, and the remove button. Clicking the row opens it in Details.
     */
    private UIElement variableRow(FlowVariable variable)
    {
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeVariable(variable));

        remove.tooltip(key("remove_variable"));

        /* The grip: drag it onto the board for a Get or a Set */
        UIElement grip = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    UITriggerBoxMenu.this.dragging = variable;

                    return true;
                }

                return false;
            }

            @Override
            public void render(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                int color = typeColor(variable.type);

                FlowTheme.rounded(context.batcher, this.area.x + 2, this.area.y + 4, this.area.ex() - 2, this.area.ey() - 4, 3, (color & 0xffffff) | (this.area.isInside(context) ? 0xff000000 : 0xa0000000), true, true);
                context.batcher.icon(Icons.ALL_DIRECTIONS, Colors.WHITE, this.area.mx(), this.area.my(), 0.5F, 0.5F);

                super.render(context);
            }
        };

        grip.tooltip(key("drag_variable"));

        UIElement row = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    UITriggerBoxMenu.this.selectVariable(variable);
                    UIUtils.playClick();

                    return true;
                }

                return false;
            }

            @Override
            public void render(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                boolean picked = UITriggerBoxMenu.this.selectedVariable == variable;
                boolean hover = this.area.isInside(context);
                int x1 = this.area.x + 18;
                int x2 = this.area.ex() - 22;
                int edge = picked ? FlowTheme.accent() : hover ? FlowTheme.typeColor(variable.type) : FlowTheme.border();

                FlowTheme.rounded(context.batcher, x1, this.area.y, x2, this.area.ey(), 3, edge, true, true);
                FlowTheme.rounded(context.batcher, x1 + 1, this.area.y + 1, x2 - 1, this.area.ey() - 1, 2, picked ? FlowTheme.body() : FlowTheme.field(), true, true);

                String type = key("type." + variable.type).get() + (variable.isArray ? "[]" : "");
                int typeW = context.batcher.getFont().getWidth(type);
                String name = context.batcher.getFont().limitToWidth(variable.name, x2 - x1 - typeW - 14);

                context.batcher.text(name, x1 + 5, this.area.my() - 4, FlowTheme.text());
                context.batcher.text(type, x2 - 5 - typeW, this.area.my() - 4, typeColor(variable.type));

                super.render(context);
            }
        };

        row.h(20);
        row.tooltip(key("hint.variable_row"));
        grip.relative(row).w(16).h(20);
        remove.relative(row).x(1F, -20).wh(20, 20);
        row.add(grip, remove);
        row.marginTop(2);

        return row;
    }

    /** Opens the variable in Details, leaving the board with nothing picked. */
    private void selectVariable(FlowVariable variable)
    {
        this.selectedVariable = variable;
        this.board.select(null);
        this.selectedVariable = variable;
        this.fillDetails(null);
    }

    private void removeVariable(FlowVariable variable)
    {
        int used = this.graph.usages(variable);

        this.graph.variables.remove(variable);

        /* Its nodes keep the id and show as pointing at an invalid variable, never at another one */
        if (used > 0)
        {
            this.context.notifyError(key("variable_in_use").format(variable.name, used));
        }

        if (this.selectedVariable == variable)
        {
            this.selectedVariable = null;
        }

        this.changed();
        this.fillLeft();
        this.refreshDetails();
    }

    /** Details of a variable: its name, type, scope, whether it is a list, and its starting value or items. */
    private void fillVariableDetails(FlowVariable variable)
    {
        UITextbox name = new UITextbox(64, (text) ->
        {
            String fresh = text.trim();

            if (fresh.isEmpty() || (this.graph.getVariable(fresh) != null && this.graph.getVariable(fresh) != variable))
            {
                return;
            }

            /* Nodes hold the variable's id, so the new name shows on every one of them at once */
            variable.name = fresh;
            this.changed();
            this.fillLeft();
        });

        name.setText(variable.name);

        /* The type, picked from a drop down list; a new type resets the value (or the items) */
        UIComboBox type = new UIComboBox(key("type." + variable.type).get(), (t) -> {}, (callback) -> callback.accept(new ArrayList<>()), null)
        {
            @Override
            protected boolean subMouseClicked(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                if (!this.area.isInside(context) || context.mouseButton != 0)
                {
                    return false;
                }

                context.replaceContextMenu((menu) ->
                {
                    for (String t : FlowVariable.TYPES)
                    {
                        menu.action(t.equals(variable.type) ? Icons.CHECKMARK : Icons.CONVERT, key("type." + t), typeColor(t), () ->
                        {
                            if (!t.equals(variable.type))
                            {
                                variable.type = t;
                                variable.value = variable.isArray ? "" : FlowVariable.defaultValue(t);
                                UITriggerBoxMenu.this.changed();
                                UITriggerBoxMenu.this.fillLeft();
                                UITriggerBoxMenu.this.refreshDetails();
                            }
                        });
                    }
                }, this.area.x, this.area.ey());

                return true;
            }
        };

        /* Where its value lives: this box, the whole world, or each entity */
        UIComboBox scope = new UIComboBox(key("scope." + variable.scope).get(), (t) -> {}, (callback) -> callback.accept(new ArrayList<>()), null)
        {
            @Override
            protected boolean subMouseClicked(mchorse.bbs_mod.ui.framework.UIContext context)
            {
                if (!this.area.isInside(context) || context.mouseButton != 0)
                {
                    return false;
                }

                context.replaceContextMenu((menu) ->
                {
                    for (String sc : FlowVariable.SCOPES)
                    {
                        menu.action(sc.equals(variable.scope) ? Icons.CHECKMARK : Icons.GLOBE, key("scope." + sc), () ->
                        {
                            variable.scope = sc;
                            UITriggerBoxMenu.this.changed();
                            UITriggerBoxMenu.this.refreshDetails();
                        });
                    }
                }, this.area.x, this.area.ey());

                return true;
            }
        };

        UIToggle array = new UIToggle(key("is_array"), variable.isArray, (b) ->
        {
            /* Turning a value into a list keeps it as the first item; a list back into a value keeps its first item */
            List<String> items = FlowValues.list(variable.value);

            variable.isArray = b.getValue();
            variable.value = b.getValue() ? FlowValues.join(List.of(variable.value)) : items.isEmpty() ? FlowVariable.defaultValue(variable.type) : items.get(0);
            this.changed();
            this.fillLeft();
            this.refreshDetails();
        });

        UIButton delete = new UIButton(key("delete_variable"), (b) -> this.removeVariable(variable));

        delete.color(Colors.NEGATIVE);
        delete.marginTop(8);

        int used = this.graph.usages(variable);

        this.details.add(
            this.header(key("variable_details")),
            UI.label(IKey.constant(variable.name + "   " + key("type." + variable.type).get() + (variable.isArray ? "[]" : ""))).color(typeColor(variable.type)),
            UI.label(key("variable_name")), this.pair(name, FlowFields.hintButton(key("hint.variable_name"))),
            UI.label(key("variable_type")), this.pair(type, FlowFields.hintButton(key("hint.variable_type"))),
            UI.label(key("variable_scope")), this.pair(scope, FlowFields.hintButton(key("scope_tooltip"))),
            this.pair(array, FlowFields.hintButton(key("hint.is_array"))),
            this.header(key("default_value"))
        );

        if (variable.isArray)
        {
            this.fillListEditor(variable);
        }
        else
        {
            this.details.add(this.pair(this.valueEditor(variable.type, variable.value, (v) ->
            {
                variable.value = v;
                this.changed();
            }), FlowFields.hintButton(FlowFields.hint("type_" + variable.type, "type_any"))));
        }

        this.details.add(UI.label(key("variable_usages").format(used)).color(FlowTheme.dim()).marginTop(6));
        this.details.add(delete);
    }

    /** A list's items, [0], [1], [2]..., each in the field of its type with a remove button, and + for one more. */
    private void fillListEditor(FlowVariable variable)
    {
        List<String> items = FlowValues.list(variable.value);

        this.details.add(UI.label(key("item_count").format(items.size())).color(FlowTheme.dim()));

        if (items.isEmpty())
        {
            this.details.add(new mchorse.bbs_mod.ui.framework.elements.utils.UIText(key("list_empty")).color(FlowTheme.dim(), false));
        }

        for (int i = 0; i < items.size(); i++)
        {
            int index = i;
            UIElement editor = this.valueEditor(variable.type, items.get(i), (v) ->
            {
                List<String> now = FlowValues.list(variable.value);

                if (index < now.size())
                {
                    now.set(index, v);
                    variable.value = FlowValues.join(now);
                    this.changed();
                }
            });
            UIIcon remove = new UIIcon(Icons.REMOVE, (b) ->
            {
                List<String> now = FlowValues.list(variable.value);

                if (index < now.size())
                {
                    now.remove(index);
                    variable.value = FlowValues.join(now);
                    this.changed();
                    this.refreshDetails();
                }
            });

            remove.tooltip(key("remove_item"));

            UILabel label = UI.label(IKey.constant("[" + i + "]")).color(typeColor(variable.type));
            UIElement row = new UIElement();

            label.labelAnchor(0F, 0.5F);
            row.h(20);
            label.relative(row).w(24).h(20);
            editor.relative(row).x(26).w(1F, -48).h(20);
            remove.relative(row).x(1F, -20).wh(20, 20);
            row.add(label, editor, remove);
            this.details.add(row);
        }

        UIButton add = new UIButton(key("add_item"), (b) ->
        {
            List<String> now = FlowValues.list(variable.value);

            now.add(FlowVariable.defaultValue(variable.type));
            variable.value = FlowValues.join(now);
            this.changed();
            this.refreshDetails();
        });

        this.details.add(this.pair(add, FlowFields.hintButton(key("hint.add_item"))));
    }

    /** The field of one value of a type: a switch, a number, three numbers, a colour, a picker slot or text. */
    private UIElement valueEditor(String type, String value, Consumer<String> setter)
    {
        if (UIFlowSlot.isSlot(type))
        {
            return new UIFlowSlot(type, value, setter);
        }

        switch (type)
        {
            case "entity" ->
            {
                /* The entity of the event, a player online, or a typed selector, name or UUID */
                return FlowFields.combo(value, setter, FlowFields::entities, key("entity_placeholder"));
            }
            case "color" ->
            {
                mchorse.bbs_mod.ui.framework.elements.input.UIColor color = new mchorse.bbs_mod.ui.framework.elements.input.UIColor((c) -> setter.accept(String.format("%06x", c & 0xffffff)));

                color.setColor(Colors.parse(value, 0xffffff));

                return color;
            }
            case "boolean" ->
            {
                boolean on = value.trim().equals("true") || value.trim().equals("1");

                return new UIToggle(key("value_true"), on, (b) -> setter.accept(String.valueOf(b.getValue())));
            }
            case "integer", "float" ->
            {
                boolean integer = type.equals("integer");
                UITrackpad trackpad = new UITrackpad((v) -> setter.accept(integer ? String.valueOf(v.longValue()) : String.valueOf(v.floatValue())));

                if (integer)
                {
                    trackpad.limit(-1_000_000_000D, 1_000_000_000D, true);
                }

                trackpad.setValue(FlowValues.parse(value, 0));
                trackpad.tooltip(key("variable_value"));

                return trackpad;
            }
            case "vec3" ->
            {
                double[] xyz = FlowValues.vec(value);
                List<UIElement> pads = new ArrayList<>();

                for (int i = 0; i < 3; i++)
                {
                    int axis = i;
                    UITrackpad trackpad = new UITrackpad((v) ->
                    {
                        xyz[axis] = v;
                        setter.accept(FlowValues.format(xyz[0]) + " " + FlowValues.format(xyz[1]) + " " + FlowValues.format(xyz[2]));
                    });

                    trackpad.setValue(xyz[i]);
                    pads.add(trackpad);
                }

                return UI.row(1, pads.toArray(new UIElement[0]));
            }
            default ->
            {
                UITextbox textbox = new UITextbox(256, setter);

                textbox.setText(value);
                textbox.placeholder(key(type.equals("entity") ? "entity_placeholder" : "variable_value"));
                textbox.tooltip(key(type.equals("entity") ? "entity_tooltip" : "variable_value"));

                return textbox;
            }
        }
    }

    public static int typeColor(String type)
    {
        return FlowTheme.typeColor(type);
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

        if ((node == null || node.getType() == null) && this.selectedVariable != null && this.graph.variables.contains(this.selectedVariable))
        {
            this.fillVariableDetails(this.selectedVariable);
        }
        else if (node == null || node.getType() == null)
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
            this.fillDetails(null);
        });

        for (String t : targets)
        {
            target.addLabel(key("target." + t));
        }

        target.setValue(Math.max(0, targets.indexOf(this.box.target)));
        actor.setText(this.box.actorId);
        actor.placeholder(key("actor_prefix"));

        w.tooltip(key("box_width"));
        h.tooltip(key("box_height"));
        d.tooltip(key("box_depth"));

        String pos = this.box.getPos().getX() + ", " + this.box.getPos().getY() + ", " + this.box.getPos().getZ();

        this.details.add(
            this.header(key("details")),
            UI.label(IKey.constant(key("component.volume").get() + "  " + pos)).color(0xffffb547),
            UI.label(key("box_size")), this.pair(UI.row(2, w, h, d), FlowFields.hintButton(key("hint.box_size"))),
            UI.label(key("offset")), this.pair(UI.row(2, ox, oy, oz), FlowFields.hintButton(key("hint.box_offset"))),
            UI.label(key("target")), this.pair(target, FlowFields.hintButton(key("hint.box_target")))
        );

        if (!this.box.target.equals(TriggerBoxBlockEntity.TARGET_ACTOR_ID))
        {
            return;
        }

        /* One Actor: a name prefix, or an actor of a film, then of one of its replays */
        List<String> modes = List.of(TriggerBoxBlockEntity.ACTOR_PREFIX, TriggerBoxBlockEntity.ACTOR_FILM);
        UICirculate mode = new UICirculate((b) ->
        {
            this.box.actorMode = modes.get(b.getValue());
            this.fillDetails(null);
        });

        for (String m : modes)
        {
            mode.addLabel(key("actor_mode." + m));
        }

        mode.setValue(Math.max(0, modes.indexOf(this.box.actorMode)));
        this.details.add(this.pair(mode, FlowFields.hintButton(key("hint.actor_mode"))));

        if (this.box.actorMode.equals(TriggerBoxBlockEntity.ACTOR_FILM))
        {
            this.details.add(UI.label(key("pin.film")));
            this.details.add(this.pair(FlowFields.combo(this.box.actorFilm, (text) ->
            {
                boolean changedFilm = !text.equals(this.box.actorFilm);

                this.box.actorFilm = text;

                if (changedFilm)
                {
                    this.box.actorReplay = "";
                }
            }, FlowFields::films, key("film_placeholder")), FlowFields.hintButton(key("hint.actor_film"))));
            this.details.add(UI.label(key("actor_replay")));
            this.details.add(this.pair(FlowFields.combo(this.box.actorReplay, (text) -> this.box.actorReplay = text, (callback) -> FlowFields.replays(this.box.actorFilm, callback), key("option.any_replay")), FlowFields.hintButton(key("hint.actor_replay"))));
        }
        else
        {
            this.details.add(this.pair(actor, FlowFields.hintButton(key("hint.actor_prefix"))));
        }
    }

    private static final java.util.Set<String> SWITCH_PARAMS = java.util.Set.of("start_closed", "start_open", "random", "loop", "clamp", "camera");

    private void fillNodeDetails(FlowNode node)
    {
        FlowNodeType type = node.getType();

        this.details.add(this.header(key("details")));
        this.details.add(UI.label(IKey.constant(this.board.title(node) + "  #" + node.id)).color(type.kind.color));
        this.details.add(new mchorse.bbs_mod.ui.framework.elements.utils.UIText(key("node_desc." + type.id)).color(FlowTheme.dim(), false));

        /* The variable a Get, Set or Add points at, picked from a drop down list by its id */
        if (type.params.stream().anyMatch((p) -> p.id().equals("variable")))
        {
            this.details.add(UI.label(key("param.variable")));
            this.details.add(this.variablePicker(node));
        }

        /* Data inputs: a wired one says so, a free one takes a typed-in value */
        List<String> inputs = node.dataInputs();

        if (!inputs.isEmpty())
        {
            this.details.add(this.header(key("inputs")));

            for (String pin : inputs)
            {
                this.details.add(this.inputField(node, pin));
            }

            if (type.growable())
            {
                UIButton more = new UIButton(key("add_input"), (b) ->
                {
                    this.graph.setInputCount(node, node.inputCount() + 1);
                    this.onGraphChanged();
                });
                UIButton fewer = new UIButton(key("remove_input"), (b) ->
                {
                    this.graph.setInputCount(node, node.inputCount() - 1);
                    this.onGraphChanged();
                });

                fewer.setEnabled(node.inputCount() > type.minInputs);
                this.details.add(UI.row(2, more, fewer));
            }
        }

        /* Growable outputs, each with its own weight or case value when it has one */
        if (type.growableOutputs())
        {
            this.details.add(this.header(key("outputs")));

            if (type.outputParam != null)
            {
                for (int i = 0; i < node.outputCount(); i++)
                {
                    String id = type.outputParam + i;
                    UITextbox field = new UITextbox(256, (text) ->
                    {
                        node.params.put(id, text);
                        this.changed();
                    });

                    field.setText(node.param(id));
                    field.tooltip(key("param." + type.outputParam));
                    this.details.add(this.pair(field, UI.label(IKey.constant(String.valueOf(i))).color(FlowTheme.dim())));
                }
            }

            UIButton more = new UIButton(key("add_output"), (b) ->
            {
                this.graph.setOutputCount(node, node.outputCount() + 1);
                this.onGraphChanged();
            });
            UIButton fewer = new UIButton(key("remove_output"), (b) ->
            {
                this.graph.setOutputCount(node, node.outputCount() - 1);
                this.onGraphChanged();
            });

            fewer.setEnabled(node.outputCount() > type.minOutputs);
            this.details.add(UI.row(2, more, fewer));
        }

        /* Settings */
        boolean settings = false;

        for (FlowNodeType.Param param : type.params)
        {
            if (param.id().equals("variable"))
            {
                continue;
            }

            if (!settings)
            {
                this.details.add(this.header(key("settings")));
                settings = true;
            }

            UIElement hint = FlowFields.hintButton(FlowFields.hint("param." + param.id(), "param"));

            if (SWITCH_PARAMS.contains(param.id()))
            {
                this.details.add(this.pair(new UIToggle(key("param." + param.id()), FlowValues.truthy(node.param(param.id())), (b) ->
                {
                    node.params.put(param.id(), String.valueOf(b.getValue()));
                    this.changed();
                }), hint));
            }
            else
            {
                this.details.add(UI.label(key("param." + param.id())));
                this.details.add(this.pair(this.paramField(node, param), hint));
            }
        }

        if (inputs.stream().anyMatch((p) -> node.declaredInputType(p).equals("string")) || type.params.stream().anyMatch((p) -> p.id().equals("command")))
        {
            this.details.add(new mchorse.bbs_mod.ui.framework.elements.utils.UIText(key("placeholders")).color(FlowTheme.dim(), false));
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

    /** A data input's row: its name in the colour of its type, then "wired" or the value it reads. */
    private UIElement inputField(FlowNode node, String pin)
    {
        String declared = node.declaredInputType(pin);
        boolean template = pin.startsWith("t_") && node.getType().templateInputs;
        boolean grown = !template && node.getType().dataInput(pin) == null;
        boolean indexed = grown && node.type.equals("make_array");
        int at = grown ? (int) FlowValues.parse(pin.substring(2), 0) : 0;
        String name = template ? pin.substring(2) : indexed ? "[" + at + "]" : grown ? String.valueOf((char) ('A' + at)) : key("pin." + pin).get();
        UILabel label = UI.label(IKey.constant(name)).color(FlowTheme.typeColor(this.graph.inputType(node, pin)));
        UIElement field;

        if (this.graph.dataInto(node.id, pin) != null)
        {
            field = UI.label(key("input_wired")).color(FlowTheme.dim());
        }
        else if (declared.equals("boolean"))
        {
            field = new UIToggle(key("value_true"), FlowValues.truthy(node.param(pin)), (b) ->
            {
                node.params.put(pin, String.valueOf(b.getValue()));
                this.changed();
            });
        }
        else if (declared.equals("entity") || this.graph.inputType(node, pin).equals("entity"))
        {
            /* An entity pin, or the value of a Set on an entity variable: the same list everywhere */
            field = FlowFields.combo(node.param(pin), (text) ->
            {
                node.params.put(pin, text);
                this.changed();
            }, FlowFields::entities, key("entity_placeholder"));
        }
        else if (UIFlowSlot.isSlot(declared))
        {
            field = new UIFlowSlot(declared, node.param(pin), (text) ->
            {
                node.params.put(pin, text);
                this.changed();
            });
        }
        else if (pin.equals("form"))
        {
            field = FlowFields.form(node.param(pin), (text) ->
            {
                node.params.put(pin, text);
                this.changed();
            }, this.main);
        }
        else if (pin.equals("film"))
        {
            field = FlowFields.combo(node.param(pin), (text) ->
            {
                node.params.put(pin, text);
                this.changed();
            }, FlowFields::films, key("film_placeholder"));
        }
        else
        {
            UITextbox textbox = new UITextbox(1000, (text) ->
            {
                node.params.put(pin, text);
                this.changed();
            });

            textbox.setText(node.param(pin));
            textbox.tooltip(key("input_value"));
            field = textbox;
        }

        UIElement row = new UIElement();

        String hintId = template ? "template_input" : indexed ? "array_input" : grown ? "input_" + declared : pin;
        IKey own = FlowFields.key("hint." + node.type + "." + pin);
        UIElement hint = FlowFields.hintButton(own.get().equals("bbs.ui.trigger_box.hint." + node.type + "." + pin) ? FlowFields.hint(hintId, "type_" + declared) : own);

        row.h(20);
        label.relative(row).w(50).h(20);
        label.labelAnchor(0F, 0.5F);
        field.relative(row).x(52).w(1F, -74).h(20);
        hint.relative(row).x(1F, -20).wh(20, 20);
        row.add(label, field, hint);

        return row;
    }

    /** The variable of a Get, Set or Add: a button that lists the flow's variables, by id. */
    private UIElement variablePicker(FlowNode node)
    {
        if (this.graph.variables.isEmpty())
        {
            return UI.label(key("no_variables")).color(0xffff7070);
        }

        FlowVariable current = this.graph.variableOf(node);
        UIButton button = new UIButton(current == null ? key("invalid_variable") : IKey.constant(current.name), (b) -> this.context.replaceContextMenu((menu) ->
        {
            for (FlowVariable variable : this.graph.variables)
            {
                menu.action(Icons.CONVERT, IKey.constant(variable.name + "  " + key("type." + variable.type).get()), typeColor(variable.type), () ->
                {
                    node.params.put("variable", variable.id);
                    this.onGraphChanged();
                });
            }
        }));

        button.color(current == null ? Colors.NEGATIVE : typeColor(current.type));

        return button;
    }

    private UIElement paramField(FlowNode node, FlowNodeType.Param param)
    {
        String id = param.id();

        if (id.equals("operator"))
        {
            UICirculate circulate = new UICirculate((b) ->
            {
                node.params.put(id, FlowNodeTypes.OPERATORS.get(b.getValue()));
                this.changed();
            });

            for (String op : FlowNodeTypes.OPERATORS)
            {
                circulate.addLabel(IKey.constant(op));
            }

            circulate.setValue(Math.max(0, FlowNodeTypes.OPERATORS.indexOf(node.param(id))));

            return circulate;
        }

        if (id.equals("interval") || id.equals("max_iterations") || id.equals("start"))
        {
            UITrackpad trackpad = new UITrackpad((v) ->
            {
                node.params.put(id, String.valueOf(v.intValue()));
                this.changed();
            });

            trackpad.limit(id.equals("start") ? -100000 : 1, id.equals("max_iterations") ? mchorse.bbs_mod.triggers.FlowRuntime.MAX_LOOP : 72000, true);
            trackpad.setValue(FlowValues.parse(node.param(id), 0));

            return trackpad;
        }

        if (id.equals("template"))
        {
            UITextbox template = new UITextbox(1000, (text) ->
            {
                node.params.put(id, text);
                this.graph.pruneLinks(node);
                this.onGraphChanged();
            });

            template.delayedInput();
            template.setText(node.param(id));
            template.tooltip(key("hint.param.template"));

            return template;
        }

        UITextbox textbox = new UITextbox(1000, (text) ->
        {
            node.params.put(id, text);
            this.changed();
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

        context.batcher.box(0, BAR, w, HEAD, FlowTheme.chrome());
        context.batcher.box(0, HEAD - 1, w, HEAD, divider);
        context.batcher.box(0, HEAD, LEFT_W, h, FlowTheme.panel());
        context.batcher.box(w - RIGHT_W, HEAD, w, h, FlowTheme.panel());
        context.batcher.box(LEFT_W - 1, HEAD, LEFT_W, h, divider);
        context.batcher.box(w - RIGHT_W, HEAD, w - RIGHT_W + 1, h, divider);

        context.batcher.icon(Icons.BLOCK, FlowTheme.accent(), 4, BAR + 4);
        context.batcher.textShadow(key("title").get(), 24, BAR + 8, FlowTheme.text());
    }
}
