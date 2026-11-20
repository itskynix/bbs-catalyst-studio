package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The Windows 11 switch: a 32 by 16 pill. Off it is dark glass with a thin grey frame and a small
 * round knob on the left; on it is filled with the accent with a white knob on the right. The knob
 * slides between the two, and swells a little under the mouse.
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
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        if (FrutigerAero.on())
        {
            /* Frosted glass off, an emerald gel on, and a white pearl for the knob */
            boolean opened = RoundedDraw.begin(context.batcher);
            int pearl = H - 4 + (hover ? 1 : 0);
            int px = x + 2 + Math.round((W - H) * toggle.glassSlide);

            FrutigerAero.toggleWell(context.batcher, x, y, W, H, on, hover);
            FrutigerAero.pearl(context.batcher, px, a.my() - pearl / 2, pearl);
            RoundedDraw.end(context.batcher, opened);
            toggle.renderLockedArea(context);

            return;
        }

        if (on)
        {
            RoundedDraw.fill(context.batcher, x, y, x + W, y + H, H / 2, RoundedDraw.ALL, hover ? Colors.mulRGB(accent, 0.9F) : accent);
        }
        else
        {
            RoundedDraw.fill(context.batcher, x, y, x + W, y + H, H / 2, RoundedDraw.ALL, hover ? 0xa0202838 : 0x80141a26);
            RoundedDraw.outline(context.batcher, x, y, x + W, y + H, H / 2, RoundedDraw.ALL, 0xff8a93a0);
        }

        int knob = (on ? 9 : 7) + (hover ? 2 : 0);
        int kx = x + (H - knob) / 2 + Math.round((W - H) * toggle.glassSlide);
        int ky = a.my() - knob / 2;

        RoundedDraw.fill(context.batcher, kx, ky, kx + knob, ky + knob, knob / 2, RoundedDraw.ALL, on ? Colors.WHITE : 0xffc4cbd6);

        toggle.renderLockedArea(context);
    }
}
