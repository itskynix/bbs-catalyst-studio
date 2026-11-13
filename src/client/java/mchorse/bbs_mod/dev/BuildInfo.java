package mchorse.bbs_mod.dev;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * What the build put into the jar: {@code assets/bbs/build_info.json}, written by the
 * {@code generateBuildInfo} Gradle task. Run from an IDE, where no such file exists, every field
 * reads as a development build instead of being empty.
 */
public final class BuildInfo
{
    private static BuildInfo instance;

    public final String modVersion;
    public final String mcVersion;
    /** The channel the jar was built for, upper case: TESTER, PUBLIC, DEVELOPMENT... or DEV without a build file. */
    public final String stage;
    /** Date and commit count, e.g. {@code 20261113.41} */
    public final String buildNumber;
    public final String commit;

    private BuildInfo(String modVersion, String mcVersion, String stage, String buildNumber, String commit)
    {
        this.modVersion = modVersion;
        this.mcVersion = mcVersion;
        this.stage = stage;
        this.buildNumber = buildNumber;
        this.commit = commit;
    }

    public static BuildInfo get()
    {
        if (instance == null)
        {
            instance = load();
        }

        return instance;
    }

    private static BuildInfo load()
    {
        try (InputStream stream = BuildInfo.class.getResourceAsStream("/assets/bbs/build_info.json"))
        {
            if (stream != null)
            {
                JsonObject json = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

                if (json != null)
                {
                    return new BuildInfo(
                        string(json, "mod_version", "C1.4"),
                        string(json, "mc_version", "1.20.4"),
                        string(json, "stage", "DEV").toUpperCase(),
                        string(json, "build_number", "dev"),
                        string(json, "commit", "nogit")
                    );
                }
            }
        }
        catch (Throwable ignored)
        {}

        return new BuildInfo("C1.4", "1.20.4", "DEV", "dev", "nogit");
    }

    private static String string(JsonObject json, String key, String fallback)
    {
        return json.has(key) && !json.get(key).isJsonNull() && !json.get(key).getAsString().isBlank() ? json.get(key).getAsString() : fallback;
    }

    /** {@code C1.4 (Minecraft 1.20.4)} */
    public String versionLine(String minecraftWord)
    {
        return this.modVersion + " (" + minecraftWord + " " + this.mcVersion + ")";
    }

    /** {@code 20261113.41 (e0997d8)} */
    public String buildLine()
    {
        return this.buildNumber + " (" + this.commit + ")";
    }
}
