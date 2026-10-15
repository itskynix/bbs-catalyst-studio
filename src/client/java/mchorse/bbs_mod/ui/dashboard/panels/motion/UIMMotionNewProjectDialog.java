package mchorse.bbs_mod.ui.dashboard.panels.motion;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Modern New mMotion Scene Creation Dialog.
 * Allows configuring Scene Name, FPS, Duration, Resolution and quick presets.
 */
public class UIMMotionNewProjectDialog extends UIOverlayPanel
{
    public final UIMMotionPanel panel;

    public final UITextbox nameBox;
    public final UITrackpad fpsInput;
    public final UITrackpad durationSecondsInput;
    public final UILabel durationCalcLabel;
    public final UITrackpad widthInput;
    public final UITrackpad heightInput;
    public final UIButton createBtn;

    public UIMMotionNewProjectDialog(UIMMotionPanel panel)
    {
        super(UIKeys.MMOTION_DIALOG_NEW_TITLE);

        this.panel = panel;

        /* 1. Project / Scene Name */
        UILabel nameLabel = UI.label(UIKeys.CATALYST_PROJECTS_NAME);
        nameLabel.labelAnchor(0, 0.5F).h(14);

        int count = panel != null && panel.hubScreen != null ? panel.hubScreen.getScenes().size() + 1 : 1;
        this.nameBox = new UITextbox(120, (t) -> {}).filename();
        this.nameBox.setText("Scene_" + count);
        this.nameBox.h(20);

        /* 2. Framerate & Duration */
        UILabel fpsLabel = UI.label(UIKeys.CATALYST_PROJECTS_FPS);
        fpsLabel.labelAnchor(0, 0.5F).h(14);
        UILabel durationLabel = UI.label(UIKeys.CATALYST_PROJECTS_DURATION);
        durationLabel.labelAnchor(0, 0.5F).h(14);
        UIElement fpsDurationLabels = UI.row(5, 0, 14, fpsLabel, durationLabel).h(14);

        this.fpsInput = new UITrackpad((v) -> this.updateDurationCalcLabel());
        this.fpsInput.limit(1, 240, true);
        this.fpsInput.setValue(60);
        this.fpsInput.h(20);

        this.durationSecondsInput = new UITrackpad((v) -> this.updateDurationCalcLabel());
        this.durationSecondsInput.limit(0.1, 3600.0);
        this.durationSecondsInput.setValue(5.0);
        this.durationSecondsInput.h(20);
        UIElement fpsDurationInputs = UI.row(5, 0, 20, this.fpsInput, this.durationSecondsInput).h(20);

        this.durationCalcLabel = new UILabel(IKey.EMPTY, Colors.GRAY);
        this.durationCalcLabel.labelAnchor(0, 0.5F).h(12);

        /* 3. Resolution */
        UILabel resLabel = UI.label(UIKeys.CATALYST_PROJECTS_RESOLUTION);
        resLabel.labelAnchor(0, 0.5F).h(14);

        this.widthInput = new UITrackpad((v) -> {});
        this.widthInput.limit(128, 7680, true);
        this.widthInput.setValue(1920);
        this.widthInput.h(20);

        this.heightInput = new UITrackpad((v) -> {});
        this.heightInput.limit(128, 4320, true);
        this.heightInput.setValue(1080);
        this.heightInput.h(20);
        UIElement resInputs = UI.row(5, 0, 20, this.widthInput, this.heightInput).h(20);

        /* 4. Quick Resolution Presets */
        UIButton p1080 = new UIButton(IKey.raw("1080p"), (b) ->
        {
            this.widthInput.setValue(1920);
            this.heightInput.setValue(1080);
        });
        UIButton p4k = new UIButton(IKey.raw("4K"), (b) ->
        {
            this.widthInput.setValue(3840);
            this.heightInput.setValue(2160);
        });
        UIButton pShorts = new UIButton(IKey.raw("9:16"), (b) ->
        {
            this.widthInput.setValue(1080);
            this.heightInput.setValue(1920);
        });
        UIButton p720 = new UIButton(IKey.raw("720p"), (b) ->
        {
            this.widthInput.setValue(1280);
            this.heightInput.setValue(720);
        });

        UIElement presetsRow = UI.row(5, 0, 20, p1080, p4k, pShorts, p720).h(20);

        /* 5. Bottom Action Buttons: Create (Red accent) & Close */
        this.createBtn = new UIButton(UIKeys.CATALYST_DIALOG_CREATE, (b) -> this.createProject()).color(0xFFDC2626, 0xFFEF4444);
        UIButton cancelBtn = new UIButton(UIKeys.GENERAL_CLOSE, (b) -> this.close());
        UIElement buttonsRow = UI.row(5, 0, 20, this.createBtn, cancelBtn).h(20);

        /* Structured Vertical Form */
        UIElement form = UI.column(
            5,
            0,
            nameLabel,
            this.nameBox,
            fpsDurationLabels.marginTop(2),
            fpsDurationInputs,
            this.durationCalcLabel,
            resLabel.marginTop(2),
            resInputs,
            presetsRow,
            buttonsRow.marginTop(6)
        );
        form.relative(this.content).xy(16, 6).w(1F, -32);

        this.content.add(form);
        this.updateDurationCalcLabel();
    }

    @Override
    public int getPreferredWidth()
    {
        return 360;
    }

    private void updateDurationCalcLabel()
    {
        double sec = this.durationSecondsInput != null ? this.durationSecondsInput.getValue() : 5.0;
        int fps = this.fpsInput != null ? (int) this.fpsInput.getValue() : 60;
        int total = (int) Math.round(sec * fps);

        if (this.durationCalcLabel != null)
        {
            this.durationCalcLabel.label = UIKeys.MMOTION_DURATION_SUMMARY.format(String.format(Locale.ROOT, "%.1f", sec), total, fps);
        }
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyCode() == GLFW.GLFW_KEY_ENTER && context.getKeyAction() == KeyAction.PRESSED)
        {
            this.createProject();
            return true;
        }

        return super.subKeyPressed(context);
    }

    private void createProject()
    {
        String name = this.nameBox.getText().trim();

        if (name.isEmpty())
        {
            int count = this.panel != null && this.panel.hubScreen != null ? this.panel.hubScreen.getScenes().size() + 1 : 1;
            name = "Scene_" + count;
        }

        int fps = (int) this.fpsInput.getValue();
        double durationSeconds = this.durationSecondsInput.getValue();
        int totalFrames = (int) Math.round(durationSeconds * (fps > 0 ? fps : 60));
        if (totalFrames <= 0) totalFrames = 300;

        UIMMotionPanel.MotionScene scene = new UIMMotionPanel.MotionScene(
            "scene_" + System.currentTimeMillis(),
            name,
            totalFrames
        );
        /* Temiz Başlangıç: Yeni sahne boş katman listesiyle başlar */
        scene.fps = fps > 0 ? fps : 60;
        scene.width = (int) this.widthInput.getValue();
        scene.height = (int) this.heightInput.getValue();
        MotionSceneIO.save(scene, scene.layers);

        if (this.panel != null && this.panel.hubScreen != null)
        {
            this.panel.hubScreen.getScenes().add(0, scene);
            this.panel.hubScreen.refresh();
        }

        this.close();

        if (this.panel != null)
        {
            this.panel.openScene(scene);
        }
    }
}
