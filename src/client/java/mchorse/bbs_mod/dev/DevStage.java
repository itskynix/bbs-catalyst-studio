package mchorse.bbs_mod.dev;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Where the mod stands in its life. The stage is resolved in this order: the local
 * {@code bbs/dev_state.json} (the developer's override, written by the developer screen), then the
 * {@code assets/bbs/build_info.json} the build put in the jar ({@code -Pstage=...}), then TESTER.
 */
public enum DevStage
{
    TESTER("Tester"),
    DEVELOPMENT("Development"),
    PRIVATE("Private"),
    UNDERRATING("Underrating"),
    PUBLIC("Public");

    public final String label;

    DevStage(String label)
    {
        this.label = label;
    }

    /** A stage by name, case-insensitive; the old name DEVELOPER still reads as DEVELOPMENT. Throws for anything else. */
    public static DevStage parse(String name)
    {
        String upper = name.trim().toUpperCase();

        return valueOf(upper.equals("DEVELOPER") ? "DEVELOPMENT" : upper);
    }

    /** The stage the jar was built as, or TESTER when it carries none that makes sense */
    public static DevStage fromBuildInfo()
    {
        try (InputStream stream = DevStage.class.getResourceAsStream("/assets/bbs/build_info.json"))
        {
            if (stream != null)
            {
                JsonObject json = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);

                if (json != null && json.has("stage"))
                {
                    return parse(json.get("stage").getAsString());
                }
            }
        }
        catch (Throwable ignored)
        {}

        return TESTER;
    }
}
