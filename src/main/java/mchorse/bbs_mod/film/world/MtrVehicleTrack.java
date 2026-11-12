package mchorse.bbs_mod.film.world;

import mchorse.bbs_mod.data.types.MapType;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Independent, per-tick trace of an MTR vehicle, stored inside a {@link MtrDispatchTrack}.
 *
 * <p>The vehicle's state is a pure function of the block-local tick: rail progress, speed,
 * door value, door direction and travel direction are sampled once per recorded tick, regardless
 * of whether an actor was riding. Playback evaluates {@link #sample(float, Sample)} and hands the
 * result to the client governor, which is the only writer of the live vehicle.</p>
 */
public class MtrVehicleTrack
{
    /** Bytes per sample: railProgress (8) + speed (8) + doorValue (4) + flags (1) */
    private static final int SAMPLE_BYTES = 21;
    private static final int FLAG_REVERSED = 1;
    private static final int FLAG_DOOR_OPENING = 2;
    private static final int FLAG_DOOR_CLOSING = 4;
    /** A per-tick rail progress jump beyond this is a teleport (path wrap, turn-around), not motion */
    private static final double DISCONTINUITY = 40D;

    /* Identity of the recorded vehicle (used to find it again, or rebuild it) */
    public long vehicleId = 0L;
    public long sidingId = 0L;
    public long depotId = 0L;
    public long routeId = 0L;
    public String routeName = "";
    public String transportMode = "TRAIN";
    public int pathSize = 0;
    public double pathLength = 0D;
    public double vehicleLength = 0D;
    /** GZIP + Base64 of the vehicle and its extra data as MTR serialized them at record time */
    public String snapshot = "";

    private int size = 0;
    private double[] railProgress = new double[0];
    private double[] speed = new double[0];
    private float[] doorValue = new float[0];
    private byte[] flags = new byte[0];

    public boolean hasData()
    {
        return this.size > 0;
    }

    public int size()
    {
        return this.size;
    }

    public void clear()
    {
        this.size = 0;
        this.railProgress = new double[0];
        this.speed = new double[0];
        this.doorValue = new float[0];
        this.flags = new byte[0];
    }

    /**
     * Writes the sample of the given block-local tick. Skipped ticks are filled with the previous
     * sample, so the trace stays dense.
     */
    public void record(int tick, double railProgress, double speed, double doorValue, int doorMultiplier, boolean reversed)
    {
        if (tick < 0 || Double.isNaN(railProgress))
        {
            return;
        }

        this.ensureCapacity(tick + 1);

        byte flags = (byte) ((reversed ? FLAG_REVERSED : 0) | (doorMultiplier > 0 ? FLAG_DOOR_OPENING : 0) | (doorMultiplier < 0 ? FLAG_DOOR_CLOSING : 0));

        for (int i = this.size; i < tick; i++)
        {
            boolean first = this.size == 0;

            this.railProgress[i] = first ? railProgress : this.railProgress[this.size - 1];
            this.speed[i] = first ? speed : this.speed[this.size - 1];
            this.doorValue[i] = first ? (float) doorValue : this.doorValue[this.size - 1];
            this.flags[i] = first ? flags : this.flags[this.size - 1];
        }

        this.railProgress[tick] = railProgress;
        this.speed[tick] = speed;
        this.doorValue[tick] = (float) Math.max(0D, Math.min(1D, doorValue));
        this.flags[tick] = flags;
        this.size = Math.max(this.size, tick + 1);
    }

    /**
     * Evaluates the trace at the given block-local tick (sub-tick precision).
     *
     * @return false when there is nothing recorded
     */
    public boolean sample(float tick, Sample out)
    {
        if (this.size == 0 || out == null)
        {
            return false;
        }

        float clamped = Math.max(0F, Math.min(tick, this.size - 1));
        int i1 = (int) Math.floor(clamped);
        int i2 = Math.min(i1 + 1, this.size - 1);
        float t = clamped - i1;

        double p1 = this.railProgress[i1];
        double p2 = this.railProgress[i2];

        if (i1 == i2 || t <= 0F || Math.abs(p2 - p1) > DISCONTINUITY || this.flags[i1] != this.flags[i2] && ((this.flags[i1] ^ this.flags[i2]) & FLAG_REVERSED) != 0)
        {
            out.railProgress = p1;
            out.speed = this.speed[i1];
            out.doorValue = this.doorValue[i1];
        }
        else
        {
            double p0 = this.railProgress[Math.max(i1 - 1, 0)];
            double p3 = this.railProgress[Math.min(i2 + 1, this.size - 1)];

            if (Math.abs(p1 - p0) > DISCONTINUITY)
            {
                p0 = p1;
            }

            if (Math.abs(p3 - p2) > DISCONTINUITY)
            {
                p3 = p2;
            }

            /* Catmull-Rom keeps velocity continuous across ticks; the clamp keeps a stopped train stopped */
            double t2 = t * t;
            double t3 = t2 * t;
            double value = 0.5D * ((2D * p1) + (-p0 + p2) * t + (2D * p0 - 5D * p1 + 4D * p2 - p3) * t2 + (-p0 + 3D * p1 - 3D * p2 + p3) * t3);

            out.railProgress = Math.max(Math.min(p1, p2), Math.min(Math.max(p1, p2), value));
            out.speed = this.speed[i1] + (this.speed[i2] - this.speed[i1]) * t;
            out.doorValue = this.doorValue[i1] + (this.doorValue[i2] - this.doorValue[i1]) * t;
        }

        int flags = this.flags[i1];

        out.reversed = (flags & FLAG_REVERSED) != 0;
        out.doorMultiplier = (flags & FLAG_DOOR_OPENING) != 0 ? 1 : ((flags & FLAG_DOOR_CLOSING) != 0 ? -1 : 0);

        return true;
    }

    private void ensureCapacity(int capacity)
    {
        if (this.railProgress.length >= capacity)
        {
            return;
        }

        int newCapacity = Math.max(capacity, Math.max(256, this.railProgress.length * 2));

        this.railProgress = Arrays.copyOf(this.railProgress, newCapacity);
        this.speed = Arrays.copyOf(this.speed, newCapacity);
        this.doorValue = Arrays.copyOf(this.doorValue, newCapacity);
        this.flags = Arrays.copyOf(this.flags, newCapacity);
    }

    public MapType toNbt()
    {
        MapType map = new MapType();

        map.putLong("vehicle_id", this.vehicleId);
        map.putLong("siding_id", this.sidingId);
        map.putLong("depot_id", this.depotId);
        map.putLong("route_id", this.routeId);
        map.putString("route_name", this.routeName == null ? "" : this.routeName);
        map.putString("transport_mode", this.transportMode == null ? "TRAIN" : this.transportMode);
        map.putInt("path_size", this.pathSize);
        map.putDouble("path_length", this.pathLength);
        map.putDouble("vehicle_length", this.vehicleLength);

        if (this.snapshot != null && !this.snapshot.isEmpty())
        {
            map.putString("snapshot", this.snapshot);
        }

        ByteBuffer buffer = ByteBuffer.allocate(this.size * SAMPLE_BYTES);

        for (int i = 0; i < this.size; i++)
        {
            buffer.putDouble(this.railProgress[i]);
            buffer.putDouble(this.speed[i]);
            buffer.putFloat(this.doorValue[i]);
            buffer.put(this.flags[i]);
        }

        map.putInt("samples_count", this.size);
        map.putByteArray("samples", buffer.array());

        return map;
    }

    public void fromNbt(MapType map)
    {
        this.clear();

        if (map == null)
        {
            return;
        }

        this.vehicleId = map.getLong("vehicle_id", 0L);
        this.sidingId = map.getLong("siding_id", 0L);
        this.depotId = map.getLong("depot_id", 0L);
        this.routeId = map.getLong("route_id", 0L);
        this.routeName = map.getString("route_name", "");
        this.transportMode = map.getString("transport_mode", "TRAIN");
        this.pathSize = map.getInt("path_size", 0);
        this.pathLength = map.getDouble("path_length", 0D);
        this.vehicleLength = map.getDouble("vehicle_length", 0D);
        this.snapshot = map.getString("snapshot", "");

        byte[] bytes = map.has("samples") ? map.getByteArray("samples", null) : null;

        if (bytes == null)
        {
            return;
        }

        int count = Math.min(map.getInt("samples_count", bytes.length / SAMPLE_BYTES), bytes.length / SAMPLE_BYTES);

        if (count <= 0)
        {
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        this.ensureCapacity(count);

        for (int i = 0; i < count; i++)
        {
            this.railProgress[i] = buffer.getDouble();
            this.speed[i] = buffer.getDouble();
            this.doorValue[i] = buffer.getFloat();
            this.flags[i] = buffer.get();
        }

        this.size = count;
    }

    public void copyFrom(MtrVehicleTrack other)
    {
        this.clear();

        if (other == null)
        {
            return;
        }

        this.vehicleId = other.vehicleId;
        this.sidingId = other.sidingId;
        this.depotId = other.depotId;
        this.routeId = other.routeId;
        this.routeName = other.routeName;
        this.transportMode = other.transportMode;
        this.pathSize = other.pathSize;
        this.pathLength = other.pathLength;
        this.vehicleLength = other.vehicleLength;
        this.snapshot = other.snapshot;
        this.size = other.size;
        this.railProgress = Arrays.copyOf(other.railProgress, other.size);
        this.speed = Arrays.copyOf(other.speed, other.size);
        this.doorValue = Arrays.copyOf(other.doorValue, other.size);
        this.flags = Arrays.copyOf(other.flags, other.size);
    }

    /** Evaluated vehicle state at one point in time */
    public static class Sample
    {
        public double railProgress;
        public double speed;
        public double doorValue;
        public int doorMultiplier;
        public boolean reversed;
    }
}
