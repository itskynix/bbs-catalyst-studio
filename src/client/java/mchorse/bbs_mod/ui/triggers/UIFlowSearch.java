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
    public static final int HEIGHT = 200;
    private static final int ROW = 14;
    private static final int TOP = 24;

    public final UITextbox search;

    private final List<FlowNodeType> filtered = new ArrayList<>();
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

        for (FlowNodeType type : FlowNodeTypes.all())
        {
            if (query.isEmpty() || name(type).toLowerCase(Locale.ROOT).contains(query) || type.id.contains(query))
            {
                this.filtered.add(type);
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

        if (index >= 0 && context.mouseButton == 0)
        {
            this.pick(this.filtered.get(index));
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
            this.pick(this.filtered.isEmpty() ? null : this.filtered.get(0));

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
        context.batcher.box(x, y, this.area.ex(), this.area.ey(), 0xf0181b20);
        context.batcher.outline(x, y, this.area.ex(), this.area.ey(), 0xff3a404a);

        int hover = this.indexAt(context);
        int rows = this.rows();
        int top = y + TOP;

        context.batcher.clip(x, top, this.area.w, this.area.h - TOP, context);

        for (int i = 0; i < rows && i + this.scroll < this.filtered.size(); i++)
        {
            int index = i + this.scroll;
            FlowNodeType type = this.filtered.get(index);
            int ry = top + i * ROW;

            if (index == hover)
            {
                context.batcher.box(x + 1, ry, this.area.ex() - 1, ry + ROW, 0x40ffffff);
            }

            context.batcher.box(x + 4, ry + 3, x + 7, ry + ROW - 3, type.kind.color);
            context.batcher.textShadow(name(type), x + 11, ry + 3, Colors.WHITE);

            String kind = kindLabel(type.kind).get();

            context.batcher.text(kind, this.area.ex() - 6 - context.batcher.getFont().getWidth(kind), ry + 3, 0xff7d8794);
        }

        if (this.filtered.isEmpty())
        {
            context.batcher.textShadow(L10n.lang("bbs.ui.trigger_box.search_empty").get(), x + 8, top + 4, 0xff7d8794);
        }

        context.batcher.unclip(context);

        context.batcher.icon(Icons.SEARCH, 0xff7d8794, this.area.ex() - 20, y + 4);

        super.render(context);
    }
}
