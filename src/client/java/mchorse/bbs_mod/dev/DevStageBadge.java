package mchorse.bbs_mod.dev;

import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * What a build that is not PUBLIC says about itself: a thin badge at the top centre of the screen,
 * a tag beside the hub titles and a suffix on the window title. Everything is silent at PUBLIC.
 */
public final class DevStageBadge
{
    private DevStageBadge()
    {}

    public static boolean visible()
    {
        return DevConfig.getStage() != DevStage.PUBLIC;
    }

    /** Red for the builds still being made, yellow for the ones out for testing and rating */
    public static int color()
    {
        return switch (DevConfig.getStage())
        {
            case DEVELOPMENT, PRIVATE -> 0xffff5a4d;
            default -> 0xffffd23f;
        };
    }

    public static String name()
    {
        return DevConfig.getStage().name();
    }

    public static String screenLabel()
    {
        return "[BBS CS - " + name() + " BUILD]";
    }

    /** What follows the game's own window title, or nothing at PUBLIC */
    public static String windowSuffix()
    {
        return visible() ? "- [Blockbuster Catalyst Studio " + name() + "]" : "";
    }

    /** The semi-transparent badge at the top centre */
    public static void render(DrawContext context)
    {
        if (!visible())
        {
            return;
        }

        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String text = screenLabel();
        int w = font.getWidth(text) + 8;
        int x = (context.getScaledWindowWidth() - w) / 2;
        int color = color();

        context.fill(x, 2, x + w, 4 + font.fontHeight + 2, 0x60000000);
        context.fill(x, 2, x + w, 3, (color & 0xffffff) | 0xa0000000);
        context.drawText(font, text, x + 4, 5, (color & 0xffffff) | 0xb0000000, true);
    }

    /** The tag to the right of a hub screen's version line: "[DEVELOPMENT]" in the stage's colour */
    public static void renderHubTag(Batcher2D batcher, int x, int y)
    {
        if (visible())
        {
            batcher.text("[" + name() + "]", x, y, color(), true);
        }
    }

    /** Applies a changed stage to the window title right away */
    public static void refreshWindowTitle()
    {
        try
        {
            MinecraftClient.getInstance().updateWindowTitle();
        }
        catch (Throwable ignored)
        {}
    }
}
