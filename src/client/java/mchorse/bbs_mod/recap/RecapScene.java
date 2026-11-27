package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.modifiers.FovPunchClip;
import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.function.IntSupplier;

/**
 * What Recap Studio puts on the world while its viewport shows a recap: the recap's camera at the
 * cursor, reworked by its timeline clips, and the recap's view settings, the scene ones hiding
 * parts of the world and the overrides replacing its time, weather, fog and lens.
 *
 * <p>All of it is off unless a recap is being shown ({@link #isActive}), so the game and the other
 * editors never see it.</p>
 *
 * <p><b>Time.</b> The timeline time is {@code cursor + transition}; while the recap is held still
 * the transition is left out, so a scrubbed moment looks the same in every frame. The recorded
 * camera is read at the moment the speed and freeze clips map that time to
 * ({@link RecapClips#sourceTime}).</p>
 *
 * <p><b>Camera.</b> The recorded eye, or the player's own eye for a recap kept before the camera
 * was, with the view settings' lens, roll and sway, is the position the timeline's clips start
 * from; they are applied over it in their layers exactly as the film studio applies them, so an
 * idle or keyframe clip takes the camera elsewhere and a modifier works on what is under it. The
 * effect clips leave their data in the same context, which the frame overlays draw from. While
 * the camera is flown by hand, that camera is shown instead.</p>
 */
public final class RecapScene implements ICameraController
{
    public static final RecapScene INSTANCE = new RecapScene();

    private static boolean active;
    private static RecapRecord record;
    private static RecapTrack track;
    private static IntSupplier cursor = () -> 0;
    private static boolean playing;
    private static float transition;
    private static long baseDayTime;

    private static final CameraClipContext CONTEXT = new CameraClipContext();
    private static final Position LAST = new Position();
    private static Position manual;

    /* How the recorded player moved and the world around it, when the recap has them; its blocks
     * are the world overlay's */
    private static RecapMotion motion;
    private static RecapEnvironment environment;
    private static final RecapEnvironment.Sample AROUND = new RecapEnvironment.Sample();
    /** Everything else recorded: what the player had on it, the other entities, the sounds, the particles. */
    private static RecapData data = new RecapData();
    private static RecapPov.Playback povPlayback;
    private static final RecapGear.Frame GEAR = new RecapGear.Frame();
    private static final RecapHudStream.Snapshot HUD_NOW = new RecapHudStream.Snapshot();
    private static final RecapGhosts GHOSTS = new RecapGhosts();

    /** The free camera: where the viewport looks from while no camera clip is under the cursor. */
    private static final Position FREE = new Position();
    private static boolean freeSet;

    private final RecapMotion.Frame frame = new RecapMotion.Frame();

    private final RecapTrack.Sample sample = new RecapTrack.Sample();
    private final Position position = new Position();
    private RecapActor actor;
    private static final String OTHER = "recap:";
    private final java.util.Map<String, RecapActor> others = new java.util.HashMap<>();
    private final RecapEntities.Frame otherFrame = new RecapEntities.Frame();
    private final java.util.WeakHashMap<Entity, IEntity> wrapped = new java.util.WeakHashMap<>();

    private RecapScene()
    {}

    public static void show(RecapRecord shown, RecapData recorded, IntSupplier at)
    {
        RecapData all = recorded == null ? new RecapData() : recorded;

        /* The blocks of another recap shown before go back to the world's own first */
        RecapWorldOverlay.attach(all.events);

        if (record != shown || data != all)
        {
            freeSet = record != shown ? false : freeSet;
            GHOSTS.clear();
            RecapSettler.reset();

            if (all.baseline != null)
            {
                all.baseline.invalidateCache();
            }
        }

        if (povPlayback == null || data != all)
        {
            povPlayback = new RecapPov.Playback(all.pov, all.gear, all.motion);
        }

        data = all;
        motion = all.motion;
        environment = all.environment;

        MinecraftClient mc = MinecraftClient.getInstance();

        /* The world's own day time, read before anything here can override it, for the timelapse */
        active = false;
        baseDayTime = mc.world == null ? 6000L : mc.world.getTimeOfDay() % 24000L;

        active = shown != null;
        record = shown;
        track = all.track;
        cursor = at;
    }

    /** Everything the shown recap recorded; never null, its parts are when not recorded. */
    public static RecapData getData()
    {
        return data;
    }

    public static void hide()
    {
        if (active)
        {
            stopSounds();
            RecapSounds.stopActive();
            RecapParticles.clearSpawned();
        }

        RecapSettler.reset();

        /* The blocks the recap set go back to what the world really has now */
        RecapWorldOverlay.detach();

        manual = null;
        active = false;
        record = null;
        track = null;
        motion = null;
        environment = null;
        data = new RecapData();
        povPlayback = null;
        GHOSTS.clear();
        traveling = false;
        freeSet = false;
        playing = false;
    }

    /**
     * Lets go of everything at a world change: the shown recap, its camera on the controller and,
     * when a recap was being shown, the frame size it asked the world to be drawn at.
     */
    public static void reset()
    {
        boolean wasShown = active;

        /* The world is going: its blocks are let go of with it, none set back */
        RecapWorldOverlay.drop();
        hide();
        mchorse.bbs_mod.BBSModClient.getCameraController().remove(INSTANCE);

        if (wasShown)
        {
            mchorse.bbs_mod.client.BBSRendering.setCustomSize(false);
        }
    }

