package mchorse.bbs_mod.ui.film.export;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.export.*;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DaVinci Resolve inspired "Deliver" Overlay Panel.
 * Provides complete render settings (Format, Codec, GPU Acceleration, Quality, Audio, Output)
 * alongside a live Render Queue for individual or batch exports.
 */
public class UIDeliverOverlayPanel extends UIOverlayPanel
{
    private final UIFilmPanel filmPanel;
    private final UICatalystPanel catalystPanel;
    private VideoExportProfile currentProfile;

    /* Top Presets bar */
    public UIButton presetButton;
    public UIIcon savePreset;

    /* Tabs */
    public UIButton tabVideo;
    public UIButton tabAudio;
    public UIButton tabFile;
    private int currentTab = 0; // 0 = Video, 1 = Audio, 2 = File

    /* Left Settings Views */
    public UIElement settingsContainer;
    public UIElement videoTabContent;
    public UIElement audioTabContent;
    public UIElement fileTabContent;

    /* Render Range controls */
    public UIButton renderRangeButton;
    public UIElement rangeInputsRow;
    public UITextbox rangeStartTextbox;
    public UITextbox rangeEndTextbox;

    /* Video controls */
    public UICirculate formatCirculate;
    public UICirculate codecCirculate;
    public UICirculate hwEncoderCirculate;
    public UILabel gpuDetectedLabel;
    public UITrackpad widthTrackpad;
    public UITrackpad heightTrackpad;
    public UITrackpad fpsTrackpad;
    public UICirculate rateControlCirculate;
    public UITrackpad qualityTrackpad;
    public UITrackpad bitrateTrackpad;
    public UICirculate presetCirculate;

    /* GIF controls */
    public UIElement gifControls;
    public UITrackpad gifFpsTrackpad;
    public UICirculate gifDitherCirculate;
    public UITrackpad gifColorsTrackpad;

    /* Stream toggles */
    public UIToggle exportVideoToggle;
    public UIToggle exportAudioToggle;

    /* Audio controls */
    public final List<AudioCodec> availableAudioCodecs = new ArrayList<>();
    public UICirculate audioCodecCirculate;
    public UICirculate audioBitrateCirculate;
    public UIToggle captureMinecraftSoundsToggle;

    /* File controls */
    public UITextbox filenameTextbox;
    public UITextbox outputFolderTextbox;
    public UIButton browseFolderButton;

    /* Right Queue panel */
    public UIElement queueContainer;
    public UIElement topQueueRow;
    public UIRenderJobList jobList;
    public UIButton addToQueueButton;
    public UIIcon clearQueueButton;
    public UIButton renderAllButton;
    public UIButton renderNowButton;

    public UIDeliverOverlayPanel(UIFilmPanel filmPanel)
    {
        this(filmPanel, null);
    }

    public UIDeliverOverlayPanel(UICatalystPanel catalystPanel)
    {
        this(null, catalystPanel);
    }

    public UIDeliverOverlayPanel(UIFilmPanel filmPanel, UICatalystPanel catalystPanel)
    {
        super(IKey.raw("Deliver — Render & Export"));

        this.filmPanel = filmPanel;
        this.catalystPanel = catalystPanel;
        this.currentProfile = ExportProfiles.getSelectedProfile().copy();

        if (this.catalystPanel != null)
        {
            CatalystComposition comp = this.catalystPanel.getActiveComposition();
            if (comp != null)
            {
                this.currentProfile.setWidth(comp.width > 0 ? comp.width : 1920);
                this.currentProfile.setHeight(comp.height > 0 ? comp.height : 1080);
                this.currentProfile.setFrameRate(comp.fps > 0 ? comp.fps : 60);
            }
            this.currentProfile.setRangeType(VideoExportProfile.RangeType.ENTIRE);
        }

        if (!this.currentProfile.getFormat().isGif() && !this.currentProfile.getFormat().isImageSequence())
        {
            this.currentProfile.setRateControl(RateControl.CRF);
            this.currentProfile.setQuality(18);
        }

        this.setupTopPresetBar();
        this.setupTabs();
        this.setupVideoTab();
        this.setupAudioTab();
        this.setupFileTab();
        this.setupQueuePanel();

        this.content.add(
            this.presetButton, this.savePreset,
            this.tabVideo, this.tabAudio, this.tabFile,
            this.settingsContainer, this.queueContainer
        );

        this.applyProfileToUI();
        this.switchTab(0);

        RenderQueue.addListener(this::onQueueUpdated);
    }

