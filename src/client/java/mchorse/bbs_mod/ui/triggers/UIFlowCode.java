package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The flow written out as Java, read only: line numbers in a gutter, keywords, strings and comments
 * coloured, the wheel scrolls.
 */
public class UIFlowCode extends UIElement
{
    private static final Set<String> KEYWORDS = Set.of("public", "private", "class", "extends", "void", "if", "else", "new", "return",
        "package", "import", "boolean", "int", "float", "long", "double", "true", "false", "null", "this", "final", "static");
    private static final int LINE = 11;

    private final List<String> lines = new ArrayList<>();
    private String code = "";
    private int scroll;

    public void setCode(String code)
    {
        if (code.equals(this.code))
        {
            return;
        }

        this.code = code;
        this.lines.clear();
        this.lines.addAll(List.of(code.split("\n", -1)));
        this.scroll = Math.min(this.scroll, Math.max(0, this.lines.size() - 1));
    }

    public String getCode()
    {
        return this.code;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        int visible = Math.max(1, (this.area.h - 12) / LINE);
        int max = Math.max(0, this.lines.size() - visible);

        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(context.mouseWheel) * 3));

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int gutter = 34;

        context.batcher.box(x, y, this.area.ex(), this.area.ey(), 0xff1a1c20);
        context.batcher.box(x, y, x + gutter, this.area.ey(), 0xff15171a);
        context.batcher.clip(this.area, context);

        int visible = (this.area.h - 12) / LINE + 1;

        for (int i = 0; i < visible && i + this.scroll < this.lines.size(); i++)
        {
            int index = i + this.scroll;
            int ly = y + 6 + i * LINE;
            String number = String.valueOf(index + 1);

            context.batcher.text(number, x + gutter - 6 - context.batcher.getFont().getWidth(number), ly, 0xff5b6470);
            this.renderLine(context, this.lines.get(index), x + gutter + 8, ly);
        }

        context.batcher.unclip(context);

        super.render(context);
    }

    /** One line, coloured token by token. */
    private void renderLine(UIContext context, String line, int x, int y)
    {
        String trimmed = line.trim();

        if (trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("//"))
        {
            context.batcher.text(line, x, y, 0xff6f7b88);

            return;
        }

        int i = 0;
        int cx = x;

        while (i < line.length())
        {
            char c = line.charAt(i);
            int end;
            int color;

            if (c == '"')
            {
                end = i + 1;

                while (end < line.length() && (line.charAt(end) != '"' || line.charAt(end - 1) == (char) 92))
                {
                    end++;
                }

                end = Math.min(line.length(), end + 1);
                color = 0xff9fd47a;
            }
            else if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*')
            {
                end = line.length();
                color = 0xff6f7b88;
            }
            else if (Character.isJavaIdentifierStart(c))
            {
                end = i + 1;

                while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end)))
                {
                    end++;
                }

                String word = line.substring(i, end);

                color = KEYWORDS.contains(word) ? 0xffd78cf0 : word.equals("Override") ? 0xffe2c08d : Character.isUpperCase(c) ? 0xff6cc6e8 : 0xffe4e8ee;
            }
            else if (Character.isDigit(c))
            {
                end = i + 1;

                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '.'))
                {
                    end++;
                }

                color = 0xfff0a868;
            }
            else
            {
                end = i + 1;
                color = c == '@' ? 0xffe2c08d : 0xffaab3be;
            }

            String token = line.substring(i, end);

            context.batcher.text(token, cx, y, color);
            cx += context.batcher.getFont().getWidth(token);
            i = end;
        }
    }
}
