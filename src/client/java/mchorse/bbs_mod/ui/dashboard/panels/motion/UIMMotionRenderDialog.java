package mchorse.bbs_mod.ui.dashboard.panels.motion;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Render the open scene to PNG files at the project resolution: the current frame only, or a
 * frame range as an image sequence. Output lands in {@code motion_scenes/renders/}.
 */
public class UIMMotionRenderDialog extends UIOverlayPanel
{
    public final UIMMotionPanel panel;

    private final UITrackpad startInput;
    private final UITrackpad endInput;
    private final UIToggle transparentToggle;
    private final UICirculate formatSelector;

    public UIMMotionRenderDialog(UIMMotionPanel panel)
    {
        super(UIKeys.MMOTION_RENDER_TITLE);

        this.panel = panel;

        UIMMotionPanel.MotionScene scene = panel.currentScene;

        UILabel info = new UILabel(IKey.raw(scene.width + " x " + scene.height + " • " + scene.fps + " FPS"), Colors.WHITE);
        info.labelAnchor(0, 0.5F).h(14);

        UIButton edit = new UIButton(UIKeys.MMOTION_RENDER_EDIT_SETTINGS, (b) ->
        {
            this.close();
            panel.openProjectSettings();
        });

        UILabel rangeLabel = UI.label(UIKeys.MMOTION_RENDER_FRAME_RANGE);
        rangeLabel.labelAnchor(0, 0.5F).h(14);

        this.startInput = new UITrackpad((v) -> {});
        this.startInput.limit(0, panel.maxFrames, true);
        this.startInput.setValue(0);
        this.startInput.h(20);

        this.endInput = new UITrackpad((v) -> {});
        this.endInput.limit(0, panel.maxFrames, true);
        this.endInput.setValue(panel.maxFrames);
        this.endInput.h(20);

        this.formatSelector = new UICirculate((b) -> {});
        this.formatSelector.addLabel(IKey.raw("PNG Sequence"));
        this.formatSelector.addLabel(IKey.raw("MOV (ProRes 4444 / Transparent)"));
        this.formatSelector.addLabel(IKey.raw("MOV (QuickTime Animation / Alpha)"));
        this.formatSelector.h(20);

        /* Transparent background on: .mov (alpha channel video) is the recommended format */
        this.transparentToggle = new UIToggle(UIKeys.MMOTION_RENDER_TRANSPARENT, false, (b) ->
        {
            if (b.getValue() && this.formatSelector.getValue() == 0)
            {
                this.formatSelector.setValue(1);
            }
        });

        UIButton frame = new UIButton(UIKeys.MMOTION_RENDER_CURRENT, (b) ->
        {
            int f = panel.currentFrame;

            this.close();
            panel.startRender(f, f, this.transparentToggle.getValue());
        });

        UIButton sequence = new UIButton(UIKeys.MMOTION_RENDER_ANIMATION, (b) ->
        {
            int start = (int) this.startInput.getValue();
            int end = (int) this.endInput.getValue();

            this.close();
            panel.startRender(Math.min(start, end), Math.max(start, end), this.transparentToggle.getValue(), this.formatSelector.getValue());
        }).color(0xFFDC2626, 0xFFEF4444);

        UIButton close = new UIButton(UIKeys.GENERAL_CLOSE, (b) -> this.close());

        UIElement form = UI.column(
            5,
            0,
            info,
            edit,
            rangeLabel.marginTop(2),
            UI.row(5, 0, 20, this.startInput, this.endInput).h(20),
            this.transparentToggle,
            this.formatSelector,
            frame.marginTop(4),
            sequence,
            close
        );
        form.relative(this.content).xy(16, 6).w(1F, -32);

        this.content.add(form);
    }

    @Override
    public int getPreferredWidth()
    {
        return 360;
    }
}
