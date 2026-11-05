package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
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
import net.minecraft.client.MinecraftClient;
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

    /**
     * The panels that cannot work without a world: a film needs chunks to stand its actors in, morphing
     * changes the player, and a model block is a block entity at a place in the world.
     */
    public static boolean requiresWorld(Class<? extends UIDashboardPanel> panel)
    {
        return panel == UIFilmPanel.class
            || panel == mchorse.bbs_mod.ui.morphing.UIMorphingPanel.class
            || panel == mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel.class;
    }

    /** Whether the editor is shut now: it needs a world and the client is not in one */
    public static boolean isLocked(Class<? extends UIDashboardPanel> panel)
    {
        return requiresWorld(panel) && MinecraftClient.getInstance().world == null;
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

    private HubChangelog changelog;
    /** The "Credits" link along the bottom: where it was drawn, for the click */
    private final int[] creditsRect = new int[4];
    /** The cursor is on a card that is shut; the reason is drawn over everything once the cards are */
    private boolean lockedHover;
    private boolean changelogChecked;

    public UICentralHubScreen(Consumer<Class<? extends UIDashboardPanel>> picker)
    {
        this.picker = picker;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.changelog != null)
        {
            this.changelog.mouseScrolled(context);

            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        /* While what is new is up, it takes every click */
        if (this.changelog != null)
        {
            this.changelog.mouseClicked(context);

            return true;
        }

        if (context.mouseButton == 0 && context.mouseX >= this.creditsRect[0] && context.mouseX < this.creditsRect[2] && context.mouseY >= this.creditsRect[1] && context.mouseY < this.creditsRect[3])
        {
            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(context, new UICreditsOverlayPanel(), 440, 330);

            return true;
        }

        if (context.mouseButton != 0 || !this.area.isInside(context))
        {
            return false;
        }

        for (int i = 0; i < this.rects.size(); i++)
        {
            int[] r = this.rects.get(i);

            if (context.mouseX >= r[0] && context.mouseX < r[2] && context.mouseY >= r[1] && context.mouseY < r[3])
            {
                if (isLocked(CARDS.get(i).panel()))
                {
                    context.notifyError(L10n.lang("bbs.hub.locked.world_required"));

                    return true;
                }

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

        String credits = mchorse.bbs_mod.l10n.L10n.lang("bbs.credits.link").get();
        int creditsW = context.batcher.getFont().getWidth(credits);
        int creditsX = this.area.mx() - creditsW / 2;
        int creditsY = this.area.ey() - 16;
        boolean creditsHover = context.mouseX >= creditsX - 4 && context.mouseX < creditsX + creditsW + 4 && context.mouseY >= creditsY - 3 && context.mouseY < creditsY + 12;

        context.batcher.text(credits, creditsX, creditsY, creditsHover ? Colors.WHITE : MUTED, false);
        this.creditsRect[0] = creditsX - 4;
        this.creditsRect[1] = creditsY - 3;
        this.creditsRect[2] = creditsX + creditsW + 4;
        this.creditsRect[3] = creditsY + 12;

        if (this.lockedHover)
        {
            this.lockedHover = false;

            FontRenderer lockFont = context.batcher.getFont();
            List<String> lines = lockFont.wrap(L10n.lang("bbs.hub.locked.world_required").get(), 220);
            int tw = 0;

            for (String line : lines)
            {
                tw = Math.max(tw, lockFont.getWidth(line));
            }

            int th = lines.size() * (lockFont.getHeight() + 2) + 6;
            int tx = Math.min(context.mouseX + 10, this.area.ex() - tw - 12);
            int ty = Math.min(context.mouseY + 12, this.area.ey() - th - 2);

            context.batcher.box(tx, ty, tx + tw + 10, ty + th, 0xF0101420);
            context.batcher.outline(tx, ty, tx + tw + 10, ty + th, Colors.setA(Colors.WHITE, 0.25F));

            for (int i = 0; i < lines.size(); i++)
            {
                context.batcher.text(lines.get(i), tx + 5, ty + 4 + i * (lockFont.getHeight() + 2), Colors.WHITE, false);
            }
        }

        /* What is new in this version, once, the first time the hub opens after the setup wizard */
        if (!this.changelogChecked)
        {
            this.changelogChecked = true;

            if (HubChangelog.shouldShow())
            {
                this.changelog = new HubChangelog();
            }
        }

        if (this.changelog != null)
        {
            this.changelog.render(context, this.area);

            if (this.changelog.isGone())
            {
                this.changelog = null;
            }
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

        if (isLocked(card.panel()))
        {
            context.batcher.box(x, y, ex, ey, 0x80000000);
            context.batcher.icon(Icons.LOCKED, Colors.WHITE, x + CARD_W / 2, y + CARD_H / 2, 0.5F, 0.5F);

            if (hover)
            {
                this.lockedHover = true;
            }
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
