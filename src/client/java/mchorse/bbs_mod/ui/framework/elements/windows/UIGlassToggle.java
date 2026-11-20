package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;

import java.util.function.Consumer;

/**
 * A switch: a 30 by 15 pill whose knob slides between off on the left and on on the right, in
 * the look of the interface theme, see {@link UITheme}.
 */
public class UIGlassToggle extends UIToggle
{
    private static final int W = 30;
    private static final int H = 15;

    public UIGlassToggle(IKey label, boolean value, Consumer<UIToggle> callback)
    {
        super(label, value, callback);

        this.h(20);
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        paint(this, context, this.hover);
    }

    /** Draws any toggle as the pill switch; plain toggles inside a glass window come through here. */
    public static void paint(UIToggle toggle, UIContext context, boolean hover)
    {
        Area a = toggle.area;
        boolean on = toggle.getValue();

        if (toggle.glassSlide < 0F)
        {
            toggle.glassSlide = on ? 1F : 0F;
        }

        toggle.glassSlide += ((on ? 1F : 0F) - toggle.glassSlide) * 0.35F;

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(toggle.label.get(), a.w - W - 10);

        context.batcher.text(label, a.x, a.my(font.getHeight()), toggle.color, false);

        int x = a.ex() - W;
        int y = a.my() - H / 2;
        UITheme.toggle(context.batcher, x, y, W, H, on, hover, toggle.glassSlide);

        toggle.renderLockedArea(context);
    }
}
