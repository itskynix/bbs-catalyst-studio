package mchorse.bbs_mod.film.eye;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.ActorEyeClientClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * Whether the picture is being seen through an actor's eyes right now, and whose. A Point of View
 * clip reports where it put the camera every time it is evaluated; the frame is seen through the
 * actor when the camera the world is drawn from stands where one of this frame's reports says, and a
 * film's camera (a playing film's, or the editor's) is the one in charge.
 *
 * <p>Only this frame's reports count: the clips are evaluated as the camera is set up, at the start
 * of every frame a film is on, so the moment the film's camera lets go (the editor closed, the film
 * stopped) there is no report of the frame and the player's own hands are back on that very frame,
 * not a quarter of a second later. Comparing places, not flags, keeps it honest the other way: a
 * camera flown off in the editor, or a later clip taking over, is simply not where the clip put it.</p>
 */
public final class ActorEyeState
{
    /** How near the camera must stand to where the clip put it (a shake on top moves it a little) */
    private static final double NEAR = 0.75D;

    /**
     * @param replay the replay looked out of, when the film it is in is on screen
     * @param tick the replay's own tick the clip was evaluated at (looping applied), the partial tick in it
     * @param firstPerson from the eyes; false from behind or in front of the actor
     * @param frame the frame it was made in
     * @param order which report of the frame it was, the later the newer
     */
    public record Report(IEntity entity, ActorEyeClientClip clip, Replay replay, double x, double y, double z, float tick, boolean firstPerson, long frame, long order)
    {}

    /* The last few reports: the editor evaluates clips at other ticks too, and those must not crowd out the camera's */
    private static final Report[] RECENT = new Report[8];
    private static int next;
    private static long frame;
    private static long order;

    private ActorEyeState()
    {}

    /** A new frame: what was reported before it no longer says anything about it */
    public static void beginFrame()
    {
        frame += 1;
    }

    /** Forget every report, for when the film's camera is let go of */
    public static void clear()
    {
        Arrays.fill(RECENT, null);
    }

    public static void report(IEntity entity, ActorEyeClientClip clip, Replay replay, double x, double y, double z, float tick, boolean firstPerson)
    {
        RECENT[next] = new Report(entity, clip, replay, x, y, z, tick, firstPerson, frame, order++);
        next = (next + 1) % RECENT.length;
    }

    /** The report the camera stands at, or null when the picture is not seen through an actor */
    public static Report current(Vec3d camera)
    {
        return find(camera, 0);
    }

    /**
     * The same, for what the game does to the view before the camera is set up this frame (the
     * projection: the nausea's warp): the camera still stands where the last frame put it, and the
     * last frame's report is the one about it.
     */
    public static Report beforeCamera()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.gameRenderer == null ? null : find(mc.gameRenderer.getCamera().getPos(), 1);
    }

    private static Report find(Vec3d camera, int framesBack)
    {
        if (!(BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController))
        {
            return null;
        }

        Report best = null;

        for (Report report : RECENT)
        {
            if (report == null || report.frame() < frame - framesBack)
            {
                continue;
            }

            double dx = report.x() - camera.x;
            double dy = report.y() - camera.y;
            double dz = report.z() - camera.z;

            if (dx * dx + dy * dy + dz * dz <= NEAR * NEAR && (best == null || report.order() > best.order()))
            {
                best = report;
            }
        }

        return best;
    }

    public static Report current()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.gameRenderer == null ? null : current(mc.gameRenderer.getCamera().getPos());
    }

    /**
     * Whether an actor is the one being looked out of, from its eyes, by a clip that hides it: then
     * nothing of its body is drawn (the head, the torso, the legs), as the game draws nothing of the
     * player's in first person; the arms come from the hand pass. In the shadow pass of a shader pack
     * it is drawn all the same, so it still throws its shadow, as the player does.
     */
    public static boolean hidesBody(IEntity entity)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return false;
        }

        Report report = current();

        return report != null && report.entity() == entity && report.firstPerson() && report.clip().hideHead.get();
    }
}
