package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

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

    /** The box under the text: dark glass, a thin frame, and while focused an accent frame with a line below. */
    public static void paintGround(UIContext context, Area a, boolean focused)
    {
        int accent = Colors.A100 | BBSSettings.primaryColor.get();
        boolean opened = RoundedDraw.begin(context.batcher);

        RoundedDraw.fill(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS, RoundedDraw.ALL, focused ? 0xe60d1118 : 0xd90f141d);
        RoundedDraw.outline(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS, RoundedDraw.ALL, focused ? accent : 0xff344258);

        if (focused)
        {
            context.batcher.box(a.x + RADIUS, a.ey() - 2, a.ex() - RADIUS, a.ey() - 1, accent);
        }

        RoundedDraw.end(context.batcher, opened);
    }
}
