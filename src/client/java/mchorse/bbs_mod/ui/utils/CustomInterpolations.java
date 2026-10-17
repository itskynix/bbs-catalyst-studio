package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The cubic-bezier easing presets the user saved from the interpolation editor, kept in
 * {@code custom_interpolations.json} next to the other settings so they outlive the game.
 */
public class CustomInterpolations
{
    private static final double EPSILON = 1.0E-6D;

    private static List<Preset> presets;

    public record Preset(String name, double x1, double y1, double x2, double y2)
    {
        public boolean sameCurve(double x1, double y1, double x2, double y2)
        {
            return Math.abs(this.x1 - x1) < EPSILON && Math.abs(this.y1 - y1) < EPSILON
                && Math.abs(this.x2 - x2) < EPSILON && Math.abs(this.y2 - y2) < EPSILON;
        }
    }

    private static File file()
    {
        return BBSMod.getSettingsPath("custom_interpolations.json");
    }

    public static List<Preset> getPresets()
    {
        if (presets == null)
        {
            presets = new ArrayList<>();

            load();
        }

        return presets;
    }

    private static void load()
    {
        try
        {
            File file = file();

            if (file == null || !file.exists())
            {
                return;
            }

            BaseType data = DataToString.read(file);

            if (data instanceof ListType list)
            {
                for (int i = 0; i < list.size(); i++)
                {
                    BaseType entry = list.get(i);

                    if (entry != null && entry.isMap())
                    {
                        MapType map = entry.asMap();
                        String name = map.getString("name");

                        if (!name.isEmpty())
                        {
                            presets.add(new Preset(name, map.getDouble("x1"), map.getDouble("y1"), map.getDouble("x2"), map.getDouble("y2")));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Adds a preset. One with the same name, or the very same curve, is overwritten in place
     * without asking; any other duplicate of it is dropped.
     */
    public static void save(String name, double x1, double y1, double x2, double y2)
    {
        List<Preset> list = getPresets();
        Preset preset = new Preset(name, x1, y1, x2, y2);
        int index = -1;

        for (int i = 0; i < list.size(); i++)
        {
            Preset other = list.get(i);

            if (other.name().equalsIgnoreCase(name) || other.sameCurve(x1, y1, x2, y2))
            {
                if (index == -1)
                {
                    index = i;
                    list.set(i, preset);
                }
                else
                {
                    list.remove(i--);
                }
            }
        }

        if (index == -1)
        {
            list.add(preset);
        }

        write();
    }

    private static void write()
    {
        ListType data = new ListType();

        for (Preset preset : getPresets())
        {
            MapType map = new MapType();

            map.putString("name", preset.name());
            map.putDouble("x1", preset.x1());
            map.putDouble("y1", preset.y1());
            map.putDouble("x2", preset.x2());
            map.putDouble("y2", preset.y2());
            data.add(map);
        }

        DataToString.writeSilently(file(), data, true);
    }
}
