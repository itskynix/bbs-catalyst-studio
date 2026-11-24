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
}
