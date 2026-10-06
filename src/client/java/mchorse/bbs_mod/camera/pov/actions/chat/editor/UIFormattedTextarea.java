/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea
 *  mchorse.bbs_mod.ui.framework.elements.input.text.utils.Cursor
 *  mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine
 *  mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer
 */
package mchorse.bbs_mod.camera.pov.actions.chat.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.Cursor;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;

public class UIFormattedTextarea
extends UITextarea<UIFormattedTextarea.FormattedLine> {
    public static final int[][] MC_COLOR_MAP = new int[][]{{0, 48}, {170, 49}, {43520, 50}, {43690, 51}, {0xAA0000, 52}, {0xAA00AA, 53}, {0xFFAA00, 54}, {0xAAAAAA, 55}, {0x555555, 56}, {0x5555FF, 57}, {0x55FF55, 97}, {0x55FFFF, 98}, {0xFF5555, 99}, {0xFF55FF, 100}, {0xFFFF55, 101}, {0xFFFFFF, 102}};
    private String currentActiveFormat = "\u00a7f";

    public UIFormattedTextarea(Consumer<String> callback) {
        super(callback);
        this.background().wrap().padding(2);
    }

    public int getWrappedWidth() {
        return Math.max(10, this.area.w - this.padding * 2 - 2);
    }

    protected void recalculateSizes() {
        super.recalculateSizes();
        this.horizontal.scrollSize = 0;
    }

    protected FormattedLine createTextLine(String line) {
        return new FormattedLine(line);
    }

    public void setFormattedText(String formatted) {
        String[] lines;
        this.text.clear();
        if (formatted == null || formatted.isEmpty()) {
            this.text.add(new FormattedLine(""));
            this.cursor.set(0, 0);
            this.deselect();
            return;
        }
        for (String l : lines = formatted.split("\n", -1)) {
            StringBuilder plain = new StringBuilder();
            ArrayList<String> formats = new ArrayList<String>();
            Object currentFormat = "\u00a7f";
            for (int i = 0; i < l.length(); ++i) {
                char c = l.charAt(i);
                if (c == '\u00a7' && i + 1 < l.length()) {
                    char code = l.charAt(i + 1);
                    if (code == 'r' || code == 'R') {
                        currentFormat = "\u00a7f";
                    } else if ("0123456789abcdef".indexOf(Character.toLowerCase(code)) >= 0) {
                        currentFormat = "\u00a7" + Character.toLowerCase(code);
                    } else if ("lmonk".indexOf(Character.toLowerCase(code)) >= 0) {
                        currentFormat = (String)currentFormat + "\u00a7" + Character.toLowerCase(code);
                    }
                    ++i;
                    continue;
                }
                plain.append(c);
                formats.add((String)currentFormat);
            }
            FormattedLine line = new FormattedLine(plain.toString(), formats);
            this.text.add(line);
        }
        this.cursor.set(0, 0);
        this.deselect();
        if (this.area.w > 0) {
            this.recalculateWrapping();
            this.recalculateSizes();
        }
    }

    public String getFormattedText() {
        StringBuilder sb = new StringBuilder();
        for (int lineIdx = 0; lineIdx < this.text.size(); ++lineIdx) {
            if (lineIdx > 0) {
                sb.append("\n");
            }
            FormattedLine line = (FormattedLine)(this.text.get(lineIdx));
            String lastFormat = null;
            for (int i = 0; i < line.text.length(); ++i) {
                String f;
                String string = f = i < line.formats.size() ? line.formats.get(i) : "\u00a7f";
                if (!f.equals(lastFormat)) {
                    sb.append(f);
                    lastFormat = f;
                }
                sb.append(line.text.charAt(i));
            }
        }
        return sb.toString();
    }

    public static int getColorRgb(char code) {
        char c = Character.toLowerCase(code);
        for (int[] pair : MC_COLOR_MAP) {
            if (pair[1] != c) continue;
            return pair[0];
        }
        return 0xFFFFFF;
    }

    public static char extractColorChar(String format) {
        if (format == null) {
            return 'f';
        }
        for (int i = 0; i < format.length(); ++i) {
            if (format.charAt(i) != '\u00a7' || i + 1 >= format.length()) continue;
            char code = Character.toLowerCase(format.charAt(i + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                return code;
            }
            ++i;
        }
        return 'f';
    }

    public int getSelectedColor() {
        if (this.isSelected()) {
            Cursor min = this.getMin();
            Cursor max = this.getMax();
            if (min.line != max.line || min.offset != max.offset) {
                Character commonColor = null;
                for (int lineIdx = min.line; lineIdx <= max.line && lineIdx < this.text.size(); ++lineIdx) {
                    FormattedLine line = (FormattedLine)(this.text.get(lineIdx));
                    int from = lineIdx == min.line ? min.offset : 0;
                    int to = lineIdx == max.line ? max.offset : line.text.length();
                    for (int i = from; i < to && i < line.formats.size(); ++i) {
                        char c = UIFormattedTextarea.extractColorChar(line.formats.get(i));
                        if (commonColor == null) {
                            commonColor = Character.valueOf(c);
                            continue;
                        }
                        if (commonColor.charValue() == c) continue;
                        return 0;
                    }
                }
                if (commonColor != null) {
                    return UIFormattedTextarea.getColorRgb(commonColor.charValue());
                }
            }
        }
        if (this.hasLine(this.cursor.line)) {
            FormattedLine line = (FormattedLine)(this.text.get(this.cursor.line));
            int idx = this.cursor.offset;
            if (idx >= line.formats.size()) {
                idx = line.formats.size() - 1;
            }
            if (idx >= 0 && idx < line.formats.size()) {
                char c = UIFormattedTextarea.extractColorChar(line.formats.get(idx));
                return UIFormattedTextarea.getColorRgb(c);
            }
        }
        char c = UIFormattedTextarea.extractColorChar(this.currentActiveFormat);
        return UIFormattedTextarea.getColorRgb(c);
    }

    public void writeCharacter(String character) {
        if (this.hasLine(this.cursor.line)) {
            FormattedLine line = (FormattedLine)(this.text.get(this.cursor.line));
            int index = this.cursor.offset;
            String currentFormat = this.currentActiveFormat != null ? this.currentActiveFormat : (index > 0 && index <= line.formats.size() ? line.formats.get(index - 1) : "\u00a7f");
            super.writeCharacter(character);
            for (int k = 0; k < character.length(); ++k) {
                if (index + k <= line.formats.size()) {
                    line.formats.add(index + k, currentFormat);
                    continue;
                }
                line.formats.add(currentFormat);
            }
        } else {
            super.writeCharacter(character);
        }
        this.notifyFormattedChanged();
    }

    public String deleteCharacter() {
        if (this.hasLine(this.cursor.line)) {
            FormattedLine line = (FormattedLine)(this.text.get(this.cursor.line));
            int index = this.cursor.offset;
            if (index > 0 && index <= line.formats.size()) {
                line.formats.remove(index - 1);
            }
        }
        String res = super.deleteCharacter();
        this.notifyFormattedChanged();
        return res;
    }

    public void deleteSelection() {
        if (!this.isSelected()) {
            return;
        }
        Cursor min = this.getMin();
        Cursor max = this.getMax();
        if (min.line == max.line && this.hasLine(min.line)) {
            FormattedLine line = (FormattedLine)(this.text.get(min.line));
            int from = Math.min(min.offset, max.offset);
            int to = Math.max(min.offset, max.offset);
            for (int i = to - 1; i >= from && i < line.formats.size(); --i) {
                line.formats.remove(i);
            }
        } else {
            for (int i = max.line; i >= min.line; --i) {
                int k;
                if (!this.hasLine(i)) continue;
                FormattedLine line = (FormattedLine)(this.text.get(i));
                if (i == max.line) {
                    for (k = max.offset - 1; k >= 0 && k < line.formats.size(); --k) {
                        line.formats.remove(k);
                    }
                    continue;
                }
                if (i != min.line) continue;
                for (k = line.formats.size() - 1; k >= min.offset; --k) {
                    line.formats.remove(k);
                }
            }
        }
        super.deleteSelection();
        this.notifyFormattedChanged();
    }

    public void writeNewLine() {
        if (this.hasLine(this.cursor.line)) {
            FormattedLine line = (FormattedLine)(this.text.get(this.cursor.line));
            int index = this.cursor.offset;
            ArrayList<String> nextFormats = new ArrayList<String>();
            if (index < line.formats.size()) {
                nextFormats.addAll(line.formats.subList(index, line.formats.size()));
                line.formats.subList(index, line.formats.size()).clear();
            }
            super.writeNewLine();
            if (this.hasLine(this.cursor.line)) {
                FormattedLine nextLine = (FormattedLine)(this.text.get(this.cursor.line));
                nextLine.formats.clear();
                nextLine.formats.addAll(nextFormats);
            }
        } else {
            super.writeNewLine();
        }
        this.notifyFormattedChanged();
    }

    public void applyFormat(String code) {
        if (this.isSelected()) {
            Cursor min = this.getMin();
            Cursor max = this.getMax();
            for (int lineIdx = min.line; lineIdx <= max.line && lineIdx < this.text.size(); ++lineIdx) {
                FormattedLine line = (FormattedLine)(this.text.get(lineIdx));
                int from = lineIdx == min.line ? min.offset : 0;
                int to = lineIdx == max.line ? max.offset : line.text.length();
                for (int i = from; i < to && i < line.formats.size(); ++i) {
                    String cur = line.formats.get(i);
                    if (UIFormattedTextarea.isColorCode(code)) {
                        String styles = UIFormattedTextarea.extractStyles(cur);
                        line.formats.set(i, code + styles);
                        continue;
                    }
                    if (cur.contains(code)) {
                        line.formats.set(i, cur.replace(code, ""));
                        continue;
                    }
                    line.formats.set(i, cur + code);
                }
            }
        } else if (UIFormattedTextarea.isColorCode(code)) {
            String styles = UIFormattedTextarea.extractStyles(this.currentActiveFormat != null ? this.currentActiveFormat : "\u00a7f");
            this.currentActiveFormat = code + styles;
        } else {
            String cur = this.currentActiveFormat != null ? this.currentActiveFormat : "\u00a7f";
            this.currentActiveFormat = cur.contains(code) ? cur.replace(code, "") : cur + code;
        }
        this.notifyFormattedChanged();
    }

    public void applyNormal() {
        if (this.isSelected()) {
            Cursor min = this.getMin();
            Cursor max = this.getMax();
            for (int lineIdx = min.line; lineIdx <= max.line && lineIdx < this.text.size(); ++lineIdx) {
                FormattedLine line = (FormattedLine)(this.text.get(lineIdx));
                int from = lineIdx == min.line ? min.offset : 0;
                int to = lineIdx == max.line ? max.offset : line.text.length();
                for (int i = from; i < to && i < line.formats.size(); ++i) {
                    char colorChar = UIFormattedTextarea.extractColorChar(line.formats.get(i));
                    line.formats.set(i, "\u00a7" + colorChar);
                }
            }
        } else {
            char colorChar = UIFormattedTextarea.extractColorChar(this.currentActiveFormat);
            this.currentActiveFormat = "\u00a7" + colorChar;
        }
        this.notifyFormattedChanged();
    }

    private void notifyFormattedChanged() {
        if (this.callback != null) {
            this.callback.accept(this.getFormattedText());
        }
    }

    private static boolean isColorCode(String code) {
        if (code == null || code.length() < 2 || code.charAt(0) != '\u00a7') {
            return false;
        }
        char c = Character.toLowerCase(code.charAt(1));
        return "0123456789abcdef".indexOf(c) >= 0;
    }

    private static String extractStyles(String format) {
        if (format == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < format.length(); ++i) {
            if (format.charAt(i) != '\u00a7' || i + 1 >= format.length()) continue;
            char c = Character.toLowerCase(format.charAt(i + 1));
            if ("lmno".indexOf(c) >= 0) {
                sb.append("\u00a7").append(c);
            }
            ++i;
        }
        return sb.toString();
    }

    protected void renderTextLine(UIContext context, String lineText, int i, int j, int nx, int ny) {
        if (i < 0 || i >= this.text.size()) {
            super.renderTextLine(context, lineText, i, j, nx, ny);
            return;
        }
        FormattedLine line = (FormattedLine)(this.text.get(i));
        FontRenderer font = this.getFont();
        int curX = nx;
        int startChar = 0;
        if (line.wrappedLines != null && j > 0) {
            for (int w = 0; w < j; ++w) {
                startChar += ((String)line.wrappedLines.get(w)).length();
            }
        }
        for (int k = 0; k < lineText.length(); ++k) {
            int globalCharIdx = startChar + k;
            String f = globalCharIdx < line.formats.size() ? line.formats.get(globalCharIdx) : "\u00a7f";
            String ch = String.valueOf(lineText.charAt(k));
            context.batcher.text(f + ch, (float)curX, (float)ny, -1, true);
            curX += font.getWidth(ch);
        }
    }

    public static class FormattedLine
    extends TextLine {
        public final List<String> formats = new ArrayList<String>();

        public FormattedLine(String plainText) {
            super(plainText);
            for (int i = 0; i < plainText.length(); ++i) {
                this.formats.add("\u00a7f");
            }
        }

        public FormattedLine(String plainText, List<String> formats) {
            super(plainText);
            this.formats.addAll(formats);
            while (this.formats.size() < plainText.length()) {
                this.formats.add("\u00a7f");
            }
        }

        public void calculateWrappedLines(FontRenderer font, int w) {
            if (this.text.isEmpty() || font.getWidth(this.text) <= w) {
                this.wrappedLines = null;
                return;
            }
            ArrayList<String> lines = new ArrayList<String>();
            int left = 0;
            int c = this.text.length();
            while (left < c) {
                int right;
                for (right = left; right < c && font.getWidth(this.text.substring(left, right + 1)) <= w; ++right) {
                }
                if (right == c) {
                    lines.add(this.text.substring(left));
                    break;
                }
                if (right == left) {
                    right = left + 1;
                } else {
                    String chunk = this.text.substring(left, right);
                    int spaceIdx = chunk.lastIndexOf(32);
                    if (spaceIdx > 0 && spaceIdx >= chunk.length() - 4) {
                        right = left + spaceIdx + 1;
                    }
                }
                lines.add(this.text.substring(left, right));
                left = right;
            }
            this.wrappedLines = lines.size() < 2 ? null : lines;
        }
    }
}

