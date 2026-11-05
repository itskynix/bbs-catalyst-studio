package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Where the first-person arms sit in the view. Not the model's bones but a fixed rig laid over
 * them, the same for every hand model: each arm, and under it the arm's tip (where the POV body
 * parts hang), the item it holds and the armor sleeve over it. A bone's transform is on top of its
 * parent's and of the game's own first-person placement, so an untouched rig is the game's view.
 */
public class PovRig extends ValueGroup
{
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";

    /** Every bone, parents before their children, in the order the editor lists them. */
    public static final List<String> BONES = Collections.unmodifiableList(Arrays.asList(
        RIGHT_ARM, "right_arm_end", "right_arm_item", "armor_right_arm",
        LEFT_ARM, "left_arm_end", "left_arm_item", "armor_left_arm"
    ));

    public PovRig(String id)
    {
        super(id);

        for (String bone : BONES)
        {
            this.add(new Bone(bone));
        }
    }

    public Bone getBone(String id)
    {
        return this.get(id) instanceof Bone bone ? bone : null;
    }

    /** The arm a bone belongs to; an arm is its own. */
    public static String armOf(String bone)
    {
        return bone.contains("left") ? LEFT_ARM : RIGHT_ARM;
    }

    public static boolean isArm(String bone)
    {
        return bone.equals(RIGHT_ARM) || bone.equals(LEFT_ARM);
    }

    public static String endOf(boolean right)
    {
        return right ? "right_arm_end" : "left_arm_end";
    }

    public static String itemOf(boolean right)
    {
        return right ? "right_arm_item" : "left_arm_item";
    }

    public static String armorOf(boolean right)
    {
        return right ? "armor_right_arm" : "armor_left_arm";
    }

    public static class Bone extends ValueGroup
    {
        public final ValueBoolean visible = new ValueBoolean("visible", true);
        public final ValueTransform transform = new ValueTransform("transform", new Transform());

        public Bone(String id)
        {
            super(id);

            this.add(this.visible);
            this.add(this.transform);
        }
    }
}
