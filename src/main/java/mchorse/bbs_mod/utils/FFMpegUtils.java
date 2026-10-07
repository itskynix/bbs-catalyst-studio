package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFMpegUtils
{
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+(\\.\\d+)*");

    private static final Set<String> SKIP_DIRS = Set.of("Windows", "Program Files", "Program Files (x86)", "$Recycle.Bin", "System Volume Information", "AppData");

    /**
     * People usually are not bright enough, even though everything is stated
     * in the tutorial, they still manage to specify either wrong path to ffmpeg, or
     * they specify the path to the folder...
     *
     * This little method should simplify their lives!
     */
    private static File findFFMPEG(String path)
    {
        if (path == null)
        {
            return new File("");
        }
        path = path.replace("\"", "").trim();
        File file = new File(path);
        boolean isWin = OS.CURRENT == OS.WINDOWS;

        if (file.isDirectory())
        {
            String subpath = isWin ? "ffmpeg.exe" : "ffmpeg";
            File bin = new File(file, subpath);

            if (bin.isFile())
            {
                return bin;
            }

            bin = new File(file, "bin" + File.separator + subpath);

            if (bin.isFile())
            {
                return bin;
            }
        }
        else if (isWin && !file.exists())
        {
            File exe = new File(path + ".exe");

            if (exe.exists())
            {
                return exe;
            }
        }

        return file;
    }

    private static String cachedResolvedFFmpeg = null;

    public static void resetCache()
    {
        cachedResolvedFFmpeg = null;
    }

    public static String getFFMPEG()
    {
        String raw = BBSSettings.videoEncoderPath != null ? BBSSettings.videoEncoderPath.get() : null;
        if (raw != null && !raw.trim().isEmpty() && !raw.trim().equalsIgnoreCase("ffmpeg"))
        {
            String clean = raw.replace("\"", "").trim();
            File encoderPath = findFFMPEG(clean);

            if (encoderPath.isFile())
            {
                return encoderPath.getAbsolutePath().replace("\"", "").trim();
            }
        }

        if (cachedResolvedFFmpeg != null && new File(cachedResolvedFFmpeg).isFile())
        {
            return cachedResolvedFFmpeg;
        }

        String detected = detectFFmpeg();
        if (detected != null && !detected.isEmpty())
        {
            cachedResolvedFFmpeg = detected.replace("\"", "").trim();
            if (BBSSettings.videoEncoderPath != null)
            {
                String cur = BBSSettings.videoEncoderPath.get();
                if (cur == null || cur.trim().isEmpty() || cur.trim().equalsIgnoreCase("ffmpeg"))
                {
                    BBSSettings.videoEncoderPath.set(cachedResolvedFFmpeg);
                }
            }
            BBSMod.LOGGER.info("Resolved FFmpeg binary at: " + cachedResolvedFFmpeg);
            return cachedResolvedFFmpeg;
        }

        String fallback = (raw != null && !raw.trim().isEmpty()) ? raw.replace("\"", "").trim() : "ffmpeg";
        return fallback;
    }

    private static String detectFFmpeg()
    {
        boolean isWin = OS.CURRENT == OS.WINDOWS;

        if (isWin)
        {
            String[] commonPaths = new String[] {
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\ffmpeg\\ffmpeg.exe",
                "D:\\ffmpeg\\bin\\ffmpeg.exe",
                "D:\\ffmpeg\\ffmpeg.exe",
                "E:\\ffmpeg\\bin\\ffmpeg.exe",
                "E:\\ffmpeg\\ffmpeg.exe"
            };

            for (String p : commonPaths)
            {
                File f = new File(p);
                if (f.isFile())
                {
                    return f.getAbsolutePath();
                }
            }

            String userHome = System.getProperty("user.home");
            if (userHome != null)
            {
                String[] homePaths = new String[] {
                    userHome + "\\ffmpeg\\bin\\ffmpeg.exe",
                    userHome + "\\ffmpeg\\ffmpeg.exe",
                    userHome + "\\scoop\\shims\\ffmpeg.exe",
                    userHome + "\\scoop\\apps\\ffmpeg\\current\\bin\\ffmpeg.exe"
                };

                for (String p : homePaths)
                {
                    File f = new File(p);
                    if (f.isFile())
                    {
                        return f.getAbsolutePath();
                    }
                }
            }

            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null)
            {
                File winget = new File(localAppData, "Microsoft\\WinGet\\Links\\ffmpeg.exe");
                if (winget.isFile())
                {
                    return winget.getAbsolutePath();
                }
            }

            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null)
            {
                File pf = new File(programFiles, "ffmpeg\\bin\\ffmpeg.exe");
                if (pf.isFile())
                {
                    return pf.getAbsolutePath();
                }
            }

            String programData = System.getenv("ProgramData");
            if (programData != null)
            {
                File choco = new File(programData, "chocolatey\\bin\\ffmpeg.exe");
                if (choco.isFile())
                {
                    return choco.getAbsolutePath();
                }
            }

            try
            {
                Process p = new ProcessBuilder("where.exe", "ffmpeg").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())))
                {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty())
                    {
                        File f = new File(line.replace("\"", "").trim());
                        if (f.isFile())
                        {
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
            catch (Exception ignored)
            {}
        }
        else
        {
            String[] unixPaths = new String[] {
                "/usr/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/opt/homebrew/bin/ffmpeg",
                "/usr/pkg/bin/ffmpeg"
            };

            for (String p : unixPaths)
            {
                File f = new File(p);
                if (f.isFile())
                {
                    return f.getAbsolutePath();
                }
            }

            try
            {
                Process p = new ProcessBuilder("which", "ffmpeg").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())))
                {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty())
                    {
                        File f = new File(line.replace("\"", "").trim());
                        if (f.isFile())
                        {
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
            catch (Exception ignored)
            {}
        }

        return null;
    }

    public static boolean checkFFMPEG()
    {
        return execute(BBSMod.getGameFolder(), "-version");
    }

    /**
     * The version the configured encoder reports of itself — "6.1", "7.0.2" — or nothing when
     * it cannot be run or doesn't answer like ffmpeg. Blocks on the process, so ask from a
     * thread of your own.
     *
     * <p>A "found" that shows the version is the only proof that means anything: a path that
     * exists can still be the wrong file, and that is found out on export otherwise.</p>
     */
    public static Optional<String> version()
    {
        String exe = getFFMPEG().replace("\"", "").trim();
        ProcessBuilder builder = new ProcessBuilder(exe, "-version");

        builder.redirectErrorStream(true);

        try
        {
            Process process = builder.start();
            String first;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                first = reader.readLine();

                /* Drain the rest so the process can exit instead of blocking on a full pipe */
                while (reader.readLine() != null);
            }

            process.waitFor();

            /* "ffmpeg version 6.1-full_build-www.gyan.dev Copyright ..." or
             * "ffmpeg version n4.4-154-g79c114e1b2-20210930 ...": the number is the part that
             * means anything to a person, the rest names the build */
            if (first != null && first.startsWith("ffmpeg version "))
            {
                String[] words = first.split(" ");
                String word = words.length > 2 ? words[2] : "?";
                Matcher number = VERSION_NUMBER.matcher(word);

                return Optional.of(number.find() ? number.group() : word);
            }
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("Failed to check FFmpeg version: " + e.getMessage());
        }

        return Optional.empty();
    }

    public static boolean execute(File folder, String... arguments)
    {
        List<String> args = new ArrayList<String>();

        args.add(getFFMPEG().replace("\"", "").trim());

        for (String arg : arguments)
        {
            if (arg != null && !arg.isEmpty())
            {
                args.add(arg.replace("\"", "").trim());
            }
        }

        ProcessBuilder builder = new ProcessBuilder(args);
        File log = BBSMod.getSettingsPath("converter.log");

        if (folder != null && folder.exists() && folder.isDirectory())
        {
            builder.directory(folder.getAbsoluteFile());
        }
        else
        {
            builder.directory(BBSMod.getGameFolder());
        }
        builder.redirectErrorStream(true);
        builder.redirectOutput(log);

        try
        {
            Process start = builder.start();

            return start.waitFor() == 0;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    public static Optional<Path> findFFMpeg(Path root)
    {
        Visitor visitor = new Visitor();

        try
        {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor);
        }
        catch (FFMpegFoundException found)
        {
            return Optional.of(found.foundPath);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static class Visitor extends SimpleFileVisitor<Path>
    {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        {
            Path name = dir.getFileName();

            if (name != null && SKIP_DIRS.contains(name.toString()))
            {
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        {
            Path fileName = file.getFileName();

            if (fileName != null && fileName.toString().equalsIgnoreCase("ffmpeg.exe"))
            {
                Path parent = file.getParent();

                if (parent != null)
                {
                    Path parentName = parent.getFileName();

                    if (parentName != null && parentName.toString().equalsIgnoreCase("bin"))
                    {
                        throw new FFMpegFoundException(file.toAbsolutePath());
                    }
                }
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc)
        {
            return FileVisitResult.CONTINUE;
        }
    }

    private static class FFMpegFoundException extends RuntimeException
    {
        final Path foundPath;

        public FFMpegFoundException(Path foundPath)
        {
            this.foundPath = foundPath;
        }
    }
}