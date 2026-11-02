package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CompoundFxPresets
{
    public static final String PRESET_TERMINAL_NIGHT = "Terminal_Night";
    public static final String PRESET_NISYAN_LOBBY_SICK_YELLOW = "Nisyan_Lobby_SickYellow";
    public static final String PRESET_MENTAL_BREAKDOWN_RED = "Mental_Breakdown_Red";

    public static File getPresetsDirectory()
    {
        File dir = BBSMod.getSettingsPath("presets/compound_fx");

        if (!dir.exists())
        {
            dir.mkdirs();
        }

        return dir;
    }

    public static List<String> getAvailablePresets()
    {
        List<String> list = new ArrayList<>();

        list.add(PRESET_TERMINAL_NIGHT);
        list.add(PRESET_NISYAN_LOBBY_SICK_YELLOW);
        list.add(PRESET_MENTAL_BREAKDOWN_RED);

        File dir = getPresetsDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));

        if (files != null)
        {
            for (File file : files)
            {
                String name = file.getName().substring(0, file.getName().length() - 5);

                if (!list.contains(name))
                {
                    list.add(name);
                }
            }
        }

        return list;
    }

    public static boolean applyPreset(CompoundFxClip clip, String presetName)
    {
        if (PRESET_TERMINAL_NIGHT.equalsIgnoreCase(presetName))
        {
            applyTerminalNight(clip);

            return true;
        }

        if (PRESET_NISYAN_LOBBY_SICK_YELLOW.equalsIgnoreCase(presetName))
        {
            applyNisyanLobbySickYellow(clip);

            return true;
        }

        if (PRESET_MENTAL_BREAKDOWN_RED.equalsIgnoreCase(presetName))
        {
            applyMentalBreakdownRed(clip);

            return true;
        }

        return loadCustomPreset(presetName, clip);
    }

    public static void applyTerminalNight(CompoundFxClip clip)
    {
        /* Terminal_Night: Cold cyan-blue tint, muted saturation, crisp vignette & grain */
        int duration = clip.duration.get();

        clip.children.removeAll();

        ColorGradeClip grade = new ColorGradeClip();
        grade.tick.set(0);
        grade.duration.set(duration);
        grade.layer.set(0);
        grade.overlayColor.set(0x16354D);
        setFallbackOrKeyframe(grade.overlayAlpha, 0.22D);
        setFallbackOrKeyframe(grade.saturation, 0.80D);
        setFallbackOrKeyframe(grade.brightness, -0.04D);
        setFallbackOrKeyframe(grade.contrast, 1.18D);
        setFallbackOrKeyframe(grade.liftR, -0.04D);
        setFallbackOrKeyframe(grade.liftG, 0.01D);
        setFallbackOrKeyframe(grade.liftB, 0.08D);
        setFallbackOrKeyframe(grade.gammaR, 0.92D);
        setFallbackOrKeyframe(grade.gammaG, 1.02D);
        setFallbackOrKeyframe(grade.gammaB, 1.12D);
        setFallbackOrKeyframe(grade.gainR, 0.90D);
        setFallbackOrKeyframe(grade.gainG, 1.00D);
        setFallbackOrKeyframe(grade.gainB, 1.15D);
        clip.children.addClip(grade);

        VignetteClip vignette = new VignetteClip();
        vignette.tick.set(0);
        vignette.duration.set(duration);
        vignette.layer.set(1);
        vignette.color.set(0x040B10);
        setFallbackOrKeyframe(vignette.intensity, 0.55D);
        setFallbackOrKeyframe(vignette.radius, 0.70D);
        setFallbackOrKeyframe(vignette.softness, 0.60D);
        clip.children.addClip(vignette);

        GrainClip grain = new GrainClip();
        grain.tick.set(0);
        grain.duration.set(duration);
        grain.layer.set(2);
        setFallbackOrKeyframe(grain.strength, 0.28D);
        setFallbackOrKeyframe(grain.size, 1.0D);
        clip.children.addClip(grain);

        clip.children.sortLayers();
        clip.children.sync();
    }

    public static void applyNisyanLobbySickYellow(CompoundFxClip clip)
    {
        /* Nisyan_Lobby_SickYellow: Sickly greenish-yellow tint, suppressed blue, chromatic aberration */
        int duration = clip.duration.get();

        clip.children.removeAll();

        ColorGradeClip grade = new ColorGradeClip();
        grade.tick.set(0);
        grade.duration.set(duration);
        grade.layer.set(0);
        grade.overlayColor.set(0x828A28);
        setFallbackOrKeyframe(grade.overlayAlpha, 0.26D);
        setFallbackOrKeyframe(grade.saturation, 0.72D);
        setFallbackOrKeyframe(grade.brightness, -0.02D);
        setFallbackOrKeyframe(grade.contrast, 1.25D);
        setFallbackOrKeyframe(grade.liftR, 0.04D);
        setFallbackOrKeyframe(grade.liftG, 0.06D);
        setFallbackOrKeyframe(grade.liftB, -0.06D);
        setFallbackOrKeyframe(grade.gammaR, 1.10D);
        setFallbackOrKeyframe(grade.gammaG, 1.18D);
        setFallbackOrKeyframe(grade.gammaB, 0.75D);
        setFallbackOrKeyframe(grade.gainR, 1.05D);
        setFallbackOrKeyframe(grade.gainG, 1.12D);
        setFallbackOrKeyframe(grade.gainB, 0.82D);
        clip.children.addClip(grade);

        VignetteClip vignette = new VignetteClip();
        vignette.tick.set(0);
        vignette.duration.set(duration);
        vignette.layer.set(1);
        vignette.color.set(0x151804);
        setFallbackOrKeyframe(vignette.intensity, 0.38D);
        setFallbackOrKeyframe(vignette.radius, 0.75D);
        setFallbackOrKeyframe(vignette.softness, 0.50D);
        clip.children.addClip(vignette);

        GrainClip grain = new GrainClip();
        grain.tick.set(0);
        grain.duration.set(duration);
        grain.layer.set(2);
        setFallbackOrKeyframe(grain.strength, 0.20D);
        setFallbackOrKeyframe(grain.size, 1.1D);
        clip.children.addClip(grain);

        clip.children.sortLayers();
        clip.children.sync();
    }

    public static void applyMentalBreakdownRed(CompoundFxClip clip)
    {
        /* Mental_Breakdown_Red: Violent crimson red, barrel distortion, intense chroma, grain & motion blur */
        int duration = clip.duration.get();

        clip.children.removeAll();

        ColorGradeClip grade = new ColorGradeClip();
        grade.tick.set(0);
        grade.duration.set(duration);
        grade.layer.set(0);
        grade.overlayColor.set(0x990000);
        setFallbackOrKeyframe(grade.overlayAlpha, 0.32D);
        setFallbackOrKeyframe(grade.saturation, 1.45D);
        setFallbackOrKeyframe(grade.brightness, 0.04D);
        setFallbackOrKeyframe(grade.contrast, 1.50D);
        setFallbackOrKeyframe(grade.liftR, 0.20D);
        setFallbackOrKeyframe(grade.liftG, -0.08D);
        setFallbackOrKeyframe(grade.liftB, -0.08D);
        setFallbackOrKeyframe(grade.gammaR, 1.38D);
        setFallbackOrKeyframe(grade.gammaG, 0.68D);
        setFallbackOrKeyframe(grade.gammaB, 0.68D);
        setFallbackOrKeyframe(grade.gainR, 1.45D);
        setFallbackOrKeyframe(grade.gainG, 0.65D);
        setFallbackOrKeyframe(grade.gainB, 0.65D);
        clip.children.addClip(grade);

        VignetteClip vignette = new VignetteClip();
        vignette.tick.set(0);
        vignette.duration.set(duration);
        vignette.layer.set(1);
        vignette.color.set(0x330000);
        setFallbackOrKeyframe(vignette.intensity, 0.65D);
        setFallbackOrKeyframe(vignette.radius, 0.60D);
        setFallbackOrKeyframe(vignette.softness, 0.40D);
        clip.children.addClip(vignette);

        GrainClip grain = new GrainClip();
        grain.tick.set(0);
        grain.duration.set(duration);
        grain.layer.set(2);
        setFallbackOrKeyframe(grain.strength, 0.45D);
        setFallbackOrKeyframe(grain.size, 1.2D);
        clip.children.addClip(grain);

        LensDistortionClip distortion = new LensDistortionClip();
        distortion.tick.set(0);
        distortion.duration.set(duration);
        distortion.layer.set(3);
        setFallbackOrKeyframe(distortion.distortion, 0.15D);
        setFallbackOrKeyframe(distortion.scale, 1.05D);
        clip.children.addClip(distortion);

        clip.children.sortLayers();
        clip.children.sync();
    }

    private static void setFallbackOrKeyframe(FxClip.Channel channel, double value)
    {
        if (channel.channel.isEmpty())
        {
            channel.channel.insert(0, value);
        }
        else
        {
            channel.channel.get(0).setValue(value);
        }
    }

    public static boolean saveCustomPreset(String name, CompoundFxClip clip)
    {
        if (name == null || name.trim().isEmpty())
        {
            return false;
        }

        try
        {
            BaseType data = clip.toData();
            File file = new File(getPresetsDirectory(), name.trim() + ".json");

            DataToString.writeSilently(file, data, true);

            return true;
        }
        catch (Throwable e)
        {
            e.printStackTrace();

            return false;
        }
    }

    public static boolean loadCustomPreset(String name, CompoundFxClip clip)
    {
        if (name == null || name.trim().isEmpty())
        {
            return false;
        }

        try
        {
            File file = new File(getPresetsDirectory(), name.trim() + ".json");

            if (!file.exists())
            {
                return false;
            }

            BaseType baseType = DataToString.read(file);

            if (baseType != null && baseType.isMap())
            {
                clip.fromData(baseType.asMap());

                return true;
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }

        return false;
    }
}
