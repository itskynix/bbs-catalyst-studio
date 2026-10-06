/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.particle.Particle
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.ParticlePovAccess;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={Particle.class})
public interface ParticlePovAccessor
extends ParticlePovAccess {
    @Override
    @Accessor(value="x")
    public double bbsPov$getX();

    @Override
    @Accessor(value="y")
    public double bbsPov$getY();

    @Override
    @Accessor(value="z")
    public double bbsPov$getZ();

    @Override
    @Accessor(value="x")
    public void bbsPov$setX(double var1);

    @Override
    @Accessor(value="y")
    public void bbsPov$setY(double var1);

    @Override
    @Accessor(value="z")
    public void bbsPov$setZ(double var1);

    @Override
    @Accessor(value="prevPosX")
    public void bbsPov$setPrevPosX(double var1);

    @Override
    @Accessor(value="prevPosY")
    public void bbsPov$setPrevPosY(double var1);

    @Override
    @Accessor(value="prevPosZ")
    public void bbsPov$setPrevPosZ(double var1);

    @Override
    @Accessor(value="gravityStrength")
    public float bbsPov$getGravityStrength();

    @Override
    @Accessor(value="velocityMultiplier")
    public float bbsPov$getVelocityMultiplier();

    @Override
    @Accessor(value="maxAge")
    public int bbsPov$getMaxAge();

    @Override
    @Accessor(value="age")
    public void bbsPov$setAge(int var1);
}

