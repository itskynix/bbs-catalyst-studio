package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.CustomInterpolations;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.CubicBezierSolver;
import java.util.ArrayList;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.InterpolationUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.renderers.InterpolationRenderer;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class UIInterpolationContextMenu extends UIContextMenu
{
    public static final Map<IInterp, Icon> INTERP_ICON_MAP = new HashMap<>();

    private static final int PADDING = 10;
    private static final int MARGIN = 5;
    private static final int GRAPH_HEIGHT = 90;
    private static final int GRAPH_PADDING = 15;
    private static final int ARGUMENTS_HEIGHT = 45;
    private static final int WIDTH = 180;
    private static final int LABEL_WIDTH = 36;
    private static final int COLUMNS = WIDTH / 20;

    /** CSS "ease": what a fresh cubic bezier starts as. */
    private static final double[] DEFAULT_CURVE = {0.25D, 0.1D, 0.25D, 1D};

    public UIElement grid;
    public UITrackpad v1;
    public UITrackpad v2;
    public UITrackpad v3;
    public UITrackpad v4;

    public UIIcon copy;
    public UIIcon paste;
    public UIIcon addPreset;
    public UIElement presetGrid;
    private int gridHeight;

    /** Which handle (0 = out, 1 = in) is being dragged on the canvas, or -1. */
    private int dragging = -1;

    private Runnable callback;
    private Interpolation interpolation;

    static
    {
        INTERP_ICON_MAP.put(Interpolations.LINEAR, Icons.INTERP_LINEAR);
        INTERP_ICON_MAP.put(Interpolations.CONST, Icons.INTERP_CONST);
        INTERP_ICON_MAP.put(Interpolations.STEP, Icons.INTERP_STEP);
        INTERP_ICON_MAP.put(Interpolations.STEP_TICK, Icons.INTERP_STEP);
        INTERP_ICON_MAP.put(Interpolations.QUAD_IN, Icons.INTERP_QUAD_IN);
        INTERP_ICON_MAP.put(Interpolations.QUAD_OUT, Icons.INTERP_QUAD_OUT);
        INTERP_ICON_MAP.put(Interpolations.QUAD_INOUT, Icons.INTERP_QUAD_INOUT);
        INTERP_ICON_MAP.put(Interpolations.CUBIC_IN, Icons.INTERP_CUBIC_IN);
        INTERP_ICON_MAP.put(Interpolations.CUBIC_OUT, Icons.INTERP_CUBIC_OUT);
        INTERP_ICON_MAP.put(Interpolations.CUBIC_INOUT, Icons.INTERP_CUBIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.EXP_IN, Icons.INTERP_EXP_IN);
        INTERP_ICON_MAP.put(Interpolations.EXP_OUT, Icons.INTERP_EXP_OUT);
        INTERP_ICON_MAP.put(Interpolations.EXP_INOUT, Icons.INTERP_EXP_INOUT);
        INTERP_ICON_MAP.put(Interpolations.BACK_IN, Icons.INTERP_BACK_IN);
        INTERP_ICON_MAP.put(Interpolations.BACK_OUT, Icons.INTERP_BACK_OUT);
        INTERP_ICON_MAP.put(Interpolations.BACK_INOUT, Icons.INTERP_BACK_INOUT);
        INTERP_ICON_MAP.put(Interpolations.ELASTIC_IN, Icons.INTERP_ELASTIC_IN);
        INTERP_ICON_MAP.put(Interpolations.ELASTIC_OUT, Icons.INTERP_ELASTIC_OUT);
        INTERP_ICON_MAP.put(Interpolations.ELASTIC_INOUT, Icons.INTERP_ELASTIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.BOUNCE_IN, Icons.INTERP_BOUNCE_IN);
        INTERP_ICON_MAP.put(Interpolations.BOUNCE_OUT, Icons.INTERP_BOUNCE_OUT);
        INTERP_ICON_MAP.put(Interpolations.BOUNCE_INOUT, Icons.INTERP_BOUNCE_INOUT);
        INTERP_ICON_MAP.put(Interpolations.SINE_IN, Icons.INTERP_SINE_IN);
        INTERP_ICON_MAP.put(Interpolations.SINE_OUT, Icons.INTERP_SINE_OUT);
        INTERP_ICON_MAP.put(Interpolations.SINE_INOUT, Icons.INTERP_SINE_INOUT);
        INTERP_ICON_MAP.put(Interpolations.QUART_IN, Icons.INTERP_QUART_IN);
        INTERP_ICON_MAP.put(Interpolations.QUART_OUT, Icons.INTERP_QUART_OUT);
        INTERP_ICON_MAP.put(Interpolations.QUART_INOUT, Icons.INTERP_QUART_INOUT);
        INTERP_ICON_MAP.put(Interpolations.QUINT_IN, Icons.INTERP_QUINT_IN);
        INTERP_ICON_MAP.put(Interpolations.QUINT_OUT, Icons.INTERP_QUINT_OUT);
        INTERP_ICON_MAP.put(Interpolations.QUINT_INOUT, Icons.INTERP_QUINT_INOUT);
        INTERP_ICON_MAP.put(Interpolations.CIRCLE_IN, Icons.INTERP_CIRCLE_IN);
        INTERP_ICON_MAP.put(Interpolations.CIRCLE_OUT, Icons.INTERP_CIRCLE_OUT);
        INTERP_ICON_MAP.put(Interpolations.CIRCLE_INOUT, Icons.INTERP_CIRCLE_INOUT);
        INTERP_ICON_MAP.put(Interpolations.CUBIC, Icons.INTERP_CUBIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.HERMITE, Icons.INTERP_CUBIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.BEZIER, Icons.INTERP_BEZIER);
        INTERP_ICON_MAP.put(Interpolations.AUTO, Icons.INTERP_AUTO);
        INTERP_ICON_MAP.put(Interpolations.AUTO_CLAMPED, Icons.INTERP_AUTO_CLAMPED);
        INTERP_ICON_MAP.put(Interpolations.BSPLINE, Icons.INTERP_BSPLINE);
    }

    public UIInterpolationContextMenu(Interpolation interpolation)
    {
        this.interpolation = interpolation;

        int w = WIDTH;
        int gridY = PADDING + GRAPH_HEIGHT + MARGIN + ARGUMENTS_HEIGHT;

        this.v1 = new UITrackpad((v) ->
        {
            this.interpolation.setV1(v);
            this.accept();
        });
        this.v2 = new UITrackpad((v) ->
        {
            this.interpolation.setV2(v);
            this.accept();
        });
        this.v3 = new UITrackpad((v) ->
        {
            this.interpolation.setV3(v);
            this.accept();
        });
        this.v4 = new UITrackpad((v) ->
        {
            this.interpolation.setV4(v);
            this.accept();
        });

        this.v1.setValue(interpolation.getV1());
        this.v2.setValue(interpolation.getV2());
        this.v3.setValue(interpolation.getV3());
        this.v4.setValue(interpolation.getV4());

        this.copy = new UIIcon(Icons.COPY, (b) ->
        {
            MapType data = new MapType();

            data.put("interp", this.interpolation.toData());
            Window.setClipboard(data, "_CopyInterpolation");
        });
        this.copy.tooltip(UIKeys.INTERPOLATIONS_CONTEXT_COPY);

        this.paste = new UIIcon(Icons.PASTE, (b) ->
        {
            MapType copy = Window.getClipboardMap("_CopyInterpolation");

            if (copy == null)
            {
                return;
            }

            BaseValue.edit(this.interpolation, (v) -> v.fromData(copy.get("interp")));

            if (this.callback != null)
            {
                this.callback.run();
            }
        });
        this.paste.tooltip(UIKeys.INTERPOLATIONS_CONTEXT_PASTE);

        this.v1.tooltip(UIKeys.INTERPOLATIONS_UI_OUT_X);
        this.v2.tooltip(UIKeys.INTERPOLATIONS_UI_OUT_Y);
        this.v3.tooltip(UIKeys.INTERPOLATIONS_UI_IN_X);
        this.v4.tooltip(UIKeys.INTERPOLATIONS_UI_IN_Y);

        this.addPreset = new UIIcon(Icons.ADD, (b) -> this.savePreset());
        this.addPreset.tooltip(UIKeys.INTERPOLATIONS_UI_PRESET_ADD);

        this.grid = new UIElement();
        this.grid.relative(this).xy(PADDING, gridY).w(w).grid(0).items(COLUMNS);

        for (IInterp value : interpolation.getMap().values())
        {
            UIIcon icon = new UIIcon(INTERP_ICON_MAP.getOrDefault(value, Icons.INTERP_LINEAR), (b) ->
            {
                if (value == Interpolations.CUBIC_BEZIER && this.interpolation.getInterp() != value)
                {
                    this.applyCurve(DEFAULT_CURVE[0], DEFAULT_CURVE[1], DEFAULT_CURVE[2], DEFAULT_CURVE[3]);

                    return;
                }

                this.interpolation.setInterp(value);
                this.accept();
            });

            icon.tooltip(InterpolationUtils.getName(value));
            icon.highlight(() -> this.interpolation.getInterp() == value, Direction.BOTTOM);
            this.grid.add(icon);
            this.setupKeybind(value, icon);
        }

        /* Label column on the left, the four boxes (and copy / paste) right of it */
        UIElement vs = UI.column(UI.row(this.v1, this.v2, this.copy), UI.row(this.v3, this.v4, this.paste));

        vs.relative(this).xy(PADDING + LABEL_WIDTH, PADDING + GRAPH_HEIGHT + MARGIN).w(w - LABEL_WIDTH);

        /* The built-in icons fill their own grid; the saved presets follow in a grid of their own, so
         * they are there whichever interpolation is picked */
        this.gridHeight = (int) Math.ceil(interpolation.getMap().size() / (float) COLUMNS) * 20;
        this.grid.h(this.gridHeight);

        this.presetGrid = new UIElement();
        this.presetGrid.relative(this).xy(PADDING, gridY + this.gridHeight).w(w);

        this.add(vs, this.grid, this.presetGrid);
        this.rebuildPresets();
    }

    /** (Re)creates the saved preset buttons, wrapping every {@link #COLUMNS}, with the + button always last. */
    private void rebuildPresets()
    {
        this.presetGrid.removeAll();

        int i = 0;

        for (CustomInterpolations.Preset preset : CustomInterpolations.getPresets())
        {
            UIPresetButton button = new UIPresetButton(preset);

            button.relative(this.presetGrid).xy((i % COLUMNS) * 20, (i / COLUMNS) * 20).wh(20, 20);
            this.presetGrid.add(button);
            i ++;
        }

        this.addPreset.relative(this.presetGrid).xy((i % COLUMNS) * 20, (i / COLUMNS) * 20).wh(20, 20);
        this.presetGrid.add(this.addPreset);
        i ++;

        int h = (int) Math.ceil(i / (float) COLUMNS) * 20;

        this.presetGrid.h(h);
        this.wh(WIDTH + PADDING * 2, PADDING + GRAPH_HEIGHT + MARGIN + ARGUMENTS_HEIGHT + this.gridHeight + h + PADDING);

        if (this.getParent() != null)
        {
            this.resize();
        }
    }

    /** Makes the interpolation a cubic bezier with these control points, as one change. */
    private void applyCurve(double x1, double y1, double x2, double y2)
    {
        MapType data = new MapType();
        ListType args = new ListType();

        data.putString("key", Interpolations.CUBIC_BEZIER.getKey());
        data.put("args", args);
        args.addDouble(x1);
        args.addDouble(y1);
        args.addDouble(x2);
        args.addDouble(y2);

        BaseValue.edit(this.interpolation, (v) -> v.fromData(data));

        this.v1.setValue(x1);
        this.v2.setValue(y1);
        this.v3.setValue(x2);
        this.v4.setValue(y2);
        this.accept();
    }

    private void savePreset()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        /* The four values only describe a curve for the cubic bezier; otherwise the default curve is what gets named */
        boolean bezier = this.isBezier();
        double x1 = bezier ? this.interpolation.getV1() : DEFAULT_CURVE[0];
        double y1 = bezier ? this.interpolation.getV2() : DEFAULT_CURVE[1];
        double x2 = bezier ? this.interpolation.getV3() : DEFAULT_CURVE[2];
        double y2 = bezier ? this.interpolation.getV4() : DEFAULT_CURVE[3];

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.INTERPOLATIONS_UI_PRESET_TITLE,
            UIKeys.INTERPOLATIONS_UI_PRESET_MESSAGE,
            (name) ->
            {
                name = name.trim();

                if (!name.isEmpty())
                {
                    CustomInterpolations.save(name, x1, y1, x2, y2);
                    this.rebuildPresets();
                }
            }
        );

        UIOverlay.addOverlay(context, panel);
    }

    /* Handles of the cubic bezier on the canvas */

    private boolean isBezier()
    {
        return this.interpolation.getInterp() == Interpolations.CUBIC_BEZIER;
    }

    private int graphX()
    {
        return this.area.x + PADDING;
    }

    private int graphW()
    {
        return this.area.w - PADDING * 2;
    }

    private int handleX(int index)
    {
        return this.graphX() + (int) (this.graphW() * (index == 0 ? this.interpolation.getV1() : this.interpolation.getV3()));
    }

    private int handleY(int index)
    {
        double v = index == 0 ? this.interpolation.getV2() : this.interpolation.getV4();

        return this.valueY(v);
    }

    private int valueY(double v)
    {
        return (int) (this.area.y + PADDING + GRAPH_PADDING + (1 - v) * (GRAPH_HEIGHT - GRAPH_PADDING * 2));
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.isBezier())
        {
            for (int i = 0; i < 2; i++)
            {
                int dx = context.mouseX - this.handleX(i);
                int dy = context.mouseY - this.handleY(i);

                if (dx * dx + dy * dy <= 64)
                {
                    this.dragging = i;

                    return true;
                }
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragging != -1)
        {
            this.dragging = -1;

            return true;
        }

        return super.subMouseReleased(context);
    }

    private void updateDrag(UIContext context)
    {
        double x = MathUtils.clamp((context.mouseX - this.graphX()) / (double) this.graphW(), 0D, 1D);
        double y = 1 - (context.mouseY - (this.area.y + PADDING + GRAPH_PADDING)) / (double) (GRAPH_HEIGHT - GRAPH_PADDING * 2);

        x = Math.round(x * 100D) / 100D;
        y = Math.round(MathUtils.clamp(y, -0.3D, 1.3D) * 100D) / 100D;

        if (this.dragging == 0)
        {
            this.interpolation.setV1(x);
            this.interpolation.setV2(y);
            this.v1.setValue(x);
            this.v2.setValue(y);
        }
        else
        {
            this.interpolation.setV3(x);
            this.interpolation.setV4(y);
            this.v3.setValue(x);
            this.v4.setValue(y);
        }

        this.accept();
    }

    /** A saved curve in the grid, drawn as its own tiny preview. Click to apply. */
    private class UIPresetButton extends UIElement
    {
        private final CustomInterpolations.Preset preset;

        public UIPresetButton(CustomInterpolations.Preset preset)
        {
            this.preset = preset;

            this.tooltip(IKey.constant(preset.name()));
        }

        private boolean isCurrent()
        {
            return UIInterpolationContextMenu.this.isBezier()
                && this.preset.sameCurve(interpolation.getV1(), interpolation.getV2(), interpolation.getV3(), interpolation.getV4());
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                UIInterpolationContextMenu.this.applyCurve(this.preset.x1(), this.preset.y1(), this.preset.x2(), this.preset.y2());

                return true;
            }

            return false;
        }

        @Override
        public void render(UIContext context)
        {
            int primary = BBSSettings.primaryColor.get();
            boolean hover = this.area.isInside(context);

            this.area.render(context.batcher, Colors.setA(Colors.WHITE, hover ? 0.12F : 0.04F));

            if (this.isCurrent())
            {
                context.batcher.box(this.area.x + 2, this.area.ey() - 2, this.area.ex() - 2, this.area.ey(), Colors.A100 | primary);
            }

            int pad = 5;
            int x = this.area.x + pad;
            int y = this.area.y + pad;
            int w = this.area.w - pad * 2;
            int h = this.area.h - pad * 2;
            LineBuilder line = new LineBuilder(1F);
            int steps = 14;

            for (int i = 0; i <= steps; i++)
            {
                double f = i / (double) steps;
                double t = CubicBezierSolver.solveT(this.preset.x1(), this.preset.x2(), f);
                double v = CubicBezierSolver.bezier(0D, this.preset.y1(), this.preset.y2(), 1D, t);

                line.add((float) (x + f * w), (float) (y + (1 - MathUtils.clamp(v, -0.3D, 1.3D)) * h));
            }

            line.render(context.batcher, SolidColorLineRenderer.get(new Color().set(hover || this.isCurrent() ? Colors.WHITE : primary, false)));

            super.render(context);
        }
    }

    private void accept()
    {
        if (this.callback != null)
        {
            this.callback.run();
        }
    }

    private void setupKeybind(IInterp interp, UIIcon icon)
    {
        IKey label = InterpolationUtils.getName(interp);
        IKey category = UIKeys.INTERPOLATIONS_KEY_CATEGORY;
        String key = interp.getKey();
        KeyCombo combo = new KeyCombo(label, interp.getKeyCode());

        if (key.endsWith("_in"))
        {
            combo = new KeyCombo(label, interp.getKeyCode(), GLFW.GLFW_KEY_LEFT_SHIFT);
        }
        else if (key.endsWith("_out"))
        {
            combo = new KeyCombo(label, interp.getKeyCode(), GLFW.GLFW_KEY_LEFT_CONTROL);
        }

        this.keys().register(combo.category(category), icon::clickItself);
    }

    public UIInterpolationContextMenu callback(Runnable callback)
    {
        this.callback = callback;

        return this;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY()).bounds(context.menu.overlay, 5);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int color = BBSSettings.primaryColor.get();
        Color fg = new Color().set(color);

        fg.a = 0.5F;

        if (this.dragging != -1)
        {
            this.updateDrag(context);
        }

        InterpolationRenderer.renderInterpolationGraph(this.interpolation, context, fg, Colors.WHITE, this.area.x + PADDING, this.area.y + PADDING, this.area.w - PADDING * 2, GRAPH_HEIGHT, 20, GRAPH_PADDING);

        if (this.isBezier())
        {
            this.renderHandles(context, color);
        }

        boolean bezier = this.isBezier();
        int ly = this.area.y + PADDING + GRAPH_HEIGHT + MARGIN;
        int dim = Colors.setA(Colors.WHITE, 0.55F);
        int font = context.batcher.getFont().getHeight();

        context.batcher.text(bezier ? UIKeys.INTERPOLATIONS_UI_OUT.get() : "V1 | V2", this.area.x + PADDING, ly + (20 - font) / 2, dim);
        context.batcher.text(bezier ? UIKeys.INTERPOLATIONS_UI_IN.get() : "V3 | V4", this.area.x + PADDING, ly + 20 + (20 - font) / 2, dim);

        if (bezier)
        {
            /* Faint tags on the boxes themselves: X1 | Y1 on top, X2 | Y2 below */
            this.renderBoxTag(context, this.v1, "X1", dim);
            this.renderBoxTag(context, this.v2, "Y1", dim);
            this.renderBoxTag(context, this.v3, "X2", dim);
            this.renderBoxTag(context, this.v4, "Y2", dim);
        }
    }

    private void renderBoxTag(UIContext context, UITrackpad box, String tag, int color)
    {
        context.batcher.text(tag, box.area.x + 4, box.area.my() - context.batcher.getFont().getHeight() / 2, color);
    }

    private void renderHandles(UIContext context, int color)
    {
        int[] hx = {this.handleX(0), this.handleX(1)};
        int[] hy = {this.handleY(0), this.handleY(1)};
        int[] ax = {this.graphX(), this.graphX() + this.graphW()};
        int[] ay = {this.valueY(0), this.valueY(1)};

        context.batcher.clip(this.area.x + PADDING - 6, this.area.y + PADDING - 8, this.graphW() + 12, GRAPH_HEIGHT + 16, context);

        for (int i = 0; i < 2; i++)
        {
            boolean active = this.dragging == i || (this.dragging == -1 && Math.pow(context.mouseX - hx[i], 2) + Math.pow(context.mouseY - hy[i], 2) <= 64);
            LineBuilder tangent = new LineBuilder(active ? 1.5F : 1F);

            tangent.add(ax[i], ay[i]);
            tangent.add(hx[i], hy[i]);
            tangent.render(context.batcher, SolidColorLineRenderer.get(new Color().set(Colors.setA(Colors.WHITE, active ? 0.8F : 0.4F), true)));

            InterpolationRenderer.renderKnob(context, hx[i], hy[i], color, active);
        }

        context.batcher.unclip(context);
    }
}