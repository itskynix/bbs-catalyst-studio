package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keyframe editor for targetActor property.
 * Clean, always-visible dropdown button in the Details panel to pick scene/world actors.
 */
public class UITargetActorKeyframeFactory extends UIKeyframeFactory<String>
{
    private UIButton pick;
    private UIElement section;

    public UITargetActorKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_ACTOR_NAME, (b) -> this.pickActor());

        this.section = UI.column(
            UI.label(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_ACTOR_NAME),
            this.pick
        ).marginTop(UIConstants.SECTION_GAP);

        this.scroll.add(this.section);

        this.updateActor();
    }

    /** Only the "Actor" gaze mode takes a name: with "None" or "Camera" keyed here the picker has nothing to say. */
    private boolean isActorMode()
    {
        var graph = this.editor.getGraph();

        if (graph == null)
        {
            return true;
        }

        for (UIKeyframeSheet sheet : graph.getSheets())
        {
            if (sheet.channel != null && sheet.channel.getId().endsWith("targetType") && !sheet.channel.isEmpty())
            {
                Object type = sheet.channel.interpolate(this.keyframe.getTick());

                return !(type instanceof Number number) || number.intValue() == FramebufferForm.TARGET_ACTOR;
            }
        }

        return true;
    }

    private void updateActor()
    {
        this.section.setVisible(this.isActorMode());

        String actor = this.keyframe.getValue();
        boolean empty = actor == null || actor.trim().isEmpty();

        this.pick.label = empty ? UIKeys.GENERAL_NONE : IKey.raw(actor);
        this.pick.tooltip(empty ? UIKeys.GENERAL_NONE : IKey.raw(actor));
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
                    if (replay.getName() != null && !replay.getName().trim().isEmpty())
                    {
                        actors.add(replay.getName().trim());
                    }
                    else if (replay.getId() != null && !replay.getId().trim().isEmpty())
                    {
                        actors.add(replay.getId().trim());
                    }
                }
            }
        }

        UIOverlay.addOverlay(this.getContext(), new UIStringOverlayPanel(UIKeys.FORMS_EDITOR_FRAMEBUFFER_TARGET_ACTOR_NAME, actors, (selected) ->
        {
            this.setValue(selected == null ? "" : selected);
            this.updateActor();
        }), 200, 240);
    }

    @Override
    public void update()
    {
        super.update();

        this.updateActor();
    }
}
