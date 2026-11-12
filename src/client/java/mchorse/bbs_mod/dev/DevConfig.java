package mchorse.bbs_mod.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The developer screen's choices, in {@code bbs/dev_state.json}. Local only; it is git-ignored. */
public final class DevConfig
{
    public static final int METRIC_FPS = 0;
    public static final int METRIC_TPS = 1;
    public static final int METRIC_PLAYHEAD = 2;
    public static final int METRIC_MEMORY = 3;
    public static final int METRICS = 4;

    /** 0 off, 1 text only, 2 graph only, 3 graph and text */
    public static final int OFF = 0;
    public static final int TEXT = 1;
    public static final int GRAPH = 2;
    public static final int BOTH = 3;

    private static DevTools.LogLevel logLevel = DevTools.LogLevel.INFO;

    private static final String[] IDS = {"fps", "tps", "playhead", "memory"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded;
    private static DevStage stage = DevStage.TESTER;
    /** Whether bbs/dev_state.json carries the stage (read from it, or chosen on the developer screen) */
    private static boolean stageOverridden;
    private static final int[] modes = new int[METRICS];

    private DevConfig()
    {}

    private static File file()
    {
        return BBSMod.getGamePath("bbs/dev_state.json");
    }

    private static void load()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        /* 1. the local file, below; 2. the jar's build_info.json; 3. TESTER */
        stage = DevStage.fromBuildInfo();

        try
        {
            File file = file();

            if (!file.isFile())
            {
                return;
            }

            JsonObject json = GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), JsonObject.class);

            if (json == null)
            {
                return;
            }

            if (json.has("stage"))
            {
                try
                {
                    stage = DevStage.parse(json.get("stage").getAsString());
                    stageOverridden = true;
                }
                catch (IllegalArgumentException ignored)
                {}
            }

            if (json.has("log"))
            {
                try
                {
                    logLevel = DevTools.LogLevel.valueOf(json.get("log").getAsString());
                }
                catch (IllegalArgumentException ignored)
                {}
            }

            for (int i = 0; i < METRICS; i++)
            {
                if (json.has(IDS[i]))
                {
                    modes[i] = Math.max(0, Math.min(3, json.get(IDS[i]).getAsInt()));
                }
            }
        }
        catch (Throwable e)
        {
            System.err.println("[BBS] Could not read dev_state.json: " + e.getMessage());
        }
    }

    private static void save()
    {
        try
        {
            JsonObject json = new JsonObject();

            if (stageOverridden)
            {
                json.addProperty("stage", stage.name());
            }

            json.addProperty("log", logLevel.name());

            for (int i = 0; i < METRICS; i++)
            {
                json.addProperty(IDS[i], modes[i]);
            }

            File file = file();

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
        }
        catch (Throwable e)
        {
            System.err.println("[BBS] Could not write dev_state.json: " + e.getMessage());
        }
    }

    public static DevStage getStage()
    {
        load();

        return stage;
    }

    public static void setStage(DevStage value)
    {
        load();
        stageOverridden = true;
        stage = value;
        save();
    }

    public static DevTools.LogLevel getLogLevel()
    {
        load();

        return logLevel;
    }

    public static void setLogLevel(DevTools.LogLevel value)
    {
        load();
        logLevel = value;
        save();
    }

    public static int getMode(int metric)
    {
        load();

        return modes[metric];
    }

    public static void setMode(int metric, int mode)
    {
        load();
        modes[metric] = Math.max(0, Math.min(3, mode));
        save();
    }
}