    public static void setPlaying(boolean isPlaying)
    {
        playing = isPlaying;
    }

    /* On the way to another dimension: a video takes none of the frames drawn meanwhile */
    private static boolean traveling;

    public static void setTraveling(boolean isTraveling)
    {
        traveling = isTraveling;
    }

    public static boolean isTraveling()
    {
        return traveling && active;
    }

    public static boolean isActive()
    {
        return active && record != null && MinecraftClient.getInstance().world != null;
    }

    /* Dimensions */

    /** The dimension the recording was in at the moment drawn, after the speed and freeze clips; null when it kept none. */
    public static String recordedDimension()
    {
        if (!isActive() || record.dimensions.isEmpty())
        {
            return null;
        }

        return record.dimensions.at((int) Math.floor(RecapClips.sourceTime(record.clips, time())));
    }

    /** Whether the recorded dimension at the cursor differs from the live client world's dimension. */
    public static boolean needsServerTravel()
    {
        String at = recordedDimension();

        return at != null && !at.equals(RecapWorldOverlay.dimensionOf(MinecraftClient.getInstance().world));
    }

    /**
     * Whether Recap Studio renders an isolated cross-dimension stage from {@link RecapBaseline}
     * on multiplayer servers where the client cannot teleport dimensions.
     */
    public static boolean showsIsolatedStage()
    {
        if (!isActive() || !needsServerTravel() || MinecraftClient.getInstance().getServer() != null)
        {
            return false;
        }

        return data.baseline != null && data.baseline.hasDimension(recordedDimension());
    }

    /** The dimension currently shown in the viewport: recorded dimension on isolated stage, else live world. */
    public static String effectiveDimension()
    {
        return showsIsolatedStage() ? recordedDimension() : RecapWorldOverlay.dimensionOf(MinecraftClient.getInstance().world);
    }

    /**
     * Whether the recording is in another dimension that has neither server travel nor an isolated
     * baseline stage: then its camera and its player are somewhere this world does not have.
     */
    public static boolean isElsewhere()
    {
        return needsServerTravel() && !showsIsolatedStage();
    }

    /** Whether the recap has a recorded camera to look through. */
    public static boolean hasCamera()
    {
        return isActive() && track != null && !track.isEmpty();
    }

    /** The timeline time drawn this frame: the cursor, plus the frame's share of a tick while playing. */
    public static float time()
    {
        return cursor.getAsInt() + (playing ? transition : 0F);
    }

    /** The exact source recording time drawn this frame, after speed and freeze clips. */
    public static float sourceNow()
    {
        return isActive() ? RecapClips.sourceTime(record.clips, time()) : 0F;
    }

    /** Deterministic sub-tick fraction {@code [0, 1]} of {@link #sourceNow()}. */
    public static float subTick()
    {
        float source = sourceNow();

        return Math.max(0F, Math.min(1F, source - (float) Math.floor(source)));
    }

    private static boolean on(RecapOption option)
    {
        return isActive() && record.isOn(option);
    }

    /* Scene */

    public static boolean hidesBlocks()
    {
        return isActive() && (!record.isOn(RecapOption.BLOCKS) || showsIsolatedStage());
    }

    public static boolean hidesSky()
    {
        return isActive() && !record.isOn(RecapOption.SKY);
    }

    public static boolean hidesParticles()
    {
        return isActive() && !record.isOn(RecapOption.PARTICLES);
    }

    public static boolean hidesLabels()
    {
        return isActive() && !record.isOn(RecapOption.NAME_TAGS);
    }

    public static boolean hidesBeacons()
    {
        return isActive() && !record.isOn(RecapOption.BEACON_BEAMS);
    }

    public static boolean hides(Entity entity)
    {
        if (!isActive())
        {
            return false;
        }

        /* A recap that kept the entities around shows those, never the live ones over them */
        if (data.hasEntities())
        {
            return true;
        }

        return entity instanceof PlayerEntity ? !record.isOn(RecapOption.PLAYERS) : !record.isOn(RecapOption.ENTITIES);
    }

    /**
     * Whether a live sound of the world is held back: while a recap with its own sounds is shown,
     * the live world's blocks, mobs and players are not heard over the recording's.
     */
    public static boolean mutesLiveSound(net.minecraft.client.sound.SoundInstance sound)
    {
        if (RecapSounds.replaying || !isActive() || !data.hasSounds() || sound == null)
        {
            return false;
        }

        net.minecraft.sound.SoundCategory category = sound.getCategory();

        return category == net.minecraft.sound.SoundCategory.BLOCKS || category == net.minecraft.sound.SoundCategory.HOSTILE
            || category == net.minecraft.sound.SoundCategory.NEUTRAL || category == net.minecraft.sound.SoundCategory.PLAYERS
            || category == net.minecraft.sound.SoundCategory.RECORDS || category == net.minecraft.sound.SoundCategory.VOICE;
    }

