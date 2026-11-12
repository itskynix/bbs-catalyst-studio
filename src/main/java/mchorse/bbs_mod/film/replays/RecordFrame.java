package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * Represents a single recorded frame of replay data, including optional
 * MTR (Minecraft Transit Railway) local-space vehicle riding data.
 */
public class RecordFrame
{
    public static final String NBT_MTR_RIDE = "mtr_ride";

    public boolean isMtrRiding = false;
    public long mtrVehicleId = 0L;
    public int mtrCarNumber = 0;
    public float mtrLocalX;
    public float mtrLocalY;
    public float mtrLocalZ;
    public float mtrLocalYaw;
    public float mtrLocalPitch;
    public double mtrRailProgress = Double.NaN;
    public double mtrSpeed = 0D;

    public RecordFrame()
    {}

    public RecordFrame copy()
    {
        RecordFrame copy = new RecordFrame();

        copy.isMtrRiding = this.isMtrRiding;
        copy.mtrVehicleId = this.mtrVehicleId;
        copy.mtrCarNumber = this.mtrCarNumber;
        copy.mtrLocalX = this.mtrLocalX;
        copy.mtrLocalY = this.mtrLocalY;
        copy.mtrLocalZ = this.mtrLocalZ;
        copy.mtrLocalYaw = this.mtrLocalYaw;
        copy.mtrLocalPitch = this.mtrLocalPitch;
        copy.mtrRailProgress = this.mtrRailProgress;
        copy.mtrSpeed = this.mtrSpeed;

        return copy;
    }

    public NbtCompound toNbt()
    {
        NbtCompound root = new NbtCompound();

        this.toNbt(root);

        return root;
    }

    public void toNbt(NbtCompound root)
    {
        if (root == null || !this.isMtrRiding)
        {
            return;
        }

        NbtCompound rideTag = new NbtCompound();

        rideTag.putLong("vehicle_id", this.mtrVehicleId);
        rideTag.putInt("car_number", this.mtrCarNumber);
        rideTag.putFloat("local_x", this.mtrLocalX);
        rideTag.putFloat("local_y", this.mtrLocalY);
        rideTag.putFloat("local_z", this.mtrLocalZ);
        rideTag.putFloat("local_yaw", this.mtrLocalYaw);
        rideTag.putFloat("local_pitch", this.mtrLocalPitch);

        if (!Double.isNaN(this.mtrRailProgress))
        {
            rideTag.putDouble("rail_progress", this.mtrRailProgress);
            rideTag.putDouble("speed", this.mtrSpeed);
        }

        root.put(NBT_MTR_RIDE, rideTag);
    }

    public void fromNbt(NbtCompound root)
    {
        this.resetMtrRide();

        if (root == null || !root.contains(NBT_MTR_RIDE, NbtElement.COMPOUND_TYPE))
        {
            return;
        }

        NbtCompound rideTag = root.getCompound(NBT_MTR_RIDE);

        this.isMtrRiding = true;
        this.mtrVehicleId = rideTag.getLong("vehicle_id");
        this.mtrCarNumber = rideTag.getInt("car_number");
        this.mtrLocalX = rideTag.getFloat("local_x");
        this.mtrLocalY = rideTag.getFloat("local_y");
        this.mtrLocalZ = rideTag.getFloat("local_z");
        this.mtrLocalYaw = rideTag.getFloat("local_yaw");
        this.mtrLocalPitch = rideTag.getFloat("local_pitch");

        if (rideTag.contains("rail_progress", NbtElement.NUMBER_TYPE))
        {
            this.mtrRailProgress = rideTag.getDouble("rail_progress");
        }

        if (rideTag.contains("speed", NbtElement.NUMBER_TYPE))
        {
            this.mtrSpeed = rideTag.getDouble("speed");
        }
    }

    public MapType toMapData()
    {
        MapType root = new MapType();

        this.toMapData(root);

        return root;
    }

    public void toMapData(MapType root)
    {
        if (root == null || !this.isMtrRiding)
        {
            return;
        }

        MapType rideMap = new MapType();

        rideMap.putLong("vehicle_id", this.mtrVehicleId);
        rideMap.putInt("car_number", this.mtrCarNumber);
        rideMap.putFloat("local_x", this.mtrLocalX);
        rideMap.putFloat("local_y", this.mtrLocalY);
        rideMap.putFloat("local_z", this.mtrLocalZ);
        rideMap.putFloat("local_yaw", this.mtrLocalYaw);
        rideMap.putFloat("local_pitch", this.mtrLocalPitch);

        if (!Double.isNaN(this.mtrRailProgress))
        {
            rideMap.putDouble("rail_progress", this.mtrRailProgress);
            rideMap.putDouble("speed", this.mtrSpeed);
        }

        root.put(NBT_MTR_RIDE, rideMap);
    }

    public void fromMapData(MapType root)
    {
        this.resetMtrRide();

        if (root == null || !root.has(NBT_MTR_RIDE))
        {
            return;
        }

        MapType rideMap = root.getMap(NBT_MTR_RIDE);

        if (rideMap == null || rideMap.isEmpty())
        {
            return;
        }

        this.isMtrRiding = true;
        this.mtrVehicleId = rideMap.getLong("vehicle_id");
        this.mtrCarNumber = rideMap.getInt("car_number");
        this.mtrLocalX = rideMap.getFloat("local_x");
        this.mtrLocalY = rideMap.getFloat("local_y");
        this.mtrLocalZ = rideMap.getFloat("local_z");
        this.mtrLocalYaw = rideMap.getFloat("local_yaw");
        this.mtrLocalPitch = rideMap.getFloat("local_pitch");

        if (rideMap.has("rail_progress"))
        {
            this.mtrRailProgress = rideMap.getDouble("rail_progress");
        }

        if (rideMap.has("speed"))
        {
            this.mtrSpeed = rideMap.getDouble("speed");
        }
    }

    public void resetMtrRide()
    {
        this.isMtrRiding = false;
        this.mtrVehicleId = 0L;
        this.mtrCarNumber = 0;
        this.mtrLocalX = 0F;
        this.mtrLocalY = 0F;
        this.mtrLocalZ = 0F;
        this.mtrLocalYaw = 0F;
        this.mtrLocalPitch = 0F;
        this.mtrRailProgress = Double.NaN;
        this.mtrSpeed = 0D;
    }
}
