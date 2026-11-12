package mchorse.bbs.compat.mtr;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.world.MtrDispatchTrack;
import mchorse.bbs_mod.film.world.WorldEventBlock;
import mchorse.bbs_mod.film.world.WorldTimeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side MTR event observer and state delta tracker for BBS replay recording.
 * Automatically captures vehicle door open/close deltas, dispatch/station arrival motion deltas,
 * and horn/announcement triggers into the active film's {@link WorldTimeline}.
 */
public class MtrEventTracker
{
    public static final String EVENT_DOOR_OPEN = MtrDispatchTrack.EVENT_DOOR_OPEN;
    public static final String EVENT_DOOR_CLOSE = MtrDispatchTrack.EVENT_DOOR_CLOSE;
    public static final String EVENT_DISPATCH_DEPART = MtrDispatchTrack.EVENT_DISPATCH_DEPART;
    public static final String EVENT_STATION_ARRIVE = MtrDispatchTrack.EVENT_STATION_ARRIVE;
    public static final String EVENT_HORN = MtrDispatchTrack.EVENT_HORN;

    private static final double SPEED_THRESHOLD = 0.0001D;
    private static final double DOOR_OPEN_THRESHOLD = 0.01D;

    private final List<WorldEventBlock> capturedBlocks = new ArrayList<>();

    private WorldEventBlock activeBlock;
    private long trackedVehicleId = 0L;
    private boolean initializedState = false;
    private boolean prevDoorsOpen = false;
    private int prevDoorMultiplier = 0;
    private boolean prevMoving = false;
    private double prevRailProgress = 0D;
    private int lastRecordedTick = 0;

    public List<WorldEventBlock> getCapturedBlocks()
    {
        return this.capturedBlocks;
    }

    public WorldEventBlock getActiveBlock()
    {
        return this.activeBlock;
    }

    /**
     * Called when recording begins at {@code startTick}.
     * If the player is riding an MTR vehicle or a nearby/dispatched vehicle is present,
     * automatically opens a new {@link WorldEventBlock} on the film's {@link WorldTimeline}.
     */
    public void startRecording(Film film, int startTick)
    {
        this.resetState();

        if (!MtrCompatHelper.isMtrLoaded() || film == null)
        {
            return;
        }

        MtrCompatHelper.VehicleLiveState state = MtrCompatHelper.sampleTrackedVehicleState(0L);

        if (state != null && !MtrGovernor.isGoverned(state.vehicleId) && (state.playerRiding || MtrCompatHelper.isPlayerInVehicle() || state.speed > SPEED_THRESHOLD || state.doorValue > DOOR_OPEN_THRESHOLD))
        {
            this.openBlockForVehicle(film, state, startTick);
            this.seedInitialState(state);
        }
    }

