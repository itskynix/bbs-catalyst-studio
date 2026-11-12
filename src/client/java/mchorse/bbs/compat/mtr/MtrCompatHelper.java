package mchorse.bbs.compat.mtr;

import mchorse.bbs.compat.mtr.mixin.VehicleRidingMovementAccessor;
import mchorse.bbs_mod.film.world.WorldTimeline;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.film.world.MtrVehicleTrack;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Isolated compatibility helper for Minecraft Transit Railway (MTR).
 * Prevents class-loading errors when MTR is not present in the runtime environment.
 */
public final class MtrCompatHelper
{
    private static final boolean MTR_LOADED = FabricLoader.getInstance().isModLoaded("mtr");

    private static boolean reflectionInitialized = false;
    private static Field fieldRidingVehicleId;
    private static Field fieldRidingVehicleCarNumber;
    private static Field fieldRidingVehicleX;
    private static Field fieldRidingVehicleY;
    private static Field fieldRidingVehicleZ;
    private static Field fieldPreviousVehicleYaw;

    private static boolean poseReflectionInitialized = false;
    private static boolean poseReflectionAvailable = false;
    private static Method methodClientDataGetInstance;
    private static Field fieldClientDataVehicles;
    private static Method methodVehicleGetId;
    private static Method methodVehicleGetTransportMode;
    private static Method methodVehicleGetSmoothedCars;
    private static Field fieldTransportModeHasPitchAscending;
    private static Field fieldTransportModeHasPitchDescending;
    private static Method methodPairLeft;
    private static Method methodPairRight;
    private static Constructor<?> ctorObjectArrayList;
    private static Constructor<?> ctorBogiePositionAndRotation;
    private static Constructor<?> ctorCarPositionAndRotation;
    private static Field fieldPosAndRotPosition;
    private static Field fieldPosAndRotYaw;
    private static Field fieldPosAndRotPitch;
    private static Field fieldVectorX;
    private static Field fieldVectorY;
    private static Field fieldVectorZ;
    private static Method methodGetRidingCarAndOffset;
    private static Method methodIntObjectPairLeftInt;
    private static Method methodIntObjectPairRight;
    private static Method methodHolderVec3dGetX;
    private static Method methodHolderVec3dGetY;
    private static Method methodHolderVec3dGetZ;

    private static boolean simReflectionInitialized = false;
    private static boolean simReflectionAvailable = false;
    private static Field fieldVehicleExtraData;
    private static Field fieldVehiclePersistentData;
    private static Field fieldVehicleRailProgress;
    private static Field fieldVehicleSpeed;
    private static Method methodVehicleStartUp;
    private static Method methodExtraGetSidingId;
    private static Method methodExtraGetThisRouteName;
    private static Field fieldExtraImmutablePath;
    private static Method methodExtraGetTotalVehicleLength;
    private static Method methodPathGetSavedRailBaseId;
    private static Method methodPathGetStartDistance;
    private static Method methodPathGetEndDistance;
    private static Method methodPathGetDwellTime;
    private static Method methodPersistentOverrideDoor;
    private static Method methodPersistentGetAdjustedDoorMultiplier;
    private static Method methodExtraGetDoorMultiplier;
    private static Method methodVehicleGetHeadPosition;
    private static Field fieldPersistentDoorValue;
    private static Field fieldPersistentOldDoorValue;
    private static Field fieldPersistentDoorCooldown;
    private static Field fieldPersistentNextAnnouncementRailProgress;
    private static Field fieldPersistentSmoothedRailProgress;
    private static Field fieldPersistentSmoothingAdjustment;

    private static Class<?> classVehicleExtension;
    private static Field fieldVehicleReversed;
    private static Method methodExtraGetDepotId;
    private static Method methodExtraGetThisRouteId;
    private static Method methodUtilitiesGetJson;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_FAILURES = new HashSet<>();
    /** Upper bound of a compressed vehicle snapshot stored in a film (bytes) */
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;

    private static final Map<Long, VehicleSnapshot> INITIAL_VEHICLE_SNAPSHOTS = new HashMap<>();
    private static final Map<Long, Boolean> LAST_DOOR_STATES = new HashMap<>();

    static
    {
        registerWorldTimelineBridge();
    }

    private MtrCompatHelper()
    {}

    public static void registerWorldTimelineBridge()
    {
        if (!MTR_LOADED)
        {
            return;
        }

        if (WorldTimeline.getMtrBridge() == null)
        {
            WorldTimeline.setMtrBridge(new WorldTimeline.IMtrWorldBridge()
            {
                @Override
                public long dispatchVehicle(String sidingId, long targetStationId)
                {
                    return MtrCompatHelper.dispatchVehicle(sidingId, targetStationId);
                }

                @Override
                public void setTrainDoorState(long vehicleId, boolean open)
                {
                    MtrCompatHelper.setTrainDoorState(vehicleId, open);
                }

                @Override
                public void recallOrResetVehicle(String sidingId, long vehicleId)
                {
                    MtrCompatHelper.recallVehicleToDepot(sidingId, vehicleId);
                }

                @Override
                public long governVehicle(String ownerId, long recordedVehicleId, long sidingId, double railProgress, double speed, double doorValue, int doorMultiplier, boolean reversed)
                {
                    return MtrCompatHelper.governVehicle(ownerId, recordedVehicleId, sidingId, railProgress, speed, doorValue, doorMultiplier, reversed);
                }

                @Override
                public void releaseVehicle(String ownerId)
                {
                    MtrGovernor.releaseOwner(ownerId);
                }

                @Override
                public void syncVehicleProgress(
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
                )
                {
                    MtrCompatHelper.syncVehicleProgress(
                        sidingId,
                        vehicleId,
                        targetStationId,
                        dwellTimeTicks,
                        localTick,
                        duration,
                        dispatchTick,
                        openDoorsTick,
                        closeDoorsTick,
                        initialRailProgress,
                        initialSpeed,
                        stationStopRailProgress
                    );
                }
            });
        }
    }

    public static boolean isMtrLoaded()
    {
        return MTR_LOADED;
    }

    public static boolean isPlayerInVehicle()
    {
        if (!isMtrLoaded())
        {
            return false;
        }

        return VehicleRidingMovementAccessor.getRidingVehicleId() != 0L;
    }

    public static RideData getRideData()
    {
        if (!isPlayerInVehicle())
        {
            return null;
        }

        long vehicleId = VehicleRidingMovementAccessor.getRidingVehicleId();
        int carNumber = VehicleRidingMovementAccessor.getRidingVehicleCarNumber();
        float localX = (float) VehicleRidingMovementAccessor.getRidingVehicleX();
        float localY = (float) VehicleRidingMovementAccessor.getRidingVehicleY();
        float localZ = (float) VehicleRidingMovementAccessor.getRidingVehicleZ();

        return new RideData(vehicleId, carNumber, localX, localY, localZ);
    }

    public static float getRelativeYaw(float playerYaw)
    {
        if (!isPlayerInVehicle())
        {
            return playerYaw;
        }

        double vehicleYawRad = VehicleRidingMovementAccessor.getPreviousVehicleYaw();
        float vehicleYawDeg = (float) Math.toDegrees(vehicleYawRad);

        return MathHelper.wrapDegrees(playerYaw - vehicleYawDeg);
    }

