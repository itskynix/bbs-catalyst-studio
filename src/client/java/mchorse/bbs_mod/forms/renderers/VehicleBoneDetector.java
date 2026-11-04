package mchorse.bbs_mod.forms.renderers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Guesses a vehicle model's body, steering wheel and four wheels from its bone names, and for
 * wheels whose names say nothing (Wheel1, wheel2 ...) from where they sit.
 *
 * <p>Names are split into words (by separators and camel case: {@code wheel_f_l},
 * {@code WheelFrontLeft} and {@code wheelFL} read alike), English and Turkish both, and Turkish
 * names written as one word ({@code tekerlekonsag}, {@code tekerlekarkasol}) are read by their
 * parts. "r" means rear as the first position word and right as the second, so {@code r_l} is rear
 * left and {@code f_r} front right.</p>
 *
 * <p>Models often hold the same wheel twice, a pivot group and the mesh inside it, both called a
 * wheel. Turning both turns the wheel twice, so a bone inside one already picked is never picked,
 * and of two matches where one holds the other, the outer one wins. Unnamed wheels are only
 * placed when no wheel is named at all, by their place against the middle of them all: front is
 * -Z, and facing front, left is -X.</p>
 */
public class VehicleBoneDetector
{
    public static final String BODY = "body";
    public static final String STEERING = "steering";
    public static final String FL = "fl";
    public static final String FR = "fr";
    public static final String RL = "rl";
    public static final String RR = "rr";

    /* Not "root" or "car": those usually hold the whole model */
    private static final String[] BODY_NAMES = {"body", "chassis", "corpus", "middle", "frame", "hull", "shell", "govde", "sasi", "kasa", "temel", "karoser"};

    /**
     * @param pivot  where a bone sits in the model ({x, y, z}), null when unknown
     * @param parent a bone's parent bone, null at the top or when unknown
     * @return the slot ({@link #BODY}, {@link #STEERING}, {@link #FL} ...) to the bone found for it
     */
    public static Map<String, String> detect(Collection<String> bones, Function<String, float[]> pivot, Function<String, String> parent)
    {
        Map<String, String> found = new LinkedHashMap<>();

        /* Steering: the steering wheel by its own name before anything only called steering */
        String steering = outermost(bones, parent, (bone) ->
        {
            String flat = flat(bone);

            return flat.contains("steeringwheel") || flat.contains("direksiyon");
        });

        if (steering == null)
        {
            steering = outermost(bones, parent, (bone) -> words(bone).contains("steering") || words(bone).contains("steer"));
        }

        if (steering != null)
        {
            found.put(STEERING, steering);
        }

        List<String> anonymous = new ArrayList<>();
        boolean named = false;

        for (String bone : bones)
        {
            if (!isWheel(bone) || bone.equals(found.get(STEERING)))
            {
                continue;
            }

            String slot = slot(bone);

            if (slot == null)
            {
                anonymous.add(bone);

                continue;
            }

            named = true;

            String previous = found.get(slot);

            /* The outer of two that hold one another */
            if (previous == null || isWithin(previous, bone, parent))
            {
                found.put(slot, bone);
            }
        }

        if (!named && pivot != null && !anonymous.isEmpty())
        {
            placeByPosition(anonymous, pivot, parent, found);
        }

        /* The body: preferably one that does not hold the wheels, or leaning it leans them too */
        String body = null;

        for (String name : BODY_NAMES)
        {
            for (String bone : bones)
            {
                if (found.containsValue(bone) || !contains(bone, name))
                {
                    continue;
                }

                boolean holdsWheels = false;

                for (String slot : new String[] {FL, FR, RL, RR})
                {
                    holdsWheels |= found.get(slot) != null && isWithin(found.get(slot), bone, parent);
                }

                if (!holdsWheels)
                {
                    body = bone;

                    break;
                }

                if (body == null)
                {
                    body = bone;
                }
            }

            if (body != null && !holdsAnyWheel(body, found, parent))
            {
                break;
            }
        }

        if (body != null)
        {
            found.put(BODY, body);
        }

        return found;
    }

    private static boolean holdsAnyWheel(String bone, Map<String, String> found, Function<String, String> parent)
    {
        for (String slot : new String[] {FL, FR, RL, RR})
        {
            if (found.get(slot) != null && isWithin(found.get(slot), bone, parent))
            {
                return true;
            }
        }

        return false;
    }

