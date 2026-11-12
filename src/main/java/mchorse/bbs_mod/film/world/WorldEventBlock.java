package mchorse.bbs_mod.film.world;

import mchorse.bbs_mod.data.types.MapType;

import java.util.UUID;

/**
 * Represents a world event block on the {@link WorldTimeline}.
 * Double-clicking this block in the UI opens its embedded {@link SubDopeSheet}.
 */
public class WorldEventBlock
{
    public static final String TYPE_DISPATCH_TRAIN = "mtr_dispatch_train";
    public static final String TYPE_FLIGHT_OPERATION = "mtr_flight_operation";
    public static final String TYPE_STATION_DOORS = "mtr_station_doors";

    public static final int COLOR_DISPATCH_TRAIN = 0xFF1E88E5;
    public static final int COLOR_FLIGHT_OPERATION = 0xFF00ACC1;
    public static final int COLOR_STATION_DOORS = 0xFF43A047;

    public String id;
    public String title;
    public int startTick;
    public int duration;
    public boolean enabled = true;
    public SubDopeSheet subDopeSheet = new SubDopeSheet();

    public int layer = 0;
    public String operationType = TYPE_DISPATCH_TRAIN;
    public int color = COLOR_DISPATCH_TRAIN;

    public transient long activeVehicleId = 0L;
    public transient boolean dispatched = false;
    public transient Boolean lastDoorOpenState = null;
    /** True while a take is still writing this block; it must not drive the vehicle it is recording */
    public transient boolean recording = false;

    public WorldEventBlock()
    {
        this.id = UUID.randomUUID().toString();
        this.title = "MTR Seferi";
        this.startTick = 0;
        this.duration = 200;
    }

    public WorldEventBlock(String title, int startTick, int duration, int layer, String operationType, int color)
    {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.startTick = Math.max(0, startTick);
        this.duration = Math.max(1, duration);
        this.layer = Math.max(0, layer);
        this.operationType = operationType;
        this.color = color;
    }

    public static WorldEventBlock createDispatchTrain(String title, int startTick, int duration, int layer)
    {
        WorldEventBlock block = new WorldEventBlock(title, startTick, duration, layer, TYPE_DISPATCH_TRAIN, COLOR_DISPATCH_TRAIN);
        MtrDispatchTrack track = block.subDopeSheet.getPrimaryTrack();

        track.dispatchTick = 0;
        track.openDoorsTick = Math.min(60, Math.max(10, duration / 3));
        track.dwellTimeTicks = Math.max(20, duration / 2);
        track.closeDoorsTick = track.openDoorsTick + track.dwellTimeTicks;
        track.syncChannelFromTicks();

        return block;
    }

    public static WorldEventBlock createFlightOperation(String title, int startTick, int duration, int layer)
    {
        WorldEventBlock block = new WorldEventBlock(title, startTick, duration, layer, TYPE_FLIGHT_OPERATION, COLOR_FLIGHT_OPERATION);
        MtrDispatchTrack track = block.subDopeSheet.getPrimaryTrack();

        track.dispatchTick = 0;
        track.openDoorsTick = Math.min(80, Math.max(10, duration / 3));
        track.dwellTimeTicks = Math.max(20, duration / 2);
        track.closeDoorsTick = track.openDoorsTick + track.dwellTimeTicks;
        track.syncChannelFromTicks();

        return block;
    }

    public static WorldEventBlock createStationDoors(String title, int startTick, int duration, int layer)
    {
        WorldEventBlock block = new WorldEventBlock(title, startTick, duration, layer, TYPE_STATION_DOORS, COLOR_STATION_DOORS);
        MtrDispatchTrack track = block.subDopeSheet.getPrimaryTrack();

        track.dispatchTick = 0;
        track.openDoorsTick = 0;
        track.dwellTimeTicks = Math.max(10, duration - 20);
        track.closeDoorsTick = track.openDoorsTick + track.dwellTimeTicks;
        track.syncChannelFromTicks();

        return block;
    }

    public int getEndTick()
    {
        return this.startTick + this.duration;
    }

