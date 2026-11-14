package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The modern text input window: a centered title with the close button, a label and a wide rounded
 * text box in the body, and a footer strip with a secondary Cancel and a primary Create on the right.
 * It is what asking for the name of a new particle or model looks like.
 */
public class UINameInputOverlayPanel extends UIOverlayPanel
{
    private static final int FOOTER = 36;

    public final UITextbox text;
    public final UIButton create;

    private final Consumer<String> callback;

    public UINameInputOverlayPanel(IKey title, IKey label, Consumer<String> callback)
    {
        super(title);

        this.callback = callback;
        this.icon(Icons.ADD);
        this.footer(FOOTER);

        UILabel caption = new UILabel(label, Colors.setA(Colors.WHITE, 0.75F));

        caption.labelAnchor(0, 0.5F);
        caption.relative(this.content).xy(16, 14).w(1F, -32).h(14);

        this.text = new UITextbox(null);
        this.text.relative(this.content).xy(16, 34).w(1F, -32).h(24);

        this.content.add(caption, this.text);

        UIButton cancel = new UIButton(UIKeys.SANDBOX_CANCEL, (b) -> this.close());

        this.create = new UIButton(UIKeys.CATALYST_DIALOG_CREATE, (b) -> this.confirm()).primary();

        UIElement bar = UI.row(6, 0, 22, cancel, this.create);

        bar.relative(this).x(1F, -214).y(1F, -FOOTER + 7).w(202).h(22);
        this.add(bar);
    }

    @Override
    public int getPreferredWidth()
    {
        return 380;
    }

    @Override
    public int getContentHeight()
    {
        return TITLE_HEIGHT + 74 + FOOTER;
    }

    @Override
    public boolean isResizable()
    {
        return false;
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.text.textbox.moveCursorToEnd();
        parent.getContext().focus(this.text);
    }

    @Override
    public void confirm()
    {
        String name = this.text.getText();

        this.close();

        if (this.callback != null)
        {
            this.callback.accept(name);
        }
    }
}
