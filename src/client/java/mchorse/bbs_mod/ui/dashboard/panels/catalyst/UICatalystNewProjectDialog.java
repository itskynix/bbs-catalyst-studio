package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.catalyst.CatalystProjectManager;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Modern New Catalyst Project Creation Dialog.
 * Allows configuring Project Name, FPS, Duration, Resolution and quick presets.
 */
public class UICatalystNewProjectDialog extends UIOverlayPanel
{
    public final UICatalystPanel panel;

    public final UITextbox nameBox;
    public final UITrackpad fpsInput;
    public final UITrackpad widthInput;
    public final UITrackpad heightInput;
    public final UIButton createBtn;

    public UICatalystNewProjectDialog(UICatalystPanel panel)
    {
        super(UIKeys.CATALYST_DIALOG_NEW_TITLE);

        this.panel = panel;

        /* 1. Project Name */
        UILabel nameLabel = UI.label(UIKeys.CATALYST_PROJECTS_NAME);
        nameLabel.labelAnchor(0, 0.5F).h(14);

        this.nameBox = new UITextbox(120, (t) -> {}).filename();
        this.nameBox.setText("Project_" + (System.currentTimeMillis() % 10000));
        this.nameBox.h(20);

        /* 2. Framerate & Duration */
        UILabel fpsLabel = UI.label(UIKeys.CATALYST_PROJECTS_FPS);
        fpsLabel.labelAnchor(0, 0.5F).h(14);
        UIElement fpsDurationLabels = UI.row(5, 0, 14, fpsLabel).h(14);

        this.fpsInput = new UITrackpad((v) -> {});
        this.fpsInput.limit(1, 240, true);
        this.fpsInput.setValue(60);
        this.fpsInput.h(20);

        UIElement fpsDurationInputs = UI.row(5, 0, 20, this.fpsInput).h(20);

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

        /* 5. Bottom Action Buttons */
        this.createBtn = new UIButton(UIKeys.CATALYST_DIALOG_CREATE, (b) -> this.createProject()).primary();
        UIButton cancelBtn = new UIButton(UIKeys.GENERAL_CLOSE, (b) -> this.close());
        /* The actions live in the footer strip, so they stay inside the frame however short the window is */
        this.footer(FOOTER);

        UIElement buttonsRow = UI.row(6, 0, 22, cancelBtn, this.createBtn);

        buttonsRow.relative(this).x(1F, -214).y(1F, -FOOTER + 7).w(202).h(22);
        this.add(buttonsRow);

        /* Structured vertical form; it scrolls when the window is too short for it */
        UIScrollView form = UI.scrollView(
            5,
            12,
            nameLabel,
            this.nameBox,
            fpsDurationLabels.marginTop(2),
            fpsDurationInputs,
            resLabel.marginTop(2),
            resInputs,
            presetsRow
        );
        form.relative(this.content).xy(0, 0).w(1F).h(1F);

        this.content.add(form);
    }

    private static final int FOOTER = 36;

    @Override
    public int getPreferredWidth()
    {
        return 420;
    }

    @Override
    public int getMinWidth()
    {
        return 420;
    }

    @Override
    public int getMinHeight()
    {
        return 320;
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
            name = "Project_" + (System.currentTimeMillis() % 10000);
        }

        int fps = (int) this.fpsInput.getValue();
        /* No length to ask for: the timeline grows with its clips, and an empty one shows ten seconds of ruler */
        double durationSeconds = 10.0;
        int width = (int) this.widthInput.getValue();
        int height = (int) this.heightInput.getValue();

        CatalystProject project = new CatalystProject(name, fps, durationSeconds);
        project.width = width > 0 ? width : 1920;
        project.height = height > 0 ? height : 1080;

        CatalystProjectManager.saveProject(project);

        this.close();

        this.panel.setActiveProject(project);
        this.panel.refreshProjects();
        this.panel.setTab(UICatalystPanel.CatalystTab.EDITOR);
    }
}
