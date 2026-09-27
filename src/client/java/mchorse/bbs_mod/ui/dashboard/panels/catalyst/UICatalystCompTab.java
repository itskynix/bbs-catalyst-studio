package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * After Effects style Composition tab element.
 * Displays comp scene icon, name, active highlight accent, and a close 'x' button.
 *
 * Close button hit-testing is done inside the click callback:
 * if the click lands on the close 'x' region, the closeCallback is invoked instead
 * of the regular tab-select callback. This avoids overriding the final mouseClicked()
 * in UIElement.
 */
public class UICatalystCompTab extends UIClickable<UICatalystCompTab>
{
    public final CatalystComposition composition;
    public final int index;
    public BooleanSupplier active;
    public Consumer<Integer> closeCallback;

    public UICatalystCompTab(CatalystComposition composition, int index, BooleanSupplier active,
                             Consumer<Integer> clickCallback, Consumer<Integer> closeCallback)
    {
        super((t) ->
        {
            /* If the click landed on the close-button zone, fire closeCallback */
            if (closeCallback != null && t.isOverCloseButton())
            {
                closeCallback.accept(index);
            }
            else
            {
                clickCallback.accept(index);
            }
        });

        this.composition = composition;
        this.index = index;
        this.active = active;
        this.closeCallback = closeCallback;
        this.h(20);
    }

    /** Returns true if the current mouse position is inside the close 'x' hit area. */
    public boolean isOverCloseButton()
    {
        /* This method is called from the lambda above where 't' is this instance. */
        /* We cannot access UIContext inside the callback directly, so we use a stored flag. */
        return this.overClose;
    }

    /** Updated each frame in renderSkin so the callback lambda can read it. */
    private boolean overClose = false;

    @Override
    protected UICatalystCompTab get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        boolean isAct = this.active != null && this.active.getAsBoolean();
        int primary = BBSSettings.primaryColor.get();

        /* Track whether mouse is hovering the close button this frame */
        int closeX = this.area.ex() - 16;
        int closeY = this.area.y + 2;
        this.overClose = this.closeCallback != null
            && context.mouseX >= closeX && context.mouseX <= closeX + 14
            && context.mouseY >= closeY && context.mouseY <= closeY + 16;

        /* Background */
        if (isAct)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF282C38);
            /* Top primary accent line */
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 2, primary | Colors.A100);
        }
        else if (this.hover)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF20222A);
        }
        else
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF15171D);
        }

        /* Border right divider */
        context.batcher.box(this.area.ex() - 1, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

        /* Comp icon */
        int x = this.area.x + 6;
        context.batcher.icon(Icons.SCENE, isAct ? Colors.WHITE : Colors.GRAY, x, this.area.my() - 8);
        x += 18;

        /* Comp title */
        FontRenderer font = context.batcher.getFont();
        int maxTitleW = this.area.w - 38;
        String title = font.limitToWidth(this.composition.name, maxTitleW);
        context.batcher.text(title, x, this.area.my() - font.getHeight() / 2, isAct ? Colors.WHITE : Colors.LIGHTEST_GRAY, false);

        /* Close 'x' button */
        if (this.closeCallback != null)
        {
            context.batcher.icon(Icons.CLOSE, this.overClose ? Colors.NEGATIVE : (isAct ? Colors.LIGHTEST_GRAY : 0x88AAAAAA), closeX, this.area.my() - 8);
        }
    }
}
