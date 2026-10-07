package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.mp3.Mp3Reader;
import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AudioReader
{
    public static final int MAX_AUDIO_BYTES = 256 * 1024 * 1024;

    public static boolean isVideo(String pathLower)
    {
        return pathLower.endsWith(".mp4") || pathLower.endsWith(".mov") || pathLower.endsWith(".mkv")
            || pathLower.endsWith(".webm") || pathLower.endsWith(".avi") || pathLower.endsWith(".m4v");
    }

    public static Wave readWave(File file) throws Exception
    {
        if (file == null || !file.exists() || !file.isFile())
        {
            return null;
        }

        String pathLower = file.getName().toLowerCase(Locale.ROOT);

        if (isVideo(pathLower))
        {
            Wave videoWave = readVideoAudio(file);

            return videoWave != null ? videoWave.normalize() : null;
        }

        if (!pathLower.endsWith(".wav") && !pathLower.endsWith(".ogg") && !pathLower.endsWith(".mp3"))
        {
            return null;
        }

        Wave wave = null;

        try (InputStream stream = new FileInputStream(file))
        {
            if (pathLower.endsWith(".wav"))
            {
                wave = new WaveReader().read(stream);
            }
            else if (pathLower.endsWith(".ogg"))
            {
                wave = VorbisReader.read(Link.assets(file.getName()), stream);
            }
            else if (pathLower.endsWith(".mp3"))
            {
                try
                {
                    wave = Mp3Reader.read(Link.assets(file.getName()), stream);
                }
                catch (Exception e)
                {
                    /* Fallback to ffmpeg for mp3 if available */
                    wave = readVideoAudio(file);

                    if (wave == null)
                    {
                        throw e;
                    }
                }
            }
        }

        if (wave != null)
        {
            return wave.normalize();
        }

        return null;
    }

    public static Wave read(AssetProvider provider, Link link) throws Exception
    {
        File directFile = provider != null ? provider.getFile(link) : null;
        if (directFile != null && directFile.exists() && directFile.isFile())
        {
            return readWave(directFile);
        }

        String pathLower = link.path.toLowerCase(Locale.ROOT);

        if (isVideo(pathLower))
        {
            Wave videoWave = readVideoAudio(provider, link);

            return videoWave != null ? videoWave.normalize() : null;
        }

        if (!pathLower.endsWith(".wav") && !pathLower.endsWith(".ogg") && !pathLower.endsWith(".mp3"))
        {
            return null;
        }

        /* System.out.println("Reading: " + link); */

        Wave wave = null;

        try (InputStream asset = provider.getAsset(link))
        {
            if (pathLower.endsWith(".wav"))
            {
                wave = new WaveReader().read(asset);
            }
            else if (pathLower.endsWith(".ogg"))
            {
                wave = VorbisReader.read(link, asset);
            }
            else if (pathLower.endsWith(".mp3"))
            {
                try
                {
                    wave = Mp3Reader.read(link, asset);
                }
                catch (Exception e)
                {
                    /* Fallback to ffmpeg for mp3 if available */
                    wave = readVideoAudio(provider, link);

                    if (wave == null)
                    {
                        throw e;
                    }
                }
            }
        }

        if (wave != null)
        {
            return wave.normalize();
        }

        throw new IllegalStateException("Given link " + link + " isn't a supported audio file (WAV, OGG, MP3)!");
    }

    /**
     * Extract a video file's audio track as 16-bit stereo PCM by piping it through ffmpeg.
     * Returns null when the file is unreachable, ffmpeg is not set up, or the video has no audio.
     */
    private static Wave readVideoAudio(AssetProvider provider, Link link) throws Exception
    {
        File file = provider != null ? provider.getFile(link) : null;

        return readVideoAudio(file);
    }

    public static int getTargetAudioRate()
    {
        try
        {
            long context = org.lwjgl.openal.ALC10.alcGetCurrentContext();
            if (context != 0)
            {
                long device = org.lwjgl.openal.ALC10.alcGetContextsDevice(context);
                if (device != 0)
                {
                    int freq = org.lwjgl.openal.ALC10.alcGetInteger(device, org.lwjgl.openal.ALC10.ALC_FREQUENCY);
                    if (freq >= 22050 && freq <= 192000)
                    {
                        return freq;
                    }
                }
            }
        }
        catch (Throwable ignored)
        {}

        return 48000;
    }

    private static class ExtractionResult
    {
        final byte[] data;
        final boolean noAudioStream;

        ExtractionResult(byte[] data, boolean noAudioStream)
        {
            this.data = data;
            this.noAudioStream = noAudioStream;
        }
    }

    private static ExtractionResult executeFFmpegAudioExtraction(File file, int sampleRate, boolean useSoxr)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add(FFMpegUtils.getFFMPEG());
        cmd.add("-nostdin");
        cmd.add("-v");
        cmd.add("error");
        cmd.add("-i");
        cmd.add(file.getAbsolutePath());
        cmd.add("-vn");
        cmd.add("-sn");
        cmd.add("-dn");
        cmd.add("-ac");
        cmd.add("2");
        cmd.add("-ar");
        cmd.add(String.valueOf(sampleRate));

        if (useSoxr)
        {
            cmd.add("-af");
            cmd.add("aresample=" + sampleRate + ":resampler=soxr:precision=28:cutoff=0.99");
        }
        else
        {
            cmd.add("-af");
            cmd.add("aresample=" + sampleRate + ":dither_method=triangular");
        }

        cmd.add("-acodec");
        cmd.add("pcm_s16le");
        cmd.add("-f");
        cmd.add("s16le");
        cmd.add("pipe:1");

        try
        {
            ProcessBuilder builder = new ProcessBuilder(cmd);

            Process process = builder.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteArrayOutputStream errBaos = new ByteArrayOutputStream();

            Thread errThread = new Thread(() ->
            {
                try (InputStream errStream = process.getErrorStream())
                {
                    if (errStream != null)
                    {
                        byte[] errBuf = new byte[4096];
                        int r;
                        while ((r = errStream.read(errBuf)) != -1)
                        {
                            errBaos.write(errBuf, 0, r);
                        }
                    }
                }
                catch (Exception ignored)
                {}
            }, "FFmpeg-Audio-Stderr");
            errThread.setDaemon(true);
            errThread.start();

            byte[] buffer = new byte[16384];
            int read;

            try (InputStream stream = process.getInputStream())
            {
                while ((read = stream.read(buffer)) != -1)
                {
                    baos.write(buffer, 0, read);
                    if (baos.size() > MAX_AUDIO_BYTES)
                    {
                        process.destroyForcibly();
                        throw new IllegalStateException("Audio stream exceeded maximum allowed size of " + MAX_AUDIO_BYTES + " bytes!");
                    }
                }
            }
            finally
            {
                boolean finished = false;
                try
                {
                    finished = process.waitFor(60L, TimeUnit.SECONDS);
                }
                catch (InterruptedException ignored)
                {
                    Thread.currentThread().interrupt();
                }

                if (!finished)
                {
                    process.destroyForcibly();
                }

                try
                {
                    errThread.join(500);
                }
                catch (InterruptedException ignored)
                {}
            }

            if (process.exitValue() == 0 && baos.size() > 0)
            {
                return new ExtractionResult(baos.toByteArray(), false);
            }

            String err = errBaos.toString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            boolean noAudio = err.contains("does not contain any stream") || err.contains("matches no streams") || err.contains("no streams");
            return new ExtractionResult(null, noAudio);
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.error("Failed to extract audio from video: " + file.getAbsolutePath(), e);
        }

        return new ExtractionResult(null, false);
    }

    public static Wave readVideoAudio(File file) throws Exception
    {
        if (file == null || !file.isFile())
        {
            return null;
        }

        int targetRate = getTargetAudioRate();

        /* Try with high-fidelity soxr resampler first */
        ExtractionResult result = executeFFmpegAudioExtraction(file, targetRate, true);

        if ((result.data == null || result.data.length == 0) && !result.noAudioStream)
        {
            /* Fallback to standard swresample if soxr is not compiled into ffmpeg build */
            result = executeFFmpegAudioExtraction(file, targetRate, false);
        }

        if (result.data == null || result.data.length == 0)
        {
            return null;
        }

        return new Wave(1, 2, targetRate, 16, result.data);
    }
}
