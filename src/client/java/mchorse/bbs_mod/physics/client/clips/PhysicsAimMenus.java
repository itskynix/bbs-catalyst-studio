package mchorse.bbs_mod.physics.client.clips;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.physics.PhysicsAim;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * The right click entries the physics clips' fields share (BBS CS): take the point the crosshair
 * lands on, or turn a direction the way the view looks. See {@link PhysicsAim} for which view that is.
 */
public final class PhysicsAimMenus
{
    private PhysicsAimMenus()
    {}

    /** "Take the point the crosshair looks at" into {@code value} */
    public static void point(ContextMenuManager menu, ValuePoint value, Runnable refill)
    {
        menu.action(Icons.MOVE_TO, UIKeys.FILM_PHYSICS_AIM_POINT, () ->
        {
            Vector3d point = PhysicsAim.point();

            if (point != null)
            {
                BaseValue.edit(value, (v) -> v.set(new Point(point.x, point.y, point.z)));
                refill.run();
            }
        });
    }

    /**
     * "Take the view's direction" into {@code value}, keeping its length when it has one, so a
     * direction that also carries a strength (a kick) keeps its strength.
     */
    public static void direction(ContextMenuManager menu, ValuePoint value, Runnable after)
    {
        menu.action(Icons.ARROW_RIGHT, UIKeys.FILM_PHYSICS_AIM_DIRECTION, () ->
        {
            Vector3f forward = PhysicsAim.direction();
            Point old = value.get();
            double length = old == null ? 0D : Math.sqrt(old.x * old.x + old.y * old.y + old.z * old.z);
            double scale = length < 1.0e-4D ? 1D : length;

            BaseValue.edit(value, (v) -> v.set(new Point(forward.x * scale, forward.y * scale, forward.z * scale)));
            after.run();
        });
    }
}