    /**
     * Finds the live MTR vehicle by {@code vehicleId} in {@code MinecraftClientData.getInstance().vehicles}
     * and returns the smoothed {@link PositionAndRotation} of {@code carNumber}, or {@code null} if not found.
     */
    public static PositionAndRotation getVehicleCarPose(long vehicleId, int carNumber)
    {
        if (!isMtrLoaded() || vehicleId == 0L || carNumber < 0)
        {
            return null;
        }

        initPoseReflection();

        if (!poseReflectionAvailable)
        {
            return null;
        }

        try
        {
            Object clientData = methodClientDataGetInstance.invoke(null);

            if (clientData == null)
            {
                return null;
            }

            Object vehiclesObj = fieldClientDataVehicles.get(clientData);

            if (!(vehiclesObj instanceof Iterable<?> vehicles))
            {
                return null;
            }

            for (Object vehicle : vehicles)
            {
                if (vehicle == null)
                {
                    continue;
                }

                long id = (Long) methodVehicleGetId.invoke(vehicle);

                if (id != vehicleId)
                {
                    continue;
                }

                Object smoothedCarsObj = methodVehicleGetSmoothedCars.invoke(vehicle, 0L);

                if (!(smoothedCarsObj instanceof List<?> smoothedCars) || carNumber >= smoothedCars.size())
                {
                    return null;
                }

                Object transportMode = methodVehicleGetTransportMode.invoke(vehicle);
                boolean hasPitch = false;

                if (transportMode != null)
                {
                    hasPitch = fieldTransportModeHasPitchAscending.getBoolean(transportMode)
                        || fieldTransportModeHasPitchDescending.getBoolean(transportMode);
                }

                PositionAndRotation targetPose = buildCarPose(smoothedCars.get(carNumber), hasPitch);

                if (targetPose == null)
                {
                    return null;
                }

                return applyLocalRidingVisualOffset(vehicleId, smoothedCars, hasPitch, targetPose);
            }
        }
        catch (Throwable ignored)
        {}

        return null;
    }

