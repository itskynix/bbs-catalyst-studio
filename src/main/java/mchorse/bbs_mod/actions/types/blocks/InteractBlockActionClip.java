package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.values.ValueBlockHitResult;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public class InteractBlockActionClip extends ActionClip
{
    public final ValueBlockHitResult hit = new ValueBlockHitResult("hit");
    public final ValueBoolean hand = new ValueBoolean("hand", true);

    /* A door, trapdoor, gate or chest opened by hand a share of the way at a time (see the replay's interactive openables):
     * how open it was when the hold began and how open it ends (0 to 1), how many ticks before this one the hold began, and
     * the angle it swung to. A negative start means an ordinary, instant interaction. */
    public final ValueFloat swingFrom = new ValueFloat("swing_from", -1F, -1F, 1F);
    public final ValueFloat swingTo = new ValueFloat("swing_to", 0F, 0F, 1F);
    public final ValueInt swingTicks = new ValueInt("swing_ticks", 0, 0, 2400);
    public final ValueFloat swingAngle = new ValueFloat("swing_angle", 90F, 15F, 135F);

    public InteractBlockActionClip()
    {
        super();

        this.add(this.hit);
        this.add(this.hand);
        this.add(this.swingFrom);
        this.add(this.swingTo);
        this.add(this.swingTicks);
        this.add(this.swingAngle);
    }

    @Override
    public void shift(double dx, double dy, double dz)
    {
        super.shift(dx, dy, dz);

        this.hit.shift(dx, dy, dz);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        this.applyPositionRotation(player, replay, tick);

        BlockHitResult result = this.hit.getHitResult();

        //? if >=1.21 {
        /*player.getWorld().getBlockState(result.getBlockPos()).onUse(player.getWorld(), player, result);
        *///?} else {
        player.getWorld().getBlockState(result.getBlockPos()).onUse(player.getWorld(), player, this.hand.get() ? Hand.MAIN_HAND : Hand.OFF_HAND, result);
        //?}
    }

    /**
     * A container the interaction opened stays open for as long as the clip is
     * long (LUCKYWAY) - the recorder grew it for exactly as long as the player
     * kept the screen up. A clip of a single tick is a tap, a button or a door,
     * and holds nothing open.
     */
    @Override
    public void applyRange(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (this.duration.get() > 1)
        {
            player.wantLidOpen(this.hit.getBlockPos());
        }
    }

    @Override
    protected Clip create()
    {
        return new InteractBlockActionClip();
    }
}