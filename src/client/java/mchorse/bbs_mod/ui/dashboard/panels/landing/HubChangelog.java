package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.onboarding.welcome.WelcomeManager;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What is new in this version, once, over the Central Hub the first time it opens after the setup
 * wizard: a slideshow of the studio's art across the top, the headline of the release on it, and
 * the features of the release below in four categories that fold open and shut, in a list that
 * scrolls as smoothly as the rest of the studio.
 *
 * <p>Drawn by {@link UICentralHubScreen} on top of its cards, and given its clicks and wheel while
 * it is open. Once it is closed it is written down in bbs/config.json and not shown again.</p>
 */
public class HubChangelog
{
    public static final String VERSION = "C1.3";
    /** How many features each category holds: vehicles, lattice, setup, studio */
    public static final int[] CATEGORIES = {5, 5, 5, 9};

    private static final Link[] SLIDES = {
        Link.assets("textures/banners/bg1.png"),
        Link.assets("textures/banners/bg2.png"),
        Link.assets("textures/banners/bg3.png")
    };

    private static final float SLIDE_SECONDS = 5F;
    private static final float CROSSFADE_SECONDS = 1.2F;
    private static final float OPEN_SECONDS = 0.3F;
    private static final float FOLD_SECONDS = 0.28F;
    /** Below this alpha (of 255) text is not drawn: the text renderer would draw it opaque */
    private static final int MIN_TEXT_ALPHA = 10;

    private static final int HEADER_H = 34;
    private static final int ITEM_H = 44;
    private static final int GAP = 6;

    private static final int[] COLORS = {0xFF4C9CFF, 0xFFFFB347, 0xFF6CE08A, 0xFFB48CFF};

    private float open;
    private boolean closing;
    private boolean gone;
    private long lastNanos;
    private float slideClock;

    /* The categories: which are open, and how far each one has unfolded */
    private final boolean[] unfolded = {true, false, false, false};
    private final float[] fold = {1F, 0F, 0F, 0F};

    private float scroll;
    private float targetScroll;
    private float maxScroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private float alpha;
    private final List<Object[]> hits = new ArrayList<>();

    /** Whether the hub should show it now: the wizard was walked and this version's list was not seen yet */
    public static boolean shouldShow()
    {
        return WelcomeManager.hasSeenWelcomeScreen() && !WelcomeManager.hasSeenChangelog(VERSION);
    }

    private static String lang(String id)
    {
        return L10n.lang("bbs.ui.changelog." + id).get();
    }

    private static float smooth(float t)
    {
        return t * t * (3F - 2F * t);
    }

    public boolean isGone()
    {
        return this.gone;
    }

    public void close()
    {
        if (this.closing)
        {
            return;
        }

        this.closing = true;
        WelcomeManager.setSeenChangelog(VERSION);
    }

    public void mouseClicked(UIContext context)
    {
        if (this.closing || context.mouseButton != 0)
        {
            return;
        }

        for (int i = this.hits.size() - 1; i >= 0; i--)
        {
            Object[] hit = this.hits.get(i);
            int[] r = (int[]) hit[0];

            if (context.mouseX >= r[0] && context.mouseX < r[2] && context.mouseY >= r[1] && context.mouseY < r[3])
            {
                UIUtils.playClick();
                ((Runnable) hit[1]).run();

                return;
            }
        }
    }

    public void mouseScrolled(UIContext context)
    {
        if (context.mouseX >= this.listX && context.mouseX < this.listX + this.listW && context.mouseY >= this.listY && context.mouseY < this.listY + this.listH)
        {
            this.targetScroll = Math.max(0F, Math.min(this.maxScroll, this.targetScroll - (float) context.mouseWheel * 30F));
        }
    }

    /* Drawing */

    private int fade(int color)
    {
        return Colors.setA(color, Colors.getA(color) * this.alpha);
    }

    private void text(UIContext c, String text, float x, float y, int color, float scale, boolean shadow)
    {
        int faded = this.fade(color);

        if ((faded >>> 24) < MIN_TEXT_ALPHA || text.isEmpty())
        {
            return;
        }

        if (scale == 1F)
        {
            c.batcher.text(text, Math.round(x), Math.round(y), faded, shadow);

            return;
        }

        MatrixStack stack = c.batcher.getContext().getMatrices();

        stack.push();
        stack.translate(x, y, 0F);
        stack.scale(scale, scale, 1F);
        c.batcher.text(text, 0, 0, faded, shadow);
        stack.pop();
    }

