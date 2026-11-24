package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;
import java.util.function.Consumer;

/**
 * A drop down list: one field that shows the current value and, clicked anywhere, lists what can be
 * picked. Picking puts it in at once. The last entry lets a value be typed in by hand, for what the
 * list does not know (a UUID, a film on another server). The list is asked for on every click, so it
 * is always current, and may arrive later (films come from a repository).
 */
public class UIComboBox extends UIElement
{
    private String value;
    private final Consumer<String> setter;
    private final Consumer<Consumer<List<FlowFields.Option>>> options;
    private final IKey placeholder;

    /** Something to run instead of the typed entry, e.g. the form picker; null for typing. */
    private Consumer<UIComboBox> custom;
    private IKey customLabel;

    public UIComboBox(String value, Consumer<String> setter, Consumer<Consumer<List<FlowFields.Option>>> options, IKey placeholder)
    {
        this.value = value == null ? "" : value;
        this.setter = setter;
        this.options = options;
        this.placeholder = placeholder;
        this.h(20);
    }

    public UIComboBox custom(IKey label, Consumer<UIComboBox> custom)
    {
        this.customLabel = label;
        this.custom = custom;

        return this;
    }

    public String getValue()
    {
        return this.value;
    }

    /** Sets the value and hands it on, as a pick from the list would. */
    public void pick(String value)
    {
        this.value = value == null ? "" : value;
        this.setter.accept(this.value);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || context.mouseButton != 0)
        {
            return false;
        }

        this.options.accept((list) ->
        {
            UIContext ctx = this.getContext();

            if (ctx == null)
            {
                return;
            }

            ctx.replaceContextMenu((menu) ->
            {
                for (FlowFields.Option option : list)
                {
                    menu.action(option.value().equals(this.value) ? Icons.CHECKMARK : Icons.CONVERT, IKey.constant(option.label()), () -> this.pick(option.value()));
                }

                if (this.custom != null)
                {
                    menu.action(Icons.EDIT, this.customLabel, () -> this.custom.accept(this));
                }
                else
                {
                    menu.action(Icons.EDIT, FlowFields.key("combo_type"), () ->
                    {
                        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(FlowFields.key("combo_type"), this.placeholder == null ? FlowFields.key("combo_type") : this.placeholder, this::pick);

                        prompt.text.setText(this.value);
                        UIOverlay.addOverlay(ctx, prompt);
                    });
                }
            }, this.area.x, this.area.ey());
        });

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        boolean hover = this.area.isInside(context);
        int x = this.area.x;
        int y = this.area.y;
        int ex = this.area.ex();
        int ey = this.area.ey();

        FlowTheme.rounded(context.batcher, x, y, ex, ey, 3, hover ? FlowTheme.accent() : FlowTheme.border(), true, true);
        FlowTheme.rounded(context.batcher, x + 1, y + 1, ex - 1, ey - 1, 2, FlowTheme.field(), true, true);

        boolean empty = this.value.isEmpty();
        String label = empty && this.placeholder != null ? this.placeholder.get() : this.value;
        String shown = context.batcher.getFont().limitToWidth(label, this.area.w - 22);

        context.batcher.text(shown, x + 6, this.area.my() - 4, empty ? FlowTheme.dim() : FlowTheme.text());

        /* The chevron: a small triangle pointing down, part of the field */
        int cx = ex - 10;
        int cy = this.area.my() - 1;
        int color = hover ? FlowTheme.accent() : FlowTheme.dim();

        context.batcher.box(cx - 3, cy - 1, cx + 4, cy, color);
        context.batcher.box(cx - 2, cy, cx + 3, cy + 1, color);
        context.batcher.box(cx - 1, cy + 1, cx + 2, cy + 2, color);
        context.batcher.box(cx, cy + 2, cx + 1, cy + 3, color);

        super.render(context);
    }
}
