/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.util.Arm
 *  org.joml.Matrix4f
 *  org.joml.Quaternionfc
 *  org.joml.Vector3fc
 */
package mchorse.bbs_mod.camera.pov.hand;

import java.util.List;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public final class PovItemPose {
    public static final String MAIN_HAND = "Main Hand Item";
    public static final String OFF_HAND = "Off Hand Item";
    public static final List<String> BONES = List.of("Main Hand Item", "Off Hand Item");

    private PovItemPose() {
    }

    public static String bone(boolean leftHanded, Arm mainArm) {
        return leftHanded == (mainArm == Arm.LEFT) ? MAIN_HAND : OFF_HAND;
    }

    public static PoseTransform transform(Pose pose, String bone) {
        PoseTransform transform = pose == null ? null : (PoseTransform)pose.transforms.get(bone);
        return transform == null ? new PoseTransform() : transform;
    }

    public static void apply(MatrixStack matrices, PoseTransform transform) {
        matrices.translate(-transform.translate.x / 16.0f, transform.translate.y / 16.0f, transform.translate.z / 16.0f);
        matrices.multiply(transform.createRotation());
        matrices.scale(transform.scale.x, transform.scale.y, transform.scale.z);
    }

    public static Matrix4f createMatrix(Transform transform) {
        return new Matrix4f().translate(-transform.translate.x / 16.0f, transform.translate.y / 16.0f, transform.translate.z / 16.0f).rotate((Quaternionfc)transform.createRotation()).scale((Vector3fc)transform.scale);
    }
}

