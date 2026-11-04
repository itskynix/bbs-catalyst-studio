package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.fx.ColorGradeClip;
import mchorse.bbs_mod.camera.clips.fx.CompoundFxClip;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.camera.clips.fx.GrainClip;
import mchorse.bbs_mod.camera.clips.fx.LensDistortionClip;
import mchorse.bbs_mod.camera.clips.fx.VignetteClip;
import mchorse.bbs_mod.camera.clips.fx.WorldWobbleClip;
import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.ReplayLookAt;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catalyst AI's director command engine. It reads a plain sentence, Turkish or English, picks the
 * intent out of its keywords and numbers, and edits the film directly: camera moves, effect stacks,
 * gaze and timeline edits. It runs locally and deterministically (the same sentence does the same
 * thing every time), and every change it makes comes with a way to take it back.
 */
public class CatalystAICommandExecutor
{
    /** What a command did: the report shown in the chat, and how to take it back (null when nothing changed). */
    public record Result(String report, boolean success, Runnable undo)
    {
        public static Result fail(String report)
        {
            return new Result(report, false, null);
        }
    }

    /* Word units take any Turkish suffix ("4 saniyelik", "20 tick'lik"); the one letter ones only stand alone */
    private static final Pattern TIME = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(saniye\\S*|sn(?![a-zçğıöşü])|seconds?|secs?|tick\\S*|s(?![a-zçğıöşü])|t(?![a-zçğıöşü]))");
    private static final Pattern BLOCKS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(blok\\S*|blocks?|metre\\S*|m(?![a-zçğıöşü]))");

    private final UIFilmPanel panel;

    /** The last clip a command added, for "extend the effect" with nothing selected */
    private Clip lastAdded;

    public CatalystAICommandExecutor(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    private static String t(String key, Object... args)
    {
        return L10n.lang("bbs.catalyst_ai.report." + key).format(args).get();
    }

    public Result execute(String input)
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return Result.fail(t("no_film"));
        }

        String text = input.toLowerCase(Locale.ROOT).replace('’', '\'').trim();

        if (text.isEmpty() || has(text, "yardım", "yardim", "help", "komutlar", "commands"))
        {
            return new Result(t("help"), true, null);
        }

        if (has(text, "bak", "look", "izle", "watch"))
        {
            Result look = this.lookAt(film, text);

            if (look != null)
            {
                return look;
            }
        }

        if (has(text, "kaydır", "kaydir", "shift", "taşı", "tasi", "move"))
        {
            return this.shift(film, text);
        }

        if (has(text, "kısalt", "kisalt", "shorten"))
        {
            return this.extend(film, text, true);
        }

        if (has(text, "uzat", "extend", "lengthen"))
        {
            return this.extend(film, text, false);
        }

        if (has(text, "korku", "horror", "gerilim", "dehşet", "dehset"))
        {
            return this.horror(film, text);
        }

        if (has(text, "girdap", "burgu", "twirl", "vortex"))
        {
            return this.wobble(film, text, true);
        }

        if (has(text, "dalga", "wave", "dalgalan", "wobble", "bükül", "bukul"))
        {
            return this.wobble(film, text, false);
        }

        if (has(text, "sars", "shake", "titre"))
        {
            return this.shake(film, text);
        }

        if (has(text, "yaklaş", "yaklas", "dolly", "push in", "zoom in", "approach"))
        {
            return this.dolly(film, text);
        }

