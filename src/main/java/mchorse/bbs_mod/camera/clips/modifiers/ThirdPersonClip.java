package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * Third person camera on its own track: an Unreal Engine style spring arm hung on a replay (and
 * optionally one of its bones), with the camera lag and the collision probe of that arm.
 *
 * <p>The data lives here; the placement is the client's job ({@code ThirdPersonClientClip}), as the
 * bone's frame and the world are only there. Every number is a keyframe channel, seeded with one key
 * at its default so that a new clip shows a value to edit rather than an empty sheet. A channel a
 * file left without keys reads as its default.</p>
 */
public class ThirdPersonClip extends CameraClip
{
    /** A keyframe channel together with what it reads as while it has no keyframes. */
    public static class Channel
    {
        public final String id;
        public final double fallback;
        public final KeyframeChannel<Double> channel;

        private Channel(String id, double fallback)
        {
            this.id = id;
            this.fallback = fallback;
            this.channel = new KeyframeChannel<>(id, KeyframeFactories.DOUBLE);
        }

        public double get(float tick)
        {
            return this.channel.isEmpty() ? this.fallback : this.channel.interpolate(tick);
        }
    }

    /** The channels in the order the keyframe editor lists them. */
    public final List<Channel> spring = new ArrayList<>();

    /** Stable id of the followed replay (empty = none), as the tracker keeps it. */
    public final ValueString selector = new ValueString("selector", "");

    /** The bone the arm hangs on; empty for the replay's root. */
    public final ValueString group = new ValueString("group", "");

    public final ValueBoolean followTargetYaw = new ValueBoolean("follow_target_yaw", true);
    public final ValueBoolean positionLag = new ValueBoolean("position_lag", true);
    public final ValueBoolean rotationLag = new ValueBoolean("rotation_lag", true);
    public final ValueBoolean collisionTest = new ValueBoolean("collision_test", true);

    /* Target */
    public final Channel targetX = this.channel("target_x", 0D);
    public final Channel targetY = this.channel("target_y", 1.4D);
    public final Channel targetZ = this.channel("target_z", 0D);

    /* The arm */
    public final Channel armLength = this.channel("arm_length", 3.5D);
    public final Channel socketX = this.channel("socket_x", 0D);
    public final Channel socketY = this.channel("socket_y", 0D);
    public final Channel socketZ = this.channel("socket_z", 0D);
    public final Channel yaw = this.channel("yaw", 0D);
    public final Channel pitch = this.channel("pitch", 10D);
    public final Channel roll = this.channel("roll", 0D);
    public final Channel autoAlign = this.channel("auto_align", 1D);

    /* Lag and collision */
    public final Channel positionLagSpeed = this.channel("position_lag_speed", 8D);
    public final Channel maxLagDistance = this.channel("max_lag_distance", 0D);
    public final Channel rotationLagSpeed = this.channel("rotation_lag_speed", 10D);
    public final Channel probeRadius = this.channel("probe_radius", 0.25D);

    public ThirdPersonClip()
    {
        super();

        this.add(this.selector);
        this.add(this.group);
        this.add(this.followTargetYaw);
        this.add(this.positionLag);
        this.add(this.rotationLag);
        this.add(this.collisionTest);

        for (Channel channel : this.spring)
        {
            this.add(channel.channel);
            channel.channel.insert(0, channel.fallback);
        }
    }

    private Channel channel(String id, double fallback)
    {
        Channel channel = new Channel(id, fallback);

        this.spring.add(channel);

        return channel;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new ThirdPersonClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        for (Channel channel : this.spring)
        {
            channel.channel.moveX(-offset);
        }
    }
}
