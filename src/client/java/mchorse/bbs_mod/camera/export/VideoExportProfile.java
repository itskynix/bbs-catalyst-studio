package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DaVinci Resolve "Deliver" style Video Export Profile.
 * Encapsulates container format, video codec, hardware acceleration backend,
 * resolution, framerate, rate control, bitrates, audio settings, and GIF palette options.
 */
public class VideoExportProfile
{
    private String id;
    private String name;
    private String description;

    private ExportFormat format = ExportFormat.MP4;
    private VideoCodec codec = VideoCodec.H264;
    private HardwareEncoder hardwareEncoder = HardwareEncoder.AUTO;

    /** Width in pixels, or <= 0 to use current render resolution. */
    private int width = 0;
    /** Height in pixels, or <= 0 to use current render resolution. */
    private int height = 0;
    /** Output framerate, or <= 0 to use current project framerate. */
    private double frameRate = 0;

    private RateControl rateControl = RateControl.CRF;
    /** CRF / CQ quality value (lower is higher quality, e.g. 18 is visually lossless). */
    private int quality = 18;
    /** Video bitrate in kbps (e.g. 20000 = 20 Mbps). */
    private int bitrate = 20000;
    private EncoderPreset preset = EncoderPreset.FAST;
    private String pixelFormat = "yuv420p";

    private AudioCodec audioCodec = AudioCodec.AAC;
    /** Audio bitrate in kbps (e.g. 320). */
    private int audioBitrate = 320;

    private boolean exportVideo = true;
    private boolean exportAudio = true;

    /** GIF specific framerate (typically 15-30 fps). */
    private int gifFps = 24;
    /** GIF dithering mode (sierra2_4a, floyd_steinberg, bayer, none). */
    private String gifDither = "sierra2_4a";
    /** GIF maximum palette colors (usually 256). */
    private int gifMaxColors = 256;

    public enum RangeType
    {
        ENTIRE("Entire Film"),
        IN_OUT("In/Out Range"),
        CUSTOM("Custom Range");

        private final String label;

        RangeType(String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return this.label;
        }

        public static RangeType fromId(String id)
        {
            if (id != null)
            {
                for (RangeType r : values())
                {
                    if (r.name().equalsIgnoreCase(id)) return r;
                }
            }
            return ENTIRE;
        }
    }

    private RangeType rangeType = RangeType.ENTIRE;
    private int customStartTick = 0;
    private int customEndTick = 0;

    /** Extra FFmpeg command line arguments appended to the video stream. */
    private String customArguments = "";

