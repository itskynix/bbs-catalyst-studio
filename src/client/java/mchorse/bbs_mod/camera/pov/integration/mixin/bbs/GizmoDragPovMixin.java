/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.utils.GizmoDrag
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.integration.access.bbs.IGizmoDragFirstPerson;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={GizmoDrag.class}, remap=false)
public abstract class GizmoDragPovMixin
implements IGizmoDragFirstPerson {
    @Unique
    private Vector3f bbsPov$rotationPivot;

    @Override
    public void bbsPov$setRotationPivot(Vector3f pivot) {
        this.bbsPov$rotationPivot = pivot != null ? new Vector3f((Vector3fc)pivot) : null;
    }

    @Override
    public Vector3f bbsPov$getRotationPivot() {
        return this.bbsPov$rotationPivot;
    }
}