    /**
     * Whether a live particle is held back: while a recap with its own particles is shown, apart
     * from those blocks and the weather give off by themselves, which the recording left to the
     * live world.
     */
    public static boolean mutesLiveParticles()
    {
        return !RecapParticles.replaying && !RecapRecorder.ambient && isActive() && data.hasParticles();
    }

    /**
     * Plays the recorded sounds and sends off the recorded particles of the moments the timeline
     * went through from {@code from} to {@code to} as it played forward, of the client's dimension.
     */
    public static void playEffects(int from, int to)
    {
        if (!isActive() || to <= from)
        {
            return;
        }

        int sourceFrom = (int) Math.floor(RecapClips.sourceTime(record.clips, from));
        int sourceTo = (int) Math.floor(RecapClips.sourceTime(record.clips, to));

        if (sourceTo <= sourceFrom)
        {
            return;
        }

        String here = effectiveDimension();

        if (!showsIsolatedStage())
        {
            RecapWorldOverlay.playActions(sourceFrom, sourceTo, here);
        }

        if (data.sounds != null)
        {
            data.sounds.play(sourceFrom, sourceTo, here);
        }

        if (data.particles != null && record.isOn(RecapOption.PARTICLES))
        {
            data.particles.play(sourceFrom, sourceTo, here);
        }
    }

    /** The point of view clip under the cursor, or null. */
    public static mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip povClip()
    {
        if (!isActive())
        {
            return null;
        }

        int tick = cursor.getAsInt();
        mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip found = null;

        for (Clip clip : record.clips.get())
        {
            if (on(clip, tick) && clip instanceof mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip eye && (found == null || eye.layer.get() >= found.layer.get()))
            {
                found = eye;
            }
        }

        return found;
    }

    /** What the recorded player had on it at the moment drawn, or null when the recap did not keep it. */
    public static RecapGear.Frame gearNow()
    {
        if (!isActive() || data.gear == null || !data.gear.sample(RecapClips.sourceTime(record.clips, time()), GEAR))
        {
            return null;
        }

        return GEAR;
    }

    /** Recorded first-person interface snapshot at exact {@code ticks + transition}, or null. */
    public static RecapHudStream.Snapshot hudNow()
    {
        if (!isActive() || data.hud == null)
        {
            return null;
        }

        data.hud.sample(sourceNow(), HUD_NOW);

        return HUD_NOW;
    }

    /* Interface */

    /** Whether a clip puts the camera somewhere: everything but the effects, tools, sound, sway and lens kicks. */
    public static boolean placesCamera(Clip clip)
    {
        return !(clip instanceof FxClip || clip instanceof RecapClips.ToolClip || clip instanceof AudioClip
            || clip instanceof ShakeClip || clip instanceof FovPunchClip);
    }

    private static boolean on(Clip clip, int tick)
    {
        return clip.enabled.get() && tick >= clip.tick.get() && tick < clip.tick.get() + clip.duration.get();
    }

