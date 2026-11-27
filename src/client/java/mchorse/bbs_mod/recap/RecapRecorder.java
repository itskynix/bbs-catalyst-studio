package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.mixin.client.RecapClientTimeAccessor;
import mchorse.bbs_mod.mixin.client.RecapWorldWeatherAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The recording side of a recap: segmented 5-minute disk spooling ({@link RecapSpool.Session}),
 * zero-shift 30-second Instant Recap buckets ({@link RecapSpool.Ring}), sub-tick POV camera
 * capture, HUD &amp; local baseline capture, and pause-aware game-tick timing.
 */
public final class RecapRecorder
{
    public enum State
    {
        IDLE, RECORDING, STOPPED
    }

    /** The longest instant recap the setting allows, in ticks: what the play ring keeps. */
    private static final int BUFFER_LIMIT = 600 * 20;

    /** Total block changes kept in a manual session before warning/capping. */
    public static final int EVENT_LIMIT = 4_000_000;
    private static boolean eventsNear;
    private static boolean eventsFull;

    private static State state = State.IDLE;
    private static int ticks;
    private static int totalEvents;
    private static final List<Integer> markers = new ArrayList<>();
    private static final RecapDimensions dimensions = new RecapDimensions();
    private static RecapWorlds.Info world;
    private static RecapSpool.Session activeSession;
    private static RecapSpool.Slice currentSlice;

    /** Set while the world gives off its own ambient particles and sounds, which are not kept. */
    public static boolean ambient;

    private static int playedTicks;
    private static final List<Integer> playedMarkers = new ArrayList<>();
    private static final RecapDimensions playedDimensions = new RecapDimensions();
    private static final RecapSpool.Ring instantRing = new RecapSpool.Ring();
    /* The point of view of the running recording, and of the instant buffer */
    private static final RecapPov povSession = new RecapPov();
    private static final RecapPov povRing = new RecapPov();
    /* Set while Recap shows a toast of its own, which is not part of the take */
    private static boolean ownToast;

    private RecapRecorder()
    {}

    public static State getState()
    {
        return state;
    }

    public static int getTicks()
    {
        return ticks;
    }

    public static int getMarkerCount()
    {
        return markers.size();
    }

    public static String activeSessionId()
    {
        return activeSession == null ? null : activeSession.id;
    }

    /** The recording's time between two ticks, for drawing: stands still while the game is paused. */
    public static float getTime(float transition)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean paused = mc != null && mc.isPaused();

