package mchorse.bbs_mod.physics;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Where the author is aiming, for the right click menus of the physics clips' fields: the point
 * the crosshair lands on and the way it looks.
 *
 * <p>In the film editor that is the preview's camera, the middle of the picture. Anywhere else it
 * is the player's own crosshair. The point is the exact spot on the surface the crosshair meets,
 * up to 128 blocks away, or 16 blocks straight ahead when it meets nothing.</p>
 */
public final class PhysicsAim
{
    private static final double REACH = 128D;
    private static final double OPEN_AIR = 16D;

    private PhysicsAim()
    {}

    /** The film editor that is open, or null */
    public static UIFilmPanel filmPanel()
    {
        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel panel)
        {
            return panel;
        }

        return null;
    }

    /** Where the view starts: the preview's camera or the player's eyes, or null when there is neither */
    public static Vector3d eye()
    {
        UIFilmPanel panel = filmPanel();

        if (panel != null && panel.getCamera() != null)
        {
            Camera camera = panel.getCamera();

            return new Vector3d(camera.position.x, camera.position.y, camera.position.z);
        }

        Entity entity = MinecraftClient.getInstance().getCameraEntity();

        if (entity == null)
        {
            return null;
        }

        Vec3d eye = entity.getCameraPosVec(1F);

        return new Vector3d(eye.x, eye.y, eye.z);
    }

    /** The way the view looks, a unit vector in world axes */
    public static Vector3f direction()
    {
        UIFilmPanel panel = filmPanel();

        if (panel != null && panel.lastView != null)
        {
            return new Matrix4f(panel.lastView).invert().transformDirection(new Vector3f(0F, 0F, -1F)).normalize();
        }

        Entity entity = MinecraftClient.getInstance().getCameraEntity();

        if (entity == null)
        {
            return new Vector3f(0F, 0F, 1F);
        }

        Vec3d look = entity.getRotationVec(1F);

        return new Vector3f((float) look.x, (float) look.y, (float) look.z).normalize();
    }

    /** The spot the crosshair lands on, or a point in open air in front of the view; null without a view */
    public static Vector3d point()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vector3d eye = eye();

        if (eye == null)
        {
            return null;
        }

        Vector3f forward = direction();
        Vector3d point = new Vector3d(eye).add(forward.x * OPEN_AIR, forward.y * OPEN_AIR, forward.z * OPEN_AIR);

        if (filmPanel() == null && mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS)
        {
            Vec3d hit = mc.crosshairTarget.getPos();

            return point.set(hit.x, hit.y, hit.z);
        }

        if (mc.world != null && mc.player != null)
        {
            Vec3d start = new Vec3d(eye.x, eye.y, eye.z);
            Vec3d end = start.add(forward.x * REACH, forward.y * REACH, forward.z * REACH);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

            if (hit.getType() == HitResult.Type.BLOCK)
            {
                point.set(hit.getPos().x, hit.getPos().y, hit.getPos().z);
            }
        }

        return point;
    }
}
