package mchorse.bbs_mod.audio;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.SOFTGainClampEx;
import org.lwjgl.openal.SOFTSourceSpatialize;
import org.slf4j.Logger;

public class SoundPlayer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static float gainLimit = -1F;

    private int source;
    private SoundBuffer buffer;
    private boolean unique;
    private float pendingOffset = -1F;

    /** Who this source belongs to, for the players handed out per owner - see the sound manager. */
    private Object owner;

    /** When the owner last asked for this source, for pruning the ones whose owner is gone. */
    private long lastUsed = System.currentTimeMillis();

    public SoundPlayer(SoundBuffer buffer)
    {
        this.buffer = buffer;
        this.source = AL10.alGenSources();

        if (this.source > 0)
        {
            AL10.alSourcei(this.source, AL10.AL_BUFFER, buffer != null ? buffer.getBuffer() : 0);
            AL10.alSourcef(this.source, AL10.AL_MAX_DISTANCE, 60);
            this.setRelative(false);
        }
        else
        {
            LOGGER.error("Failed to allocate OpenAL source (OpenAL error or source limit reached)");
        }
    }

    public SoundPlayer unique()
    {
        this.unique = true;

        return this;
    }

    public SoundPlayer owner(Object owner)
    {
        this.owner = owner;

        return this;
    }

    public Object getOwner()
    {
        return this.owner;
    }

    public void refresh()
    {
        this.lastUsed = System.currentTimeMillis();
    }

    public long getLastUsed()
    {
        return this.lastUsed;
    }

    public int getSource()
    {
        return this.source;
    }

    public SoundBuffer getBuffer()
    {
        return this.buffer;
    }

    public boolean isUnique()
    {
        return this.unique;
    }

    public boolean canBeRemoved()
    {
        return !this.unique && this.isStopped();
    }

    /* Properties */

    public static float getGainLimit()
    {
        if (gainLimit < 0F)
        {
            ALCapabilities capabilities = AL.getCapabilities();

            gainLimit = capabilities != null && capabilities.AL_SOFT_gain_clamp_ex
                ? Math.max(AL10.alGetFloat(SOFTGainClampEx.AL_GAIN_LIMIT_SOFT), 1F)
                : 1F;

            if (gainLimit <= 1F)
            {
                LOGGER.warn("AL_SOFT_gain_clamp_ex is missing, sounds can't be played louder than 100%");
            }
        }

        return gainLimit;
    }

    public void setVolume(float volume)
    {
        float gain = MathUtils.clamp(volume, 0F, getGainLimit());

        /* AL_MAX_GAIN has to be lifted first, or the gain below gets clamped back to it */
        if (gain > 1F)
        {
            AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, gain);
        }

        AL10.alSourcef(this.source, AL10.AL_GAIN, gain);
    }

    public void setPitch(float pitch)
    {
        AL10.alSourcef(this.source, AL10.AL_PITCH, pitch);
    }

    public void setRelative(boolean relative)
    {
        AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, relative ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, relative ? 0.0F : 1.0F);
    }

    /**
     * Configure this OpenAL source for pure 2D stereo editing/playback in Catalyst Studio:
     * - Relative to listener (AL_SOURCE_RELATIVE = AL_TRUE)
     * - At listener position (0, 0, 0) and zero velocity
     * - Distance attenuation disabled (AL_ROLLOFF_FACTOR = 0.0f)
     * - Preserves left/right channels without 3D spatial collapse
     */
    public SoundPlayer configure2DStereo()
    {
        AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(this.source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        AL10.alSource3f(this.source, AL10.AL_VELOCITY, 0.0F, 0.0F, 0.0F);
        AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 0.0F);

        ALCapabilities capabilities = AL.getCapabilities();
        if (capabilities != null && capabilities.AL_SOFT_source_spatialize)
        {
            /* AL_FALSE disables 3D HRTF pinna filtering and channel collapse, preserving pristine direct stereo */
            AL10.alSourcei(this.source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_FALSE);
        }

        return this;
    }

    /**
     * Pan stereo/mono audio in 2D space (-1.0 Left .. 0.0 Center .. +1.0 Right)
     * using circular constant-power panning vector on OpenAL listener plane.
     */
    public SoundPlayer setPan(float pan)
    {
        if (this.source > 0)
        {
            float clampedPan = Math.max(-1.0f, Math.min(1.0f, pan));

            if (Math.abs(clampedPan) < 0.001f)
            {
                /* Pure centered direct stereo without HRTF */
                this.configure2DStereo();
                return this;
            }

            float z = (float) -Math.sqrt(Math.max(0.0f, 1.0f - clampedPan * clampedPan));

            AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(this.source, AL10.AL_POSITION, clampedPan, 0.0f, z);
            AL10.alSource3f(this.source, AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);
            AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 0.0f);

            ALCapabilities capabilities = AL.getCapabilities();
            if (capabilities != null && capabilities.AL_SOFT_source_spatialize)
            {
                AL10.alSourcei(this.source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
            }
        }

        return this;
    }

    public void setLooping(boolean looping)
    {
        AL10.alSourcei(this.source, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
    }

    public void setPosition(Vector3f vector)
    {
        this.setPosition(vector.x, vector.y, vector.z);
    }

    public void setPosition(float x, float y, float z)
    {
        AL10.alSource3f(this.source, AL10.AL_POSITION, x, y, z);
    }

    public void setVelocity(Vector3f vector)
    {
        this.setVelocity(vector.x, vector.y, vector.z);
    }

    public void setVelocity(float x, float y, float z)
    {
        AL10.alSource3f(this.source, AL10.AL_VELOCITY, x, y, z);
    }

    /* Playback */

    public void play()
    {
        AL10.alSourcePlay(this.source);
        if (this.pendingOffset >= 0F)
        {
            float off = this.pendingOffset;
            this.pendingOffset = -1F;
            this.setPlaybackPosition(off);
        }
    }

    public void pause()
    {
        AL10.alSourcePause(this.source);
    }

    public void stop()
    {
        AL10.alSourceStop(this.source);
        this.pendingOffset = -1F;
    }

    public int getSourceState()
    {
        return AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE);
    }

    public boolean isPlaying()
    {
        return this.getSourceState() == AL10.AL_PLAYING;
    }

    public boolean isPaused()
    {
        return this.getSourceState() == AL10.AL_PAUSED;
    }

    public boolean isStopped()
    {
        if (this.source == -1)
        {
            return true;
        }

        int state = this.getSourceState();

        return state == AL10.AL_STOPPED || state == AL10.AL_INITIAL;
    }

    public float getPlaybackPosition()
    {
        return AL10.alGetSourcef(this.source, AL11.AL_SEC_OFFSET);
    }

    public void setPlaybackPosition(float seconds)
    {
        if (this.buffer != null)
        {
            seconds = MathUtils.clamp(seconds, 0, this.buffer.getDuration());
        }

        if (this.isPlaying())
        {
            if (this.source > 0)
            {
                AL10.alSourcef(this.source, AL11.AL_SEC_OFFSET, seconds);
            }
            this.pendingOffset = -1F;
        }
        else
        {
            this.pendingOffset = seconds;
            if (this.source > 0)
            {
                AL10.alSourcef(this.source, AL11.AL_SEC_OFFSET, seconds);
            }
        }
    }

    public void setBuffer(SoundBuffer buffer)
    {
        if (this.source > 0)
        {
            AL10.alSourceStop(this.source);
            AL10.alSourcei(this.source, AL10.AL_BUFFER, buffer != null ? buffer.getBuffer() : 0);
        }
        this.buffer = buffer;
    }

    public void delete()
    {
        if (this.source > 0)
        {
            try
            {
                AL10.alSourceStop(this.source);
                AL10.alSourcei(this.source, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(this.source);
            }
            catch (Exception ignored) {}
            this.source = -1;
        }

        this.buffer = null;
    }
}