    /**
     * Called every tick during {@code Recorder.update()}.
     * Detects door state deltas, motion deltas (dispatch/arrive), and horn/announcement triggers,
     * injecting relative keyframes into the active block's {@code SubDopeSheet}.
     */
    public void update(Film film, int recordTick)
    {
        this.lastRecordedTick = recordTick;

        if (!MtrCompatHelper.isMtrLoaded() || film == null)
        {
            return;
        }

        MtrCompatHelper.VehicleLiveState state = MtrCompatHelper.sampleTrackedVehicleState(this.trackedVehicleId);

        if (state == null)
        {
            return;
        }

        /* Automatically open a block if the player boards or activates a vehicle during the take */
        if (this.activeBlock == null)
        {
            if (!MtrGovernor.isGoverned(state.vehicleId) && (state.playerRiding || MtrCompatHelper.isPlayerInVehicle() || state.speed > SPEED_THRESHOLD || state.doorValue > DOOR_OPEN_THRESHOLD))
            {
                this.openBlockForVehicle(film, state, recordTick);
                this.seedInitialState(state);
            }

            return;
        }

        /* If the player switched to a different vehicle mid-take, finalize the previous block and start a new one */
        if (state.playerRiding && this.trackedVehicleId != 0L && state.vehicleId != this.trackedVehicleId)
        {
            this.lockActiveBlockDuration(recordTick);
            this.openBlockForVehicle(film, state, recordTick);
            this.seedInitialState(state);

            return;
        }

        if (!this.initializedState)
        {
            this.seedInitialState(state);

            return;
        }

        int relativeTick = Math.max(0, recordTick - this.activeBlock.startTick);
        MtrDispatchTrack track = this.activeBlock.subDopeSheet.getPrimaryTrack();

        /* The vehicle's own trace: one sample per tick, whether or not anybody is riding */
        if (state.vehicleId == this.trackedVehicleId)
        {
            track.vehicle.record(relativeTick, state.railProgress, state.speed, state.doorValue, state.doorMultiplier, state.reversed);
        }

        if (state.stationId != 0L && track.targetStationId == 0L)
        {
            track.targetStationId = state.stationId;
        }

        boolean currDoorsOpen = state.doorValue > DOOR_OPEN_THRESHOLD || state.doorMultiplier > 0;
        boolean currMoving = Math.abs(state.speed) > SPEED_THRESHOLD;

        /* 1. Door State Delta */
        if (!this.prevDoorsOpen && currDoorsOpen)
        {
            if (Double.isNaN(track.stationStopRailProgress))
            {
                track.stationStopRailProgress = state.railProgress;
            }

            track.recordAutoEvent(EVENT_DOOR_OPEN, relativeTick);
        }
        else if (this.prevDoorsOpen && (!currDoorsOpen || (this.prevDoorMultiplier >= 0 && state.doorMultiplier < 0)))
        {
            track.recordAutoEvent(EVENT_DOOR_CLOSE, relativeTick);
        }

        /* 2. Motion Delta (Dispatch / Station Arrival) */
        if (!this.prevMoving && currMoving)
        {
            track.recordAutoEvent(EVENT_DISPATCH_DEPART, relativeTick);
        }
        else if (this.prevMoving && !currMoving)
        {
            track.stationStopRailProgress = state.railProgress;
            track.recordAutoEvent(EVENT_STATION_ARRIVE, relativeTick);
        }

        /* 3. Horn / Announcement Trigger */
        if (state.nextAnnouncementRailProgress > 0D
            && this.prevRailProgress < state.nextAnnouncementRailProgress
            && state.railProgress >= state.nextAnnouncementRailProgress)
        {
            track.recordAutoEvent(EVENT_HORN, relativeTick);
        }

        this.prevDoorsOpen = currDoorsOpen;
        this.prevDoorMultiplier = state.doorMultiplier;
        this.prevMoving = currMoving;
        this.prevRailProgress = state.railProgress;

        this.activeBlock.duration = Math.max(20, relativeTick + 1);
    }

    /**
     * Called when recording stops ({@code Recorder.stopRecording}) to lock the active block's duration
     * to the total elapsed ticks and commit it to the film's {@link WorldTimeline}.
     */
    public void stopRecording(Film film, int endTick)
    {
        int finalTick = Math.max(this.lastRecordedTick, endTick);

        this.lockActiveBlockDuration(finalTick);
        this.commitToFilm(film);
    }

    public void commitToFilm(Film film)
    {
        if (film == null || film.worldTimeline == null || this.capturedBlocks.isEmpty())
        {
            return;
        }

        List<WorldEventBlock> existing = film.worldTimeline.getBlocks();

        for (WorldEventBlock block : this.capturedBlocks)
        {
            if (block == null)
            {
                continue;
            }

            boolean found = false;

            for (WorldEventBlock e : existing)
            {
                if (e != null && e.id != null && e.id.equals(block.id))
                {
                    found = true;
                    break;
                }
            }

            if (!found)
            {
                film.worldTimeline.addBlock(block);
            }
        }
    }