    private int paragraph(UIContext c, String text, int x, int y, int w, int color)
    {
        int faded = this.fade(color);

        if ((faded >>> 24) < MIN_TEXT_ALPHA)
        {
            return c.batcher.getFont().wrap(text, w).size() * 11;
        }

        return c.batcher.wallText(text, x, y, faded, w, 11);
    }

    private void box(UIContext c, int x1, int y1, int x2, int y2, int color)
    {
        int faded = this.fade(color);

        if ((faded >>> 24) > 0)
        {
            c.batcher.box(x1, y1, x2, y2, faded);
        }
    }

    private boolean hover(UIContext c, int x1, int y1, int x2, int y2)
    {
        return !this.closing && c.mouseX >= x1 && c.mouseX < x2 && c.mouseY >= y1 && c.mouseY < y2;
    }

    private void hit(int x1, int y1, int x2, int y2, Runnable action)
    {
        this.hits.add(new Object[] {new int[] {x1, y1, x2, y2}, action});
    }

    private void button(UIContext c, String label, int x, int y, int w, int h, boolean primary, Runnable action)
    {
        int accent = Colors.A100 | BBSSettings.primaryColor.get();
        boolean hover = this.hover(c, x, y, x + w, y + h);
        int fill = primary ? (hover ? Colors.lerp(accent, Colors.WHITE, 0.15F) : accent) : (hover ? 0xFF2A3042 : 0xFF1C2130);
        FontRenderer font = c.batcher.getFont();

        this.box(c, x, y, x + w, y + h, fill);

        if (!primary)
        {
            c.batcher.outline(x, y, x + w, y + h, this.fade(Colors.setA(Colors.WHITE, 0.14F)));
        }

        this.text(c, label, x + w / 2F - font.getWidth(label) / 2F, y + h / 2F - font.getHeight() / 2F, Colors.WHITE, 1F, false);
        this.hit(x, y, x + w, y + h, action);
    }

    public void render(UIContext c, Area area)
    {
        if (this.gone)
        {
            return;
        }

        long now = System.nanoTime();
        float dt = this.lastNanos == 0L ? 0F : Math.min((now - this.lastNanos) / 1.0E9F, 0.1F);
        boolean animate = BBSSettings.popupAnimations == null || BBSSettings.popupAnimations.get();

        this.lastNanos = now;
        this.slideClock += dt;
        this.open = animate ? Math.max(0F, Math.min(1F, this.open + (this.closing ? -dt : dt) / OPEN_SECONDS)) : (this.closing ? 0F : 1F);
        this.hits.clear();

        if (this.closing && this.open <= 0F)
        {
            this.gone = true;

            return;
        }

        /* Each category folds toward where it was sent */
        for (int i = 0; i < this.fold.length; i++)
        {
            float target = this.unfolded[i] ? 1F : 0F;

            this.fold[i] = animate ? Math.max(0F, Math.min(1F, this.fold[i] + Math.signum(target - this.fold[i]) * dt / FOLD_SECONDS)) : target;
        }

        /* The list eases toward where the wheel sent it, at the studio's own scroll smoothing */
        float smoothing = BBSSettings.getScrollSmoothingIntensity();

        this.scroll = smoothing > 0F ? this.scroll + (this.targetScroll - this.scroll) * Math.min(1F, dt * 20F / (2.5F * smoothing)) : this.targetScroll;

        float inverse = 1F - this.open;

        this.alpha = 1F - inverse * inverse * inverse;

        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        c.batcher.beginBatch();
        this.box(c, area.x, area.y, area.ex(), area.ey(), 0xB0000000);

        int cw = Math.min(780, area.w - 40);
        int ch = Math.min(500, area.h - 40);
        int cx = area.mx() - cw / 2;
        int cy = area.my() - ch / 2 + Math.round(12F * inverse);
        int pad = 20;
        int glow = this.fade(Colors.setA(accent, 0.35F));

        if ((glow >>> 24) > 0)
        {
            c.batcher.dropShadow(cx, cy, cx + cw, cy + ch, 16, glow, 0);
        }

        this.box(c, cx, cy, cx + cw, cy + ch, 0xF5101420);
        c.batcher.outline(cx, cy, cx + cw, cy + ch, this.fade(Colors.setA(Colors.WHITE, 0.14F)));

        /* The slideshow: one picture after another, each slowly panning, crossfading into the next */
        int bandH = Math.min(170, (int) (ch * 0.34F));

        this.slides(c, cx + 1, cy + 1, cw - 2, bandH);
        c.batcher.gradientVBox(cx + 1, cy + bandH - 70, cx + cw - 1, cy + 1 + bandH, this.fade(0x00000000), this.fade(0xE0101420));
        this.text(c, lang("title"), cx + pad, cy + bandH - 40, Colors.WHITE, 1.8F, true);
        this.text(c, lang("subtitle"), cx + pad, cy + bandH - 16, Colors.setA(Colors.WHITE, 0.75F), 1F, true);
        this.slideDots(c, cx + cw - pad, cy + bandH - 14, accent);

        /* The categories, folding */
        int buttonH = 24;
        int buttonY = cy + ch - pad - buttonH;

        this.listX = cx + pad;
        this.listY = cy + bandH + 12;
        this.listW = cw - pad * 2;
        this.listH = buttonY - 10 - this.listY;

        c.batcher.clip(this.listX, this.listY, this.listW, this.listH, c);

        int y = this.listY - Math.round(this.scroll);
        int contentW = this.listW - 8;

        for (int i = 0; i < CATEGORIES.length; i++)
        {
            y = this.category(c, i, this.listX, y, contentW) + GAP;
        }

        int content = y + Math.round(this.scroll) - this.listY - GAP;

        this.maxScroll = Math.max(0, content - this.listH);
        this.targetScroll = Math.min(this.targetScroll, this.maxScroll);
        this.scroll = Math.min(this.scroll, this.maxScroll);

        if (this.maxScroll > 0F)
        {
            int bar = Math.max(18, (int) (this.listH * (float) this.listH / (this.listH + this.maxScroll)));
            int at = (int) ((this.listH - bar) * this.scroll / this.maxScroll);
            int sx = this.listX + this.listW - 3;

            this.box(c, sx, this.listY, sx + 3, this.listY + this.listH, 0x20FFFFFF);
            this.box(c, sx, this.listY + at, sx + 3, this.listY + at + bar, accent);
        }

        c.batcher.unclip(c);

        this.button(c, lang("discord"), cx + pad, buttonY, 190, buttonH, false, () -> UIUtils.openWebLink(L10n.lang("bbs.ui.supporters.community_link").get()));
        this.button(c, lang("enter"), cx + cw - pad - 190, buttonY, 190, buttonH, true, this::close);

        c.batcher.endBatch();
    }

