package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashSet;
import java.util.Set;

public class UIFramebufferFormPanel extends UIFormPanel<FramebufferForm>
{
    public UICirculate targetType;
    public UILabel targetActorLabel;
    public UIButton targetActor;

    public UIFramebufferFormPanel(UIForm editor)
    {
        super(editor);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        UITrackpad height = UIValues.trackpad(() -> this.form.height);

        width.limit(2, 4096, true).tooltip(UIKeys.VIDEO_SETTINGS_WIDTH);
        height.limit(2, 4096, true).tooltip(UIKeys.VIDEO_SETTINGS_HEIGHT);

        UITrackpad scale = UIValues.trackpad(() -> this.form.scale);
        UIToggle glow = UIValues.toggle(UIKeys.FORMS_EDITOR_FRAMEBUFFER_GLOW, () -> this.form.glow);
        UITrackpad inset = UIValues.trackpad(() -> this.form.inset);
        inset.limit(-5.0D, 5.0D);

        this.targetType = new UICirculate((b) ->
        {
            this.form.targetType.set(b.getValue());
            this.updateTargetVisibility();
        });
        this.targetType.addLabel(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_NONE);
        this.targetType.addLabel(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_CAMERA);
        this.targetType.addLabel(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_ACTOR);
        this.targetType.valueBinding(() ->
        {
            if (this.form != null && this.targetType.getValue() != this.form.targetType.get())
            {
                this.targetType.setValue(this.form.targetType.get());
                this.updateTargetVisibility();
            }
        });

        this.targetActorLabel = UI.label(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_ACTOR_NAME);
        this.targetActor = new UIButton(UIKeys.GENERAL_NONE, (b) -> this.pickActor());
        this.targetActor.valueBinding(this::updateActorButtonLabel);

        UITrackpad saccadeFreq = UIValues.trackpad(() -> this.form.saccadeFrequency);
        saccadeFreq.limit(0D, 20D);

        UITrackpad saccadeAmp = UIValues.trackpad(() -> this.form.saccadeAmplitude);
        saccadeAmp.limit(0D, 2D);

        UITrackpad maxLook = UIValues.trackpad(() -> this.form.maxLookOffset);
        maxLook.limit(0.05D, 1.5D);

        this.options.add(
            UI.label(UIKeys.VIDEO_SETTINGS_RESOLUTION),
            UI.row(width, height),
            UI.labelRow(UIKeys.TRANSFORMS_SCALE, scale),
            UI.labelRow(UIKeys.FORMS_EDITOR_FRAMEBUFFER_INSET, inset),
            glow,
            UI.label(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET),
            this.targetType,
            this.targetActorLabel,
            this.targetActor,
            UI.label(UIKeys.FORMS_EDITOR_FRAMEBUFFER_SACCADES),
            UI.labelRow(UIKeys.FORMS_EDITOR_FRAMEBUFFER_SACCADE_FREQ, saccadeFreq),
            UI.labelRow(UIKeys.FORMS_EDITOR_FRAMEBUFFER_SACCADE_AMP, saccadeAmp),
            UI.labelRow(UIKeys.FORMS_EDITOR_FRAMEBUFFER_SACCADE_MAX_OFFSET, maxLook)
        );

        this.targetActorLabel.setVisible(false);
        this.targetActor.setVisible(false);
        this.updateTargetVisibility();
    }

    @Override
    public void startEdit(FramebufferForm form)
    {
        super.startEdit(form);
        this.updateTargetVisibility();
        this.updateActorButtonLabel();
    }

    private void updateTargetVisibility()
    {
        boolean showActor = (this.form != null && this.form.targetType.get() == FramebufferForm.TARGET_ACTOR);

        this.targetActorLabel.setVisible(showActor);
        this.targetActor.setVisible(showActor);
    }

    private void updateActorButtonLabel()
    {
        if (this.form == null)
        {
            return;
        }

        String actor = this.form.targetActor.get();

        this.targetActor.label = (actor == null || actor.isEmpty()) ? UIKeys.GENERAL_NONE : IKey.raw(actor);
    }

    private void pickActor()
    {
        Set<String> actors = new LinkedHashSet<>();

        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        if (dashboard != null)
        {
            UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

            if (filmPanel != null && filmPanel.getData() != null)
            {
                for (Replay replay : filmPanel.getData().replays.getList())
                {
                    if (replay.getName() != null && !replay.getName().isEmpty())
                    {
                        actors.add(replay.getName());
                    }
                    else if (replay.getId() != null && !replay.getId().isEmpty())
                    {
                        actors.add(replay.getId());
                    }
                }
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null)
        {
            for (net.minecraft.entity.Entity e : mc.world.getEntities())
            {
                if (e.getCustomName() != null && !e.getCustomName().getString().isEmpty())
                {
                    actors.add(e.getCustomName().getString());
                }
                else if (e instanceof net.minecraft.entity.player.PlayerEntity player)
                {
                    actors.add(player.getName().getString());
                }
            }
        }

        UIOverlay.addOverlay(this.getContext(), new UIStringOverlayPanel(
            UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_ACTOR,
            true,
            actors,
            (selected) ->
            {
                this.form.targetActor.set(selected == null ? "" : selected);
                this.updateActorButtonLabel();
            }
        ), 0.4F, 0.5F);
    }
}
