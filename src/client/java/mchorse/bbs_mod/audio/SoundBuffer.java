package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class SoundBuffer
{
    private final Link id;
    private int buffer;
    private float duration;
    private Waveform waveform;
    private Wave wave;

    public SoundBuffer(Link id, Wave wave, Waveform waveform)
    {
        this.id = id;

        if (wave != null && (wave.bitsPerSample != 16 || wave.numChannels != 2 || wave.sampleRate > 48000 || wave.audioFormat != 1))
        {
            wave = wave.normalize();
        }

        this.wave = wave;
        this.buffer = AL10.alGenBuffers();
        ByteBuffer buffer = MemoryUtil.memAlloc(wave.data.length);

        buffer.put(wave.data);
        buffer.flip();

        AL10.alBufferData(this.buffer, wave.getALFormat(), buffer, wave.sampleRate);

        int alErr = AL10.alGetError();
        if (alErr != AL10.AL_NO_ERROR)
        {
            BBSMod.LOGGER.error("OpenAL error 0x" + Integer.toHexString(alErr) + " buffering sound for " + id + " (size: " + wave.data.length + " bytes)");
        }

        MemoryUtil.memFree(buffer);

        this.duration = wave.getDuration();
        this.waveform = waveform;
    }

    public Link getId()
    {
        return this.id;
    }

    public int getBuffer()
    {
        return this.buffer;
    }

    public float getDuration()
    {
        return this.duration;
    }

    public Waveform getWaveform()
    {
        return this.waveform;
    }

    public Wave getWave()
    {
        return this.wave;
    }

    public void delete()
    {
        AL10.alDeleteBuffers(this.buffer);

        this.buffer = -1;
        this.wave = null;

        if (this.waveform != null)
        {
            this.waveform.delete();
        }
    }
}