/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.FilmControllerContext
 *  mchorse.bbs_mod.film.FilmEntityRenderer
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.utils.interps.Lerps
 *  mchorse.bbs_mod.utils.joml.Vectors
 *  org.joml.Vector3d
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovEntityFireRenderer;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FilmEntityRenderer.class}, remap=false)
public class FilmEntityRendererFirePovMixin {
    @Inject(method={"renderEntity"}, at={@At(value="TAIL")})
    private static void bbsPov$renderEntityFire(FilmControllerContext context, CallbackInfo info) {
        if (context == null || context.map != null || context.consumers == null || context.camera == null || context.stack == null) {
            return;
        }
        float fireIntensity = PovEntityFireRenderer.resolveFireIntensity(context);
        if (fireIntensity <= 0.001f) {
            return;
        }
        IEntity entity = context.entity;
        if (entity == null) {
            return;
        }
        Vector3d position = Vectors.TEMP_3D.set(Lerps.lerp((double)entity.getPrevX(), (double)entity.getX(), (double)context.transition), Lerps.lerp((double)entity.getPrevY(), (double)entity.getY(), (double)context.transition), Lerps.lerp((double)entity.getPrevZ(), (double)entity.getZ(), (double)context.transition));
        boolean relative = context.replay != null && context.relative;
        Vector3d origin = relative ? context.replay.getRelativeOrigin() : new Vector3d(context.camera.getPos().x, context.camera.getPos().y, context.camera.getPos().z);
        double dx = position.x - origin.x;
        double dy = position.y - origin.y;
        double dz = position.z - origin.z;
        float width = 0.6f;
        float height = 1.8f;
        context.stack.push();
        context.stack.translate(dx, dy, dz);
        PovEntityFireRenderer.render(context.stack, context.consumers, context.camera, width, height, fireIntensity);
        context.stack.pop();
    }
}

