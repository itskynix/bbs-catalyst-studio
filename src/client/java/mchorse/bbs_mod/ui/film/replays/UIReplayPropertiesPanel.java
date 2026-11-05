package mchorse.bbs_mod.ui.film.replays;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.ReplayLookAt;
import mchorse.bbs_mod.film.replays.PovBodyParts;
import mchorse.bbs_mod.film.replays.PovRig;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.film.replays.pov.UIPovBodyPartList;
import mchorse.bbs_mod.ui.film.replays.pov.UIPovModelEditor;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class UIReplayPropertiesPanel extends UIElement
{
    private static final List<BiFunction<UIFilmPanel, Supplier<Replay>, UIElement>> ACTIONS = new ArrayList<>();

    public static void registerAction(BiFunction<UIFilmPanel, Supplier<Replay>, UIElement> factory)
    {
        ACTIONS.add(Objects.requireNonNull(factory));
    }

    private final UIFilmPanel filmPanel;

    public UIElement properties;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UITextbox label;
    public UITextbox nameTag;
    public UIToggle shadow;
    public UITrackpad shadowSize;
    public UIToggle shadowFollow;
    public UITrackpad shadowOffsetX;
    public UITrackpad shadowOffsetY;
    public UITrackpad shadowOffsetZ;
    public UITrackpad looping;
    public UIToggle actor;
    public UIToggle actorPickup;
    public UIToggle fp;
    public UIToggle relative;
    public UITrackpad relativeOffsetX;
    public UITrackpad relativeOffsetY;
    public UITrackpad relativeOffsetZ;
    public UIToggle axesPreview;
    public UIButton pickAxesPreviewBone;
    public UICirculate lookAtMode;
    public UIButton lookAtTarget;
    public UITrackpad lookAtX;
    public UITrackpad lookAtY;
    public UITrackpad lookAtZ;
    public UIElement lookAtPoint;

    /* Point of view: the hand model, how the head and the hurt jolt behave, and the POV body parts */
    public UISection povSection;
    public UINestedEdit povHand;
    public UIButton povHandReset;
    public UIToggle povHardcoreLook;
    public UIToggle povCameraShake;
    public UIPovBodyPartList povParts;
    public UINestedEdit povPartForm;
    public UIButton povPartBone;
    public UIToggle povPartEnabled;
    public UIPropTransform povPartTransform;
    private UIElement povPartFields;

    /** Whether the section is laid out as shown; it is only for replays with a point of view */
    private boolean shownPov = true;

    /** The look-at mode the section is laid out for; the fields of the other modes are hidden */
    private int shownLookAtMode = -1;

    private UIReplayList list;

    /** The replay whose values the fields show; writes still go to the whole selection. */
    private Replay replay;

    /**
     * Bind a field to the shown replay: the read runs on every frame the field is drawn, so the
     * panel stops showing what the replay held when it was last selected.
     */
    private <T extends UIElement> T bound(T element, Consumer<Replay> read)
    {
        element.valueBinding(() ->
        {
            if (this.replay != null)
            {
                read.accept(this.replay);
            }
        });

        return element;
    }

    public UIReplayPropertiesPanel(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;

        this.pickEdit = new UINestedEdit((editing) ->
        {
            if (this.list == null)
            {
                return;
            }

            Replay r = this.list.getSelectedReplayFirst();

            if (r != null)
            {
                this.list.openFormEditor(r.form, editing, this.pickEdit::setForm);
            }
        });
        this.pickEdit.pick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM);
        this.pickEdit.edit.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM);
        this.enabled = this.bound(new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.edit((replay) -> replay.enabled.set(b.getValue()));
            filmPanel.getController().createEntities();
        }), (r) -> this.enabled.setValue(r.enabled.get()));
        this.label = this.bound(new UITextbox(1000, (s) -> this.edit((replay) -> replay.label.set(s))), (r) -> this.label.setText(r.label.get()));
        this.label.textbox.setPlaceholder(UIKeys.FILM_REPLAY_LABEL);
        this.nameTag = this.bound(new UITextbox(1000, (s) -> this.edit((replay) -> replay.nameTag.set(s))), (r) -> this.nameTag.setText(r.nameTag.get()));
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.shadow = this.bound(new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.edit((replay) -> replay.shadow.set(b.getValue()))), (r) -> this.shadow.setValue(r.shadow.get()));
        this.shadowSize = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.shadowSize.set(v.floatValue()))), (r) -> this.shadowSize.setValue(r.shadowSize.get()));
        this.shadowSize.tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE);
        this.shadowFollow = this.bound(new UIToggle(UIKeys.FILM_REPLAY_SHADOW_FOLLOW, (b) -> this.edit((replay) -> replay.shadowFollow.set(b.getValue()))), (r) -> this.shadowFollow.setValue(r.shadowFollow.get()));
        this.shadowFollow.tooltip(UIKeys.FILM_REPLAY_SHADOW_FOLLOW_TOOLTIP);
        this.shadowOffsetX = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().x = v))), (r) -> this.shadowOffsetX.setValue(r.shadowOffset.get().x));
        this.shadowOffsetX.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetY = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().y = v))), (r) -> this.shadowOffsetY.setValue(r.shadowOffset.get().y));
        this.shadowOffsetY.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().z = v))), (r) -> this.shadowOffsetZ.setValue(r.shadowOffset.get().z));
        this.shadowOffsetZ.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.looping = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.looping.set(v.intValue()))), (r) -> this.looping.setValue(r.looping.get()));
        this.looping.limit(0).integer().tooltip(UIKeys.FILM_REPLAY_LOOPING_TOOLTIP);
        this.actor = this.bound(new UIToggle(UIKeys.FILM_REPLAY_ACTOR, (b) -> this.edit((replay) -> replay.actor.set(b.getValue()))), (r) -> this.actor.setValue(r.actor.get()));
        this.actor.tooltip(UIKeys.FILM_REPLAY_ACTOR_TOOLTIP);
        this.actorPickup = this.bound(new UIToggle(UIKeys.FILM_REPLAY_ACTOR_PICKUP, (b) -> this.edit((replay) -> replay.actorPickup.set(b.getValue()))), (r) -> this.actorPickup.setValue(r.actorPickup.get()));
        this.actorPickup.tooltip(UIKeys.FILM_REPLAY_ACTOR_PICKUP_TOOLTIP);
        this.fp = new UIToggle(UIKeys.FILM_REPLAY_FP, (b) ->
        {
            if (filmPanel.getData() != null)
            {
                for (Replay replay : filmPanel.getData().replays.getList())
                {
                    if (replay.fp.get())
                    {
                        replay.fp.set(false);
                    }
                }
            }

            Replay first = this.list == null ? null : this.list.getSelectedReplayFirst();

            if (first != null)
            {
                first.fp.set(b.getValue());
            }
        });
        this.bound(this.fp, (r) -> this.fp.setValue(r.fp.get()));
        this.relative = this.bound(new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, (b) -> this.edit((replay) -> replay.relative.set(b.getValue()))), (r) -> this.relative.setValue(r.relative.get()));
        this.relative.tooltip(UIKeys.FILM_REPLAY_RELATIVE_TOOLTIP);
        this.relativeOffsetX = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().x = v))), (r) -> this.relativeOffsetX.setValue(r.relativeOffset.get().x));
        this.relativeOffsetY = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().y = v))), (r) -> this.relativeOffsetY.setValue(r.relativeOffset.get().y));
        this.relativeOffsetZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().z = v))), (r) -> this.relativeOffsetZ.setValue(r.relativeOffset.get().z));
        this.axesPreview = this.bound(new UIToggle(UIKeys.FILM_REPLAY_AXES_PREVIEW, (b) -> this.edit((replay) -> replay.axesPreview.set(b.getValue()))), (r) -> this.axesPreview.setValue(r.axesPreview.get()));
        this.pickAxesPreviewBone = new UIButton(UIKeys.FILM_REPLAY_PICK_AXES_PREVIEW, (b) ->
        {
            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay != null && filmPanel.getData() != null)
            {
                UIAnchorKeyframeFactory.displayAttachments(filmPanel, replay.getId(), replay.axesPreviewBone.get(), (s) ->
                {
                    this.edit((r) -> r.axesPreviewBone.set(s));
                });
            }
        });

        this.lookAtMode = this.bound(new UICirculate((b) -> this.edit((replay) -> replay.lookAtMode.set(b.getValue()))), (r) ->
        {
            this.lookAtMode.setValue(r.lookAtMode.get());
            this.showLookAtFields(r.lookAtMode.get());
        });
        this.lookAtMode.tooltip(UIKeys.FILM_REPLAY_LOOK_AT_NOTE);
        this.lookAtMode.addLabel(UIKeys.FILM_REPLAY_LOOK_AT_OFF);
        this.lookAtMode.addLabel(UIKeys.FILM_REPLAY_LOOK_AT_REPLAY);
        this.lookAtMode.addLabel(UIKeys.FILM_REPLAY_LOOK_AT_CAMERA);
        this.lookAtMode.addLabel(UIKeys.FILM_REPLAY_LOOK_AT_POINT);
        this.lookAtTarget = this.bound(new UIButton(IKey.EMPTY, (b) -> this.openLookAtTargetMenu()), (r) -> this.lookAtTarget.label = this.lookAtTargetLabel(r));
        this.lookAtTarget.tooltip(UIKeys.FILM_REPLAY_LOOK_AT_TARGET);
        this.lookAtX = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.lookAtPoint, (value) -> value.get().x = v))), (r) -> this.lookAtX.setValue(r.lookAtPoint.get().x));
        this.lookAtY = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.lookAtPoint, (value) -> value.get().y = v))), (r) -> this.lookAtY.setValue(r.lookAtPoint.get().y));
        this.lookAtZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.lookAtPoint, (value) -> value.get().z = v))), (r) -> this.lookAtZ.setValue(r.lookAtPoint.get().z));
        this.lookAtX.tooltip(UIKeys.FILM_REPLAY_LOOK_AT_POINT);
        this.lookAtY.tooltip(UIKeys.FILM_REPLAY_LOOK_AT_POINT);
        this.lookAtZ.tooltip(UIKeys.FILM_REPLAY_LOOK_AT_POINT);
        this.lookAtPoint = UI.row(this.lookAtX, this.lookAtY, this.lookAtZ);

        for (UITrackpad trackpad : new UITrackpad[] {this.lookAtX, this.lookAtY, this.lookAtZ})
        {
            trackpad.context((menu) -> menu.action(Icons.BLOCK, UIKeys.FILM_REPLAY_LOOK_AT_FROM_LOOK, this::pasteLookAtFromLook));
        }

        this.createPovSection();

        UISection lookAtSection = new UISection(UIKeys.FILM_REPLAY_LOOK_AT);

        lookAtSection.fields.add(
            this.lookAtMode, this.lookAtTarget, this.lookAtPoint
        );

        UISection shadowSection = new UISection(UIKeys.FILM_REPLAY_SHADOW);

        shadowSection.fields.add(
            this.shadow, this.shadowSize,
            this.shadowFollow, UI.row(this.shadowOffsetX, this.shadowOffsetY, this.shadowOffsetZ)
        );

        UISection other = new UISection(UIKeys.FILM_REPLAY_SECTION_OTHER);

        other.fields.add(
            this.looping, this.actor, this.actorPickup, this.fp,
            this.relative, UI.row(this.relativeOffsetX, this.relativeOffsetY, this.relativeOffsetZ),
            this.axesPreview, this.pickAxesPreviewBone
        );

        lookAtSection.setExpanded(false);
        shadowSection.setExpanded(false);
        other.setExpanded(false);

        this.properties = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            this.pickEdit, this.enabled, this.label, this.nameTag,
            this.povSection,
            lookAtSection,
            shadowSection,
            other
        );
        this.properties.relative(this).x(0).y(0).w(1F).h(1F);
        this.bound(this.povSection, this::showPovSection);

        for (var factory : ACTIONS)
        {
            UIElement action = factory.apply(this.filmPanel, () -> this.replay);

            if (action != null) this.properties.add(action);
        }

        this.add(this.properties);
        this.showLookAtFields(ReplayLookAt.OFF);
        this.setReplay(null);
    }

    /**
     * The POV section, as in the replay list's own POV category: the hand model picked (Pick) or
     * placed in the POV model editor (Edit), Hardcore Look and Camera Shake, and the POV body
     * parts with the picked one's form, bone, switch and transform.
     */
    private void createPovSection()
    {
        this.povHand = new UINestedEdit((editing) ->
        {
            if (this.replay == null)
            {
                return;
            }

            if (editing)
            {
                UIOverlay.addOverlay(this.getContext(), new UIPovModelEditor(this.filmPanel, this.replay), 0.9F, 0.9F).instant();
            }
            else
            {
                Replay replay = this.replay;

                UIFormPalette.open(this.getParentContainer(), false, PovHandsForm.of(replay), (f) ->
                {
                    replay.pov.handForm.set(FormUtils.copy(f));
                    this.povHand.setForm(PovHandsForm.of(replay));
                });
            }
        });
        this.povHand.pick.tooltip(UIKeys.FILM_REPLAY_POV_HAND_PICK);
        this.povHand.edit.tooltip(UIKeys.FILM_REPLAY_POV_HAND_EDIT);

        this.povHandReset = new UIButton(UIKeys.FILM_REPLAY_POV_HAND_RESET, (b) ->
        {
            if (this.replay != null)
            {
                this.replay.pov.handForm.set(null);
                this.povHand.setForm(PovHandsForm.of(this.replay));
            }
        });
        this.povHandReset.tooltip(UIKeys.FILM_REPLAY_POV_HAND_RESET);

        this.povHardcoreLook = this.bound(new UIToggle(UIKeys.FILM_REPLAY_POV_HARDCORE_LOOK, (b) -> this.edit((replay) -> replay.pov.hardcoreLook.set(b.getValue()))), (r) -> this.povHardcoreLook.setValue(r.pov.hardcoreLook.get()));
        this.povHardcoreLook.tooltip(UIKeys.FILM_REPLAY_POV_HARDCORE_LOOK_TOOLTIP);
        this.povCameraShake = this.bound(new UIToggle(UIKeys.FILM_REPLAY_POV_CAMERA_SHAKE, (b) -> this.edit((replay) -> replay.pov.cameraShake.set(b.getValue()))), (r) -> this.povCameraShake.setValue(r.pov.cameraShake.get()));
        this.povCameraShake.tooltip(UIKeys.FILM_REPLAY_POV_CAMERA_SHAKE_TOOLTIP);

        this.povParts = new UIPovBodyPartList((l) -> this.fillPovPart(l.isEmpty() ? null : l.get(0)));
        this.povParts.background();
        this.povParts.h(16 * 4);

        UIIcon add = new UIIcon(Icons.ADD, (b) ->
        {
            if (this.replay != null)
            {
                PovBodyParts.Part part = this.replay.pov.bodyParts.add(null, PovRig.endOf(true));

                this.povParts.setList(this.replay.pov.bodyParts.getAllTyped());
                this.povParts.setCurrentScroll(part);
                this.fillPovPart(part);
            }
        });
        add.tooltip(UIKeys.FILM_REPLAY_POV_BODY_PARTS_ADD);

        UIIcon remove = new UIIcon(Icons.REMOVE, (b) ->
        {
            PovBodyParts.Part part = this.povParts.getCurrentFirst();

            if (this.replay != null && part != null)
            {
                this.replay.pov.bodyParts.remove(part);
                this.povParts.setList(this.replay.pov.bodyParts.getAllTyped());
                this.fillPovPart(null);
            }
        });
        remove.tooltip(UIKeys.FILM_REPLAY_POV_BODY_PARTS_REMOVE);

        this.povPartForm = new UINestedEdit((editing) ->
        {
            PovBodyParts.Part part = this.povParts.getCurrentFirst();

            if (part != null)
            {
                UIFormPalette.open(this.getParentContainer(), editing, part.form.get(), (f) ->
                {
                    part.form.set(FormUtils.copy(f));
                    this.povPartForm.setForm(part.form.get());
                });
            }
        });

        this.povPartBone = new UIButton(UIKeys.FILM_REPLAY_POV_BODY_PARTS_BONE, (b) ->
        {
            PovBodyParts.Part part = this.povParts.getCurrentFirst();

            if (part == null)
            {
                return;
            }

            this.getContext().replaceContextMenu((menu) ->
            {
                for (String bone : PovRig.BONES)
                {
                    menu.action(PovRig.isArm(bone) ? Icons.LIMB : Icons.POINTER, IKey.constant(bone), bone.equals(part.bone.get()), () ->
                    {
                        part.bone.set(bone);
                        this.povPartBone.label = IKey.constant(bone);
                    });
                }
            });
        });
        this.povPartBone.tooltip(UIKeys.FILM_REPLAY_POV_BODY_PARTS_BONE);

        this.povPartEnabled = new UIToggle(UIKeys.FILM_REPLAY_POV_BODY_PARTS_ENABLED, (b) ->
        {
            PovBodyParts.Part part = this.povParts.getCurrentFirst();

            if (part != null)
            {
                part.enabled.set(b.getValue());
            }
        });

        this.povPartTransform = new UIPropTransform().callbacks(() ->
        {
            PovBodyParts.Part part = this.povParts.getCurrentFirst();

            return part == null ? null : part.transform;
        });

        this.povPartFields = UI.column(this.povPartForm, this.povPartBone, this.povPartEnabled, this.povPartTransform);

        /* The list's header: its name, and the add and remove icons pinned at the right */
        UIElement partsHeader = new UIElement();
        UIElement partsLabel = UI.label(UIKeys.FILM_REPLAY_POV_BODY_PARTS).labelAnchor(0F, 0.5F);

        partsHeader.h(20);
        partsLabel.relative(partsHeader).w(1F, -40).h(1F);
        add.relative(partsHeader).x(1F, -40).wh(20, 20);
        remove.relative(partsHeader).x(1F, -20).wh(20, 20);
        partsHeader.add(partsLabel, add, remove);

        this.povSection = new UISection(UIKeys.FILM_REPLAY_POV_SECTION);
        this.povSection.fields.add(
            this.povHand, this.povHandReset,
            this.povHardcoreLook, this.povCameraShake,
            partsHeader,
            this.povParts, this.povPartFields
        );
        this.povSection.setExpanded(false);
    }

    /** The picked POV body part's fields, or none of them without one. */
    private void fillPovPart(PovBodyParts.Part part)
    {
        this.povPartFields.setVisible(part != null);

        if (part != null)
        {
            this.povPartForm.setForm(part.form.get());
            this.povPartBone.label = IKey.constant(part.bone.get());
            this.povPartEnabled.setValue(part.enabled.get());
            this.povPartTransform.setTransform(part.transform.get());
        }

        if (this.properties != null && this.properties.hasParent())
        {
            this.properties.resize();
        }
    }

    /** The section only for a replay with a point of view; laid out again only when that changes. */
    private void showPovSection(Replay replay)
    {
        boolean shown = ReplayPov.supports(replay);

        if (shown == this.shownPov)
        {
            return;
        }

        this.shownPov = shown;
        this.povSection.setVisible(shown);

        if (this.properties != null && this.properties.hasParent())
        {
            this.properties.resize();
        }
    }

    /** Only the fields of the chosen mode: the replay picker for a replay, X/Y/Z for a point. */
    private void showLookAtFields(int mode)
    {
        if (mode == this.shownLookAtMode)
        {
            return;
        }

        this.shownLookAtMode = mode;
        this.lookAtTarget.setVisible(mode == ReplayLookAt.REPLAY);
        this.lookAtPoint.setVisible(mode == ReplayLookAt.POINT);

        if (this.properties != null && this.properties.hasParent())
        {
            this.properties.resize();
        }
    }

    /**
     * Where the player is looking goes into the point: the middle of the block under the
     * crosshair, or the eyes of the entity under it - the same look the action clips' block
     * position takes, but as a point to face rather than a block.
     */
    private void pasteLookAtFromLook()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        HitResult result = mc == null ? null : mc.crosshairTarget;
        Vec3d point = null;

        if (result instanceof BlockHitResult bhr && result.getType() == HitResult.Type.BLOCK)
        {
            BlockPos pos = bhr.getBlockPos();

            point = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        }
        else if (result instanceof EntityHitResult ehr && result.getType() == HitResult.Type.ENTITY)
        {
            Entity entity = ehr.getEntity();

            point = entity.getEyePos();
        }

        if (point == null)
        {
            return;
        }

        Vec3d paste = point;

        this.edit((replay) -> BaseValue.edit(replay.lookAtPoint, (value) ->
        {
            value.get().x = paste.x;
            value.get().y = paste.y;
            value.get().z = paste.z;
        }));
    }

    private IKey lookAtTargetLabel(Replay replay)
    {
        Film film = this.filmPanel.getData();

        if (film != null)
        {
            for (Replay other : film.replays.getList())
            {
                if (other.getId().equals(replay.lookAtTarget.get()))
                {
                    return IKey.constant(other.getName());
                }
            }
        }

        return IKey.constant("-");
    }

    private void openLookAtTargetMenu()
    {
        Film film = this.filmPanel.getData();

        if (film == null || this.replay == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((manager) ->
        {
            manager.autoKeys();

            for (Replay other : film.replays.getList())
            {
                if (other == this.replay)
                {
                    continue;
                }

                manager.action(Icons.FILM, IKey.constant(other.getName()), () -> this.edit((replay) -> replay.lookAtTarget.set(other.getId())));
            }
        });
    }

    public void attachReplayList(UIReplayList list)
    {
        this.list = list;
    }

    public Consumer<Form> getFormConsumer()
    {
        return this.pickEdit::setForm;
    }

    private void edit(Consumer<Replay> consumer)
    {
        if (consumer != null && this.list != null)
        {
            for (Replay replay : this.list.getSelectedReplays())
            {
                consumer.accept(replay);
            }
        }
    }

    public void setReplay(Replay replay)
    {
        this.replay = replay;
        this.properties.setVisible(replay != null);

        if (replay != null)
        {
            this.pickEdit.setForm(replay.form.get());
            this.povHand.setForm(PovHandsForm.of(replay));
            this.povParts.setList(replay.pov.bodyParts.getAllTyped());
            this.povParts.deselect();
        }

        this.fillPovPart(null);
        this.showPovSection(replay);
    }

    /** The form whose arms the POV shows: its own hand model, else the replay's form. */
    private static final class PovHandsForm
    {
        static Form of(Replay replay)
        {
            Form hand = replay.pov.handForm.get();

            return hand != null ? hand : replay.form.get();
        }
    }
}