    /** Whether a camera clip is under the cursor; without one the free camera is shown. */
    public static boolean hasCameraClip()
    {
        if (!isActive())
        {
            return false;
        }

        int tick = cursor.getAsInt();

        for (Clip clip : record.clips.get())
        {
            if (on(clip, tick) && placesCamera(clip))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the viewport shows the recorded player's point of view: a point of view clip under the
     * cursor, and no flight. Then the game's interface is drawn, after the view settings.
     */
    public static boolean isPointOfView()
    {
        return isActive() && manual == null && !isElsewhere() && povClip() != null;
    }

    /**
     * Whether the point of view is from the eyes, not from behind or in front: only then are the
     * player's own body left out and its hands drawn.
     */
    public static boolean isFirstPerson()
    {
        return isPointOfView() && povClip().perspective.get() == mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip.PERSPECTIVE_FIRST;
    }

    /** The replay the recorded point of view plays as at the moment drawn, or null while no recap is shown. */
    public static mchorse.bbs_mod.film.replays.Replay povReplay()
    {
        return isActive() && povPlayback != null ? povPlayback.replay(RecapClips.sourceTime(record.clips, time())) : null;
    }

    /** The moment drawn in the ticks of {@link #povReplay}'s own timeline. */
    public static float povTick()
    {
        if (povReplay() == null)
        {
            return 0F;
        }

        return Math.max(0F, RecapClips.sourceTime(record.clips, time()) - povPlayback.start());
    }

    /**
     * Whether the viewport starts from the free camera: no flight, and no camera clip under the
     * cursor or the recording in another dimension, whose eye is nowhere in this world.
     */
    public static boolean isFreeCamera()
    {
        return isActive() && manual == null && (!hasCameraClip() || isElsewhere());
    }

    /** Puts the free camera somewhere, as a flight leaves it. */
    public static void setFree(Position position)
    {
        FREE.set(position);
        freeSet = true;
    }

    /** Whether a piece of the interface is drawn in the viewport: first person, and switched on. */
    public static boolean showsHud(RecapOption option)
    {
        return option.group == RecapOption.Group.HUD && isPointOfView() && record.isOn(option);
    }

    /* Overrides */

    /**
     * The world around as the recording had it at the moment drawn, after the speed and freeze
     * clips; false for a recap kept before it was.
     */
    private static boolean around()
    {
        return isActive() && environment != null && environment.sample(RecapClips.sourceTime(record.clips, time()), AROUND);
    }

    /**
     * The time of day the sky is drawn with. What decides it, first to last: the day time and
     * timelapse clips, the view settings' time, the time recorded, the world's own. Only drawn:
     * the world is never told another time.
     */
    public static Long timeOfDay()
    {
        if (!isActive())
        {
            return null;
        }

        boolean set = on(RecapOption.TIME_OF_DAY);
        Long recorded = !set && around() ? AROUND.time : null;
        long base = set ? record.getValue(RecapOption.TIME_OF_DAY) : (recorded != null ? recorded : baseDayTime);
        Long clipped = RecapClips.dayTime(record.clips, time(), base);

        if (clipped != null)
        {
            return clipped;
        }

        return set ? (long) record.getValue(RecapOption.TIME_OF_DAY) : recorded;
    }

    /** The rain drawn: the view settings' weather, else the rain recorded, else the world's own. */
    public static Double weather()
    {
        if (on(RecapOption.WEATHER))
        {
            return record.getValue(RecapOption.WEATHER) > 0 ? 1D : 0D;
        }

        return around() ? (double) AROUND.rain : null;
    }

    /** The thunder drawn: storm in the view settings' weather, else the thunder recorded, else the world's own. */
    public static Double thunder()
    {
        if (on(RecapOption.WEATHER))
        {
            return record.getValue(RecapOption.WEATHER) > 1 ? 1D : 0D;
        }

        return around() ? (double) AROUND.thunder : null;
    }

    public static Integer fogColor()
    {
        if (on(RecapOption.FOG_COLOR))
        {
            return record.getValue(RecapOption.FOG_COLOR);
        }

        return showsIsolatedStage() ? RecapBaseline.atmosphereColor(recordedDimension()) : null;
    }

    public static Float fogDistance()
    {
        return on(RecapOption.FOG) ? (float) record.getValue(RecapOption.FOG) : null;
    }

    public static boolean nightVision()
    {
        return on(RecapOption.NIGHT_VISION);
    }

    /* Flight */

    /** A camera flown by hand, which the viewport shows instead of the clips while it is set. */
    public static void setManual(Position position)
    {
        manual = position;
    }

    /** What the camera showed in the last frame, after the clips: where a flight starts from. */
    public static Position getLast()
    {
        return LAST;
    }

    /** The clips' context of the last frame: what the effects are drawn from. */
    public static CameraClipContext getContext()
    {
        return CONTEXT;
    }

    /* Camera */

    @Override
    public int getPriority()
    {
        return 100400;
    }

    /**
     * Where the recording's eye is at timeline {@code time}, after the speed and freeze clips: the
     * recorded camera, or the player's own eye for a recap kept before the camera was.
     */
    public boolean base(float time, RecapTrack.Sample out)
    {
        if (!isActive())
        {
            return false;
        }

        float source = RecapClips.sourceTime(record.clips, time);

        if (track != null && track.sample(source, out))
        {
            return true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return false;
        }

        Vec3d eye = mc.player.getCameraPosVec(1F);

        out.x = eye.x;
        out.y = eye.y;
        out.z = eye.z;
        out.yaw = mc.player.getYaw();
        out.pitch = mc.player.getPitch();
        out.fov = mc.options.getFov().getValue();

        return true;
    }

    @Override
    public void setup(Camera camera, float frameTransition)
    {
        transition = Math.max(0F, Math.min(1F, frameTransition));

        float time = time();
        int now = cursor.getAsInt();

        /* The world's blocks as the recording had them at this moment, of this dimension only;
         * also while flying, so the cursor moved under a flight shows its blocks */
        if (isActive())
        {
            float source = RecapClips.sourceTime(record.clips, time);
            boolean exporting = mchorse.bbs_mod.BBSModClient.getVideoRecorder().isRecording();

            if (!showsIsolatedStage())
            {
                RecapWorldOverlay.update(MinecraftClient.getInstance().world, (int) Math.floor(RecapClips.sourceTime(record.clips, now)));
            }

            RecapSettler.update(record, data, effectiveDimension(), source, playing, exporting);
        }

        if (manual != null)
        {
            manual.apply(camera);
            LAST.set(manual);

            return;
        }

        if (!this.base(time, this.sample))
        {
            return;
        }

        boolean elsewhere = isElsewhere();
        boolean free = !hasCameraClip() || elsewhere;

        /* The free camera, outside, where it was left; first seen behind the player, or where the
         * client is when the recording is in another dimension */
        if (free && !freeSet)
        {
            Position outside = elsewhere ? liveView() : outsideView(record, track, motion, time);

            if (outside != null)
            {
                setFree(outside);
            }
        }

        /* The eye as recorded, with the view settings' lens, roll and sway; a position's yaw
         * is the game's turned half way round */
        float yaw = this.sample.yaw;
        float pitch = this.sample.pitch;
        float roll = on(RecapOption.CAMERA_ROLL) ? record.getValue(RecapOption.CAMERA_ROLL) : 0F;
        float fov = on(RecapOption.FOV) ? record.getValue(RecapOption.FOV) : this.sample.fov;

        if (on(RecapOption.CAMERA_SHAKE))
        {
            float amount = record.getValue(RecapOption.CAMERA_SHAKE) / 100F * 1.6F;

            yaw += amount * (float) (Math.sin(time * 0.37F) + 0.5F * Math.sin(time * 0.91F + 1.3F));
            pitch += amount * (float) (Math.sin(time * 0.29F + 2.1F) + 0.5F * Math.sin(time * 1.13F));
        }

        if (free && freeSet)
        {
            this.position.set(FREE);
            this.position.angle.roll += roll;
            this.position.angle.fov = on(RecapOption.FOV) ? record.getValue(RecapOption.FOV) : this.position.angle.fov;
        }
        else
        {
            this.position.point.set(this.sample.x, this.sample.y, this.sample.z);
            this.position.angle.set(yaw + 180F, pitch, roll, fov);
        }

        /* Then the clips under the cursor, in their layers, as the film studio applies them */
        int tick = cursor.getAsInt();
        float sub = playing ? transition : 0F;

        this.fillEntities();
        CONTEXT.clips = record.clips;
        CONTEXT.playing = playing;
        CONTEXT.clipData.clear();
        CONTEXT.setup(tick, sub);

        for (Clip clip : record.clips.getClips(tick))
        {
            CONTEXT.apply(clip, this.position);
        }

        AudioClientClip.manageSounds(CONTEXT);
        CONTEXT.currentLayer = 0;

        this.position.angle.fov = Math.max(1F, Math.min(170F, this.position.angle.fov));
        this.position.apply(camera);
        LAST.set(this.position);
    }

    /** The entities the tracking clips can follow: the player, and the other players by name. */
    private void fillEntities()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        CONTEXT.entities.clear();

        if (mc.world == null || mc.player == null)
        {
            return;
        }

        /* The recorded player, standing where the recording had it, for the clips that follow it */
        if (this.actor == null || this.actor.getMcEntity() != mc.player)
        {
            this.actor = new RecapActor(mc.player);
        }

        int tick = cursor.getAsInt();

        /* Not while the recording is in another dimension: the clips that follow it hold still */
        if (!isElsewhere())
        {
            float exactSource = RecapClips.sourceTime(record.clips, time());

            this.actor.place(track, motion, exactSource, exactSource);
            CONTEXT.entities.put(RecapClips.PLAYER, this.actor.isPlaced() ? this.actor : this.wrap(mc.player));
        }

        if (!data.hasEntities())
        {
            for (PlayerEntity player : mc.world.getPlayers())
            {
                if (player != mc.player)
                {
                    CONTEXT.entities.put(player.getName().getString(), this.wrap(player));
                }
            }

            return;
        }

        /* The other recorded players, each where its own track has it at the moment drawn */
        float source = RecapClips.sourceTime(record.clips, time());
        String here = effectiveDimension();

        for (RecapEntities.Track other : data.entities.getTracks())
        {
            if (other.player && (here == null || other.dimension == null || other.dimension.equals(here)) && other.sample(source, this.otherFrame))
            {
                RecapActor stand = this.others.computeIfAbsent(other.uuid, (id) -> new RecapActor(mc.player, true));

                if (stand.getMcEntity() != mc.player)
                {
                    stand = new RecapActor(mc.player, true);
                    this.others.put(other.uuid, stand);
                }

                stand.place(this.otherFrame);
                CONTEXT.entities.put(OTHER + other.uuid, stand);
            }
        }
    }

    /** The recorded players clips can follow, id to name: the one who recorded first, its name empty. */
    public static java.util.Map<String, String> targets()
    {
        java.util.Map<String, String> targets = new java.util.LinkedHashMap<>();

        targets.put(RecapClips.PLAYER, "");

        if (isActive() && data.entities != null)
        {
            for (RecapEntities.Track other : data.entities.getTracks())
            {
                if (other.player)
                {
                    targets.put(OTHER + other.uuid, other.name);
                }
            }
        }

        return targets;
    }

    public static java.util.Map<String, IEntity> targetEntities()
    {
        return CONTEXT.entities;
    }

    /** The target a clip follows by id, as it stands in the last frame drawn; null when it is not there. */
    public static IEntity target(String id)
    {
        return CONTEXT.entities.get(id);
    }

    /**
     * The recorded player at a moment of the recording: its own frame when its movement was kept,
     * else made from the camera, feet an eye height under it. False when there is neither.
     */
    public static boolean frameAt(RecapTrack camera, RecapMotion moves, float source, RecapMotion.Frame out, double eyeHeight)
    {
        if (moves != null && moves.sample(source, out))
        {
            return true;
        }

        RecapTrack.Sample sample = new RecapTrack.Sample();

        if (camera == null || !camera.sample(source, sample))
        {
            return false;
        }

        out.x = sample.x;
        out.y = sample.y - eyeHeight;
        out.z = sample.z;
        out.bodyYaw = out.headYaw = sample.yaw;
        out.pitch = sample.pitch;
        out.limbPos = out.limbSpeed = out.swing = out.lean = 0F;
        out.pose = net.minecraft.entity.EntityPose.STANDING;
        out.flags = RecapMotion.ON_GROUND;

        return true;
    }

    /**
     * A camera outside, behind and above the recorded player at {@code time}, looking at its head:
     * where the free camera starts and what the first camera clip of a new recap holds.
     */
    public static Position outsideView(RecapRecord shown, RecapTrack camera, RecapMotion moves, float time)
    {
        RecapMotion.Frame at = new RecapMotion.Frame();
        float source = shown == null ? time : RecapClips.sourceTime(shown.clips, time);

        if (!frameAt(camera, moves, source, at, 1.62D))
        {
            return null;
        }

        double headY = at.y + 1.62D;
        double yawRad = Math.toRadians(at.headYaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double cx = at.x - fx * 4.5D;
        double cy = headY + 1.2D;
        double cz = at.z - fz * 4.5D;
        double dx = at.x - cx;
        double dy = headY - cy;
        double dz = at.z - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        Position position = new Position();

        position.point.set(cx, cy, cz);
        position.angle.set(yaw + 180F, pitch, 0F, 70F);

        return position;
    }

    /** Where the client's own camera is: the free camera's start while the recording is elsewhere. */
    private static Position liveView()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return null;
        }

        Vec3d eye = mc.player.getCameraPosVec(1F);
        Position position = new Position();

        position.point.set(eye.x, eye.y, eye.z);
        position.angle.set(mc.player.getYaw() + 180F, mc.player.getPitch(), 0F, 70F);

        return position;
    }

