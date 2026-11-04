package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.IValueListener;

/**
 * The parts of a vehicle that open: doors, the hood, the trunk. Each one is a bone, the axis it
 * swings about and how far it swings open. A part swinging about its yaw axis is a door, and a
 * door has a seat behind it; one swinging about its pitch axis is a lid (hood or trunk).
 */
public class ValueVehicleParts extends ValueList<ValueVehicleParts.Part>
{
    public static final int AXIS_YAW = 0;
    public static final int AXIS_PITCH = 1;

    public ValueVehicleParts(String id)
    {
        super(id);
    }

    public Part add(String bone, int axis, float angle)
    {
        Part part = new Part("");

        part.bone.set(bone);
        part.axis.set(axis);
        part.angle.set(angle);

        this.preNotify();
        this.add(part);
        this.sync();
        this.postNotify();

        return part;
    }

    public void remove(Part part)
    {
        this.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.list.remove(part);
        this.sync();
        this.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    public Part find(String bone)
    {
        for (Part part : this.list)
        {
            if (part.bone.get().equals(bone))
            {
                return part;
            }
        }

        return null;
    }

    @Override
    protected Part create(String id)
    {
        return new Part(id);
    }

    public static class Part extends ValueGroup
    {
        public final ValueString bone = new ValueString("bone", "");
        public final ValueInt axis = new ValueInt("axis", AXIS_YAW, AXIS_YAW, AXIS_PITCH);
        /** Degrees; the sign says which way it swings */
        public final ValueFloat angle = new ValueFloat("angle", 75F, -180F, 180F);

        public Part(String id)
        {
            super(id);

            this.add(this.bone);
            this.add(this.axis);
            this.add(this.angle);
        }

        public boolean isDoor()
        {
            return this.axis.get() == AXIS_YAW;
        }
    }
}
