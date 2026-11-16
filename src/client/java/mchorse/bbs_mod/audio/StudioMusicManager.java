package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UICentralHubPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.hub.UICommunityHubPanel;
import mchorse.bbs_mod.ui.dashboard.panels.motion.UIMMotionPanel;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.utils.MathUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The music under the studio's workspaces: one instrumental track for each kind of workspace, played
 * in a loop through its own 2D stereo source while the dashboard is open.
 *
 * <p>Moving to another kind of workspace crossfades over about a second: the old track fades out as
 * the new one fades in. Moving between workspaces of the same kind leaves the track alone, playing from
 * where it is. While something is playing or being recorded in the editor the music is ducked to nothing
 * in 0.4 seconds, and comes back when it stops. A track that is not there is skipped without a word.</p>
 *
 * <p>Each kind has a list of tracks, so more can be added later; with one track in the list it loops on
 * the source itself, with several they follow each other and the list goes round.</p>
 */
public class StudioMusicManager
{
    public enum Category
    {
        /** The welcome screen and the central hub */
        HUB("hub"),
        CATALYST("catalyst"),
        /** The film editor and mMotion */
        FILM("film"),
        /** Particles, models and textures */
        WORKSHOP("workshop");

        public final String id;

        Category(String id)
        {
            this.id = id;
        }
    }

    private static final StudioMusicManager INSTANCE = new StudioMusicManager();

    private static final float CROSSFADE_SECONDS = 1.2F;
    private static final float DUCK_SECONDS = 0.4F;

    public static StudioMusicManager get()
    {
        return INSTANCE;
    }

    /** The category a dashboard panel plays the music of, or null for a panel that leaves the music as it is */
    public static Category categoryOf(UIDashboardPanel panel)
    {
        if (panel instanceof UICentralHubPanel || panel instanceof UICommunityHubPanel)
        {
            return Category.HUB;
        }
        else if (panel instanceof UICatalystPanel)
        {
            return Category.CATALYST;
        }
        else if (panel instanceof UIFilmPanel || panel instanceof UIMMotionPanel)
        {
            return Category.FILM;
        }
        else if (panel instanceof UIParticleSchemePanel || panel instanceof UIModelBlockPanel || panel instanceof UITextureManagerPanel)
        {
            return Category.WORKSHOP;
        }

        return null;
    }

    /** What is playing or on its way in or out: one track of one category */
    private static class Voice
    {
        final Category category;
        int index;
        Link link;
        SoundBuffer buffer;
        SoundPlayer player;
        /** How much of its volume it has, 0 to 1, moving towards the target */
        float level;
        float target = 1F;
        volatile Wave decoded;
        volatile boolean loading;
        boolean failed;

        Voice(Category category)
        {
            this.category = category;
        }

        void release()
        {
            if (this.player != null)
            {
                this.player.stop();
                this.player.delete();
                this.player = null;
            }

            if (this.buffer != null)
            {
                this.buffer.delete();
                this.buffer = null;
            }
        }
    }

    private final Map<Category, List<Link>> tracks = new EnumMap<>(Category.class);
    private final List<Voice> leaving = new ArrayList<>();

    private Voice current;
    private Category wanted;
    private float duck = 1F;
    private boolean ducked;
    private long last;

    private StudioMusicManager()
    {
        for (Category category : Category.values())
        {
            List<Link> list = new ArrayList<>();

            list.add(Link.bbs("sounds/studio_music/" + category.id + ".ogg"));
            this.tracks.put(category, list);
        }
    }

    /** More tracks for a kind of workspace, played one after the other */
    public void addTrack(Category category, Link link)
    {
        this.tracks.get(category).add(link);
    }

    /** The workspace on screen changed: its kind of music takes over, unless it is already the one playing */
    public void setCategory(Category category)
    {
        if (category == null || category == this.wanted)
        {
            return;
        }

        this.wanted = category;
    }

    /** The editor is playing or recording: the music gives way */
    public void setDucked(boolean ducked)
    {
        this.ducked = ducked;
    }

    /** The dashboard closes: the music stops and its sources are freed */
    public void stop()
    {
        this.wanted = null;
        this.releaseAll();
    }

