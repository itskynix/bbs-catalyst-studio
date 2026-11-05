package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The camera behind an actor's eyes: it stands on a bone of the actor (the head by default),
 * looks the way that bone looks, and moves with it, so a replay is seen in the first person.
 *
 * <p>The placement is the client's to work out - it needs the bone's matrix, which only the
 * renderer has (see {@code ActorEyeClientClip}). What is kept here is the data: which actor and
 * which bone, an offset in the bone's own axes (the eyes sit in front of and above the neck the
 * head turns about), an angle on top of the bone's, the field of view, how much the camera lags
 * behind the bone to take the edge off sharp turns, and how the first-person hands are placed.</p>
 *
 * <p>The offset and the roll have keyframe channels of their own, added to the set values, so a
 * shot can lean or push in over its length.</p>
 *
 * <p>Shown as the "Point of View" clip: the replay looked out of is its source, the POV output
 * flags say which layers of that point of view reach the picture, and the camera modes say how
 * the head is followed and from where (the eyes, or behind or in front of the actor).</p>
 */
public class ActorEyeClip extends EntityClip
{
    public static final int PERSPECTIVE_FIRST = 0;
    public static final int PERSPECTIVE_BACK = 1;
    public static final int PERSPECTIVE_FRONT = 2;

    /** The bone the camera stands on; when the actor's form has no such bone, its eyes */
    public final ValueString bone = new ValueString("bone", "head");
    /** On top of the bone's own turn: x pitch, y yaw, z roll, in degrees */
    public final ValuePoint angle = new ValuePoint("angle", new Point(0, 0, 0));
    public final ValueFloat fov = new ValueFloat("fov", 70F, 1F, 170F);
    /** 0 follows the bone exactly; towards 1 the camera trails it, smoothing jolts and quick turns */
    public final ValueFloat damping = new ValueFloat("damping", 0F, 0F, 1F);

    /** Hide the actor's head (and what hangs off it), so it does not block the view from inside it */
    public final ValueBoolean hideHead = new ValueBoolean("hide_head", true);
    /** Draw the actor's arms and held items in front of the camera, the way the game draws the player's */
    public final ValueBoolean hands = new ValueBoolean("hands", true);
    /** Moves the first-person arms in the view (blocks), for models whose arms sit too near or too far */
    public final ValuePoint handOffset = new ValuePoint("hand_offset", new Point(0, 0, 0));
    public final ValueFloat handPitch = new ValueFloat("hand_pitch", 0F, -90F, 90F);
    public final ValueFloat handYaw = new ValueFloat("hand_yaw", 0F, -90F, 90F);

    /* POV output: which layers of the replay's point of view reach the picture (the hands are above) */
    public final ValueBoolean outHud = new ValueBoolean("out_hud", true);
    public final ValueBoolean outCrosshair = new ValueBoolean("out_crosshair", true);
    public final ValueBoolean outPovActions = new ValueBoolean("out_pov_actions", true);
    public final ValueBoolean outScreenEffects = new ValueBoolean("out_screen_effects", true);
    public final ValueBoolean outCursor = new ValueBoolean("out_cursor", true);
    public final ValueBoolean outCameraShake = new ValueBoolean("out_camera_shake", true);

    /* Camera: follow the head (else the body), keep the head within the body's turn, outline the looked-at block */
    public final ValueBoolean headLook = new ValueBoolean("head_look", true);
    public final ValueBoolean hardcoreLook = new ValueBoolean("hardcore_look", false);
    public final ValueBoolean blockOutline = new ValueBoolean("block_outline", true);

    /** As the game's F5 cycles it: from the eyes, from behind the actor, from in front of it */
    public final ValueInt perspective = new ValueInt("perspective", PERSPECTIVE_FIRST, PERSPECTIVE_FIRST, PERSPECTIVE_FRONT);

    /* Keyed on top of the offset and the roll, over the clip's length */
    public final KeyframeChannel<Double> channelX = new KeyframeChannel<>("eye_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> channelY = new KeyframeChannel<>("eye_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> channelZ = new KeyframeChannel<>("eye_z", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> channelRoll = new KeyframeChannel<>("eye_roll", KeyframeFactories.DOUBLE);

    public ActorEyeClip()
    {
        super();

        /* The head turns about the neck; the eyes are a little above it */
        this.offset.set(new Point(0, 0.25, 0));

        this.add(this.bone);
        this.add(this.angle);
        this.add(this.fov);
        this.add(this.damping);
        this.add(this.hideHead);
        this.add(this.hands);
        this.add(this.handOffset);
        this.add(this.handPitch);
        this.add(this.handYaw);
        this.add(this.outHud);
        this.add(this.outCrosshair);
        this.add(this.outPovActions);
        this.add(this.outScreenEffects);
        this.add(this.outCursor);
        this.add(this.outCameraShake);
        this.add(this.headLook);
        this.add(this.hardcoreLook);
        this.add(this.blockOutline);
        this.add(this.perspective);
        this.add(this.channelX);
        this.add(this.channelY);
        this.add(this.channelZ);
        this.add(this.channelRoll);
    }

    private static double keyed(KeyframeChannel<Double> channel, float tick)
    {
        return channel.isEmpty() ? 0D : channel.interpolate(tick, 0D);
    }

    /** The offset at a tick of the clip: the set one with the keyed one on top */
    public Point offsetAt(float tick)
    {
        Point offset = this.offset.get();

        return new Point(offset.x + keyed(this.channelX, tick), offset.y + keyed(this.channelY, tick), offset.z + keyed(this.channelZ, tick));
    }

    /** The angle at a tick of the clip: the set one with the keyed roll on top */
    public Point angleAt(float tick)
    {
        Point angle = this.angle.get();

        return new Point(angle.x, angle.y, angle.z + keyed(this.channelRoll, tick));
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new ActorEyeClip();
    }
}
