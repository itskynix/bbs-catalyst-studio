package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class UIGeneralFormPanel extends UIFormPanel
{
    public UIKeybind hotkey;

    public UIToggle visible;
    public UIToggle pickable;
    public UIButton filterTracks;
    public UIToggle boneTracks;
    
    public UITextbox trackName;
    public UITrackpad uiScale;
    public UITextbox name;
    public UIPropTransform transform;

    public UIToggle hitbox;
    public UITrackpad hitboxWidth;
    public UITrackpad hitboxHeight;
    public UISliderTrackpad hitboxSneakMultiplier;
    public UISliderTrackpad hitboxEyeHeight;

    public UITrackpad hp;
    public UITrackpad speed;
    public UITrackpad stepHeight;

    public UIGeneralFormPanel(UIForm editor)
    {
        super(editor);

        this.hotkey = new UIKeybind((combo) ->
        {
            this.form.hotkey.set(combo.keys.isEmpty() ? 0 : combo.keys.get(0));
        });
        this.hotkey.single().tooltip(UIKeys.FORMS_EDITORS_GENERAL_HOTKEY);

        this.visible = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_VISIBLE, () -> this.form.visible);
        this.pickable = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_PICKABLE, () -> this.form.pickable);
        this.pickable.tooltip(UIKeys.FORMS_EDITORS_GENERAL_PICKABLE_TOOLTIP);
        this.filterTracks = new UIButton(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS, (b) -> this.openTrackFilter());
        this.filterTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS_TOOLTIP);
        this.boneTracks = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_BONE_TRACKS, (b) ->
        {
            if (this.form instanceof ModelForm m) m.boneTracks.set(b.getValue());
        });
        this.boneTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_BONE_TRACKS_TOOLTIP);
        UIValues.resettable(this.boneTracks, () -> this.form instanceof ModelForm m ? m.boneTracks : null, () ->
        {
            if (this.form instanceof ModelForm m) this.boneTracks.setValue(m.boneTracks.get());
        });
        this.trackName = UIValues.textbox(120, () -> this.form.trackName);
        this.trackName.tooltip(UIKeys.FORMS_EDITORS_GENERAL_TRACK_NAME_TOOLTIP);
        this.uiScale = UIValues.trackpad(() -> this.form.uiScale);
        this.uiScale.limit(0.01D, 100D);
        this.name = UIValues.textbox(120, () -> this.form.name);

        this.transform = new UIPropTransform().withAnchor().callbacks(() -> this.form.transform).barBackground();
        this.transform.enableHotkeys();
        this.transform.valueBinding(() -> this.transform.setTransform(this.form == null ? null : this.form.transform.get()));

        this.hitbox = UIValues.toggle(UIKeys.CAMERA_PANELS_ENABLED, () -> this.form.hitbox);
        this.hitboxWidth = UIValues.trackpad(() -> this.form.hitboxWidth);
        this.hitboxWidth.limit(0).tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_WIDTH);
        this.hitboxHeight = UIValues.trackpad(() -> this.form.hitboxHeight);
        this.hitboxHeight.limit(0).tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_HEIGHT);
        this.hitboxSneakMultiplier = new UISliderTrackpad((v) -> this.form.hitboxSneakMultiplier.set(v.floatValue()));
        this.hitboxSneakMultiplier.limit(0, 1);
        this.hitboxEyeHeight = new UISliderTrackpad((v) -> this.form.hitboxEyeHeight.set(v.floatValue()));
        this.hitboxEyeHeight.limit(0, 1);

        this.hp = UIValues.trackpad(() -> this.form.hp);
        this.hp.limit(1F);
        this.speed = UIValues.trackpad(() -> this.form.speed);
        this.speed.limit(0F);
        this.stepHeight = UIValues.trackpad(() -> this.form.stepHeight);
        this.stepHeight.limit(0F);

        UISection display = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_DISPLAY);

        display.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_DISPLAY, this.name),
            this.hotkey, this.visible, this.pickable,
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_UI_SCALE, this.uiScale)
        );

        UISection tracks = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRACKS);

        tracks.fields.add(this.filterTracks, this.boneTracks, this.trackName);

        UISection transform = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRANSFORM);

        transform.fields.add(this.transform);

        UISection hitbox = new UISection(UIKeys.FORMS_EDITORS_GENERAL_HITBOX);

        hitbox.fields.add(
            this.hitbox,
            UI.row(this.hitboxWidth, this.hitboxHeight),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_SNEAK_MULTIPLIER, this.hitboxSneakMultiplier),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_EYE_HEIGHT, this.hitboxEyeHeight)
        );

        UISection movement = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_MOVEMENT);

        movement.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HP, this.hp),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED, this.speed.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED_TOOLTIP)),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_STEP_HEIGHT, this.stepHeight)
        );

        this.options.add(
            display,
            tracks,
            transform,
            hitbox,
            movement,
            this.openablesSection(),
            this.smoothingSection(),
            this.aiSection()
        );
    }

    /* Behaviour the form carries wherever it goes: worn from the podium, on a model block, in a replay */

    private UITrackpad trackpad(java.util.function.Supplier<? extends mchorse.bbs_mod.settings.values.base.BaseValueNumber<?>> value, double min, double max, boolean integer, IKey tooltip)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max, integer);
        trackpad.tooltip(tooltip);

        return trackpad;
    }

    private UIToggle toggle(IKey label, java.util.function.Supplier<mchorse.bbs_mod.settings.values.numeric.ValueBoolean> value, IKey tooltip)
    {
        UIToggle toggle = UIValues.toggle(label, value);

        toggle.tooltip(tooltip);

        return toggle;
    }

    private UISection openablesSection()
    {
        UISection section = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_OPENABLES);

        section.fields.add(
            this.toggle(UIKeys.FILM_REPLAY_INTERACTIVE_OPENABLES, () -> this.form.interactiveOpenables, UIKeys.FILM_REPLAY_INTERACTIVE_OPENABLES_TOOLTIP),
            UI.labelRow(UIKeys.FILM_REPLAY_OPENABLE_ANGLE, this.trackpad(() -> this.form.openableAngle, 15D, 135D, false, UIKeys.FILM_REPLAY_OPENABLE_ANGLE_TOOLTIP)),
            UI.labelRow(UIKeys.FILM_REPLAY_OPENABLE_THRESHOLD, this.trackpad(() -> this.form.openableThreshold, 0.1D, 0.9D, false, UIKeys.FILM_REPLAY_OPENABLE_THRESHOLD_TOOLTIP)),
            this.toggle(UIKeys.FORMS_HOLD_OPEN_ANGLE, () -> this.form.holdOpenAngle, UIKeys.FORMS_HOLD_OPEN_ANGLE_TOOLTIP)
        );
        section.setExpanded(false);

        return section;
    }

    private UISection smoothingSection()
    {
        UISection section = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_SMOOTHING);

        section.fields.add(
            this.toggle(UIKeys.FILM_REPLAY_LOCOMOTION_SMOOTHING, () -> this.form.locomotionSmoothing, UIKeys.FILM_REPLAY_LOCOMOTION_SMOOTHING_TOOLTIP),
            UI.labelRow(UIKeys.FILM_REPLAY_LOCOMOTION_TIME, this.trackpad(() -> this.form.locomotionTime, 0.05D, 2D, false, UIKeys.FILM_REPLAY_LOCOMOTION_TIME_TOOLTIP))
        );
        section.setExpanded(false);

        return section;
    }

    private UISection aiSection()
    {
        UISection section = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_AI);

        section.fields.add(
            this.toggle(UIKeys.FILM_REPLAY_HAS_AI, () -> this.form.hasAi, UIKeys.FILM_REPLAY_HAS_AI_TOOLTIP),
            UI.labelRow(UIKeys.FILM_REPLAY_AI_IDLE_MIN, this.trackpad(() -> this.form.aiIdleMin, 0D, 120D, false, UIKeys.FILM_REPLAY_AI_IDLE_MIN_TOOLTIP)),
            UI.labelRow(UIKeys.FILM_REPLAY_AI_IDLE_MAX, this.trackpad(() -> this.form.aiIdleMax, 0D, 120D, false, UIKeys.FILM_REPLAY_AI_IDLE_MAX_TOOLTIP)),
            UI.labelRow(UIKeys.FILM_REPLAY_AI_SPEED, this.trackpad(() -> this.form.aiSpeed, 0.3D, 8D, false, UIKeys.FILM_REPLAY_AI_SPEED_TOOLTIP)),
            UI.labelRow(UIKeys.FILM_REPLAY_AI_SEED, this.trackpad(() -> this.form.aiSeed, 0D, 9999D, true, UIKeys.FILM_REPLAY_AI_SEED_TOOLTIP)),
            this.toggle(UIKeys.FILM_REPLAY_AI_AVOIDANCE, () -> this.form.aiAvoidance, UIKeys.FILM_REPLAY_AI_AVOIDANCE_TOOLTIP),
            UI.labelRow(UIKeys.FILM_REPLAY_AI_FOLLOW_TARGET, UIValues.textbox(() -> this.form.aiFollowTarget).tooltip(UIKeys.FILM_REPLAY_AI_FOLLOW_TARGET_TOOLTIP)),
            UI.labelRow(UIKeys.FILM_REPLAY_AI_FOLLOW_DISTANCE, this.trackpad(() -> this.form.aiFollowDistance, 0D, 16D, false, UIKeys.FILM_REPLAY_AI_FOLLOW_DISTANCE_TOOLTIP)),
            this.toggle(UIKeys.FILM_REPLAY_ORIENT_TO_MOVEMENT, () -> this.form.orientToMovement, UIKeys.FILM_REPLAY_ORIENT_TO_MOVEMENT_TOOLTIP),
            this.toggle(UIKeys.FILM_REPLAY_CONTROLLER_ROTATION, () -> this.form.useControllerDesiredRotation, UIKeys.FILM_REPLAY_CONTROLLER_ROTATION_TOOLTIP),
            UI.labelRow(UIKeys.FILM_REPLAY_ROTATION_RATE, this.trackpad(() -> this.form.rotationRate, 30D, 1080D, false, UIKeys.FILM_REPLAY_ROTATION_RATE_TOOLTIP))
        );
        section.setExpanded(false);

        return section;
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        /* Everything built through UIValues reads its own property — only the controls that
         * aren't bound to one are filled here. */
        this.hotkey.setKeyCombo(new KeyCombo(IKey.EMPTY, form.hotkey.get()));

        if (form instanceof ModelForm m)
        {
            this.boneTracks.setValue(m.boneTracks.get());
            this.boneTracks.setVisible(true);
        }
        else
        {
            this.boneTracks.setVisible(false);
        }

        this.hitboxSneakMultiplier.setValue(form.hitboxSneakMultiplier.get());
        this.hitboxEyeHeight.setValue(form.hitboxEyeHeight.get());

        this.options.resize();
    }

    private void openTrackFilter()
    {
        if (this.form == null)
        {
            return;
        }

        Set<String> disabled = this.form.disabledTracks.get();
        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> keyToColor = new HashMap<>();

        for (TrackDescriptor track : TrackCatalog.of(this.form))
        {
            keys.add(track.filterKey());
            keyToColor.put(track.filterKey(), track.color());
        }

        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(disabled, keys, keyToColor);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

        panel.onClose(e -> this.form.disabledTracks.set(disabled));
    }
}