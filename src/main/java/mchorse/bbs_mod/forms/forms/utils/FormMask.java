package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A geometric mask a form is drawn through: whatever falls outside the shape is cut away (or,
 * inverted, whatever falls inside). The shape stands in the form's own space, on its XY plane,
 * so it moves, turns and scales with the form; its own offset, rotation and radius place it. A custom shape is a list of 3D points instead,
 * also in the form's space.
 */
public class FormMask extends ValueGroup
{
    public static final int NONE = 0;
    public static final int CIRCLE = 1;
    public static final int RECTANGLE = 2;
    public static final int CUSTOM = 3;

    private static final int CIRCLE_SEGMENTS = 48;

    /** How the form is cut: a shape on the screen through the stencil, or a plane through the form in 3D. */
    public static final int MODE_SCREEN_2D = 0;
    public static final int MODE_SLICE_3D = 1;

    public final ValueInt mode = new ValueInt("mask_mode", MODE_SCREEN_2D, MODE_SCREEN_2D, MODE_SLICE_3D);
    public final ValueInt type = new ValueInt("type", NONE, NONE, CUSTOM);
    public final ValueBoolean invert = new ValueBoolean("invert", false);
    /** Soft edge, as a fraction of the shape that fades out towards its border. */
    public final ValueFloat feather = new ValueFloat("feather", 0F, 0F, 1F);
    public final ValueFloat width = new ValueFloat("width", 1F, 0.001F, 1000F);
    public final ValueFloat height = new ValueFloat("height", 1F, 0.001F, 1000F);
    public final ValueFloat offsetX = new ValueFloat("offset_x", 0F);
    public final ValueFloat offsetY = new ValueFloat("offset_y", 0F);
    public final ValueFloat offsetZ = new ValueFloat("offset_z", 0F);
    /** Circle: the radius. */
    public final ValueFloat radius = new ValueFloat("radius", 0.5F, 0.001F, 1000F);
    /** Turn of the shape's plane, in degrees: pitch, yaw, and roll in the plane. Owned by the form. */
    public final ValueFloat rotationX;
    public final ValueFloat rotationY;
    public final ValueFloat rotationZ;
    /** Slide of the shape along its plane and stretch about its middle, over the placement. Owned by the form. */
    public final ValueFloat posX;
    public final ValueFloat posY;
    public final ValueFloat scaleX;
    public final ValueFloat scaleY;
    /** Custom shape: points as {@code x y z} triples separated by semicolons. */
    public final ValueString points = new ValueString("points", "");

    public FormMask(String id, ValueFloat rotationX, ValueFloat rotationY, ValueFloat rotationZ, ValueFloat posX, ValueFloat posY, ValueFloat scaleX, ValueFloat scaleY)
    {
        super(id);

        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.posX = posX;
        this.posY = posY;
        this.scaleX = scaleX;
        this.scaleY = scaleY;

        /* Authoring switches, like the hitbox: not tracks of the timeline */
        this.invisible();
        this.mode.invisible();
        this.type.invisible();
        this.invert.invisible();
        this.feather.invisible();
        this.width.invisible();
        this.height.invisible();
        this.offsetX.invisible();
        this.offsetY.invisible();
        this.offsetZ.invisible();
        this.radius.invisible();
        this.points.invisible();

        this.add(this.mode);
        this.add(this.type);
        this.add(this.invert);
        this.add(this.feather);
        this.add(this.width);
        this.add(this.height);
        this.add(this.offsetX);
        this.add(this.offsetY);
        this.add(this.offsetZ);
        this.add(this.radius);
        this.add(this.points);
    }

    /** Whether the 2D shape cuts the form: on screen mode, with a shape to cut by. */
    public boolean isActive()
    {
        return this.mode.get() == MODE_SCREEN_2D && this.type.get() != NONE && !this.outline().isEmpty();
    }

    public boolean isSlicing()
    {
        return this.mode.get() == MODE_SLICE_3D;
    }

    /**
     * The plane's turn as a matrix: yaw, then pitch, then roll applied to a point of the shape
     * (a point is rolled in its plane first, tipped, and turned about the vertical last).
     */
    public Matrix3f rotationMatrix()
    {
        return new Matrix3f()
            .rotateY((float) Math.toRadians(this.rotationY.get()))
            .rotateX((float) Math.toRadians(this.rotationX.get()))
            .rotateZ((float) Math.toRadians(this.rotationZ.get()));
    }

    /** The direction the plane faces, in the form's space. */
    public Vector3f normal()
    {
        return this.rotationMatrix().transform(new Vector3f(0F, 0F, 1F));
    }

