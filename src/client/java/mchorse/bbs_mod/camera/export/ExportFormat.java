package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Output container formats supported by the export pipeline.
 */
public enum ExportFormat
{
    MP4("mp4", ".mp4", IKey.raw("MP4 (MPEG-4 Part 14)"), true,
        Arrays.asList(VideoCodec.H264, VideoCodec.H265, VideoCodec.AV1), VideoCodec.H264),

    MOV("mov", ".mov", IKey.raw("QuickTime Movie (.mov)"), true,
        Arrays.asList(VideoCodec.PRORES, VideoCodec.H264, VideoCodec.H265), VideoCodec.PRORES),

    MKV("mkv", ".mkv", IKey.raw("Matroska Video (.mkv)"), true,
        Arrays.asList(VideoCodec.H264, VideoCodec.H265, VideoCodec.AV1, VideoCodec.PRORES, VideoCodec.VP9), VideoCodec.H264),

    GIF("gif", ".gif", IKey.raw("Animated GIF (.gif)"), false,
        Collections.singletonList(VideoCodec.GIF), VideoCodec.GIF),

    PNG_SEQUENCE("png", ".png", IKey.raw("PNG Image Sequence (.png)"), false,
        Collections.singletonList(VideoCodec.PNG), VideoCodec.PNG),

    WEBM("webm", ".webm", IKey.raw("WebM (.webm)"), true,
        Arrays.asList(VideoCodec.VP9, VideoCodec.AV1), VideoCodec.VP9);

    private final String id;
    private final String extension;
    private final IKey label;
    private final boolean supportsAudio;
    private final List<VideoCodec> supportedCodecs;
    private final VideoCodec defaultCodec;

    ExportFormat(String id, String extension, IKey label, boolean supportsAudio, List<VideoCodec> supportedCodecs, VideoCodec defaultCodec)
    {
        this.id = id;
        this.extension = extension;
        this.label = label;
        this.supportsAudio = supportsAudio;
        this.supportedCodecs = supportedCodecs;
        this.defaultCodec = defaultCodec;
    }

    public String getId()
    {
        return this.id;
    }

    public String getExtension()
    {
        return this.extension;
    }

    public IKey getLabel()
    {
        return this.label;
    }

    public boolean isAudioSupported()
    {
        return this.supportsAudio;
    }

    public List<VideoCodec> getSupportedCodecs()
    {
        return this.supportedCodecs;
    }

    public VideoCodec getDefaultCodec()
    {
        return this.defaultCodec;
    }

    public boolean supportsCodec(VideoCodec codec)
    {
        return this.supportedCodecs.contains(codec);
    }

    public boolean isImageSequence()
    {
        return this == PNG_SEQUENCE;
    }

    public boolean isGif()
    {
        return this == GIF;
    }

    /**
     * Formats the output filename or file pattern given a base name (e.g., "my_video").
     */
    public String getOutputPattern(String baseName)
    {
        if (this == PNG_SEQUENCE)
        {
            return baseName + "_%05d.png";
        }

        return baseName + this.extension;
    }

    public static ExportFormat fromId(String id)
    {
        if (id == null)
        {
            return MP4;
        }

        for (ExportFormat format : values())
        {
            if (format.id.equalsIgnoreCase(id))
            {
                return format;
            }
        }

        return MP4;
    }
}
