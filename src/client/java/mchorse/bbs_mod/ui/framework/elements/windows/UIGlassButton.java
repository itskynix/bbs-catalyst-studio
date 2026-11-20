package mchorse.bbs_mod.ui.framework.elements.windows;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * A desktop button with rounded corners and a faint glint along its top edge. The primary one is
 * filled with the accent and reads as the thing to press; the secondary one is dark glass with a
 * thin frame, so Cancel and Apply never look alike.
 */
public class UIGlassButton extends UIButton
{
    private static final int RADIUS = 4;

    public static final int SECONDARY_FILL = 0xe61c2433;
    public static final int SECONDARY_HOVER = 0xf0263249;
    public static final int SECONDARY_FRAME = 0xff344258;

    private final boolean primary;

    public UIGlassButton(IKey label, boolean primary, Consumer<UIButton> callback)
    {
        super(label, callback);

        this.primary = primary;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        paint(this, context, this.primary, this.hover);
    }

    /** Draws any button in the glass style; plain buttons inside a glass window come through here. */
    public static void paint(UIButton button, UIContext context, boolean primary, boolean hover)
    {
        Area a = button.area;
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        paintGround(context, a, primary, hover);

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(button.label.get(), a.w - 8);

        if (FrutigerAero.on())
        {
            /* White on the turquoise gel, ocean navy on the ice glass */
            int x = a.mx(font.getWidth(label));
            int y = a.my(font.getHeight());

            if (primary)
            {
                Batcher2D.keepWhite += 1;
                context.batcher.text(label, x, y, Colors.WHITE, false);
                Batcher2D.keepWhite -= 1;
            }
            else
            {
                context.batcher.text(label, x, y, Colors.A100 | FrutigerAero.INK, false);
            }
        }
        else
        {
            context.batcher.text(label, a.mx(font.getWidth(label)), a.my(font.getHeight()), UITheme.buttonText(primary, contrast(accent)), false);
        }

        button.renderLockedArea(context);
    }

    /** A button that has a colour of its own: the same rounded body, filled with it. */
    public static void paintColored(UIButton button, UIContext context, int fill)
    {
        Area a = button.area;

        paintColoredGround(context, a, fill);

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(button.label.get(), a.w - 8);

        context.batcher.text(label, a.mx(font.getWidth(label)), a.my(font.getHeight()), button.textColor, button.textShadow);

        button.renderLockedArea(context);
    }

    /**
     * The body of a button without its label, for any control that wants to look like one, in the look
     * of the interface theme (see {@link UITheme}). One batch.
     */
    public static void paintGround(UIContext context, Area a, boolean primary, boolean hover)
    {
        UITheme.button(context.batcher, a, RADIUS, primary, hover);
    }

    /** The same body filled with a colour of its own. One batch. */
    public static void paintColoredGround(UIContext context, Area a, int fill)
    {
        boolean opened = RoundedDraw.begin(context.batcher);

        RoundedDraw.fill(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS, RoundedDraw.ALL, fill | Colors.A100);

        for (int k = 0; k < 4; k++)
        {
            RoundedDraw.band(context.batcher, a.x, a.y, a.ex(), a.ey(), RADIUS, RoundedDraw.ALL, a.y + k, a.y + k + 1, Colors.setA(Colors.WHITE, 0.16F * (1F - k / 4F)));
        }

        RoundedDraw.end(context.batcher, opened);
    }

    /** Black on a light accent, white on a dark one. */
    public static int contrast(int color)
    {
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;

        return (0.299F * r + 0.587F * g + 0.114F * b) > 150F ? 0xff10141c : Colors.WHITE;
    }
}
