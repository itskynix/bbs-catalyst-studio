package mchorse.bbs.compat.mtr;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The single writer of BBS-governed MTR vehicles on the client.
 *
 * <p>A governed vehicle's rail progress, speed, travel direction and doors are whatever the
 * film says they are at the current scene tick. MTR keeps simulating and receiving server
 * updates, but the governed state is re-asserted right after MTR's own simulation step
 * (see {@code VehicleExtensionMixin}) and governed vehicles survive server removals
 * (see {@code PacketUpdateVehiclesLiftsMixin}), so neither wall-clock simulation nor
 * server packets can move them.</p>
 */
public final class MtrGovernor
{
    /** A target nobody refreshed for this long belongs to a film that is gone; hand the vehicle back */
    private static final long STALE_MILLIS = 2000L;

    private static final Map<Long, Target> TARGETS = new ConcurrentHashMap<>();
    /** Recorded vehicle ID -> live vehicle ID currently standing in for it */
    private static final Map<Long, Long> ALIASES = new ConcurrentHashMap<>();

    private static boolean simulateHookSeen = false;

    private MtrGovernor()
    {}

    public static boolean isActive()
    {
        return !TARGETS.isEmpty();
    }

    public static boolean isGoverned(long vehicleId)
    {
        return vehicleId != 0L && TARGETS.containsKey(vehicleId);
    }

    /**
     * Whether MTR's simulation hook has run at least once. If vehicles are governed and this
     * stays false, the installed MTR version does not match the mixin targets.
     */
    public static boolean isSimulateHookSeen()
    {
        return simulateHookSeen;
    }

    /**
     * Resolves the live vehicle ID that plays the role of the given recorded vehicle ID.
     */
    public static long resolveLiveId(long recordedVehicleId)
    {
        Long alias = ALIASES.get(recordedVehicleId);

        return alias == null ? recordedVehicleId : alias.longValue();
    }

    /**
     * Pins the given live vehicle to a state. A NaN {@code doorValue} leaves the doors to MTR.
     */
    public static void govern(String owner, long recordedVehicleId, long liveVehicleId, Object vehicle, double railProgress, double speed, double doorValue, int doorMultiplier, int reversed)
    {
        if (liveVehicleId == 0L || Double.isNaN(railProgress))
        {
            return;
        }

        Target target = TARGETS.get(liveVehicleId);

        if (target == null)
        {
            target = new Target();
            TARGETS.put(liveVehicleId, target);
        }

        target.owner = owner == null ? "" : owner;
        target.recordedVehicleId = recordedVehicleId;
        target.railProgress = railProgress;
        target.speed = speed;
        target.doorValue = doorValue;
        target.doorMultiplier = doorMultiplier;
        target.reversed = reversed;
        target.touched = System.currentTimeMillis();

        if (recordedVehicleId != 0L)
        {
            ALIASES.put(recordedVehicleId, liveVehicleId);
        }

        /* Apply right away, so poses read before MTR's next simulation step are already correct.
         * Doors are only written from the simulation hook to keep MTR's door sound edges intact. */
        if (vehicle != null)
        {
            MtrCompatHelper.writeGovernedState(vehicle, target, false);
        }
    }

    public static void release(long liveVehicleId)
    {
        Target target = TARGETS.remove(liveVehicleId);

        if (target != null)
        {
            ALIASES.remove(target.recordedVehicleId, liveVehicleId);
        }
    }

    public static void releaseOwner(String owner)
    {
        if (owner == null || TARGETS.isEmpty())
        {
            return;
        }

        Iterator<Map.Entry<Long, Target>> it = TARGETS.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Long, Target> entry = it.next();

            if (owner.equals(entry.getValue().owner))
            {
                ALIASES.remove(entry.getValue().recordedVehicleId, entry.getKey());
                it.remove();
            }
        }
    }

    public static void releaseAll()
    {
        TARGETS.clear();
        ALIASES.clear();
    }

    /**
     * Called by {@code VehicleExtensionMixin} right after MTR simulated the given vehicle:
     * overwrites whatever MTR computed with the governed state.
     */
    public static void enforce(Object vehicle)
    {
        simulateHookSeen = true;

        if (TARGETS.isEmpty() || vehicle == null)
        {
            return;
        }

        long id = MtrCompatHelper.readVehicleId(vehicle);
        Target target = id == 0L ? null : TARGETS.get(id);

        if (target == null)
        {
            return;
        }

        if (System.currentTimeMillis() - target.touched > STALE_MILLIS)
        {
            release(id);

            return;
        }

        MtrCompatHelper.writeGovernedState(vehicle, target, true);
    }

    /**
     * Wraps the predicate MTR uses to drop client vehicles on a server update, so governed
     * vehicles are never removed (nor disposed) by the server.
     */
    public static Predicate<Object> wrapRemoval(Predicate<Object> original)
    {
        return (object) ->
        {
            if (!TARGETS.isEmpty() && MtrCompatHelper.isVehicle(object) && isGoverned(MtrCompatHelper.readVehicleId(object)))
            {
                return false;
            }

            return original.test(object);
        };
    }

    /**
     * Called after MTR applied a server update: a governed vehicle that the server re-sent now
     * exists twice (the kept one and the fresh one). Keep the fresh one and pin it again.
     */
    public static void afterServerUpdate()
    {
        if (TARGETS.isEmpty())
        {
            return;
        }

        MtrCompatHelper.dedupeAndEnforceGoverned(TARGETS);
    }

    public static final class Target
    {
        public String owner = "";
        public long recordedVehicleId;
        public double railProgress;
        public double speed;
        /** NaN: doors are left to MTR */
        public double doorValue = Double.NaN;
        public int doorMultiplier;
        /** -1: leave as is, 0: forwards, 1: reversed */
        public int reversed = -1;
        public long touched;
    }
}
