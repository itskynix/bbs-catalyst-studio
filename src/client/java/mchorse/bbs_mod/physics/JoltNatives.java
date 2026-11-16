package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.BBSMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Puts the Jolt native library where the JVM can load it, and loads it.
 *
 * <p>The library rides inside the mod jar, one copy per platform, and a nested jar is not a file the
 * operating system can open, so the right one is written to {@code <game>/bbs_cs/natives} first, under
 * a name that carries a hash of its content. The write happens once: the same build finds its file there
 * on every later launch, and a new build of Jolt gets a new name instead of overwriting a library another
 * copy of the game may have open.</p>
 */
final class JoltNatives
{
    private JoltNatives()
    {}

    /**
     * @throws IOException                   when the library cannot be read out of the jar or written to disk
     * @throws UnsupportedOperationException when there is no library for this system or processor
     */
    static void load() throws IOException
    {
        String directory = directory();
        String name = name();
        byte[] library = read(directory + "/com/github/stephengold/" + name);
        Path file = extract(library, hash(library) + "-" + name);

        System.load(file.toAbsolutePath().toString());

        BBSMod.LOGGER.info("Loaded the Jolt native library for {} from {}.", directory, file);
    }

    /** Where jolt-jni keeps a platform's library: system, then processor. Unknown ones are refused, not guessed at */
    private static String directory()
    {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String system;

        if (os.contains("win"))
        {
            system = "windows";
        }
        else if (os.contains("mac") || os.contains("darwin"))
        {
            system = "osx";
        }
        else if (os.contains("nux") || os.contains("nix"))
        {
            system = "linux";
        }
        else
        {
            throw new UnsupportedOperationException("There is no Jolt library for the \"" + os + "\" system.");
        }

        String cpu = switch (architecture)
        {
            case "amd64", "x86_64", "x86-64" -> "x86-64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new UnsupportedOperationException("There is no Jolt library for the \"" + architecture + "\" processor.");
        };

        return system + "/" + cpu;
    }

    private static String name()
    {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("win"))
        {
            return "joltjni.dll";
        }

        return os.contains("mac") || os.contains("darwin") ? "libjoltjni.dylib" : "libjoltjni.so";
    }

    private static byte[] read(String resource) throws IOException
    {
        /* The mod's own class loader: under Fabric the library lives in a jar inside the mod jar, which only it can see */
        try (InputStream stream = JoltNatives.class.getClassLoader().getResourceAsStream(resource))
        {
            if (stream == null)
            {
                throw new IOException("The Jolt library \"" + resource + "\" is not in the mod.");
            }

            return stream.readAllBytes();
        }
    }

    private static Path extract(byte[] library, String name) throws IOException
    {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("bbs_cs").resolve("natives");
        Path file = directory.resolve(name);

        if (Files.isRegularFile(file) && Files.size(file) == library.length)
        {
            return file;
        }

        Files.createDirectories(directory);

        /* Written beside the target and moved onto it, so a half-written library is never there to be loaded */
        Path partial = Files.createTempFile(directory, name, ".part");

        try
        {
            Files.write(partial, library);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            /* Another copy of the game has it open and Windows will not replace a loaded library; the hash is in
             * the name, so a file of this size under this name is this library */
            if (!Files.isRegularFile(file) || Files.size(file) != library.length)
            {
                throw e;
            }
        }
        finally
        {
            Files.deleteIfExists(partial);
        }

        return file;
    }

    private static String hash(byte[] library)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(library);
            byte[] head = new byte[8];

            System.arraycopy(digest, 0, head, 0, head.length);

            return HexFormat.of().formatHex(head);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is missing from this Java runtime.", e);
        }
    }
}
