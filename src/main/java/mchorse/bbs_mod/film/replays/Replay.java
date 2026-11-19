package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.migration.FormStableIds;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.camera.data.Point;
import org.joml.Vector3d;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.categories.CategoryPath;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.entity.LivingEntity;

import java.util.List;

public class Replay extends ValueGroup
{
    public final ValueForm form = new ValueForm("form");
    public final ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public final FormProperties properties = new FormProperties("properties");
    public final Clips actions = new Clips("actions", BBSMod.getFactoryActionClips());
    /** The replay seen from inside its own head; see {@link ReplayPov}. */
    public final ReplayPov pov = new ReplayPov("pov");

    public final ValueBoolean enabled = new ValueBoolean("enabled", true);
    /**
     * Path of the folder this replay sits in for the replay list, {@code "Crowd/Guards"};
     * empty is the root. See {@link CategoryPath} for why the path itself is the address.
     */
    public final ValueString category = new ValueString("category", "");
    public final ValueString label = new ValueString("label", "");
    public final ValueString nameTag = new ValueString("name_tag", "");
    public final ValueBoolean shadow = new ValueBoolean("shadow", true);
    public final ValueFloat shadowSize = new ValueFloat("shadow_size", 0.5F);
    /** When enabled, the shadow follows the form's perceived position (anchor/transform motion). */
    public final ValueBoolean shadowFollow = new ValueBoolean("shadow_follow", false);
    /** Extra world-space offset added to the followed shadow position (corrects a model's shifted floor level). */
    public final ValuePoint shadowOffset = new ValuePoint("shadow_offset", new Point(0, 0, 0));
    public final ValueInt looping = new ValueInt("looping", 0);

    public final ValueBoolean actor = new ValueBoolean("actor", false);
    /** Whether the actor's body sweeps up items it walks over. What it takes is given back when the film stops. */
    public final ValueBoolean actorPickup = new ValueBoolean("actor_pickup", true);
    public final ValueBoolean fp = new ValueBoolean("fp", false);
    public final ValueBoolean relative = new ValueBoolean("relative", false);
    public final ValuePoint relativeOffset = new ValuePoint("relativeOffset", new Point(0, 0, 0));

    /** What the actor looks at: 0 nothing, 1 another replay, 2 the camera, 3 a point. Weighted by the look_at_weight track. */
    public final ValueInt lookAtMode = new ValueInt("look_at_mode", 0);
    /** Id of the replay looked at, in mode 1 */
    public final ValueString lookAtTarget = new ValueString("look_at_target", "");
    /** The point looked at in mode 3, unless the look_at_x/y/z tracks have keys */
    public final ValuePoint lookAtPoint = new ValuePoint("look_at_point", new Point(0, 0, 0));

    /** Turning the head to a passing character and back, worked out while playing; see {@link ReplayCrowdGlance} */
    public final ReplayCrowdGlance crowdGlance = new ReplayCrowdGlance("crowd_glance");

    /* Interactive openables: while this replay is being recorded, a door, trapdoor, fence gate or chest is
     * opened by holding use and moving the mouse, a share of the way at a time, instead of at once */
    public final ValueBoolean interactiveOpenables = new ValueBoolean("interactive_openables", false);
    /** How far an openable swings when fully open, in degrees while it is being moved */
    public final ValueFloat openableAngle = new ValueFloat("openable_angle", 90F, 15F, 135F);
    /** How far it must be opened (0.1 to 0.9 of the way) for letting go to finish the opening; below it closes again */
    public final ValueFloat openableThreshold = new ValueFloat("openable_threshold", 0.5F, 0.1F, 0.9F);

    /* Locomotion smoothing: the actor speeds up, turns and stops with some inertia on the screen, and lands exactly on its keyed place */
    public final ValueBoolean locomotionSmoothing = new ValueBoolean("locomotion_smoothing", false);
    /** The time the smoothing takes to settle, in seconds */
    public final ValueFloat locomotionTime = new ValueFloat("locomotion_time", 0.3F, 0.05F, 2F);

    /* AI: the actor walks about by itself inside the film's navigation volume, waiting a little at each place it gets to */
    public final ValueBoolean hasAi = new ValueBoolean("has_ai", false);
    public final ValueFloat aiIdleMin = new ValueFloat("ai_idle_min", 1F, 0F, 120F);
    public final ValueFloat aiIdleMax = new ValueFloat("ai_idle_max", 4F, 0F, 120F);
    /** Walking speed, in blocks per second */
    public final ValueFloat aiSpeed = new ValueFloat("ai_speed", 1.6F, 0.3F, 8F);
    /** Another number is another walk */
    public final ValueInt aiSeed = new ValueInt("ai_seed", 0, 0, 9999);
    public final ValueBoolean aiAvoidance = new ValueBoolean("ai_avoidance", true);
    /** Turn to face the way it moves */
    public final ValueBoolean orientToMovement = new ValueBoolean("orient_to_movement", true);
    /** Turn to face where it is heading for, the controller's wish, which wins over the way it moves */
    public final ValueBoolean useControllerDesiredRotation = new ValueBoolean("use_controller_desired_rotation", false);
    /** How fast it turns, in degrees per second */
    public final ValueFloat rotationRate = new ValueFloat("rotation_rate", 360F, 30F, 1080F);

