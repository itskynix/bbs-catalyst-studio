package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * A 2D transform of the finished frame (Premiere / After Effects style): shift, rotate and scale the
 * picture around its centre. It is a post-production pass, not a camera: it never touches the 3D
 * camera, the angle or the depth of field, so it stacks on top of any camera clip underneath.
 *
 * <p>Position is a fraction of the frame size, rotation is in degrees (clockwise on screen) and the
 * scales are factors. Each channel reads the matching base value while it has no keyframes, which is
 * what the panel's trackpads edit.</p>
 */
public class TransformFxClip extends FxClip
{
    public final ValueFloat basePosX = new ValueFloat("base_pos_x", 0F, -10F, 10F);
    public final ValueFloat basePosY = new ValueFloat("base_pos_y", 0F, -10F, 10F);
    public final ValueFloat baseRotation = new ValueFloat("base_rotation", 0F, -360F, 360F);
    public final ValueFloat baseScaleX = new ValueFloat("base_scale_x", 1F, 0F, 100F);
    public final ValueFloat baseScaleY = new ValueFloat("base_scale_y", 1F, 0F, 100F);
    /** While on, scale Y follows scale X */
    public final ValueBoolean uniformScale = new ValueBoolean("uniform_scale", true);
    /** What fills the area the moved frame no longer covers: 0 black, 1 repeat, 2 mirror, 3 clamp */
    public final ValueInt edgeMode = new ValueInt("edge_mode", 0, 0, 3);

    public final Channel posX = this.channel("pos_x", 0D);
    public final Channel posY = this.channel("pos_y", 0D);
    public final Channel rotation = this.channel("rotation", 0D);
    public final Channel scaleX = this.channel("scale_x", 1D);
    public final Channel scaleY = this.channel("scale_y", 1D);

    public TransformFxClip()
    {
        super();

        this.add(this.basePosX);
        this.add(this.basePosY);
        this.add(this.baseRotation);
        this.add(this.baseScaleX);
        this.add(this.baseScaleY);
        this.add(this.uniformScale);
        this.add(this.edgeMode);
    }

    private static double read(Channel channel, ValueFloat base, float tick)
    {
        return channel.channel.isEmpty() ? base.get() : channel.channel.interpolate(tick);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        double x = read(this.posX, this.basePosX, t) * factor;
        double y = read(this.posY, this.basePosY, t) * factor;
        double rot = read(this.rotation, this.baseRotation, t) * factor;
        double sx = read(this.scaleX, this.baseScaleX, t);
        double sy = this.uniformScale.get() ? sx : read(this.scaleY, this.baseScaleY, t);

        /* The envelope fades the effect in and out: a factor below one eases towards neutral */
        sx = 1D + (sx - 1D) * factor;
        sy = 1D + (sy - 1D) * factor;

        /* Neutral: no pass at all, so an idle transform costs nothing */
        if (x == 0D && y == 0D && rot == 0D && sx == 1D && sy == 1D)
        {
            return;
        }

        out.add(new FxOverlay(FxOverlay.TRANSFORM)
            .set(0, x)
            .set(1, y)
            .set(2, rot)
            .set(3, sx)
            .set(4, sy)
            .set(8, this.edgeMode.get()));
    }

    @Override
    protected Clip create()
    {
        return new TransformFxClip();
    }
}
