package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The recap transport of the pause menu: a flat strip at the bottom middle in the studio's theme,
 * the running time above a row of icon buttons — start, stop, save, discard, marker and, with
 * Instant Recap switched on, save the last stretch.
 */
public class RecapPauseBar extends ClickableWidget
{
    private static final int BUTTON = 22;
    private static final int BUTTON_H = 20;
    private static final int GAP = 2;
    private static final int GROUP_GAP = 9;
    private static final int PADDING = 4;
    private static final int TIME_H = 12;
    private static final int BOTTOM = 6;

    private enum Action
    {
        START("start", Icons.PLAY),
        STOP("stop", Icons.STOP),
        SAVE("save", Icons.SAVE),
        CANCEL("cancel", Icons.CLOSE),
        MARKER("marker", Icons.BOOKMARK),
        INSTANT("instant", Icons.STOPWATCH);

        final String id;
        final Icon icon;

        Action(String id, Icon icon)
        {
            this.id = id;
            this.icon = icon;
        }
    }

    private final List<Action> actions = new ArrayList<>();
    private final boolean compact;
    private final int buttonsX;
    private final int buttonsY;

    public RecapPauseBar(int screenWidth, int screenHeight)
    {
        super(0, 0, 0, 0, Text.literal(L10n.lang("bbs.recap.bar.title").get()));

        this.actions.add(Action.START);
        this.actions.add(Action.STOP);
        this.actions.add(Action.SAVE);
        this.actions.add(Action.CANCEL);
        this.actions.add(Action.MARKER);

        if (RecapRecorder.isInstantEnabled())
        {
            this.actions.add(Action.INSTANT);
        }

        int rowWidth = this.offset(this.actions.size()) - GAP;
        /* A short window has no room above the buttons: the time goes beside them instead */
        this.compact = screenHeight < 260;

        int timeWidth = this.compact ? 46 : 0;
        int width = rowWidth + timeWidth + PADDING * 2;
        int height = BUTTON_H + PADDING * 2 + (this.compact ? 0 : TIME_H);

        this.setWidth(width);
        this.height = height;
        this.setX((screenWidth - width) / 2);
        this.setY(screenHeight - height - BOTTOM);

        this.buttonsX = this.getX() + PADDING + timeWidth;
        this.buttonsY = this.getY() + PADDING + (this.compact ? 0 : TIME_H);
    }

    /** Where button {@code index} starts, from the first: the capture group, then the marker group. */
    private int offset(int index)
    {
        int x = index * (BUTTON + GAP);

        return index >= 4 ? x + GROUP_GAP : x;
    }

    private boolean enabled(Action action)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        RecapRecorder.State state = RecapRecorder.getState();

        if (mc.world == null)
        {
            return false;
        }

        return switch (action)
        {
            case START -> state == RecapRecorder.State.IDLE;
            case STOP -> state == RecapRecorder.State.RECORDING;
            case SAVE, CANCEL -> state != RecapRecorder.State.IDLE;
            case MARKER -> state == RecapRecorder.State.RECORDING || (state == RecapRecorder.State.IDLE && RecapRecorder.isInstantEnabled());
            case INSTANT -> RecapRecorder.isInstantEnabled();
        };
    }

    private Action actionAt(double mouseX, double mouseY)
    {
        if (mouseY < this.buttonsY || mouseY >= this.buttonsY + BUTTON_H)
        {
            return null;
        }

        for (int i = 0; i < this.actions.size(); i++)
        {
            int x = this.buttonsX + this.offset(i);

            if (mouseX >= x && mouseX < x + BUTTON)
            {
                return this.actions.get(i);
            }
        }

        return null;
    }

    @Override
    public void onClick(double mouseX, double mouseY)
    {
        Action action = this.actionAt(mouseX, mouseY);

        if (action == null || !this.enabled(action))
        {
            return;
        }

        switch (action)
        {
            case START -> RecapRecorder.start();
            case STOP -> RecapRecorder.stop();
            case SAVE -> RecapRecorder.save();
            case CANCEL -> RecapRecorder.cancel();
            case MARKER -> RecapRecorder.marker();
            case INSTANT -> RecapRecorder.saveInstant();
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta)
    {
        Batcher2D batcher = new Batcher2D(context);
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int x = this.getX();
        int y = this.getY();
        int ex = x + this.width;
        int ey = y + this.height;
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        batcher.box(x, y, ex, ey, BBSSettings.deepSurface());
        batcher.outline(x, y, ex, ey, BBSSettings.dividerColor());
        batcher.box(x, y, ex, y + 1, accent);

        this.renderTime(batcher, font, accent);

        Action hovered = this.actionAt(mouseX, mouseY);

        for (int i = 0; i < this.actions.size(); i++)
        {
            Action action = this.actions.get(i);
            int bx = this.buttonsX + this.offset(i);
            boolean enabled = this.enabled(action);
            boolean hover = enabled && action == hovered;

            if (i == 4)
            {
                batcher.box(bx - GROUP_GAP / 2 - 2, this.buttonsY + 3, bx - GROUP_GAP / 2 - 1, this.buttonsY + BUTTON_H - 3, BBSSettings.dividerColor());
            }

            batcher.box(bx, this.buttonsY, bx + BUTTON, this.buttonsY + BUTTON_H, hover ? BBSSettings.accentOverlay(Colors.A50) : BBSSettings.raisedSurface());

            if (hover)
            {
                batcher.box(bx, this.buttonsY + BUTTON_H - 1, bx + BUTTON, this.buttonsY + BUTTON_H, accent);
            }

            int color = !enabled ? 0x50ffffff : action == Action.CANCEL ? 0xffe57373 : Colors.WHITE;

            batcher.icon(action.icon, color, bx + BUTTON / 2F, this.buttonsY + BUTTON_H / 2F, 0.5F, 0.5F);
        }

        batcher.flush();

        if (hovered != null)
        {
            context.drawTooltip(font, Text.literal(this.tooltipOf(hovered)), mouseX, mouseY);
        }
    }

    private void renderTime(Batcher2D batcher, TextRenderer font, int accent)
    {
        RecapRecorder.State state = RecapRecorder.getState();
        String time = RecapTime.format(RecapRecorder.getTicks());
        int color = state == RecapRecorder.State.IDLE ? 0x90ffffff : Colors.WHITE;
        int width = font.getWidth(time);
        int centerX = this.compact ? this.getX() + PADDING + 23 : this.getX() + this.width / 2;
        int textY = this.compact ? this.buttonsY + (BUTTON_H - font.fontHeight) / 2 : this.getY() + PADDING;

        batcher.text(time, centerX - width / 2F, textY, color, false);

        if (state != RecapRecorder.State.IDLE)
        {
            int dot = state == RecapRecorder.State.RECORDING ? 0xffe53935 : accent;
            int dx = centerX - width / 2 - 8;

            batcher.box(dx, textY + 2, dx + 4, textY + 6, dot);
        }
    }

    private String tooltipOf(Action action)
    {
        String text = L10n.lang("bbs.recap.bar." + action.id).get();

        if (action == Action.START)
        {
            return text + "  " + BBSModClient.getKeyRecapRecord().getBoundKeyLocalizedText().getString();
        }

        if (action == Action.MARKER)
        {
            return text + "  " + BBSModClient.getKeyRecapMarker().getBoundKeyLocalizedText().getString();
        }

        return text;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder)
    {
        builder.put(NarrationPart.TITLE, this.getMessage());
    }
}