    private IEntity wrap(Entity entity)
    {
        return this.wrapped.computeIfAbsent(entity, MCEntity::new);
    }

    /** Draws the isolated cross-dimension terrain stage when active on a multiplayer server. */
    public static void renderStage(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        if (!showsIsolatedStage() || !record.isOn(RecapOption.BLOCKS) || data.baseline == null)
        {
            return;
        }

        int sourceTick = (int) Math.floor(RecapClips.sourceTime(record.clips, time()));

        data.baseline.renderIsolated(context, data.events, recordedDimension(), sourceTick);
    }

    /**
     * Draws the recorded player in the world, where, as turned and as moving as the recording had
     * it: the player's own model, with the recorded stride, arm swing, crouch, swim and glide lent
     * to it for the draw and given back after. Not while the viewport looks out of its eyes.
     */
    public static void renderActor(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive() || mc.player == null || isFirstPerson() || isElsewhere() || !record.isOn(RecapOption.PLAYERS)
            || context.matrixStack() == null || context.consumers() == null)
        {
            return;
        }

        if ((track == null || track.isEmpty()) && (motion == null || motion.isEmpty()))
        {
            return;
        }

        net.minecraft.client.network.ClientPlayerEntity player = mc.player;
        RecapMotion.Frame at = INSTANCE.frame;
        float source = RecapClips.sourceTime(record.clips, time());

