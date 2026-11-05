package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What a replay looks like from inside its own head: the interface drawn over the view, the
 * first-person hands, and where the eye points. Each page is a group of its own channels, which
 * the POV timeline shows instead of the replay's — the hotbar slots and the head rotation are not
 * repeated here, the POV timeline borrows the replay's own channels for them.
 *
 * <p>An unkeyed channel means "as the game would draw it", so a replay nobody animated in first
 * person plays back exactly as before.
 */
public class ReplayPov extends ValueGroup
{
    public final Page hotbar = new Page("hotbar");
    public final Page hands = new Page("hands");
    public final Page vision = new Page("vision");

    /** The overlay clips of the POV (interfaces, boss bars, toasts...), on a timeline of their own. */
    public final Clips actions = new Clips("actions", BBSMod.getFactoryPovActionClips());

    /** The form whose arms are seen in first person; none means the replay's own form. */
    public final ValueForm handForm = new ValueForm("hand_form");
    /** Where the arms, their tips, items and armor sleeves sit in the view. */
    public final PovRig rig = new PovRig("rig");
    /** Forms seen only in first person, each on a bone of the rig. */
    public final PovBodyParts bodyParts = new PovBodyParts("body_parts");
    /** The head kept within the turn a body allows, rather than spinning free of it. */
    public final ValueBoolean hardcoreLook = new ValueBoolean("hardcore_look", false);
    /** The view jolted when the replay is hurt, as the game jolts it. */
    public final ValueBoolean cameraShake = new ValueBoolean("camera_shake", true);

    /* Hotbar: the interface as a whole */
    public final KeyframeChannel<Boolean> hudVisible = this.hotbar.add("hud_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> statusBars = this.hotbar.add("status_bars", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> crosshair = this.hotbar.add("crosshair", KeyframeFactories.BOOLEAN);
    /** The game mode, by the game's numbers (0 survival, 1 creative, 2 adventure, 3 spectator): it says what the interface shows */
    public final KeyframeChannel<Integer> gameMode = this.hotbar.add("game_mode", KeyframeFactories.INTEGER);

    /* Hotbar: the 27 slots of the inventory above the hotbar, as one group (the hotbar itself is the replay's own slots) */
    public final KeyframeChannel<mchorse.bbs_mod.film.pov.PovSlotSet> inventorySlots = this.hotbar.add("inventory_slots", KeyframeFactories.POV_SLOTS);

    /* Hotbar: the cursor of an open inventory (in interface units from the screen's middle), and what it carries */
    public final KeyframeChannel<Boolean> cursorVisible = this.hotbar.add("cursor_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Double> cursorX = this.hotbar.add("cursor_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> cursorY = this.hotbar.add("cursor_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<ItemStack> cursorItem = this.hotbar.add("cursor_item", KeyframeFactories.ITEM_STACK);

    /* Hotbar: the status bars */
    public final KeyframeChannel<Double> health = this.hotbar.add("health", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> maxHealth = this.hotbar.add("max_health", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> absorption = this.hotbar.add("absorption", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Boolean> hardcore = this.hotbar.add("hardcore", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Double> armor = this.hotbar.add("armor", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> hunger = this.hotbar.add("hunger", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> air = this.hotbar.add("air", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> mountHealth = this.hotbar.add("mount_health", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> experience = this.hotbar.add("experience", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Integer> experienceLevel = this.hotbar.add("experience_level", KeyframeFactories.INTEGER);

    /* Hands: which of them are drawn */
    public final KeyframeChannel<Boolean> rightHandVisible = this.hands.add("right_hand_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Boolean> leftHandVisible = this.hands.add("left_hand_visible", KeyframeFactories.BOOLEAN);

    /* Hands: the swing of an attack and the dip of a hand changing what it holds, 0..1 */
    public final KeyframeChannel<Double> rightSwing = this.hands.add("right_swing", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> leftSwing = this.hands.add("left_swing", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> mainEquip = this.hands.add("main_equip", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> offhandEquip = this.hands.add("offhand_equip", KeyframeFactories.DOUBLE);

    /* Hands: the walking bob — where in the step it is and how far it swings */
    public final KeyframeChannel<Double> bobPhase = this.hands.add("bob_phase", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> bobStrength = this.hands.add("bob_strength", KeyframeFactories.DOUBLE);

    /* Hands: the lag of the hands behind a turning view, in degrees */
    public final KeyframeChannel<Double> renderPitch = this.hands.add("render_pitch", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> renderYaw = this.hands.add("render_yaw", KeyframeFactories.DOUBLE);

    /* Vision: the eye on top of the head, in degrees, and the lens */
    public final KeyframeChannel<Double> eyePitch = this.vision.add("eye_pitch", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> eyeYaw = this.vision.add("eye_yaw", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> eyeRoll = this.vision.add("eye_roll", KeyframeFactories.DOUBLE);
    /* Vision: the eye moved off its place on the head, in the head's own axes, in blocks */
    public final KeyframeChannel<Double> eyeOffsetX = this.vision.add("eye_offset_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> eyeOffsetY = this.vision.add("eye_offset_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> eyeOffsetZ = this.vision.add("eye_offset_z", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> fov = this.vision.add("fov", KeyframeFactories.DOUBLE);

    public ReplayPov(String id)
    {
        super(id);

        this.add(this.hotbar);
        this.add(this.hands);
        this.add(this.vision);
        this.add(this.actions);
        this.add(this.handForm);
        this.add(this.rig);
        this.add(this.bodyParts);
        this.add(this.hardcoreLook);
        this.add(this.cameraShake);
    }

    /**
     * Whose point of view can be taken: the player-shaped models, which have hands to show and a
     * head to look out of.
     */
    public static boolean supports(Replay replay)
    {
        if (replay == null || !(replay.form.get() instanceof ModelForm form))
        {
            return false;
        }

        String model = form.model.get();

        return model.startsWith("player/") || model.startsWith("emoticons/");
    }

    public List<KeyframeChannel<?>> getChannels()
    {
        List<KeyframeChannel<?>> channels = new ArrayList<>();

        channels.addAll(this.hotbar.getChannels());
        channels.addAll(this.hands.getChannels());
        channels.addAll(this.vision.getChannels());

        return channels;
    }

    public void shift(float tick)
    {
        for (KeyframeChannel<?> channel : this.getChannels())
        {
            for (Keyframe<?> keyframe : channel.getKeyframes())
            {
                keyframe.setTick(keyframe.getTick() + tick);
            }
        }

        this.actions.shift(tick);
    }

    /** One page of the POV timeline; its channels in the order they are listed. */
    public static class Page extends ValueGroup
    {
        public Page(String id)
        {
            super(id);
        }

        private <T> KeyframeChannel<T> add(String id, IKeyframeFactory<T> factory)
        {
            KeyframeChannel<T> channel = new KeyframeChannel<>(id, factory);

            this.add(channel);

            return channel;
        }

        public List<KeyframeChannel<?>> getChannels()
        {
            List<KeyframeChannel<?>> channels = new ArrayList<>();

            for (BaseValue value : this.getAll())
            {
                if (value instanceof KeyframeChannel<?> channel)
                {
                    channels.add(channel);
                }
            }

            return channels;
        }
    }
}
