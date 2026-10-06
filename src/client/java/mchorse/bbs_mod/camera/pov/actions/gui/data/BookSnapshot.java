/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.gui.data;

import java.util.ArrayList;
import java.util.List;

public final class BookSnapshot {
    public static final char PAGE_SEP = '\u001e';
    public final boolean writable;
    public final boolean signing;
    public final int page;
    public final String pages;
    public final String title;
    public final String author;
    public final int selStart;
    public final int selEnd;

    public BookSnapshot(boolean writable, boolean signing, int page, String pages, String title, String author, int selStart, int selEnd) {
        this.writable = writable;
        this.signing = signing;
        this.page = Math.max(0, page);
        this.pages = pages == null ? "" : pages;
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.selStart = Math.max(0, selStart);
        this.selEnd = Math.max(0, selEnd);
    }

    public static String pack(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder packed = new StringBuilder();
        for (int i = 0; i < values.size(); ++i) {
            String page;
            if (i > 0) {
                packed.append('\u001e');
            }
            packed.append((page = values.get(i)) == null ? "" : page);
        }
        return packed.toString();
    }

    public static List<String> unpack(String packed) {
        ArrayList<String> values = new ArrayList<String>();
        if (packed == null || packed.isEmpty()) {
            values.add("");
            return values;
        }
        int start = 0;
        for (int i = 0; i < packed.length(); ++i) {
            if (packed.charAt(i) != '\u001e') continue;
            values.add(packed.substring(start, i));
            start = i + 1;
        }
        values.add(packed.substring(start));
        if (values.isEmpty()) {
            values.add("");
        }
        return values;
    }
}

