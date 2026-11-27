package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.entity.Entity;

/**
 * The recorded player, as the tracking clips see it: the player's own entity for everything it
 * is (its model, its size, its eyes), but standing where the recording had it. Its position and
 * its turn come from the recap's camera at two ticks of the recording, the one at the cursor and
 * the next, so a clip that follows it moves in step with the picture between ticks too.
 */
public class RecapActor extends MCEntity
{
    private final RecapTrack.Sample now = new RecapTrack.Sample();
    private final RecapTrack.Sample before = new RecapTrack.Sample();
    private boolean placed;

    public RecapActor(Entity player)
    {
        super(player);
    }

    /** Puts the actor at the recording's {@code previous} and {@code current} moments. */
    public void place(RecapTrack track, float previous, float current)
    {
        this.placed = track != null && track.sample(previous, this.before) && track.sample(current, this.now);
    }

    public boolean isPlaced()
    {
        return this.placed;
    }

    private double feet(RecapTrack.Sample sample)
    {
        return sample.y - this.getEyeHeight();
    }

    @Override
    public double getX()
    {
        return this.placed ? this.now.x : super.getX();
    }

    @Override
    public double getPrevX()
    {
        return this.placed ? this.before.x : super.getPrevX();
    }

    @Override
    public double getY()
    {
        return this.placed ? this.feet(this.now) : super.getY();
    }

    @Override
    public double getPrevY()
    {
        return this.placed ? this.feet(this.before) : super.getPrevY();
    }

    @Override
    public double getZ()
    {
        return this.placed ? this.now.z : super.getZ();
    }

    @Override
    public double getPrevZ()
    {
        return this.placed ? this.before.z : super.getPrevZ();
    }

    @Override
    public float getYaw()
    {
        return this.placed ? this.now.yaw : super.getYaw();
    }

    @Override
    public float getPrevYaw()
    {
        return this.placed ? this.before.yaw : super.getPrevYaw();
    }

    @Override
    public float getHeadYaw()
    {
        return this.getYaw();
    }

    @Override
    public float getPrevHeadYaw()
    {
        return this.getPrevYaw();
    }

    @Override
    public float getBodyYaw()
    {
        return this.getYaw();
    }

    @Override
    public float getPrevBodyYaw()
    {
        return this.getPrevYaw();
    }

    @Override
    public float getPitch()
    {
        return this.placed ? this.now.pitch : super.getPitch();
    }

    @Override
    public float getPrevPitch()
    {
        return this.placed ? this.before.pitch : super.getPrevPitch();
    }
}