    private void releaseAll()
    {
        if (this.current != null)
        {
            this.current.release();
            this.current = null;
        }

        for (Voice voice : this.leaving)
        {
            voice.release();
        }

        this.leaving.clear();
        this.last = 0L;
    }

    /** Once per rendered frame while the dashboard is open */
    public void frame()
    {
        long now = System.nanoTime();
        float dt = this.last == 0L ? 0F : Math.min(0.1F, (now - this.last) / 1.0E9F);

        this.last = now;

        if (!BBSSettings.studioMusicEnabled.get() || this.wanted == null)
        {
            if (this.current != null || !this.leaving.isEmpty())
            {
                this.releaseAll();
            }

            return;
        }

        /* A different kind of workspace: the track playing leaves, the new one comes in */
        if (this.current == null || this.current.category != this.wanted)
        {
            if (this.current != null)
            {
                this.current.target = 0F;
                this.leaving.add(this.current);
            }

            this.current = new Voice(this.wanted);
        }

        this.duck = MathUtils.clamp(this.duck + (this.ducked ? -dt : dt) / DUCK_SECONDS, 0F, 1F);

        this.update(this.current, dt);

        for (int i = this.leaving.size() - 1; i >= 0; i--)
        {
            Voice voice = this.leaving.get(i);

            this.update(voice, dt);

            if (voice.level <= 0F)
            {
                voice.release();
                this.leaving.remove(i);
            }
        }
    }

    private float base(Category category)
    {
        return MathUtils.clamp(category == Category.HUB ? BBSSettings.welcomeMusicVolume.get() : BBSSettings.studioMusicVolume.get(), 0F, 1F);
    }

    private void update(Voice voice, float dt)
    {
        voice.level = MathUtils.clamp(voice.level + (voice.target > voice.level ? dt : -dt) / CROSSFADE_SECONDS, 0F, 1F);

        List<Link> list = this.tracks.get(voice.category);

        if (voice.player == null)
        {
            if (voice.target > 0F && !voice.failed)
            {
                this.load(voice, list);
            }

            return;
        }

        voice.player.setVolume(this.base(voice.category) * voice.level * this.duck);

        /* With several tracks the next one follows when this one ends; a single one just loops on the source */
        if (list.size() > 1 && voice.target > 0F && !voice.player.isPlaying())
        {
            voice.release();
            voice.index = (voice.index + 1) % list.size();
            voice.decoded = null;
        }
    }

    /** Reads the track on a thread of its own; its source is made here, on the render thread, once it is decoded */
    private void load(Voice voice, List<Link> list)
    {
        if (voice.decoded != null)
        {
            Wave wave = voice.decoded;

            voice.decoded = null;

            try
            {
                voice.buffer = new SoundBuffer(voice.link, wave, null);
                voice.player = new SoundPlayer(voice.buffer).configure2DStereo();
                voice.player.setLooping(list.size() == 1);
                voice.player.setVolume(0F);
                voice.player.play();
            }
            catch (Throwable e)
            {
                voice.release();
                voice.failed = true;
            }

            return;
        }

        if (voice.loading)
        {
            return;
        }

        voice.link = list.get(voice.index % list.size());
        voice.loading = true;

        Link link = voice.link;
        Thread thread = new Thread(() ->
        {
            try
            {
                Wave wave = decode(link);

                if (wave == null)
                {
                    voice.failed = true;
                }
                else
                {
                    voice.decoded = wave;
                }
            }
            catch (Throwable e)
            {
                voice.failed = true;
            }
            finally
            {
                voice.loading = false;
            }
        }, "BBS-StudioMusic");

        thread.setDaemon(true);
        thread.start();
    }

    /** The track of a link, from the mod's own files or from the asset packs; null when there is no such file */
    private static Wave decode(Link link) throws Exception
    {
        try (InputStream stream = BBSMod.class.getResourceAsStream("/assets/bbs/" + link.path))
        {
            if (stream != null)
            {
                Wave wave = VorbisReader.read(link, stream);

                return wave == null ? null : wave.normalize();
            }
        }

        if (BBSMod.getProvider() != null && BBSMod.getProvider().hasAsset(link))
        {
            return AudioReader.read(BBSMod.getProvider(), link);
        }

        return null;
    }
}
