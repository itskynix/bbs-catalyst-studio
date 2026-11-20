package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.Area;

import java.util.function.Consumer;

/** A text box on dark glass with 4 pixel corners and a thin frame; focused, the frame turns accent with a line along the bottom. */
public class UIGlassTextbox extends UITextbox
{
    private static final int RADIUS = 4;

    public UIGlassTextbox(Consumer<String> callback)
    {
        super(callback);

        /* the box paints its own ground below; the text is drawn over it without one */
        this.background(false);
    }

    @Override
    public void render(UIContext context)
    {
        paintGround(context, this.area, this.isFocused());

        super.render(context);
    }

    /** The box under the text, in the look of the interface theme (see {@link UITheme}). */
    public static void paintGround(UIContext context, Area a, boolean focused)
    {
        UITheme.textbox(context.batcher, a, RADIUS, focused);
    }
}
