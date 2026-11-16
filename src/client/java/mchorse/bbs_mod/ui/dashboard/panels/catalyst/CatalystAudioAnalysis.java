package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the smart tools know about a sound: its loudness every ten milliseconds, worked out here from
 * the samples, with no outside help. A sound is read once and the reduced shape is kept, so the tools
 * can ask again for nothing.
 */
public final class CatalystAudioAnalysis
{
    /** The width of one step of the envelope */
    public static final double WINDOW_SECONDS = 0.01D;

    private static final Map<String, Envelope> CACHE = new ConcurrentHashMap<>();

    private CatalystAudioAnalysis()
    {}

    /** The loudest sample, 0 to 1, of each ten milliseconds of a sound */
    public static final class Envelope
    {
        public final float[] peak;

        public Envelope(float[] peak)
        {
            this.peak = peak;
        }

        public double seconds()
        {
            return this.peak.length * WINDOW_SECONDS;
        }

        private int index(double seconds)
        {
            return Math.max(0, Math.min(this.peak.length, (int) Math.floor(seconds / WINDOW_SECONDS)));
        }

        /** The loudest of a stretch of the sound, 0 to 1 */
        public float peakIn(double from, double to)
        {
            float max = 0F;

            for (int i = this.index(from); i < Math.max(this.index(from), this.index(to)) && i < this.peak.length; i++)
            {
                max = Math.max(max, this.peak[i]);
            }

            return max;
        }
    }

    public static double toDb(double amplitude)
    {
        return amplitude <= 1.0E-6D ? -120D : 20D * Math.log10(amplitude);
    }

    /** The envelope of the sound at a path. Reads the file, so this belongs off the render thread; null if it has no sound. */
    public static Envelope envelope(String path)
    {
        return envelope(path, null);
    }

