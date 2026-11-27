package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.entity.Entity;

/**
 * The recorded player, as the tracking clips see it: the player's own entity for everything it
 * is (its model, its size, its eyes), but standing where the recording had it. Its feet, body,
 * head and look come from the recorded movement (or the camera, for a recap kept before the
 * movement was) at two ticks of the recording, the one at the cursor and
 * the next, so a clip that follows it moves in step with the picture between ticks too.
 */
public class RecapActor extends MCEntity
{
    private final RecapMotion.Frame now = new RecapMotion.Frame();
    private final RecapMotion.Frame before = new RecapMotion.Frame();
    private boolean placed;

    public RecapActor(Entity player)
    {
        super(player);
    }

    /** Puts the actor at the recording's {@code previous} and {@code current} moments. */
    public void place(RecapTrack track, RecapMotion motion, float previous, float current)
    {
        double eye = this.getMcEntity() instanceof net.minecraft.entity.player.PlayerEntity player ? player.getStandingEyeHeight() : 1.62D;

        this.placed = RecapScene.frameAt(track, motion, previous, this.before, eye) && RecapScene.frameAt(track, motion, current, this.now, eye);
    }

    public boolean isPlaced()
    {
        return this.placed;
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
        return this.placed ? this.now.y : super.getY();
    }

    @Override
    public double getPrevY()
    {
        return this.placed ? this.before.y : super.getPrevY();
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
        return this.placed ? this.now.headYaw : super.getYaw();
    }

    @Override
    public float getPrevYaw()
    {
        return this.placed ? this.before.headYaw : super.getPrevYaw();
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
        return this.placed ? this.now.bodyYaw : super.getBodyYaw();
    }

    @Override
    public float getPrevBodyYaw()
    {
        return this.placed ? this.before.bodyYaw : super.getPrevBodyYaw();
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
