package mchorse.bbs_mod.film.world;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.List;

/**
 * World Timeline data model attached to {@link mchorse.bbs_mod.film.Film}.
 * Stores and evaluates {@link WorldEventBlock} blocks for MTR train/subway/flight operations.
 */
public class WorldTimeline extends BaseValue
{
    private static IMtrWorldBridge mtrBridge;

    public final List<WorldEventBlock> blocks = new ArrayList<>();

    public WorldTimeline()
    {
        this("world_timeline");
    }

    public WorldTimeline(String id)
    {
        super(id);
    }

    public static void setMtrBridge(IMtrWorldBridge bridge)
    {
        mtrBridge = bridge;
    }

    public static IMtrWorldBridge getMtrBridge()
    {
        return mtrBridge;
    }

    public List<WorldEventBlock> getBlocks()
    {
        return this.blocks;
    }

    public void addBlock(WorldEventBlock block)
    {
        if (block == null)
        {
            return;
        }

        this.preNotify();
        this.blocks.add(block);
        this.postNotify();
    }

    public void removeBlock(WorldEventBlock block)
    {
        if (block == null)
        {
            return;
        }

        this.preNotify();
        block.onScrubBeforeStart(mtrBridge);
        this.blocks.remove(block);
        this.postNotify();
    }

    public int calculateDuration()
    {
        int max = 0;

        for (WorldEventBlock block : this.blocks)
        {
            if (block != null && block.enabled)
            {
                max = Math.max(max, block.getEndTick());
            }
        }

        return max;
    }

    /**
     * Evaluates all blocks at the given scene {@code tick}.
     * Includes scrubbing protection when {@code tick < block.startTick}.
     */
    public void apply(float tick)
    {
        if (this.blocks.isEmpty() || mtrBridge == null)
        {
            return;
        }

        for (WorldEventBlock block : this.blocks)
        {
            if (block == null)
            {
                continue;
            }

            if (!block.enabled)
            {
                block.onScrubBeforeStart(mtrBridge);

                continue;
            }

            if (tick < block.startTick)
            {
                /* Scrubbing protection: safely recall/reset the vehicle to its depot when rewound before startTick */
                block.onScrubBeforeStart(mtrBridge);
            }
            else if (tick <= block.getEndTick())
            {
                float localTick = tick - block.startTick;

                block.apply(localTick, tick, mtrBridge);
            }
            else
            {
                block.onAfterEnd(mtrBridge);
            }
        }
    }

    public void resetAll()
    {
        if (mtrBridge == null)
        {
            return;
        }

        for (WorldEventBlock block : this.blocks)
        {
            if (block != null)
            {
                block.onScrubBeforeStart(mtrBridge);
            }
        }
    }

    public void toNbt(MapType data)
    {
        if (data == null)
        {
            return;
        }

        ListType list = new ListType();

        for (WorldEventBlock block : this.blocks)
        {
            if (block != null)
            {
                list.add(block.toNbt());
            }
        }

        data.put("blocks", list);
    }

    public void fromNbt(MapType data)
    {
        this.blocks.clear();

        if (data == null || !data.has("blocks") || !data.get("blocks").isList())
        {
            return;
        }

        ListType list = data.getList("blocks");

        for (int i = 0; i < list.size(); i++)
        {
            BaseType item = list.get(i);

            if (item != null && item.isMap())
            {
                WorldEventBlock block = new WorldEventBlock();

                block.fromNbt(item.asMap());
                this.blocks.add(block);
            }
        }
    }

    @Override
    public BaseType toData()
    {
        MapType map = new MapType();

        this.toNbt(map);

        return map;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data == null)
        {
            this.blocks.clear();

            return;
        }

        if (data.isMap())
        {
            this.fromNbt(data.asMap());
        }
        else if (data.isList())
        {
            MapType wrapper = new MapType();

            wrapper.put("blocks", data.asList());
            this.fromNbt(wrapper);
        }
        else
        {
            this.blocks.clear();
        }
    }

    /**
     * Bridge interface implemented on the client by {@code MtrCompatHelper} so {@code src/main}
     * never imports client-only or MTR classes directly.
     */
    public interface IMtrWorldBridge
    {
        long dispatchVehicle(String sidingId, long targetStationId);

        void setTrainDoorState(long vehicleId, boolean open);

        void recallOrResetVehicle(String sidingId, long vehicleId);

        /**
         * Takes over the vehicle recorded as {@code recordedVehicleId} (or a live vehicle of
         * {@code sidingId}) on behalf of {@code ownerId} and pins it to the given state.
         *
         * @return the live vehicle ID being governed, or 0 when no such vehicle is loaded
         */
        long governVehicle(String ownerId, long recordedVehicleId, long sidingId, double railProgress, double speed, double doorValue, int doorMultiplier, boolean reversed);

        /** Hands every vehicle governed on behalf of {@code ownerId} back to MTR */
        void releaseVehicle(String ownerId);

        void syncVehicleProgress(
            String sidingId,
            long vehicleId,
            long targetStationId,
            int dwellTimeTicks,
            float localTick,
            int duration,
            int dispatchTick,
            int openDoorsTick,
            int closeDoorsTick,
            double initialRailProgress,
            double initialSpeed,
            double stationStopRailProgress
        );
    }
}