    /**
     * Transforms local MTR car coordinates and relative angles into absolute world coordinates and angles.
     */
    public static TransformedPose transformToWorld(PositionAndRotation pose, float localX, float localY, float localZ, float localYaw, float localPitch)
    {
        if (pose == null)
        {
            return null;
        }

        Vector3d worldPos = pose.transformForwards(new Vector3d(localX, localY, localZ), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
        float worldYaw = (float) (localYaw + Math.toDegrees(pose.yaw));
        float worldPitch = (float) (localPitch + Math.toDegrees(pose.pitch));

        return new TransformedPose(worldPos.x, worldPos.y, worldPos.z, worldYaw, worldPitch);
    }

    /**
     * Dispatches an MTR vehicle matching {@code sidingId} (siding ID, vehicle ID, or route name)
     * toward {@code targetStationId}. Captures a depot snapshot for scrubbing protection.
     */
    public static long dispatchVehicle(String sidingId, long targetStationId)
    {
        if (!isMtrLoaded())
        {
            return 0L;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return 0L;
        }

        try
        {
            Object vehicle = findMatchingVehicle(sidingId, 0L);

            if (vehicle == null)
            {
                return 0L;
            }

            long vehicleId = (Long) methodVehicleGetId.invoke(vehicle);

            captureSnapshotIfAbsent(vehicleId, vehicle);

            if (methodVehicleStartUp != null)
            {
                try
                {
                    methodVehicleStartUp.invoke(vehicle, 0D);
                }
                catch (Throwable ignoredStart)
                {}
            }

            return vehicleId;
        }
        catch (Throwable ignored)
        {
            return 0L;
        }
    }

    /**
     * Trigger-based (edge-triggered) door state transition on the given MTR vehicle.
     * Avoids overwriting {@code doorValue} on every render/tick frame so MTR's native
     * door animation and motor sounds run smoothly without flickering.
     */
    public static void setTrainDoorState(long vehicleId, boolean open)
    {
        if (!isMtrLoaded() || vehicleId == 0L)
        {
            return;
        }

        Boolean lastState = LAST_DOOR_STATES.get(vehicleId);

        if (lastState != null && lastState.booleanValue() == open)
        {
            return;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return;
        }

        try
        {
            Object vehicle = findMatchingVehicle("", vehicleId);

            if (vehicle == null)
            {
                return;
            }

            Object persistentData = fieldVehiclePersistentData.get(vehicle);

            if (persistentData == null)
            {
                return;
            }

            LAST_DOOR_STATES.put(vehicleId, Boolean.valueOf(open));

            if (methodPersistentOverrideDoor != null)
            {
                methodPersistentOverrideDoor.invoke(persistentData, open ? 1 : -1);
            }

            if (fieldPersistentDoorCooldown != null)
            {
                fieldPersistentDoorCooldown.setInt(persistentData, open ? 40 : 0);
            }
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Scrubbing protection: safely recalls/resets a dispatched vehicle back to its depot/siding
     * when the timeline cursor is rewound before {@code block.startTick}.
     */
    public static void recallVehicleToDepot(String sidingId, long vehicleId)
    {
        if (!isMtrLoaded())
        {
            return;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return;
        }

        try
        {
            Object vehicle = findMatchingVehicle(sidingId, vehicleId);

            if (vehicle == null)
            {
                return;
            }

            long resolvedId = (Long) methodVehicleGetId.invoke(vehicle);

            /* Hand the vehicle back to MTR: its own simulation and the next server update take over */
            LAST_DOOR_STATES.remove(resolvedId);
            INITIAL_VEHICLE_SNAPSHOTS.remove(resolvedId);
            MtrGovernor.release(resolvedId);
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Finds the live vehicle that stands in for a recorded one and pins it to the given state
     * through {@link MtrGovernor}.
     *
     * @return the live vehicle ID, or 0 when no matching vehicle is loaded on the client
     */
    public static long governVehicle(String ownerId, long recordedVehicleId, long sidingId, double railProgress, double speed, double doorValue, int doorMultiplier, boolean reversed)
    {
        if (!isMtrLoaded() || Double.isNaN(railProgress))
        {
            return 0L;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return 0L;
        }

        try
        {
            Object vehicle = findGovernableVehicle(recordedVehicleId, sidingId, railProgress);

            if (vehicle == null)
            {
                return 0L;
            }

            long liveId = (Long) methodVehicleGetId.invoke(vehicle);

            MtrGovernor.govern(ownerId, recordedVehicleId, liveId, vehicle, railProgress, speed, doorValue, doorMultiplier, reversed ? 1 : 0);

            return liveId;
        }
        catch (Throwable t)
        {
            logOnce("governVehicle", t);

            return 0L;
        }
    }

    /**
     * Strict lookup: the vehicle already standing in for the recorded one, the recorded ID itself,
     * or the vehicle of the same siding that is closest to the wanted rail progress. Never an
     * arbitrary vehicle.
     */
    private static Object findGovernableVehicle(long recordedVehicleId, long sidingId, double railProgress) throws Exception
    {
        Object clientData = methodClientDataGetInstance.invoke(null);
        Object vehiclesObj = clientData == null ? null : fieldClientDataVehicles.get(clientData);

        if (!(vehiclesObj instanceof Iterable<?> vehicles))
        {
            return null;
        }

        long aliasId = MtrGovernor.resolveLiveId(recordedVehicleId);
        Object exact = null;
        Object sameSiding = null;
        double bestDistance = Double.MAX_VALUE;

        for (Object vehicle : vehicles)
        {
            if (vehicle == null)
            {
                continue;
            }

            long id = (Long) methodVehicleGetId.invoke(vehicle);

            if (id == aliasId && aliasId != 0L)
            {
                return vehicle;
            }

            if (id == recordedVehicleId && recordedVehicleId != 0L)
            {
                exact = vehicle;
            }
            else if (sidingId != 0L && !MtrGovernor.isGoverned(id))
            {
                Object extra = fieldVehicleExtraData.get(vehicle);

                if (extra != null && ((Long) methodExtraGetSidingId.invoke(extra)) == sidingId)
                {
                    double distance = fieldVehicleRailProgress == null ? 0D : Math.abs(fieldVehicleRailProgress.getDouble(vehicle) - railProgress);

                    if (distance < bestDistance)
                    {
                        bestDistance = distance;
                        sameSiding = vehicle;
                    }
                }
            }
        }

        return exact != null ? exact : sameSiding;
    }

    public static boolean isVehicle(Object object)
    {
        initPoseReflection();

        return classVehicleExtension != null && classVehicleExtension.isInstance(object);
    }

    public static long readVehicleId(Object vehicle)
    {
        initPoseReflection();

        if (!poseReflectionAvailable || vehicle == null)
        {
            return 0L;
        }

        try
        {
            return (Long) methodVehicleGetId.invoke(vehicle);
        }
        catch (Throwable ignored)
        {
            return 0L;
        }
    }

    /**
     * The only place that writes a governed vehicle. Called by {@link MtrGovernor} both when a
     * target is set and right after MTR's own simulation step.
     *
     * @param fromSimulation true when called after MTR's simulation step; only then doors are
     *                       written, so MTR's old/new door value edge (door sounds) stays intact
     */
    static void writeGovernedState(Object vehicle, MtrGovernor.Target target, boolean fromSimulation)
    {
        initPoseReflection();
        initSimReflection();

        if (!simReflectionAvailable || fieldVehicleRailProgress == null)
        {
            return;
        }

        try
        {
            fieldVehicleRailProgress.setDouble(vehicle, target.railProgress);

            if (fieldVehicleSpeed != null)
            {
                fieldVehicleSpeed.setDouble(vehicle, target.speed);
            }

            if (fieldVehicleReversed != null && target.reversed >= 0)
            {
                fieldVehicleReversed.setBoolean(vehicle, target.reversed == 1);
            }

            Object persistentData = fieldVehiclePersistentData.get(vehicle);

            if (persistentData == null)
            {
                return;
            }

            /* No catch-up animation towards whatever the server last said */
            if (fieldPersistentSmoothedRailProgress != null)
            {
                fieldPersistentSmoothedRailProgress.setDouble(persistentData, target.railProgress);
            }

            if (fieldPersistentSmoothingAdjustment != null)
            {
                fieldPersistentSmoothingAdjustment.setDouble(persistentData, 0D);
            }

            if (fromSimulation && !Double.isNaN(target.doorValue) && fieldPersistentDoorValue != null)
            {
                fieldPersistentDoorValue.setDouble(persistentData, target.doorValue);

                if (target.doorValue > 0D && fieldPersistentDoorCooldown != null && fieldPersistentDoorCooldown.getInt(persistentData) < 2)
                {
                    fieldPersistentDoorCooldown.setInt(persistentData, 2);
                }

                if (target.doorMultiplier != 0 && methodPersistentOverrideDoor != null)
                {
                    methodPersistentOverrideDoor.invoke(persistentData, target.doorMultiplier);
                }
            }
        }
        catch (Throwable t)
        {
            logOnce("writeGovernedState", t);
        }
    }

    /**
     * After a server update a governed vehicle may exist twice: the instance BBS refused to drop
     * and the fresh one the server sent. Keeps the newest instance of each and pins it again.
     */
    @SuppressWarnings("unchecked")
    static void dedupeAndEnforceGoverned(Map<Long, MtrGovernor.Target> targets)
    {
        initPoseReflection();

        if (!poseReflectionAvailable)
        {
            return;
        }

        try
        {
            Object clientData = methodClientDataGetInstance.invoke(null);
            Object vehiclesObj = clientData == null ? null : fieldClientDataVehicles.get(clientData);

            if (!(vehiclesObj instanceof Iterable<?>))
            {
                return;
            }

            Iterable<Object> vehicles = (Iterable<Object>) vehiclesObj;
            Map<Long, Integer> counts = new HashMap<>();

            for (Object vehicle : vehicles)
            {
                long id = readVehicleId(vehicle);

                if (targets.containsKey(id))
                {
                    counts.merge(id, 1, Integer::sum);
                }
            }

            Iterator<Object> it = vehicles.iterator();

            while (it.hasNext())
            {
                Object vehicle = it.next();
                long id = readVehicleId(vehicle);
                MtrGovernor.Target target = targets.get(id);

                if (target == null)
                {
                    continue;
                }

                int remaining = counts.getOrDefault(id, 1);

                if (remaining > 1)
                {
                    /* Older duplicate; it shares its persistent data with the fresh one, so no dispose */
                    it.remove();
                    counts.put(id, remaining - 1);
                }
                else
                {
                    writeGovernedState(vehicle, target, false);
                }
            }
        }
        catch (Throwable t)
        {
            logOnce("dedupeAndEnforceGoverned", t);
        }
    }

    /**
     * Fills the identity of {@code track} (who the vehicle is, which path it runs on, and a
     * serialized snapshot of it) from the live vehicle at record time.
     */
    public static void captureVehicleIdentity(MtrVehicleTrack track, long vehicleId)
    {
        if (!isMtrLoaded() || track == null || vehicleId == 0L)
        {
            return;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return;
        }

        try
        {
            Object vehicle = findGovernableVehicle(vehicleId, 0L, 0D);

            if (vehicle == null)
            {
                return;
            }

            Object extra = fieldVehicleExtraData.get(vehicle);
            Object transportMode = methodVehicleGetTransportMode.invoke(vehicle);

            track.vehicleId = vehicleId;
            track.transportMode = transportMode == null ? "TRAIN" : transportMode.toString();

            if (extra == null)
            {
                return;
            }

            track.sidingId = (Long) methodExtraGetSidingId.invoke(extra);
            track.vehicleLength = (Double) methodExtraGetTotalVehicleLength.invoke(extra);

            Object routeName = methodExtraGetThisRouteName.invoke(extra);

            track.routeName = routeName instanceof String ? (String) routeName : "";

            if (methodExtraGetDepotId != null)
            {
                track.depotId = (Long) methodExtraGetDepotId.invoke(extra);
            }

            if (methodExtraGetThisRouteId != null)
            {
                track.routeId = (Long) methodExtraGetThisRouteId.invoke(extra);
            }

            if (fieldExtraImmutablePath.get(extra) instanceof List<?> path && !path.isEmpty())
            {
                track.pathSize = path.size();
                track.pathLength = (Double) methodPathGetEndDistance.invoke(path.get(path.size() - 1));
            }

            if (methodUtilitiesGetJson != null)
            {
                String json = "{\"vehicle\":" + methodUtilitiesGetJson.invoke(null, vehicle) + ",\"data\":" + methodUtilitiesGetJson.invoke(null, extra) + "}";
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
                {
                    gzip.write(json.getBytes(StandardCharsets.UTF_8));
                }

                /* A vehicle with an enormous path is not worth bloating the film file for */
                if (bytes.size() <= MAX_SNAPSHOT_BYTES)
                {
                    track.snapshot = Base64.getEncoder().encodeToString(bytes.toByteArray());
                }
            }
        }
        catch (Throwable t)
        {
            logOnce("captureVehicleIdentity", t);
        }
    }

    private static void logOnce(String where, Throwable t)
    {
        if (LOGGED_FAILURES.add(where))
        {
            LOGGER.error("[BBS/MTR] {} failed; MTR integration may be out of sync with the installed MTR version", where, t);
        }
    }

    /**
     * Populates {@code frame.mtrRailProgress} and {@code frame.mtrSpeed} from the live MTR vehicle during recording.
     */
    public static void populateRecordedVehicleProgress(mchorse.bbs_mod.film.replays.RecordFrame frame)
    {
        if (!isMtrLoaded() || frame == null || frame.mtrVehicleId == 0L)
        {
            return;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return;
        }

        try
        {
            Object vehicle = findMatchingVehicle("", frame.mtrVehicleId);

            if (vehicle == null)
            {
                return;
            }

            if (fieldVehicleRailProgress != null)
            {
                frame.mtrRailProgress = fieldVehicleRailProgress.getDouble(vehicle);
            }

            if (fieldVehicleSpeed != null)
            {
                frame.mtrSpeed = fieldVehicleSpeed.getDouble(vehicle);
            }
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Synchronizes a dispatched vehicle's rail progress and station stop dwell phase along the timeline block.
     * Uses recorded {@code initialRailProgress}, {@code initialSpeed}, and {@code stationStopRailProgress}
     * when present so playback is locked to scene ticks rather than live world time.
     */
    public static void syncVehicleProgress(
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
    )
    {
        if (!isMtrLoaded())
        {
            return;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return;
        }

        try
        {
            Object vehicle = findMatchingVehicle(sidingId, vehicleId);

            if (vehicle == null)
            {
                return;
            }

            long resolvedId = (Long) methodVehicleGetId.invoke(vehicle);
            VehicleSnapshot snapshot = captureSnapshotIfAbsent(resolvedId, vehicle);

            if (snapshot == null || fieldVehicleRailProgress == null)
            {
                return;
            }

            boolean hasRecordedStart = !Double.isNaN(initialRailProgress);
            double startProgress = hasRecordedStart ? initialRailProgress : snapshot.railProgress;
            double stationStopProgress;

            if (!Double.isNaN(stationStopRailProgress) && stationStopRailProgress >= startProgress)
            {
                stationStopProgress = stationStopRailProgress;
            }
            else if (hasRecordedStart && initialSpeed > 0.0001D && openDoorsTick > dispatchTick)
            {
                /* MTR speed is in meters per millisecond; 1 scene tick = 50 ms */
                stationStopProgress = startProgress + initialSpeed * (double) (openDoorsTick - dispatchTick) * 50D;
            }
            else
            {
                stationStopProgress = resolveStationStopProgress(vehicle, targetStationId, startProgress);
            }

            double departEndProgress = stationStopProgress + Math.max(40D, (stationStopProgress - startProgress) * 0.75D);

            double targetProgress;
            double targetSpeed;

            if (localTick <= dispatchTick)
            {
                targetProgress = startProgress;
                targetSpeed = hasRecordedStart ? initialSpeed : 0D;
            }
            else if (localTick < openDoorsTick)
            {
                float span = Math.max(1F, (float) (openDoorsTick - dispatchTick));
                float t = MathHelper.clamp((localTick - dispatchTick) / span, 0F, 1F);
                float smooth = hasRecordedStart && initialSpeed > 0.0001D ? t : t * t * (3F - 2F * t);

                targetProgress = startProgress + (stationStopProgress - startProgress) * (double) smooth;
                targetSpeed = (stationStopProgress - startProgress) / (double) (span * 50F);
            }
            else if (localTick <= closeDoorsTick)
            {
                targetProgress = stationStopProgress;
                targetSpeed = 0D;
            }
            else
            {
                float remainSpan = Math.max(20F, (float) Math.max(duration - closeDoorsTick, 40));
                float t = Math.max(0F, (localTick - closeDoorsTick) / remainSpan);
                float accel = t < 1F ? t * t : 1F + (t - 1F) * 2F;

                targetProgress = stationStopProgress + (departEndProgress - stationStopProgress) * (double) accel;
                targetSpeed = (departEndProgress - stationStopProgress) / (double) (remainSpan * 50F);
            }

            /* Hand-made blocks (no recorded trace): the synthetic curve goes through the governor too,
             * doors stay with MTR's own edge-triggered override */
            MtrGovernor.govern("legacy:" + resolvedId, 0L, resolvedId, vehicle, targetProgress, targetSpeed, Double.NaN, 0, -1);
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Returns the siding ID or vehicle ID string of the vehicle the player is currently riding
     * or the nearest MTR vehicle in the client world.
     */
    public static String detectNearestVehicleOrSidingId()
    {
        if (!isMtrLoaded())
        {
            return "";
        }

        long ridingId = readRidingVehicleId();

        if (ridingId != 0L)
        {
            return Long.toString(ridingId);
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable)
        {
            return "";
        }

        try
        {
            Object clientData = methodClientDataGetInstance.invoke(null);

            if (clientData == null)
            {
                return "";
            }

            Object vehiclesObj = fieldClientDataVehicles.get(clientData);

            if (!(vehiclesObj instanceof Iterable<?> vehicles))
            {
                return "";
            }

            for (Object vehicle : vehicles)
            {
                if (vehicle == null)
                {
                    continue;
                }

                if (fieldVehicleExtraData != null && methodExtraGetSidingId != null)
                {
                    Object extra = fieldVehicleExtraData.get(vehicle);

                    if (extra != null)
                    {
                        long siding = (Long) methodExtraGetSidingId.invoke(extra);

                        if (siding != 0L)
                        {
                            return Long.toString(siding);
                        }
                    }
                }

                long id = (Long) methodVehicleGetId.invoke(vehicle);

                if (id != 0L)
                {
                    return Long.toString(id);
                }
            }
        }
        catch (Throwable ignored)
        {}

        return "";
    }

    /**
     * Samples the live state of the MTR vehicle the local player is currently riding,
     * or the nearest active MTR vehicle within range during recording.
     */
    public static VehicleLiveState sampleTrackedVehicleState(long preferredVehicleId)
    {
        if (!isMtrLoaded())
        {
            return null;
        }

        initPoseReflection();
        initSimReflection();

        if (!poseReflectionAvailable || !simReflectionAvailable)
        {
            return null;
        }

        try
        {
            Object clientData = methodClientDataGetInstance.invoke(null);

            if (clientData == null)
            {
                return null;
            }

            Object vehiclesObj = fieldClientDataVehicles.get(clientData);

            if (!(vehiclesObj instanceof Iterable<?> vehicles))
            {
                return null;
            }

            long ridingId = readRidingVehicleId();
            Object targetVehicle = null;
            boolean playerRiding = false;

            if (ridingId != 0L)
            {
                for (Object vehicle : vehicles)
                {
                    if (vehicle != null && ((Long) methodVehicleGetId.invoke(vehicle)) == ridingId)
                    {
                        targetVehicle = vehicle;
                        playerRiding = true;
                        break;
                    }
                }
            }

            if (targetVehicle == null && preferredVehicleId != 0L)
            {
                for (Object vehicle : vehicles)
                {
                    if (vehicle != null && ((Long) methodVehicleGetId.invoke(vehicle)) == preferredVehicleId)
                    {
                        targetVehicle = vehicle;
                        break;
                    }
                }
            }

            if (targetVehicle == null)
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                ClientPlayerEntity player = mc != null ? mc.player : null;
                double bestDistSq = 96D * 96D;

                for (Object vehicle : vehicles)
                {
                    if (vehicle == null)
                    {
                        continue;
                    }

                    if (player != null && methodVehicleGetHeadPosition != null)
                    {
                        Object headVec = methodVehicleGetHeadPosition.invoke(vehicle);

                        if (headVec != null)
                        {
                            double vx = fieldVectorX.getDouble(headVec);
                            double vy = fieldVectorY.getDouble(headVec);
                            double vz = fieldVectorZ.getDouble(headVec);
                            double dx = vx - player.getX();
                            double dy = vy - player.getY();
                            double dz = vz - player.getZ();
                            double distSq = dx * dx + dy * dy + dz * dz;

                            if (distSq < bestDistSq)
                            {
                                bestDistSq = distSq;
                                targetVehicle = vehicle;
                            }
                        }
                    }
                    else if (targetVehicle == null)
                    {
                        targetVehicle = vehicle;
                    }
                }
            }

            if (targetVehicle == null)
            {
                return null;
            }

            long vehicleId = (Long) methodVehicleGetId.invoke(targetVehicle);
            long sidingId = 0L;
            String routeName = "";
            long stationId = 0L;
            int doorMultiplier = 0;

            double railProgress = fieldVehicleRailProgress != null ? fieldVehicleRailProgress.getDouble(targetVehicle) : 0D;
            double speed = fieldVehicleSpeed != null ? fieldVehicleSpeed.getDouble(targetVehicle) : 0D;

            Object extra = fieldVehicleExtraData != null ? fieldVehicleExtraData.get(targetVehicle) : null;

            if (extra != null)
            {
                if (methodExtraGetSidingId != null)
                {
                    sidingId = (Long) methodExtraGetSidingId.invoke(extra);
                }

                if (methodExtraGetThisRouteName != null)
                {
                    Object rn = methodExtraGetThisRouteName.invoke(extra);

                    if (rn instanceof String s)
                    {
                        routeName = s;
                    }
                }

                if (methodExtraGetDoorMultiplier != null)
                {
                    doorMultiplier = (Integer) methodExtraGetDoorMultiplier.invoke(extra);
                }

                if (fieldExtraImmutablePath != null)
                {
                    Object pathObj = fieldExtraImmutablePath.get(extra);

                    if (pathObj instanceof List<?> pathList)
                    {
                        for (int i = 0; i < pathList.size(); i++)
                        {
                            Object pathData = pathList.get(i);

                            if (pathData == null)
                            {
                                continue;
                            }

                            long dwell = methodPathGetDwellTime != null ? (Long) methodPathGetDwellTime.invoke(pathData) : 0L;
                            double endDist = methodPathGetEndDistance != null ? (Double) methodPathGetEndDistance.invoke(pathData) : 0D;

                            if (dwell > 0L && endDist >= railProgress - 5D)
                            {
                                stationId = methodPathGetSavedRailBaseId != null ? (Long) methodPathGetSavedRailBaseId.invoke(pathData) : 0L;
                                break;
                            }
                        }
                    }
                }
            }

            double doorValue = 0D;
            double oldDoorValue = 0D;
            double nextAnnounceProgress = 0D;

            Object persistentData = fieldVehiclePersistentData != null ? fieldVehiclePersistentData.get(targetVehicle) : null;

            if (persistentData != null)
            {
                if (fieldPersistentDoorValue != null)
                {
                    doorValue = fieldPersistentDoorValue.getDouble(persistentData);
                }

                if (fieldPersistentOldDoorValue != null)
                {
                    oldDoorValue = fieldPersistentOldDoorValue.getDouble(persistentData);
                }

                if (methodPersistentGetAdjustedDoorMultiplier != null && extra != null)
                {
                    doorMultiplier = (Integer) methodPersistentGetAdjustedDoorMultiplier.invoke(persistentData, extra);
                }

                if (fieldPersistentNextAnnouncementRailProgress != null)
                {
                    nextAnnounceProgress = fieldPersistentNextAnnouncementRailProgress.getDouble(persistentData);
                }
            }

            Object transportModeObj = methodVehicleGetTransportMode != null ? methodVehicleGetTransportMode.invoke(targetVehicle) : null;
            String transportMode = transportModeObj != null ? transportModeObj.toString() : "TRAIN";

            return new VehicleLiveState(
                vehicleId,
                sidingId,
                routeName,
                stationId,
                transportMode,
                railProgress,
                speed,
                doorValue,
                oldDoorValue,
                doorMultiplier,
                nextAnnounceProgress,
                playerRiding,
                fieldVehicleReversed != null && fieldVehicleReversed.getBoolean(targetVehicle)
            );
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static Object findMatchingVehicle(String sidingId, long preferredVehicleId) throws Exception
    {
        Object clientData = methodClientDataGetInstance.invoke(null);

        if (clientData == null)
        {
            return null;
        }

        Object vehiclesObj = fieldClientDataVehicles.get(clientData);

        if (!(vehiclesObj instanceof Iterable<?> vehicles))
        {
            return null;
        }

        if (preferredVehicleId != 0L)
        {
            for (Object vehicle : vehicles)
            {
                if (vehicle != null && ((Long) methodVehicleGetId.invoke(vehicle)) == preferredVehicleId)
                {
                    return vehicle;
                }
            }
        }

        long parsedId = 0L;
        boolean hasParsedId = false;

        if (sidingId != null && !sidingId.trim().isEmpty())
        {
            try
            {
                parsedId = Long.parseLong(sidingId.trim());
                hasParsedId = true;
            }
            catch (NumberFormatException ignored)
            {}
        }

        Object fallbackVehicle = null;

        for (Object vehicle : vehicles)
        {
            if (vehicle == null)
            {
                continue;
            }

            if (fallbackVehicle == null)
            {
                fallbackVehicle = vehicle;
            }

            long id = (Long) methodVehicleGetId.invoke(vehicle);

            if (hasParsedId && id == parsedId)
            {
                return vehicle;
            }

            if (fieldVehicleExtraData != null)
            {
                Object extra = fieldVehicleExtraData.get(vehicle);

                if (extra != null)
                {
                    if (hasParsedId && methodExtraGetSidingId != null)
                    {
                        long vehicleSidingId = (Long) methodExtraGetSidingId.invoke(extra);

                        if (vehicleSidingId == parsedId)
                        {
                            return vehicle;
                        }
                    }

                    if (sidingId != null && !sidingId.trim().isEmpty() && methodExtraGetThisRouteName != null)
                    {
                        Object routeNameObj = methodExtraGetThisRouteName.invoke(extra);

                        if (routeNameObj instanceof String routeName && routeName.toLowerCase().contains(sidingId.trim().toLowerCase()))
                        {
                            return vehicle;
                        }
                    }
                }
            }
        }

        long ridingId = readRidingVehicleId();

        if (ridingId != 0L)
        {
            for (Object vehicle : vehicles)
            {
                if (vehicle != null && ((Long) methodVehicleGetId.invoke(vehicle)) == ridingId)
                {
                    return vehicle;
                }
            }
        }

        return fallbackVehicle;
    }

    private static VehicleSnapshot captureSnapshotIfAbsent(long vehicleId, Object vehicle)
    {
        VehicleSnapshot existing = INITIAL_VEHICLE_SNAPSHOTS.get(vehicleId);

        if (existing != null)
        {
            return existing;
        }

        try
        {
            double progress = fieldVehicleRailProgress != null ? fieldVehicleRailProgress.getDouble(vehicle) : 0D;
            double speed = fieldVehicleSpeed != null ? fieldVehicleSpeed.getDouble(vehicle) : 0D;
            VehicleSnapshot snapshot = new VehicleSnapshot(progress, speed);

            INITIAL_VEHICLE_SNAPSHOTS.put(vehicleId, snapshot);

            return snapshot;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static double resolveStationStopProgress(Object vehicle, long targetStationId, double startProgress)
    {
        try
        {
            if (fieldVehicleExtraData != null && fieldExtraImmutablePath != null)
            {
                Object extra = fieldVehicleExtraData.get(vehicle);

                if (extra != null)
                {
                    Object pathObj = fieldExtraImmutablePath.get(extra);

                    if (pathObj instanceof List<?> pathList && !pathList.isEmpty())
                    {
                        for (int i = 0; i < pathList.size(); i++)
                        {
                            Object pathData = pathList.get(i);

                            if (pathData == null)
                            {
                                continue;
                            }

                            double endDist = methodPathGetEndDistance != null ? (Double) methodPathGetEndDistance.invoke(pathData) : 0D;
                            long savedRailId = methodPathGetSavedRailBaseId != null ? (Long) methodPathGetSavedRailBaseId.invoke(pathData) : 0L;
                            long dwellTime = methodPathGetDwellTime != null ? (Long) methodPathGetDwellTime.invoke(pathData) : 0L;

                            if (targetStationId != 0L && savedRailId == targetStationId && endDist > startProgress)
                            {
                                return endDist;
                            }

                            if (targetStationId == 0L && dwellTime > 0L && endDist > startProgress + 5D)
                            {
                                return endDist;
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable ignored)
        {}

        return startProgress + 60D;
    }

    private static void initSimReflection()
    {
        if (simReflectionInitialized)
        {
            return;
        }

        simReflectionInitialized = true;

        try
        {
            Class<?> vehicleClass = Class.forName("org.mtr.core.data.Vehicle");
            Class<?> vehicleExtClass = Class.forName("org.mtr.mod.data.VehicleExtension");
            Class<?> extraDataClass = Class.forName("org.mtr.core.data.VehicleExtraData");
            Class<?> pathDataClass = Class.forName("org.mtr.core.data.PathData");
            Class<?> persistentDataClass = Class.forName("org.mtr.mod.data.PersistentVehicleData");

            fieldVehicleExtraData = vehicleClass.getField("vehicleExtraData");
            fieldVehiclePersistentData = vehicleExtClass.getField("persistentVehicleData");
            fieldVehicleRailProgress = getAccessibleField(vehicleClass, "railProgress");
            fieldVehicleSpeed = getAccessibleField(vehicleClass, "speed");
            fieldVehicleReversed = getAccessibleField(vehicleClass, "reversed");

            try
            {
                methodExtraGetDepotId = extraDataClass.getMethod("getDepotId");
                methodExtraGetThisRouteId = extraDataClass.getMethod("getThisRouteId");
                methodUtilitiesGetJson = Class.forName("org.mtr.core.tool.Utilities").getMethod("getJsonObjectFromData", Class.forName("org.mtr.core.serializer.SerializedDataBase"));
            }
            catch (Throwable ignoredIdentity)
            {}

            try
            {
                methodVehicleStartUp = vehicleClass.getMethod("startUp", double.class);
            }
            catch (Throwable ignoredMethod)
            {}

            try
            {
                methodVehicleGetHeadPosition = vehicleClass.getMethod("getHeadPosition");
            }
            catch (Throwable ignoredHead)
            {}

            methodExtraGetSidingId = extraDataClass.getMethod("getSidingId");
            methodExtraGetThisRouteName = extraDataClass.getMethod("getThisRouteName");
            fieldExtraImmutablePath = extraDataClass.getField("immutablePath");
            methodExtraGetTotalVehicleLength = extraDataClass.getMethod("getTotalVehicleLength");

            try
            {
                methodExtraGetDoorMultiplier = extraDataClass.getMethod("getDoorMultiplier");
            }
            catch (Throwable ignoredDoorMult)
            {}

            methodPathGetSavedRailBaseId = pathDataClass.getMethod("getSavedRailBaseId");
            methodPathGetStartDistance = pathDataClass.getMethod("getStartDistance");
            methodPathGetEndDistance = pathDataClass.getMethod("getEndDistance");
            methodPathGetDwellTime = pathDataClass.getMethod("getDwellTime");

            methodPersistentOverrideDoor = persistentDataClass.getMethod("overrideDoorMultiplier", int.class);

            try
            {
                methodPersistentGetAdjustedDoorMultiplier = persistentDataClass.getMethod("getAdjustedDoorMultiplier", extraDataClass);
            }
            catch (Throwable ignoredAdjDoor)
            {}

            fieldPersistentDoorValue = getAccessibleField(persistentDataClass, "doorValue");
            fieldPersistentOldDoorValue = getAccessibleField(persistentDataClass, "oldDoorValue");
            fieldPersistentDoorCooldown = getAccessibleField(persistentDataClass, "doorCooldown");
            fieldPersistentNextAnnouncementRailProgress = getAccessibleField(persistentDataClass, "nextAnnouncementRailProgress");
            fieldPersistentSmoothedRailProgress = getAccessibleField(persistentDataClass, "smoothedRailProgress");
            fieldPersistentSmoothingAdjustment = getAccessibleField(persistentDataClass, "railProgressSmoothingAdjustment");

            simReflectionAvailable = true;
        }
        catch (Throwable ignored)
        {
            simReflectionAvailable = false;
        }
    }

    private static final class VehicleSnapshot
    {
        public final double railProgress;
        public final double speed;

        public VehicleSnapshot(double railProgress, double speed)
        {
            this.railProgress = railProgress;
            this.speed = speed;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PositionAndRotation buildCarPose(Object carAndPositionPair, boolean hasPitch) throws Exception
    {
        if (carAndPositionPair == null)
        {
            return null;
        }

        Object vehicleCar = methodPairLeft.invoke(carAndPositionPair);
        Object bogiesObj = methodPairRight.invoke(carAndPositionPair);

        if (vehicleCar == null || !(bogiesObj instanceof List<?> bogiesList))
        {
            return null;
        }

        List bogiePositions = (List) ctorObjectArrayList.newInstance();

        for (int i = 0; i < bogiesList.size(); i++)
        {
            Object bogiePair = bogiesList.get(i);
            Object pos1 = methodPairLeft.invoke(bogiePair);
            Object pos2 = methodPairRight.invoke(bogiePair);
            Object bogiePosAndRot = ctorBogiePositionAndRotation.newInstance(pos1, pos2, Boolean.TRUE);

            bogiePositions.add(bogiePosAndRot);
        }

        Object mtrPosAndRot = ctorCarPositionAndRotation.newInstance(bogiePositions, vehicleCar, Boolean.valueOf(hasPitch));
        Object vec = fieldPosAndRotPosition.get(mtrPosAndRot);
        double x = fieldVectorX.getDouble(vec);
        double y = fieldVectorY.getDouble(vec);
        double z = fieldVectorZ.getDouble(vec);
        double yaw = fieldPosAndRotYaw.getDouble(mtrPosAndRot);
        double pitch = fieldPosAndRotPitch.getDouble(mtrPosAndRot);

        return new PositionAndRotation(x, y, z, yaw, pitch);
    }

    /**
     * When the local client player is actively riding the same MTR vehicle, MTR's {@code RenderVehicles}
     * renders the vehicle relative to {@code clientPlayerEntity.getPos()} rather than raw {@code smoothedRailProgress}
     * to eliminate camera shake. Applying the identical delta keeps replay actors locked to the rendered car mesh.
     */
    private static PositionAndRotation applyLocalRidingVisualOffset(long vehicleId, List<?> smoothedCars, boolean hasPitch, PositionAndRotation renderingPose)
    {
        if (methodGetRidingCarAndOffset == null)
        {
            return renderingPose;
        }

        try
        {
            Object ridingPair = methodGetRidingCarAndOffset.invoke(null, vehicleId);

            if (ridingPair == null)
            {
                return renderingPose;
            }

            int ridingCarNumber = (Integer) methodIntObjectPairLeftInt.invoke(ridingPair);

            if (ridingCarNumber < 0 || ridingCarNumber >= smoothedCars.size())
            {
                return renderingPose;
            }

            Object offsetPair = methodIntObjectPairRight.invoke(ridingPair);

            if (offsetPair == null)
            {
                return renderingPose;
            }

            Object offsetVectorObj = methodPairLeft.invoke(offsetPair);
            Object offsetRotationObj = methodPairRight.invoke(offsetPair);

            if (offsetVectorObj == null)
            {
                return renderingPose;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;

            if (player == null)
            {
                return renderingPose;
            }

            PositionAndRotation ridingCarPose = buildCarPose(smoothedCars.get(ridingCarNumber), hasPitch);

            if (ridingCarPose == null)
            {
                return renderingPose;
            }

            double ox = (Double) methodHolderVec3dGetX.invoke(offsetVectorObj);
            double oy = (Double) methodHolderVec3dGetY.invoke(offsetVectorObj);
            double oz = (Double) methodHolderVec3dGetZ.invoke(offsetVectorObj);

            Vec3d playerPos = player.getPos();
            Vector3d negOffset = new Vector3d(-ox, -oy, -oz)
                .rotateX((float) ridingCarPose.pitch)
                .rotateY((float) ridingCarPose.yaw);

            if (offsetRotationObj == null)
            {
                double wx = negOffset.x + playerPos.x + (renderingPose.x - ridingCarPose.x);
                double wy = negOffset.y + playerPos.y + (renderingPose.y - ridingCarPose.y);
                double wz = negOffset.z + playerPos.z + (renderingPose.z - ridingCarPose.z);

                return new PositionAndRotation(wx, wy, wz, renderingPose.yaw, renderingPose.pitch);
            }
            else
            {
                double offsetRotation = (Double) offsetRotationObj;
                Camera camera = mc.gameRenderer.getCamera();

                if (Math.abs(MathHelper.wrapDegrees(Math.round(player.getYaw()) - Math.round(camera.getYaw()))) > 90)
                {
                    offsetRotation += Math.PI;
                }

                double ridingRotation = offsetRotation - ridingCarPose.yaw - Math.toRadians(camera.getYaw());
                Vector3d rotated = negOffset
                    .add(
                        renderingPose.x - ridingCarPose.x,
                        renderingPose.y - ridingCarPose.y,
                        renderingPose.z - ridingCarPose.z
                    )
                    .rotateY((float) ridingRotation)
                    .add(playerPos.x, playerPos.y, playerPos.z);

                return new PositionAndRotation(rotated.x, rotated.y, rotated.z, renderingPose.yaw + ridingRotation, renderingPose.pitch);
            }
        }
        catch (Throwable ignored)
        {
            return renderingPose;
        }
    }

    private static void initPoseReflection()
    {
        if (poseReflectionInitialized)
        {
            return;
        }

        poseReflectionInitialized = true;

        try
        {
            Class<?> clientDataClass = Class.forName("org.mtr.mod.client.MinecraftClientData");
            Class<?> vehicleExtClass = Class.forName("org.mtr.mod.data.VehicleExtension");
            Class<?> transportModeClass = Class.forName("org.mtr.core.data.TransportMode");
            Class<?> pairClass = Class.forName("org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair");
            Class<?> objectArrayListClass = Class.forName("org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList");
            Class<?> vectorClass = Class.forName("org.mtr.core.tool.Vector");
            Class<?> vehicleCarClass = Class.forName("org.mtr.core.data.VehicleCar");
            Class<?> posAndRotClass = Class.forName("org.mtr.mod.render.PositionAndRotation");

            classVehicleExtension = vehicleExtClass;
            methodClientDataGetInstance = clientDataClass.getMethod("getInstance");
            fieldClientDataVehicles = clientDataClass.getField("vehicles");

            methodVehicleGetId = vehicleExtClass.getMethod("getId");
            methodVehicleGetTransportMode = vehicleExtClass.getMethod("getTransportMode");
            methodVehicleGetSmoothedCars = vehicleExtClass.getMethod("getSmoothedVehicleCarsAndPositions", long.class);

            fieldTransportModeHasPitchAscending = transportModeClass.getField("hasPitchAscending");
            fieldTransportModeHasPitchDescending = transportModeClass.getField("hasPitchDescending");

            methodPairLeft = pairClass.getMethod("left");
            methodPairRight = pairClass.getMethod("right");

            ctorObjectArrayList = objectArrayListClass.getConstructor();
            ctorBogiePositionAndRotation = posAndRotClass.getConstructor(vectorClass, vectorClass, boolean.class);
            ctorCarPositionAndRotation = posAndRotClass.getConstructor(objectArrayListClass, vehicleCarClass, boolean.class);

            fieldPosAndRotPosition = posAndRotClass.getField("position");
            fieldPosAndRotYaw = posAndRotClass.getField("yaw");
            fieldPosAndRotPitch = posAndRotClass.getField("pitch");

            fieldVectorX = vectorClass.getField("x");
            fieldVectorY = vectorClass.getField("y");
            fieldVectorZ = vectorClass.getField("z");

            try
            {
                Class<?> ridingMovementClass = Class.forName("org.mtr.mod.client.VehicleRidingMovement");
                Class<?> intObjectPairClass = Class.forName("org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntObjectImmutablePair");
                Class<?> holderVec3dClass = Class.forName("org.mtr.mapping.holder.Vector3d");

                methodGetRidingCarAndOffset = ridingMovementClass.getMethod("getRidingVehicleCarNumberAndOffset", long.class);
                methodIntObjectPairLeftInt = intObjectPairClass.getMethod("leftInt");
                methodIntObjectPairRight = intObjectPairClass.getMethod("right");
                methodHolderVec3dGetX = holderVec3dClass.getMethod("getXMapped");
                methodHolderVec3dGetY = holderVec3dClass.getMethod("getYMapped");
                methodHolderVec3dGetZ = holderVec3dClass.getMethod("getZMapped");
            }
            catch (Throwable ignoredOptional)
            {}

            poseReflectionAvailable = true;
        }
        catch (Throwable ignored)
        {
            poseReflectionAvailable = false;
        }
    }

    public static long readRidingVehicleId()
    {
        if (!isMtrLoaded())
        {
            return 0L;
        }

        try
        {
            return mchorse.bbs_mod.mixin.client.mtr.VehicleRidingMovementAccessor.getRidingVehicleId();
        }
        catch (Throwable ignored)
        {
            initReflection();

            if (fieldRidingVehicleId != null)
            {
                try
                {
                    return fieldRidingVehicleId.getLong(null);
                }
                catch (Throwable ignoredReflect)
                {}
            }
        }

        return 0L;
    }

    public static int readRidingVehicleCarNumber()
    {
        if (!isMtrLoaded())
        {
            return 0;
        }

        try
        {
            return mchorse.bbs_mod.mixin.client.mtr.VehicleRidingMovementAccessor.getRidingVehicleCarNumber();
        }
        catch (Throwable ignored)
        {
            initReflection();

            if (fieldRidingVehicleCarNumber != null)
            {
                try
                {
                    return fieldRidingVehicleCarNumber.getInt(null);
                }
                catch (Throwable ignoredReflect)
                {}
            }
        }

        return 0;
    }

    public static double readRidingVehicleX()
    {
        if (!isMtrLoaded())
        {
            return 0D;
        }

        try
        {
            return mchorse.bbs_mod.mixin.client.mtr.VehicleRidingMovementAccessor.getRidingVehicleX();
        }
        catch (Throwable ignored)
        {
            initReflection();

            if (fieldRidingVehicleX != null)
            {
                try
                {
                    return fieldRidingVehicleX.getDouble(null);
                }
                catch (Throwable ignoredReflect)
                {}
            }
        }

        return 0D;
    }

    public static double readRidingVehicleY()
    {
        if (!isMtrLoaded())
        {
            return 0D;
        }

        try
        {
            return mchorse.bbs_mod.mixin.client.mtr.VehicleRidingMovementAccessor.getRidingVehicleY();
        }
        catch (Throwable ignored)
        {
            initReflection();

            if (fieldRidingVehicleY != null)
            {
                try
                {
                    return fieldRidingVehicleY.getDouble(null);
                }
                catch (Throwable ignoredReflect)
                {}
            }
        }

        return 0D;
    }

    public static double readRidingVehicleZ()
    {
        if (!isMtrLoaded())
        {
            return 0D;
        }

        try
        {
            return mchorse.bbs_mod.mixin.client.mtr.VehicleRidingMovementAccessor.getRidingVehicleZ();
        }
        catch (Throwable ignored)
        {
            initReflection();

            if (fieldRidingVehicleZ != null)
            {
                try
                {
                    return fieldRidingVehicleZ.getDouble(null);
                }
                catch (Throwable ignoredReflect)
                {}
            }
        }

        return 0D;
    }

    public static double readPreviousVehicleYaw()
    {
        if (!isMtrLoaded())
        {
            return 0D;
        }

        try
        {
            return mchorse.bbs_mod.mixin.client.mtr.VehicleRidingMovementAccessor.getPreviousVehicleYaw();
        }
        catch (Throwable ignored)
        {
            initReflection();

            if (fieldPreviousVehicleYaw != null)
            {
                try
                {
                    return fieldPreviousVehicleYaw.getDouble(null);
                }
                catch (Throwable ignoredReflect)
                {}
            }
        }

        return 0D;
    }

    private static void initReflection()
    {
        if (reflectionInitialized)
        {
            return;
        }

        reflectionInitialized = true;

        try
        {
            Class<?> clazz = Class.forName("org.mtr.mod.client.VehicleRidingMovement");

            fieldRidingVehicleId = getAccessibleField(clazz, "ridingVehicleId");
            fieldRidingVehicleCarNumber = getAccessibleField(clazz, "ridingVehicleCarNumber");
            fieldRidingVehicleX = getAccessibleField(clazz, "ridingVehicleX");
            fieldRidingVehicleY = getAccessibleField(clazz, "ridingVehicleY");
            fieldRidingVehicleZ = getAccessibleField(clazz, "ridingVehicleZ");
            fieldPreviousVehicleYaw = getAccessibleField(clazz, "previousVehicleYaw");
        }
        catch (Throwable ignored)
        {}
    }

    private static Field getAccessibleField(Class<?> clazz, String name)
    {
        /* MTR declares simulation fields (railProgress, speed, reversed) on generated schema
         * superclasses, so the whole hierarchy has to be searched */
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass())
        {
            try
            {
                Field field = current.getDeclaredField(name);

                field.setAccessible(true);

                return field;
            }
            catch (Throwable ignored)
            {}
        }

        LOGGER.warn("[BBS/MTR] Field {}.{} was not found; the installed MTR version may be unsupported", clazz.getName(), name);

        return null;
    }

    public static final class RideData
    {
        public final long vehicleId;
        public final int carNumber;
        public final float localX;
        public final float localY;
        public final float localZ;

        public RideData(long vehicleId, int carNumber, float localX, float localY, float localZ)
        {
            this.vehicleId = vehicleId;
            this.carNumber = carNumber;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
        }

        public long vehicleId()
        {
            return this.vehicleId;
        }

        public int carNumber()
        {
            return this.carNumber;
        }

        public float localX()
        {
            return this.localX;
        }

        public float localY()
        {
            return this.localY;
        }

        public float localZ()
        {
            return this.localZ;
        }
    }

    public static final class Vector3d
    {
        public final double x;
        public final double y;
        public final double z;

        public Vector3d(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vector3d rotateX(float pitch)
        {
            float cos = MathHelper.cos(pitch);
            float sin = MathHelper.sin(pitch);
            double ny = this.y * (double) cos + this.z * (double) sin;
            double nz = this.z * (double) cos - this.y * (double) sin;

            return new Vector3d(this.x, ny, nz);
        }

        public Vector3d rotateY(float yaw)
        {
            float cos = MathHelper.cos(yaw);
            float sin = MathHelper.sin(yaw);
            double nx = this.x * (double) cos + this.z * (double) sin;
            double nz = this.z * (double) cos - this.x * (double) sin;

            return new Vector3d(nx, this.y, nz);
        }

        public Vector3d add(double dx, double dy, double dz)
        {
            return new Vector3d(this.x + dx, this.y + dy, this.z + dz);
        }
    }

    public static final class PositionAndRotation
    {
        public final double x;
        public final double y;
        public final double z;
        public final double yaw;
        public final double pitch;
        public final Vector3d position;

        public PositionAndRotation(double x, double y, double z, double yaw, double pitch)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.position = new Vector3d(x, y, z);
        }

        public <T> T transformForwards(T initialValue, Rotate<T> rotateX, Rotate<T> rotateY, Translate<T> translate)
        {
            return translate.apply(rotateY.apply(rotateX.apply(initialValue, (float) this.pitch), (float) this.yaw), this.x, this.y, this.z);
        }

        @FunctionalInterface
        public interface Rotate<T>
        {
            public T apply(T input, float amount);
        }

        @FunctionalInterface
        public interface Translate<T>
        {
            public T apply(T input, double amountX, double amountY, double amountZ);
        }
    }

    public static final class TransformedPose
    {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;

        public TransformedPose(double x, double y, double z, float yaw, float pitch)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public double x()
        {
            return this.x;
        }

        public double y()
        {
            return this.y;
        }

        public double z()
        {
            return this.z;
        }

        public float yaw()
        {
            return this.yaw;
        }

        public float pitch()
        {
            return this.pitch;
        }
    }

    public static final class VehicleLiveState
    {
        public final long vehicleId;
        public final long sidingId;
        public final String routeName;
        public final long stationId;
        public final String transportMode;
        public final double railProgress;
        public final double speed;
        public final double doorValue;
        public final double oldDoorValue;
        public final int doorMultiplier;
        public final double nextAnnouncementRailProgress;
        public final boolean playerRiding;
        public final boolean reversed;

        public VehicleLiveState(
            long vehicleId,
            long sidingId,
            String routeName,
            long stationId,
            String transportMode,
            double railProgress,
            double speed,
            double doorValue,
            double oldDoorValue,
            int doorMultiplier,
            double nextAnnouncementRailProgress,
            boolean playerRiding,
            boolean reversed
        )
        {
            this.reversed = reversed;
            this.vehicleId = vehicleId;
            this.sidingId = sidingId;
            this.routeName = routeName == null ? "" : routeName;
            this.stationId = stationId;
            this.transportMode = transportMode == null ? "TRAIN" : transportMode;
            this.railProgress = railProgress;
            this.speed = speed;
            this.doorValue = doorValue;
            this.oldDoorValue = oldDoorValue;
            this.doorMultiplier = doorMultiplier;
            this.nextAnnouncementRailProgress = nextAnnouncementRailProgress;
            this.playerRiding = playerRiding;
        }
    }
}