    /** A category: its header, then its features as far as it has unfolded; where the next one starts */
    private int category(UIContext c, int index, int x, int y, int w)
    {
        FontRenderer font = c.batcher.getFont();
        String id = "cat" + (index + 1);
        int color = COLORS[index % COLORS.length];
        int count = CATEGORIES[index];
        boolean hover = this.hover(c, x, Math.max(y, this.listY), x + w, Math.min(y + HEADER_H, this.listY + this.listH));
        float t = smooth(this.fold[index]);

        /* The header: a bar in the category's colour, a chevron that turns as it opens, the count */
        this.box(c, x, y, x + w, y + HEADER_H, hover ? 0xFF222A3C : 0xFF181D2A);
        this.box(c, x, y, x + 3, y + HEADER_H, color);
        this.chevron(c, x + 12, y + HEADER_H / 2, t, hover ? Colors.WHITE : 0xFFB8BCC8);
        this.text(c, "§l" + lang(id), x + 26, y + 7, Colors.WHITE, 1F, false);
        this.text(c, font.limitToWidth(lang(id + "_summary"), w - 80), x + 26, y + 20, Colors.setA(Colors.WHITE, 0.55F), 1F, false);

        String badge = String.valueOf(count);
        int bw = font.getWidth(badge) + 10;

        this.box(c, x + w - bw - 10, y + 11, x + w - 10, y + 23, Colors.setA(color, 0.85F));
        this.text(c, badge, x + w - bw - 5, y + 13, 0xFF101420, 1F, false);

        int top = Math.max(y, this.listY);
        int bottom = Math.min(y + HEADER_H, this.listY + this.listH);

        if (bottom > top)
        {
            this.hit(x, top, x + w, bottom, () -> this.unfolded[index] = !this.unfolded[index]);
        }

        y += HEADER_H;

        if (t <= 0F)
        {
            return y;
        }

        /* The features, two columns, revealed from the top as the category unfolds */
        int rows = (count + 1) / 2;
        int full = GAP + rows * (ITEM_H + GAP);
        int shown = Math.round(full * t);
        int colW = (w - GAP) / 2;
        float alpha = this.alpha;

        int clipTop = Math.max(y, this.listY);
        int clipBottom = Math.min(y + shown, this.listY + this.listH);

        if (clipBottom > clipTop)
        {
            c.batcher.clip(x, clipTop, w, clipBottom - clipTop, c);
            this.alpha = alpha * t;

            for (int i = 0; i < count; i++)
            {
                int ix = x + (i % 2) * (colW + GAP);
                int iy = y + GAP + (i / 2) * (ITEM_H + GAP) - Math.round((1F - t) * 10F);
                String item = id + ".item" + (i + 1);

                if (iy + ITEM_H < clipTop || iy > clipBottom)
                {
                    continue;
                }

                this.box(c, ix, iy, ix + colW, iy + ITEM_H, 0xFF141927);
                this.box(c, ix + 8, iy + 10, ix + 12, iy + 14, color);
                this.text(c, font.limitToWidth(lang(item), colW - 28), ix + 18, iy + 8, Colors.WHITE, 1F, false);
                this.paragraph(c, lang(item + "_desc"), ix + 18, iy + 21, colW - 26, Colors.setA(Colors.WHITE, 0.6F));
            }

            this.alpha = alpha;
            c.batcher.unclip(c);
        }

        return y + shown;
    }

