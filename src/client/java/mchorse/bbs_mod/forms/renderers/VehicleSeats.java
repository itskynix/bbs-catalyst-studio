package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueVehicleParts;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The four seats of a vehicle: front left (the driver's), front right, rear left and rear right,
 * each a sitting plane in the actor's frame (blocks; front +Z, left +X, up from the actor's feet) -
 * the height the seated actor's hips rest on. Placed by hand in the vehicle IK tab, or found from
 * the model: its seat bones (a bench across the back seats two), then the seat behind each door,
 * then the middle of a quarter at a cushion's height. A vehicle with one row of seats has no rear
 * ones: every click boards the front row.
 */
public class VehicleSeats
{
    public static final int FL = 0;
    public static final int FR = 1;
    public static final int RL = 2;
    public static final int RR = 3;

    /** How far a seat's plane reaches each way from its middle, in blocks, as drawn */
    private static final float PLANE = 0.22F;
    private static final float EDGE = 0.014F;

    /** The seats found for a form, with what they were found from */
    private static final Map<ModelForm, Object[]> FOUND = new WeakHashMap<>();

    public static final class Layout
    {
        /** The sitting planes, or null where there is no seat */
        public final Vector3f[] seats = new Vector3f[4];
        /** The vehicle's middle, and which way its front and its left are (see {@link VehicleGeometry.Wheels}) */
        public final Vector3f middle = new Vector3f();
        public float front = 1F;
        public float left = 1F;
        /** Where the front row gives way to the rear one, along the front; minus infinity with one row */
        public float split = Float.NEGATIVE_INFINITY;

        /**
         * The seat a click at this point of the actor's frame boards: the one of its quarter, or the
         * nearest one when that quarter has none; -1 without a seat at all.
         */
        public int pick(Vector3f at)
        {
            boolean frontRow = (at.z - this.middle.z) * this.front >= this.split;
            boolean leftSide = (at.x - this.middle.x) * this.left >= 0F;
            int quarter = frontRow ? (leftSide ? FL : FR) : (leftSide ? RL : RR);

            if (this.seats[quarter] != null)
            {
                return quarter;
            }

            int nearest = -1;
            float best = Float.POSITIVE_INFINITY;

            for (int i = 0; i < 4; i++)
            {
                Vector3f seat = this.seats[i];

                if (seat == null)
                {
                    continue;
                }

                float distance = (seat.x - at.x) * (seat.x - at.x) + (seat.z - at.z) * (seat.z - at.z);

                if (distance < best)
                {
                    best = distance;
                    nearest = i;
                }
            }

            return nearest;
        }

        /** Whether a seat is on the vehicle's left */
        public boolean isLeft(int seat)
        {
            return seat == FL || seat == RL;
        }
    }

    /** The seats as they are now: set by hand, or found from the model */
    public static Layout layout(ModelForm form)
    {
        Layout found = found(form);

        if (!form.seatsCustom.get())
        {
            return found;
        }

        Layout layout = new Layout();

        layout.middle.set(found.middle);
        layout.front = found.front;
        layout.left = found.left;

        for (int i = 0; i < 4; i++)
        {
            if (form.seatEnabled[i].get())
            {
                layout.seats[i] = new Vector3f(form.seatX[i].get(), form.seatY[i].get(), form.seatZ[i].get());
            }
        }

        layout.split = split(layout);

        return keepInside(layout, form);
    }

    /**
     * No seat outside the vehicle's own box. A coordinate far beyond it (more than twice its size
     * away) was written in pixels, sixteen to a block, and is taken back; then every seat is held
     * inside the box, a little in from its sides.
     */
    private static Layout keepInside(Layout layout, ModelForm form)
    {
        VehicleGeometry.Wheels wheels = VehicleGeometry.wheels(form);
        Vector3f min = wheels.footMin;
        Vector3f max = wheels.footMax;

        for (Vector3f seat : layout.seats)
        {
            if (seat == null)
            {
                continue;
            }

            for (int axis = 0; axis < 3; axis++)
            {
                float extent = Math.max(max.get(axis) - min.get(axis), 0.5F);
                float value = seat.get(axis);

                if (value < min.get(axis) - 2F * extent || value > max.get(axis) + 2F * extent)
                {
                    value /= 16F;
                }

                float margin = Math.min(0.05F, extent / 4F);

                seat.setComponent(axis, MathUtils.clamp(value, min.get(axis) + margin, max.get(axis) - margin));
            }
        }

        return layout;
    }