    public void onScrubBeforeStart(WorldTimeline.IMtrWorldBridge bridge)
    {
        if (bridge != null && (this.dispatched || this.activeVehicleId != 0L))
        {
            MtrDispatchTrack track = this.subDopeSheet.getPrimaryTrack();

            bridge.recallOrResetVehicle(track.sidingId, this.activeVehicleId);
            bridge.releaseVehicle(this.id);
        }

        this.dispatched = false;
        this.activeVehicleId = 0L;
        this.lastDoorOpenState = null;
    }

    public void onAfterEnd(WorldTimeline.IMtrWorldBridge bridge)
    {
        if (bridge != null && this.activeVehicleId != 0L && Boolean.TRUE.equals(this.lastDoorOpenState))
        {
            bridge.setTrainDoorState(this.activeVehicleId, false);
            this.lastDoorOpenState = Boolean.FALSE;
        }

        if (bridge != null && (this.dispatched || this.activeVehicleId != 0L))
        {
            bridge.releaseVehicle(this.id);
            this.dispatched = false;
            this.activeVehicleId = 0L;
        }
    }

    public void apply(float localTick, float masterTick, WorldTimeline.IMtrWorldBridge bridge)
    {
        if (!this.enabled || this.recording || this.subDopeSheet == null)
        {
            return;
        }

        this.subDopeSheet.apply(this, localTick, masterTick, bridge);
    }

    public MapType toNbt()
    {
        MapType map = new MapType();

        this.toNbt(map);

        return map;
    }

    public void toNbt(MapType map)
    {
        if (map == null)
        {
            return;
        }

        map.putString("id", this.id == null ? UUID.randomUUID().toString() : this.id);
        map.putString("title", this.title == null ? "" : this.title);
        map.putInt("start_tick", this.startTick);
        map.putInt("duration", this.duration);
        map.putBool("enabled", this.enabled);
        map.putInt("layer", this.layer);
        map.putString("operation_type", this.operationType == null ? TYPE_DISPATCH_TRAIN : this.operationType);
        map.putInt("color", this.color);

        if (this.subDopeSheet != null)
        {
            map.put("sub_dope_sheet", this.subDopeSheet.toNbt());
        }
    }

    public void fromNbt(MapType map)
    {
        if (map == null)
        {
            return;
        }

        this.id = map.has("id") ? map.getString("id") : UUID.randomUUID().toString();
        this.title = map.has("title") ? map.getString("title") : "";
        this.startTick = map.has("start_tick") ? Math.max(0, map.getInt("start_tick")) : 0;
        this.duration = map.has("duration") ? Math.max(1, map.getInt("duration")) : 200;
        this.enabled = !map.has("enabled") || map.getBool("enabled");
        this.layer = map.has("layer") ? Math.max(0, map.getInt("layer")) : 0;
        this.operationType = map.has("operation_type") ? map.getString("operation_type") : TYPE_DISPATCH_TRAIN;

        if (map.has("color"))
        {
            this.color = map.getInt("color");
        }
        else if (TYPE_FLIGHT_OPERATION.equals(this.operationType))
        {
            this.color = COLOR_FLIGHT_OPERATION;
        }
        else if (TYPE_STATION_DOORS.equals(this.operationType))
        {
            this.color = COLOR_STATION_DOORS;
        }
        else
        {
            this.color = COLOR_DISPATCH_TRAIN;
        }

        this.subDopeSheet = new SubDopeSheet();

        if (map.has("sub_dope_sheet") && map.get("sub_dope_sheet").isMap())
        {
            this.subDopeSheet.fromNbt(map.getMap("sub_dope_sheet"));
        }
    }

    public WorldEventBlock copy()
    {
        WorldEventBlock copy = new WorldEventBlock();

        copy.id = UUID.randomUUID().toString();
        copy.title = this.title;
        copy.startTick = this.startTick;
        copy.duration = this.duration;
        copy.enabled = this.enabled;
        copy.layer = this.layer;
        copy.operationType = this.operationType;
        copy.color = this.color;
        copy.subDopeSheet = this.subDopeSheet == null ? new SubDopeSheet() : this.subDopeSheet.copy();

        return copy;
    }
}
