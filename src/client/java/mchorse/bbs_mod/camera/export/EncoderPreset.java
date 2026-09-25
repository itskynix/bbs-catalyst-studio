package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * Encoder speed vs quality presets.
 */
public enum EncoderPreset
{
    ULTRAFAST("ultrafast", IKey.raw("Ultra Fast (Fastest Render)"), "ultrafast", "p1", "speed", "veryfast"),
    FAST("fast", IKey.raw("Fast"), "fast", "p3", "speed", "fast"),
    MEDIUM("medium", IKey.raw("Medium / Balanced"), "medium", "p4", "balanced", "medium"),
    SLOW("slow", IKey.raw("Slow / High Quality"), "slow", "p6", "quality", "slow"),
    VERYSLOW("veryslow", IKey.raw("Very Slow / Archival Master"), "veryslow", "p7", "quality", "veryslow");

    private final String id;
    private final IKey label;
    private final String x264Preset;
    private final String nvencPreset;
    private final String amfQuality;
    private final String qsvPreset;

    EncoderPreset(String id, IKey label, String x264Preset, String nvencPreset, String amfQuality, String qsvPreset)
    {
        this.id = id;
        this.label = label;
        this.x264Preset = x264Preset;
        this.nvencPreset = nvencPreset;
        this.amfQuality = amfQuality;
        this.qsvPreset = qsvPreset;
    }

    public String getId()
    {
        return this.id;
    }

    public IKey getLabel()
    {
        return this.label;
    }

    public String getPresetFor(HardwareEncoder encoder)
    {
        HardwareEncoder resolved = encoder.resolve();

        switch (resolved)
        {
            case NVENC:
                return this.nvencPreset;
            case AMF:
                return this.amfQuality;
            case QSV:
                return this.qsvPreset;
            default:
                return this.x264Preset;
        }
    }

    public static EncoderPreset fromId(String id)
    {
        if (id == null)
        {
            return MEDIUM;
        }

        for (EncoderPreset preset : values())
        {
            if (preset.id.equalsIgnoreCase(id) || preset.name().equalsIgnoreCase(id))
            {
                return preset;
            }
        }

        return MEDIUM;
    }
}
