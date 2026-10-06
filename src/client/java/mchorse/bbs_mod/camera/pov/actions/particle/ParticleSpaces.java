/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.entities.MCEntity
 *  mchorse.bbs_mod.utils.interps.Lerps
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.util.math.Vec3d
 */
package mchorse.bbs_mod.camera.pov.actions.particle;

import java.util.Random;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public final class ParticleSpaces {
    private ParticleSpaces() {
    }

    public static Vec3d lerpPos(IEntity entity, float tickDelta) {
        return new Vec3d(Lerps.lerp((double)entity.getPrevX(), (double)entity.getX(), (double)tickDelta), Lerps.lerp((double)entity.getPrevY(), (double)entity.getY(), (double)tickDelta), Lerps.lerp((double)entity.getPrevZ(), (double)entity.getZ(), (double)tickDelta));
    }

    public static float lerpYaw(IEntity entity, float tickDelta) {
        return (float)Lerps.lerpYaw((double)entity.getPrevHeadYaw(), (double)entity.getHeadYaw(), (double)tickDelta);
    }

    public static float width(IEntity entity) {
        MCEntity mc;
        Entity entity2;
        if (entity instanceof MCEntity && (entity2 = (mc = (MCEntity)entity).getMcEntity()) instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity2;
            return living.getWidth();
        }
        return 0.6f;
    }

    public static float height(IEntity entity) {
        MCEntity mc;
        Entity entity2;
        if (entity instanceof MCEntity && (entity2 = (mc = (MCEntity)entity).getMcEntity()) instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity2;
            return living.getHeight();
        }
        return 1.8f;
    }

    public static Vec3d entityToWorld(IEntity entity, double x, double y, double z, float tickDelta) {
        float yaw = ParticleSpaces.lerpYaw(entity, tickDelta) * ((float)Math.PI / 180);
        return ParticleSpaces.lerpPos(entity, tickDelta).add(new Vec3d(x, y, z).rotateY(-yaw));
    }

    public static Vec3d worldToEntity(double originX, double originY, double originZ, float yawDegrees, Vec3d world) {
        return world.subtract(originX, originY, originZ).rotateY(yawDegrees * ((float)Math.PI / 180));
    }

    public static Vec3d pointInActorAabb(Vec3d origin, float width, float height, Random random) {
        return origin.add((random.nextDouble() - 0.5) * (double)width, random.nextDouble() * (double)height, (random.nextDouble() - 0.5) * (double)width);
    }
}

