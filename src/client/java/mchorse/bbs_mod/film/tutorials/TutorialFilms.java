package mchorse.bbs_mod.film.tutorials;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.fx.ColorGradeClip;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.camera.clips.fx.GrainClip;
import mchorse.bbs_mod.camera.clips.fx.LensDistortionClip;
import mchorse.bbs_mod.camera.clips.fx.LetterboxClip;
import mchorse.bbs_mod.camera.clips.fx.ScreenShakeClip;
import mchorse.bbs_mod.camera.clips.fx.VignetteClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.clips.modifiers.DollyZoomClip;
import mchorse.bbs_mod.camera.clips.modifiers.FovPunchClip;
import mchorse.bbs_mod.camera.clips.modifiers.OrbitClip;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.physics.actions.ImpulseActionClip;
import mchorse.bbs_mod.physics.collision.CollisionSlot;
import mchorse.bbs_mod.physics.collision.FormCollision;
import mchorse.bbs_mod.physics.collision.FormCollisions;
import mchorse.bbs_mod.physics.forms.PhysicsForms;
import mchorse.bbs_mod.physics.forms.PhysicsType;
import mchorse.bbs_mod.physics.ragdoll.ActiveKnob;
import mchorse.bbs_mod.physics.ragdoll.ActiveRagdolls;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The tutorial films the mod ships: ready made scenes that show a new feature set up and working,
 * opened read only in the film editor (see {@code UIFilmPanel#openTutorial}) and turned into an
 * ordinary film of the author's with "Unpack Film".
 *
 * <p><b>Built, not stored.</b> Each one is put together here out of the very values the editor
 * makes (replays, forms, keyframes, camera, effect and action clips), so it always matches what
 * this build reads and writes, and nothing has to be migrated when the film format moves on. A
 * film's place in the world is the only input: everything is laid out around {@code origin}, the
 * block the author stood on when they first opened it this session, so it plays in front of them
 * wherever they are. The same origin always builds the same film, bit for bit: the replays have
 * fixed ids, nothing reads the clock or a random number, and every time is in ticks.</p>
 *
 * <p>More can be added with {@link #register}; the film editor's list shows whatever is here.</p>
 */
public final class TutorialFilms
{
    /** What a tutorial film's id starts with. It has no slash and a colon, so it can never be the path of a saved film. */
    public static final String PREFIX = "bbs_tutorial:";

    /** One tutorial: its key (the end of its id and of its language keys), its icon and how it is built. */
    public record Tutorial(String key, Icon icon, Builder builder)
    {
        public String id()
        {
            return PREFIX + this.key;
        }

        public IKey title()
        {
            return L10n.lang("bbs.ui.film.tutorials." + this.key + ".title");
        }

        public IKey description()
        {
            return L10n.lang("bbs.ui.film.tutorials." + this.key + ".description");
        }
    }

    /** Builds a tutorial film around a block of the world. */
    public interface Builder
    {
        void build(Film film, int x, int y, int z);
    }

    private static final List<Tutorial> ALL = new ArrayList<>();

    static
    {
        register("physics", Icons.PHYSICS, TutorialFilms::physics);
        register("effects", Icons.SPARKLES, TutorialFilms::effects);
        register("cameras", Icons.CAMERA, TutorialFilms::cameras);
    }

    private TutorialFilms()
    {}

    public static void register(String key, Icon icon, Builder builder)
    {
        ALL.removeIf((tutorial) -> tutorial.key.equals(key));
        ALL.add(new Tutorial(key, icon, builder));
    }

    public static List<Tutorial> all()
    {
        return Collections.unmodifiableList(ALL);
    }

    public static boolean isTutorial(String id)
    {
        return id != null && id.startsWith(PREFIX);
    }

    public static Tutorial get(String id)
    {
        for (Tutorial tutorial : ALL)
        {
            if (tutorial.id().equals(id))
            {
                return tutorial;
            }
        }

        return null;
    }

    /** The tutorial film of {@code id} laid out around the block {@code x y z}, or null when there is no such tutorial. */
    public static Film build(String id, int x, int y, int z)
    {
        Tutorial tutorial = get(id);

        if (tutorial == null)
        {
            return null;
        }

        Film film = new Film();

        film.setId(id);
        film.description.set(tutorial.description().get());
        tutorial.builder.build(film, x, y, z);

        return film;
    }

    /* The films */

    /**
     * New Physics: two characters with active physics. The left one stands and takes a shove at
     * tick 40: it staggers, balances and recovers. The right one is thrown at tick 70: it braces
     * before the ground, lands, lies still, and gets back up.
     */
    private static void physics(Film film, int x, int y, int z)
    {
        int duration = 260;

        idle(film, duration, x + 0.5, y + 2.4, z - 4.5, 180F, 14F, 70F);

        Replay left = actor(film, "a0000001", key("physics.left"), x - 1.5, y, z + 3.5, 180F);
        Replay right = actor(film, "a0000002", key("physics.right"), x + 2.5, y, z + 3.5, 180F);

        activeRagdoll(left, 0.8F, 50F, 0.6F);
        activeRagdoll(right, 0.6F, 40F, 0.5F);

        /* A shove sideways at the left one's chest: a stagger it recovers from */
        impulse(left, 40, x - 2.5, y + 1.2, z + 3.5, false, 1F, 0F, 0F, 4F, 2F);
        /* A blast under the right one's side: off its feet, a brace, a landing, and up again */
        impulse(right, 70, x + 2.0, y + 0.3, z + 3.5, false, 0.35F, 1F, 0F, 9F, 2.5F);

        subtitle(film, 0, 70, key("physics.step1"));
        subtitle(film, 70, 90, key("physics.step2"));
        subtitle(film, 160, duration - 160, key("physics.step3"));
    }

    /** New Effects: one character, and the screen effects one after the other, each named as it plays. */
    private static void effects(Film film, int x, int y, int z)
    {
        int step = 40;
        String[] names = {"effects.color_grade", "effects.vignette", "effects.letterbox", "effects.grain", "effects.shake", "effects.lens"};
        int duration = step * names.length;

        idle(film, duration, x + 0.5, y + 1.9, z - 2.5, 180F, 8F, 60F);
        actor(film, "a0000001", key("effects.actor"), x + 0.5, y, z + 2.5, 180F);

        ColorGradeClip grade = new ColorGradeClip();

        grade.overlayColor.set(0x3a5f9a);
        value(grade.overlayAlpha, 0.18D);
        value(grade.saturation, 0.45D);
        value(grade.contrast, 1.2D);

        VignetteClip vignette = new VignetteClip();

        value(vignette.intensity, 0.85D);
        value(vignette.radius, 0.6D);

        LetterboxClip letterbox = new LetterboxClip();

        GrainClip grain = new GrainClip();

        value(grain.strength, 0.6D);

        ScreenShakeClip shake = new ScreenShakeClip();

        value(shake.amplitude, 0.7D);

        LensDistortionClip lens = new LensDistortionClip();

        value(lens.distortion, 0.35D);
        value(lens.chromaticAberration, 0.5D);

        FxClip[] effects = {grade, vignette, letterbox, grain, shake, lens};

        for (int i = 0; i < effects.length; i++)
        {
            place(film, effects[i], i * step, step, 1);
            subtitle(film, i * step, step, key(names[i]));
        }
    }

    /** New Cameras: a dolly in, an orbit round the actor, a dolly zoom and a FOV punch, each named as it plays. */
    private static void cameras(Film film, int x, int y, int z)
    {
        int step = 60;
        int duration = step * 4;
        double ax = x + 0.5;
        double az = z + 3.5;

        Replay actor = actor(film, "a0000001", key("cameras.actor"), ax, y, az, 180F);

        /* The base shot the modifiers work on, the whole film long */
        idle(film, duration, ax, y + 1.7, az - 5, 180F, 4F, 60F);

        DollyClip dolly = new DollyClip();

        dolly.position.set(new Position((float) ax, (float) (y + 1.7), (float) (az - 7), 180F, 4F, 0F, 60F));
        dolly.distance.set(4F);
        place(film, dolly, 0, step, 1);

        OrbitClip orbit = new OrbitClip();

        orbit.selector.set(actor.getId());
        orbit.distance.set(4F);
        orbit.yaw.set(60F);
        orbit.pitch.set(15F);
        place(film, orbit, step, step, 2);

        DollyZoomClip zoom = new DollyZoomClip();

        zoom.focus.set(5F);
        place(film, zoom, step * 2, step, 2);

        FovPunchClip punch = new FovPunchClip();

        punch.intensity.set(14F);
        punch.punchDuration.set(12F);
        place(film, punch, step * 3 + 10, step - 10, 2);

        subtitle(film, 0, step, key("cameras.dolly"));
        subtitle(film, step, step, key("cameras.orbit"));
        subtitle(film, step * 2, step, key("cameras.dolly_zoom"));
        subtitle(film, step * 3, step, key("cameras.fov_punch"));
    }

    /* Pieces */

    private static String key(String id)
    {
        return L10n.lang("bbs.ui.film.tutorials." + id).get();
    }

    /** The base camera: still, at a place, the whole film long. */
    private static void idle(Film film, int duration, double x, double y, double z, float yaw, float pitch, float fov)
    {
        IdleClip idle = new IdleClip();

        idle.position.set(new Position((float) x, (float) y, (float) z, yaw, pitch, 0F, fov));
        place(film, idle, 0, duration, 0);
    }

    private static void place(Film film, CameraClip clip, int tick, int duration, int layer)
    {
        clip.tick.set(tick);
        clip.duration.set(duration);
        clip.layer.set(layer);
        film.camera.addClip(clip);
    }

    /** A line of text along the bottom of the frame. */
    private static void subtitle(Film film, int tick, int duration, String text)
    {
        SubtitleClip subtitle = new SubtitleClip();
        Placement placement = new Placement(SubtitleClip.DEFAULT_SCALE);

        placement.windowY = 0.88F;
        placement.anchorY = 1F;
        subtitle.placement.set(placement);
        subtitle.title.set(text);
        subtitle.background.set(0x99000000);
        subtitle.maxWidth.set(420);
        place(film, subtitle, tick, duration, 3);
    }

    /** One effect value held for the whole clip: a single keyframe at its start. */
    private static void value(FxClip.Channel channel, double value)
    {
        channel.channel.insert(0F, value);
    }

    /** A Steve standing at a place, facing the camera, with a fixed id so the film builds the same each time. */
    private static Replay actor(Film film, String id, String label, double x, double y, double z, float yaw)
    {
        Replay replay = new Replay(id);
        ModelForm form = new ModelForm();

        form.model.set("player/steve");
        replay.form.set(form);
        replay.label.set(label);
        replay.keyframes.x.insert(0F, x);
        replay.keyframes.y.insert(0F, y);
        replay.keyframes.z.insert(0F, z);
        replay.keyframes.yaw.insert(0F, (double) yaw);
        replay.keyframes.headYaw.insert(0F, (double) yaw);
        replay.keyframes.bodyYaw.insert(0F, (double) yaw);
        film.replays.add(replay);

        return replay;
    }

    /**
     * Makes the actor's form a ragdoll with active physics: the six body bones marked up the way
     * the collision tab's automatic pass marks them, held by the animation (handle 1), and the
     * active controller on at full strength.
     */
    private static void activeRagdoll(Replay replay, float balance, float fallAngle, float recovery)
    {
        if (!(replay.form.get() instanceof ModelForm form))
        {
            return;
        }

        FormCollision collision = FormCollision.EMPTY;

        for (String bone : new String[] {"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"})
        {
            collision = collision.with(bone, CollisionSlot.AUTO);
        }

        FormCollisions.set(form, collision);
        PhysicsForms.setType(form, PhysicsType.RAGDOLL);
        PhysicsForms.setAuthority(form, 1F);
        ActiveRagdolls.set(form, ActiveKnob.STRENGTH, 1F);
        ActiveRagdolls.set(form, ActiveKnob.BALANCE, balance);
        ActiveRagdolls.set(form, ActiveKnob.FALL_ANGLE, fallAngle);
        ActiveRagdolls.set(form, ActiveKnob.RECOVERY, recovery);
        ActiveRagdolls.set(form, ActiveKnob.REFLEX, 0.8F);
        ActiveRagdolls.set(form, ActiveKnob.IMPACT, 0.8F);
    }

    /** A push on one actor alone at a tick, in world coordinates: directed along {@code dx dy dz}, or radial. */
    private static void impulse(Replay replay, int tick, double x, double y, double z, boolean radial, float dx, float dy, float dz, float strength, float radius)
    {
        ImpulseActionClip clip = new ImpulseActionClip();

        clip.tick.set(tick);
        clip.duration.set(1);
        clip.onlyThisReplay.set(true);
        clip.point.get().set(x, y, z);
        clip.radial.set(radial);
        clip.direction.get().set(dx, dy, dz);
        clip.strength.set(strength);
        clip.radius.set(radius);
        replay.actions.addClip(clip);
    }
}