        return Result.fail(t("unknown"));
    }

    /* Intents */

    private Result dolly(Film film, String text)
    {
        int duration = ticks(text, 80);
        float distance = blocks(text, 3F);
        DollyClip clip = new DollyClip();
        Camera camera = this.panel.getCamera();

        if (camera != null)
        {
            clip.fromCamera(camera);
        }

        clip.distance.set(distance);
        clip.interp.setInterp(Interpolations.CIRCLE_OUT);

        Runnable undo = this.addCameraClip(film, clip, duration);

        return new Result(t("dolly", seconds(duration), format(distance)), true, undo);
    }

    private Result horror(Film film, String text)
    {
        int duration = ticks(text, 200);
        CompoundFxClip compound = new CompoundFxClip();

        /* Cold, sickly green: crushed saturation, green shadows, heavy corners and a lens that bulges */
        ColorGradeClip grade = child(new ColorGradeClip(), duration, 0);

        grade.overlayColor.set(0x1E3A2A);
        key(grade.overlayAlpha, 0.25D);
        key(grade.saturation, 0.6D);
        key(grade.brightness, -0.06D);
        key(grade.contrast, 1.22D);
        key(grade.liftR, -0.05D);
        key(grade.liftG, 0.03D);
        key(grade.liftB, 0.02D);
        key(grade.gainR, 0.88D);
        key(grade.gainG, 1.04D);
        key(grade.gainB, 0.96D);

        GrainClip grain = child(new GrainClip(), duration, 1);

        key(grain.strength, 0.35D);
        key(grain.size, 1.2D);

        VignetteClip vignette = child(new VignetteClip(), duration, 2);

        vignette.color.set(0x020604);
        key(vignette.intensity, 0.7D);
        key(vignette.radius, 0.62D);
        key(vignette.softness, 0.55D);

        LensDistortionClip lens = child(new LensDistortionClip(), duration, 3);

        key(lens.distortion, 0.12D);
        key(lens.chromaticAberration, 0.15D);

        compound.children.addClip(grade);
        compound.children.addClip(grain);
        compound.children.addClip(vignette);
        compound.children.addClip(lens);
        compound.children.sortLayers();

        Runnable undo = this.addCameraClip(film, compound, duration);

        return new Result(t("horror", seconds(duration)), true, undo);
    }

    private Result shake(Film film, String text)
    {
        int duration = ticks(text, 40);
        ShakeClip clip = new ShakeClip();
        int preset = ShakeClip.PRESET_IMPACT;

        if (has(text, "hafif", "light", "handheld", "omuz"))
        {
            preset = ShakeClip.PRESET_HANDHELD;
        }
        else if (has(text, "panik", "panic"))
        {
            preset = ShakeClip.PRESET_PANIC;
        }

        clip.applyPreset(preset);

        Runnable undo = this.addCameraClip(film, clip, duration);

        return new Result(t("shake", seconds(duration)), true, undo);
    }

    private Result wobble(Film film, String text, boolean twirl)
    {
        int duration = ticks(text, 100);
        WorldWobbleClip clip = new WorldWobbleClip();

        clip.mode.set(twirl ? WorldWobbleClip.TWIRL : WorldWobbleClip.WAVE);

        Runnable undo = this.addCameraClip(film, clip, duration);

        return new Result(t(twirl ? "twirl" : "wave", seconds(duration)), true, undo);
    }

    private record Mention(Replay replay, int index, int length)
    {}

    /** "Alex'i Steve'e baktır": the first actor named looks at the second. Null when no actor is named at all. */
    private Result lookAt(Film film, String text)
    {
        List<Mention> mentions = new ArrayList<>();

        for (Replay replay : film.replays.getList())
        {
            Mention best = null;

            for (String alias : aliases(replay))
            {
                int index = text.indexOf(alias);

                if (index >= 0 && (best == null || index < best.index() || (index == best.index() && alias.length() > best.length())))
                {
                    best = new Mention(replay, index, alias.length());
                }
            }

            if (best != null)
            {
                mentions.add(best);
            }
        }

        if (mentions.size() < 2)
        {
            return mentions.isEmpty() ? null : Result.fail(t("look_need_two"));
        }

        mentions.sort(Comparator.comparingInt(Mention::index));

        Replay looker = mentions.get(0).replay();
        Replay target = mentions.get(1).replay();
        int tick = this.panel.getCursor();

        int oldMode = looker.lookAtMode.get();
        String oldTarget = looker.lookAtTarget.get();
        BaseType oldProperties = looker.properties.toData();

        looker.preNotify();
        looker.lookAtMode.set(ReplayLookAt.REPLAY);
        looker.lookAtTarget.set(target.getId());

        /* The eyes too: every framebuffer face in the looker's form is keyed at the cursor to track the target */
        int eyes = 0;

        for (FramebufferForm face : faces(looker.form.get(), new ArrayList<>()))
        {
            KeyframeChannel type = looker.properties.create(face.targetType);
            KeyframeChannel actor = looker.properties.create(face.targetActor);

            if (type != null && actor != null)
            {
                type.insert(tick, FramebufferForm.TARGET_ACTOR);
                actor.insert(tick, target.getName());
                eyes += 1;
            }
        }

        looker.postNotify();
        this.panel.fillData();

        Runnable undo = () ->
        {
            looker.preNotify();
            looker.lookAtMode.set(oldMode);
            looker.lookAtTarget.set(oldTarget);
            looker.properties.fromData(oldProperties);
            looker.postNotify();
            this.panel.fillData();
        };

        String report = eyes > 0
            ? t("look_eyes", looker.getName(), target.getName(), eyes)
            : t("look", looker.getName(), target.getName());

        return new Result(report, true, undo);
    }

    private Result shift(Film film, String text)
    {
        int amount = ticks(text, 0);

        if (amount == 0)
        {
            return Result.fail(t("need_amount"));
        }

        if (has(text, "geri", "sola", "back", "left", "earlier"))
        {
            amount = -Math.abs(amount);
        }

        List<Clip> clips = this.selected();
        boolean fallback = clips.isEmpty();

        if (fallback)
        {
            int cursor = this.panel.getCursor();

            for (Clip clip : film.camera.get())
            {
                if (clip.tick.get() >= cursor)
                {
                    clips.add(clip);
                }
            }
        }

        if (clips.isEmpty())
        {
            return Result.fail(t("nothing_to_edit"));
        }

        Runnable undo = this.snapshotCamera(film);

        film.camera.preNotify();

        for (Clip clip : clips)
        {
            clip.tick.set(Math.max(0, clip.tick.get() + amount));
        }

        film.camera.postNotify();
        this.panel.fillData();

        return new Result(t(fallback ? "shift_after_cursor" : "shift", clips.size(), amount), true, undo);
    }

    private Result extend(Film film, String text, boolean shorten)
    {
        int amount = Math.abs(ticks(text, 0));

        if (amount == 0)
        {
            return Result.fail(t("need_amount"));
        }

        List<Clip> clips = this.selected();

        if (clips.isEmpty() && this.lastAdded != null && film.camera.getIndex(this.lastAdded) >= 0)
        {
            clips.add(this.lastAdded);
        }

        if (clips.isEmpty())
        {
            int cursor = this.panel.getCursor();

            for (Clip clip : film.camera.get())
            {
                if (clip instanceof FxClip && clip.isInside(cursor))
                {
                    clips.add(clip);
                }
            }
        }

        if (clips.isEmpty())
        {
            return Result.fail(t("nothing_to_edit"));
        }

        Runnable undo = this.snapshotCamera(film);
        int delta = shorten ? -amount : amount;

        film.camera.preNotify();

        for (Clip clip : clips)
        {
            clip.duration.set(Math.max(1, clip.duration.get() + delta));
        }

        film.camera.postNotify();
        this.panel.fillData();

        return new Result(t(shorten ? "shorten" : "extend", clips.size(), seconds(amount)), true, undo);
    }

    /* Film edits */

    private Runnable addCameraClip(Film film, Clip clip, int duration)
    {
        Runnable undo = this.snapshotCamera(film);

        clip.tick.set(this.panel.getCursor());
        clip.duration.set(Math.max(1, duration));
        clip.layer.set(film.camera.findFreeLayer(clip));

        film.camera.preNotify();
        film.camera.addClip(clip);
        film.camera.sortLayers();
        film.camera.postNotify();

        this.lastAdded = clip;

        if (!this.panel.cameraEditor.clips.isNested())
        {
            this.panel.cameraEditor.pickClip(clip);
        }

        return undo;
    }

    /** The camera timeline as it is now; running the result puts it back. */
    private Runnable snapshotCamera(Film film)
    {
        BaseType data = film.camera.toData();

        return () ->
        {
            film.camera.preNotify();
            film.camera.fromData(data);
            film.camera.postNotify();

            this.lastAdded = null;
            this.panel.cameraEditor.setClips(film.camera);
            this.panel.cameraEditor.pickClip(null);
        };
    }

    /** The clips picked on the film's own camera timeline (not inside a compound clip). */
    private List<Clip> selected()
    {
        List<Clip> clips = new ArrayList<>(this.panel.cameraEditor.clips.getClipsFromSelection());

        if (clips.isEmpty() && this.panel.cameraEditor.getClip() != null)
        {
            clips.add(this.panel.cameraEditor.getClip());
        }

        Clips root = this.panel.getData().camera;

        clips.removeIf((c) -> root.getIndex(c) < 0);

        return clips;
    }

    /* Helpers */

    private static <T extends Clip> T child(T clip, int duration, int layer)
    {
        clip.tick.set(0);
        clip.duration.set(duration);
        clip.layer.set(layer);

        return clip;
    }

    private static void key(FxClip.Channel channel, double value)
    {
        channel.channel.insert(0, value);
    }

    private static List<FramebufferForm> faces(Form form, List<FramebufferForm> out)
    {
        if (form == null)
        {
            return out;
        }

        if (form instanceof FramebufferForm face)
        {
            out.add(face);
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            faces(part.getForm(), out);
        }

        return out;
    }

    /** What an actor can be called in a sentence: its name, label and name tag, and the last part of a model path. */
    private static List<String> aliases(Replay replay)
    {
        List<String> aliases = new ArrayList<>();

        for (String name : new String[] {replay.getName(), replay.label.get(), replay.nameTag.get()})
        {
            if (name == null || name.isBlank() || name.equals("-"))
            {
                continue;
            }

            String lower = name.toLowerCase(Locale.ROOT).trim();

            aliases.add(lower);

            int slash = lower.lastIndexOf('/');

            if (slash >= 0 && slash < lower.length() - 1)
            {
                aliases.add(lower.substring(slash + 1));
            }
        }

        return aliases;
    }

    private static boolean has(String text, String... words)
    {
        for (String word : words)
        {
            if (text.contains(word))
            {
                return true;
            }
        }

        return false;
    }

    /** A duration in the sentence, in ticks: "4 saniye", "4s", "80 tick". */
    static int ticks(String text, int fallback)
    {
        Matcher matcher = TIME.matcher(text);

        if (!matcher.find())
        {
            return fallback;
        }

        double value = Double.parseDouble(matcher.group(1).replace(',', '.'));

        return (int) Math.round(matcher.group(2).startsWith("t") ? value : value * 20D);
    }

    static float blocks(String text, float fallback)
    {
        Matcher matcher = BLOCKS.matcher(text);

        return matcher.find() ? Float.parseFloat(matcher.group(1).replace(',', '.')) : fallback;
    }

    private static String seconds(int ticks)
    {
        return format(ticks / 20F);
    }

    private static String format(float value)
    {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
