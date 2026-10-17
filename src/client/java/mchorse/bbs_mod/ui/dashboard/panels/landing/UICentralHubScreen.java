package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.motion.UIMMotionPanel;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Central Hub: the place every editor is entered from. A grid of cards, one per editor, on
 * the same drifting backdrop the landing screens use.
 *
 * <p>The screen only knows which editor was picked; what that means (switching the panel, opening
 * its own landing screen in a tab) is up to whoever listens.</p>
 */
public class UICentralHubScreen extends UIElement
{

    private static final int CARD_W = 184;
    private static final int CARD_H = 98;
    private static final int GAP = 12;
    private static final int BOX_PADDING = 26;
    private static final int MAX_COLUMNS = 4;

    private static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    private static final int MUTED = Colors.setA(Colors.WHITE, 0.5F);

    private record Card(IKey name, IKey description, Icon icon, Class<? extends UIDashboardPanel> panel, boolean isNew)
    {}

    private static final List<Card> CARDS = List.of(
        new Card(UIKeys.CENTRAL_HUB_CARD_FILMS_TITLE, UIKeys.CENTRAL_HUB_CARD_FILMS_DESC, Icons.FILM, UIFilmPanel.class, false),
        new Card(UIKeys.CENTRAL_HUB_CARD_CATALYST_TITLE, UIKeys.CENTRAL_HUB_CARD_CATALYST_DESC, Icons.FIVE_STAR, UICatalystPanel.class, false),
        new Card(UIKeys.CENTRAL_HUB_CARD_MMOTION_TITLE, UIKeys.CENTRAL_HUB_CARD_MMOTION_DESC, Icons.CURVES, UIMMotionPanel.class, true),
        new Card(UIKeys.CENTRAL_HUB_CARD_PARTICLES_TITLE, UIKeys.CENTRAL_HUB_CARD_PARTICLES_DESC, Icons.PARTICLE, UIParticleSchemePanel.class, false),
        new Card(UIKeys.CENTRAL_HUB_CARD_MODELS_TITLE, UIKeys.CENTRAL_HUB_CARD_MODELS_DESC, Icons.POSE, UIModelEditorPanel.class, false),
        new Card(UIKeys.CENTRAL_HUB_CARD_AUDIO_TITLE, UIKeys.CENTRAL_HUB_CARD_AUDIO_DESC, Icons.SOUND, UIAudioEditorPanel.class, false),
        new Card(UIKeys.CENTRAL_HUB_CARD_TEXTURE_TITLE, UIKeys.CENTRAL_HUB_CARD_TEXTURE_DESC, Icons.MATERIAL, UITextureManagerPanel.class, false)
    );

    /** The card of an editor, by its panel class; null for a panel that has none. */
    private static Card cardOf(Class<? extends UIDashboardPanel> panel)
    {
        for (Card card : CARDS)
        {
            if (card.panel() == panel)
            {
                return card;
            }
        }

        return null;
    }

    /** Whether the Central Hub leads to this panel, i.e. it is an editor with a place in the tab strip. */
    public static boolean isWorkspace(Class<? extends UIDashboardPanel> panel)
    {
        return cardOf(panel) != null;
    }

    public static IKey nameOf(Class<? extends UIDashboardPanel> panel)
    {
        Card card = cardOf(panel);

        return card == null ? IKey.constant(panel.getSimpleName()) : card.name();
    }

    public static Icon iconOf(Class<? extends UIDashboardPanel> panel)
    {
        Card card = cardOf(panel);

        return card == null ? Icons.FOLDER : card.icon();
    }

    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final Consumer<Class<? extends UIDashboardPanel>> picker;

    /** Where each card was drawn last frame, for the clicks; same order as {@link #CARDS}. */
    private final List<int[]> rects = new ArrayList<>();

    public UICentralHubScreen(Consumer<Class<? extends UIDashboardPanel>> picker)
    {
        this.picker = picker;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton != 0 || !this.area.isInside(context))
        {
            return false;
        }