    public final ValueBoolean axesPreview = new ValueBoolean("axes_preview", false);
    public final ValueString axesPreviewBone = new ValueString("axes_preview_bone", "");

    public Replay(String id)
    {
        super(id);

        this.add(this.form);
        this.add(this.keyframes);
        this.add(this.properties);
        this.add(this.actions);
        this.add(this.pov);

        this.add(this.enabled);
        this.add(this.category);
        this.add(this.label);
        this.add(this.nameTag);
        this.add(this.shadow);
        this.add(this.shadowSize);
        this.add(this.shadowFollow);
        this.add(this.shadowOffset);
        this.add(this.looping);

        this.add(this.actor);
        this.add(this.actorPickup);
        this.add(this.fp);
        this.add(this.relative);
        this.add(this.relativeOffset);

        this.add(this.lookAtMode);
        this.add(this.lookAtTarget);
        this.add(this.lookAtPoint);
        this.add(this.crowdGlance);

        this.add(this.interactiveOpenables);
        this.add(this.openableAngle);
        this.add(this.openableThreshold);
        this.add(this.locomotionSmoothing);
        this.add(this.locomotionTime);

        this.add(this.hasAi);
        this.add(this.aiIdleMin);
        this.add(this.aiIdleMax);
        this.add(this.aiSpeed);
        this.add(this.aiSeed);
        this.add(this.aiAvoidance);
        this.add(this.orientToMovement);
        this.add(this.useControllerDesiredRotation);
        this.add(this.rotationRate);

        this.add(this.axesPreview);
        this.add(this.axesPreviewBone);
    }

    /**
     * Where this replay's own frame sits when it is {@link #relative}: its first keyframe plus the
     * authored offset. A relative replay is built around a fixed origin instead of around wherever
     * the camera happens to be, so every consumer that places it has to ask the same question — the
     * renderer, the bone matrices and the motion path all did it with their own copy of this sum.
     */
    public Vector3d getRelativeOrigin()
    {
        Point offset = this.relativeOffset.get();

        return new Vector3d(
            this.keyframes.x.interpolate(0F) + offset.x,
            this.keyframes.y.interpolate(0F) + offset.y,
            this.keyframes.z.interpolate(0F) + offset.z
        );
    }


    /**
     * Normalizes a user-supplied folder path; see {@link CategoryPath#normalize(String)}.
     */
    public static String normalizeCategory(String raw)
    {
        return CategoryPath.normalize(raw);
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isMap())
        {
            FormStableIds.ensureReplay(data.asMap());
        }

        super.fromData(data);
    }

    public String getName()
    {
        String label = this.label.get();

        if (!label.isEmpty())
        {
            return label;
        }

        Form form = this.form.get();

        if (form == null)
        {
            return "-";
        }

        return form.getDisplayName();
    }

    public void shift(float tick)
    {
        this.keyframes.shift(tick);
        this.properties.shift(tick);
        this.actions.shift(tick);
        this.pov.shift(tick);
    }

    /**
     * The tick of the replay's last keyframe over its own channels and every property track —
     * where its authored motion ends; {@code -1} when it has no keyframes at all.
     */
    public float getLastKeyframeTick()
    {
        float last = -1F;

        for (KeyframeChannel<?> channel : this.keyframes.getChannels())
        {
            last = Math.max(last, lastTick(channel));
        }

        for (KeyframeChannel<?> channel : this.properties.tracks.values())
        {
            last = Math.max(last, lastTick(channel));
        }

        for (KeyframeChannel<?> channel : this.pov.getChannels())
        {
            last = Math.max(last, lastTick(channel));
        }

        return last;
    }

    private static float lastTick(KeyframeChannel<?> channel)
    {
        List<?> keyframes = channel.getKeyframes();

        return keyframes.isEmpty() ? -1F : ((Keyframe<?>) keyframes.get(keyframes.size() - 1)).getTick();
    }

    public void applyActions(LivingEntity actor, SuperFakePlayer fakePlayer, Film film, int tick)
    {
        List<Clip> clips = this.actions.getClips(tick);

        for (Clip clip : clips)
        {
            ((ActionClip) clip).apply(actor, fakePlayer, film, this, tick);
        }
    }

    public void applyClientActions(int tick, IEntity entity, Film film)
    {
        tick = this.getTick(tick);

        List<Clip> clips = this.actions.getClips(tick);

        for (Clip clip : clips)
        {
            if (clip instanceof ActionClip actionClip && actionClip.isClient())
            {
                actionClip.applyClient(entity, film, this, tick);
            }
        }
    }

    public int getTick(int tick)
    {
        return this.looping.get() > 0 ? tick % this.looping.get() : tick;
    }
}
