package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic FFmpeg command line builder for BBS Render & Export.
 * Formats command line arguments tailored to the selected {@link VideoExportProfile},
 * hardware encoder, container, audio configuration, and video filter chain.
 */
public class FFmpegCommandBuilder
{
    private VideoExportProfile profile;
    private String movieName;
    private File audioFile;
    private int inputWidth;
    private int inputHeight;
    private double inputFps;
    private int motionBlurSteps;
    private File outputFolder;

    public FFmpegCommandBuilder(VideoExportProfile profile)
    {
        this.profile = profile != null ? profile : VideoExportProfile.getBuiltInPresets().get(0);
    }

    public FFmpegCommandBuilder profile(VideoExportProfile profile)
    {
        this.profile = profile;
        return this;
    }

    public FFmpegCommandBuilder movieName(String movieName)
    {
        this.movieName = movieName;
        return this;
    }

    public FFmpegCommandBuilder audio(File audioFile)
    {
        this.audioFile = audioFile;
        return this;
    }

    public FFmpegCommandBuilder inputResolution(int width, int height)
    {
        this.inputWidth = width;
        this.inputHeight = height;
        return this;
    }

    public FFmpegCommandBuilder inputFramerate(double fps)
    {
        this.inputFps = fps;
        return this;
    }

    public FFmpegCommandBuilder motionBlur(int steps)
    {
        this.motionBlurSteps = Math.max(0, steps);
        return this;
    }

    public FFmpegCommandBuilder outputFolder(File outputFolder)
    {
        this.outputFolder = outputFolder;
        return this;
    }

    /**
     * Builds the complete list of arguments for executing FFmpeg via {@link ProcessBuilder}.
     */
    public List<String> buildRecordingArgs()
    {
        List<String> args = new ArrayList<>();

        args.add(FFMpegUtils.getFFMPEG().replace("\"", "").trim());
        args.add("-y"); // overwrite without prompt

        // 1. Raw video input stream from stdin (RGBA8 from Minecraft framebuffer)
        args.add("-f");
        args.add("rawvideo");
        args.add("-pix_fmt");
        args.add("rgba");
        args.add("-s");
        args.add(this.inputWidth + "x" + this.inputHeight);
        args.add("-r");
        args.add(String.valueOf((float) this.inputFps));
        args.add("-i");
        args.add("-");

        // 2. Audio input (if available and supported)
        boolean hasAudio = this.audioFile != null && this.profile.getFormat().isAudioSupported() && !this.profile.getAudioCodec().isNone();

        if (hasAudio)
        {
            args.add("-i");
            args.add(this.audioFile.getAbsolutePath().replace("\"", "").trim());
        }

        // 3. Filter chain
        String filterGraph = this.buildFilterChain();
        if (filterGraph != null && !filterGraph.isEmpty())
        {
            args.add("-vf");
            args.add(filterGraph);
        }

        // 4. Video Codec & Hardware Encoder parameters
        this.appendVideoCodecArgs(args);

        // 5. Audio Codec parameters
        if (hasAudio)
        {
            this.appendAudioCodecArgs(args);
        }
        else if (this.profile.getFormat().isAudioSupported())
        {
            args.add("-an"); // explicitly disable audio if no audio stream
        }

        // 6. Custom extra arguments
        String custom = this.profile.getCustomArguments();
        if (custom != null && !custom.trim().isEmpty())
        {
            for (String part : custom.trim().split(" "))
            {
                if (!part.isEmpty())
                {
                    args.add(part.replace("\"", "").trim());
                }
            }
        }

        // 7. Container specific flags
        if (this.profile.getFormat() == ExportFormat.MP4)
        {
            args.add("-movflags");
            args.add("+faststart");
        }

        // 8. Output filename / pattern
        String outputPattern = this.profile.getFormat().getOutputPattern(this.movieName != null ? this.movieName : "video");
        if (this.outputFolder != null)
        {
            args.add(new File(this.outputFolder, outputPattern).getAbsolutePath().replace("\"", "").trim());
        }
        else
        {
            args.add(outputPattern.replace("\"", "").trim());
        }

        return args;
    }