    private static void placeByPosition(List<String> wheels, Function<String, float[]> pivot, Function<String, String> parent, Map<String, String> found)
    {
        List<String> outer = new ArrayList<>();

        for (String bone : wheels)
        {
            boolean inner = false;

            for (String other : wheels)
            {
                inner |= !other.equals(bone) && isWithin(bone, other, parent);
            }

            if (!inner && pivot.apply(bone) != null)
            {
                outer.add(bone);
            }
        }

        if (outer.size() < 2)
        {
            return;
        }

        float mx = 0F;
        float mz = 0F;

        for (String bone : outer)
        {
            mx += pivot.apply(bone)[0];
            mz += pivot.apply(bone)[2];
        }

        mx /= outer.size();
        mz /= outer.size();

        for (String bone : outer)
        {
            float[] at = pivot.apply(bone);
            String slot = (at[2] < mz ? "f" : "r") + (at[0] < mx ? "l" : "r");

            found.putIfAbsent(slot, bone);
        }
    }

    /** Of the bones that match, the outermost: a match inside another match is the same part */
    private static String outermost(Collection<String> bones, Function<String, String> parent, Predicate<String> match)
    {
        String best = null;

        for (String bone : bones)
        {
            if (match.test(bone) && (best == null || isWithin(best, bone, parent)))
            {
                best = bone;
            }
        }

        return best;
    }

    /** Whether {@code bone} sits somewhere inside {@code ancestor} */
    static boolean isWithin(String bone, String ancestor, Function<String, String> parent)
    {
        if (bone == null || ancestor == null || parent == null || bone.equals(ancestor))
        {
            return false;
        }

        String current = parent.apply(bone);

        for (int depth = 0; current != null && depth < 64; depth++)
        {
            if (current.equals(ancestor))
            {
                return true;
            }

            current = parent.apply(current);
        }

        return false;
    }

    private static boolean isWheel(String bone)
    {
        List<String> words = words(bone);
        String flat = flat(bone);

        if (flat.contains("steeringwheel") || flat.contains("spare") || flat.contains("yedek") || words.contains("wheels"))
        {
            return false;
        }

        return words.contains("wheel") || words.contains("tire") || words.contains("tyre") || flat.contains("wheel") || flat.contains("teker");
    }

    private static boolean contains(String bone, String name)
    {
        return words(bone).contains(name) || flat(bone).equals(name);
    }

    /** Front/rear then left/right of a wheel, from its name; null when the name does not say */
    static String slot(String bone)
    {
        String flat = flat(bone);

        /* Turkish written as one word: tekerlekonsag, tekerlek_arka_sol */
        if (flat.contains("teker"))
        {
            String rest = flat.replace("tekerlek", "").replace("teker", "");
            String axle = rest.contains("arka") ? "r" : (rest.contains("on") ? "f" : null);
            String side = rest.contains("sol") ? "l" : (rest.contains("sag") ? "r" : null);

            if (axle != null && side != null)
            {
                return axle + side;
            }
        }

        String byWords = slot(words(bone));

        if (byWords != null)
        {
            return byWords;
        }

        /* Words run together without a capital between them: FrontLeftwheel */
        String axle = flat.contains("front") ? "f" : (flat.contains("rear") || flat.contains("back") ? "r" : null);
        String side = flat.contains("left") ? "l" : (flat.contains("right") ? "r" : null);

        return axle != null && side != null ? axle + side : null;
    }

    static String slot(List<String> words)
    {
        String axle = null;
        String side = null;

        for (String word : words)
        {
            switch (word)
            {
                case "fl" -> { axle = "f"; side = "l"; }
                case "fr" -> { axle = "f"; side = "r"; }
                case "rl", "bl" -> { axle = "r"; side = "l"; }
                case "rr", "br" -> { axle = "r"; side = "r"; }
                case "front", "f", "forward", "frontleft", "frontright" -> {
                    axle = axle == null ? "f" : axle;

                    if (word.endsWith("left")) side = "l";
                    if (word.endsWith("right")) side = "r";
                }
                case "rear", "back", "b", "rearleft", "rearright", "backleft", "backright" -> {
                    axle = axle == null ? "r" : axle;

                    if (word.endsWith("left")) side = "l";
                    if (word.endsWith("right")) side = "r";
                }
                case "left", "l" -> side = side == null ? "l" : side;
                case "right" -> side = side == null ? "r" : side;
                /* rear while there is no axle yet, right once there is */
                case "r" -> {
                    if (axle == null) axle = "r";
                    else if (side == null) side = "r";
                }
                default -> {}
            }
        }

        return axle != null && side != null ? axle + side : null;
    }

