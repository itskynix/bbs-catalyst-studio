/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.cubic.RigBone
 *  mchorse.bbs_mod.cubic.model.ArmorSlot
 */
package mchorse.bbs_mod.camera.pov.hand.editor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.model.ArmorSlot;

public final class HandBoneUtils {
    private HandBoneUtils() {
    }

    public static HandBones collect(ModelInstance model) {
        if (model == null || model.getModel() == null) {
            return HandBones.EMPTY;
        }
        String mainRoot = HandBoneUtils.getRoot(model.getFpMain(), "right_arm");
        String offRoot = HandBoneUtils.getRoot(model.getFpOffhand(), "left_arm");
        LinkedHashSet<String> roots = new LinkedHashSet<String>();
        roots.add(mainRoot);
        roots.add(offRoot);
        LinkedHashMap<String, Integer> depths = new LinkedHashMap<String, Integer>();
        for (String name : model.getModel().getGroupKeysInHierarchyOrder()) {
            RigBone bone = model.getModel().getBone(name);
            int depth = HandBoneUtils.handDepth(bone, roots);
            if (depth < 0) continue;
            depths.put(name, depth);
        }
        if (depths.isEmpty()) {
            Set disabled = model.getDisabledBones();
            for (String name : model.getModel().getGroupKeysInHierarchyOrder()) {
                if (disabled != null && disabled.contains(name)) continue;
                depths.put(name, 0);
            }
        }
        return new HandBones(mainRoot, offRoot, depths);
    }

    private static int handDepth(RigBone bone, Set<String> roots) {
        int depth = 0;
        while (bone != null) {
            if (roots.contains(bone.getBoneName())) {
                return depth;
            }
            bone = bone.getParentBone();
            ++depth;
        }
        return -1;
    }

    private static String getRoot(ArmorSlot slot, String fallback) {
        return slot == null || slot.group == null || slot.group.isBlank() ? fallback : slot.group;
    }

    public record HandBones(String mainRoot, String offRoot, Map<String, Integer> depths) {
        private static final HandBones EMPTY = new HandBones("right_arm", "left_arm", Map.of());

        public boolean contains(String bone) {
            return this.depths.containsKey(bone);
        }
    }
}

