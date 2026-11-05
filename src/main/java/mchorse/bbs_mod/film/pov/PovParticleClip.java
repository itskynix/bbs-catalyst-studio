package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Particles in front of the replay's eyes: a puff of the picked particle when the clip starts, and
 * again every so many ticks if an interval is set. Where it puffs is in the view's own axes (x to
 * the right, y up, z ahead), so it stays in front of the eyes wherever they look.
 */
public class PovParticleClip extends PovClip
{
    public final ValueString particle = new ValueString("particle", "minecraft:poof");
    public final ValueInt count = new ValueInt("count", 8, 1, 500);
    /** 0 puffs once, at the start */
    public final ValueInt interval = new ValueInt("interval", 0, 0, 1000);
    public final ValuePoint offset = new ValuePoint("offset", new Point(0, 0, 1));
    public final ValueFloat spread = new ValueFloat("spread", 0.25F, 0F, 8F);
    public final ValueFloat speed = new ValueFloat("speed", 0.02F, 0F, 4F);

    public PovParticleClip()
    {
        super();

        this.add(this.particle);
        this.add(this.count);
        this.add(this.interval);
        this.add(this.offset);
        this.add(this.spread);
        this.add(this.speed);
    }

    @Override
    protected Clip create()
    {
        return new PovParticleClip();
    }
}