    /** Lower case, Turkish letters to their plain ones, nothing but letters and digits */
    static String flat(String name)
    {
        StringBuilder builder = new StringBuilder();

        for (char c : name.toLowerCase(Locale.ROOT).toCharArray())
        {
            switch (c)
            {
                case 'ğ' -> builder.append('g');
                case 'ş' -> builder.append('s');
                case 'ı' -> builder.append('i');
                case 'ö' -> builder.append('o');
                case 'ü' -> builder.append('u');
                case 'ç' -> builder.append('c');
                default -> {
                    if (Character.isLetterOrDigit(c))
                    {
                        builder.append(c);
                    }
                }
            }
        }

        return builder.toString();
    }

    /** "WheelFrontLeft", "wheel_f_l", "wheel.FL" -> wheel front left / wheel f l / wheel fl */
    static List<String> words(String name)
    {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char previous = 0;

        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);

            if (!Character.isLetterOrDigit(c))
            {
                flush(words, word);
            }
            else
            {
                /* A capital after a small letter starts a word: WheelFront -> Wheel, Front; FL stays one */
                if (Character.isUpperCase(c) && Character.isLowerCase(previous))
                {
                    flush(words, word);
                }

                /* Digits end a word: wheel1 -> wheel, 1 */
                if (word.length() > 0 && Character.isDigit(c) != Character.isDigit(previous))
                {
                    flush(words, word);
                }

                word.append(c);
            }

            previous = c;
        }

        flush(words, word);

        return words;
    }

    private static void flush(List<String> words, StringBuilder word)
    {
        if (word.length() > 0)
        {
            words.add(word.toString().toLowerCase(Locale.ROOT));
            word.setLength(0);
        }
    }

    /* Bits of a door that are named after it but are not the door: they ride inside it */
    private static final String[] DOOR_BITS = {"handle", "kolu", "window", "glass", "cam", "mirror", "ayna", "lock", "kilit", "hinge", "mentese", "frame", "cerceve"};

    /**
     * The parts that open, from their names: doors ({@code Door1}, {@code onsolkapi},
     * {@code arkasagkapi}) swing about their yaw axis, the hood ({@code kaput}, {@code onkapak},
     * {@code hood}) and the trunk ({@code bagaj}, {@code trunk}) about their pitch axis. Of a part
     * held twice (a pivot group and the mesh inside it) only the outer one is listed.
     *
     * @return each part's bone and its axis ({@code 0} yaw, {@code 1} pitch), in the model's order
     */
    public static Map<String, Integer> detectParts(Collection<String> bones, Function<String, String> parent)
    {
        Map<String, Integer> matched = new LinkedHashMap<>();

        for (String bone : bones)
        {
            Integer axis = partAxis(bone);

            if (axis != null)
            {
                matched.put(bone, axis);
            }
        }

        Map<String, Integer> found = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : matched.entrySet())
        {
            boolean inner = false;

            for (String other : matched.keySet())
            {
                if (isWithin(entry.getKey(), other, parent))
                {
                    inner = true;

                    break;
                }
            }

            if (!inner)
            {
                found.put(entry.getKey(), entry.getValue());
            }
        }

        return found;
    }

    /** A seat ({@code koltuk1}, {@code arkakoltuk}, {@code seat_driver}): what a click boards through, as a door does */
    public static boolean isSeat(String bone)
    {
        String flat = flat(bone);

        return flat.contains("koltuk") || flat.contains("seat") || flat.contains("sandalye");
    }

    /** 1 for a lid (hood, trunk), 0 for a door, null for anything else */
    public static Integer partAxis(String bone)
    {
        String flat = flat(bone);
        List<String> words = words(bone);

        if (flat.contains("hood") || flat.contains("bonnet") || flat.contains("kaput") || flat.contains("onkapak") || flat.contains("motorkapa")
            || flat.contains("trunk") || flat.contains("bagaj") || flat.contains("tailgate") || flat.contains("arkakapak") || words.contains("boot"))
        {
            return 1;
        }

        if (flat.contains("door") || flat.contains("kapi"))
        {
            for (String bit : DOOR_BITS)
            {
                if (flat.contains(bit))
                {
                    return null;
                }
            }

            return 0;
        }

        return null;
    }
}
