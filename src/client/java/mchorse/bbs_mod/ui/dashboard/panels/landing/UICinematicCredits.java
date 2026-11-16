package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The credits as the end of a film: the whole screen goes dark and the names rise from the bottom of
 * it to the top, soft at both edges, to the end, which closes it after a few seconds.
 *
 * <p>The wheel moves the credits by hand, a held left button or Space stops them and lets them go
 * again, and the cross at the top right or Esc closes the screen. Nothing else gets a key or a click
 * while it is open. The text avoids the capital dotted I, which the pixel font of the studio draws
 * wrongly.</p>
 */
public class UICinematicCredits extends UIElement
{
    private static final int BACKGROUND = 0xFF0B0F19;
    private static final float SPEED = 42F;
    private static final int FADE = 60;
    private static final long END_WAIT_MS = 3500L;

    /** One line of the credits: its text, its size against the normal text, its colour and the space under it */
    private record Line(String text, float scale, int color, int gap)
    {}

    private float offset;
    private boolean paused;
    private long last;
    private long endedAt;
    private final int[] cross = new int[4];

    /** Opens the credits over everything the menu has open */
    public static void open(UIContext context)
    {
        UIElement overlay = context.menu.overlay;
        UICinematicCredits credits = new UICinematicCredits();

        credits.full(overlay);
        overlay.add(credits);
        overlay.resize();
    }

    private void close()
    {
        this.removeFromParent();
    }

    /** Who the mod loader lists as contributors, three names to a line */
    private static List<String> contributors()
    {
        List<String> lines = new ArrayList<>();

        try
        {
            ModContainer container = FabricLoader.getInstance().getModContainer(BBSMod.MOD_ID).orElse(null);

            if (container != null)
            {
                StringBuilder line = new StringBuilder();
                int count = 0;

                for (Person person : container.getMetadata().getContributors())
                {
                    line.append(count > 0 ? "  ·  " : "").append(person.getName());
                    count += 1;

                    if (count == 3)
                    {
                        lines.add(line.toString());
                        line.setLength(0);
                        count = 0;
                    }
                }

                if (count > 0)
                {
                    lines.add(line.toString());
                }
            }
        }
        catch (Throwable ignored)
        {}

        return lines;
    }

    private static String version()
    {
        try
        {
            ModContainer container = FabricLoader.getInstance().getModContainer(BBSMod.MOD_ID).orElse(null);

            return container == null ? "" : container.getMetadata().getVersion().getFriendlyString();
        }
        catch (Throwable e)
        {
            return "";
        }
    }

    private List<Line> build()
    {
        int accent = Colors.A100 | BBSSettings.primaryColor.get();
        int white = 0xFFF2F5FA;
        int soft = 0xFFB4BCCB;
        int dim = 0xFF7C8598;
        List<Line> lines = new ArrayList<>();
        String version = version();

        lines.add(new Line("Blockbuster Catalyst Studio", 2.4F, white, 10));

        if (!version.isEmpty())
        {
            lines.add(new Line(UIKeys.CREDITS_VERSION.format(version).get(), 1F, dim, 4));
        }

        lines.add(new Line("", 1F, white, 56));

        Object[][] people = {
            {"Kynix", UIKeys.CREDITS_ROLE_KYNIX, null, 0xFFB48CFF},
            {"McHorse", UIKeys.CREDITS_ROLE_MCHORSE, UIKeys.CREDITS_LICENSE_MIT, 0xFF4C9CFF},
            {"Wemppy", UIKeys.CREDITS_ROLE_WEMPPY, null, 0xFF6CE08A},
            {"qualett", UIKeys.CREDITS_ROLE_QUALETT, UIKeys.CREDITS_LICENSE_MIT, 0xFFFFB347},
            {"Glaxium", UIKeys.CREDITS_ROLE_GLAXIUM, null, 0xFFFF7A90}
        };

        for (Object[] person : people)
        {
            lines.add(new Line((String) person[0], 1.8F, (Integer) person[3], 8));
            lines.add(new Line(((IKey) person[1]).get(), 1F, soft, person[2] == null ? 44 : 6));

            if (person[2] != null)
            {
                lines.add(new Line(((IKey) person[2]).get(), 0.9F, dim, 44));
            }
        }

        lines.add(new Line(UIKeys.CREDITS_CONTRIBUTIONS.get(), 1.5F, accent, 16));
        lines.add(new Line(UIKeys.CREDITS_THANKS.get(), 1F, soft, 8));
        lines.add(new Line(UIKeys.LANGUAGE_CREDITS.get(), 1F, soft, 8));

        for (String names : contributors())
        {
            lines.add(new Line(names, 1F, white, 8));
        }

        lines.add(new Line(UIKeys.CREDITS_NAMES_WHERE.get(), 0.9F, dim, 26));
        lines.add(new Line(UIKeys.CREDITS_LICENSES.get(), 1F, soft, 8));
        lines.add(new Line("THIRD_PARTY_LICENSES.md", 1F, white, 120));

        return lines;
    }