    /** Halfway between the rows of seats set by hand, when there are two rows */
    private static float split(Layout layout)
    {
        float frontRow = Float.NaN;
        float rearRow = Float.NaN;

        for (int i = 0; i < 4; i++)
        {
            Vector3f seat = layout.seats[i];

            if (seat == null)
            {
                continue;
            }

            float row = (seat.z - layout.middle.z) * layout.front;

            if (i < 2)
            {
                frontRow = Float.isNaN(frontRow) ? row : Math.min(frontRow, row);
            }
            else
            {
                rearRow = Float.isNaN(rearRow) ? row : Math.max(rearRow, row);
            }
        }

        return Float.isNaN(frontRow) || Float.isNaN(rearRow) ? Float.NEGATIVE_INFINITY : (frontRow + rearRow) / 2F;
    }

    /** The seats the model gives, whether or not they are set by hand (what the tab fills them from) */
    public static Layout found(ModelForm form)
    {
        Model model = VehicleGeometry.getModel(form);
        List<ValueVehicleParts.Part> parts = VehicleGeometry.parts(form);
        StringBuilder key = new StringBuilder(form.boneBody.get()).append('\n').append(form.boneWheelFL.get());

        for (ValueVehicleParts.Part part : parts)
        {
            key.append('\n').append(part.bone.get()).append(':').append(part.axis.get());
        }

        String signature = key.toString();
        Object[] cached = FOUND.get(form);

        if (cached != null && cached[0] == model && signature.equals(cached[1]))
        {
            return (Layout) cached[2];
        }

        Layout layout = find(model, form, parts);

        FOUND.put(form, new Object[] {model, signature, layout});

        return layout;
    }

    private static Layout find(Model model, ModelForm form, List<ValueVehicleParts.Part> parts)
    {
        Layout layout = new Layout();
        VehicleGeometry.Wheels wheels = VehicleGeometry.wheels(form);
        Vector3f footMin = wheels.footMin;
        Vector3f footMax = wheels.footMax;
        Vector3f middle = VehicleGeometry.toActor(VehicleGeometry.center(model, form));

        if (model == null)
        {
            middle.set(footMin).add(footMax).mul(0.5F);
        }

        layout.middle.set(middle);
        layout.front = wheels.front;
        layout.left = wheels.left;

        float halfWidth = Math.max((footMax.x - footMin.x) / 2F, 0.3F);
        float halfLength = Math.max((footMax.z - footMin.z) / 2F, 0.5F);
        float cushion = footMin.y + (footMax.y - footMin.y) * 0.25F;
        List<Vector3f> bones = new ArrayList<>();
        List<Vector3f> doors = new ArrayList<>();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        if (model != null)
        {
            Map<String, String> parents = new HashMap<>();
            List<String> seats = new ArrayList<>();

            for (ModelGroup group : model.getAllGroups())
            {
                if (group.parent != null)
                {
                    parents.put(group.id, group.parent.id);
                }

                if (VehicleBoneDetector.isSeat(group.id))
                {
                    seats.add(group.id);
                }
            }

            for (String bone : seats)
            {
                /* A seat held twice (a pivot group and the cushion inside it) is one seat: the outer one */
                boolean inner = false;

                for (String other : seats)
                {
                    inner |= VehicleBoneDetector.isWithin(bone, other, parents::get);
                }

                if (inner)
                {
                    continue;
                }

                /* The cushion: the lower third of the seat's box (its back reaches up past it) */
                Vector3f point = VehicleGeometry.bounds(model, bone, min, max)
                    ? VehicleGeometry.toActor(new Vector3f((min.x + max.x) / 2F, min.y + (max.y - min.y) * 0.3F, (min.z + max.z) / 2F))
                    : VehicleGeometry.toActor(model.getGroup(bone).initial.translate);

                if (Math.abs(point.x - middle.x) < halfWidth * 0.25F)
                {
                    /* A bench across the middle seats one on each side */
                    bones.add(new Vector3f(middle.x + halfWidth * 0.45F, point.y, point.z));
                    bones.add(new Vector3f(middle.x - halfWidth * 0.45F, point.y, point.z));
                }
                else
                {
                    bones.add(point);
                }
            }

            for (ValueVehicleParts.Part part : parts)
            {
                if (!part.isDoor())
                {
                    continue;
                }

                Vector3f seat = VehicleGeometry.seat(model, form, part.bone.get());
                ModelGroup group = model.getGroup(part.bone.get());

                if (seat != null)
                {
                    doors.add(VehicleGeometry.toActor(seat));
                }
                else if (group != null)
                {
                    /* A door without a box of its own: in from its hinge most of the way to the middle */
                    Vector3f pivot = VehicleGeometry.toActor(group.initial.translate);

                    doors.add(new Vector3f(middle.x + (pivot.x - middle.x) * 0.45F, cushion, middle.z + (pivot.z - middle.z) * 0.45F));
                }
            }
        }

        /* Two rows when the seats spread along the vehicle, split halfway between the first and the last */
        float first = Float.POSITIVE_INFINITY;
        float last = Float.NEGATIVE_INFINITY;

        for (List<Vector3f> list : List.of(bones, doors))
        {
            for (Vector3f seat : list)
            {
                float row = (seat.z - middle.z) * layout.front;

                first = Math.min(first, row);
                last = Math.max(last, row);
            }
        }

        layout.split = last - first > 0.6F ? (first + last) / 2F : Float.NEGATIVE_INFINITY;

        boolean rows = layout.split != Float.NEGATIVE_INFINITY;

        for (int i = 0; i < 4; i++)
        {
            boolean frontRow = i < 2;
            float side = (i % 2 == 0 ? 1F : -1F) * layout.left;

            if (!frontRow && !rows)
            {
                continue;
            }

            /* The quarter's own seat (a seat bone before a door's), the one nearest its middle */
            float rowZ = middle.z + layout.front * (frontRow ? halfLength * 0.05F : -halfLength * 0.45F);
            Vector3f aim = new Vector3f(middle.x + side * halfWidth * 0.45F, 0F, rowZ);
            Vector3f seat = nearest(bones, layout, i, aim);

            if (seat == null)
            {
                seat = nearest(doors, layout, i, aim);
            }

            /* None there: the middle of the quarter, at a cushion's height */
            layout.seats[i] = seat != null ? new Vector3f(seat) : new Vector3f(aim.x, cushion, aim.z);
        }

        return keepInside(layout, form);
    }

