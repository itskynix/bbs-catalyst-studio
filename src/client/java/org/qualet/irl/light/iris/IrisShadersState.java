package org.qualet.irl.light.iris;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Safe Iris state & soft-dependency bridge. All direct references to
 * {@code net.irisshaders.iris.*} live inside {@link IrisAccess}, which is only
 * loaded by the JVM when {@link #IRIS_LOADED} is {@code true}.
 */
public final class IrisShadersState
{
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static boolean broken;

    private IrisShadersState()
    {}

    public static boolean isIrisLoaded()
    {
        return IRIS_LOADED;
    }

    /**
     * True when Iris is not installed OR when Iris positively reports the
     * vanilla (no shaderpack) pipeline.
     */
    public static boolean shadersDisabled()
    {
        if (!IRIS_LOADED)
        {
            return true;
        }

        if (broken)
        {
            return false;
        }

        try
        {
            return IrisAccess.isVanillaPipeline();
        }
        catch (Throwable t)
        {
            broken = true;

            return false;
        }
    }

    public static Object currentPipelineOrNull()
    {
        if (!IRIS_LOADED)
        {
            return null;
        }

        try
        {
            return IrisAccess.currentPipelineOrNull();
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public static String currentPackName()
    {
        if (!IRIS_LOADED)
        {
            return "Iris not loaded";
        }

        try
        {
            return IrisAccess.currentPackName();
        }
        catch (Throwable t)
        {
            return "Iris not loaded";
        }
    }

    public static boolean isFallback()
    {
        if (!IRIS_LOADED)
        {
            return false;
        }

        try
        {
            return IrisAccess.isFallback();
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    public static Path shaderpacksDirectory()
    {
        if (!IRIS_LOADED)
        {
            return null;
        }

        return IrisAccess.shaderpacksDirectory();
    }

    public static List<String> enumerateShaderpacks() throws Exception
    {
        if (!IRIS_LOADED)
        {
            return List.of();
        }

        return IrisAccess.enumerateShaderpacks();
    }

    public static String formatProgramSourceHash(Object source)
    {
        if (!IRIS_LOADED || source == null)
        {
            return null;
        }

        return IrisAccess.formatProgramSourceHash(source);
    }

    /**
     * Isolated inner class so {@link IrisShadersState} can initialize when Iris
     * is absent from the classpath.
     */
    private static final class IrisAccess
    {
        static boolean isVanillaPipeline()
        {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

            return pipeline instanceof VanillaRenderingPipeline;
        }

        static Object currentPipelineOrNull()
        {
            return Iris.getPipelineManager().getPipelineNullable();
        }

        static String currentPackName()
        {
            return Iris.getCurrentPackName();
        }

        static boolean isFallback()
        {
            return Iris.isFallback();
        }

        static Path shaderpacksDirectory()
        {
            return Iris.getShaderpacksDirectory();
        }

        static List<String> enumerateShaderpacks() throws Exception
        {
            return List.copyOf(Iris.getShaderpacksDirectoryManager().enumerate());
        }

        static String formatProgramSourceHash(Object raw)
        {
            if (!(raw instanceof ProgramSource source))
            {
                return null;
            }

            try
            {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (String part : new String[] {
                    source.getName(),
                    source.getVertexSource().orElse(""),
                    source.getGeometrySource().orElse(""),
                    source.getTessControlSource().orElse(""),
                    source.getTessEvalSource().orElse(""),
                    source.getFragmentSource().orElse("")
                })
                {
                    digest.update(part.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
                return source.getName() + " sha256=" + HexFormat.of().formatHex(digest.digest());
            }
            catch (Exception e)
            {
                return "unavailable: " + e;
            }
        }
    }
}
