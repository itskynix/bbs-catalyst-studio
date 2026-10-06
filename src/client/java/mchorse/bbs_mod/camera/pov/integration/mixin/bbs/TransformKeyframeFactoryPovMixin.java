/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.DataStorageUtils
 *  mchorse.bbs_mod.data.types.BaseType
 *  mchorse.bbs_mod.data.types.ListType
 *  mchorse.bbs_mod.utils.keyframes.factories.TransformKeyframeFactory
 *  mchorse.bbs_mod.utils.pose.Transform
 *  org.joml.Vector4f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.utils.keyframes.factories.TransformKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TransformKeyframeFactory.class}, remap=false)
public abstract class TransformKeyframeFactoryPovMixin {
    @Inject(method={"fromData"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$readLegacyLayout(BaseType data, CallbackInfoReturnable<Transform> info) {
        if (data == null || !data.isList()) {
            return;
        }
        Vector4f old = DataStorageUtils.vector4fFromData((ListType)data.asList());
        Transform transform = new Transform();
        boolean genericDefault = old.z == 0.0f && old.w == 1.0f;
        float scale = genericDefault ? 1.0f : old.z;
        float rotation = genericDefault ? 0.0f : (float)Math.toRadians(old.w);
        transform.translate.set(old.x, old.y, 0.0f);
        transform.scale.set(scale, scale, 1.0f);
        transform.rotate.set(0.0f, 0.0f, rotation);
        info.setReturnValue(transform);
    }
}