    private void openBlockForVehicle(Film film, MtrCompatHelper.VehicleLiveState state, int startTick)
    {
        this.trackedVehicleId = state.vehicleId;

        String vehicleIdentifier = state.sidingId != 0L ? Long.toString(state.sidingId) : Long.toString(state.vehicleId);
        String routeDisplay = this.formatRouteName(state.routeName);
        String label = routeDisplay.isEmpty() ? vehicleIdentifier : (routeDisplay + " #" + vehicleIdentifier);
        String title = "Kaydedilen Sefer: " + label;

        int nextLayer = this.findNextAvailableLayer(film);
        boolean isFlight = "AIRPLANE".equalsIgnoreCase(state.transportMode);

        WorldEventBlock block = isFlight
            ? WorldEventBlock.createFlightOperation(title, startTick, 100, nextLayer)
            : WorldEventBlock.createDispatchTrain(title, startTick, 100, nextLayer);

        MtrDispatchTrack track = block.subDopeSheet.getPrimaryTrack();

        track.sidingId = vehicleIdentifier;
        track.targetStationId = state.stationId;
        track.initialRailProgress = state.railProgress;
        track.initialSpeed = state.speed;
        track.dispatchTick = 0;
        track.openDoorsTick = 40;
        track.dwellTimeTicks = 60;
        track.closeDoorsTick = 100;
        track.syncChannelFromTicks();

        MtrCompatHelper.captureVehicleIdentity(track.vehicle, state.vehicleId);
        track.vehicle.record(0, state.railProgress, state.speed, state.doorValue, state.doorMultiplier, state.reversed);
        block.recording = true;

        this.activeBlock = block;
        this.capturedBlocks.add(block);

        if (film != null && film.worldTimeline != null)
        {
            film.worldTimeline.addBlock(block);
        }
    }

    private void seedInitialState(MtrCompatHelper.VehicleLiveState state)
    {
        this.initializedState = true;
        this.prevDoorsOpen = state.doorValue > DOOR_OPEN_THRESHOLD || state.doorMultiplier > 0;
        this.prevDoorMultiplier = state.doorMultiplier;
        this.prevMoving = Math.abs(state.speed) > SPEED_THRESHOLD;
        this.prevRailProgress = state.railProgress;
    }

    private void lockActiveBlockDuration(int endTick)
    {
        if (this.activeBlock == null)
        {
            return;
        }

        int elapsed = Math.max(1, endTick - this.activeBlock.startTick);
        MtrDispatchTrack track = this.activeBlock.subDopeSheet.getPrimaryTrack();

        this.activeBlock.duration = Math.max(elapsed, track.closeDoorsTick + 1);
        track.syncChannelFromTicks();
        this.activeBlock.recording = false;
        this.activeBlock = null;
    }

    private int findNextAvailableLayer(Film film)
    {
        int layer = 0;

        if (film != null && film.worldTimeline != null)
        {
            for (WorldEventBlock b : film.worldTimeline.getBlocks())
            {
                if (b != null)
                {
                    layer = Math.max(layer, b.layer + 1);
                }
            }
        }

        for (WorldEventBlock b : this.capturedBlocks)
        {
            if (b != null)
            {
                layer = Math.max(layer, b.layer + 1);
            }
        }

        return layer;
    }

    private String formatRouteName(String raw)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return "";
        }

        int pipeIndex = raw.indexOf("||");

        return (pipeIndex >= 0 ? raw.substring(0, pipeIndex) : raw).replace('|', ' ').trim();
    }

    private void resetState()
    {
        this.capturedBlocks.clear();
        this.activeBlock = null;
        this.trackedVehicleId = 0L;
        this.initializedState = false;
        this.prevDoorsOpen = false;
        this.prevDoorMultiplier = 0;
        this.prevMoving = false;
        this.prevRailProgress = 0D;
        this.lastRecordedTick = 0;
    }
}
