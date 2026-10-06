/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.screen.ScreenTexts
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.OrderedText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Style
 *  net.minecraft.text.Text
 *  net.minecraft.text.Text$Serialization
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.Util
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.data.BookSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class BookGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final BookGuiRenderer INSTANCE = new BookGuiRenderer();
    private static final Identifier PAGE_FORWARD = new Identifier("widget/page_forward");
    private static final Identifier PAGE_FORWARD_HIGHLIGHTED = new Identifier("widget/page_forward_highlighted");
    private static final Identifier PAGE_BACKWARD = new Identifier("widget/page_backward");
    private static final Identifier PAGE_BACKWARD_HIGHLIGHTED = new Identifier("widget/page_backward_highlighted");
    private static final Identifier WIDGET_BUTTON = new Identifier("widget/button");
    private static final Identifier WIDGET_BUTTON_HIGHLIGHTED = new Identifier("widget/button_highlighted");

    private BookGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        BookGuiRenderer.drawChrome(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick, ctx.opacity, cursorX, cursorY);
    }

    private static void drawChrome(Batcher2D batcher, GuiPovActionClip clip, String guiId, float tick, float opacity, float cursorX, float cursorY) {
        boolean writable = GuiTextRenderer.sampleBool(clip.getBookWritable(guiId), tick, false);
        boolean signing = writable && GuiTextRenderer.sampleBool(clip.getBookSigning(guiId), tick, false);
        List<String> pages = BookSnapshot.unpack(GuiTextRenderer.sampleString(clip.getBookPages(guiId), tick, ""));
        int pageCount = Math.max(1, pages.size());
        int page = MathHelper.clamp((int)GuiTextRenderer.sampleInt(clip.getBookPage(guiId), tick, 0), (int)0, (int)(pageCount - 1));
        String title = GuiTextRenderer.sampleString(clip.getBookTitle(guiId), tick, "");
        String author = GuiTextRenderer.sampleString(clip.getBookAuthor(guiId), tick, "");
        int selStart = GuiTextRenderer.sampleInt(clip.getBookSelStart(guiId), tick, 0);
        int selEnd = GuiTextRenderer.sampleInt(clip.getBookSelEnd(guiId), tick, 0);
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int ink = BookGuiRenderer.bookInk(opacity);
        if (signing) {
            MutableText header = Text.translatable((String)"book.editTitle");
            batcher.getContext().drawText(font, (Text)header, 36 + (114 - font.getWidth((StringVisitable)header)) / 2, 34, ink, false);
            int titleX = 36 + (114 - font.getWidth(title)) / 2;
            batcher.getContext().drawText(font, title, titleX, 50, ink, false);
            MutableText signedBy = Text.translatable((String)"book.byAuthor", (Object[])new Object[]{author});
            batcher.getContext().drawText(font, (Text)signedBy, 36 + (114 - font.getWidth((StringVisitable)signedBy)) / 2, 60, BookGuiRenderer.bookInk(opacity) | 0x555555, false);
            batcher.getContext().drawTextWrapped(font, (StringVisitable)Text.translatable((String)"book.finalizeWarning"), 36, 82, 114, ink);
            BookGuiRenderer.drawBookTitleCaret(batcher, title, titleX, 50, selStart, selEnd, opacity);
        } else {
            boolean hover;
            String pageText = pages.get(page);
            StringVisitable visitable = writable ? Text.literal((String)pageText) : BookGuiRenderer.parseBookPage(pageText);
            List lines = font.wrapLines((StringVisitable)visitable, 114);
            for (int i = 0; i < lines.size(); ++i) {
                batcher.getContext().drawText(font, (OrderedText)lines.get(i), 36, 32 + i * 9, ink, false);
            }
            MutableText indicator = Text.translatable((String)"book.pageIndicator", (Object[])new Object[]{page + 1, pageCount});
            batcher.getContext().drawText(font, (Text)indicator, 148 - font.getWidth((StringVisitable)indicator), 18, ink, false);
            if (page > 0) {
                hover = GuiTextRenderer.inBounds(cursorX, cursorY, 43, 159, 23, 13);
                batcher.drawGuiTexture(hover ? PAGE_BACKWARD_HIGHLIGHTED : PAGE_BACKWARD, 43, 159, 23, 13);
            }
            if (writable || page < pageCount - 1) {
                hover = GuiTextRenderer.inBounds(cursorX, cursorY, 116, 159, 23, 13);
                batcher.drawGuiTexture(hover ? PAGE_FORWARD_HIGHLIGHTED : PAGE_FORWARD, 116, 159, 23, 13);
            }
            if (writable) {
                BookGuiRenderer.drawBookPageCaret(batcher, pageText, selStart, selEnd, opacity);
            }
        }
        if (signing) {
            BookGuiRenderer.drawBookButton(batcher, -4, 196, 98, 20, (Text)Text.translatable((String)"book.finalizeButton"), cursorX, cursorY, opacity);
            BookGuiRenderer.drawBookButton(batcher, 98, 196, 98, 20, ScreenTexts.CANCEL, cursorX, cursorY, opacity);
        } else if (writable) {
            BookGuiRenderer.drawBookButton(batcher, -4, 196, 98, 20, (Text)Text.translatable((String)"book.signButton"), cursorX, cursorY, opacity);
            BookGuiRenderer.drawBookButton(batcher, 98, 196, 98, 20, ScreenTexts.DONE, cursorX, cursorY, opacity);
        } else {
            BookGuiRenderer.drawBookButton(batcher, -4, 196, 200, 20, ScreenTexts.DONE, cursorX, cursorY, opacity);
        }
    }

    private static StringVisitable parseBookPage(String page) {
        if (page == null || page.isEmpty()) {
            return StringVisitable.EMPTY;
        }
        try {
            //? if >=1.20.4 {
            MutableText parsed = Text.Serialization.fromJson((String)page);
            //?} else {
            /*MutableText parsed = Text.Serializer.fromJson((String)page);
            *///?}
            return parsed != null ? parsed : Text.literal((String)page);
        }
        catch (Exception ignored) {
            return Text.literal((String)page);
        }
    }

    private static void drawBookButton(Batcher2D batcher, int x, int y, int width, int height, Text label, float cursorX, float cursorY, float opacity) {
        boolean hover = GuiTextRenderer.inBounds(cursorX, cursorY, x, y, width, height);
        batcher.drawGuiTexture(hover ? WIDGET_BUTTON_HIGHLIGHTED : WIDGET_BUTTON, x, y, width, height);
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int color = (int)(255.0f * opacity) << 24 | 0xFFFFFF;
        batcher.getContext().drawCenteredTextWithShadow(font, label, x + width / 2, y + (height - 8) / 2, color);
    }

    private static int bookInk(float opacity) {
        return (int)(255.0f * opacity) << 24;
    }

    private static void drawBookPageCaret(Batcher2D batcher, String page, int selStart, int selEnd, float opacity) {
        String text = page == null ? "" : page;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        ArrayList<String> lines = new ArrayList<String>();
        font.getTextHandler().wrapLines(text, 114, Style.EMPTY, true, (style, start, end) -> lines.add(text.substring(start, end)));
        if (lines.isEmpty()) {
            lines.add("");
        }
        int start2 = MathHelper.clamp((int)Math.min(selStart, selEnd), (int)0, (int)text.length());
        int end2 = MathHelper.clamp((int)Math.max(selStart, selEnd), (int)0, (int)text.length());
        int cursor = 0;
        for (int i = 0; i < lines.size(); ++i) {
            String line = (String)lines.get(i);
            int lineStart = cursor;
            int lineEnd = cursor + line.length();
            int y = 32 + i * 9;
            if (start2 != end2 && end2 > lineStart && start2 < lineEnd) {
                int x1 = 36 + font.getWidth(line.substring(0, Math.max(0, start2 - lineStart)));
                int x2 = 36 + font.getWidth(line.substring(0, Math.min(line.length(), end2 - lineStart)));
                batcher.getContext().fill(RenderLayer.getGuiTextHighlight(), x1, y - 1, x2, y + 9, -16776961);
            }
            cursor = lineEnd;
        }
        if (start2 == end2 && Util.getMeasuringTimeMs() / 300L % 2L == 0L) {
            int remaining = MathHelper.clamp((int)selStart, (int)0, (int)text.length());
            int lineY = 32;
            String line = "";
            for (String candidate : lines) {
                if (remaining <= candidate.length()) {
                    line = candidate;
                    break;
                }
                remaining -= candidate.length();
                lineY += 9;
            }
            remaining = MathHelper.clamp((int)remaining, (int)0, (int)line.length());
            int caretX = 36 + font.getWidth(line.substring(0, remaining));
            int alpha = (int)(255.0f * opacity) << 24;
            if (remaining < line.length()) {
                batcher.getContext().fill(RenderLayer.getGuiOverlay(), caretX, lineY - 1, caretX + 1, lineY + 10, alpha);
            } else {
                batcher.getContext().drawText(MinecraftClient.getInstance().textRenderer, "_", caretX, lineY, alpha, false);
            }
        }
    }

    private static void drawBookTitleCaret(Batcher2D batcher, String text, int x, int y, int selStart, int selEnd, float opacity) {
        int end;
        String title = text == null ? "" : text;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int start = MathHelper.clamp((int)Math.min(selStart, selEnd), (int)0, (int)title.length());
        if (start != (end = MathHelper.clamp((int)Math.max(selStart, selEnd), (int)0, (int)title.length()))) {
            int x1 = x + font.getWidth(title.substring(0, start));
            int x2 = x + font.getWidth(title.substring(0, end));
            batcher.getContext().fill(RenderLayer.getGuiTextHighlight(), x1, y - 1, x2, y + 9, -16776961);
        }
        if (start == end && Util.getMeasuringTimeMs() / 300L % 2L == 0L) {
            int caret = MathHelper.clamp((int)selStart, (int)0, (int)title.length());
            int caretX = x + font.getWidth(title.substring(0, caret));
            batcher.getContext().drawText(font, "_", caretX, y, BookGuiRenderer.bookInk(opacity), false);
        }
    }
}

