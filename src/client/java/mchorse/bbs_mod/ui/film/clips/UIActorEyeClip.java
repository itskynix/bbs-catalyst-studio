package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.ActorEyeClientClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clips;

/**
 * The Point of View clip's panel: the replay looked out of (its source) and the bone the camera
 * stands on; the POV output, which layers of the replay's point of view reach the picture; the
 * camera, how the head is followed, whether the looked-at block is outlined, the perspective the
 * game's F5 would cycle and the field of view; then the eye's own offset, angle, damping and
 * keyframes, and the first-person arm placement.
 */
public class UIActorEyeClip extends UIClip<ActorEyeClientClip>
{
    public UIButton selector;
    public UIButton bone;
    public UIPointModule offset;
    public UIPointModule angle;
    public UITrackpad fov;
    public UITrackpad damping;
    public UIToggle hideHead;
    public UIToggle hands;
    public UIPointModule handOffset;
    public UITrackpad handPitch;
    public UITrackpad handYaw;

    public UIToggle outHud;
    public UIToggle outCrosshair;
    public UIToggle outPovActions;
    public UIToggle outScreenEffects;
    public UIToggle outCursor;
    public UIToggle outCameraShake;
    public UIToggle headLook;
    public UIToggle hardcoreLook;
    public UIToggle blockOutline;
    public UICirculate perspective;

    public UIKeyframeEditor keyframes;
    public UIButton editKeyframes;

    public UIActorEyeClip(ActorEyeClientClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.clips.actor_eye." + id);
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
        this.selector.label = key("replay_source");
        this.selector.tooltip(key("replay_source_tooltip"));

        this.bone = new UIButton(key("bone"), (b) ->
        {
            UIAnchorKeyframeFactory.displayAttachments(this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.bone.get(), (attachment) -> this.clip.bone.set(attachment));
        });
        this.bone.tooltip(key("bone_tooltip"));

        this.offset = this.bind(new UIPointModule(this.editor, key("offset")).contextMenu(), () -> this.offset.fill(this.clip.offset));
        this.angle = this.bind(new UIPointModule(this.editor, key("angle")).contextMenu(), () -> this.angle.fill(this.clip.angle));
        this.fov = this.trackpad(this.clip.fov);
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);
        this.damping = this.trackpad(this.clip.damping);
        this.damping.limit(0D, 1D).values(0.05D, 0.01D, 0.1D);
        this.damping.tooltip(key("damping_tooltip"));

        this.hideHead = this.toggle(key("hide_head"), this.clip.hideHead);
        this.hands = this.toggle(key("out_hands"), this.clip.hands);
        this.outHud = this.toggle(key("out_hud"), this.clip.outHud);
        this.outCrosshair = this.toggle(key("out_crosshair"), this.clip.outCrosshair);
        this.outPovActions = this.toggle(key("out_pov_actions"), this.clip.outPovActions);
        this.outPovActions.tooltip(key("out_pov_actions_tooltip"));
        this.outScreenEffects = this.toggle(key("out_screen_effects"), this.clip.outScreenEffects);
        this.outScreenEffects.tooltip(key("out_screen_effects_tooltip"));
        this.outCursor = this.toggle(key("out_cursor"), this.clip.outCursor);
        this.outCursor.tooltip(key("out_cursor_tooltip"));
        this.outCameraShake = this.toggle(key("out_camera_shake"), this.clip.outCameraShake);
        this.outCameraShake.tooltip(key("out_camera_shake_tooltip"));

        this.headLook = this.toggle(key("head_look"), this.clip.headLook);
        this.headLook.tooltip(key("head_look_tooltip"));
        this.hardcoreLook = this.toggle(key("hardcore_look"), this.clip.hardcoreLook);
        this.hardcoreLook.tooltip(key("hardcore_look_tooltip"));
        this.blockOutline = this.toggle(key("block_outline"), this.clip.blockOutline);
        this.blockOutline.tooltip(key("block_outline_tooltip"));

        /* The game's F5, as a button: each press steps to the next perspective */
        this.perspective = new UICirculate((b) -> this.editor.editMultiple(this.clip.perspective, (v) -> v.set(b.getValue())));
        this.perspective.addLabel(key("perspective_first"));
        this.perspective.addLabel(key("perspective_back"));
        this.perspective.addLabel(key("perspective_front"));
        this.perspective.tooltip(key("perspective_tooltip"));
        this.bind(this.perspective, () -> this.perspective.setValue(this.clip.perspective.get()));
        this.handOffset = this.bind(new UIPointModule(this.editor, key("hand_offset")).contextMenu(), () -> this.handOffset.fill(this.clip.handOffset));
        this.handPitch = this.trackpad(this.clip.handPitch);
        this.handPitch.tooltip(key("hand_pitch"));
        this.handYaw = this.trackpad(this.clip.handYaw);
        this.handYaw.tooltip(key("hand_yaw"));

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("actor_eye_keyframes");

        this.editKeyframes = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.updateKeyframeSheets();
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();

            if (this.keyframes.view.getGraph() != null)
            {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
        this.editKeyframes.keys().register(Keys.FORMS_EDIT, () -> this.editKeyframes.clickItself());
    }

    private void updateKeyframeSheets()
    {
        this.keyframes.view.removeAllSheets();
        this.keyframes.view.addSheet(new UIKeyframeSheet("eye_x", key("channel_x"), 0xFF5555, this.clip.channelX, null).icon(Icons.X).seed(() -> 0D));
        this.keyframes.view.addSheet(new UIKeyframeSheet("eye_y", key("channel_y"), 0x55FF55, this.clip.channelY, null).icon(Icons.Y).seed(() -> 0D));
        this.keyframes.view.addSheet(new UIKeyframeSheet("eye_z", key("channel_z"), 0x5599FF, this.clip.channelZ, null).icon(Icons.Z).seed(() -> 0D));
        this.keyframes.view.addSheet(new UIKeyframeSheet("eye_roll", key("channel_roll"), 0xAA55FF, this.clip.channelRoll, null).icon(Icons.ORBIT).seed(() -> 0D));
    }

    @Override
    public boolean openSubEditor()
    {
        return this.openSubEditor(this.editKeyframes);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("replay_source"), this.selector, this.bone));
        this.panels.add(this.section(key("output"), this.hands, this.outHud, this.outCrosshair, this.outPovActions, this.outScreenEffects, this.outCursor, this.outCameraShake));
        this.panels.add(this.section(key("camera"), this.headLook, this.hardcoreLook, this.blockOutline, this.perspective, this.fov));
        this.panels.add(this.offset);
        this.panels.add(this.angle);
        this.panels.add(this.section(key("view"), this.damping, this.hideHead, this.editKeyframes));
        this.panels.add(this.section(key("hands_section"), this.handOffset, UI.row(UIConstants.MARGIN, 0, 20, this.handPitch, this.handYaw)));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.updateKeyframeSheets();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("actor_eye"))
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
            data.putString("embed", "actor_eye");
        }
    }
}