    private void setupTopPresetBar()
    {
        this.presetButton = new UIButton(IKey.raw("Preset: " + this.currentProfile.getName()), (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                for (VideoExportProfile p : ExportProfiles.getProfiles())
                {
                    menu.action(Icons.FILM, IKey.raw(p.getName() + " (" + p.getFormat().name() + ")"), () ->
                    {
                        this.currentProfile = p.copy();
                        ExportProfiles.setSelectedProfile(p);
                        this.applyProfileToUI();
                    });
                }
            });
        });

        this.savePreset = new UIIcon(Icons.SAVED, (b) ->
        {
            UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
                IKey.raw("Save Preset"),
                IKey.raw("Enter custom preset name:"),
                (name) ->
                {
                    if (name != null && !name.trim().isEmpty())
                    {
                        this.currentProfile.setName(name.trim());
                        this.currentProfile.setId("custom_" + StringUtils.createTimestampFilename());
                        ExportProfiles.addProfile(this.currentProfile.copy());
                        this.presetButton.label = IKey.raw("Preset: " + name.trim());
                    }
                }
            );
            UIOverlay.addOverlay(this.getContext(), prompt);
        });
        this.savePreset.tooltip(IKey.raw("Save as Custom Preset"));
    }

    private void setupTabs()
    {
        this.tabVideo = new UIButton(IKey.raw("Video"), (b) -> this.switchTab(0));
        this.tabAudio = new UIButton(IKey.raw("Audio"), (b) -> this.switchTab(1));
        this.tabFile = new UIButton(IKey.raw("File"), (b) -> this.switchTab(2));

        this.settingsContainer = new UIElement();
        this.videoTabContent = UI.scrollView(4, 8);
        this.audioTabContent = UI.scrollView(4, 8);
        this.fileTabContent = UI.scrollView(4, 8);

        this.videoTabContent.relative(this.settingsContainer).w(1F).h(1F);
        this.audioTabContent.relative(this.settingsContainer).w(1F).h(1F);
        this.fileTabContent.relative(this.settingsContainer).w(1F).h(1F);

        this.settingsContainer.add(this.videoTabContent, this.audioTabContent, this.fileTabContent);
    }

    private void setupVideoTab()
    {
        // 0. Render Range controls
        boolean hasInOut = this.filmPanel != null && this.filmPanel.cameraEditor.clips.hasInOut();
        if (hasInOut)
        {
            this.currentProfile.setRangeType(VideoExportProfile.RangeType.IN_OUT);
        }
        else if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.IN_OUT)
        {
            this.currentProfile.setRangeType(VideoExportProfile.RangeType.ENTIRE);
        }

        this.renderRangeButton = new UIButton(IKey.raw("Range: " + this.currentProfile.getRangeType().getLabel()), (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                String entireLabel = this.catalystPanel != null ? "Entire Composition" : "Entire Film";
                menu.action(Icons.FILM, IKey.raw(entireLabel), () ->
                {
                    this.currentProfile.setRangeType(VideoExportProfile.RangeType.ENTIRE);
                    this.updateRangeUI();
                });

                if (this.filmPanel != null)
                {
                    boolean inOutAvailable = this.filmPanel.cameraEditor.clips.hasInOut();
                    String inOutLabel = "In/Out Range" + (inOutAvailable ? " (" + this.filmPanel.cameraEditor.clips.getInPoint() + " - " + this.filmPanel.cameraEditor.clips.getOutPoint() + ")" : " (Not Set)");
                    if (inOutAvailable)
                    {
                        menu.action(Icons.LEFT_HANDLE, IKey.raw(inOutLabel), () ->
                        {
                            this.currentProfile.setRangeType(VideoExportProfile.RangeType.IN_OUT);
                            this.updateRangeUI();
                        });
                    }
                }

                menu.action(Icons.EDIT, IKey.raw("Custom Range"), () ->
                {
                    this.currentProfile.setRangeType(VideoExportProfile.RangeType.CUSTOM);
                    this.updateRangeUI();
                });
            });
        });

        this.rangeStartTextbox = new UITextbox(10, (str) ->
        {
            int tick = this.parseTimeToTicks(str);
            this.currentProfile.setCustomStartTick(tick);
        });

        this.rangeEndTextbox = new UITextbox(10, (str) ->
        {
            int tick = this.parseTimeToTicks(str);
            this.currentProfile.setCustomEndTick(tick);
        });

        boolean useSeconds = BBSSettings.editorSeconds.get();
        String unitLabel = useSeconds ? "Seconds" : "Ticks";

        this.rangeInputsRow = UI.row(
            UI.column(UI.label(IKey.raw("Start (" + unitLabel + ")")), this.rangeStartTextbox),
            UI.column(UI.label(IKey.raw("End (" + unitLabel + ")")), this.rangeEndTextbox)
        );

        // 1. Format circulate
        this.formatCirculate = new UICirculate((c) ->
        {
            ExportFormat format = ExportFormat.values()[c.getValue()];
            this.currentProfile.setFormat(format);
            this.refreshCodecCirculate();
            this.updateVideoVisibility();
        });
        for (ExportFormat f : ExportFormat.values())
        {
            this.formatCirculate.addLabel(f.getLabel());
        }

        // 2. Codec circulate
        this.codecCirculate = new UICirculate((c) ->
        {
            List<VideoCodec> supported = this.currentProfile.getFormat().getSupportedCodecs();
            if (c.getValue() >= 0 && c.getValue() < supported.size())
            {
                this.currentProfile.setCodec(supported.get(c.getValue()));
            }
        });
        this.refreshCodecCirculate();

        // 3. Hardware Encoder circulate
        this.hwEncoderCirculate = new UICirculate((c) ->
        {
            HardwareEncoder enc = HardwareEncoder.values()[c.getValue()];
            this.currentProfile.setHardwareEncoder(enc);
        });
        for (HardwareEncoder h : HardwareEncoder.values())
        {
            this.hwEncoderCirculate.addLabel(h.getLabel());
        }

        this.gpuDetectedLabel = UI.label(IKey.raw("GPU: " + HardwareEncoder.getDetectedGpuName())).color(Colors.LIGHTER_GRAY);

        // 4. Resolution & FPS
        int curW = BBSRendering.getVideoWidth();
        int curH = BBSRendering.getVideoHeight();
        double curFps = BBSRendering.getVideoFrameRate();

        this.widthTrackpad = new UITrackpad((val) -> this.currentProfile.setWidth(val.intValue())).limit(2, 8192).integer();
        this.widthTrackpad.setValue(this.currentProfile.getWidth() > 0 ? this.currentProfile.getWidth() : curW);

        this.heightTrackpad = new UITrackpad((val) -> this.currentProfile.setHeight(val.intValue())).limit(2, 8192).integer();
        this.heightTrackpad.setValue(this.currentProfile.getHeight() > 0 ? this.currentProfile.getHeight() : curH);

        this.fpsTrackpad = new UITrackpad((val) -> this.currentProfile.setFrameRate(val)).limit(1, 1000).integer();
        this.fpsTrackpad.setValue(this.currentProfile.getFrameRate() > 0 ? this.currentProfile.getFrameRate() : curFps);

        // 5. Rate Control & Quality
        this.rateControlCirculate = new UICirculate((c) ->
        {
            RateControl rc = RateControl.values()[c.getValue()];
            this.currentProfile.setRateControl(rc);
            this.updateVideoVisibility();
        });
        for (RateControl rc : RateControl.values())
        {
            this.rateControlCirculate.addLabel(rc.getLabel());
        }

        this.qualityTrackpad = new UITrackpad((val) -> this.currentProfile.setQuality(val.intValue())).limit(0, 51).integer();
        this.qualityTrackpad.setValue(this.currentProfile.getQuality());

        this.bitrateTrackpad = new UITrackpad((val) ->
        {
            if (this.currentProfile != null)
            {
                this.currentProfile.setBitrate(val.intValue());
            }
        }).limit(500, 200000).integer();
        this.bitrateTrackpad.setValue(this.currentProfile.getBitrate());

        this.presetCirculate = new UICirculate((c) ->
        {
            EncoderPreset p = EncoderPreset.values()[c.getValue()];
            this.currentProfile.setPreset(p);
        });
        for (EncoderPreset p : EncoderPreset.values())
        {
            this.presetCirculate.addLabel(p.getLabel());
        }

        // GIF options
        this.gifControls = new UIElement();
        this.gifFpsTrackpad = new UITrackpad((val) -> this.currentProfile.setGifFps(val.intValue())).limit(1, 60).integer();
        this.gifFpsTrackpad.setValue(this.currentProfile.getGifFps());

        this.gifDitherCirculate = new UICirculate((c) ->
        {
            String[] dithers = new String[]{"sierra2_4a", "bayer", "floyd_steinberg", "none"};
            this.currentProfile.setGifDither(dithers[c.getValue()]);
        });
        this.gifDitherCirculate.addLabel(IKey.raw("Dither: Sierra 2-4A (HQ)"));
        this.gifDitherCirculate.addLabel(IKey.raw("Dither: Bayer (Patterned)"));
        this.gifDitherCirculate.addLabel(IKey.raw("Dither: Floyd-Steinberg"));
        this.gifDitherCirculate.addLabel(IKey.raw("Dither: None"));

        this.gifColorsTrackpad = new UITrackpad((val) -> this.currentProfile.setGifMaxColors(val.intValue())).limit(2, 256).integer();
        this.gifColorsTrackpad.setValue(this.currentProfile.getGifMaxColors());

        UIElement gifRow = UI.row(
            UI.column(UI.label(IKey.raw("GIF FPS")), this.gifFpsTrackpad),
            UI.column(UI.label(IKey.raw("Dithering")), this.gifDitherCirculate),
            UI.column(UI.label(IKey.raw("Max Colors")), this.gifColorsTrackpad)
        );
        this.gifControls.add(gifRow);
        gifRow.relative(this.gifControls).w(1F).h(40);

        // Layout video tab
        UIElement resRow = UI.row(
            UI.column(UI.label(IKey.raw("Width")), this.widthTrackpad),
            UI.column(UI.label(IKey.raw("Height")), this.heightTrackpad),
            UI.column(UI.label(IKey.raw("FPS")), this.fpsTrackpad)
        );

        UIElement rateRow = UI.row(
            UI.column(UI.label(IKey.raw("Rate Control")), this.rateControlCirculate),
            UI.column(UI.label(IKey.raw("CRF / Quality (0-51)")), this.qualityTrackpad),
            UI.column(UI.label(IKey.raw("Bitrate (kbps)")), this.bitrateTrackpad)
        );

        this.exportVideoToggle = new UIToggle(IKey.raw("Export Video"), this.currentProfile.isExportVideo(), (t) ->
        {
            this.currentProfile.setExportVideo(t.getValue());
            this.validateRenderButtons();
        });

        UIElement videoCol = UI.column(
            4, 0,
            this.exportVideoToggle,
            UI.label(IKey.raw("Render Range")).color(Colors.LIGHTER_GRAY).marginTop(2), this.renderRangeButton,
            this.rangeInputsRow,
            UI.label(IKey.raw("Container Format")).color(Colors.LIGHTER_GRAY).marginTop(2), this.formatCirculate,
            UI.label(IKey.raw("Video Codec")).color(Colors.LIGHTER_GRAY).marginTop(2), this.codecCirculate,
            UI.label(IKey.raw("Hardware Accelerator")).color(Colors.LIGHTER_GRAY).marginTop(2), this.hwEncoderCirculate,
            this.gpuDetectedLabel.marginTop(1),
            resRow.marginTop(4),
            rateRow.marginTop(4),
            UI.label(IKey.raw("Encoder Speed Preset")).color(Colors.LIGHTER_GRAY).marginTop(2), this.presetCirculate,
            this.gifControls.marginTop(4)
        );

        videoCol.relative(this.videoTabContent).w(1F);
        this.videoTabContent.add(videoCol);
    }

    private void setupAudioTab()
    {
        this.exportAudioToggle = new UIToggle(IKey.raw("Export Audio"), this.currentProfile.isExportAudio(), (t) ->
        {
            this.currentProfile.setExportAudio(t.getValue());
            this.validateRenderButtons();
        });

        this.availableAudioCodecs.clear();
        for (AudioCodec ac : AudioCodec.values())
        {
            if (ac != AudioCodec.NONE)
            {
                this.availableAudioCodecs.add(ac);
            }
        }

        this.audioCodecCirculate = new UICirculate((c) ->
        {
            if (c.getValue() >= 0 && c.getValue() < this.availableAudioCodecs.size())
            {
                AudioCodec ac = this.availableAudioCodecs.get(c.getValue());
                this.currentProfile.setAudioCodec(ac);
            }
        });
        for (AudioCodec ac : this.availableAudioCodecs)
        {
            this.audioCodecCirculate.addLabel(ac.getLabel());
        }

        this.audioBitrateCirculate = new UICirculate((c) ->
        {
            int[] bitrates = new int[]{128, 192, 256, 320};
            this.currentProfile.setAudioBitrate(bitrates[c.getValue()]);
        });
        this.audioBitrateCirculate.addLabel(IKey.raw("128 kbps"));
        this.audioBitrateCirculate.addLabel(IKey.raw("192 kbps (Standard)"));
        this.audioBitrateCirculate.addLabel(IKey.raw("256 kbps (High Quality)"));
        this.audioBitrateCirculate.addLabel(IKey.raw("320 kbps (Studio Master)"));
        this.audioBitrateCirculate.setValue(3);

        this.captureMinecraftSoundsToggle = new UIToggle(IKey.raw("Capture In-Game Minecraft Sounds"), BBSSettings.videoExportMinecraftSounds.get(), (t) ->
        {
            BBSSettings.videoExportMinecraftSounds.set(t.getValue());
        });

        UIElement audioCol;
        if (this.catalystPanel != null)
        {
            audioCol = UI.column(
                6, 0,
                this.exportAudioToggle,
                UI.label(IKey.raw("Audio Codec")).color(Colors.LIGHTER_GRAY).marginTop(4), this.audioCodecCirculate,
                UI.label(IKey.raw("Audio Bitrate")).color(Colors.LIGHTER_GRAY).marginTop(4), this.audioBitrateCirculate
            );
        }
        else
        {
            audioCol = UI.column(
                6, 0,
                this.exportAudioToggle,
                UI.label(IKey.raw("Audio Codec")).color(Colors.LIGHTER_GRAY).marginTop(4), this.audioCodecCirculate,
                UI.label(IKey.raw("Audio Bitrate")).color(Colors.LIGHTER_GRAY).marginTop(4), this.audioBitrateCirculate,
                this.captureMinecraftSoundsToggle.marginTop(8)
            );
        }

        audioCol.relative(this.audioTabContent).w(1F);
        this.audioTabContent.add(audioCol);
    }

    private void setupFileTab()
    {
        String defaultName = "export";
        if (this.filmPanel != null && this.filmPanel.getData() != null)
        {
            defaultName = this.filmPanel.getData().getId();
        }
        else if (this.catalystPanel != null && this.catalystPanel.getActiveComposition() != null)
        {
            defaultName = this.catalystPanel.getActiveComposition().name.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        else
        {
            defaultName = StringUtils.createTimestampFilename();
        }

        this.filenameTextbox = new UITextbox((t) -> {});
        this.filenameTextbox.setText(defaultName);

        File movieFolder = BBSRendering.getVideoFolder();
        this.outputFolderTextbox = new UITextbox((t) -> {});
        this.outputFolderTextbox.setText(movieFolder.getAbsolutePath());

        this.browseFolderButton = new UIButton(IKey.raw("Open Videos Folder"), (b) ->
        {
            String custom = this.outputFolderTextbox.getText();
            File target = custom != null && !custom.trim().isEmpty() ? new File(custom.replace("\"", "").trim()) : BBSRendering.getVideoFolder();
            if (!target.exists()) target.mkdirs();
            UIUtils.openFolder(target);
        });

        UIElement fileCol = UI.column(
            6, 0,
            UI.label(IKey.raw("Export Filename")).color(Colors.LIGHTER_GRAY), this.filenameTextbox,
            UI.label(IKey.raw("Output Folder")).color(Colors.LIGHTER_GRAY).marginTop(4), this.outputFolderTextbox,
            this.browseFolderButton.marginTop(4)
        );

        fileCol.relative(this.fileTabContent).w(1F);
        this.fileTabContent.add(fileCol);
    }

    private void setupQueuePanel()
    {
        this.queueContainer = new UIElement();

        UILabel queueLabel = UI.label(IKey.raw("Render Queue")).color(Colors.WHITE);
        this.clearQueueButton = new UIIcon(Icons.CLOSE, (b) -> RenderQueue.clear());
        this.clearQueueButton.tooltip(IKey.raw("Clear Completed / All"));

        this.addToQueueButton = new UIButton(IKey.raw("+ Add to Queue"), (b) -> this.addCurrentToQueue());

        this.jobList = new UIRenderJobList((list) -> {});
        this.jobList.background();
        this.jobList.updateList();

        this.renderNowButton = new UIButton(IKey.raw("Render Now"), (b) -> this.startImmediateRender());
        this.renderAllButton = new UIButton(IKey.raw("Render All Jobs"), (b) -> this.startQueueRender());

        this.topQueueRow = UI.row(queueLabel, this.clearQueueButton);

        this.queueContainer.add(this.topQueueRow, this.addToQueueButton, this.jobList, this.renderNowButton, this.renderAllButton);
    }

    private void refreshCodecCirculate()
    {
        this.codecCirculate.getLabels().clear();
        for (VideoCodec c : this.currentProfile.getFormat().getSupportedCodecs())
        {
            this.codecCirculate.addLabel(c.getLabel());
        }
        int index = this.currentProfile.getFormat().getSupportedCodecs().indexOf(this.currentProfile.getCodec());
        this.codecCirculate.setValue(Math.max(0, index));
    }

    private void updateVideoVisibility()
    {
        boolean isGif = this.currentProfile.getFormat().isGif();
        this.gifControls.setVisible(isGif);
        this.hwEncoderCirculate.setEnabled(!isGif);
        this.rateControlCirculate.setEnabled(!isGif);
        this.qualityTrackpad.setEnabled(!isGif && this.currentProfile.getRateControl() == RateControl.CRF);
        this.bitrateTrackpad.setEnabled(!isGif && (this.currentProfile.getRateControl() == RateControl.CBR || this.currentProfile.getRateControl() == RateControl.VBR));
    }

    private void applyProfileToUI()
    {
        this.presetButton.label = IKey.raw("Preset: " + this.currentProfile.getName());
        this.formatCirculate.setValue(this.currentProfile.getFormat().ordinal());
        this.refreshCodecCirculate();
        this.hwEncoderCirculate.setValue(this.currentProfile.getHardwareEncoder().ordinal());

        if (this.currentProfile.getWidth() > 0) this.widthTrackpad.setValue(this.currentProfile.getWidth());
        if (this.currentProfile.getHeight() > 0) this.heightTrackpad.setValue(this.currentProfile.getHeight());
        if (this.currentProfile.getFrameRate() > 0) this.fpsTrackpad.setValue(this.currentProfile.getFrameRate());

        this.rateControlCirculate.setValue(this.currentProfile.getRateControl().ordinal());
        this.qualityTrackpad.setValue(this.currentProfile.getQuality());
        this.bitrateTrackpad.setValue(this.currentProfile.getBitrate());
        this.presetCirculate.setValue(this.currentProfile.getPreset().ordinal());

        this.gifFpsTrackpad.setValue(this.currentProfile.getGifFps());
        this.gifColorsTrackpad.setValue(this.currentProfile.getGifMaxColors());

        if (this.exportVideoToggle != null) this.exportVideoToggle.setValue(this.currentProfile.isExportVideo());
        if (this.exportAudioToggle != null) this.exportAudioToggle.setValue(this.currentProfile.isExportAudio());
        int acIdx = this.availableAudioCodecs.indexOf(this.currentProfile.getAudioCodec());
        this.audioCodecCirculate.setValue(Math.max(0, acIdx));

        if (this.audioBitrateCirculate != null)
        {
            int ab = this.currentProfile.getAudioBitrate();
            int bitIdx = 3;

            if (ab <= 128)
            {
                bitIdx = 0;
            }
            else if (ab <= 192)
            {
                bitIdx = 1;
            }
            else if (ab <= 256)
            {
                bitIdx = 2;
            }

            this.audioBitrateCirculate.setValue(bitIdx);
        }

        this.updateRangeUI();
        this.updateVideoVisibility();
        this.validateRenderButtons();
    }

    public void validateRenderButtons()
    {
        boolean valid = this.currentProfile != null && (this.currentProfile.isExportVideo() || this.currentProfile.isExportAudio());
        if (this.renderNowButton != null)
        {
            this.renderNowButton.setEnabled(valid);
            if (!valid) this.renderNowButton.tooltip(IKey.raw("En az bir akış seçilmeli (Video veya Ses)!"));
            else this.renderNowButton.removeTooltip();
        }
        if (this.addToQueueButton != null)
        {
            this.addToQueueButton.setEnabled(valid);
            if (!valid) this.addToQueueButton.tooltip(IKey.raw("En az bir akış seçilmeli (Video veya Ses)!"));
            else this.addToQueueButton.removeTooltip();
        }
        if (this.renderAllButton != null)
        {
            this.renderAllButton.setEnabled(valid);
            if (!valid) this.renderAllButton.tooltip(IKey.raw("En az bir akış seçilmeli (Video veya Ses)!"));
            else this.renderAllButton.removeTooltip();
        }
    }

    private void switchTab(int tab)
    {
        this.currentTab = tab;
        this.videoTabContent.setVisible(tab == 0);
        this.audioTabContent.setVisible(tab == 1);
        this.fileTabContent.setVisible(tab == 2);
    }

    private void syncUIToProfile()
    {
        if (this.currentProfile == null)
        {
            return;
        }

        // Bitrate sync
        try
        {
            String bTxt = this.bitrateTrackpad.textbox.getText().trim();
            if (!bTxt.isEmpty())
            {
                this.currentProfile.setBitrate((int) Math.max(500, Math.min(200000, Double.parseDouble(bTxt))));
            }
            else
            {
                this.currentProfile.setBitrate((int) this.bitrateTrackpad.getValue());
            }
        }
        catch (Exception ignored)
        {
            this.currentProfile.setBitrate((int) this.bitrateTrackpad.getValue());
        }

        // Resolution & FPS sync
        try
        {
            String wTxt = this.widthTrackpad.textbox.getText().trim();
            if (!wTxt.isEmpty()) this.currentProfile.setWidth((int) Double.parseDouble(wTxt));
            else this.currentProfile.setWidth((int) this.widthTrackpad.getValue());
        }
        catch (Exception ignored)
        {
            this.currentProfile.setWidth((int) this.widthTrackpad.getValue());
        }

        try
        {
            String hTxt = this.heightTrackpad.textbox.getText().trim();
            if (!hTxt.isEmpty()) this.currentProfile.setHeight((int) Double.parseDouble(hTxt));
            else this.currentProfile.setHeight((int) this.heightTrackpad.getValue());
        }
        catch (Exception ignored)
        {
            this.currentProfile.setHeight((int) this.heightTrackpad.getValue());
        }

        try
        {
            String fpsTxt = this.fpsTrackpad.textbox.getText().trim();
            if (!fpsTxt.isEmpty()) this.currentProfile.setFrameRate(Double.parseDouble(fpsTxt));
            else this.currentProfile.setFrameRate(this.fpsTrackpad.getValue());
        }
        catch (Exception ignored)
        {
            this.currentProfile.setFrameRate(this.fpsTrackpad.getValue());
        }

        try
        {
            String qTxt = this.qualityTrackpad.textbox.getText().trim();
            if (!qTxt.isEmpty()) this.currentProfile.setQuality((int) Double.parseDouble(qTxt));
            else this.currentProfile.setQuality((int) this.qualityTrackpad.getValue());
        }
        catch (Exception ignored)
        {
            this.currentProfile.setQuality((int) this.qualityTrackpad.getValue());
        }

        if (this.currentProfile.getFormat().isGif())
        {
            this.currentProfile.setGifFps((int) this.gifFpsTrackpad.getValue());
            this.currentProfile.setGifMaxColors((int) this.gifColorsTrackpad.getValue());
        }

        if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.CUSTOM && this.rangeStartTextbox != null && this.rangeEndTextbox != null)
        {
            this.currentProfile.setCustomStartTick(this.parseTimeToTicks(this.rangeStartTextbox.getText()));
            this.currentProfile.setCustomEndTick(this.parseTimeToTicks(this.rangeEndTextbox.getText()));
        }
    }

    private String formatTicksToDisplay(int ticks)
    {
        if (BBSSettings.editorSeconds.get())
        {
            return String.format(java.util.Locale.ROOT, "%.2fs", ticks / 20.0);
        }
        return String.valueOf(ticks);
    }

    private int parseTimeToTicks(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return 0;
        }
        String clean = text.trim().toLowerCase().replace("s", "");
        try
        {
            if (BBSSettings.editorSeconds.get())
            {
                double seconds = Double.parseDouble(clean);
                return (int) Math.round(seconds * 20.0);
            }
            return Integer.parseInt(clean);
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    private void updateRangeUI()
    {
        VideoExportProfile.RangeType type = this.currentProfile.getRangeType();
        String label = "Range: " + type.getLabel();
        if (type == VideoExportProfile.RangeType.IN_OUT && this.filmPanel != null && this.filmPanel.cameraEditor.clips.hasInOut())
        {
            label += " (" + this.filmPanel.cameraEditor.clips.getInPoint() + " - " + this.filmPanel.cameraEditor.clips.getOutPoint() + ")";
        }
        if (this.renderRangeButton != null)
        {
            this.renderRangeButton.label = IKey.raw(label);
        }

        boolean isCustom = type == VideoExportProfile.RangeType.CUSTOM;
        if (this.rangeInputsRow != null)
        {
            this.rangeInputsRow.setVisible(isCustom);
        }

        if (isCustom && this.rangeStartTextbox != null && this.rangeEndTextbox != null)
        {
            int defaultDuration = 200;
            if (this.filmPanel != null && this.filmPanel.getData() != null)
            {
                defaultDuration = this.filmPanel.getData().camera.calculateDuration();
            }
            else if (this.catalystPanel != null && this.catalystPanel.getActiveComposition() != null)
            {
                defaultDuration = this.catalystPanel.getActiveComposition().duration;
            }

            int start = this.currentProfile.getCustomStartTick();
            int end = this.currentProfile.getCustomEndTick() > 0 ? this.currentProfile.getCustomEndTick() : defaultDuration;
            this.rangeStartTextbox.setText(this.formatTicksToDisplay(start));
            this.rangeEndTextbox.setText(this.formatTicksToDisplay(end));
        }

        if (this.videoTabContent != null)
        {
            this.videoTabContent.resize();
        }
    }

    private void addCurrentToQueue()
    {
        this.syncUIToProfile();

        if (!this.currentProfile.isExportVideo() && !this.currentProfile.isExportAudio())
        {
            mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Deliver] En az bir akış (Video veya Ses) seçilmeli!");
            return;
        }

        String filmId = "Film";
        int duration = 100;

        if (this.filmPanel != null && this.filmPanel.getData() != null)
        {
            filmId = this.filmPanel.getData().getId();
            duration = this.filmPanel.getData().camera.calculateDuration();

            if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.IN_OUT && this.filmPanel.cameraEditor.clips.hasInOut())
            {
                duration = Math.max(1, this.filmPanel.cameraEditor.clips.getOutPoint() - this.filmPanel.cameraEditor.clips.getInPoint());
            }
            else if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.CUSTOM)
            {
                int start = this.currentProfile.getCustomStartTick();
                int end = this.currentProfile.getCustomEndTick() > 0 ? this.currentProfile.getCustomEndTick() : duration;
                duration = Math.max(1, end - start);
            }
        }
        else if (this.catalystPanel != null)
        {
            CatalystComposition comp = this.catalystPanel.getActiveComposition();
            filmId = comp != null ? "catalyst:" + comp.name : "Catalyst";
            duration = comp != null ? Math.max(1, comp.duration) : 100;

            if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.CUSTOM)
            {
                int start = this.currentProfile.getCustomStartTick();
                int end = this.currentProfile.getCustomEndTick() > 0 ? this.currentProfile.getCustomEndTick() : duration;
                duration = Math.max(1, end - start);
            }
        }

        String filename = this.filenameTextbox.getText();
        if (filename.isEmpty())
        {
            String base = filmId.startsWith("catalyst:") ? filmId.substring(9) : filmId;
            filename = base.replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + StringUtils.createTimestampFilename();
        }

        File folder = new File(this.outputFolderTextbox.getText().replace("\"", "").trim());
        RenderJob job = new RenderJob(filmId, filename, this.currentProfile, folder, filename, duration);
        RenderQueue.addJob(job);
        this.jobList.updateList();
    }

    private void onQueueUpdated()
    {
        this.jobList.updateList();
    }

    /**
     * Starts rendering the full queue sequentially.
     */
    private void startQueueRender()
    {
        this.syncUIToProfile();

        if (!this.currentProfile.isExportVideo() && !this.currentProfile.isExportAudio())
        {
            mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Deliver] En az bir akış (Video veya Ses) seçilmeli!");
            return;
        }

        if (RenderQueue.getJobs().isEmpty())
        {
            this.addCurrentToQueue();
        }

        RenderQueue.setProcessingQueue(true);
        this.close();

        this.processNextQueueJob();
    }

    public void processNextQueueJob()
    {
        RenderJob job = RenderQueue.getNextPendingJob();
        if (job == null)
        {
            RenderQueue.setProcessingQueue(false);
            return;
        }

        boolean isCatalystJob = (job.getFilmId() != null && job.getFilmId().startsWith("catalyst:")) || this.catalystPanel != null;

        if (isCatalystJob && this.catalystPanel != null)
        {
            RenderQueue.setActiveJob(job);
            job.setStatus(RenderJob.Status.RENDERING);
            if (this.jobList != null)
            {
                this.jobList.updateList();
            }

            VideoExportProfile prof = job.getProfile();
            File folder = job.getOutputFolder();
            if (folder != null && !folder.exists()) folder.mkdirs();
            File targetOut = new File(folder, prof.getFormat().getOutputPattern(job.getFilename()));

            this.catalystPanel.startExportFromProfile(prof, targetOut, job.getDuration(), job, () ->
            {
                if (RenderQueue.isProcessingQueue())
                {
                    this.processNextQueueJob();
                }
            });
            return;
        }

        if (this.filmPanel == null)
        {
            RenderQueue.setProcessingQueue(false);
            return;
        }

        RenderQueue.setActiveJob(job);
        this.filmPanel.recorder.setJob(job);

        // Apply profile settings
        VideoExportProfile prof = job.getProfile();
        int width = prof.getWidth() > 0 ? prof.getWidth() : BBSRendering.getVideoWidth();
        int height = prof.getHeight() > 0 ? prof.getHeight() : BBSRendering.getVideoHeight();

        if (width % 2 != 0) width++;
        if (height % 2 != 0) height++;

        UIFilmPanel.applyExportSizeToBBS(width, height);
        final int finalQueueW = width;
        final int finalQueueH = height;
        BBSRendering.scheduleAfterNextExportFrame(() ->
        {
            try
            {
                this.filmPanel.recorder.setJob(job);
                this.filmPanel.recorder.setFinishedListener((cancelled) ->
                {
                    job.setStatus(cancelled ? RenderJob.Status.CANCELLED : RenderJob.Status.COMPLETED);
                    RenderJob active = RenderQueue.getActiveJob();
                    if (active == job)
                    {
                        RenderQueue.setActiveJob(null);
                    }
                    if (this.jobList != null)
                    {
                        this.jobList.updateList();
                    }
                    if (RenderQueue.isProcessingQueue() && !cancelled)
                    {
                        this.processNextQueueJob();
                    }
                });

                this.filmPanel.recorder.startRecording(
                    job.getDuration(),
                    BBSRendering.getTexture().id,
                    finalQueueW,
                    finalQueueH,
                    prof,
                    job.getFilename()
                );

                if (!this.filmPanel.recorder.isExporting())
                {
                    mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[BBS] Render başlatılamadı: Kuyruk oturumu başlatılamadı!");
                    job.setStatus(RenderJob.Status.CANCELLED);
                    if (RenderQueue.getActiveJob() == job)
                    {
                        RenderQueue.setActiveJob(null);
                    }
                    if (RenderQueue.isProcessingQueue())
                    {
                        this.processNextQueueJob();
                    }
                }
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[BBS] Render başlatılamadı: " + (t.getMessage() != null ? t.getMessage() : t.toString()));
                job.setStatus(RenderJob.Status.CANCELLED);
                if (RenderQueue.getActiveJob() == job)
                {
                    RenderQueue.setActiveJob(null);
                }
                if (RenderQueue.isProcessingQueue())
                {
                    this.processNextQueueJob();
                }
            }
        });
    }

    /**
     * Immediately renders the current film/composition with currently selected profile.
     */
    private void startImmediateRender()
    {
        this.syncUIToProfile();

        if (!this.currentProfile.isExportVideo() && !this.currentProfile.isExportAudio())
        {
            mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Deliver] En az bir akış (Video veya Ses) seçilmeli!");
            return;
        }

        this.close();

        if (this.catalystPanel != null)
        {
            CatalystComposition comp = this.catalystPanel.getActiveComposition();
            int duration = comp != null ? Math.max(1, comp.duration) : 100;
            if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.CUSTOM && this.currentProfile.getCustomEndTick() > 0)
            {
                int start = this.currentProfile.getCustomStartTick();
                int end = this.currentProfile.getCustomEndTick();
                duration = Math.max(1, end - start);
            }

            String rawFilename = this.filenameTextbox.getText().trim();
            final String finalFilename = rawFilename.isEmpty()
                ? (comp != null ? comp.name.replaceAll("[^a-zA-Z0-9._-]", "_") : "catalyst") + "_" + StringUtils.createTimestampFilename()
                : rawFilename;

            File folder = new File(this.outputFolderTextbox.getText().replace("\"", "").trim());
            if (!folder.exists()) folder.mkdirs();
            File targetOut = new File(folder, this.currentProfile.getFormat().getOutputPattern(finalFilename));

            String compFilmId = comp != null ? "catalyst:" + comp.name : "Catalyst";
            RenderJob matchingJob = null;
            for (RenderJob j : RenderQueue.getJobs())
            {
                if (j.getStatus() == RenderJob.Status.PENDING)
                {
                    if (j.getFilename().equalsIgnoreCase(finalFilename)
                        || j.getFilename().equalsIgnoreCase(rawFilename)
                        || j.getTitle().equalsIgnoreCase(finalFilename)
                        || j.getTitle().equalsIgnoreCase(rawFilename)
                        || (j.getFilmId() != null && j.getFilmId().equals(compFilmId)))
                    {
                        matchingJob = j;
                        break;
                    }
                }
            }
            if (matchingJob != null)
            {
                matchingJob.setStatus(RenderJob.Status.RENDERING);
                RenderQueue.setActiveJob(matchingJob);
            }

            this.catalystPanel.startExportFromProfile(this.currentProfile, targetOut, duration, matchingJob, null);
            return;
        }

        if (this.filmPanel == null || this.filmPanel.getData() == null) return;

        int duration = this.filmPanel.getData().camera.calculateDuration();
        if (this.currentProfile.getRangeType() == VideoExportProfile.RangeType.CUSTOM && this.currentProfile.getCustomEndTick() > duration)
        {
            duration = this.currentProfile.getCustomEndTick();
        }
        final int finalDuration = duration;
        int width = this.currentProfile.getWidth() > 0 ? this.currentProfile.getWidth() : BBSRendering.getVideoWidth();
        int height = this.currentProfile.getHeight() > 0 ? this.currentProfile.getHeight() : BBSRendering.getVideoHeight();
        if (width % 2 != 0) width++;
        if (height % 2 != 0) height++;

        String rawFilename = this.filenameTextbox.getText().trim();
        final String finalFilename = rawFilename.isEmpty()
            ? this.filmPanel.getData().getId() + "_" + StringUtils.createTimestampFilename()
            : rawFilename;

        String currentFilmId = this.filmPanel.getData().getId();

        RenderJob matchingJob = null;
        for (RenderJob j : RenderQueue.getJobs())
        {
            if (j.getStatus() == RenderJob.Status.PENDING)
            {
                if (j.getFilename().equalsIgnoreCase(finalFilename)
                    || j.getFilename().equalsIgnoreCase(rawFilename)
                    || j.getTitle().equalsIgnoreCase(finalFilename)
                    || j.getTitle().equalsIgnoreCase(rawFilename)
                    || (j.getFilmId() != null && j.getFilmId().equals(currentFilmId)))
                {
                    matchingJob = j;
                    break;
                }
            }
        }
        if (matchingJob != null)
        {
            matchingJob.setStatus(RenderJob.Status.RENDERING);
            RenderQueue.setActiveJob(matchingJob);
            this.filmPanel.recorder.setJob(matchingJob);
        }

        UIFilmPanel.applyExportSizeToBBS(width, height);
        final int finalW = width;
        final int finalH = height;
        final RenderJob immediateJob = matchingJob;
        BBSRendering.scheduleAfterNextExportFrame(() ->
        {
            try
            {
                if (immediateJob != null)
                {
                    this.filmPanel.recorder.setJob(immediateJob);
                }

                this.filmPanel.recorder.setFinishedListener((cancelled) ->
                {
                    if (immediateJob != null)
                    {
                        immediateJob.setStatus(cancelled ? RenderJob.Status.CANCELLED : RenderJob.Status.COMPLETED);
                    }
                    RenderJob active = RenderQueue.getActiveJob();
                    if (active != null)
                    {
                        active.setStatus(cancelled ? RenderJob.Status.CANCELLED : RenderJob.Status.COMPLETED);
                        RenderQueue.setActiveJob(null);
                    }
                    if (this.jobList != null)
                    {
                        this.jobList.updateList();
                    }
                });

                this.filmPanel.recorder.startRecording(
                    finalDuration,
                    BBSRendering.getTexture().id,
                    finalW,
                    finalH,
                    this.currentProfile,
                    finalFilename
                );

                if (!this.filmPanel.recorder.isExporting())
                {
                    mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[BBS] Render başlatılamadı: Oturum başlatılamadı (isExporting == false)!");
                    if (immediateJob != null)
                    {
                        immediateJob.setStatus(RenderJob.Status.CANCELLED);
                    }
                    if (RenderQueue.getActiveJob() != null)
                    {
                        RenderQueue.getActiveJob().setStatus(RenderJob.Status.CANCELLED);
                        RenderQueue.setActiveJob(null);
                    }
                }
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[BBS] Render başlatılamadı: " + (t.getMessage() != null ? t.getMessage() : t.toString()));
                if (immediateJob != null)
                {
                    immediateJob.setStatus(RenderJob.Status.CANCELLED);
                }
                if (RenderQueue.getActiveJob() != null)
                {
                    RenderQueue.getActiveJob().setStatus(RenderJob.Status.CANCELLED);
                    RenderQueue.setActiveJob(null);
                }
            }
        });
    }

    @Override
    public void resize()
    {
        int w = this.content.area.w;
        int h = this.content.area.h;

        // Top preset bar
        int topH = 22;
        int presetW = Math.min(280, Math.max(160, w - 40));
        this.presetButton.relative(this.content).xy(6, 4).w(presetW).h(topH);
        this.savePreset.relative(this.content).xy(presetW + 10, 4).w(topH).h(topH);

        // Split Left (Settings) and Right (Queue)
        int leftW = Math.max(260, (int) (w * 0.55F));
        int rightW = w - leftW - 18;
        int mainY = topH + 12;
        int mainH = h - mainY - 6;

        // Tab buttons
        int tabW = 75;
        this.tabVideo.relative(this.content).xy(6, mainY).w(tabW).h(20);
        this.tabAudio.relative(this.content).xy(6 + tabW + 2, mainY).w(tabW).h(20);
        this.tabFile.relative(this.content).xy(6 + (tabW + 2) * 2, mainY).w(tabW).h(20);

        // Settings Container and its tab scrollviews
        this.settingsContainer.relative(this.content).xy(6, mainY + 24).w(leftW).h(mainH - 24);
        this.videoTabContent.relative(this.settingsContainer).w(1F).h(1F);
        this.audioTabContent.relative(this.settingsContainer).w(1F).h(1F);
        this.fileTabContent.relative(this.settingsContainer).w(1F).h(1F);

        // Queue Container and its children
        this.queueContainer.relative(this.content).xy(leftW + 12, mainY).w(rightW).h(mainH);
        this.topQueueRow.relative(this.queueContainer).xy(0, 0).w(1F).h(20);
        this.addToQueueButton.relative(this.queueContainer).xy(0, 24).w(1F).h(22);
        this.jobList.relative(this.queueContainer).xy(0, 50).w(1F).h(1F, -104);
        this.renderNowButton.relative(this.queueContainer).x(0).y(1F, -48).w(1F).h(22);
        this.renderAllButton.relative(this.queueContainer).x(0).y(1F, -24).w(1F).h(22);

        super.resize();
    }

    @Override
    public void render(UIContext context)
    {
        // Highlight active sub-tab button
        int activeCol = BBSSettings.primaryColor.get();
        int normCol = Colors.LIGHTEST_GRAY;
        this.tabVideo.textColor = this.currentTab == 0 ? activeCol : normCol;
        this.tabAudio.textColor = this.currentTab == 1 ? activeCol : normCol;
        this.tabFile.textColor = this.currentTab == 2 ? activeCol : normCol;

        // Draw vertical hairline divider between settings and queue
        int divX = this.settingsContainer.area.ex() + 4;
        context.batcher.box(divX, this.settingsContainer.area.y, divX + 1, this.settingsContainer.area.ey(), Colors.A25 | Colors.GRAY);

        super.render(context);
    }
}
