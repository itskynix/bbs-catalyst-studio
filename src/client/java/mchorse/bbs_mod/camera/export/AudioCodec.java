package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * Audio codecs supported during video rendering.
 */
public enum AudioCodec
{
    AAC("aac", IKey.raw("AAC (Standard)")),
    PCM_16("pcm_s16le", IKey.raw("Uncompressed PCM 16-bit (Lossless)")),
    MP3("libmp3lame", IKey.raw("MP3")),
    OPUS("libopus", IKey.raw("Opus")),
    COPY("copy", IKey.raw("Direct Stream Copy")),
    NONE("none", IKey.raw("No Audio"));

    private final String ffmpegCodec;
    private final IKey label;

    AudioCodec(String ffmpegCodec, IKey label)
    {
        this.ffmpegCodec = ffmpegCodec;
        this.label = label;
    }

    public String getFfmpegCodec()
    {
        return this.ffmpegCodec;
    }

    /** The label in the current language; the English one written above is the fallback. */
    public IKey getLabel()
    {
        return L10n.lang("bbs.ui.deliver.audio_codec." + this.name().toLowerCase(), this.label.get());
    }

    public boolean isNone()
    {
        return this == NONE;
    }

    public static AudioCodec fromId(String id)
    {
        if (id == null)
        {
            return AAC;
        }

        for (AudioCodec codec : values())
        {
            if (codec.name().equalsIgnoreCase(id) || codec.ffmpegCodec.equalsIgnoreCase(id))
            {
                return codec;
            }
        }

        return AAC;
    }
}
