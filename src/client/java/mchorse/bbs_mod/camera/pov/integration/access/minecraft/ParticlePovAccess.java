/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

public interface ParticlePovAccess {
    public double bbsPov$getX();

    public double bbsPov$getY();

    public double bbsPov$getZ();

    public void bbsPov$setX(double var1);

    public void bbsPov$setY(double var1);

    public void bbsPov$setZ(double var1);

    public void bbsPov$setPrevPosX(double var1);

    public void bbsPov$setPrevPosY(double var1);

    public void bbsPov$setPrevPosZ(double var1);

    public float bbsPov$getGravityStrength();

    public float bbsPov$getVelocityMultiplier();

    public int bbsPov$getMaxAge();

    public void bbsPov$setAge(int var1);
}