        if (!frameAt(track, motion, source, at, player.getStandingEyeHeight()))
        {
            return;
        }

        int srcTick = (int) Math.floor(source);
        float sub = Math.max(0F, Math.min(1F, source - srcTick));
        net.minecraft.client.render.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3d camera = context.camera().getPos();
        mchorse.bbs_mod.mixin.client.RecapLimbAccessor limbs = (mchorse.bbs_mod.mixin.client.RecapLimbAccessor) player.limbAnimator;
        mchorse.bbs_mod.mixin.client.RecapLivingAccessor living = (mchorse.bbs_mod.mixin.client.RecapLivingAccessor) player;
        mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker flags = (mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker) player;

        /* Everything the renderer reads off the entity, kept to be given back */
        int prevAge = player.age;
        float yaw = player.getYaw(), prevYaw = player.prevYaw, pitch = player.getPitch(), prevPitch = player.prevPitch;
        float bodyYaw = player.bodyYaw, prevBodyYaw = player.prevBodyYaw, headYaw = player.headYaw, prevHeadYaw = player.prevHeadYaw;
        float limbPos = player.limbAnimator.getPos(), limbSpeed = player.limbAnimator.getSpeed(), limbPrev = limbs.bbs$getPrevSpeed();
        float swing = player.handSwingProgress, lastSwing = player.lastHandSwingProgress;
        boolean handSwinging = player.handSwinging;
        net.minecraft.util.Hand preferredHand = player.preferredHand;
        float lean = living.bbs$getLeaningPitch(), lastLean = living.bbs$getLastLeaningPitch();
        net.minecraft.entity.EntityPose pose = player.getPose();
        boolean sneaking = player.isSneaking(), sprinting = player.isSprinting(), swimming = player.isSwimming();
        boolean gliding = flags.bbs$getFlag(RecapMotion.GLIDE_FLAG);
        /* What it had in its hands and on it, as the recording had it */
        RecapGear.Frame gear = gearNow();
        RecapLend lent = gear == null ? null : RecapLend.lend(player, gear, false);
        boolean shadows = mc.options.getEntityShadows().getValue();

