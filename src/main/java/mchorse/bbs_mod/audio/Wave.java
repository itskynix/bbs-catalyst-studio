package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.audio.wav.WaveCue;
import mchorse.bbs_mod.audio.wav.WaveList;
import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Wave
{
    public int audioFormat;
    public int numChannels;
    public int sampleRate;
    public int byteRate;
    public int blockAlign;
    public int bitsPerSample;

    public byte[] data;

    public List<WaveList> lists = new ArrayList<>();
    public List<WaveCue> cues = new ArrayList<>();

    public Wave(int audioFormat, int numChannels, int sampleRate, int bitsPerSample, byte[] data)
    {
        int bytesPerSample = bitsPerSample / 8;
        int byteRate = sampleRate * numChannels * bytesPerSample;
        int blockAlign = numChannels * bytesPerSample;

        this.audioFormat = audioFormat;
        this.numChannels = numChannels;
        this.sampleRate = sampleRate;
        this.byteRate = byteRate;
        this.blockAlign = blockAlign;
        this.bitsPerSample = bitsPerSample;
        this.data = data;
    }

    public Wave(int audioFormat, int numChannels, int sampleRate, int byteRate, int blockAlign, int bitsPerSample, byte[] data)
    {
        this.audioFormat = audioFormat;
        this.numChannels = numChannels;
        this.sampleRate = sampleRate;
        this.byteRate = byteRate;
        this.blockAlign = blockAlign;
        this.bitsPerSample = bitsPerSample;
        this.data = data;
    }

    public int getBytesPerSample()
    {
        return this.bitsPerSample / 8;
    }

    public float getDuration()
    {
        return this.data.length / (float) this.numChannels / (float) this.getBytesPerSample() / (float) this.sampleRate;
    }

    public int getALFormat()
    {
        int bytes = this.getBytesPerSample();

        if (bytes == 1)
        {
            if (this.numChannels == 2)
            {
                return AL10.AL_FORMAT_STEREO8;
            }
            else if (this.numChannels == 1)
            {
                return AL10.AL_FORMAT_MONO8;
            }
        }
        else if (bytes == 2)
        {
            if (this.numChannels == 2)
            {
                return AL10.AL_FORMAT_STEREO16;
            }
            else if (this.numChannels == 1)
            {
                return AL10.AL_FORMAT_MONO16;
            }
        }

        throw new IllegalStateException("Current WAV file has unusual configuration... channels: " + this.numChannels + ", BPS: " + bytes);
    }

    public int getScanRegion(float pixelsPerSecond)
    {
        return (int) (this.sampleRate / pixelsPerSecond) * this.getBytesPerSample() * this.numChannels;
    }

    public Wave convertTo16()
    {
        if (this.bitsPerSample == 16 && this.audioFormat == 1)
        {
            return this;
        }

        int bytesPerSample = this.getBytesPerSample();
        if (bytesPerSample <= 0 || this.numChannels <= 0)
        {
            return this;
        }

        int totalSamples = this.data.length / bytesPerSample;
        byte[] convertedData = new byte[totalSamples * 2];
        boolean isFloat = (this.audioFormat == 3) || (this.bitsPerSample == 32 && this.audioFormat != 1);

        for (int i = 0; i < totalSamples; i++)
        {
            int idx = i * bytesPerSample;
            float f = 0.0f;

            if (isFloat)
            {
                if (this.bitsPerSample == 32 && idx + 4 <= this.data.length)
                {
                    int b0 = this.data[idx] & 0xFF;
                    int b1 = this.data[idx + 1] & 0xFF;
                    int b2 = this.data[idx + 2] & 0xFF;
                    int b3 = this.data[idx + 3] & 0xFF;
                    int bits = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
                    f = Float.intBitsToFloat(bits);
                }
                else if (this.bitsPerSample == 64 && idx + 8 <= this.data.length)
                {
                    long bits = 0;
                    for (int k = 0; k < 8; k++)
                    {
                        bits |= ((long) (this.data[idx + k] & 0xFF)) << (k * 8);
                    }
                    f = (float) Double.longBitsToDouble(bits);
                }
            }
            else // Integer PCM (Little Endian)
            {
                if (this.bitsPerSample == 8 && idx < this.data.length)
                {
                    int b0 = this.data[idx] & 0xFF;
                    f = (b0 - 128) / 128.0f;
                }
                else if (this.bitsPerSample == 16 && idx + 2 <= this.data.length)
                {
                    int b0 = this.data[idx] & 0xFF;
                    int b1 = this.data[idx + 1];
                    int val16 = (b1 << 8) | b0;
                    f = val16 / 32768.0f;
                }
                else if (this.bitsPerSample == 24 && idx + 3 <= this.data.length)
                {
                    int b0 = this.data[idx] & 0xFF;
                    int b1 = this.data[idx + 1] & 0xFF;
                    int b2 = this.data[idx + 2]; // signed byte
                    int val24 = (b2 << 16) | (b1 << 8) | b0;
                    f = val24 / 8388608.0f;
                }
                else if (this.bitsPerSample == 32 && idx + 4 <= this.data.length)
                {
                    int b0 = this.data[idx] & 0xFF;
                    int b1 = this.data[idx + 1] & 0xFF;
                    int b2 = this.data[idx + 2] & 0xFF;
                    int b3 = this.data[idx + 3]; // signed byte
                    int val32 = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
                    f = val32 / 2147483648.0f;
                }
            }

            if (Float.isNaN(f))
            {
                f = 0.0f;
            }
            else if (f > 1.0f)
            {
                f = 1.0f;
            }
            else if (f < -1.0f)
            {
                f = -1.0f;
            }

            short sample16 = (short) Math.round(f * 32767.0f);
            convertedData[i * 2] = (byte) (sample16 & 0xFF);
            convertedData[i * 2 + 1] = (byte) ((sample16 >> 8) & 0xFF);
        }

        int byteRate = this.sampleRate * this.numChannels * 2;
        int blockAlign = this.numChannels * 2;
        Wave wave = new Wave(1, this.numChannels, this.sampleRate, byteRate, blockAlign, 16, convertedData);
        wave.lists = this.lists;
        wave.cues = this.cues;

        return wave;
    }

    public Wave downmixToStereo()
    {
        if (this.numChannels <= 2)
        {
            return this;
        }

        Wave current = this;
        if (current.bitsPerSample != 16 || current.audioFormat != 1)
        {
            current = current.convertTo16();
        }

        int inChannels = current.numChannels;
        int inFrames = current.data.length / (inChannels * 2);
        byte[] outData = new byte[inFrames * 4];

        for (int f = 0; f < inFrames; f++)
        {
            float left = 0.0f;
            float right = 0.0f;

            if (inChannels >= 6)
            {
                short chL = current.getSample16(f, 0);
                short chR = current.getSample16(f, 1);
                short chC = current.getSample16(f, 2);
                short chLfe = current.getSample16(f, 3);
                short chLs = current.getSample16(f, 4);
                short chRs = current.getSample16(f, 5);

                left = chL + 0.707f * chC + 0.5f * chLfe + 0.707f * chLs;
                right = chR + 0.707f * chC + 0.5f * chLfe + 0.707f * chRs;
                left *= 0.5f;
                right *= 0.5f;
            }
            else
            {
                for (int c = 0; c < inChannels; c++)
                {
                    short val = current.getSample16(f, c);
                    if (c % 2 == 0)
                    {
                        left += val;
                    }
                    else
                    {
                        right += val;
                    }
                }
                float norm = (float) Math.sqrt((inChannels + 1) / 2.0);
                left /= norm;
                right /= norm;
            }

            int clL = Math.max(-32768, Math.min(32767, Math.round(left)));
            int clR = Math.max(-32768, Math.min(32767, Math.round(right)));

            int outIdx = f * 4;
            outData[outIdx] = (byte) (clL & 0xFF);
            outData[outIdx + 1] = (byte) ((clL >> 8) & 0xFF);
            outData[outIdx + 2] = (byte) (clR & 0xFF);
            outData[outIdx + 3] = (byte) ((clR >> 8) & 0xFF);
        }

        int byteRate = current.sampleRate * 2 * 2;
        Wave wave = new Wave(1, 2, current.sampleRate, byteRate, 4, 16, outData);
        wave.lists = current.lists;
        wave.cues = current.cues;
        return wave;
    }

    public Wave resample(int targetSampleRate)
    {
        if (this.sampleRate == targetSampleRate || targetSampleRate <= 0 || this.sampleRate <= 0)
        {
            return this;
        }

        Wave current = this;
        if (current.bitsPerSample != 16 || current.audioFormat != 1)
        {
            current = current.convertTo16();
        }

        int channels = current.numChannels;
        int inFrames = current.data.length / (channels * 2);
        if (inFrames <= 0)
        {
            return current;
        }

        int outFrames = (int) Math.round(inFrames * ((double) targetSampleRate / (double) current.sampleRate));
        byte[] outData = new byte[outFrames * channels * 2];

        double ratio = (double) current.sampleRate / (double) targetSampleRate;

        for (int outF = 0; outF < outFrames; outF++)
        {
            double inPos = outF * ratio;
            int i1 = (int) Math.floor(inPos);
            double t = inPos - i1;

            int i0 = Math.max(0, i1 - 1);
            int i2 = Math.min(inFrames - 1, i1 + 1);
            int i3 = Math.min(inFrames - 1, i1 + 2);
            i1 = Math.min(inFrames - 1, Math.max(0, i1));

            for (int c = 0; c < channels; c++)
            {
                double p0 = current.getSample16(i0, c);
                double p1 = current.getSample16(i1, c);
                double p2 = current.getSample16(i2, c);
                double p3 = current.getSample16(i3, c);

                /* Catmull-Rom cubic spline interpolation */
                double a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
                double b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3;
                double cCoeff = -0.5 * p0 + 0.5 * p2;
                double d = p1;

                double val = ((a * t + b) * t + cCoeff) * t + d;
                int clamped = Math.max(-32768, Math.min(32767, (int) Math.round(val)));

                int outIdx = (outF * channels + c) * 2;
                outData[outIdx] = (byte) (clamped & 0xFF);
                outData[outIdx + 1] = (byte) ((clamped >> 8) & 0xFF);
            }
        }

        int byteRate = targetSampleRate * channels * 2;
        int blockAlign = channels * 2;
        Wave wave = new Wave(1, channels, targetSampleRate, byteRate, blockAlign, 16, outData);
        wave.lists = current.lists;

        if (current.cues != null)
        {
            List<WaveCue> newCues = new ArrayList<>();
            for (WaveCue cue : current.cues)
            {
                WaveCue nc = new WaveCue();
                nc.id = cue.id;
                nc.position = (int) Math.round(cue.position * ((double) targetSampleRate / (double) current.sampleRate));
                nc.dataChunkID = cue.dataChunkID;
                nc.chunkStart = cue.chunkStart;
                nc.blockStart = cue.blockStart;
                nc.sampleStart = (int) Math.round(cue.sampleStart * ((double) targetSampleRate / (double) current.sampleRate));
                newCues.add(nc);
            }
            wave.cues = newCues;
        }

        return wave;
    }

    public Wave normalize()
    {
        Wave current = this;

        if (current.bitsPerSample != 16 || current.audioFormat != 1)
        {
            current = current.convertTo16();
        }

        if (current.numChannels > 2)
        {
            current = current.downmixToStereo();
        }

        if (current.sampleRate > 48000)
        {
            int targetRate = (current.sampleRate % 44100 == 0) ? 44100 : 48000;
            current = current.resample(targetRate);
        }

        return current;
    }

    public short getSample16(int frame, int channel)
    {
        int idx = (frame * this.numChannels + channel) * 2;
        if (idx < 0 || idx + 1 >= this.data.length)
        {
            return 0;
        }
        int b0 = this.data[idx] & 0xFF;
        int b1 = this.data[idx + 1];
        return (short) ((b1 << 8) | b0);
    }

    public float[] getCues()
    {
        float[] cues = new float[this.cues.size()];
        int i = 0;

        for (WaveCue cue : this.cues)
        {
            cues[i] = cue.position / (float) this.sampleRate;

            i += 1;
        }

        return cues;
    }

    /**
     * Improved sample rate conversion with linear interpolation
     * Reduces aliasing distortion and frequency loss during audio mixing
     * Fixed stereo to mono conversion and sample position calculation
     */
    public void add(ByteBuffer buffer, Wave wave, float offset, float shift, float duration)
    {
        this.add(buffer, wave, offset, shift, duration, 1F);
    }

    public void add(ByteBuffer buffer, Wave wave, float offset, float shift, float duration, float gain)
    {
        int waveStart = this.truncate((int) (shift * wave.byteRate));
        int start = this.truncate((int) (offset * this.byteRate));
        int end = this.truncate((int) ((offset + duration) * this.byteRate));

        end = this.truncate(Math.min(end, this.data.length));

        /* Calculate sample rate ratio for conversion */
        float ratio = (float) wave.sampleRate / (float) this.sampleRate;
        
        
        /* Use linear interpolation for better quality when sample rates differ */
        boolean useLinearInterpolation = Math.abs(ratio - 1.0f) > 0.01f;

        /* Calculate step size based on channel count and sample rate ratio */
        int targetStep = this.numChannels * this.getBytesPerSample();
        int sourceStep = wave.numChannels * wave.getBytesPerSample();

        for (int i = 0; start + i < end; i += targetStep)
        {
            /* Fixed sample position calculation to prevent duration doubling
             * Account for channel count difference in stereo to mono conversion */
            float sampleIndex = (i / targetStep) * ratio;
            float exactPos = waveStart + sampleIndex * sourceStep;
            int a = this.truncate((int) exactPos);
            int b = start + i;

            /* Ensure we don't go beyond the source audio data range */
            int requiredSourceBytes = useLinearInterpolation ? sourceStep * 2 : sourceStep;

            if (a + requiredSourceBytes - 1 >= wave.data.length)
            {
                break;
            }

            /* Ensure we don't go beyond the target audio data range */
            if (b + targetStep - 1 >= this.data.length)
            {
                break;
            }

            int waveShort;
            
            if (useLinearInterpolation)
            {
                /* Linear interpolation for better sample rate conversion */
                waveShort = this.getLinearInterpolatedSample(wave, exactPos);
            }
            else
            {
                /* Direct sample access when sample rates match
                 * Handle stereo to mono conversion by averaging channels */
                if (wave.numChannels == 2 && this.numChannels == 1)
                {
                    /* Stereo to mono conversion: average left and right channels */
                    buffer.position(0);
                    buffer.put(wave.data[a]);
                    buffer.put(wave.data[a + 1]);

                    short leftSample = buffer.getShort(0);
                    
                    buffer.position(0);
                    buffer.put(wave.data[a + 2]);
                    buffer.put(wave.data[a + 3]);

                    short rightSample = buffer.getShort(0);
                    
                    waveShort = (leftSample + rightSample) / 2;
                }
                else
                {
                    /* Direct sample access for same channel configuration */
                    buffer.position(0);
                    buffer.put(wave.data[a]);
                    buffer.put(wave.data[a + 1]);
                    waveShort = buffer.getShort(0);
                }
            }

            buffer.position(0);
            buffer.put(this.data[b]);
            buffer.put(this.data[b + 1]);

            int bytesShort = buffer.getShort(0);
            
            /* Improved audio mixing algorithm with smart volume normalization
             * Convert to float for precise calculations */
            float waveFloat = waveShort / (float) Short.MAX_VALUE * gain;
            float bytesFloat = bytesShort / (float) Short.MAX_VALUE;
            
            /* Calculate sum and check for clipping */
            float sum = waveFloat + bytesFloat;
            
            /* Apply smart normalization only when clipping would occur */
            float mixedFloat;

            if (sum > 1F || sum < -1F)
            {
                /* Dynamic normalization to preserve as much volume as possible */
                float absSum = Math.abs(sum);
                float normalizationFactor = 1F / absSum;

                /* Slight headroom */
                mixedFloat = sum * normalizationFactor * 0.95F;
            }
            else
            {
                /* No clipping, use direct sum to preserve volume */
                mixedFloat = sum;
            }
            
            /* Convert back to short */
            int finalShort = (int) (mixedFloat * Short.MAX_VALUE);

            buffer.putShort(0, (short) MathUtils.clamp(finalShort, Short.MIN_VALUE, Short.MAX_VALUE));

            this.data[b + 1] = buffer.get(1);
            this.data[b] = buffer.get(0);
        }
        
    }
    
    /**
     * Linear interpolation between samples for high-quality sample rate conversion
     * Reduces aliasing and preserves high-frequency content better than nearest neighbor
     * Fixed fraction calculation and stereo to mono handling
     */
    private int getLinearInterpolatedSample(Wave wave, float exactPos)
    {
        int bytesPerSample = wave.getBytesPerSample();
        int sourceStep = wave.numChannels * bytesPerSample;
        
        /* Calculate base index aligned to sample boundaries */
        int baseIndex = this.truncate((int) exactPos);
        /* Fixed fraction calculation to account for actual sample size */
        float fraction = (exactPos - baseIndex) / sourceStep;

        /* Ensure we have valid sample positions */
        int requiredBytes = sourceStep * 2; // Need two samples for interpolation

        if (baseIndex + requiredBytes - 1 >= wave.data.length || baseIndex < 0)
        {
            /* Return silence if out of bounds */
            return 0;
        }

        /* Get the two surrounding samples for interpolation */
        ByteBuffer buffer = MemoryUtil.memAlloc(4);
        
        /* First sample (handle stereo to mono conversion if needed) */
        buffer.position(0);

        short sample1;

        if (wave.numChannels == 2 && this.numChannels == 1)
        {
            /* Stereo to mono: average left and right channels */
            buffer.put(wave.data[baseIndex]);
            buffer.put(wave.data[baseIndex + 1]);

            short leftSample1 = buffer.getShort(0);
            
            buffer.position(0);
            buffer.put(wave.data[baseIndex + 2]);
            buffer.put(wave.data[baseIndex + 3]);

            short rightSample1 = buffer.getShort(0);
            
            sample1 = (short) ((leftSample1 + rightSample1) / 2);
        }
        else
        {
            /* Direct sample access */
            buffer.put(wave.data[baseIndex]);
            buffer.put(wave.data[baseIndex + 1]);

            sample1 = buffer.getShort(0);
        }
        
        /* Second sample (handle stereo to mono conversion if needed) */
        buffer.position(0);

        int nextSampleIndex = baseIndex + sourceStep;
        short sample2;

        if (wave.numChannels == 2 && this.numChannels == 1)
        {
            /* Stereo to mono: average left and right channels */
            buffer.put(wave.data[nextSampleIndex]);
            buffer.put(wave.data[nextSampleIndex + 1]);

            short leftSample2 = buffer.getShort(0);
            
            buffer.position(0);
            buffer.put(wave.data[nextSampleIndex + 2]);
            buffer.put(wave.data[nextSampleIndex + 3]);

            short rightSample2 = buffer.getShort(0);
            
            sample2 = (short) ((leftSample2 + rightSample2) / 2);
        }
        else
        {
            /* Direct sample access */
            buffer.put(wave.data[nextSampleIndex]);
            buffer.put(wave.data[nextSampleIndex + 1]);

            sample2 = buffer.getShort(0);
        }
        
        MemoryUtil.memFree(buffer);
        
        /* Linear interpolation: sample1 + (sample2 - sample1) * fraction */
        float interpolated = sample1 + (sample2 - sample1) * fraction;

        return (int) MathUtils.clamp(interpolated, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private int truncate(int offset)
    {
        return offset - offset % 2;
    }

    /**
     * Creates a mono excerpt (copy) of this Wave in the time range [fromSeconds, toSeconds).
     * - Clamps out-of-bounds times to [0, duration]
     * - If the clamped range is empty, returns an empty Wave (same format, empty data)
     * - Assumes PCM-like layout and mono; does not handle stereo/channel remapping
     */
    public Wave excerptMono(float fromSeconds, float toSeconds)
    {
        float duration = this.getDuration();
        float from = MathUtils.clamp(fromSeconds, 0F, duration);
        float to = MathUtils.clamp(toSeconds, 0F, duration);

        if (to < from)
        {
            float tmp = from;

            from = to;
            to = tmp;
        }

        int bps = this.getBytesPerSample();

        if (to <= from || this.data == null || this.data.length == 0 || bps <= 0)
        {
            Wave empty = new Wave(this.audioFormat, 1, this.sampleRate, this.byteRate, this.blockAlign, this.bitsPerSample, new byte[0]);

            empty.lists = this.lists;
            empty.cues = this.cues;

            return empty;
        }

        int fromSample = (int) Math.floor(from * this.sampleRate);
        int toSample = (int) Math.ceil(to * this.sampleRate);
        int startByte = fromSample * bps;
        int endByte = toSample * bps;

        startByte = MathUtils.clamp(startByte, 0, this.data.length);
        endByte = MathUtils.clamp(endByte, 0, this.data.length);

        startByte -= startByte % bps;
        endByte -= endByte % bps;

        if (endByte <= startByte)
        {
            Wave empty = new Wave(this.audioFormat, 1, this.sampleRate, this.byteRate, this.blockAlign, this.bitsPerSample, new byte[0]);

            empty.lists = this.lists;
            empty.cues = this.cues;

            return empty;
        }

        byte[] out = new byte[endByte - startByte];
        Wave copy = new Wave(this.audioFormat, 1, this.sampleRate, this.sampleRate * bps, bps, this.bitsPerSample, out);

        System.arraycopy(this.data, startByte, out, 0, out.length);

        copy.lists = this.lists;
        copy.cues = this.cues;

        return copy;
    }
}