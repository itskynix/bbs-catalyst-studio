package mchorse.bbs_mod.film.world;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * MTR dispatch & station door control track inside a {@link SubDopeSheet}.
 *
 * <ul>
 *   <li>Keyframe 1 ({@link #dispatchTick}): Dispatch vehicle from siding/depot</li>
 *   <li>Keyframe 2 ({@link #openDoorsTick}): Arrive at target station and open doors</li>
 *   <li>Keyframe 3 ({@link #closeDoorsTick}): Close doors and depart after dwell time</li>
 * </ul>
 */
public class MtrDispatchTrack
{
    public static final String EVENT_DOOR_OPEN = "EVENT_DOOR_OPEN";
    public static final String EVENT_DOOR_CLOSE = "EVENT_DOOR_CLOSE";
    public static final String EVENT_DISPATCH_DEPART = "EVENT_DISPATCH_DEPART";
    public static final String EVENT_STATION_ARRIVE = "EVENT_STATION_ARRIVE";
    public static final String EVENT_HORN = "EVENT_HORN";

    public static final int CHANNEL_DISPATCH = 0;
    public static final int CHANNEL_OPEN_DOORS = 1;
    public static final int CHANNEL_CLOSE_DOORS = 2;
    public static final int CHANNEL_STATION_ARRIVE = 3;
    public static final int CHANNEL_HORN = 4;
    public static final int CHANNEL_COUNT = 5;

    public String sidingId = "";
    public long targetStationId = 0L;
    public int dwellTimeTicks = 100;

    public int dispatchTick = 0;
    public int openDoorsTick = 60;
    public int closeDoorsTick = 160;
    public int stationArriveTick = -1;
    public int hornTick = -1;

    public double initialRailProgress = Double.NaN;
    public double initialSpeed = 0D;
    public double stationStopRailProgress = Double.NaN;

    public final boolean[] channelVisible = new boolean[] {true, true, true, true, true};
    public final boolean[] channelLocked = new boolean[] {false, false, false, false, false};

    public final KeyframeChannel<Double> keyframes = new KeyframeChannel<>("mtr_dispatch", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> actionKeyframes = new KeyframeChannel<>("mtr_actions", KeyframeFactories.DOUBLE);

    /** Recorded per-tick trace of the vehicle; when present it alone drives the vehicle */
    public final MtrVehicleTrack vehicle = new MtrVehicleTrack();
    private final MtrVehicleTrack.Sample sample = new MtrVehicleTrack.Sample();

    public MtrDispatchTrack()
    {
        this.syncChannelFromTicks();
    }

    public void setDwellTimeTicks(int dwellTimeTicks)
    {
        this.dwellTimeTicks = Math.max(1, dwellTimeTicks);
        this.closeDoorsTick = this.openDoorsTick + this.dwellTimeTicks;
        this.syncChannelFromTicks();
    }

    public void setDispatchTick(int tick)
    {
        this.dispatchTick = Math.max(0, tick);

        if (this.openDoorsTick < this.dispatchTick)
        {
            this.openDoorsTick = this.dispatchTick;
        }

        if (this.closeDoorsTick < this.openDoorsTick + 1)
        {
            this.closeDoorsTick = this.openDoorsTick + Math.max(1, this.dwellTimeTicks);
        }

        this.syncChannelFromTicks();
    }

    public void setOpenDoorsTick(int tick)
    {
        this.openDoorsTick = Math.max(this.dispatchTick, tick);

        if (this.closeDoorsTick <= this.openDoorsTick)
        {
            this.closeDoorsTick = this.openDoorsTick + Math.max(1, this.dwellTimeTicks);
        }
        else
        {
            this.dwellTimeTicks = this.closeDoorsTick - this.openDoorsTick;
        }

        this.syncChannelFromTicks();
    }

    public void setCloseDoorsTick(int tick)
    {
        this.closeDoorsTick = Math.max(this.openDoorsTick + 1, tick);
        this.dwellTimeTicks = this.closeDoorsTick - this.openDoorsTick;
        this.syncChannelFromTicks();
    }

    public void setStationArriveTick(int tick)
    {
        this.stationArriveTick = tick < 0 ? -1 : tick;
        this.syncChannelFromTicks();
    }

    public void setHornTick(int tick)
    {
        this.hornTick = tick < 0 ? -1 : tick;
        this.syncChannelFromTicks();
    }

    public int getChannelTick(int channelIndex)
    {
        if (channelIndex == CHANNEL_DISPATCH)
        {
            return this.dispatchTick;
        }
        else if (channelIndex == CHANNEL_OPEN_DOORS)
        {
            return this.openDoorsTick;
        }
        else if (channelIndex == CHANNEL_CLOSE_DOORS)
        {
            return this.closeDoorsTick;
        }
        else if (channelIndex == CHANNEL_STATION_ARRIVE)
        {
            return this.stationArriveTick;
        }
        else if (channelIndex == CHANNEL_HORN)
        {
            return this.hornTick;
        }

        return -1;
    }

    public void setChannelTick(int channelIndex, int tick)
    {
        if (channelIndex >= 0 && channelIndex < CHANNEL_COUNT && this.channelLocked[channelIndex])
        {
            return;
        }

        if (channelIndex == CHANNEL_DISPATCH)
        {
            this.setDispatchTick(tick);
        }
        else if (channelIndex == CHANNEL_OPEN_DOORS)
        {
            this.setOpenDoorsTick(tick);
        }
        else if (channelIndex == CHANNEL_CLOSE_DOORS)
        {
            this.setCloseDoorsTick(tick);
        }
        else if (channelIndex == CHANNEL_STATION_ARRIVE)
        {
            this.setStationArriveTick(tick);
        }
        else if (channelIndex == CHANNEL_HORN)
        {
            this.setHornTick(tick);
        }
    }

    public boolean isChannelVisible(int channelIndex)
    {
        return channelIndex >= 0 && channelIndex < CHANNEL_COUNT && this.channelVisible[channelIndex];
    }

    public void toggleChannelVisible(int channelIndex)
    {
        if (channelIndex >= 0 && channelIndex < CHANNEL_COUNT)
        {
            this.channelVisible[channelIndex] = !this.channelVisible[channelIndex];
        }
    }

    public boolean isChannelLocked(int channelIndex)
    {
        return channelIndex >= 0 && channelIndex < CHANNEL_COUNT && this.channelLocked[channelIndex];
    }

    public void toggleChannelLocked(int channelIndex)
    {
        if (channelIndex >= 0 && channelIndex < CHANNEL_COUNT)
        {
            this.channelLocked[channelIndex] = !this.channelLocked[channelIndex];
        }
    }

    public void recordAutoEvent(String eventType, int relativeTick)
    {
        int clampedTick = Math.max(0, relativeTick);

        if (EVENT_DISPATCH_DEPART.equals(eventType))
        {
            this.dispatchTick = clampedTick;

            if (this.openDoorsTick < this.dispatchTick)
            {
                this.openDoorsTick = this.dispatchTick + 20;
            }

            if (this.closeDoorsTick <= this.openDoorsTick)
            {
                this.closeDoorsTick = this.openDoorsTick + Math.max(1, this.dwellTimeTicks);
            }

            this.actionKeyframes.insert(clampedTick, 1D);
        }
        else if (EVENT_STATION_ARRIVE.equals(eventType))
        {
            this.stationArriveTick = clampedTick;
            this.actionKeyframes.insert(clampedTick, 2D);
        }
        else if (EVENT_DOOR_OPEN.equals(eventType))
        {
            if (clampedTick < this.dispatchTick)
            {
                this.dispatchTick = clampedTick;
            }

            this.openDoorsTick = clampedTick;

            if (this.closeDoorsTick <= this.openDoorsTick)
            {
                this.closeDoorsTick = this.openDoorsTick + Math.max(20, this.dwellTimeTicks);
            }
            else
            {
                this.dwellTimeTicks = this.closeDoorsTick - this.openDoorsTick;
            }

            this.actionKeyframes.insert(clampedTick, 3D);
        }
        else if (EVENT_DOOR_CLOSE.equals(eventType))
        {
            if (clampedTick <= this.openDoorsTick)
            {
                this.openDoorsTick = Math.max(this.dispatchTick, clampedTick - 1);
            }

            this.closeDoorsTick = Math.max(this.openDoorsTick + 1, clampedTick);
            this.dwellTimeTicks = this.closeDoorsTick - this.openDoorsTick;
            this.actionKeyframes.insert(clampedTick, 4D);
        }
        else if (EVENT_HORN.equals(eventType))
        {
            this.hornTick = clampedTick;
            this.actionKeyframes.insert(clampedTick, 5D);
        }

        this.syncChannelFromTicks();
    }

    public void syncChannelFromTicks()
    {
        this.keyframes.removeAll();
        this.keyframes.insert(this.dispatchTick, 1D);
        this.keyframes.insert(this.openDoorsTick, 2D);
        this.keyframes.insert(this.closeDoorsTick, 3D);

        if (this.hornTick >= 0)
        {
            this.keyframes.insert(this.hornTick, 4D);
        }
    }

    public void syncTicksFromChannel()
    {
        int size = this.keyframes.getKeyframes().size();

        if (size >= 1)
        {
            this.dispatchTick = Math.max(0, Math.round(this.keyframes.get(0).getTick()));
        }

        if (size >= 2)
        {
            this.openDoorsTick = Math.max(this.dispatchTick, Math.round(this.keyframes.get(1).getTick()));
        }

        if (size >= 3)
        {
            this.closeDoorsTick = Math.max(this.openDoorsTick + 1, Math.round(this.keyframes.get(2).getTick()));
            this.dwellTimeTicks = this.closeDoorsTick - this.openDoorsTick;
        }
    }

    public void apply(WorldEventBlock block, float localTick, float masterTick, WorldTimeline.IMtrWorldBridge bridge)
    {
        if (bridge == null)
        {
            return;
        }

        if (this.vehicle.hasData())
        {
            /* Recorded take: the vehicle is a pure function of the scene tick */
            if (this.vehicle.sample(localTick, this.sample))
            {
                block.activeVehicleId = bridge.governVehicle(
                    block.id,
                    this.vehicle.vehicleId,
                    this.vehicle.sidingId,
                    this.sample.railProgress,
                    this.sample.speed,
                    this.sample.doorValue,
                    this.sample.doorMultiplier,
                    this.sample.reversed
                );
                block.dispatched = block.activeVehicleId != 0L;
            }

            return;
        }

        if (localTick < this.dispatchTick)
        {
            if (block.dispatched || block.activeVehicleId != 0L)
            {
                bridge.recallOrResetVehicle(this.sidingId, block.activeVehicleId);
                block.dispatched = false;
                block.activeVehicleId = 0L;
                block.lastDoorOpenState = null;
            }

            return;
        }

        if (!block.dispatched || block.activeVehicleId == 0L)
        {
            long vehicleId = bridge.dispatchVehicle(this.sidingId, this.targetStationId);

            block.activeVehicleId = vehicleId;
            block.dispatched = true;
        }

        if (this.isChannelVisible(CHANNEL_DISPATCH) || this.isChannelVisible(CHANNEL_OPEN_DOORS) || this.isChannelVisible(CHANNEL_CLOSE_DOORS))
        {
            bridge.syncVehicleProgress(
                this.sidingId,
                block.activeVehicleId,
                this.targetStationId,
                this.dwellTimeTicks,
                localTick,
                block.duration,
                this.dispatchTick,
                this.openDoorsTick,
                this.closeDoorsTick,
                this.initialRailProgress,
                this.initialSpeed,
                this.stationStopRailProgress
            );
        }

        if (this.isChannelVisible(CHANNEL_OPEN_DOORS) && this.isChannelVisible(CHANNEL_CLOSE_DOORS))
        {
            boolean doorsOpen = localTick >= this.openDoorsTick && localTick < this.closeDoorsTick;

            if (block.activeVehicleId != 0L && (block.lastDoorOpenState == null || block.lastDoorOpenState.booleanValue() != doorsOpen))
            {
                bridge.setTrainDoorState(block.activeVehicleId, doorsOpen);
                block.lastDoorOpenState = Boolean.valueOf(doorsOpen);
            }
        }
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

        map.putString("siding_id", this.sidingId == null ? "" : this.sidingId);
        map.putLong("target_station_id", this.targetStationId);
        map.putInt("dwell_time_ticks", this.dwellTimeTicks);
        map.putInt("dispatch_tick", this.dispatchTick);
        map.putInt("open_doors_tick", this.openDoorsTick);
        map.putInt("close_doors_tick", this.closeDoorsTick);
        map.putInt("station_arrive_tick", this.stationArriveTick);
        map.putInt("horn_tick", this.hornTick);

        if (!Double.isNaN(this.initialRailProgress))
        {
            map.putDouble("initial_rail_progress", this.initialRailProgress);
            map.putDouble("initial_speed", this.initialSpeed);
        }

        if (!Double.isNaN(this.stationStopRailProgress))
        {
            map.putDouble("station_stop_rail_progress", this.stationStopRailProgress);
        }

        int visibleMask = 0;
        int lockedMask = 0;

        for (int i = 0; i < CHANNEL_COUNT; i++)
        {
            if (this.channelVisible[i])
            {
                visibleMask |= (1 << i);
            }

            if (this.channelLocked[i])
            {
                lockedMask |= (1 << i);
            }
        }

        map.putInt("channel_visible_mask", visibleMask);
        map.putInt("channel_locked_mask", lockedMask);
        map.put("keyframes", this.keyframes.toData());
        map.put("action_keyframes", this.actionKeyframes.toData());

        if (this.vehicle.hasData())
        {
            map.put("vehicle_track", this.vehicle.toNbt());
        }
    }

    public void fromNbt(MapType map)
    {
        if (map == null)
        {
            return;
        }

        if (map.has("siding_id"))
        {
            this.sidingId = map.getString("siding_id");
        }

        if (map.has("target_station_id"))
        {
            this.targetStationId = map.getLong("target_station_id");
        }

        if (map.has("dwell_time_ticks"))
        {
            this.dwellTimeTicks = Math.max(1, map.getInt("dwell_time_ticks"));
        }

        if (map.has("dispatch_tick"))
        {
            this.dispatchTick = Math.max(0, map.getInt("dispatch_tick"));
        }

        if (map.has("open_doors_tick"))
        {
            this.openDoorsTick = Math.max(this.dispatchTick, map.getInt("open_doors_tick"));
        }

        if (map.has("close_doors_tick"))
        {
            this.closeDoorsTick = Math.max(this.openDoorsTick + 1, map.getInt("close_doors_tick"));
        }
        else
        {
            this.closeDoorsTick = this.openDoorsTick + this.dwellTimeTicks;
        }

        if (map.has("station_arrive_tick"))
        {
            this.stationArriveTick = map.getInt("station_arrive_tick");
        }

        if (map.has("horn_tick"))
        {
            this.hornTick = map.getInt("horn_tick");
        }

        if (map.has("initial_rail_progress"))
        {
            this.initialRailProgress = map.getDouble("initial_rail_progress");
        }
        else
        {
            this.initialRailProgress = Double.NaN;
        }

        if (map.has("initial_speed"))
        {
            this.initialSpeed = map.getDouble("initial_speed");
        }
        else
        {
            this.initialSpeed = 0D;
        }

        if (map.has("station_stop_rail_progress"))
        {
            this.stationStopRailProgress = map.getDouble("station_stop_rail_progress");
        }
        else
        {
            this.stationStopRailProgress = Double.NaN;
        }

        if (map.has("channel_visible_mask"))
        {
            int visibleMask = map.getInt("channel_visible_mask");

            for (int i = 0; i < CHANNEL_COUNT; i++)
            {
                this.channelVisible[i] = (visibleMask & (1 << i)) != 0;
            }
        }

        if (map.has("channel_locked_mask"))
        {
            int lockedMask = map.getInt("channel_locked_mask");

            for (int i = 0; i < CHANNEL_COUNT; i++)
            {
                this.channelLocked[i] = (lockedMask & (1 << i)) != 0;
            }
        }

        if (map.has("keyframes"))
        {
            this.keyframes.fromData(map.get("keyframes"));
        }

        if (map.has("action_keyframes"))
        {
            this.actionKeyframes.fromData(map.get("action_keyframes"));
        }

        if (map.has("vehicle_track") && map.get("vehicle_track").isMap())
        {
            this.vehicle.fromNbt(map.getMap("vehicle_track"));
        }
        else
        {
            this.vehicle.clear();
        }

        this.syncChannelFromTicks();
    }

    public MtrDispatchTrack copy()
    {
        MtrDispatchTrack copy = new MtrDispatchTrack();

        copy.sidingId = this.sidingId;
        copy.targetStationId = this.targetStationId;
        copy.dwellTimeTicks = this.dwellTimeTicks;
        copy.dispatchTick = this.dispatchTick;
        copy.openDoorsTick = this.openDoorsTick;
        copy.closeDoorsTick = this.closeDoorsTick;
        copy.stationArriveTick = this.stationArriveTick;
        copy.hornTick = this.hornTick;
        copy.initialRailProgress = this.initialRailProgress;
        copy.initialSpeed = this.initialSpeed;
        copy.stationStopRailProgress = this.stationStopRailProgress;

        for (int i = 0; i < CHANNEL_COUNT; i++)
        {
            copy.channelVisible[i] = this.channelVisible[i];
            copy.channelLocked[i] = this.channelLocked[i];
        }

        copy.actionKeyframes.copyOver(this.actionKeyframes, 0);
        copy.vehicle.copyFrom(this.vehicle);
        copy.syncChannelFromTicks();

        return copy;
    }
}
