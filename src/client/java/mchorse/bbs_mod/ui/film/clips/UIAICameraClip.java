package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.AICameraClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIAngleModule;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

public class UIAICameraClip extends UIKeyframeClip
{
    public UIButton openCopilotBtn;
    public UIPointModule originModule;
    public UIAngleModule angleModule;
    public UIButton snapOriginBtn;

    public AICameraClip getAIClip()
    {
        return (AICameraClip) this.clip;
    }

    public UIAICameraClip(AICameraClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.openCopilotBtn = new UIButton(L10n.lang("bbs.copilot.open_copilot"), (b) ->
        {
            if (this.editor instanceof UIClipsPanel clipsPanel && clipsPanel.filmPanel != null)
            {
                UIOverlay.addOverlay(this.getContext(), new mchorse.bbs_mod.copilot.ui.UICopilotDialog(clipsPanel.filmPanel, this.clip), 620, 380);
            }
        });

        this.originModule = new UIPointModule(this.editor, L10n.lang("bbs.copilot.anchor_origin"));
        this.originModule.contextMenu();

        this.angleModule = new UIAngleModule(this.editor);
        this.angleModule.title.label = L10n.lang("bbs.copilot.anchor_angle");
        this.angleModule.contextMenu();

        this.snapOriginBtn = new UIButton(L10n.lang("bbs.copilot.snap_origin_to_camera"), (b) -> this.snapOriginToCamera());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(L10n.lang("bbs.copilot.ai_camera_controls"), this.openCopilotBtn));
        this.panels.add(this.originModule);
        this.panels.add(this.angleModule);
        this.panels.add(this.snapOriginBtn);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.originModule.fill(this.getAIClip().origin);
        this.angleModule.fill(this.getAIClip().angle);
    }

    public void snapOriginToCamera()
    {
        Camera cam = this.editor != null ? this.editor.getCamera() : null;
        if (cam != null)
        {
            this.getAIClip().origin.set(new Point(cam.position.x, cam.position.y, cam.position.z));
            Angle a = new Angle(0, 0);
            a.set(cam);
            this.getAIClip().angle.set(a);
            this.originModule.fill(this.getAIClip().origin);
            this.angleModule.fill(this.getAIClip().angle);
            this.getAIClip().postNotify();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.cameraEntity != null)
        {
            Entity e = client.cameraEntity;
            this.getAIClip().origin.set(new Point(e.getX(), e.getY() + e.getEyeHeight(e.getPose()), e.getZ()));
            this.getAIClip().angle.set(new Angle(e.getYaw(), e.getPitch()));
            this.originModule.fill(this.getAIClip().origin);
            this.angleModule.fill(this.getAIClip().angle);
            this.getAIClip().postNotify();
        }
    }
}