    /** The same, telling {@code progress} (0 to 1) how far through the samples it is */
    public static Envelope envelope(String path, java.util.function.DoubleConsumer progress)
    {
        if (path == null || path.trim().isEmpty())
        {
            return null;
        }

        String key = path.trim();
        Envelope known = CACHE.get(key);

        if (known != null)
        {
            if (progress != null)
            {
                progress.accept(1D);
            }

            return known;
        }

        try
        {
            /* The file is read in one go, which says nothing about how far it is: the first 40 percent creeps up with the clock until it is done */
            boolean[] reading = {true};

            if (progress != null)
            {
                long began = System.currentTimeMillis();
                Thread ticker = new Thread(() ->
                {
                    try
                    {
                        while (reading[0])
                        {
                            progress.accept(0.4D * (1D - Math.exp(-(System.currentTimeMillis() - began) / 2500D)));
                            Thread.sleep(50L);
                        }
                    }
                    catch (Throwable ignored)
                    {}
                }, "CatalystAnalysisTicker");

                ticker.setDaemon(true);
                ticker.start();
            }

            Wave wave;

            try
            {
                File file = new File(key);

                wave = file.isFile() ? AudioReader.readWave(file) : null;

                if (wave == null)
                {
                    wave = AudioReader.read(BBSMod.getProvider(), Link.create(key));
                }
            }
            finally
            {
                reading[0] = false;
            }

            if (wave == null || wave.data == null || wave.numChannels <= 0 || wave.sampleRate <= 0)
            {
                return null;
            }

            wave = wave.convertTo16();

            if (wave.bitsPerSample != 16)
            {
                return null;
            }

            ShortBuffer samples = ByteBuffer.wrap(wave.data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            int channels = wave.numChannels;
            int frames = samples.limit() / channels;
            int hop = Math.max(1, (int) Math.round(wave.sampleRate * WINDOW_SECONDS));
            float[] peak = new float[(frames + hop - 1) / hop];

            for (int w = 0; w < peak.length; w++)
            {
                if (progress != null && (w & 1023) == 0)
                {
                    progress.accept(0.4D + 0.6D * (w / (double) peak.length));
                }

                int from = w * hop;
                int to = Math.min(frames, from + hop);
                int max = 0;

                for (int f = from; f < to; f++)
                {
                    for (int c = 0; c < channels; c++)
                    {
                        max = Math.max(max, Math.abs((int) samples.get(f * channels + c)));
                    }
                }

                peak[w] = max / 32768F;
            }

            Envelope envelope = new Envelope(peak);

            if (progress != null)
            {
                progress.accept(1D);
            }

            CACHE.put(key, envelope);

            return envelope;
        }
        catch (Throwable e)
        {
            return null;
        }
    }

    /** Forgets what is known, after a file was replaced */
    public static void clear()
    {
        CACHE.clear();
    }

    /**
     * The stretches of {@code from} to {@code to} seconds that stay under a level for at least a
     * length of time: {start, end} in seconds each, in order.
     */
    public static List<double[]> silences(Envelope envelope, double from, double to, double thresholdDb, double minSeconds)
    {
        List<double[]> out = new ArrayList<>();
        double level = Math.pow(10D, thresholdDb / 20D);
        int start = envelope.index(from);
        int end = envelope.index(to);
        int begin = -1;

        for (int i = start; i <= end; i++)
        {
            boolean quiet = i < end && envelope.peak[i] < level;

            if (quiet && begin < 0)
            {
                begin = i;
            }
            else if (!quiet && begin >= 0)
            {
                double length = (i - begin) * WINDOW_SECONDS;

                if (length >= minSeconds)
                {
                    out.add(new double[] {begin * WINDOW_SECONDS, i * WINDOW_SECONDS});
                }

                begin = -1;
            }
        }

        return out;
    }

    /**
     * The stretches where something is being said: above a level, with pauses shorter than
     * {@code mergeSeconds} bridged, and bursts shorter than a tenth of a second dropped.
     */
    public static List<double[]> activity(Envelope envelope, double from, double to, double thresholdDb, double mergeSeconds)
    {
        List<double[]> out = new ArrayList<>();
        double level = Math.pow(10D, thresholdDb / 20D);
        int start = envelope.index(from);
        int end = envelope.index(to);
        int begin = -1;
        int last = -1;

        for (int i = start; i < end; i++)
        {
            if (envelope.peak[i] >= level)
            {
                if (begin < 0)
                {
                    begin = i;
                }

                last = i;
            }
            else if (begin >= 0 && (i - last) * WINDOW_SECONDS > mergeSeconds)
            {
                add(out, begin, last + 1);
                begin = -1;
            }
        }

        if (begin >= 0)
        {
            add(out, begin, last + 1);
        }

        return out;
    }

    private static void add(List<double[]> out, int begin, int end)
    {
        if ((end - begin) * WINDOW_SECONDS >= 0.1D)
        {
            out.add(new double[] {begin * WINDOW_SECONDS, end * WINDOW_SECONDS});
        }
    }

    /**
     * The beats of a stretch, in seconds: where the loudness jumps up the most against the half
     * second around it. {@code sensitivity} is how many times the local average a rise has to be, and
     * two beats are never closer than a quarter of a second.
     */
    public static List<Double> beats(Envelope envelope, double from, double to, double sensitivity)
    {
        List<Double> out = new ArrayList<>();
        int start = Math.max(1, envelope.index(from));
        int end = envelope.index(to);
        int around = (int) Math.round(0.5D / WINDOW_SECONDS);
        int gap = (int) Math.round(0.25D / WINDOW_SECONDS);
        int lastBeat = -gap;

        /* A rise is the loudness over the one before it, smoothed across three steps so a single click is not a beat */
        float[] rise = new float[envelope.peak.length];

        for (int i = 1; i < rise.length; i++)
        {
            float now = (envelope.peak[i] + envelope.peak[Math.max(0, i - 1)] + envelope.peak[Math.max(0, i - 2)]) / 3F;
            float before = (envelope.peak[Math.max(0, i - 3)] + envelope.peak[Math.max(0, i - 4)] + envelope.peak[Math.max(0, i - 5)]) / 3F;

            rise[i] = Math.max(0F, now - before);
        }

        for (int i = start; i < end - 1; i++)
        {
            double sum = 0D;
            int count = 0;

            for (int k = Math.max(0, i - around); k < Math.min(rise.length, i + around); k++)
            {
                sum += rise[k];
                count += 1;
            }

            double mean = count == 0 ? 0D : sum / count;
            boolean peak = rise[i] >= rise[i - 1] && rise[i] > rise[i + 1];

            if (peak && rise[i] > 0.02F && rise[i] > mean * sensitivity && i - lastBeat >= gap)
            {
                out.add(i * WINDOW_SECONDS);
                lastBeat = i;
            }
        }

        return out;
    }
}