    /** A small triangle pointing right when shut and down once it is more than half open */
    private void chevron(UIContext c, int x, int y, float t, int color)
    {
        boolean down = t >= 0.5F;

        for (int i = 0; i < 4; i++)
        {
            int length = 7 - i * 2;

            if (down)
            {
                /* Rows, shrinking downward */
                this.box(c, x - 3 + i, y - 2 + i, x - 3 + i + length, y - 1 + i, color);
            }
            else
            {
                /* Columns, shrinking to the right */
                this.box(c, x - 2 + i, y - 3 + i, x - 1 + i, y - 3 + i + length, color);
            }
        }
    }

    private void slides(UIContext c, int x, int y, int w, int h)
    {
        int count = SLIDES.length;
        float cycle = this.slideClock % (SLIDE_SECONDS * count);
        int current = (int) (cycle / SLIDE_SECONDS) % count;
        float within = cycle - current * SLIDE_SECONDS;
        float blend = Math.max(0F, Math.min(1F, (within - (SLIDE_SECONDS - CROSSFADE_SECONDS)) / CROSSFADE_SECONDS));

        this.box(c, x, y, x + w, y + h, 0xFF1B1530);
        this.slide(c, SLIDES[current], x, y, w, h, within / SLIDE_SECONDS, 1F);

        if (blend > 0F)
        {
            this.slide(c, SLIDES[(current + 1) % count], x, y, w, h, (within - SLIDE_SECONDS) / SLIDE_SECONDS, blend);
        }
    }

    /** A picture cropped to cover the band, slid across it a little as it is shown (pan from 0 to 1) */
    private void slide(UIContext c, Link link, int x, int y, int w, int h, float pan, float opacity)
    {
        Texture texture = BBSModClient.getTextures().getTexture(link);
        int tint = Colors.setA(Colors.WHITE, this.alpha * opacity);

        if (texture == null || texture.width <= 0 || texture.height <= 0 || (tint >>> 24) == 0)
        {
            return;
        }

        /* Cover: the largest piece of the picture with the band's proportions, a tenth narrower to have room to pan */
        float aspect = w / (float) h;
        float spanW = Math.min(texture.width * 0.9F, texture.height * aspect);
        float spanH = spanW / aspect;
        float room = texture.width - spanW;
        float u1 = room * Math.max(0F, Math.min(1F, 0.5F + (pan - 0.5F) * 0.8F));
        float v1 = (texture.height - spanH) / 2F;

        c.batcher.texturedBox(texture, tint, x, y, w, h, u1, v1, u1 + spanW, v1 + spanH);
    }

    private void slideDots(UIContext c, int right, int y, int accent)
    {
        int count = SLIDES.length;
        int current = (int) ((this.slideClock % (SLIDE_SECONDS * count)) / SLIDE_SECONDS) % count;
        int x = right - count * 14;

        for (int i = 0; i < count; i++)
        {
            final int index = i;
            int dx = x + i * 14;

            this.box(c, dx, y, dx + 8, y + 8, i == current ? accent : 0x80FFFFFF);
            this.hit(dx - 3, y - 3, dx + 11, y + 11, () -> this.slideClock = index * SLIDE_SECONDS);
        }
    }
}
