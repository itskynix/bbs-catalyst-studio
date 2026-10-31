package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.forms.forms.utils.FormMask;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing a form's mask by hand in the form editor's viewport: the handles on the shape's border
 * are picked and dragged with the mouse, and a custom shape grows by clicking points into the scene
 * (the pen). The state is one for the whole editor - there is a single viewport and a single mouse.
 *
 * <p>The viewport hands in its view as one matrix, {@link #setView}, taking the form's space to the
 * clip space of the area it is drawn in. Everything here goes through it: a handle is projected to
 * the screen to be picked, and the mouse is unprojected into a ray in the form's space, which meets
 * the plane the dragged handle stands on.</p>
 */
public class MaskEditing
{
    public enum Kind
    {
        CENTER, RADIUS, CORNER, ROTATE, POINT
    }

    public static class Handle
    {
        public final Kind kind;
        public final int index;
        public final Vector3f position;
        /** Where the stalk of a handle starts, or null. */
        public final Vector3f link;

        public Handle(Kind kind, int index, Vector3f position, Vector3f link)
        {
            this.kind = kind;
            this.index = index;
            this.position = position;
            this.link = link;
        }

        public boolean is(Kind kind, int index)
        {
            return this.kind == kind && this.index == index;
        }
    }

    /** The pen is on: a click on the scene adds a point to a custom shape. */
    public static boolean pen;
    /** The custom shape's point that is selected, or -1. */
    public static int selected = -1;

    private static final float PICK_RADIUS = 12F;
    private static final float STALK = 0.25F;

    private static final Matrix4f view = new Matrix4f();
    private static final Area area = new Area();
    private static boolean ready;

    private static Kind dragKind;
    private static int dragIndex;
    private static boolean dragging;
    private static final Vector3f grab = new Vector3f();

    private static Kind hoverKind;
    private static int hoverIndex;

    /** The form space to clip space matrix and the area it is drawn in; called as the viewport draws. */
    public static void setView(Matrix4f matrix, Area viewport)
    {
        view.set(matrix);
        area.copy(viewport);

        ready = true;
    }

    public static void reset()
    {
        pen = false;
        selected = -1;
        dragging = false;
        hoverKind = null;
        ready = false;
    }

    public static List<Handle> handles(FormMask mask)
    {
        List<Handle> list = new ArrayList<>();
        int type = mask.type.get();

        if (type == FormMask.CIRCLE)
        {
            list.add(new Handle(Kind.CENTER, 0, mask.toForm(0F, 0F, 0F), null));
            list.add(new Handle(Kind.RADIUS, 0, mask.toForm(mask.radius.get(), 0F, 0F), null));
        }
        else if (type == FormMask.RECTANGLE)
        {
            float hw = mask.width.get() / 2F;
            float hh = mask.height.get() / 2F;

            list.add(new Handle(Kind.CENTER, 0, mask.toForm(0F, 0F, 0F), null));

            for (int i = 0; i < 4; i++)
            {
                list.add(new Handle(Kind.CORNER, i, mask.toForm(corner(i, hw, hh)[0], corner(i, hw, hh)[1], 0F), null));
            }

            list.add(new Handle(Kind.ROTATE, 0, mask.toForm(0F, hh + STALK, 0F), mask.toForm(0F, hh, 0F)));
        }
        else if (type == FormMask.CUSTOM)
        {
            List<Vector3f> points = mask.getPoints();

            for (int i = 0; i < points.size(); i++)
            {
                Vector3f point = points.get(i);

                list.add(new Handle(Kind.POINT, i, mask.toForm(point.x, point.y, point.z), null));
            }
        }

        return list;
    }

    /** Corner 0 to 3 go round from the bottom left. */
    private static float[] corner(int i, float hw, float hh)
    {
        return new float[] {i == 1 || i == 2 ? hw : -hw, i >= 2 ? hh : -hh};
    }

    /** Whether the handle is the one under the mouse, being dragged, or the selected point. */
    public static boolean isLit(Handle handle)
    {
        if (dragging)
        {
            return handle.is(dragKind, dragIndex);
        }

        return handle.kind == Kind.POINT && handle.index == selected || hoverKind != null && handle.is(hoverKind, hoverIndex);
    }

    /** Where on screen a point of the form's space is, or null when it is behind the camera. */
    private static float[] project(Vector3f point)
    {
        Vector4f clip = view.transform(new Vector4f(point.x, point.y, point.z, 1F));

        if (clip.w <= 0F)
        {
            return null;
        }

        float x = clip.x / clip.w;
        float y = clip.y / clip.w;

        return new float[] {area.x + (x * 0.5F + 0.5F) * area.w, area.y + (1F - (y * 0.5F + 0.5F)) * area.h};
    }

    /**
     * The mouse's ray meeting the mask's plane - the one through {@code point}, facing where the
     * mask faces, whichever way it is turned - in the form's space, or null when the ray runs
     * along it.
     */
    private static Vector3f hit(UIContext context, FormMask mask, Vector3f point)
    {
        float x = (context.mouseX - area.x) / (float) area.w * 2F - 1F;
        float y = 1F - (context.mouseY - area.y) / (float) area.h * 2F;
        Matrix4f inverse = new Matrix4f(view).invert();
        Vector3f near = inverse.transformProject(new Vector3f(x, y, -1F));
        Vector3f far = inverse.transformProject(new Vector3f(x, y, 1F));
        Vector3f direction = far.sub(near, new Vector3f());
        Vector3f normal = mask.normal();
        float along = normal.dot(direction);

        if (Math.abs(along) < 1.0E-6F)
        {
            return null;
        }

        float t = normal.dot(point.sub(near, new Vector3f())) / along;

        return near.add(direction.mul(t));
    }

    private static Handle pick(UIContext context, FormMask mask)
    {
        Handle best = null;
        float bestDistance = PICK_RADIUS * PICK_RADIUS;

        for (Handle handle : handles(mask))
        {
            float[] screen = project(handle.position);

            if (screen == null)
            {
                continue;
            }

            float dx = screen[0] - context.mouseX;
            float dy = screen[1] - context.mouseY;
            float distance = dx * dx + dy * dy;

            if (distance <= bestDistance)
            {
                bestDistance = distance;
                best = handle;
            }
        }

        return best;
    }

    /**
     * A click in the viewport. Left on a handle takes it, left on the scene with the pen on adds a
     * point, right on a point removes it. Returns whether the click was used.
     */
    public static boolean mouseClicked(UIContext context, FormMask mask)
    {
        if (!ready || !area.isInside(context))
        {
            return false;
        }

        Handle handle = pick(context, mask);

        if (context.mouseButton == 1)
        {
            if (handle != null && handle.kind == Kind.POINT)
            {
                removePoint(mask, handle.index);

                return true;
            }

            return false;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        if (handle != null)
        {
            Vector3f at = hit(context, mask, handle.position);

            if (at == null)
            {
                return false;
            }

            grab.set(handle.position).sub(at);

            dragKind = handle.kind;
            dragIndex = handle.index;
            dragging = true;

            if (handle.kind == Kind.POINT)
            {
                selected = handle.index;
            }

            return true;
        }

        if (pen && mask.type.get() == FormMask.CUSTOM)
        {
            Vector3f at = hit(context, mask, mask.position());

            if (at == null)
            {
                return false;
            }

            List<Vector3f> points = mask.getPoints();
            Vector3f local = mask.toLocal(at.x, at.y, at.z);

            points.add(local);
            mask.setPoints(points);

            selected = points.size() - 1;
            dragKind = Kind.POINT;
            dragIndex = selected;
            dragging = true;
            grab.set(0F);

            return true;
        }

        return false;
    }

    /** Every frame: lights the handle under the mouse, and moves the one being dragged. */
    public static void update(UIContext context, FormMask mask)
    {
        if (!ready)
        {
            return;
        }

        if (!dragging)
        {
            Handle handle = area.isInside(context) ? pick(context, mask) : null;

            hoverKind = handle == null ? null : handle.kind;
            hoverIndex = handle == null ? 0 : handle.index;

            return;
        }

        Handle dragged = null;

        for (Handle handle : handles(mask))
        {
            if (handle.is(dragKind, dragIndex))
            {
                dragged = handle;
            }
        }

        if (dragged == null)
        {
            dragging = false;

            return;
        }

        Vector3f at = hit(context, mask, dragged.position);

        if (at != null)
        {
            drag(mask, dragged, at.add(grab));
        }
    }

    public static void mouseReleased()
    {
        dragging = false;
    }

    public static boolean isDragging()
    {
        return dragging;
    }

    /** Moves what the handle stands for to where the mouse is, a point of the form's space. */
    private static void drag(FormMask mask, Handle handle, Vector3f target)
    {
        switch (handle.kind)
        {
            case CENTER:
                Vector3f placement = mask.placementFor(target);

                mask.offsetX.set(placement.x);
                mask.offsetY.set(placement.y);
                mask.offsetZ.set(placement.z);

                break;
            case RADIUS:
                /* Measured before the stretch, which the radius is under */
                Vector3f onCircle = mask.toLocal(target.x, target.y, target.z);

                mask.radius.set(Math.max(0.001F, (float) Math.sqrt(onCircle.x * onCircle.x + onCircle.y * onCircle.y)));

                break;
            case ROTATE:
                /* The roll turns in the plane as tipped and turned by pitch and yaw */
                Vector3f tilted = mask.toTilted(target.x, target.y, target.z);

                /* Read around the shape's middle, which the animation's slide (turned with the roll) moved off the pivot */
                tilted.sub(new Matrix3f().rotateZ((float) Math.toRadians(mask.rotationZ.get())).transform(new Vector3f(mask.posX.get(), mask.posY.get(), 0F)));

                float angle = (float) Math.toDegrees(Math.atan2(tilted.y, tilted.x)) - 90F;

                while (angle > 180F)
                {
                    angle -= 360F;
                }

                while (angle < -180F)
                {
                    angle += 360F;
                }

                mask.rotationZ.set(angle);

                break;
            case CORNER:
                /* The opposite corner stays where it is: the centre moves to between the two */
                float hw = mask.width.get() / 2F;
                float hh = mask.height.get() / 2F;
                float[] opposite = corner((handle.index + 2) % 4, hw, hh);
                Vector3f local = mask.toLocal(target.x, target.y, target.z);
                Vector3f center = mask.placementFor(mask.toForm((opposite[0] + local.x) / 2F, (opposite[1] + local.y) / 2F, 0F));

                mask.width.set(Math.max(0.001F, Math.abs(local.x - opposite[0])));
                mask.height.set(Math.max(0.001F, Math.abs(local.y - opposite[1])));
                mask.offsetX.set(center.x);
                mask.offsetY.set(center.y);
                mask.offsetZ.set(center.z);

                break;
            case POINT:
                List<Vector3f> points = mask.getPoints();

                if (handle.index < points.size())
                {
                    Vector3f raw = mask.toLocal(target.x, target.y, target.z);

                    points.set(handle.index, raw);
                    mask.setPoints(points);
                }

                break;
        }
    }

    public static void removePoint(FormMask mask, int index)
    {
        List<Vector3f> points = mask.getPoints();

        if (index >= 0 && index < points.size())
        {
            points.remove(index);
            mask.setPoints(points);
        }

        selected = -1;
        dragging = false;
    }

    public static void removeSelected(FormMask mask)
    {
        removePoint(mask, selected);
    }
}