    /**
     * Builds arguments for {@link mchorse.bbs_mod.utils.VideoMuxer} secondary audio merge pass.
     */
    public List<String> buildMuxArgs(File videoFile, File audioFile, String outputMovieName)
    {
        List<String> args = new ArrayList<>();

        args.add(FFMpegUtils.getFFMPEG().replace("\"", "").trim());
        args.add("-y");
        args.add("-i");
        args.add(videoFile.getName());
        args.add("-i");
        args.add(audioFile.getName());
        args.add("-map");
        args.add("0:v:0");
        args.add("-map");
        args.add("1:a:0");
        args.add("-c:v");
        args.add("copy");

        if (this.profile.getFormat().isAudioSupported() && !this.profile.getAudioCodec().isNone())
        {
            args.add("-c:a");
            args.add(this.profile.getAudioCodec().getFfmpegCodec());

            if (this.profile.getAudioCodec() != AudioCodec.PCM_16 && this.profile.getAudioCodec() != AudioCodec.COPY)
            {
                args.add("-b:a");
                args.add(this.profile.getAudioBitrate() + "k");
            }
        }
        else
        {
            args.add("-c:a");
            args.add("aac");
            args.add("-b:a");
            args.add("192k");
        }

        args.add("-shortest");

        String outputPattern = this.profile.getFormat().getOutputPattern(outputMovieName);
        args.add(outputPattern);

        return args;
    }

    /**
     * Constructs the video filter chain including vflip, motion blur, resolution scaling,
     * framerate resampling, and high-quality GIF palettegen.
     */
    private String buildFilterChain()
    {
        StringBuilder sb = new StringBuilder();

        // OpenGL textures are bottom-left origin; vflip renders them upright
        sb.append("vflip");

        // Motion blur frames accumulation
        for (int i = 0; i < this.motionBlurSteps; i++)
        {
            sb.append(",tblend=all_mode=average,framestep=2");
        }

        // Resolution scaling if requested
        int targetW = this.profile.getWidth();
        int targetH = this.profile.getHeight();
        if (targetW > 0 && targetH > 0 && (targetW != this.inputWidth || targetH != this.inputHeight))
        {
            sb.append(",scale=").append(targetW).append(":").append(targetH).append(":flags=lanczos");
        }

        // GIF High-Quality Two-Pass Palettegen filter
        if (this.profile.getFormat().isGif() || this.profile.getCodec().isGif())
        {
            int gifFps = this.profile.getGifFps() > 0 ? this.profile.getGifFps() : 24;
            String dither = this.profile.getGifDither() != null ? this.profile.getGifDither() : "sierra2_4a";
            int maxColors = this.profile.getGifMaxColors() > 0 ? this.profile.getGifMaxColors() : 256;

            // Chained filtergraph: [input] -> fps -> split [s0][s1]; [s0] palettegen [p]; [s1][p] paletteuse
            sb.append(",fps=").append(gifFps);
            sb.append(",split[s0][s1];[s0]palettegen=stats_mode=full:max_colors=").append(maxColors).append("[p];[s1][p]paletteuse=dither=").append(dither);
            return sb.toString();
        }

        // Framerate resampling if profile specifies custom fps
        if (this.profile.getFrameRate() > 0 && Math.abs(this.profile.getFrameRate() - this.inputFps) > 0.1)
        {
            sb.append(",fps=").append((float) this.profile.getFrameRate());
        }

        return sb.toString();
    }

