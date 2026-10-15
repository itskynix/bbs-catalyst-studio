package mchorse.bbs_mod.ui.dashboard.panels.motion;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Locale;

/**
 * Edit the settings of the scene that is open: frame rate, duration and render resolution.
 * Changing the frame rate keeps the duration in seconds; the frame count is recomputed.
 */
public class UIMMotionProjectSettingsDialog extends UIOverlayPanel
{
    public final UIMMotionPanel panel;

    private final UITrackpad fpsInput;
    private final UITrackpad durationSecondsInput;
    private final UITrackpad widthInput;
    private final UITrackpad heightInput;
    private final UILabel summary;

    public UIMMotionProjectSettingsDialog(UIMMotionPanel panel)
    {
        super(UIKeys.MMOTION_PROJECT_SETTINGS);

        this.panel = panel;

        UIMMotionPanel.MotionScene scene = panel.currentScene;
        int fps = scene.fps;

        UILabel nameLabel = new UILabel(UIKeys.MMOTION_SCENE_LABEL.format(scene.name), Colors.WHITE);
        nameLabel.labelAnchor(0, 0.5F).h(14);

        UILabel fpsLabel = UI.label(UIKeys.CATALYST_PROJECTS_FPS);
        fpsLabel.labelAnchor(0, 0.5F).h(14);
        UILabel durationLabel = UI.label(UIKeys.MMOTION_DURATION_SECONDS);
        durationLabel.labelAnchor(0, 0.5F).h(14);

        this.fpsInput = new UITrackpad((v) -> this.updateSummary());
        this.fpsInput.limit(1, 240, true);
        this.fpsInput.setValue(fps);
        this.fpsInput.h(20);

        this.durationSecondsInput = new UITrackpad((v) -> this.updateSummary());
        this.durationSecondsInput.limit(0.05, 3600.0);
        this.durationSecondsInput.setValue(Math.round(scene.duration / (double) fps * 100D) / 100D);
        this.durationSecondsInput.h(20);

        this.summary = new UILabel(IKey.raw(""), Colors.GRAY);
        this.summary.labelAnchor(0, 0.5F).h(12);

        UILabel resLabel = UI.label(UIKeys.CATALYST_PROJECTS_RESOLUTION);
        resLabel.labelAnchor(0, 0.5F).h(14);

        this.widthInput = new UITrackpad((v) -> {});
        this.widthInput.limit(16, 7680, true);
        this.widthInput.setValue(scene.width);
        this.widthInput.h(20);

        this.heightInput = new UITrackpad((v) -> {});
        this.heightInput.limit(16, 4320, true);
        this.heightInput.setValue(scene.height);
        this.heightInput.h(20);

        UIButton p720 = new UIButton(IKey.raw("720p"), (b) -> this.setResolution(1280, 720));
        UIButton p1080 = new UIButton(IKey.raw("1080p"), (b) -> this.setResolution(1920, 1080));
        UIButton p4k = new UIButton(IKey.raw("4K"), (b) -> this.setResolution(3840, 2160));
        UIButton pShorts = new UIButton(IKey.raw("9:16"), (b) -> this.setResolution(1080, 1920));

        UIButton apply = new UIButton(UIKeys.MMOTION_APPLY, (b) -> this.apply()).color(0xFF3B82F6, 0xFF60A5FA);
        UIButton close = new UIButton(UIKeys.GENERAL_CLOSE, (b) -> this.close());

        UIElement form = UI.column(
            5,
            0,
            nameLabel,
            UI.row(5, 0, 14, fpsLabel, durationLabel).h(14),
            UI.row(5, 0, 20, this.fpsInput, this.durationSecondsInput).h(20),
            this.summary,
            resLabel.marginTop(2),
            UI.row(5, 0, 20, this.widthInput, this.heightInput).h(20),
            UI.row(5, 0, 20, p720, p1080, p4k, pShorts).h(20),
            UI.row(5, 0, 20, apply, close).h(20).marginTop(6)
        );
        form.relative(this.content).xy(16, 6).w(1F, -32);

        this.content.add(form);
        this.updateSummary();
    }

    @Override
    public int getPreferredWidth()
    {
        return 360;
    }

    private void setResolution(int w, int h)
    {
        this.widthInput.setValue(w);
        this.heightInput.setValue(h);
    }

    private int frames()
    {
        return Math.max(1, (int) Math.round(this.durationSecondsInput.getValue() * (int) this.fpsInput.getValue()));
    }

    private void updateSummary()
    {
        if (this.summary != null && this.fpsInput != null && this.durationSecondsInput != null)
        {
            this.summary.label = UIKeys.MMOTION_FRAMES_SUMMARY.format(this.frames(), (int) this.fpsInput.getValue());
        }
    }

    private void apply()
    {
        this.panel.applyProjectSettings((int) this.fpsInput.getValue(), this.frames(), (int) this.widthInput.getValue(), (int) this.heightInput.getValue());
        this.close();
    }
}
