package mchorse.bbs_mod.ui.framework.elements.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.InterfaceBlur;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector2i;

import java.util.HashMap;
import java.util.Map;

public class UIOverlay extends UIElement
{
    private static final Map<String, Vector2i> offsets = new HashMap<>();

    /** Sizes the user dragged panels to, by class, alongside {@link #offsets}. */
    private static final Map<String, Vector2i> sizes = new HashMap<>();

    /**
     * How large a panel opened without a size may grow. The default is a share of the screen, and a
     * share of a wide one is a lot of empty box around content that doesn't grow with it.
     */
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 340;

    /** Null means the dimming from the settings; a set value overrides it. */
    private Integer background;

    /** When the window began opening or closing, and whether it is on its way out */
    private final long animationStart = System.nanoTime();
    private long closeStart;
    private boolean closing;

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel)
    {
        UIOverlay overlay = new UIOverlay();
        int width = panel.getPreferredWidth();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(0.5F, 0.5F).anchor(0.5F).bounds(overlay, 0);

        if (width > 0)
        {
            panel.w(width);
        }
        else
        {
            panel.getFlex().w.max = MAX_WIDTH;
        }

        panel.getFlex().h.max = MAX_HEIGHT;
        panel.sizeless();

        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, float w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, int h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).w(w).h(h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayLeft(context, panel, w, 10);
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(padding).y(padding).w(w).h(1F, -padding * 2).anchor(0F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayRight(context, panel, w, 10);
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(1F, -padding).y(padding).w(w).h(1F, -padding * 2).anchor(1F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static void setupPanel(UIContext context, UIOverlay overlay, UIOverlayPanel panel)
    {
        /* A panel kept between openings (the settings) may still be fading out of its last one: that
         * window is over, and the panel is handed to the new one in working order */
        if (panel.getParent() instanceof UIOverlay old && old.closing)
        {
            old.finishClosing();
        }

        if (panel.hasParent())
        {
            return;
        }

        panel.setEnabled(true);

        Flex flex = panel.getFlex();
        Vector2i offset = offsets.get(panel.getClass().getSimpleName());

        panel.setInitialOffset(flex.x.offset, flex.y.offset);
        panel.setupResize();

        if (offset != null)
        {
            flex.x.offset = offset.x;
            flex.y.offset = offset.y;
        }

        Vector2i size = sizes.get(panel.getClass().getSimpleName());

        /* A size the user picked outranks both the share of the screen and the cap on it */
        if (size != null && panel.isResizable())
        {
            flex.w.max = flex.h.max = 0;
            flex.w.set(0F, size.x);
            flex.h.set(0F, size.y);
        }

        overlay.full(context.menu.overlay);
        context.menu.overlay.add(overlay);
        overlay.add(panel);
        context.menu.overlay.resize();
    }

    public static boolean has(UIContext context)
    {
        /* A window fading out is already closed to everything but the eye */
        for (UIOverlayPanel panel : context.menu.getRoot().getChildren(UIOverlayPanel.class))
        {
            if (!(panel.getParent() instanceof UIOverlay overlay) || !overlay.closing)
            {
                return true;
            }
        }

        return false;
    }

    public UIOverlay()
    {
        this.eventPropagataion(EventPropagation.BLOCK).markContainer();
    }

    public UIOverlay background(int background)
    {
        this.background = background;

        return this;
    }

    public UIOverlay noBackground()
    {
        return this.background(0);
    }

    /**
     * Closes the window: it is closed at once as far as everyone else can tell - its panels are told,
     * its place is remembered - and then fades out, being taken off the screen when the animation
     * has run. Without animations it simply goes.
     */
    public void closeItself()
    {
        if (this.closing)
        {
            return;
        }

        UIUtils.playClick();

        boolean animated = OverlayAnimation.isEnabled() && this.hasParent();

        if (animated)
        {
            this.closing = true;
            this.closeStart = System.nanoTime();
        }
        else
        {
            this.removeFromParent();
        }

        for (UIOverlayPanel element : this.getChildren(UIOverlayPanel.class))
        {
            if (animated)
            {
                element.setEnabled(false);
            }
            else
            {
                element.removeFromParent();
            }

            element.onClose();

            /* Save offset, and the size if the user picked one */
            Vector2i offset = new Vector2i(element.getFlex().x.offset, element.getFlex().y.offset);

            offsets.put(element.getClass().getSimpleName(), offset);

            if (element.wasResized())
            {
                Vector2i size = new Vector2i(element.getFlex().w.offset, element.getFlex().h.offset);

                sizes.put(element.getClass().getSimpleName(), size);
            }
        }
    }

    /** Takes a window that is fading out off the screen now, handing its panels back usable. */
    private void finishClosing()
    {
        this.removeFromParent();

        for (UIOverlayPanel element : this.getChildren(UIOverlayPanel.class))
        {
            element.removeFromParent();
            element.setEnabled(true);
        }
    }

    /* Don't pass user input down the line... */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.closing)
        {
            return true;
        }

        this.closeItself();

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        int background = this.background == null ? BBSSettings.overlayBackground() : this.background;
        boolean animated = OverlayAnimation.isEnabled();
        double progress = 1D;

        if (animated)
        {
            long now = System.nanoTime();
            long duration = OverlayAnimation.duration();
            long elapsed = this.closing ? now - this.closeStart : now - this.animationStart;

            if (this.closing && elapsed >= duration)
            {
                this.finishClosing();

                return;
            }

            progress = OverlayAnimation.progress(elapsed / (double) duration, this.closing);
        }
        else if (this.closing)
        {
            this.finishClosing();

            return;
        }

        float alpha = (float) Math.min(Math.max(progress, 0D), 1D);

        /* An overlay that asked for no background of its own gets no blur either; the setting
         * turned down to zero is a different thing — that is the dimming off, not the glass */
        if (this.background == null || Colors.getA(this.background) > 0F)
        {
            InterfaceBlur.apply();
        }

        if (alpha < 1F)
        {
            background = Colors.setA(background, Colors.getA(background) * alpha);
        }

        if (Colors.getA(background) > 0F)
        {
            this.area.render(context.batcher, background);
        }

        if (!animated || (progress >= 1D && !this.closing))
        {
            super.render(context);

            return;
        }

        /* The windows scale about their own middle and fade with the same progress. The areas the
         * mouse is tested against stay where they settle, so a click lands on what is drawn once
         * the window has stopped, and the middle never moves under the cursor. */
        UIOverlayPanel panel = this.getChildren(UIOverlayPanel.class).isEmpty() ? null : this.getChildren(UIOverlayPanel.class).get(0);
        float scale = (float) OverlayAnimation.scale(progress);
        MatrixStack stack = context.batcher.getContext().getMatrices();
        float px = panel == null ? this.area.mx() : panel.area.mx();
        float py = panel == null ? this.area.my() : panel.area.my();

        context.batcher.flush();
        stack.push();
        stack.translate(px, py, 0F);
        stack.scale(scale, scale, 1F);
        stack.translate(-px, -py, 0F);
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);

        try
        {
            super.render(context);
            context.batcher.flush();
        }
        finally
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            stack.pop();
        }
    }
}