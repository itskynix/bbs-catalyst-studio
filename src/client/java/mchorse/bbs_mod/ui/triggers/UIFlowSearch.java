package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.triggers.FlowNodeType;
import mchorse.bbs_mod.triggers.FlowNodeTypes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The add menu of the flow board: a search field over every node there is, events first. Typing
 * filters by the node's name in the current language and by its id; Enter takes the first match.
 */
public class UIFlowSearch extends UIElement
{
    public static final int WIDTH = 180;
    public static final int HEIGHT = 240;
    private static final int ROW = 14;
    private static final int TOP = 24;

    public final UITextbox search;

    /** A line of the menu: a family heading (type null) or a node under it. */
    private record Row(FlowNodeType.Kind kind, FlowNodeType type) {}

    private final List<Row> filtered = new ArrayList<>();
    private Consumer<FlowNodeType> callback;
    private int scroll;
    private boolean focusNext;

    public UIFlowSearch()
    {
        this.search = new UITextbox(100, (text) -> this.filter());
        this.search.placeholder(mchorse.bbs_mod.ui.UIKeys.GENERAL_SEARCH);
        this.search.relative(this).xy(4, 4).w(1F, -8).h(16);
        this.add(this.search);
        this.filter();
    }

    public static String name(FlowNodeType type)
    {
        return L10n.lang("bbs.ui.trigger_box.node." + type.id).get();
    }

    public static IKey kindLabel(FlowNodeType.Kind kind)
    {
        return L10n.lang("bbs.ui.trigger_box.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    public static IKey categoryLabel(FlowNodeType.Kind kind)
    {
        return L10n.lang("bbs.ui.trigger_box.category." + kind.name().toLowerCase(Locale.ROOT));
    }

    public void open(Consumer<FlowNodeType> callback)
    {
        this.callback = callback;
        this.search.setText("");
        this.scroll = 0;
        this.filter();
        this.setVisible(true);
        this.focusNext = true;
    }

    public void close()
    {
        this.setVisible(false);
        this.callback = null;

        if (this.getContext() != null)
        {
            this.getContext().unfocus();
        }
    }

    private void filter()
    {
        String query = this.search.textbox.getText().trim().toLowerCase(Locale.ROOT);

        this.filtered.clear();

        /* Family by family, a heading over the matching nodes; a family with no match is left out */
        for (FlowNodeType.Kind kind : FlowNodeType.Kind.values())
        {
            boolean heading = false;

            for (FlowNodeType type : FlowNodeTypes.all())
            {
                if (type.kind != kind || type.hidden)
                {
                    continue;
                }

                if (query.isEmpty() || name(type).toLowerCase(Locale.ROOT).contains(query) || type.id.contains(query) || categoryLabel(kind).get().toLowerCase(Locale.ROOT).contains(query))
                {
                    if (!heading)
                    {
                        this.filtered.add(new Row(kind, null));
                        heading = true;
                    }

                    this.filtered.add(new Row(kind, type));
                }
            }
        }

        this.scroll = 0;
    }

    private void pick(FlowNodeType type)
    {
        Consumer<FlowNodeType> callback = this.callback;

        this.close();

        if (callback != null && type != null)
        {
            callback.accept(type);
        }
    }

    private int rows()
    {
        return (this.area.h - TOP - 4) / ROW;
    }

    private int indexAt(UIContext context)
    {
        int y = context.mouseY - (this.area.y + TOP);

        if (!this.area.isInside(context) || y < 0)
        {
            return -1;
        }

        int index = y / ROW + this.scroll;

        return index < this.filtered.size() ? index : -1;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        int index = this.indexAt(context);

        if (index >= 0 && context.mouseButton == 0 && this.filtered.get(index).type() != null)
        {
            this.pick(this.filtered.get(index).type());
        }

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        int max = Math.max(0, this.filtered.size() - this.rows());

        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(context.mouseWheel)));

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.close();

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
        {
            this.pick(this.filtered.stream().map(Row::type).filter((t) -> t != null).findFirst().orElse(null));

            return true;
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.focusNext)
        {
            this.focusNext = false;
            context.focus(this.search);
        }

        int x = this.area.x;
        int y = this.area.y;

        context.batcher.dropShadow(x, y, this.area.ex(), this.area.ey(), 8, Colors.A50, 0);
        context.batcher.box(x, y, this.area.ex(), this.area.ey(), FlowTheme.body());
        context.batcher.outline(x, y, this.area.ex(), this.area.ey(), FlowTheme.border());

        int hover = this.indexAt(context);
        int rows = this.rows();
        int top = y + TOP;

        context.batcher.clip(x, top, this.area.w, this.area.h - TOP, context);

        for (int i = 0; i < rows && i + this.scroll < this.filtered.size(); i++)
        {
            int index = i + this.scroll;
            Row row = this.filtered.get(index);
            int ry = top + i * ROW;

            if (row.type() == null)
            {
                /* The family heading: its colour, its name, a rule to the edge */
                String label = categoryLabel(row.kind()).get().toUpperCase(Locale.ROOT);
                int w = context.batcher.getFont().getWidth(label);

                context.batcher.text(label, x + 6, ry + 4, row.kind().color);
                context.batcher.box(x + 10 + w, ry + 8, this.area.ex() - 6, ry + 9, (row.kind().color & 0xffffff) | 0x60000000);

                continue;
            }

            if (index == hover)
            {
                context.batcher.box(x + 1, ry, this.area.ex() - 1, ry + ROW, (FlowTheme.accent() & 0xffffff) | 0x40000000);
            }

            context.batcher.box(x + 10, ry + 3, x + 13, ry + ROW - 3, row.kind().color);
            context.batcher.textShadow(name(row.type()), x + 17, ry + 3, FlowTheme.text());

            String symbol = FlowNodeTypes.OPERATOR_OF.get(row.type().id);

            if (symbol != null)
            {
                context.batcher.text(symbol, this.area.ex() - 8 - context.batcher.getFont().getWidth(symbol), ry + 3, FlowTheme.dim());
            }
        }

        if (this.filtered.isEmpty())
        {
            context.batcher.textShadow(L10n.lang("bbs.ui.trigger_box.search_empty").get(), x + 8, top + 4, FlowTheme.dim());
        }

        context.batcher.unclip(context);

        context.batcher.icon(Icons.SEARCH, FlowTheme.dim(), this.area.ex() - 20, y + 4);

        super.render(context);
    }
}
