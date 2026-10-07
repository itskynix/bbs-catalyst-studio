package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataParser;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class WelcomeManager
{
    private WelcomeManager()
    {}

    public static File getBbsConfigFile()
    {
        File runDir;
        try
        {
            runDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile();
        }
        catch (Throwable t)
        {
            runDir = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().runDirectory : new File(".");
        }
        File bbsDir = new File(runDir != null ? runDir : new File("."), "bbs");
        if (!bbsDir.exists())
        {
            bbsDir.mkdirs();
        }
        return new File(bbsDir, "config.json");
    }

    public static boolean hasSeenWelcomeScreen()
    {
        // 1. Check BBSSettings (welcome_seen)
        if (BBSSettings.onboardingWelcomeSeen != null && BBSSettings.onboardingWelcomeSeen.get())
        {
            return true;
        }

        // 2. Check .minecraft/bbs/config.json
        File file = getBbsConfigFile();
        if (file.exists())
        {
            try
            {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                BaseType data = DataParser.parse(content);
                if (data instanceof MapType map)
                {
                    if (map.has("hasSeenWelcomeScreen"))
                    {
                        return map.getByte("hasSeenWelcomeScreen") != 0;
                    }
                }
            }
            catch (Exception ignored)
            {}
        }

        return false;
    }

    public static void setSeenWelcomeScreen(boolean seen)
    {
        // 1. Update BBSSettings
        if (BBSSettings.onboardingWelcomeSeen != null)
        {
            BBSSettings.onboardingWelcomeSeen.set(seen);
        }
        if (BBSMod.getSettings() != null)
        {
            mchorse.bbs_mod.settings.Settings bbs = BBSMod.getSettings().modules.get("bbs");
            if (bbs != null)
            {
                bbs.save();
            }
        }

        // 2. Update .minecraft/bbs/config.json
        File file = getBbsConfigFile();
        try
        {
            MapType map = new MapType();
            if (file.exists())
            {
                try
                {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    BaseType data = DataParser.parse(content);
                    if (data instanceof MapType m)
                    {
                        map = m;
                    }
                }
                catch (Exception ignored)
                {}
            }

            map.putByte("hasSeenWelcomeScreen", (byte) (seen ? 1 : 0));
            Files.writeString(file.toPath(), DataToString.toString(map, true), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            System.err.println("[BBS] Failed to write config.json: " + e.getMessage());
        }
    }
}
