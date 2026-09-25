package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry and persistence manager for video export profiles.
 */
public class ExportProfiles
{
    private static final List<VideoExportProfile> profiles = new ArrayList<>();
    private static VideoExportProfile selectedProfile = null;
    private static boolean initialized = false;

    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        profiles.clear();
        profiles.addAll(VideoExportProfile.getBuiltInPresets());

        loadCustomProfiles();

        if (selectedProfile == null && !profiles.isEmpty())
        {
            selectedProfile = profiles.get(0);
        }

        initialized = true;
    }

    public static List<VideoExportProfile> getProfiles()
    {
        if (!initialized)
        {
            initialize();
        }

        return profiles;
    }

    public static VideoExportProfile getSelectedProfile()
    {
        if (!initialized)
        {
            initialize();
        }

        if (selectedProfile == null && !profiles.isEmpty())
        {
            selectedProfile = profiles.get(0);
        }

        return selectedProfile;
    }

    public static void setSelectedProfile(VideoExportProfile profile)
    {
        selectedProfile = profile;
    }

    public static void addProfile(VideoExportProfile profile)
    {
        if (profile == null)
        {
            return;
        }

        profiles.add(profile);
        saveCustomProfiles();
    }

    public static void removeProfile(VideoExportProfile profile)
    {
        if (profile == null)
        {
            return;
        }

        profiles.remove(profile);

        if (selectedProfile == profile)
        {
            selectedProfile = !profiles.isEmpty() ? profiles.get(0) : null;
        }

        saveCustomProfiles();
    }

    private static File getConfigFile()
    {
        return BBSMod.getSettingsPath("export_profiles.json");
    }

    public static void saveCustomProfiles()
    {
        try
        {
            File file = getConfigFile();
            ListType list = new ListType();

            for (VideoExportProfile profile : profiles)
            {
                list.add(profile.toData());
            }

            MapType root = new MapType();
            root.put("profiles", list);

            if (selectedProfile != null)
            {
                root.putString("selected", selectedProfile.getId());
            }

            DataToString.write(file, root, true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void loadCustomProfiles()
    {
        File file = getConfigFile();

        if (!file.exists())
        {
            return;
        }

        try
        {
            MapType root = DataToString.read(file).asMap();

            if (root.has("profiles"))
            {
                ListType list = root.getList("profiles");
                profiles.clear();

                for (int i = 0; i < list.size(); i++)
                {
                    MapType map = list.getMap(i);
                    VideoExportProfile profile = new VideoExportProfile("", "", "");
                    profile.fromData(map);
                    profiles.add(profile);
                }

                if (profiles.isEmpty())
                {
                    profiles.addAll(VideoExportProfile.getBuiltInPresets());
                }
            }

            if (root.has("selected"))
            {
                String selectedId = root.getString("selected");

                for (VideoExportProfile p : profiles)
                {
                    if (p.getId().equals(selectedId))
                    {
                        selectedProfile = p;
                        break;
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