    /**
     * Appends video codec, rate control, and hardware acceleration flags.
     */
    private void appendVideoCodecArgs(List<String> args)
    {
        VideoCodec codec = this.profile.getCodec();
        HardwareEncoder hw = this.profile.getHardwareEncoder().resolve();

        // 1. GIF
        if (codec.isGif() || this.profile.getFormat().isGif())
        {
            args.add("-c:v");
            args.add("gif");
            return;
        }

        // 2. PNG Sequence
        if (codec.isImageSequence() || this.profile.getFormat().isImageSequence())
        {
            args.add("-c:v");
            args.add("png");
            return;
        }

        // 3. Apple ProRes
        if (codec == VideoCodec.PRORES)
        {
            String encoderName = hw.getFfmpegEncoder(VideoCodec.PRORES);
            args.add("-c:v");
            args.add(encoderName);

            if ("prores_ks".equals(encoderName))
            {
                args.add("-profile:v");
                args.add("3"); // ProRes 422 HQ
                args.add("-vendor");
                args.add("apl0");
            }
            else if ("prores_videotoolbox".equals(encoderName))
            {
                args.add("-profile:v");
                args.add("3");
            }

            args.add("-pix_fmt");
            args.add(this.profile.getPixelFormat() != null ? this.profile.getPixelFormat() : "yuv422p10le");
            return;
        }

        // 4. Hardware Accelerated & Software H.264 / H.265 / AV1 / VP9
        String encoder = hw.getFfmpegEncoder(codec);
        args.add("-c:v");
        args.add(encoder);

        RateControl rc = this.profile.getRateControl();
        int quality = this.profile.getQuality();
        int bitrate = this.profile.getBitrate();
        EncoderPreset preset = this.profile.getPreset();

        switch (hw)
        {
            case NVENC:
                args.add("-preset");
                args.add(preset.getPresetFor(HardwareEncoder.NVENC));

                if (rc == RateControl.LOSSLESS)
                {
                    args.add("-tune");
                    args.add("lossless");
                    args.add("-qp");
                    args.add("0");
                }
                else if (rc == RateControl.CBR)
                {
                    args.add("-tune");
                    args.add("hq");
                    args.add("-rc");
                    args.add("cbr");
                    args.add("-b:v");
                    args.add(bitrate + "k");
                    args.add("-minrate");
                    args.add(bitrate + "k");
                    args.add("-maxrate");
                    args.add(bitrate + "k");
                    args.add("-bufsize");
                    args.add((bitrate * 2) + "k");
                    args.add("-cbr_padding");
                    args.add("1");
                }
                else if (rc == RateControl.VBR)
                {
                    args.add("-tune");
                    args.add("hq");
                    if (bitrate > 0)
                    {
                        args.add("-b:v");
                        args.add(bitrate + "k");
                        args.add("-maxrate");
                        args.add(bitrate + "k");
                        args.add("-bufsize");
                        args.add((bitrate * 2) + "k");
                    }
                    args.add("-cq");
                    args.add(String.valueOf(quality));
                }
                else
                {
                    // CRF / Default
                    args.add("-tune");
                    args.add("hq");
                    if (bitrate > 0)
                    {
                        args.add("-b:v");
                        args.add(bitrate + "k");
                        args.add("-maxrate");
                        args.add(bitrate + "k");
                        args.add("-bufsize");
                        args.add((bitrate * 2) + "k");
                    }
                    args.add("-cq");
                    args.add(String.valueOf(quality));
                }
                break;

            case AMF:
                args.add("-quality");
                args.add(preset.getPresetFor(HardwareEncoder.AMF));

                if (rc == RateControl.LOSSLESS)
                {
                    args.add("-rc");
                    args.add("cqp");
                    args.add("-qp_i");
                    args.add("0");
                    args.add("-qp_p");
                    args.add("0");
                }
                else if (rc == RateControl.CBR || rc == RateControl.VBR)
                {
                    args.add("-rc");
                    args.add(rc == RateControl.CBR ? "cbr" : "vbr_peak");
                    args.add("-b:v");
                    args.add(bitrate + "k");
                    args.add("-maxrate");
                    args.add(bitrate + "k");
                }
                else
                {
                    args.add("-rc");
                    args.add("cqp");
                    args.add("-qp_i");
                    args.add(String.valueOf(quality));
                    args.add("-qp_p");
                    args.add(String.valueOf(quality));
                }
                break;

            case QSV:
                args.add("-preset");
                args.add(preset.getPresetFor(HardwareEncoder.QSV));

                if (rc == RateControl.CBR || rc == RateControl.VBR)
                {
                    args.add("-b:v");
                    args.add(bitrate + "k");
                    args.add("-maxrate");
                    args.add(bitrate + "k");
                }
                else
                {
                    args.add("-global_quality");
                    args.add(String.valueOf(quality));
                }
                break;

            case VIDEOTOOLBOX:
                if (rc == RateControl.CBR || rc == RateControl.VBR)
                {
                    args.add("-b:v");
                    args.add(bitrate + "k");
                }
                else
                {
                    // VideoToolbox quality range is 1-100 (higher is better)
                    int vtQuality = Math.max(1, Math.min(100, 100 - (quality * 2)));
                    args.add("-q:v");
                    args.add(String.valueOf(vtQuality));
                }
                break;

            case CPU:
            default:
                if ("libx264".equals(encoder))
                {
                    args.add("-preset");
                    args.add(preset.getPresetFor(HardwareEncoder.CPU));
                    args.add("-tune");
                    args.add("zerolatency");

                    if (rc == RateControl.LOSSLESS)
                    {
                        args.add("-qp");
                        args.add("0");
                    }
                    else if (rc == RateControl.CBR || rc == RateControl.VBR)
                    {
                        args.add("-b:v");
                        args.add(bitrate + "k");
                        if (rc == RateControl.CBR)
                        {
                            args.add("-minrate");
                            args.add(bitrate + "k");
                            args.add("-maxrate");
                            args.add(bitrate + "k");
                            args.add("-bufsize");
                            args.add((bitrate * 2) + "k");
                        }
                        else
                        {
                            args.add("-crf");
                            args.add(String.valueOf(quality));
                            args.add("-maxrate");
                            args.add(bitrate + "k");
                            args.add("-bufsize");
                            args.add((bitrate * 2) + "k");
                        }
                    }
                    else
                    {
                        args.add("-crf");
                        args.add(String.valueOf(quality));
                    }
                }
                else if ("libx265".equals(encoder))
                {
                    args.add("-preset");
                    args.add(preset.getPresetFor(HardwareEncoder.CPU));

                    if (rc == RateControl.LOSSLESS)
                    {
                        args.add("-x265-params");
                        args.add("lossless=1");
                    }
                    else if (rc == RateControl.CBR)
                    {
                        args.add("-b:v");
                        args.add(bitrate + "k");
                    }
                    else
                    {
                        args.add("-crf");
                        args.add(String.valueOf(quality));
                    }
                }
                else if ("libsvtav1".equals(encoder))
                {
                    args.add("-preset");
                    args.add("8");
                    args.add("-crf");
                    args.add(String.valueOf(quality));
                }
                break;
        }

        // Pixel format
        args.add("-pix_fmt");
        args.add(this.profile.getPixelFormat() != null ? this.profile.getPixelFormat() : "yuv420p");
    }

