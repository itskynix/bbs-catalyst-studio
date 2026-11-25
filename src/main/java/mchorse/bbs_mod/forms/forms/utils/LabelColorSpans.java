package mchorse.bbs_mod.forms.forms.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Colours of parts of a label's text, kept as one string so old labels (which have none) load as they
 * were and new ones save as plain data: {@code start-end-RRGGBB;...}, the offsets counted in the
 * characters of the raw text, new lines included, the end not included.
 */
public class LabelColorSpans
{
    public record Span(int start, int end, int rgb) {}

    public static List<Span> parse(String data)
    {
        List<Span> spans = new ArrayList<>();

        if (data == null || data.isEmpty())
        {
            return spans;
        }

        for (String part : data.split(";"))
        {
            String[] fields = part.split("-");

            if (fields.length != 3)
            {
                continue;
            }

            try
            {
                int start = Integer.parseInt(fields[0]);
                int end = Integer.parseInt(fields[1]);

                if (start >= 0 && end > start)
                {
                    spans.add(new Span(start, end, Integer.parseInt(fields[2], 16) & 0xffffff));
                }
            }
            catch (NumberFormatException e)
            {}
        }

        return spans;
    }

    public static String write(List<Span> spans)
    {
        StringBuilder builder = new StringBuilder();

        for (Span span : spans)
        {
            if (!builder.isEmpty())
            {
                builder.append(';');
            }

            builder.append(span.start).append('-').append(span.end).append('-').append(String.format("%06x", span.rgb));
        }

        return builder.toString();
    }

    /** Colours the range, or gives it back the label's own colour when {@code rgb} is null. */
    public static List<Span> apply(List<Span> spans, int start, int end, Integer rgb)
    {
        List<Span> out = new ArrayList<>();

        for (Span span : spans)
        {
            if (span.start < start)
            {
                out.add(new Span(span.start, Math.min(span.end, start), span.rgb));
            }

            if (span.end > end)
            {
                out.add(new Span(Math.max(span.start, end), span.end, span.rgb));
            }
        }

        if (rgb != null)
        {
            out.add(new Span(start, end, rgb & 0xffffff));
        }

        return tidy(out);
    }

    /** Moves the spans along with an edit of the text: what was typed or removed shifts what follows it. */
    public static List<Span> shift(List<Span> spans, String before, String after)
    {
        int limit = Math.min(before.length(), after.length());
        int prefix = 0;
        int suffix = 0;

        while (prefix < limit && before.charAt(prefix) == after.charAt(prefix))
        {
            prefix++;
        }

        while (suffix < limit - prefix && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix))
        {
            suffix++;
        }

        int oldEnd = before.length() - suffix;
        int newEnd = after.length() - suffix;
        int delta = after.length() - before.length();
        List<Span> out = new ArrayList<>();

        for (Span span : spans)
        {
            int start = span.start >= oldEnd ? span.start + delta : span.start <= prefix ? span.start : newEnd;
            int end = span.end <= prefix ? span.end : span.end >= oldEnd ? span.end + delta : prefix;

            if (end > start)
            {
                out.add(new Span(start, end, span.rgb));
            }
        }

        return tidy(out);
    }

    private static List<Span> tidy(List<Span> spans)
    {
        spans.removeIf((span) -> span.end <= span.start);
        spans.sort(Comparator.comparingInt(Span::start));

        List<Span> out = new ArrayList<>();

        for (Span span : spans)
        {
            Span last = out.isEmpty() ? null : out.get(out.size() - 1);

            if (last != null && last.rgb == span.rgb && last.end >= span.start)
            {
                out.set(out.size() - 1, new Span(last.start, Math.max(last.end, span.end), last.rgb));
            }
            else
            {
                out.add(span);
            }
        }

        return out;
    }

    /**
     * The colour (null for the label's own) of every visible character of the text, in order: spaces,
     * new lines and the {@code [x} formatting codes are not counted, so the list matches the letters of
     * the drawn lines however the text was wrapped or trimmed.
     */
    public static List<Integer> visible(String raw, List<Span> spans)
    {
        List<Integer> colors = new ArrayList<>();
        boolean codes = raw.contains("[");

        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);

            if (codes && c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '[')
            {
                /* An escaped bracket is drawn as a bracket */
                i++;
                colors.add(colorAt(spans, i));
            }
            else if ((c == '§' || (codes && c == '[')) && i + 1 < raw.length())
            {
                i++;
            }
            else if (!Character.isWhitespace(c))
            {
                colors.add(colorAt(spans, i));
            }
        }

        return colors;
    }

    private static Integer colorAt(List<Span> spans, int offset)
    {
        for (Span span : spans)
        {
            if (offset >= span.start && offset < span.end)
            {
                return span.rgb;
            }
        }

        return null;
    }
}