    /** The shape's position, in the form's space. */
    public Vector3f position()
    {
        return new Vector3f(this.offsetX.get(), this.offsetY.get(), this.offsetZ.get());
    }

    /**
     * Local to the form's space: stretched and slid in the plane by the animation, turned by the
     * plane's rotation, then moved by the position.
     */
    public Vector3f toForm(float x, float y, float z)
    {
        Vector3f animated = new Vector3f(x * this.scaleX.get() + this.posX.get(), y * this.scaleY.get() + this.posY.get(), z);

        return this.rotationMatrix().transform(animated).add(this.position());
    }

    /** The way back of {@link #toForm}. A stretch of zero has no way back, and is read as a hair's width. */
    public Vector3f toLocal(float x, float y, float z)
    {
        Vector3f local = this.rotationMatrix().transpose().transform(new Vector3f(x, y, z).sub(this.position()));

        local.x = (local.x - this.posX.get()) / nonZero(this.scaleX.get());
        local.y = (local.y - this.posY.get()) / nonZero(this.scaleY.get());

        return local;
    }

    /**
     * The placement that puts the shape's middle at {@code center} (a point of the form's space)
     * with the animation's slide on top: the editor's handles move the placement, never the tracks.
     */
    public Vector3f placementFor(Vector3f center)
    {
        return new Vector3f(center).sub(this.rotationMatrix().transform(new Vector3f(this.posX.get(), this.posY.get(), 0F)));
    }

    private static float nonZero(float value)
    {
        return Math.abs(value) < 1.0E-4F ? Math.copySign(1.0E-4F, value == 0F ? 1F : value) : value;
    }

    /**
     * A point of the form's space with the pitch and yaw taken back, but not the roll: the frame
     * the roll turns in, around the placement (the rotation turns the slide with the shape). The
     * rotation handle reads its angle there.
     */
    public Vector3f toTilted(float x, float y, float z)
    {
        Matrix3f tilt = new Matrix3f()
            .rotateY((float) Math.toRadians(this.rotationY.get()))
            .rotateX((float) Math.toRadians(this.rotationX.get()));

        return tilt.transpose().transform(new Vector3f(x, y, z).sub(this.position()));
    }

    /** The custom shape's points, as they are stored: before the mask's rotation and offset. */
    public List<Vector3f> getPoints()
    {
        List<Vector3f> list = new ArrayList<>();

        for (String point : this.points.get().split(";"))
        {
            String[] parts = point.trim().split("[\\s,]+");

            if (parts.length < 2)
            {
                continue;
            }

            try
            {
                list.add(new Vector3f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), parts.length > 2 ? Float.parseFloat(parts[2]) : 0F));
            }
            catch (NumberFormatException e)
            {}
        }

        return list;
    }

    public void setPoints(List<Vector3f> list)
    {
        StringBuilder builder = new StringBuilder();

        for (Vector3f point : list)
        {
            if (builder.length() > 0)
            {
                builder.append("; ");
            }

            builder.append(point.x).append(' ').append(point.y).append(' ').append(point.z);
        }

        this.points.set(builder.toString());
    }

    /** The outline in the shape's own plane, before the rotation and the position. */
    public List<Vector3f> localOutline()
    {
        List<Vector3f> outline = new ArrayList<>();
        int type = this.type.get();

        if (type == CIRCLE)
        {
            float radius = this.radius.get();

            for (int i = 0; i < CIRCLE_SEGMENTS; i++)
            {
                double angle = Math.PI * 2D * i / CIRCLE_SEGMENTS;

                outline.add(new Vector3f((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, 0F));
            }
        }
        else if (type == RECTANGLE)
        {
            float hw = this.width.get() / 2F;
            float hh = this.height.get() / 2F;

            outline.add(new Vector3f(-hw, -hh, 0F));
            outline.add(new Vector3f(hw, -hh, 0F));
            outline.add(new Vector3f(hw, hh, 0F));
            outline.add(new Vector3f(-hw, hh, 0F));
        }
        else if (type == CUSTOM)
        {
            outline.addAll(this.getPoints());

            if (outline.size() < 3)
            {
                outline.clear();
            }
        }

        return outline;
    }

    /**
     * The outline of the shape in the form's space. Empty when there is nothing to draw. It is
     * a simple polygon: the circle and rectangle always, a custom shape as long as its edges do
     * not cross.
     */
    public List<Vector3f> outline()
    {
        List<Vector3f> outline = this.localOutline();

        for (Vector3f point : outline)
        {
            point.set(this.toForm(point.x, point.y, point.z));
        }

        return outline;
    }
}