    private static Vector3f nearest(List<Vector3f> seats, Layout layout, int quarter, Vector3f aim)
    {
        Vector3f nearest = null;
        float best = Float.POSITIVE_INFINITY;

        for (Vector3f seat : seats)
        {
            boolean frontRow = (seat.z - layout.middle.z) * layout.front >= layout.split;
            boolean leftSide = (seat.x - layout.middle.x) * layout.left >= 0F;
            float distance = (seat.x - aim.x) * (seat.x - aim.x) + (seat.z - aim.z) * (seat.z - aim.z);

            if (frontRow == quarter < 2 && leftSide == layout.isLeft(quarter) && distance < best)
            {
                best = distance;
                nearest = seat;
            }
        }

        return nearest;
    }

    /**
     * The sitting planes in the form editor's preview: a translucent square on each seat with its
     * edges and a mark toward the front, green for the driver's and blue for the others, the one the
     * gizmo moves ({@code highlight}) brighter. Drawn through the body (depth off), since they sit
     * inside the cabin. Positions are in the vehicle's frame, the frame the form is previewed in.
     */
    public static void renderGuide(MatrixStack stack, ModelForm form, int highlight)
    {
        Layout layout = layout(form);
        float lift = form.seatHeight.get();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 4; i++)
        {
            Vector3f seat = layout.seats[i];

            if (seat == null)
            {
                continue;
            }

            boolean lit = i == highlight;
            float r = i == FL ? 0.25F : 0.3F;
            float g = i == FL ? 0.95F : 0.62F;
            float b = i == FL ? 0.4F : 1F;
            float x = seat.x;
            float y = seat.y + lift;
            float z = seat.z;
            float edge = lit ? EDGE * 1.8F : EDGE;

            Draw.fillBox(builder, stack, x - PLANE, y - 0.003F, z - PLANE, x + PLANE, y + 0.003F, z + PLANE, r, g, b, lit ? 0.45F : 0.22F);

            Vector3f[] corners = {
                new Vector3f(x - PLANE, y, z - PLANE), new Vector3f(x + PLANE, y, z - PLANE),
                new Vector3f(x + PLANE, y, z + PLANE), new Vector3f(x - PLANE, y, z + PLANE)
            };

            for (int c = 0; c < 4; c++)
            {
                VehicleCameraGuide.line(builder, stack, corners[c], corners[(c + 1) % 4], edge, r, g, b);
            }

            /* Toward the front, the way the seated actor faces */
            float tip = PLANE + 0.16F;

            VehicleCameraGuide.line(builder, stack, new Vector3f(x, y, z + PLANE * layout.front), new Vector3f(x, y, z + tip * layout.front), edge, r, g, b);
            VehicleCameraGuide.line(builder, stack, new Vector3f(x - 0.07F, y, z + (tip - 0.07F) * layout.front), new Vector3f(x, y, z + tip * layout.front), edge, r, g, b);
            VehicleCameraGuide.line(builder, stack, new Vector3f(x + 0.07F, y, z + (tip - 0.07F) * layout.front), new Vector3f(x, y, z + tip * layout.front), edge, r, g, b);
        }

        Bufs.draw(builder);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