    /**
     * Appends audio encoding arguments.
     */
    private void appendAudioCodecArgs(List<String> args)
    {
        AudioCodec audioCodec = this.profile.getAudioCodec();
        args.add("-c:a");
        args.add(audioCodec.getFfmpegCodec());

        if (audioCodec != AudioCodec.PCM_16 && audioCodec != AudioCodec.COPY && audioCodec != AudioCodec.NONE)
        {
            args.add("-b:a");
            args.add(this.profile.getAudioBitrate() + "k");
        }

        args.add("-shortest");
    }

    /**
     * Generates a template string containing tokens (%WIDTH%, %HEIGHT%, %FPS%, %NAME%, %FILTERS%, %AUDIO_TRACK%)
     * for seamless compatibility with legacy {@link mchorse.bbs_mod.BBSSettings#videoArguments}.
     */
    public String buildLegacyTemplate(boolean withAudio)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("-f rawvideo -pix_fmt rgba -s %WIDTH%x%HEIGHT% -r %FPS% -i - ");

        if (withAudio && this.profile.getFormat().isAudioSupported() && !this.profile.getAudioCodec().isNone())
        {
            sb.append("-i %AUDIO_TRACK% ");
        }

        sb.append("-vf %FILTERS% ");

        List<String> dummyArgs = new ArrayList<>();
        this.appendVideoCodecArgs(dummyArgs);

        if (withAudio && this.profile.getFormat().isAudioSupported() && !this.profile.getAudioCodec().isNone())
        {
            this.appendAudioCodecArgs(dummyArgs);
        }

        if (this.profile.getFormat() == ExportFormat.MP4)
        {
            dummyArgs.add("-movflags");
            dummyArgs.add("+faststart");
        }

        for (int i = 0; i < dummyArgs.size(); i++)
        {
            sb.append(dummyArgs.get(i));
            if (i < dummyArgs.size() - 1)
            {
                sb.append(" ");
            }
        }

        sb.append(" ");
        sb.append(this.profile.getFormat().getOutputPattern("%NAME%"));

        return sb.toString();
    }
}