        return state == State.RECORDING && !paused ? ticks + Math.max(0F, Math.min(1F, transition)) : ticks;
    }

    /** Whether the world is to go on under the pause menu: a recording is running and the setting asks for it. */
    public static boolean keepsWorldRunning()
    {
        return state == State.RECORDING && BBSSettings.recapKeepRunning != null && BBSSettings.recapKeepRunning.get();
    }

    public static boolean isInstantEnabled()
    {
        return BBSSettings.recapInstant != null && BBSSettings.recapInstant.get();
    }

    private static boolean isBaselineEnabled()
    {
        return BBSSettings.recapBaseline == null || BBSSettings.recapBaseline.get();
    }

    private static int instantWindow()
    {
        return BBSSettings.recapInstantSeconds == null ? 0 : Math.min(BUFFER_LIMIT, BBSSettings.recapInstantSeconds.get() * 20);
    }

    /** How much play an instant recap would take right now. */
    public static int getInstantTicks()
    {
        return Math.min(instantWindow(), instantRing.availableTicks(playedTicks));
    }

    /** Captures intra-tick POV camera motion on render frames while actively playing. */
    public static void onRenderFrame(MinecraftClient mc, float tickDelta)
    {
        if (mc == null || mc.world == null || mc.gameRenderer == null || mc.isPaused() || RecapScene.isActive())
        {
            return;
        }

        boolean rec = state == State.RECORDING && currentSlice != null && currentSlice.ticks > 0;
        RecapSpool.Slice ring = !rec && isInstantEnabled() ? instantRing.current() : (isInstantEnabled() ? instantRing.current() : null);

        if (!rec && (ring == null || ring.ticks <= 0))
        {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();
        float clampedDelta = Math.max(0F, Math.min(1F, tickDelta));
        float fov = mc.options.getFov().getValue();

        if (rec)
        {
            currentSlice.track.addSubSample((currentSlice.ticks - 1) + clampedDelta, pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch(), fov);
        }

        if (ring != null && ring.ticks > 0)
        {
            ring.track.addSubSample((ring.ticks - 1) + clampedDelta, pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch(), fov);
        }
    }

    /** Once a client tick. Neither manual recording nor Instant Recap advances while the game world is paused. */
    public static void tick(MinecraftClient mc)
    {
        if (mc.world == null || mc.isPaused())
        {
            return;
        }

        String dim = RecapWorldOverlay.dimensionOf(mc.world);

        if (state == State.RECORDING && currentSlice != null)
        {
            if (mc.player != null && !RecapScene.isActive())
            {
                povSession.record(ticks, mc.player);
            }

            sampleSliceTick(mc, currentSlice, dim, isBaselineEnabled());
            ticks += 1;
            dimensions.mark(ticks - 1, dim);

            if (currentSlice.ticks >= RecapSpool.SLICE_TICKS || currentSlice.events.size() >= RecapSpool.SLICE_EVENT_CAP)
            {
                rotateSlice(false);
            }
        }

        if (RecapScene.isActive())
        {
            return;
        }

        RecapSpool.Slice ring = instantRing.active(playedTicks, BUFFER_LIMIT);

        if (isInstantEnabled() && mc.player != null)
        {
            povRing.record(playedTicks, mc.player);
            povRing.dropBefore(playedTicks - BUFFER_LIMIT);
        }

        sampleSliceTick(mc, ring, dim, isInstantEnabled() && isBaselineEnabled());
        playedTicks += 1;
        playedDimensions.mark(playedTicks - 1, dim);
        playedDimensions.dropBefore(playedTicks - BUFFER_LIMIT);

        int oldest = playedTicks - BUFFER_LIMIT;

        playedMarkers.removeIf((marker) -> marker < oldest);
    }

    private static void sampleSliceTick(MinecraftClient mc, RecapSpool.Slice slice, String dim, boolean captureBaseline)
    {
        int localTick = slice.ticks;

        slice.ticks += 1;
        sample(mc, slice.track);
        sampleMotion(mc, slice.motion);
        sampleEnvironment(mc, slice.environment);
        sampleGear(mc, slice.gear);
        slice.entities.record(mc.world.getEntities(), mc.player, mc.player, localTick, dim);
        slice.hud.record(mc, localTick);

        if (captureBaseline)
        {
            slice.baseline.recordStep(mc.world, mc.player, dim);
        }
    }

    private static void rotateSlice(boolean cleanStop)
    {
        if (activeSession == null || currentSlice == null || currentSlice.ticks <= 0)
        {
            return;
        }

        RecapSpool.Slice sealed = currentSlice;
        RecapSpool.Slice next = new RecapSpool.Slice(ticks);

        next.gear.seedFrom(sealed.gear);
        currentSlice = next;

        List<Integer> markersCopy = new ArrayList<>(markers);
        RecapDimensions dimsCopy = new RecapDimensions();

        dimsCopy.copy(dimensions);
        activeSession.spoolSlice(sealed, ticks, markersCopy, dimsCopy, cleanStop);
    }

    private static void sampleMotion(MinecraftClient mc, RecapMotion into)
    {
        if (mc.player != null)
        {
            into.add(mc.player);
        }
        else if (!into.isEmpty())
        {
            RecapMotion.Frame frame = new RecapMotion.Frame();

            into.sample(into.size() - 1, frame);
            into.add(frame.x, frame.y, frame.z, frame.bodyYaw, frame.headYaw, frame.pitch, frame.limbPos, frame.limbSpeed, frame.swing, frame.lean, frame.pose.ordinal(), frame.flags);
        }
    }

    private static void sampleGear(MinecraftClient mc, RecapGear into)
    {
        if (mc.player != null)
        {
            GameMode mode = mc.interactionManager == null ? null : mc.interactionManager.getCurrentGameMode();

            into.add(mc.player, mode == null ? -1 : mode.getId());
        }
        else
        {
            into.repeat();
        }
    }

    /* Sounds, particles and world events */

    private static boolean keepsEffects()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return !ambient && mc != null && !mc.isPaused() && (state == State.RECORDING || !RecapScene.isActive());
    }

    public static void onSound(SoundInstance sound, float volume, float pitch)
    {
        if (!keepsEffects() || !RecapSounds.isKept(sound))
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String dimension = RecapWorldOverlay.dimensionOf(mc.world);

        if (state == State.RECORDING && currentSlice != null)
        {
            currentSlice.sounds.add(currentSlice.ticks, sound, volume, pitch, dimension);
        }

        RecapSpool.Slice ring = instantRing.current();

        if (!RecapScene.isActive() && mc.world != null && ring != null)
        {
            ring.sounds.add(ring.ticks, sound, volume, pitch, dimension);
        }
    }

    public static void onParticle(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz)
    {
        if (RecapParticles.replaying || !keepsEffects())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String dimension = RecapWorldOverlay.dimensionOf(mc.world);

        if (state == State.RECORDING && currentSlice != null)
        {
            currentSlice.particles.addEffect(currentSlice.ticks, effect, x, y, z, vx, vy, vz, dimension);
        }

        RecapSpool.Slice ring = instantRing.current();

        if (!RecapScene.isActive() && mc.world != null && ring != null)
        {
            ring.particles.addEffect(ring.ticks, effect, x, y, z, vx, vy, vz, dimension);
        }
    }

    public static void onBlockBreakParticles(BlockPos pos, BlockState state)
    {
        if (RecapParticles.replaying || !keepsEffects())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String dimension = RecapWorldOverlay.dimensionOf(mc.world);

        if (RecapRecorder.state == State.RECORDING && currentSlice != null)
        {
            currentSlice.particles.addBlockBreak(currentSlice.ticks, pos, state, dimension);
        }

        RecapSpool.Slice ring = instantRing.current();

        if (!RecapScene.isActive() && mc.world != null && ring != null)
        {
            ring.particles.addBlockBreak(ring.ticks, pos, state, dimension);
        }
    }

    public static void onBlockHitParticles(BlockPos pos, Direction side)
    {
        if (RecapParticles.replaying || !keepsEffects())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String dimension = RecapWorldOverlay.dimensionOf(mc.world);

        if (state == State.RECORDING && currentSlice != null)
        {
            currentSlice.particles.addBlockHit(currentSlice.ticks, pos, side, dimension);
        }

        RecapSpool.Slice ring = instantRing.current();

        if (!RecapScene.isActive() && mc.world != null && ring != null)
        {
            ring.particles.addBlockHit(ring.ticks, pos, side, dimension);
        }
    }

    private static void sampleEnvironment(MinecraftClient mc, RecapEnvironment into)
    {
        ClientWorld cw = mc.world;
        long time = ((RecapClientTimeAccessor) cw.getLevelProperties()).bbs$getTimeOfDay();
        RecapWorldWeatherAccessor weather = (RecapWorldWeatherAccessor) cw;

        into.add(time, weather.bbs$getRainGradient(), weather.bbs$getThunderGradient());
    }

    public static void onBlockChanged(ClientWorld in, BlockPos pos, BlockState from, BlockState to)
    {
        onBlockChanged(in, pos, from, to, null, null);
    }

    /** Records a block change along with optional before/after BlockEntity NBT payloads. */
    public static void onBlockChanged(ClientWorld in, BlockPos pos, BlockState from, BlockState to, NbtCompound fromNbt, NbtCompound toNbt)
    {
        if (RecapWorldOverlay.isApplying() || (from == to && fromNbt == null && toNbt == null))
        {
            return;
        }

        RecapWorldOverlay.onBlockChanged(in, pos, to, toNbt);

        String dimension = RecapWorldOverlay.dimensionOf(in);

        if (state == State.RECORDING && currentSlice != null)
        {
            if (totalEvents < EVENT_LIMIT)
            {
                currentSlice.events.add(currentSlice.ticks, pos, from, to, fromNbt, toNbt, dimension);
                totalEvents += 1;

                if (!eventsNear && totalEvents >= EVENT_LIMIT / 4 * 3)
                {
                    eventsNear = true;
                    say("bbs.recap.message.events_near");
                }
            }
            else if (!eventsFull)
            {
                eventsFull = true;
                say("bbs.recap.message.events_full");
            }
        }

        RecapSpool.Slice ring = instantRing.current();

        if (in != null && !RecapScene.isActive() && ring != null && ring.events.size() < RecapSpool.SLICE_EVENT_CAP)
        {
            ring.events.add(ring.ticks, pos, from, to, fromNbt, toNbt, dimension);
        }
    }

    /** Records a synced block event (chest lid open/close, note block, bell, piston action). */
    public static void onBlockAction(ClientWorld in, BlockPos pos, Block block, int type, int data)
    {
        if (RecapWorldOverlay.isApplying() || in == null)
        {
            return;
        }

        String dimension = RecapWorldOverlay.dimensionOf(in);

        if (state == State.RECORDING && currentSlice != null)
        {
            currentSlice.events.addBlockAction(currentSlice.ticks, pos, block, type, data, dimension);
        }

        RecapSpool.Slice ring = instantRing.current();

        if (!RecapScene.isActive() && ring != null)
        {
            ring.events.addBlockAction(ring.ticks, pos, block, type, data, dimension);
        }
    }

    /** A toast of the game popping up while recording: part of the point of view. */
    public static void onToast(net.minecraft.client.toast.Toast toast)
    {
        if (ownToast || RecapScene.isActive())
        {
            return;
        }

        if (state == State.RECORDING)
        {
            povSession.toast(ticks, toast);
        }

        povRing.toast(playedTicks, toast);
    }

    /** A totem of undying popping while recording. */
    public static void onTotem(net.minecraft.item.ItemStack stack)
    {
        if (RecapScene.isActive())
        {
            return;
        }

        if (state == State.RECORDING)
        {
            povSession.totem(ticks, stack);
        }

        povRing.totem(playedTicks, stack);
    }

    private static void sample(MinecraftClient mc, RecapTrack into)
    {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();

        into.add(pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch(), mc.options.getFov().getValue());
    }

    /** Starts a recording, or goes on with the stopped one from where it was held. */
    public static boolean start()
    {
        if (state == State.STOPPED)
        {
            state = State.RECORDING;

            return true;
        }

        RecapWorlds.Info info = RecapWorlds.current();

        if (state != State.IDLE || info == null)
        {
            return false;
        }

        String id;

        do
        {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        while (RecapStore.get(id) != null);

        world = info;
        ticks = 0;
        totalEvents = 0;
        markers.clear();
        eventsNear = eventsFull = false;
        dimensions.clear();
        povSession.clear();
        activeSession = new RecapSpool.Session(id, info);
        currentSlice = new RecapSpool.Slice(0);
        state = State.RECORDING;

        return true;
    }

    public static boolean stop()
    {
        if (state != State.RECORDING)
        {
            return false;
        }

        state = State.STOPPED;

        /* Flush a durable slice checkpoint to .active/<id>/ when pausing a non-empty stretch */
        if (currentSlice != null && currentSlice.ticks > 0)
        {
            rotateSlice(false);
        }

        return true;
    }

    /** Writes the recording to the list and ends the session; null when there is nothing to write. */
    public static RecapRecord save()
    {
        return saveSession(false);
    }

    private static RecapRecord saveSession(boolean fastExit)
    {
        if (state == State.IDLE || world == null || activeSession == null)
        {
            return null;
        }

        state = State.STOPPED;

        MinecraftClient mc = MinecraftClient.getInstance();

        /* If Start -> Save was pressed while paused before any client tick ran, capture one frame now */
        if (ticks == 0 && activeSession.committedCount() == 0 && currentSlice != null && mc != null && mc.world != null)
        {
            String dim = RecapWorldOverlay.dimensionOf(mc.world);

            sampleSliceTick(mc, currentSlice, dim, isBaselineEnabled());
            ticks = 1;
            dimensions.mark(0, dim);
        }

        if (ticks <= 0)
        {
            activeSession.discard();
            reset();

            return null;
        }

        RecapRecord record = buildRecord(activeSession.id, world, ticks, activeSession.created, markers, dimensions, false);

        if (!RecapStore.save(record))
        {
            return null;
        }

        RecapSpool.Session finishing = activeSession;
        RecapSpool.Slice tail = currentSlice;

        activeSession = null;
        currentSlice = null;
        RecapStore.savePov(record, povSession.extract(0, record.ticks));
        finishing.finalizeToStore(record, tail, fastExit);
        reset();

        return record;
    }

    public static boolean cancel()
    {
        if (state == State.IDLE)
        {
            return false;
        }

        if (activeSession != null)
        {
            activeSession.discard();
            activeSession = null;
        }

        reset();

        return true;
    }

    /** Marks this moment: in the running recording, or in the instant buffer while idle. */
    public static boolean marker()
    {
        if (state == State.RECORDING)
        {
            if (!markers.contains(ticks))
            {
                markers.add(ticks);
            }

            return true;
        }

        if (state == State.IDLE && MinecraftClient.getInstance().world != null)
        {
            if (!playedMarkers.contains(playedTicks))
            {
                playedMarkers.add(playedTicks);
            }

            return true;
        }

        return false;
    }

    /** Saves the last stretch of play as a recap of its own. */
    public static RecapRecord saveInstant()
    {
        RecapWorlds.Info info = RecapWorlds.current();

        if (!isInstantEnabled() || info == null)
        {
            return null;
        }

        int length = getInstantTicks();

        if (length <= 0)
        {
            return null;
        }

        int from = playedTicks - length;
        List<Integer> inside = new ArrayList<>();

        for (int marker : playedMarkers)
        {
            if (marker >= from)
            {
                inside.add(marker - from);
            }
        }

        RecapData data = instantRing.extractTail(playedTicks, length);
        int actualTicks = data.track != null && !data.track.isEmpty() ? data.track.size() : length;
        RecapRecord record = buildRecord("", info, actualTicks, System.currentTimeMillis(), inside, RecapDimensions.tail(playedDimensions, from), true);

        if (!RecapStore.save(record))
        {
            return null;
        }

        RecapStore.writeAllDirect(record, data, null);
        RecapStore.savePov(record, povRing.extract(from, playedTicks));

        return record;
    }

    public static void toggleWithMessage()
    {
        if (state == State.IDLE)
        {
            startWithMessage();
        }
        else
        {
            saveWithMessage();
        }
    }

    public static RecapRecord saveInstantWithMessage()
    {
        if (!isInstantEnabled())
        {
            say("bbs.recap.message.instant_off");

            return null;
        }

        RecapRecord record = saveInstant();

        say(record != null ? "bbs.recap.message.instant_saved" : "bbs.recap.message.instant_empty");

        if (record != null)
        {
            toast("bbs.recap.message.instant_saved", record);
        }

        return record;
    }

    public static void startWithMessage()
    {
        boolean resume = state == State.STOPPED;

        if (start())
        {
            say(resume ? "bbs.recap.message.resumed" : "bbs.recap.message.started");
        }
        else
        {
            say("bbs.recap.message.unavailable");
        }
    }

    public static void stopWithMessage()
    {
        if (stop())
        {
            say("bbs.recap.message.stopped");
        }
    }

    public static void saveWithMessage()
    {
        RecapRecord record = save();

        say(record != null ? "bbs.recap.message.saved" : "bbs.recap.message.failed");

        if (record != null)
        {
            toast("bbs.recap.message.saved", record);
        }
    }

    private static void toast(String key, RecapRecord record)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Text title = Text.literal(L10n.lang(key).get());
        Text description = Text.literal(record.name + "  " + RecapTime.format(record.ticks));

        ownToast = true;

        try
        {
            SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, title, description);
        }
        finally
        {
            ownToast = false;
        }
    }

    public static void cancelWithMessage()
    {
        if (cancel())
        {
            say("bbs.recap.message.cancelled");
        }
    }

    public static void markerWithMessage()
    {
        say(marker() ? "bbs.recap.message.marker" : "bbs.recap.message.no_marker");
    }

    private static void say(String key)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null)
        {
            mc.player.sendMessage(Text.literal(L10n.lang(key).get()), true);
        }
    }

    public static void onJoin()
    {
        reset();
        playedTicks = 0;
        playedMarkers.clear();
        playedDimensions.clear();
        povRing.clear();
        instantRing.clear();
    }

    /** Leaving the world keeps what was being recorded rather than losing it. */
    public static void onLeave()
    {
        if (state != State.IDLE && ticks > 0)
        {
            saveSession(false);
        }
        else if (activeSession != null)
        {
            activeSession.discard();
        }

        onJoin();
    }

    /** Fast game exit: seals the tail slice and journal without blocking shutdown to re-merge multi-slice sessions. */
    public static void onClientStopping()
    {
        if (state != State.IDLE && ticks > 0)
        {
            boolean multiSlice = activeSession != null && activeSession.committedCount() > 0;

            saveSession(multiSlice);
        }
        else if (activeSession != null)
        {
            activeSession.discard();
        }

        onJoin();
    }

    private static void reset()
    {
        state = State.IDLE;
        ticks = 0;
        totalEvents = 0;
        markers.clear();
        eventsNear = eventsFull = false;
        dimensions.clear();
        world = null;
        activeSession = null;
        currentSlice = null;
        povSession.clear();
    }

    private static RecapRecord buildRecord(String id, RecapWorlds.Info info, int length, long created, List<Integer> marked, RecapDimensions places, boolean instant)
    {
        RecapRecord record = new RecapRecord();
        LocalDateTime now = LocalDateTime.now();

        if (id != null && !id.isEmpty())
        {
            record.id = id;
        }

        record.name = info.name() + " " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"));
        record.worldKey = info.key();
        record.worldName = info.name();
        record.worldTarget = info.target();
        record.singleplayer = info.singleplayer();
        record.ticks = length;
        record.created = created;
        record.instant = instant;
        record.dimensions.copy(places);

        for (int marker : marked)
        {
            record.addMarker(marker);
        }

        return record;
    }
}
