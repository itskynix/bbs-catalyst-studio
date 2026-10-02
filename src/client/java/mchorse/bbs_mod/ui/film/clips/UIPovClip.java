package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.overwrite.POVClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.interps.Lerps;

public class UIPovClip extends UIClip<POVClip>
{
    public UIButton selector;
    public UIButton perspective;
    public UIToggle povOutput;
    public UIToggle headLook;
    public UIToggle renderHands;
    public UIToggle leftHandVisible;
    public UIToggle rightHandVisible;
    public UIToggle bobbing;
    public UITrackpad bobStrength;
    public UIPointModule offset;
    public UITrackpad fov;

    public UIPovClip(POVClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.selector = this.bind(new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        }), () ->
        {
            String id = this.clip.selector.get();

            if (id.isEmpty())
            {
                this.selector.label = UIKeys.CAMERA_PANELS_TARGET_TITLE;
            }
            else
            {
                UIFilmPanel panel = this.getParent(UIFilmPanel.class);
                String name = id;

                if (panel != null)
                {
                    for (Replay replay : panel.getData().replays.getList())
                    {
                        if (replay.getId().equals(id))
                        {
                            name = replay.getName();
                            break;
                        }
                    }
                }

                this.selector.label = IKey.raw(name);
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_POV_REPLAY_TOOLTIP);

        this.perspective = this.bind(new UIButton(IKey.EMPTY, (b) ->
        {
            int next = (this.clip.perspective.get() + 1) % 3;
            this.clip.perspective.set(next);
        }), () ->
        {
            int p = this.clip.perspective.get();

            if (p == POVClip.PERSPECTIVE_THIRD_PERSON_BACK)
            {
                this.perspective.label = UIKeys.CAMERA_PANELS_POV_THIRD_BACK;
            }
            else if (p == POVClip.PERSPECTIVE_THIRD_PERSON_FRONT)
            {
                this.perspective.label = UIKeys.CAMERA_PANELS_POV_THIRD_FRONT;
            }
            else
            {
                this.perspective.label = UIKeys.CAMERA_PANELS_POV_FIRST_PERSON;
            }
        });
        this.perspective.context((menu) ->
        {
            menu.action(Icons.USER, UIKeys.CAMERA_PANELS_POV_FIRST_PERSON, () -> this.clip.perspective.set(POVClip.PERSPECTIVE_FIRST_PERSON));
            menu.action(Icons.ARROW_LEFT, UIKeys.CAMERA_PANELS_POV_THIRD_BACK, () -> this.clip.perspective.set(POVClip.PERSPECTIVE_THIRD_PERSON_BACK));
            menu.action(Icons.ARROW_RIGHT, UIKeys.CAMERA_PANELS_POV_THIRD_FRONT, () -> this.clip.perspective.set(POVClip.PERSPECTIVE_THIRD_PERSON_FRONT));
        });
        this.perspective.tooltip(UIKeys.CAMERA_PANELS_POV_PERSPECTIVE);

        this.povOutput = this.toggle(UIKeys.CAMERA_PANELS_POV_OUTPUT, this.clip.povOutput);
        this.povOutput.tooltip(UIKeys.CAMERA_PANELS_POV_OUTPUT_TOOLTIP);

        this.headLook = this.toggle(UIKeys.CAMERA_PANELS_POV_HEAD_LOOK, this.clip.headLook);
        this.headLook.tooltip(UIKeys.CAMERA_PANELS_POV_HEAD_LOOK_TOOLTIP);

        this.renderHands = this.toggle(UIKeys.CAMERA_PANELS_POV_RENDER_HANDS, this.clip.renderHands);
        this.renderHands.tooltip(UIKeys.CAMERA_PANELS_POV_RENDER_HANDS_TOOLTIP);

        this.leftHandVisible = this.toggle(UIKeys.CAMERA_PANELS_POV_LEFT_HAND, this.clip.leftHandVisible);
        this.leftHandVisible.tooltip(UIKeys.CAMERA_PANELS_POV_LEFT_HAND_TOOLTIP);

        this.rightHandVisible = this.toggle(UIKeys.CAMERA_PANELS_POV_RIGHT_HAND, this.clip.rightHandVisible);
        this.rightHandVisible.tooltip(UIKeys.CAMERA_PANELS_POV_RIGHT_HAND_TOOLTIP);

        this.bobbing = this.toggle(UIKeys.CAMERA_PANELS_POV_BOBBING, this.clip.bobbing);
        this.bobbing.tooltip(UIKeys.CAMERA_PANELS_POV_BOBBING_TOOLTIP);

        this.bobStrength = this.trackpad(this.clip.bobStrength);
        this.bobStrength.tooltip(UIKeys.CAMERA_PANELS_POV_BOB_STRENGTH);

        this.offset = this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu(), () -> this.offset.fill(this.clip.offset));

        this.fov = this.trackpad(this.clip.fov);
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_POV_REPLAY, this.selector, this.perspective));
        this.panels.add(this.povOutput);
        this.panels.add(this.headLook);
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_POV_RENDER_HANDS, this.renderHands, UI.row(this.leftHandVisible, this.rightHandVisible)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_POV_BOBBING, this.bobbing, this.bobStrength));
        this.panels.add(this.offset);
        this.panels.add(this.fov);
    }

    @Override
    public void editClip(Position position)
    {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);

        if (panel != null)
        {
            String id = this.clip.selector.get();
            IEntity actor = id.isEmpty() ? null : panel.getController().getEntities().get(id);

            if (actor != null && this.clip.perspective.get() == POVClip.PERSPECTIVE_FIRST_PERSON)
            {
                float transition = this.getContext().getTransition();
                double px = Lerps.lerp(actor.getPrevX(), actor.getX(), transition);
                double py = Lerps.lerp(actor.getPrevY(), actor.getY(), transition) + actor.getEyeHeight();
                double pz = Lerps.lerp(actor.getPrevZ(), actor.getZ(), transition);

                this.clip.offset.get().set(position.point.x - px, position.point.y - py, position.point.z - pz);
            }
        }

        if (position.angle.fov != this.clip.fov.get())
        {
            this.clip.fov.set(position.angle.fov);
        }

        super.editClip(position);
    }
}