    public VideoExportProfile(String id, String name, String description)
    {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public VideoExportProfile copy()
    {
        VideoExportProfile copy = new VideoExportProfile(this.id, this.name, this.description);

        copy.format = this.format;
        copy.codec = this.codec;
        copy.hardwareEncoder = this.hardwareEncoder;
        copy.width = this.width;
        copy.height = this.height;
        copy.frameRate = this.frameRate;
        copy.rateControl = this.rateControl;
        copy.quality = this.quality;
        copy.bitrate = this.bitrate;
        copy.preset = this.preset;
        copy.pixelFormat = this.pixelFormat;
        copy.audioCodec = this.audioCodec;
        copy.audioBitrate = this.audioBitrate;
        copy.exportVideo = this.exportVideo;
        copy.exportAudio = this.exportAudio;
        copy.gifFps = this.gifFps;
        copy.gifDither = this.gifDither;
        copy.gifMaxColors = this.gifMaxColors;
        copy.customArguments = this.customArguments;
        copy.rangeType = this.rangeType;
        copy.customStartTick = this.customStartTick;
        copy.customEndTick = this.customEndTick;

        return copy;
    }

    /* Getters and setters */

    public String getId()
    {
        return this.id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public ExportFormat getFormat()
    {
        return this.format;
    }

    public void setFormat(ExportFormat format)
    {
        this.format = format;

        if (!format.supportsCodec(this.codec))
        {
            this.codec = format.getDefaultCodec();
        }

        if (!format.isAudioSupported())
        {
            this.audioCodec = AudioCodec.NONE;
        }
        else if (this.audioCodec == AudioCodec.NONE)
        {
            this.audioCodec = AudioCodec.AAC;
        }
    }

    public VideoCodec getCodec()
    {
        return this.codec;
    }

    public void setCodec(VideoCodec codec)
    {
        this.codec = codec;
    }

    public HardwareEncoder getHardwareEncoder()
    {
        return this.hardwareEncoder;
    }

    public void setHardwareEncoder(HardwareEncoder hardwareEncoder)
    {
        this.hardwareEncoder = hardwareEncoder;
    }

    public int getWidth()
    {
        return this.width;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public int getHeight()
    {
        return this.height;
    }

    public void setHeight(int height)
    {
        this.height = height;
    }

    public double getFrameRate()
    {
        return this.frameRate;
    }

    public void setFrameRate(double frameRate)
    {
        this.frameRate = frameRate;
    }

    public RateControl getRateControl()
    {
        return this.rateControl;
    }

    public void setRateControl(RateControl rateControl)
    {
        this.rateControl = rateControl;
    }

    public int getQuality()
    {
        return this.quality;
    }

    public void setQuality(int quality)
    {
        this.quality = Math.max(0, Math.min(51, quality));
    }

    public int getBitrate()
    {
        return this.bitrate;
    }

    public void setBitrate(int bitrate)
    {
        this.bitrate = Math.max(100, bitrate);
    }

    public EncoderPreset getPreset()
    {
        return this.preset;
    }

    public void setPreset(EncoderPreset preset)
    {
        this.preset = preset;
    }

    public String getPixelFormat()
    {
        return this.pixelFormat;
    }

    public void setPixelFormat(String pixelFormat)
    {
        this.pixelFormat = pixelFormat;
    }

    public AudioCodec getAudioCodec()
    {
        return this.audioCodec;
    }

    public void setAudioCodec(AudioCodec audioCodec)
    {
        this.audioCodec = audioCodec;
    }

    public int getAudioBitrate()
    {
        return this.audioBitrate;
    }

    public void setAudioBitrate(int audioBitrate)
    {
        this.audioBitrate = audioBitrate;
    }

    public int getGifFps()
    {
        return this.gifFps;
    }

    public void setGifFps(int gifFps)
    {
        this.gifFps = Math.max(1, Math.min(60, gifFps));
    }

    public String getGifDither()
    {
        return this.gifDither;
    }

    public void setGifDither(String gifDither)
    {
        this.gifDither = gifDither;
    }

    public int getGifMaxColors()
    {
        return this.gifMaxColors;
    }

    public void setGifMaxColors(int gifMaxColors)
    {
        this.gifMaxColors = Math.max(2, Math.min(256, gifMaxColors));
    }

    public String getCustomArguments()
    {
        return this.customArguments;
    }

    public void setCustomArguments(String customArguments)
    {
        this.customArguments = customArguments == null ? "" : customArguments;
    }

    public boolean isExportVideo()
    {
        return this.exportVideo;
    }

    public void setExportVideo(boolean exportVideo)
    {
        this.exportVideo = exportVideo;
    }

    public boolean isExportAudio()
    {
        return this.exportAudio;
    }

    public void setExportAudio(boolean exportAudio)
    {
        this.exportAudio = exportAudio;
    }

    /* Serialization */

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("id", this.id);
        data.putString("name", this.name);
        data.putString("description", this.description);
        data.putString("format", this.format.getId());
        data.putString("codec", this.codec.getId());
        data.putString("hardwareEncoder", this.hardwareEncoder.getId());
        data.putInt("width", this.width);
        data.putInt("height", this.height);
        data.putDouble("frameRate", this.frameRate);
        data.putString("rateControl", this.rateControl.getId());
        data.putInt("quality", this.quality);
        data.putInt("bitrate", this.bitrate);
        data.putString("preset", this.preset.getId());
        data.putString("pixelFormat", this.pixelFormat);
        data.putString("audioCodec", this.audioCodec.getFfmpegCodec());
        data.putInt("audioBitrate", this.audioBitrate);
        data.putBool("exportVideo", this.exportVideo);
        data.putBool("exportAudio", this.exportAudio);
        data.putInt("gifFps", this.gifFps);
        data.putString("gifDither", this.gifDither);
        data.putInt("gifMaxColors", this.gifMaxColors);
        data.putString("customArguments", this.customArguments);
        data.putString("rangeType", this.getRangeType().name());
        data.putInt("customStartTick", this.customStartTick);
        data.putInt("customEndTick", this.customEndTick);

        return data;
    }

    public void fromData(MapType data)
    {
        if (data == null)
        {
            return;
        }

        if (data.has("id")) this.id = data.getString("id");
        if (data.has("name")) this.name = data.getString("name");
        if (data.has("description")) this.description = data.getString("description");
        if (data.has("format")) this.format = ExportFormat.fromId(data.getString("format"));
        if (data.has("codec")) this.codec = VideoCodec.fromId(data.getString("codec"));
        if (data.has("hardwareEncoder")) this.hardwareEncoder = HardwareEncoder.fromId(data.getString("hardwareEncoder"));
        if (data.has("width")) this.width = data.getInt("width");
        if (data.has("height")) this.height = data.getInt("height");
        if (data.has("frameRate")) this.frameRate = data.getDouble("frameRate");
        if (data.has("rateControl")) this.rateControl = RateControl.fromId(data.getString("rateControl"));
        if (data.has("quality")) this.quality = data.getInt("quality");
        if (data.has("bitrate")) this.bitrate = data.getInt("bitrate");
        if (data.has("preset")) this.preset = EncoderPreset.fromId(data.getString("preset"));
        if (data.has("pixelFormat")) this.pixelFormat = data.getString("pixelFormat");
        if (data.has("audioCodec")) this.audioCodec = AudioCodec.fromId(data.getString("audioCodec"));
        if (data.has("audioBitrate")) this.audioBitrate = data.getInt("audioBitrate");
        if (data.has("exportVideo")) this.exportVideo = data.getBool("exportVideo");
        if (data.has("exportAudio")) this.exportAudio = data.getBool("exportAudio");
        if (data.has("gifFps")) this.gifFps = data.getInt("gifFps");
        if (data.has("gifDither")) this.gifDither = data.getString("gifDither");
        if (data.has("gifMaxColors")) this.gifMaxColors = data.getInt("gifMaxColors");
        if (data.has("customArguments")) this.customArguments = data.getString("customArguments");
        if (data.has("rangeType")) this.rangeType = RangeType.fromId(data.getString("rangeType"));
        if (data.has("customStartTick")) this.customStartTick = data.getInt("customStartTick");
        if (data.has("customEndTick")) this.customEndTick = data.getInt("customEndTick");
    }

    public RangeType getRangeType()
    {
        return this.rangeType != null ? this.rangeType : RangeType.ENTIRE;
    }

    public void setRangeType(RangeType rangeType)
    {
        this.rangeType = rangeType != null ? rangeType : RangeType.ENTIRE;
    }

    public int getCustomStartTick()
    {
        return this.customStartTick;
    }

    public void setCustomStartTick(int customStartTick)
    {
        this.customStartTick = customStartTick;
    }

    public int getCustomEndTick()
    {
        return this.customEndTick;
    }

    public void setCustomEndTick(int customEndTick)
    {
        this.customEndTick = customEndTick;
    }

    /* Built-in Presets (DaVinci Resolve Deliver Inspired) */

    public static List<VideoExportProfile> getBuiltInPresets()
    {
        List<VideoExportProfile> presets = new ArrayList<>();

        // 1. YouTube 1080p60
        VideoExportProfile yt1080 = new VideoExportProfile("youtube_1080p", "YouTube 1080p", "High-Quality H.264 standard for YouTube (1080p, 60fps)");
        yt1080.setFormat(ExportFormat.MP4);
        yt1080.setCodec(VideoCodec.H264);
        yt1080.setHardwareEncoder(HardwareEncoder.AUTO);
        yt1080.setWidth(1920);
        yt1080.setHeight(1080);
        yt1080.setFrameRate(60);
        yt1080.setRateControl(RateControl.CRF);
        yt1080.setQuality(18);
        yt1080.setBitrate(20000);
        yt1080.setPreset(EncoderPreset.FAST);
        yt1080.setPixelFormat("yuv420p");
        yt1080.setAudioCodec(AudioCodec.AAC);
        yt1080.setAudioBitrate(192);
        presets.add(yt1080);

        // 2. YouTube 4K UHD
        VideoExportProfile yt4k = new VideoExportProfile("youtube_4k", "YouTube 4K UHD", "Ultra HD H.265/HEVC with high bitrate (3840x2160, 60fps)");
        yt4k.setFormat(ExportFormat.MP4);
        yt4k.setCodec(VideoCodec.H265);
        yt4k.setHardwareEncoder(HardwareEncoder.AUTO);
        yt4k.setWidth(3840);
        yt4k.setHeight(2160);
        yt4k.setFrameRate(60);
        yt4k.setRateControl(RateControl.CRF);
        yt4k.setQuality(18);
        yt4k.setBitrate(45000);
        yt4k.setPreset(EncoderPreset.MEDIUM);
        yt4k.setPixelFormat("yuv420p");
        yt4k.setAudioCodec(AudioCodec.AAC);
        yt4k.setAudioBitrate(256);
        presets.add(yt4k);

        // 3. Apple ProRes 422 HQ Master
        VideoExportProfile prores = new VideoExportProfile("prores_master", "ProRes 422 HQ Master", "Industry standard editing & post-production master (10-bit)");
        prores.setFormat(ExportFormat.MOV);
        prores.setCodec(VideoCodec.PRORES);
        prores.setHardwareEncoder(HardwareEncoder.AUTO);
        prores.setWidth(0); // keep project size
        prores.setHeight(0);
        prores.setFrameRate(0); // keep project fps
        prores.setRateControl(RateControl.LOSSLESS);
        prores.setPixelFormat("yuv422p10le");
        prores.setAudioCodec(AudioCodec.PCM_16);
        presets.add(prores);

        // 4. Discord / Web Small File
        VideoExportProfile discord = new VideoExportProfile("discord_web", "Discord / Web Fast", "High compression H.264 designed for chat sharing and small size (720p30)");
        discord.setFormat(ExportFormat.MP4);
        discord.setCodec(VideoCodec.H264);
        discord.setHardwareEncoder(HardwareEncoder.CPU); // libx264 gives best small file efficiency
        discord.setWidth(1280);
        discord.setHeight(720);
        discord.setFrameRate(30);
        discord.setRateControl(RateControl.CBR);
        discord.setBitrate(3000);
        discord.setQuality(24);
        discord.setPreset(EncoderPreset.FAST);
        discord.setPixelFormat("yuv420p");
        discord.setAudioCodec(AudioCodec.AAC);
        discord.setAudioBitrate(128);
        presets.add(discord);

        // 5. Cinematic AV1 Next-Gen
        VideoExportProfile av1 = new VideoExportProfile("av1_cinematic", "Cinematic AV1", "Next-generation royalty-free codec with extreme compression efficiency");
        av1.setFormat(ExportFormat.MKV);
        av1.setCodec(VideoCodec.AV1);
        av1.setHardwareEncoder(HardwareEncoder.AUTO);
        av1.setWidth(0);
        av1.setHeight(0);
        av1.setFrameRate(60);
        av1.setRateControl(RateControl.CRF);
        av1.setQuality(22);
        av1.setPreset(EncoderPreset.MEDIUM);
        av1.setPixelFormat("yuv420p");
        av1.setAudioCodec(AudioCodec.OPUS);
        av1.setAudioBitrate(160);
        presets.add(av1);

        // 6. High-Quality Two-Pass Animated GIF
        VideoExportProfile gif = new VideoExportProfile("hq_gif", "Animated GIF (HQ Palette)", "High-quality animated GIF with custom 256-color palette and dithering");
        gif.setFormat(ExportFormat.GIF);
        gif.setCodec(VideoCodec.GIF);
        gif.setWidth(640);
        gif.setHeight(360);
        gif.setFrameRate(24);
        gif.setGifFps(24);
        gif.setGifDither("sierra2_4a");
        gif.setGifMaxColors(256);
        gif.setAudioCodec(AudioCodec.NONE);
        presets.add(gif);

        // 7. Lossless PNG Image Sequence
        VideoExportProfile pngSeq = new VideoExportProfile("png_sequence", "PNG Image Sequence", "Lossless individual frames formatted as image sequence (%NAME%_00001.png)");
        pngSeq.setFormat(ExportFormat.PNG_SEQUENCE);
        pngSeq.setCodec(VideoCodec.PNG);
        pngSeq.setWidth(0);
        pngSeq.setHeight(0);
        pngSeq.setFrameRate(0);
        pngSeq.setAudioCodec(AudioCodec.NONE);
        presets.add(pngSeq);

        return Collections.unmodifiableList(presets);
    }
}
