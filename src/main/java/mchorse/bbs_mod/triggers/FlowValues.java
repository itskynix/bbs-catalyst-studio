package mchorse.bbs_mod.triggers;

/**
 * Reading flow values, which are kept as text: numbers (true and false count as 1 and 0) and the
 * comparisons of a branch. Plain Java, so the code view and the runtime read values the same way.
 */
public class FlowValues
{
    public static boolean compare(String a, String operator, String b)
    {
        Double x = number(a);
        Double y = number(b);

        if (x != null && y != null)
        {
            return switch (operator)
            {
                case "!=" -> !x.equals(y);
                case ">" -> x > y;
                case "<" -> x < y;
                case ">=" -> x >= y;
                case "<=" -> x <= y;
                default -> x.equals(y);
            };
        }

        int c = a.compareTo(b);

        return switch (operator)
        {
            case "!=" -> c != 0;
            case ">" -> c > 0;
            case "<" -> c < 0;
            case ">=" -> c >= 0;
            case "<=" -> c <= 0;
            default -> c == 0;
        };
    }

    private static Double number(String text)
    {
        if (text == null)
        {
            return null;
        }

        String t = text.trim();

        if (t.equals("true")) return 1D;
        if (t.equals("false")) return 0D;

        try
        {
            return Double.parseDouble(t);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    public static double parse(String text, double fallback)
    {
        Double value = number(text);

        return value == null ? fallback : value;
    }

    /** Whether a value counts as true: "true", or a number other than zero. */
    public static boolean truthy(String value)
    {
        if (value == null)
        {
            return false;
        }

        Double number = number(value);

        return number != null ? number != 0D : value.trim().equalsIgnoreCase("true");
    }

    /**
     * A comparison over any number of values: && is true when all are; the others hold between every
     * value and the next, so {@code a < b < c} reads as it would on paper.
     */
    public static boolean check(String operator, java.util.List<String> values)
    {
        if (operator.equals("&&"))
        {
            for (String value : values)
            {
                if (!truthy(value))
                {
                    return false;
                }
            }

            return true;
        }

        for (int i = 1; i < values.size(); i++)
        {
            if (!compare(values.get(i - 1), operator, values.get(i)))
            {
                return false;
            }
        }

        return true;
    }

    /** A Boolean operator over its inputs: and, or, nand, nor, xor (an odd number true), xnor. */
    public static boolean logic(String operator, java.util.List<String> values)
    {
        int count = 0;

        for (String value : values)
        {
            count += truthy(value) ? 1 : 0;
        }

        return switch (operator)
        {
            case "or" -> count > 0;
            case "nand" -> count < values.size();
            case "nor" -> count == 0;
            case "xor" -> count % 2 == 1;
            case "xnor" -> count % 2 == 0;
            default -> count == values.size();
        };
    }

    /** Three numbers out of "x y z" (or "x, y, z"); a missing one is 0. */
    public static double[] vec(String value)
    {
        String[] split = value == null ? new String[0] : value.trim().split("[ ,]+");
        double[] xyz = new double[3];

        for (int i = 0; i < 3 && i < split.length; i++)
        {
            xyz[i] = parse(split[i], 0);
        }

        return xyz;
    }

    /** A list out of text: comma separated, each item trimmed, empty text an empty list. */
    public static java.util.List<String> list(String value)
    {
        java.util.List<String> list = new java.util.ArrayList<>();

        if (value == null || value.isBlank())
        {
            return list;
        }

        for (String item : value.split(","))
        {
            list.add(item.trim());
        }

        return list;
    }

    /** A list back into text: each item's own commas become spaces, so it stays one item. */
    public static String join(java.util.List<String> items)
    {
        java.util.List<String> clean = new java.util.ArrayList<>();

        for (String item : items)
        {
            clean.add(item == null ? "" : item.replace(",", " ").trim());
        }

        return String.join(",", clean);
    }

    /** The item at the index, or empty text past either end. */
    public static String item(String list, int index)
    {
        java.util.List<String> items = list(list);

        return index >= 0 && index < items.size() ? items.get(index) : "";
    }

    /** The list with the item at the index replaced; an index past the end grows the list with empty items up to it. */
    public static String setItem(String list, int index, String value, int max)
    {
        java.util.List<String> items = list(list);

        if (index < 0 || index >= max)
        {
            return join(items);
        }

        while (items.size() <= index)
        {
            items.add("");
        }

        items.set(index, value);

        return join(items);
    }

    /** Whole numbers without a trailing ".0", so a counter reads 3, not 3.0. */
    public static String format(double value)
    {
        return value == Math.rint(value) && Math.abs(value) < 1E15 ? String.valueOf((long) value) : String.valueOf(value);
    }
}