    private int height(List<Line> lines, int fontHeight)
    {
        int total = 0;

        for (Line line : lines)
        {
            total += Math.round(fontHeight * line.scale()) + line.gap();
        }

        return total;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && context.mouseX >= this.cross[0] && context.mouseX < this.cross[2] && context.mouseY >= this.cross[1] && context.mouseY < this.cross[3])
        {
            this.close();
        }

        return true;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        this.offset = Math.max(0F, this.offset - (float) context.mouseWheel * 48F);
        this.endedAt = 0L;

        return true;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.close();
        }
        else if (context.isPressed(GLFW.GLFW_KEY_SPACE))
        {
            this.paused = !this.paused;
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        long now = System.nanoTime();
        float dt = this.last == 0L ? 0F : Math.min(0.1F, (now - this.last) / 1.0E9F);

        this.last = now;

        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;
        int cx = this.area.mx();
        int fontHeight = context.batcher.getFont().getHeight();
        List<Line> lines = this.build();
        int total = this.height(lines, fontHeight);
        boolean holding = Window.isMouseButtonPressed(0) && !(context.mouseX >= this.cross[0] && context.mouseX < this.cross[2] && context.mouseY >= this.cross[1] && context.mouseY < this.cross[3]);

        /* The credits rise by themselves, unless held or paused; at the end they wait a moment and the screen closes */
        if (!this.paused && !holding)
        {
            this.offset += SPEED * dt;
        }

        float top = y + h - this.offset;

        if (top + total < y)
        {
            if (this.endedAt == 0L)
            {
                this.endedAt = System.currentTimeMillis();
            }
            else if (System.currentTimeMillis() - this.endedAt > END_WAIT_MS)
            {
                this.close();

                return;
            }
        }
        else
        {
            this.endedAt = 0L;
        }

        /* The dark stage, with the edges darker still */
        context.batcher.box(x, y, x + w, y + h, BACKGROUND);
        context.batcher.gradientHBox(x, y, x + w / 4, y + h, 0x99000000, 0x00000000);
        context.batcher.gradientHBox(x + w - w / 4, y, x + w, y + h, 0x00000000, 0x99000000);

        context.batcher.clip(x, y, w, h, context);

        MatrixStack stack = context.batcher.getContext().getMatrices();
        float line = top;

        for (Line entry : lines)
        {
            int size = Math.round(fontHeight * entry.scale());

            if (!entry.text().isEmpty() && line + size > y && line < y + h)
            {
                int width = context.batcher.getFont().getWidth(entry.text());

                stack.push();
                stack.translate(cx, line, 0);
                stack.scale(entry.scale(), entry.scale(), 1F);
                context.batcher.text(entry.text(), -width / 2, 0, entry.color(), entry.scale() > 1.2F);
                context.batcher.flush();
                stack.pop();
            }

            line += size + entry.gap();
        }

        context.batcher.unclip(context);

        /* Soft at the top and the bottom: the names come out of the dark and go back into it */
        context.batcher.gradientVBox(x, y, x + w, y + FADE, BACKGROUND, 0x000B0F19);
        context.batcher.gradientVBox(x, y + h - FADE, x + w, y + h, 0x000B0F19, BACKGROUND);

        /* The cross at the top right */
        this.cross[0] = x + w - 40;
        this.cross[1] = y + 14;
        this.cross[2] = x + w - 14;
        this.cross[3] = y + 40;

        boolean hover = context.mouseX >= this.cross[0] && context.mouseX < this.cross[2] && context.mouseY >= this.cross[1] && context.mouseY < this.cross[3];

        context.batcher.box(this.cross[0], this.cross[1], this.cross[2], this.cross[3], hover ? 0x44FFFFFF : 0x1AFFFFFF);
        context.batcher.icon(Icons.CLOSE, hover ? 0xFFFFFFFF : 0xCCFFFFFF, this.cross[0] + 5, this.cross[1] + 5);

        if (this.paused)
        {
            String text = UIKeys.CREDITS_PAUSED.get();

            context.batcher.text(text, cx - context.batcher.getFont().getWidth(text) / 2, y + h - FADE / 2 - fontHeight / 2, 0xFF7C8598, false);
        }
    }
}
