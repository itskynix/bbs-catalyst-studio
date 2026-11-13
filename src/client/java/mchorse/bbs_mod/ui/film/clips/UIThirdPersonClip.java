package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.ThirdPersonClientClip;
import mchorse.bbs_mod.camera.clips.modifiers.ThirdPersonClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel of the third person camera: who and which bone it hangs on, the switches of the arm's
 * follow, lag and collision, and the button that opens the keyframes of every number of the arm.
 */
public class UIThirdPersonClip extends UIClip<ThirdPersonClientClip>
{
    public UIButton selector;
    public UIButton group;
    public UIToggle followTargetYaw;
    public UIToggle positionLag;
    public UIToggle rotationLag;
    public UIToggle collisionTest;
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UIThirdPersonClip(ThirdPersonClientClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) ->
        {
            UIAnchorKeyframeFactory.displayAttachments(this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(), (attachment) -> this.clip.group.set(attachment));
        });
        this.group.tooltip(UIKeys.THIRD_PERSON_BONE_TOOLTIP);

        this.followTargetYaw = this.toggle(UIKeys.THIRD_PERSON_FOLLOW_YAW, this.clip.followTargetYaw);
        this.followTargetYaw.tooltip(UIKeys.THIRD_PERSON_FOLLOW_YAW_TOOLTIP);
        this.positionLag = this.toggle(UIKeys.THIRD_PERSON_POSITION_LAG, this.clip.positionLag);
        this.positionLag.tooltip(UIKeys.THIRD_PERSON_POSITION_LAG_TOOLTIP);
        this.rotationLag = this.toggle(UIKeys.THIRD_PERSON_ROTATION_LAG, this.clip.rotationLag);
        this.rotationLag.tooltip(UIKeys.THIRD_PERSON_ROTATION_LAG_TOOLTIP);
        this.collisionTest = this.toggle(UIKeys.THIRD_PERSON_COLLISION, this.clip.collisionTest);
        this.collisionTest.tooltip(UIKeys.THIRD_PERSON_COLLISION_TOOLTIP);

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("third_person_keyframes");

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.group));
        this.panels.add(this.section(UIKeys.THIRD_PERSON_ARM, this.followTargetYaw, this.positionLag, this.rotationLag, this.collisionTest));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, this.edit));
    }

    @Override
    public boolean openSubEditor()
    {
        return this.openSubEditor(this.edit);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.updateDuration(this.clip.duration.get());

        List<KeyframeChannel<?>> channels = new ArrayList<>();

        for (ThirdPersonClip.Channel channel : this.clip.spring)
        {
            channels.add(channel.channel);
        }

        this.keyframes.setChannels(channels, (channel) -> L10n.lang("bbs.ui.camera.third_person.channel." + channel.getId()));
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("third_person"))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "third_person");
        }
    }
}