        try
        {
            player.age = Math.max(0, srcTick);
            player.setYaw(at.headYaw);
            player.prevYaw = at.headYaw;
            player.setPitch(at.pitch);
            player.prevPitch = at.pitch;
            player.bodyYaw = player.prevBodyYaw = at.bodyYaw;
            player.headYaw = player.prevHeadYaw = at.headYaw;
            /* Compensate for LimbAnimator.getPos(sub) = pos - speed * (1 - sub) so limb stride is exact */
            limbs.bbs$setPos(at.limbPos + at.limbSpeed * (1F - sub));
            limbs.bbs$setSpeed(at.limbSpeed);
            limbs.bbs$setPrevSpeed(at.limbSpeed);
            player.handSwingProgress = player.lastHandSwingProgress = at.swing;
            player.handSwinging = at.swing > 0F || at.is(RecapMotion.SWINGING);
            player.preferredHand = at.is(RecapMotion.OFF_HAND_SWING) ? net.minecraft.util.Hand.OFF_HAND : net.minecraft.util.Hand.MAIN_HAND;
            living.bbs$setLeaningPitch(at.lean);
            living.bbs$setLastLeaningPitch(at.lean);
            player.setPose(at.pose);
            player.setSneaking(at.is(RecapMotion.SNEAKING));
            player.setSprinting(at.is(RecapMotion.SPRINTING));
            player.setSwimming(at.is(RecapMotion.SWIMMING));
            flags.bbs$setFlag(RecapMotion.GLIDE_FLAG, at.is(RecapMotion.GLIDING));

            /* Its shadow would be cast where the player really stands */
            dispatcher.setRenderShadows(false);

            String dim = effectiveDimension();
            int light = showsIsolatedStage()
                ? ("minecraft:the_nether".equals(dim) || "minecraft:the_end".equals(dim)
                    ? net.minecraft.client.render.LightmapTextureManager.pack(8, 7)
                    : net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE)
                : net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y + 1D, at.z));

            dispatcher.render(player, at.x - camera.x, at.y - camera.y, at.z - camera.z, at.headYaw, sub, context.matrixStack(), context.consumers(), light);
        }
        finally
        {
            dispatcher.setRenderShadows(shadows);
            player.age = prevAge;
            player.setYaw(yaw);
            player.prevYaw = prevYaw;
            player.setPitch(pitch);
            player.prevPitch = prevPitch;
            player.bodyYaw = bodyYaw;
            player.prevBodyYaw = prevBodyYaw;
            player.headYaw = headYaw;
            player.prevHeadYaw = prevHeadYaw;
            limbs.bbs$setPos(limbPos);
            limbs.bbs$setSpeed(limbSpeed);
            limbs.bbs$setPrevSpeed(limbPrev);
            player.handSwingProgress = swing;
            player.lastHandSwingProgress = lastSwing;
            player.handSwinging = handSwinging;
            player.preferredHand = preferredHand;
            living.bbs$setLeaningPitch(lean);
            living.bbs$setLastLeaningPitch(lastLean);
            player.setPose(pose);
            player.setSneaking(sneaking);
            player.setSprinting(sprinting);
            player.setSwimming(swimming);
            flags.bbs$setFlag(RecapMotion.GLIDE_FLAG, gliding);

            if (lent != null)
            {
                lent.giveBack(player);
            }
        }
    }

    /**
     * Draws the other entities the recap kept, where and as they were at the moment drawn, of the
     * client's dimension, as the view settings let players and other entities through.
     */
    public static void renderEntities(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
    {
        if (!isActive() || !data.hasEntities() || context.matrixStack() == null || context.consumers() == null)
        {
            return;
        }

        float source = RecapClips.sourceTime(record.clips, time());

        GHOSTS.render(data.entities, source, record.isOn(RecapOption.PLAYERS), record.isOn(RecapOption.ENTITIES), context.camera().getPos(),
            context.matrixStack(), context.consumers());
    }

    /**
     * The game's first person hand pass while a recap is shown: true when Recap took it. Looking out
     * of the recorded player's eyes, its hands are drawn with what it held then, raised, swinging and
     * turned as recorded, through the hand lens and the point of view clip's arm offset; with any
     * other camera of a recap no hands at all.
     */
    public static boolean renderPovHands(net.minecraft.client.render.Camera camera, float tickDelta)
    {
        if (!isActive())
        {
            return false;
        }

        mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip clip = isFirstPerson() ? povClip() : null;
        MinecraftClient mc = MinecraftClient.getInstance();

        if (clip == null || !clip.hands.get() || mc.player == null || mc.world == null)
        {
            return true;
        }

        RecapGear.Frame gear = gearNow();

        if (gear != null && gear.mode == 3)
        {
            /* A spectator has no hands */
            return true;
        }

        net.minecraft.client.network.ClientPlayerEntity player = mc.player;
        RecapMotion.Frame at = INSTANCE.frame;

        if (!frameAt(track, motion, RecapClips.sourceTime(record.clips, time()), at, player.getStandingEyeHeight()))
        {
            return true;
        }

        mchorse.bbs_mod.mixin.client.RecapHeldItemAccessor held = (mchorse.bbs_mod.mixin.client.RecapHeldItemAccessor) mc.gameRenderer.firstPersonRenderer;
        org.joml.Matrix4f projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.systems.VertexSorter sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();

        /* Kept to be given back */
        net.minecraft.item.ItemStack mainHand = held.bbs$getMainHand(), offHand = held.bbs$getOffHand();
        float equipMain = held.bbs$getEquipMain(), prevEquipMain = held.bbs$getPrevEquipMain();
        float equipOff = held.bbs$getEquipOff(), prevEquipOff = held.bbs$getPrevEquipOff();
        float pitch = player.getPitch(), prevPitch = player.prevPitch, yaw = player.getYaw(), prevYaw = player.prevYaw;
        float renderPitch = player.renderPitch, lastRenderPitch = player.lastRenderPitch, renderYaw = player.renderYaw, lastRenderYaw = player.lastRenderYaw;
        float swing = player.handSwingProgress, lastSwing = player.lastHandSwingProgress;
        RecapLend lent = gear == null ? null : RecapLend.lend(player, gear, false);

        try
        {
            net.minecraft.item.ItemStack main = gear == null ? player.getMainHandStack() : gear.mainHand();
            net.minecraft.item.ItemStack off = gear == null ? player.getOffHandStack() : gear.item(RecapGear.OFF_HAND);

            held.bbs$setMainHand(main);
            held.bbs$setOffHand(off);
            /* How far each hand has dipped to change what it holds, as the recording had it */
            mchorse.bbs_mod.film.replays.Replay pov = povReplay();
            float povTick = povTick();
            float raisedMain = pov == null || pov.pov.mainEquip.isEmpty() ? 1F : 1F - pov.pov.mainEquip.interpolate(povTick, 0D).floatValue();
            float raisedOff = pov == null || pov.pov.offhandEquip.isEmpty() ? 1F : 1F - pov.pov.offhandEquip.interpolate(povTick, 0D).floatValue();

            held.bbs$setEquipMain(raisedMain);
            held.bbs$setPrevEquipMain(raisedMain);
            held.bbs$setEquipOff(raisedOff);
            held.bbs$setPrevEquipOff(raisedOff);
            player.setPitch(at.pitch);
            player.prevPitch = at.pitch;
            player.setYaw(at.headYaw);
            player.prevYaw = at.headYaw;
            player.renderPitch = player.lastRenderPitch = at.pitch;
            player.renderYaw = player.lastRenderYaw = at.headYaw;
            player.handSwingProgress = player.lastHandSwingProgress = at.swing;

            /* The hand's own lens, as the game draws hands: a fixed field of view, apart from the scene's */
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(mc.gameRenderer.getBasicProjectionMatrix(70D), com.mojang.blaze3d.systems.VertexSorter.BY_DISTANCE);

            net.minecraft.client.util.math.MatrixStack matrices = new net.minecraft.client.util.math.MatrixStack();
            mchorse.bbs_mod.camera.data.Point offset = clip.handOffset.get();

            /* The walking bob, as the game bobs the hand it draws */
            if (clip.outCameraShake.get() && pov != null)
            {
                float[] bob = mchorse.bbs_mod.film.eye.PovHands.bob(pov, null, povTick, tickDelta);
                float phase = bob[0] * (float) Math.PI;
                float strength = bob[1];

                matrices.translate(Math.sin(phase) * strength * 0.5F, -Math.abs(Math.cos(phase) * strength), 0F);
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(phase) * strength * 3F));
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees((float) Math.abs(Math.cos(phase - 0.2F) * strength) * 5F));
            }

            matrices.translate(offset.x, offset.y, offset.z);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(clip.handPitch.get()));
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(clip.handYaw.get()));

            int light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y + player.getStandingEyeHeight(), at.z));

            mc.gameRenderer.getLightmapTextureManager().enable();
            mc.gameRenderer.firstPersonRenderer.renderItem(1F, matrices, mc.getBufferBuilders().getEntityVertexConsumers(), player, light);
            mc.gameRenderer.getLightmapTextureManager().disable();
        }
        catch (RuntimeException e)
        {
            /* Hands that cannot be drawn are left out of this frame, nothing else */
        }
        finally
        {
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection, sorting);
            held.bbs$setMainHand(mainHand);
            held.bbs$setOffHand(offHand);
            held.bbs$setEquipMain(equipMain);
            held.bbs$setPrevEquipMain(prevEquipMain);
            held.bbs$setEquipOff(equipOff);
            held.bbs$setPrevEquipOff(prevEquipOff);
            player.setPitch(pitch);
            player.prevPitch = prevPitch;
            player.setYaw(yaw);
            player.prevYaw = prevYaw;
            player.renderPitch = renderPitch;
            player.lastRenderPitch = lastRenderPitch;
            player.renderYaw = renderYaw;
            player.lastRenderYaw = lastRenderYaw;
            player.handSwingProgress = swing;
            player.lastHandSwingProgress = lastSwing;

            if (lent != null)
            {
                lent.giveBack(player);
            }
        }

        return true;
    }

    /** Lets the sounds of the audio clips go when the recap is no longer shown. */
    public static void stopSounds()
    {
        CONTEXT.playing = false;

        if (CONTEXT.clips != null)
        {
            AudioClientClip.manageSounds(CONTEXT);
        }

        CONTEXT.shutdown();
        CONTEXT.clips = null;
        INSTANCE.wrapped.clear();
        INSTANCE.actor = null;
        INSTANCE.others.clear();
    }
}
