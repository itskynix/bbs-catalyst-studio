package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Crash debris emitter: at the moment the clip begins, a burst of sparks, glass shards, metal scrap
 * and smoke flies out of the impact point. The clip only describes the burst; the client turns it
 * into particles (see {@code DebrisSpawner}).
 */
public class DebrisClip extends FxClip
{
    public static final String DEBRIS_KEY = "debris";

    /** A burst the clip asks for this frame. */
    public static class Burst
    {
        public final DebrisClip clip;
        public final int age;

        public Burst(DebrisClip clip, int age)
        {
            this.clip = clip;
            this.age = age;
        }
    }

    public final ValueFloat x = new ValueFloat("x", 0F, -3.0E7F, 3.0E7F);
    public final ValueFloat y = new ValueFloat("y", 64F, -3.0E7F, 3.0E7F);
    public final ValueFloat z = new ValueFloat("z", 0F, -3.0E7F, 3.0E7F);
    public final ValueInt count = new ValueInt("count", 48, 1, 512);
    /** Launch speed in blocks per tick */
    public final ValueFloat speed = new ValueFloat("speed", 0.35F, 0F, 4F);
    public final ValueBoolean sparks = new ValueBoolean("sparks", true);
    public final ValueBoolean glass = new ValueBoolean("glass", true);
    public final ValueBoolean metal = new ValueBoolean("metal", true);
    public final ValueBoolean smoke = new ValueBoolean("smoke", true);

    public DebrisClip()
    {
        super();

        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
        this.add(this.count);
        this.add(this.speed);
        this.add(this.sparks);
        this.add(this.glass);
        this.add(this.metal);
        this.add(this.smoke);
    }

    public static List<Burst> getBursts(ClipContext context)
    {
        return context.clipData.get(DEBRIS_KEY, ArrayList::new);
    }

    @Override
    protected void collect(float tick, float factor, List<FxOverlay> out)
    {}

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        getBursts(context).add(new Burst(this, context.relativeTick));
    }

    @Override
    protected Clip create()
    {
        return new DebrisClip();
    }
}
