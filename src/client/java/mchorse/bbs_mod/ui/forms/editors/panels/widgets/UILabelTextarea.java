package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.Cursor;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;

import java.util.function.Consumer;

/**
 * The label's multi line text field. It remembers the last selected range after the focus goes
 * elsewhere (a text area drops its selection then), so a colour picked beside it can still be put on
 * what was selected.
 */
public class UILabelTextarea extends UITextarea<TextLine>
{
    private int start;
    private int end;

    public UILabelTextarea(Consumer<String> callback)
    {
        super(callback);

        this.background().wrap(true);
    }

    @Override
    public void setText(String text)
    {
        super.setText(text);

        /* A text area drops trailing empty lines on load; a label keeps them */
        int trailing = 0;

        for (int i = text.length() - 1; i >= 0 && text.charAt(i) == '\n'; i--)
        {
            trailing++;
        }

        if (trailing > 0 && !this.getText().equals(text))
        {
            while (this.getText().length() < text.length())
            {
                this.getLines().add(this.createTextLine(""));
            }
        }

        this.start = this.end = 0;
    }

    /** The first character of the remembered selection, counted over the whole text. */
    public int getSelectionStart()
    {
        return this.start;
    }

    /** One past its last character; equal to the start when nothing is selected. */
    public int getSelectionEnd()
    {
        return this.end;
    }

    @Override
    public boolean isUserEditing()
    {
        return this.isFocused();
    }

    @Override
    public void render(UIContext context)
    {
        if (this.isFocused())
        {
            this.remember();
        }

        super.render(context);
    }

    private void remember()
    {
        if (!this.isSelected())
        {
            this.start = this.end = 0;

            return;
        }

        int a = this.offsetOf(this.cursor);
        int b = this.offsetOf(this.selection);

        this.start = Math.min(a, b);
        this.end = Math.max(a, b);
    }

    private int offsetOf(Cursor cursor)
    {
        int offset = 0;

        for (int i = 0; i < this.getLines().size(); i++)
        {
            String line = this.getLines().get(i).text;

            if (i == cursor.line)
            {
                return offset + Math.max(0, Math.min(cursor.offset, line.length()));
            }

            offset += line.length() + 1;
        }

        return Math.max(0, offset - 1);
    }
}
