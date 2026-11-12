package mchorse.bbs.compat.mtr;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuration settings for MTR (Minecraft Transit Railway) integration.
 */
public final class MtrConfig
{
    public static ValueBoolean mtrLocalSpaceRecord;
    public static ValueBoolean mtrPlaybackSync;

    private MtrConfig()
    {}

    public static void register(SettingsBuilder builder)
    {
        ValueGroup general = builder.category("general", Icons.GEAR).getCategory();

        if (!FabricLoader.getInstance().isModLoaded("mtr"))
        {
            general.invisible();
        }

        mtrLocalSpaceRecord = builder.getBoolean("mtrLocalSpaceRecord", true);
        mtrPlaybackSync = builder.getBoolean("mtrPlaybackSync", true);
    }

    public static boolean isLocalSpaceRecord()
    {
        return mtrLocalSpaceRecord == null || mtrLocalSpaceRecord.get();
    }

    public static boolean isPlaybackSync()
    {
        return mtrPlaybackSync == null || mtrPlaybackSync.get();
    }
}
