package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * Supported video compression codecs.
 */
public enum VideoCodec
{
    H264("h264", IKey.raw("H.264 / AVC (Broad Compatibility)"), "libx264"),
    H265("h265", IKey.raw("H.265 / HEVC (High Efficiency)"), "libx265"),
    AV1("av1", IKey.raw("AV1 (Next-Gen Open Codec)"), "libsvtav1"),
    PRORES("prores", IKey.raw("Apple ProRes (Master / Editing)"), "prores_ks"),
    VP9("vp9", IKey.raw("VP9 (WebM)"), "libvpx-vp9"),
    GIF("gif", IKey.raw("GIF (Palettegen Animation)"), "gif"),
    PNG("png", IKey.raw("PNG Sequence (Lossless Image Frames)"), "png");

    private final String id;
    private final IKey label;
    private final String softwareEncoder;

    VideoCodec(String id, IKey label, String softwareEncoder)
    {
        this.id = id;
        this.label = label;
        this.softwareEncoder = softwareEncoder;
    }

    public String getId()
    {
        return this.id;
    }

    public IKey getLabel()
    {
        return this.label;
    }

    public String getSoftwareEncoder()
    {
        return this.softwareEncoder;
    }

    public String getFfmpegEncoder(HardwareEncoder encoder)
    {
        return encoder.getFfmpegEncoder(this);
    }

    public boolean isHardwareAccelerated(HardwareEncoder encoder)
    {
        HardwareEncoder resolved = encoder.resolve();

        if (resolved == HardwareEncoder.CPU)
        {
            return false;
        }

        switch (this)
        {
            case H264:
            case H265:
                return resolved == HardwareEncoder.NVENC || resolved == HardwareEncoder.AMF || resolved == HardwareEncoder.QSV || resolved == HardwareEncoder.VIDEOTOOLBOX;
            case AV1:
                return resolved == HardwareEncoder.NVENC || resolved == HardwareEncoder.AMF || resolved == HardwareEncoder.QSV;
            case PRORES:
                return resolved == HardwareEncoder.VIDEOTOOLBOX;
            case VP9:
                return resolved == HardwareEncoder.QSV;
            default:
                return false;
        }
    }

    public boolean isImageSequence()
    {
        return this == PNG;
    }

    public boolean isGif()
    {
        return this == GIF;
    }

    public static VideoCodec fromId(String id)
    {
        if (id == null)
        {
            return H264;
        }

        for (VideoCodec codec : values())
        {
            if (codec.id.equalsIgnoreCase(id))
            {
                return codec;
            }
        }

        return H264;
    }
}