        for (int i = 0; i < this.rects.size(); i++)
        {
            int[] r = this.rects.get(i);

            if (context.mouseX >= r[0] && context.mouseX < r[2] && context.mouseY >= r[1] && context.mouseY < r[3])
            {
                this.picker.accept(CARDS.get(i).panel());

                return true;
            }
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        this.backdrop.render(context, this.area);

        FontRenderer font = context.batcher.getFont();
        int primary = Colors.A100 | BBSSettings.primaryColor.get();
        int cx = this.area.mx();

        /* The header stacks by the font's own height, so nothing of it can run into the cards */
        int fh = font.getHeight();
        int hy = this.area.y + 22;
        String welcome = UIKeys.CENTRAL_HUB_WELCOME.get();

        this.scaledText(context, UIKeys.CENTRAL_HUB_TITLE.get(), cx, hy, 2F, Colors.WHITE);
        hy += fh * 2 + 10;

        context.batcher.text(welcome, cx - font.getWidth(welcome) / 2, hy, DIMMED, false);
        hy += fh + 6;

        this.scaledText(context, UIKeys.CENTRAL_HUB_SUBTITLE.get(), cx, hy, 1.5F, primary);
        hy += (int) (fh * 1.5F);

        /* The panel the cards sit in */
        int columns = Math.max(1, Math.min(MAX_COLUMNS, (this.area.w - 120 - BOX_PADDING * 2 + GAP) / (CARD_W + GAP)));
        int rows = (CARDS.size() + columns - 1) / columns;
        int gridW = columns * CARD_W + (columns - 1) * GAP;
        int gridH = rows * CARD_H + (rows - 1) * GAP;
        int boxW = gridW + BOX_PADDING * 2;
        int boxH = gridH + BOX_PADDING * 2;
        int top = hy + 22;
        int boxX = cx - boxW / 2;
        int spare = this.area.ey() - top - boxH;
        int boxY = top + Math.max(0, Math.min(spare - 24, spare / 3));

        context.batcher.dropShadow(boxX, boxY, boxX + boxW, boxY + boxH, 14, Colors.A50, 0);
        context.batcher.box(boxX, boxY, boxX + boxW, boxY + boxH, Colors.setA(0x0b0e16, 0.72F));
        context.batcher.outline(boxX, boxY, boxX + boxW, boxY + boxH, Colors.setA(Colors.WHITE, 0.12F));

        this.rects.clear();

        for (int i = 0; i < CARDS.size(); i++)
        {
            int col = i % columns;
            int row = i / columns;
            int x = boxX + BOX_PADDING + col * (CARD_W + GAP);
            int y = boxY + BOX_PADDING + row * (CARD_H + GAP);

            this.rects.add(new int[] {x, y, x + CARD_W, y + CARD_H});
            this.renderCard(context, CARDS.get(i), x, y, primary);
        }
    }

    private void renderCard(UIContext context, Card card, int x, int y, int primary)
    {
        FontRenderer font = context.batcher.getFont();
        boolean hover = context.mouseX >= x && context.mouseX < x + CARD_W && context.mouseY >= y && context.mouseY < y + CARD_H && this.area.isInside(context);
        int ex = x + CARD_W;
        int ey = y + CARD_H;

        if (hover)
        {
            /* A soft glow in the accent colour around the card */
            context.batcher.dropShadow(x, y, ex, ey, 10, Colors.setA(primary, 0.45F), 0);
        }

        context.batcher.box(x, y, ex, ey, hover ? 0xf01a2030 : 0xe0121722);
        context.batcher.gradientVBox(x, y, ex, y + 40, Colors.setA(primary, hover ? 0.28F : 0.14F), Colors.setA(primary, 0F));
        context.batcher.outline(x, y, ex, ey, hover ? primary : Colors.setA(Colors.WHITE, 0.14F));
        context.batcher.box(x, y, x + 2, ey, hover ? primary : Colors.setA(primary, 0.6F));

        context.batcher.icon(card.icon(), Colors.WHITE, x + 14, y + 14);
        context.batcher.text("§l" + card.name().get(), x + 40, y + 19, Colors.WHITE, false);

        List<String> lines = font.wrap(card.description().get(), CARD_W - 28);
        int lineY = y + 44;

        for (int i = 0; i < Math.min(3, lines.size()); i++)
        {
            context.batcher.text(lines.get(i), x + 14, lineY, hover ? DIMMED : MUTED, false);

            lineY += font.getHeight() + 3;
        }

        if (card.isNew())
        {
            String label = UIKeys.CENTRAL_HUB_BADGE_NEW.get();
            int w = font.getWidth(label) + 24;
            int bx = ex - w - 8;
            int by = y + 8;

            context.batcher.box(bx, by, bx + w, by + font.getHeight() + 6, primary);
            context.batcher.icon(Icons.FIVE_STAR, Colors.WHITE, bx + 3, by + 3);
            context.batcher.text(label, bx + 20, by + 4, Colors.WHITE, false);
        }
    }

    /** Text centred on x, drawn bigger than the font. */
    private void scaledText(UIContext context, String text, int cx, int y, float scale, int color)
    {
        MatrixStack stack = context.batcher.getContext().getMatrices();
        int width = context.batcher.getFont().getWidth(text);

        stack.push();
        stack.translate(cx - width * scale / 2F, y, 0F);
        stack.scale(scale, scale, 1F);

        context.batcher.text(text, 0, 0, color, true);

        stack.pop();
    }
}
