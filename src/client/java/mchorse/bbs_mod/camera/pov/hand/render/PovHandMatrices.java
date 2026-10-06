/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.IModel
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.cubic.RigBone
 *  mchorse.bbs_mod.cubic.model.ArmorSlot
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.forms.renderers.utils.MatrixCache
 *  mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace
 *  mchorse.bbs_mod.utils.MatrixStackUtils
 *  mchorse.bbs_mod.utils.pose.Pose
 *  mchorse.bbs_mod.utils.pose.PoseTransform
 *  mchorse.bbs_mod.utils.pose.Transform
 *  mchorse.bbs_mod.utils.pose.Transform$RotationMode
 *  net.minecraft.client.util.math.MatrixStack
 *  org.joml.Matrix3f
 *  org.joml.Matrix3fc
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionfc
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 */
package mchorse.bbs_mod.camera.pov.hand.render;

import mchorse.bbs_mod.camera.pov.hand.PovItemPose;
import java.util.HashMap;
import java.util.Map;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class PovHandMatrices {
    private static final Map<String, Entry> BONES = new HashMap<String, Entry>();
    private static final Map<String, ItemEntry> ITEMS = new HashMap<String, ItemEntry>();
    private static final Map<String, Matrix4f> BODY_PART_BASES = new HashMap<String, Matrix4f>();
    private static Pose capturedHandPose;

    private PovHandMatrices() {
    }

    public static void clear() {
        BONES.clear();
        ITEMS.clear();
        BODY_PART_BASES.clear();
        capturedHandPose = null;
    }

    public static void captureItem(String bone, Matrix4f origin, Matrix4f full) {
        if (bone != null && origin != null && full != null) {
            ITEMS.put(bone, new ItemEntry(new Matrix4f((Matrix4fc)full), new Matrix4f((Matrix4fc)origin)));
        }
    }

    public static void capture(ModelInstance instance, ArmorSlot slot, Matrix4f renderBase, MatrixCache matrices, Pose pose) {
        if (instance == null || slot == null || slot.group == null || renderBase == null || matrices == null) {
            return;
        }
        IModel model = instance.getModel();
        if (model == null) {
            return;
        }
        Pose renderedPose = pose == null ? new Pose() : pose.copy();
        capturedHandPose = renderedPose.copy();
        for (String groupKey : model.getGroupKeysInHierarchyOrder()) {
            MatrixCacheEntry entry;
            RigBone bone = model.getBone(groupKey);
            if (!PovHandMatrices.belongsTo(bone, slot.group) || (entry = matrices.get(groupKey)) == null || entry.matrix() == null || entry.origin() == null) continue;
            BONES.put(groupKey, new Entry(new Matrix4f((Matrix4fc)renderBase).mul((Matrix4fc)entry.matrix()), new Matrix4f((Matrix4fc)renderBase).mul((Matrix4fc)entry.origin()), instance, new Matrix4f((Matrix4fc)renderBase), renderedPose.copy(), entry.evaluatedRotation() == null ? null : new Vector3f((Vector3fc)entry.evaluatedRotation())));
        }
    }

    public static void captureBodyPart(String path, ModelFormRenderer renderer, IEntity entity, MatrixStack parentAttachment, Transform partTransform) {
        ModelInstance instance;
        ModelInstance modelInstance = instance = renderer == null ? null : renderer.getModel();
        if (instance == null || instance.getModel() == null || parentAttachment == null || path == null || path.isBlank()) {
            return;
        }
        Matrix4f baseMatrix = new Matrix4f((Matrix4fc)parentAttachment.peek().getPositionMatrix());
        BODY_PART_BASES.put(path, baseMatrix);
        MatrixStack renderStack = new MatrixStack();
        renderStack.peek().getPositionMatrix().set((Matrix4fc)baseMatrix);
        if (partTransform != null) {
            MatrixStackUtils.applyTransform((MatrixStack)renderStack, (Transform)partTransform);
        }
        MatrixCache rendered = new MatrixCache();
        renderer.collectMatrices(entity, renderStack, rendered, "", 0.0f);
        MatrixCacheEntry root = rendered.get("");
        if (root != null && root.matrix() != null) {
            ItemEntry itemEntry = new ItemEntry(new Matrix4f((Matrix4fc)root.matrix()), baseMatrix);
            ITEMS.put(path, itemEntry);
        }
        MatrixCache local = new MatrixCache();
        instance.captureMatrices(local);
        Pose pose = renderer.getPose();
        Pose capturedPose = pose == null ? new Pose() : pose.copy();
        for (String bone : local.keySet()) {
            MatrixCacheEntry worldEntry = rendered.get(bone);
            MatrixCacheEntry localEntry = local.get(bone);
            if (worldEntry.matrix() == null || worldEntry.origin() == null || localEntry.matrix() == null) continue;
            Matrix4f renderBase = new Matrix4f((Matrix4fc)worldEntry.matrix()).mul((Matrix4fc)new Matrix4f((Matrix4fc)localEntry.matrix()).invert());
            Entry entry = new Entry(new Matrix4f((Matrix4fc)worldEntry.matrix()), new Matrix4f((Matrix4fc)worldEntry.origin()), instance, renderBase, capturedPose.copy(), worldEntry.evaluatedRotation() == null ? null : new Vector3f((Vector3fc)worldEntry.evaluatedRotation()));
            BONES.put(path + "/" + bone, entry);
        }
    }

    private static boolean belongsTo(RigBone bone, String root) {
        for (RigBone current = bone; current != null; current = current.getParentBone()) {
            if (!root.equals(current.getBoneName())) continue;
            return true;
        }
        return false;
    }

    public static Matrix4f getFull(String bone) {
        Entry entry = BONES.get(bone);
        ItemEntry item = ITEMS.get(bone);
        return entry != null ? new Matrix4f((Matrix4fc)entry.full) : (item == null ? null : new Matrix4f((Matrix4fc)item.full));
    }

    public static Matrix4f getOrigin(String bone) {
        Entry entry = BONES.get(bone);
        ItemEntry item = ITEMS.get(bone);
        return entry != null ? new Matrix4f((Matrix4fc)entry.origin) : (item == null ? null : new Matrix4f((Matrix4fc)item.origin));
    }

    public static Pose getCapturedPose() {
        return capturedHandPose == null ? null : capturedHandPose.copy();
    }

    public static Matrix4f getForSpace(String bone, TransformSpace space) {
        Entry entry = BONES.get(bone);
        if (entry == null) {
            return PovHandMatrices.getItemForSpace(bone, space);
        }
        if (space == TransformSpace.PARENT) {
            return new Matrix4f((Matrix4fc)entry.origin);
        }
        Matrix4f result = new Matrix4f((Matrix4fc)entry.full);
        if (space == TransformSpace.GLOBAL) {
            PovHandMatrices.replaceBasis(result, entry.renderBase);
        } else if (space == TransformSpace.VIEW || space == TransformSpace.WORLD) {
            Vector3f position = result.getTranslation(new Vector3f());
            result.identity().setTranslation((Vector3fc)position);
        }
        return MatrixStackUtils.stripScale((Matrix4f)result);
    }
    private static Matrix4f getItemForSpace(String bone, TransformSpace space) {
        ItemEntry item = ITEMS.get(bone);
        if (item == null) {
            return null;
        }
        Matrix4f result = space == TransformSpace.PARENT ? (BODY_PART_BASES.containsKey(bone) ? new Matrix4f((Matrix4fc)BODY_PART_BASES.get(bone)) : new Matrix4f((Matrix4fc)item.origin)) : new Matrix4f((Matrix4fc)item.full);
        if (space == TransformSpace.GLOBAL) {
            Matrix4f base = BODY_PART_BASES.get(bone);
            if (base != null) {
                PovHandMatrices.replaceBasis(result, base);
            } else {
                Vector3f position = result.getTranslation(new Vector3f());
                result.identity().setTranslation((Vector3fc)position);
            }
        } else if (space == TransformSpace.VIEW || space == TransformSpace.WORLD) {
            Vector3f position = result.getTranslation(new Vector3f());
            result.identity().setTranslation((Vector3fc)position);
        }
        return MatrixStackUtils.stripScale((Matrix4f)result);
    }

    public static Matrix3f getBasisForSpace(String bone, TransformSpace space) {
        Matrix4f matrix = PovHandMatrices.getForSpace(bone, space);
        return matrix == null ? null : PovHandMatrices.basis(matrix);
    }

    public static Matrix3f getGlobalBasis(String bone) {
        Entry entry = BONES.get(bone);
        if (entry == null && ITEMS.containsKey(bone)) {
            Matrix4f base = BODY_PART_BASES.get(bone);
            return base != null ? PovHandMatrices.basis(base) : new Matrix3f();
        }
        return entry == null ? null : PovHandMatrices.basis(entry.renderBase);
    }

    public static Vector3f getAdditiveRotationBase(String bone, Transform editedTrack) {
        PoseTransform poseTrack;
        Entry entry;
        block3: {
            block2: {
                entry = BONES.get(bone);
                if (entry == null || entry.evaluatedRotation == null || !(editedTrack instanceof PoseTransform)) break block2;
                poseTrack = (PoseTransform)editedTrack;
                if (poseTrack.rotationMode != Transform.RotationMode.QUATERNION && poseTrack.fix == 0.0f) break block3;
            }
            return null;
        }
        return new Vector3f((Vector3fc)entry.evaluatedRotation).sub((Vector3fc)poseTrack.rotate);
    }

    private static void replaceBasis(Matrix4f target, Matrix4f source) {
        Vector3f position = target.getTranslation(new Vector3f());
        Matrix3f rotation = PovHandMatrices.basis(source);
        target.set((Matrix3fc)rotation).setTranslation((Vector3fc)position);
    }

    private static Matrix3f basis(Matrix4f matrix) {
        return MatrixStackUtils.stripScale((Matrix4f)new Matrix4f((Matrix4fc)matrix)).get3x3(new Matrix3f());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static synchronized Matrix4f evaluateFull(String bone, Transform baseline, Transform edited) {
        Pose pose;
        Entry context = BONES.get(bone);
        ItemEntry item = ITEMS.get(bone);
        Matrix4f attachment = BODY_PART_BASES.get(bone);
        if (attachment != null && edited != null && context == null) {
            MatrixStack stack = new MatrixStack();
            MatrixStackUtils.multiply((MatrixStack)stack, (Matrix4f)attachment);
            MatrixStackUtils.applyTransform((MatrixStack)stack, (Transform)edited);
            return MatrixStackUtils.stripScale((Matrix4f)new Matrix4f((Matrix4fc)stack.peek().getPositionMatrix()));
        }
        if (context == null && item != null && edited != null) {
            return MatrixStackUtils.stripScale((Matrix4f)new Matrix4f((Matrix4fc)item.origin).mul((Matrix4fc)PovItemPose.createMatrix(edited)));
        }
        ModelInstance instance = context == null ? null : context.instance;
        Matrix4f renderBase = context == null ? null : context.renderBase;
        Pose pose2 = pose = context == null ? null : context.pose;
        if (bone == null || baseline == null || edited == null || instance == null || instance.getModel() == null || renderBase == null || pose == null) {
            return PovHandMatrices.getFull(bone);
        }
        IModel model = instance.getModel();
        String modelBone = bone.contains("/") ? bone.substring(bone.lastIndexOf(47) + 1) : bone;
        Pose working = pose.copy();
        PoseTransform transform = working.transforms.computeIfAbsent(modelBone, ignored -> new PoseTransform());
        transform.translate.add(edited.translate.x - baseline.translate.x, edited.translate.y - baseline.translate.y, edited.translate.z - baseline.translate.z);
        Transform rotationDelta = new Transform();
        rotationDelta.setModeQuaternion();
        rotationDelta.quat.set((Quaternionfc)baseline.createRotation()).invert().mul((Quaternionfc)edited.createRotation());
        transform.addRotation(rotationDelta);
        transform.scale.mul(PovHandMatrices.ratio(edited.scale.x, baseline.scale.x), PovHandMatrices.ratio(edited.scale.y, baseline.scale.y), PovHandMatrices.ratio(edited.scale.z, baseline.scale.z));
        try {
            model.resetPose();
            model.applyPose(working);
            MatrixCache matrices = new MatrixCache();
            instance.captureMatrices(matrices);
            MatrixCacheEntry entry = matrices.get(modelBone);
            if (entry == null || entry.matrix() == null) {
                Matrix4f matrix4f = PovHandMatrices.getFull(bone);
                return matrix4f;
            }
            Matrix4f matrix4f = MatrixStackUtils.stripScale((Matrix4f)new Matrix4f((Matrix4fc)renderBase).mul((Matrix4fc)entry.matrix()));
            return matrix4f;
        }
        finally {
            model.resetPose();
            model.applyPose(pose);
        }
    }

    private static float ratio(float value, float baseline) {
        return Math.abs(baseline) < 1.0E-6f ? 1.0f : value / baseline;
    }

    private record ItemEntry(Matrix4f full, Matrix4f origin) {
    }

    private record Entry(Matrix4f full, Matrix4f origin, ModelInstance instance, Matrix4f renderBase, Pose pose, Vector3f evaluatedRotation) {
    }
}

