package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.OS;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/**
 * Hardware accelerated and software video encoders.
 * Detects host GPU vendor via OpenGL renderer strings and maps
 * codecs to optimal vendor-specific FFmpeg encoders (NVENC, AMF, QSV, VideoToolbox, CPU).
 */
public enum HardwareEncoder
{
    AUTO("auto", IKey.raw("Auto (Detect GPU)")),
    CPU("cpu", IKey.raw("Software (CPU)")),
    NVENC("nvenc", IKey.raw("NVIDIA NVENC")),
    AMF("amf", IKey.raw("AMD AMF")),
    QSV("qsv", IKey.raw("Intel QuickSync (QSV)")),
    VIDEOTOOLBOX("videotoolbox", IKey.raw("Apple VideoToolbox"));

    private final String id;
    private final IKey label;

    private static HardwareEncoder detectedGpu = null;
    private static String detectedGpuName = null;

    HardwareEncoder(String id, IKey label)
    {
        this.id = id;
        this.label = label;
    }

    public String getId()
    {
        return this.id;
    }

    public IKey getLabel()
    {
        return this.label;
    }

    /**
     * Resolves AUTO to the detected hardware encoder on this system, or CPU fallback.
     */
    public HardwareEncoder resolve()
    {
        if (this == AUTO)
        {
            return detect();
        }

        return this;
    }

    /**
     * Detects host GPU from the current OpenGL context and system environment.
     */
    public static HardwareEncoder detect()
    {
        if (detectedGpu != null)
        {
            return detectedGpu;
        }

        String renderer = "";
        String vendor = "";

        try
        {
            renderer = GL11.glGetString(GL11.GL_RENDERER);
            vendor = GL11.glGetString(GL11.GL_VENDOR);
        }
        catch (Throwable ignored)
        {
            // OpenGL context may not be current on this thread
        }

        if (renderer == null) renderer = "";
        if (vendor == null) vendor = "";

        detectedGpuName = (vendor + " " + renderer).trim();
        String combined = detectedGpuName.toLowerCase(Locale.ROOT);

        if (combined.contains("nvidia") || combined.contains("geforce") || combined.contains("quadro") || combined.contains("rtx") || combined.contains("gtx"))
        {
            detectedGpu = NVENC;
        }
        else if (combined.contains("amd") || combined.contains("radeon") || combined.contains("advanced micro devices") || combined.contains("ati"))
        {
            detectedGpu = AMF;
        }
        else if (combined.contains("intel") || combined.contains("iris") || combined.contains("hd graphics") || combined.contains("arc"))
        {
            detectedGpu = QSV;
        }
        else if (OS.CURRENT == OS.MACOS || combined.contains("apple"))
        {
            detectedGpu = VIDEOTOOLBOX;
        }
        else
        {
            detectedGpu = CPU;
        }

        return detectedGpu;
    }

    /**
     * Returns a human-readable name of the detected GPU.
     */
    public static String getDetectedGpuName()
    {
        if (detectedGpu == null)
        {
            detect();
        }

        return (detectedGpuName != null && !detectedGpuName.isEmpty()) ? detectedGpuName : "Unknown GPU";
    }

    /**
     * Check whether this hardware encoder is supported or available on current OS.
     */
    public boolean isSupported()
    {
        if (this == AUTO || this == CPU)
        {
            return true;
        }

        if (this == VIDEOTOOLBOX)
        {
            return OS.CURRENT == OS.MACOS;
        }

        if (this == NVENC || this == AMF || this == QSV)
        {
            return OS.CURRENT == OS.WINDOWS || OS.CURRENT == OS.LINUX;
        }

        return false;
    }

    /**
     * Maps the given video codec to the appropriate FFmpeg encoder name for this hardware encoder.
     */
    public String getFfmpegEncoder(VideoCodec codec)
    {
        HardwareEncoder resolved = this.resolve();

        switch (codec)
        {
            case H264:
                switch (resolved)
                {
                    case NVENC: return "h264_nvenc";
                    case AMF: return "h264_amf";
                    case QSV: return "h264_qsv";
                    case VIDEOTOOLBOX: return "h264_videotoolbox";
                    default: return "libx264";
                }

            case H265:
                switch (resolved)
                {
                    case NVENC: return "hevc_nvenc";
                    case AMF: return "hevc_amf";
                    case QSV: return "hevc_qsv";
                    case VIDEOTOOLBOX: return "hevc_videotoolbox";
                    default: return "libx265";
                }

            case AV1:
                switch (resolved)
                {
                    case NVENC: return "av1_nvenc";
                    case AMF: return "av1_amf";
                    case QSV: return "av1_qsv";
                    default: return "libsvtav1";
                }

            case PRORES:
                switch (resolved)
                {
                    case VIDEOTOOLBOX: return "prores_videotoolbox";
                    default: return "prores_ks";
                }

            case VP9:
                switch (resolved)
                {
                    case QSV: return "vp9_qsv";
                    default: return "libvpx-vp9";
                }

            case PNG:
                return "png";

            case GIF:
                return "gif";

            default:
                return "libx264";
        }
    }

    public static HardwareEncoder fromId(String id)
    {
        if (id == null)
        {
            return AUTO;
        }

        for (HardwareEncoder encoder : values())
        {
            if (encoder.id.equalsIgnoreCase(id))
            {
                return encoder;
            }
        }

        return AUTO;
    }
}
