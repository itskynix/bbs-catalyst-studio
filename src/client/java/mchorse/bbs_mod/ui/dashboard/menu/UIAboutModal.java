package mchorse.bbs_mod.ui.dashboard.menu;

import mchorse.bbs_mod.dev.BuildInfo;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;

/**
 * "About BBS CS": the same small window whichever editor it is opened from. It cannot be sized — there
 * is nothing in it to give more room — and closes with the X or Escape.
 */
public class UIAboutModal extends UIOverlayPanel
{
    public static final int WIDTH = 340;
    public static final int HEIGHT = 244;

    private static final String CREDITS = "Kynix • McHorse, Wemppy, qualet, Glaxium";

    private final UIButton changelog;

    public UIAboutModal(UIDashboard dashboard)
    {
        super(UIKeys.ABOUT_TITLE);

        this.changelog = new UIButton(UIKeys.ABOUT_VIEW_CHANGES, (b) ->
        {
            UIContext context = this.getContext();

            this.close();
            dashboard.openChangelog(context);
        });
        this.changelog.relative(this.content).x(0.5F).y(1F, -30).wh(180, 22).anchor(0.5F, 0F);

        this.content.add(new UIRenderable(this::renderInfo), this.changelog);
    }

    @Override
    public boolean isResizable()
    {
        return false;
    }

    private void renderInfo(UIContext context)
    {
        BuildInfo info = BuildInfo.get();
        FontRenderer font = context.batcher.getFont();
        int cx = this.content.area.mx();
        int y = this.content.area.y + 14;

        /* The emblem and the name */
        context.batcher.icon(Icons.FIVE_STAR, 0xFF4C9CFF, cx, y + 12, 0.5F, 0.5F);
        y += 34;

        this.scaled(context, "BBS CS", cx, y, 2F, Colors.WHITE);
        y += font.getHeight() * 2 + 4;

        String subtitle = UIKeys.ABOUT_SUBTITLE.get();

        context.batcher.text(subtitle, cx - font.getWidth(subtitle) / 2, y, Colors.setA(Colors.WHITE, 0.7F), false);
        y += font.getHeight() + 14;

        int x = this.content.area.x + 20;
        int right = this.content.area.ex() - 20;

        y = this.row(context, UIKeys.ABOUT_VERSION.get(), info.versionLine(UIKeys.ABOUT_MINECRAFT.get()), x, right, y);
        y = this.row(context, UIKeys.ABOUT_BUILD.get(), "Build " + info.buildLine(), x, right, y);

        /* The channel as a badge */
        context.batcher.text(UIKeys.ABOUT_CHANNEL.get(), x, y + 3, Colors.setA(Colors.WHITE, 0.55F), false);

        int color = stageColor(info.stage);
        int bw = font.getWidth(info.stage) + 12;

        context.batcher.box(right - bw, y, right, y + 15, Colors.setA(color, 0.25F));
        context.batcher.outline(right - bw, y, right, y + 15, color);
        context.batcher.text(info.stage, right - bw + 6, y + 4, color, false);
        y += 21;

        y = this.row(context, UIKeys.ABOUT_AUTHORS.get(), "", x, right, y);

        int w = font.getWidth(CREDITS);

        context.batcher.text(CREDITS, Math.max(x, cx - w / 2), y - 4, Colors.setA(Colors.WHITE, 0.85F), false);
    }

    private int row(UIContext context, String label, String value, int x, int right, int y)
    {
        FontRenderer font = context.batcher.getFont();

        context.batcher.text(label, x, y + 3, Colors.setA(Colors.WHITE, 0.55F), false);
        context.batcher.text(value, right - font.getWidth(value), y + 3, Colors.WHITE, false);

        return y + 21;
    }

    private void scaled(UIContext context, String text, int cx, int y, float scale, int color)
    {
        MatrixStack stack = context.batcher.getContext().getMatrices();
        int w = context.batcher.getFont().getWidth(text);

        stack.push();
        stack.translate(cx - w * scale / 2F, y, 0F);
        stack.scale(scale, scale, 1F);
        context.batcher.text(text, 0, 0, color, true);
        stack.pop();
    }

    private static int stageColor(String stage)
    {
        return switch (stage)
        {
            case "PUBLIC" -> 0xFF6CE08A;
            case "TESTER" -> 0xFFFFB347;
            case "DEV", "DEVELOPMENT" -> 0xFFB48CFF;
            default -> 0xFF4C9CFF;
        };
    }
}
